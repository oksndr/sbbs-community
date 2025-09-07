package com.itheima.sbbs.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.druid.util.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;


import com.itheima.sbbs.common.BussinessException;
import com.itheima.sbbs.entity.*;
import com.itheima.sbbs.mapper.LikeMapper;
import com.itheima.sbbs.mapper.PostMapper;
import com.itheima.sbbs.mapper.UserMapper;
import com.itheima.sbbs.mapper.CommentMapper;
import com.itheima.sbbs.service.AsyncNotificationService;
import com.itheima.sbbs.service.LikeService;
import com.itheima.sbbs.service.PostService;
import com.itheima.sbbs.service.CommentService;
import com.itheima.sbbs.service.ExperienceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class LikeServiceImpl extends ServiceImpl<LikeMapper, Like> implements LikeService {

    @Autowired
    private LikeMapper likeMapper;
    @Autowired
    private PostMapper postMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private PostService postService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private AsyncNotificationService asyncNotificationService;
    @Autowired
    private ExperienceService experienceService;

    /**
     * 点赞帖子
     * 检测帖子是否存在
     * 检测用户之前有没有点过踩(有的话就把点踩记录删除)
     */
    @Override
    public void like(Like like) {
        // 🚀 **预检查：移出事务，减少事务持有时间**
        Post post = postMapper.selectById(like.getPostId());
        if (post == null) {
            throw new BussinessException(ErrorResult.numError());
        }
        
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        
        // 🚀 **核心数据库操作：最小事务范围**
        this.executeLikeTransaction(like, post.getId(), currentUserId);
        
        // 🚀 **异步化非关键操作：移出事务**
        if (currentUserId != post.getUserId()) {
            // 异步添加点赞经验值给帖子作者
            try {
                experienceService.addPostLikeExperience(post.getUserId());
                // 给点赞者每天第一次点赞经验值
                experienceService.addFirstLikeExperience(currentUserId);
            } catch (Exception e) {
                log.warn("添加帖子点赞经验值失败，帖子作者ID: {}", post.getUserId(), e);
            }
            
            // 异步发送通知
            asyncExecuteNotification(post.getUserId(), currentUserId, post.getId(), like.getId(), "like");
        }
        
        // 异步清理缓存
        asyncExecuteCacheClear(post.getId());
    }
    
    /**
     * 🚀 **最小事务范围：只包含数据库写操作**
     */
    @Transactional
    private void executeLikeTransaction(Like like, Integer postId, Integer currentUserId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Like::getPostId, postId)
                .eq(Like::getUserId, currentUserId);
        Like likeInDb = likeMapper.selectOne(wrapper);
        
        if (likeInDb == null) {
            // 新点赞：直接插入
            likeMapper.insert(like);
            postLikePlus1(like.getPostId());
        } else if (likeInDb.getType() == 1) {
            throw new BussinessException(ErrorResult.likeError());
        } else if (likeInDb.getType() == -1) {
            // 优化：从点踩改为点赞，使用update替代delete+insert
            LambdaUpdateWrapper<Like> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Like::getId, likeInDb.getId())
                       .set(Like::getType, 1)
                       .set(Like::getUpdated, new java.util.Date());
            likeMapper.update(null, updateWrapper);
            postLikePlus2(like.getPostId());
        }
    }

    /**
     * 用户取消点赞帖子但没点踩
     * @param like
     */
    @Override
    public void cancelLike(Like like) {
        // 🚀 **预检查：移出事务，减少事务持有时间**
        Post post = postMapper.selectById(like.getPostId());
        if (post == null) {
            throw new BussinessException(ErrorResult.numError());
        }
        
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        
        // 🚀 **核心数据库操作：最小事务范围**
        this.executeCancelLikeTransaction(like, post.getId(), currentUserId);
        
        // 🚀 **异步化非关键操作：缓存清理**
        asyncExecuteCacheClear(post.getId());
    }
    
    /**
     * 🚀 **最小事务范围：只包含数据库写操作**
     */
    @Transactional
    private void executeCancelLikeTransaction(Like like, Integer postId, Integer currentUserId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Like::getPostId, postId)
                .eq(Like::getUserId, currentUserId);
        Like likeInDb = likeMapper.selectOne(wrapper);
        
        if (likeInDb == null) {
            //并没有给这个帖子点过赞或踩 直接抛出异常
            throw new BussinessException(ErrorResult.cancelLikeError());
        } else if (likeInDb.getType() == 1) {
            //点过赞 -> 删除点赞记录 点赞数-1
            likeMapper.deleteById(likeInDb.getId());
            postLikeLess1(like.getPostId());
        } else if (likeInDb.getType() == -1) {
            //点过踩 直接抛出异常
            throw new BussinessException(ErrorResult.cancelLikeError());
        }
    }

    /**
     * 用户点踩
     * @param like
     */
    @Override
    public void dislike(Like like) {
        // 🚀 **预检查：移出事务，减少事务持有时间**
        Post post = postMapper.selectById(like.getPostId());
        if (post == null) {
            throw new BussinessException(ErrorResult.numError());
        }
        
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        
        // 🚀 **核心数据库操作：最小事务范围**
        this.executeDislikeTransaction(like, post.getId(), currentUserId);
        
        // 🚀 **异步化非关键操作：移出事务**
        if (currentUserId != post.getUserId()) {
            // 异步扣除点踩经验值给帖子作者
            try {
                experienceService.reducePostDislikeExperience(post.getUserId());
            } catch (Exception e) {
                log.warn("扣除帖子点踩经验值失败，帖子作者ID: {}", post.getUserId(), e);
            }
            
            // 异步发送通知
            asyncExecuteNotification(post.getUserId(), currentUserId, post.getId(), like.getId(), "dislike");
        }
        
        // 异步清理缓存
        asyncExecuteCacheClear(post.getId());
    }
    
    /**
     * 🚀 **最小事务范围：只包含数据库写操作**
     */
    @Transactional
    private void executeDislikeTransaction(Like like, Integer postId, Integer currentUserId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Like::getPostId, postId)
                .eq(Like::getUserId, currentUserId);
        Like likeInDb = likeMapper.selectOne(wrapper);
        
        if (likeInDb == null) {
            // 新点踩：直接插入
            likeMapper.insert(like);
            postDisLikePlus1(like.getPostId());
        } else if (likeInDb.getType() == 1) {
            // 优化：从点赞改为点踩，使用update替代delete+insert
            LambdaUpdateWrapper<Like> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Like::getId, likeInDb.getId())
                       .set(Like::getType, -1)
                       .set(Like::getUpdated, new java.util.Date());
            likeMapper.update(null, updateWrapper);
            postLikeLess2(like.getPostId());
        } else if (likeInDb.getType() == -1) {
            throw new BussinessException(ErrorResult.dislikeError());
        }
    }

    /**
     * 用户取消点踩
     */
    @Override
    public void cancelDislike(Like like) {
        // 🚀 **预检查：移出事务，减少事务持有时间**
        Post post = postMapper.selectById(like.getPostId());
        if (post == null) {
            throw new BussinessException(ErrorResult.numError());
        }
        
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        
        // 🚀 **核心数据库操作：最小事务范围**
        this.executeCancelDislikeTransaction(like, post.getId(), currentUserId);
        
        // 🚀 **异步化非关键操作：缓存清理**
        asyncExecuteCacheClear(post.getId());
    }
    
    /**
     * 🚀 **最小事务范围：只包含数据库写操作**
     */
    @Transactional
    private void executeCancelDislikeTransaction(Like like, Integer postId, Integer currentUserId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Like::getPostId, postId)
                .eq(Like::getUserId, currentUserId);
        Like likeInDb = likeMapper.selectOne(wrapper);
        
        if (likeInDb == null) {
            //并没有给这个帖子点赞或点踩 直接抛出异常
            throw new BussinessException(ErrorResult.cancelDislikeError());
        } else if (likeInDb.getType() == 1) {
            //点过赞 直接抛出异常
            throw new BussinessException(ErrorResult.likeError());
        } else if (likeInDb.getType() == -1) {
            //点过踩 -> 删除点踩记录 点踩数-1
            likeMapper.deleteById(likeInDb.getId());
            postDislikeLess1(like.getPostId());
        }
    }

    /**
     * 新用户点赞
     * 帖子点赞数 + 1
     */
    private void postLikePlus1(Integer postId) {
        LambdaUpdateWrapper<Post> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Post::getId, postId)
                .setSql("like_count = like_count + 1")
                .setSql("updated = CURRENT_TIMESTAMP");
        postMapper.update(null, wrapper);
    }

    /**
     * 新用户点踩
     * 帖子点踩数 + 1
     */
    private void postDisLikePlus1(Integer postId) {
        LambdaUpdateWrapper<Post> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Post::getId, postId)
                .setSql("dislike_count = dislike_count + 1")
                .setSql("updated = CURRENT_TIMESTAMP");
        postMapper.update(null, wrapper);
    }

    /**
     * 老用户 取消点赞, 但没有点踩
     * 帖子点赞数 - 1
     */
    private void postLikeLess1(Integer postId) {
        LambdaUpdateWrapper<Post> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Post::getId, postId)
                .setSql("like_count = like_count - 1")
                .setSql("updated = CURRENT_TIMESTAMP");
        postMapper.update(null, wrapper);
    }

    /**
     * 老用户 取消点踩 但没有点赞
     * 帖子点踩数 - 1
     */
    private void postDislikeLess1(Integer postId) {
        LambdaUpdateWrapper<Post> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Post::getId, postId)
                .setSql("dislike_count = dislike_count - 1")
                .setSql("updated = CURRENT_TIMESTAMP");
        postMapper.update(null, wrapper);
    }

    /**
     * 曾经点过踩的老用户改成点赞:
     * 点赞 + 1
     * 点踩 - 1
     */
    private void postLikePlus2(Integer postId) {
        LambdaUpdateWrapper<Post> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Post::getId, postId)
                .setSql("like_count = like_count + 1")
                .setSql("dislike_count = dislike_count - 1")
                .setSql("updated = CURRENT_TIMESTAMP");
        postMapper.update(null, wrapper);
    }

    /**
     * 曾经点过赞的老用户改成点踩:
     * 点赞 - 1
     * 点踩 + 1
     */
    private void postLikeLess2(Integer postId) {
        LambdaUpdateWrapper<Post> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Post::getId, postId)
                .setSql("like_count = like_count - 1")
                .setSql("dislike_count = dislike_count + 1")
                .setSql("updated = CURRENT_TIMESTAMP");
        postMapper.update(null, wrapper);
    }

    /**
     * 点赞评论
     */
    @Override
    public void likeComment(Like like) {
        // 🚀 **预检查：移出事务，减少事务持有时间**
        Comment comment = commentMapper.selectById(like.getCommentId());
        if (comment == null) {
            throw new BussinessException(ErrorResult.numError());
        }
        
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        
        // 🚀 **核心数据库操作：最小事务范围**
        this.executeLikeCommentTransaction(like, comment.getId(), currentUserId);
        
        // 🚀 **异步化非关键操作：移出事务**
        if (currentUserId != comment.getUserId()) {
            // 异步添加评论点赞经验值给评论作者
            try {
                experienceService.addCommentLikeExperience(comment.getUserId());
            } catch (Exception e) {
                log.warn("添加评论点赞经验值失败，评论作者ID: {}", comment.getUserId(), e);
            }
            
            // 异步发送通知
            asyncExecuteNotification(comment.getUserId(), currentUserId, comment.getId(), like.getId(), "commentLike");
        }
        
        // 异步清理缓存
        clearCommentCache(comment);
    }
    
    /**
     * 🚀 **最小事务范围：只包含数据库写操作**
     */
    @Transactional
    private void executeLikeCommentTransaction(Like like, Integer commentId, Integer currentUserId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Like::getCommentId, commentId)
                .eq(Like::getUserId, currentUserId);
        Like likeInDb = likeMapper.selectOne(wrapper);
        
        if (likeInDb == null) {
            // 新点赞：直接插入
            likeMapper.insert(like);
            commentMapper.updateCountsById(like.getCommentId(), 1, 0);
        } else if (likeInDb.getType() == 1) {
            throw new BussinessException(ErrorResult.likeError());
        } else if (likeInDb.getType() == -1) {
            // 优化：从点踩改为点赞，使用update替代delete+insert
            LambdaUpdateWrapper<Like> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Like::getId, likeInDb.getId())
                       .set(Like::getType, 1)
                       .set(Like::getUpdated, new java.util.Date());
            likeMapper.update(null, updateWrapper);
            commentMapper.updateCountsById(like.getCommentId(), 1, -1);
        }
    }

    /**
     * 点踩评论
     */
    @Override
    public void dislikeComment(Like like) {
        // 🚀 **预检查：移出事务，减少事务持有时间**
        Comment comment = commentMapper.selectById(like.getCommentId());
        if (comment == null) {
            throw new BussinessException(ErrorResult.numError());
        }
        
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        
        // 🚀 **核心数据库操作：最小事务范围**
        this.executeDislikeCommentTransaction(like, comment.getId(), currentUserId);
        
        // 🚀 **异步化非关键操作：移出事务**
        if (currentUserId != comment.getUserId()) {
            // 异步发送通知
            asyncExecuteNotification(comment.getUserId(), currentUserId, comment.getId(), like.getId(), "commentDislike");
        }
        
        // 异步清理缓存
        clearCommentCache(comment);
    }
    
    /**
     * 🚀 **最小事务范围：只包含数据库写操作**
     */
    @Transactional
    private void executeDislikeCommentTransaction(Like like, Integer commentId, Integer currentUserId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Like::getCommentId, commentId)
                .eq(Like::getUserId, currentUserId);
        Like likeInDb = likeMapper.selectOne(wrapper);
        
        if (likeInDb == null) {
            // 新点踩：直接插入
            likeMapper.insert(like);
            commentMapper.updateCountsById(like.getCommentId(), 0, 1);
        } else if (likeInDb.getType() == -1) {
            throw new BussinessException(ErrorResult.dislikeError());
        } else if (likeInDb.getType() == 1) {
            // 优化：从点赞改为点踩，使用update替代delete+insert
            LambdaUpdateWrapper<Like> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Like::getId, likeInDb.getId())
                       .set(Like::getType, -1)
                       .set(Like::getUpdated, new java.util.Date());
            likeMapper.update(null, updateWrapper);
            commentMapper.updateCountsById(like.getCommentId(), -1, 1);
        }
    }

    private void clearCommentCache(Comment comment) {
        commentService.clearCommentPageCache(comment.getId());
    }

    // ========== 异步版本方法（解决Web上下文问题）==========

    @Override
    @Transactional
    public void likeCommentAsync(Like like, Integer currentUserId) {
        Comment comment = commentMapper.selectById(like.getCommentId());
        if (comment != null) {
            LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Like::getCommentId, like.getCommentId())
                    .eq(Like::getUserId, like.getUserId());
            Like likeInDb = likeMapper.selectOne(wrapper);
            if (likeInDb == null) {
                likeMapper.insert(like);
                commentMapper.updateCountsById(like.getCommentId(), 1, 0);
                
                // 异步添加评论点赞经验值给评论作者
                try {
                    if (currentUserId != comment.getUserId()) {
                        experienceService.addCommentLikeExperience(comment.getUserId());
                    }
                } catch (Exception e) {
                    log.warn("添加评论点赞经验值失败，评论作者ID: {}", comment.getUserId(), e);
                }
                
            } else if (likeInDb.getType() == 1) {
                throw new BussinessException(ErrorResult.likeError());
            } else if (likeInDb.getType() == -1) {
                likeMapper.deleteById(likeInDb.getId());
                likeMapper.insert(like);
                commentMapper.updateCountsById(like.getCommentId(), 1, -1);
                
                // 异步添加评论点赞经验值给评论作者（从点踩改为点赞）
                try {
                    if (currentUserId != comment.getUserId()) {
                        experienceService.addCommentLikeExperience(comment.getUserId());
                    }
                } catch (Exception e) {
                    log.warn("添加评论点赞经验值失败，评论作者ID: {}", comment.getUserId(), e);
                }
            }
            if (currentUserId != comment.getUserId()) {
                User user = userMapper.selectById(comment.getUserId());
                if (user != null) {
                    asyncNotificationService.sendCommentLikeNotification(user, currentUserId, comment.getId(), like.getId());
                }
            }
            clearCommentCache(comment);
        } else {
            throw new BussinessException(ErrorResult.numError());
        }
    }

    @Override
    @Transactional
    public void dislikeCommentAsync(Like like, Integer currentUserId) {
        Comment comment = commentMapper.selectById(like.getCommentId());
        if (comment != null) {
            LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Like::getCommentId, like.getCommentId())
                    .eq(Like::getUserId, like.getUserId());
            Like likeInDb = likeMapper.selectOne(wrapper);
            if (likeInDb == null) {
                likeMapper.insert(like);
                commentMapper.updateCountsById(like.getCommentId(), 0, 1);
            } else if (likeInDb.getType() == -1) {
                throw new BussinessException(ErrorResult.dislikeError());
            } else if (likeInDb.getType() == 1) {
                likeMapper.deleteById(likeInDb.getId());
                likeMapper.insert(like);
                commentMapper.updateCountsById(like.getCommentId(), -1, 1);
            }
            if (currentUserId != comment.getUserId()) {
                User user = userMapper.selectById(comment.getUserId());
                if (user != null) {
                    asyncNotificationService.sendCommentDislikeNotification(user, currentUserId, comment.getId(), like.getId());
                }
            }
            clearCommentCache(comment);
        } else {
            throw new BussinessException(ErrorResult.numError());
        }
    }

    @Override
    @Transactional
    public void cancelLikeCommentAsync(Like like, Integer currentUserId) {
        Comment comment = commentMapper.selectById(like.getCommentId());
        if (comment != null) {
            LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Like::getCommentId, like.getCommentId())
                    .eq(Like::getUserId, like.getUserId());
            Like likeInDb = likeMapper.selectOne(wrapper);
            if (likeInDb == null) {
                throw new BussinessException(ErrorResult.cancelLikeError());
            } else if (likeInDb.getType() == 1) {
                likeMapper.deleteById(likeInDb.getId());
                commentMapper.updateCountsById(like.getCommentId(), -1, 0);
                clearCommentCache(comment);
            } else if (likeInDb.getType() == -1) {
                throw new BussinessException(ErrorResult.cancelLikeError());
            }
        } else {
            throw new BussinessException(ErrorResult.numError());
        }
    }

    @Override
    @Transactional
    public void cancelDislikeCommentAsync(Like like, Integer currentUserId) {
        Comment comment = commentMapper.selectById(like.getCommentId());
        if (comment != null) {
            LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Like::getCommentId, like.getCommentId())
                    .eq(Like::getUserId, like.getUserId());
            Like likeInDb = likeMapper.selectOne(wrapper);
            if (likeInDb == null) {
                throw new BussinessException(ErrorResult.cancelDislikeError());
            } else if (likeInDb.getType() == -1) {
                likeMapper.deleteById(likeInDb.getId());
                commentMapper.updateCountsById(like.getCommentId(), 0, -1);
            } else if (likeInDb.getType() == 1) {
                throw new BussinessException(ErrorResult.cancelDislikeError());
            }
            clearCommentCache(comment);
        } else {
            throw new BussinessException(ErrorResult.numError());
        }
    }

    /**
     * 🚀 **异步执行通知发送**
     */
    private void asyncExecuteNotification(Integer targetUserId, Integer senderUserId, Integer entityId, Integer likeId, String type) {
        CompletableFuture.runAsync(() -> {
            try {
                User user = userMapper.selectById(targetUserId);
                if (user != null) {
                    if ("like".equals(type)) {
                        asyncNotificationService.sendLikeNotification(user, senderUserId, entityId, likeId);
                    } else if ("dislike".equals(type)) {
                        asyncNotificationService.sendDislikeNotification(user, senderUserId, entityId, likeId);
                    } else if ("commentLike".equals(type)) {
                        asyncNotificationService.sendCommentLikeNotification(user, senderUserId, entityId, likeId);
                    } else if ("commentDislike".equals(type)) {
                        asyncNotificationService.sendCommentDislikeNotification(user, senderUserId, entityId, likeId);
                    }
                }
            } catch (Exception e) {
                log.warn("异步发送通知失败，目标用户ID: {}, 发送者ID: {}, 类型: {}", targetUserId, senderUserId, type, e);
            }
        });
    }

    /**
     * 🚀 **异步执行缓存清理**
     */
    private void asyncExecuteCacheClear(Integer postId) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("异步清除帖子缓存，帖子ID: {}", postId);
                postService.clearPostDetailCache(postId);
                postService.clearPostListCache();
            } catch (Exception e) {
                log.warn("异步清除帖子缓存失败，帖子ID: {}", postId, e);
            }
        });
    }

    /**
     * 取消点赞评论
     */
    @Override
    public void cancelLikeComment(Like like) {
        // 🚀 **预检查：移出事务，减少事务持有时间**
        Comment comment = commentMapper.selectById(like.getCommentId());
        if (comment == null) {
            throw new BussinessException(ErrorResult.numError());
        }
        
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        
        // 🚀 **核心数据库操作：最小事务范围**
        this.executeCancelLikeCommentTransaction(like, comment.getId(), currentUserId);
        
        // 异步清理缓存
        clearCommentCache(comment);
    }
    
    /**
     * 🚀 **最小事务范围：只包含数据库写操作**
     */
    @Transactional
    private void executeCancelLikeCommentTransaction(Like like, Integer commentId, Integer currentUserId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Like::getCommentId, commentId)
                .eq(Like::getUserId, currentUserId);
        Like likeInDb = likeMapper.selectOne(wrapper);
        
        if (likeInDb == null) {
            throw new BussinessException(ErrorResult.cancelLikeError());
        } else if (likeInDb.getType() == 1) {
            likeMapper.deleteById(likeInDb.getId());
            commentMapper.updateCountsById(like.getCommentId(), -1, 0);
        } else if (likeInDb.getType() == -1) {
            throw new BussinessException(ErrorResult.cancelLikeError());
        }
    }

    /**
     * 取消点踩评论
     */
    @Override
    public void cancelDislikeComment(Like like) {
        // 🚀 **预检查：移出事务，减少事务持有时间**
        Comment comment = commentMapper.selectById(like.getCommentId());
        if (comment == null) {
            throw new BussinessException(ErrorResult.numError());
        }
        
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        
        // 🚀 **核心数据库操作：最小事务范围**
        this.executeCancelDislikeCommentTransaction(like, comment.getId(), currentUserId);
        
        // 异步清理缓存
        clearCommentCache(comment);
    }
    
    /**
     * 🚀 **最小事务范围：只包含数据库写操作**
     */
    @Transactional
    private void executeCancelDislikeCommentTransaction(Like like, Integer commentId, Integer currentUserId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Like::getCommentId, commentId)
                .eq(Like::getUserId, currentUserId);
        Like likeInDb = likeMapper.selectOne(wrapper);
        
        if (likeInDb == null) {
            throw new BussinessException(ErrorResult.cancelDislikeError());
        } else if (likeInDb.getType() == -1) {
            likeMapper.deleteById(likeInDb.getId());
            commentMapper.updateCountsById(like.getCommentId(), 0, -1);
        } else if (likeInDb.getType() == 1) {
            throw new BussinessException(ErrorResult.cancelDislikeError());
        }
    }

}
