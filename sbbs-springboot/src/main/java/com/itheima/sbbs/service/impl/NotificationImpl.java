package com.itheima.sbbs.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.sbbs.entity.Comment;
import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.entity.NotificationJumpInfo;
import com.itheima.sbbs.enums.NotificationType;
import com.itheima.sbbs.mapper.CommentMapper;
import com.itheima.sbbs.mapper.NotificationMapper;
import com.itheima.sbbs.service.NotificationCacheService;
import com.itheima.sbbs.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class NotificationImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    @Autowired
    private CommentMapper commentMapper;
    
    @Autowired
    private NotificationCacheService notificationCacheService;

    // 默认页大小，与Controller中保持一致
    private static final Integer DEFAULT_PAGE_SIZE = 10;

    @Override
    public NotificationJumpInfo getJumpInfo(Integer notificationId) {
        log.info("获取通知跳转信息，通知ID: {}", notificationId);

        // 1. 查询通知信息
        Notification notification = this.getById(notificationId);
        if (notification == null || notification.getDeleted() == 1) {
            log.warn("通知不存在或已删除，通知ID: {}", notificationId);
            return null;
        }

        // 2. 权限检查：只有通知接收者才能获取跳转信息
        Integer currentUserId = null;
        if (StpUtil.isLogin()) {
            currentUserId = StpUtil.getLoginIdAsInt();
            if (!currentUserId.equals(notification.getReceiverId())) {
                log.warn("用户无权访问此通知，用户ID: {}, 通知接收者ID: {}", currentUserId, notification.getReceiverId());
                return null;
            }
        } else {
            log.warn("用户未登录，无法获取通知跳转信息");
            return null;
        }

        // 3. 根据通知类型构建跳转信息
        NotificationJumpInfo jumpInfo = buildJumpInfo(notification);
        
        // 4. 如果成功构建跳转信息，自动标记为已读
        if (jumpInfo != null && !notification.isRead()) {
            try {
                boolean markSuccess = markAsRead(notificationId, currentUserId);
                if (markSuccess) {
                    log.info("通知已自动标记为已读，通知ID: {}", notificationId);
                } else {
                    log.warn("通知自动标记为已读失败，通知ID: {}", notificationId);
                }
            } catch (Exception e) {
                log.error("通知自动标记为已读时发生异常，通知ID: {}", notificationId, e);
                // 不影响跳转信息的返回，继续执行
            }
        }
        
        return jumpInfo;
    }

    /**
     * 根据通知信息构建跳转信息
     * @param notification 通知对象
     * @return 跳转信息
     */
    private NotificationJumpInfo buildJumpInfo(Notification notification) {
        NotificationJumpInfo jumpInfo = new NotificationJumpInfo();
        jumpInfo.setNotificationId(notification.getId());
        jumpInfo.setNotificationType(notification.getNotificationType());

        Integer notificationType = notification.getNotificationType();
        Integer relatedId = notification.getRelatedId();
        Integer triggerEntityId = notification.getTriggerEntityId();

        log.info("构建跳转信息，通知类型: {}, 相关ID: {}, 触发实体ID: {}",
                notificationType, relatedId, triggerEntityId);

        try {
            switch (notificationType) {
                case 1: // 评论了我的帖子
                    return buildType1JumpInfo(jumpInfo, relatedId, triggerEntityId);
                case 2: // 回复了我的评论
                    return buildType2JumpInfo(jumpInfo, relatedId, triggerEntityId);
                case 3: // 在评论中@了我
                    return buildType3JumpInfo(jumpInfo, relatedId, triggerEntityId);
                case 4: // "回复 xxx :"格式回复了我
                    return buildType4JumpInfo(jumpInfo, relatedId, triggerEntityId);
                case 5: // 点赞了我的帖子
                case 6: // 点踩了我的帖子
                    return buildLikePostJumpInfo(jumpInfo, notification);
                case 7: // 点赞了我的评论
                case 8: // 点踩了我的评论
                    return buildLikeCommentJumpInfo(jumpInfo, notification);
                case 9: // 帖子被管理员删除
                    log.info("类型9通知（帖子被删除）不支持跳转，通知ID: {}", notification.getId());
                    return null; // 前端应该禁用点击
                case 10: // 用户关注
                    return buildUserFollowJumpInfo(jumpInfo, notification);
                default:
                    log.warn("不支持的通知类型: {}", notificationType);
                    return null;
            }
        } catch (Exception e) {
            log.error("构建跳转信息时发生错误，通知ID: {}, 通知类型: {}",
                    notification.getId(), notificationType, e);
            return null;
        }
    }

    /**
     * 构建类型1跳转信息：评论了我的帖子
     * @param jumpInfo 跳转信息对象
     * @param postId 帖子ID (relatedId)
     * @param commentId 评论ID (triggerEntityId)
     * @return 跳转信息
     */
    private NotificationJumpInfo buildType1JumpInfo(NotificationJumpInfo jumpInfo, Integer postId, Integer commentId) {
        log.info("构建类型1跳转信息，帖子ID: {}, 评论ID: {}", postId, commentId);

        // 查询评论信息
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == 1) {
            log.warn("评论不存在或已删除，评论ID: {}", commentId);
            return null;
        }

        // 计算评论所在页码
        Integer pageNumber = calculateCommentPage(postId, commentId);
        if (pageNumber == null) {
            log.warn("无法计算评论页码，帖子ID: {}, 评论ID: {}", postId, commentId);
            return null;
        }

        jumpInfo.setPostId(postId);
        jumpInfo.setPageNumber(pageNumber);
        jumpInfo.setTargetCommentId(commentId);
        jumpInfo.setParentCommentId(null); // 一级评论没有父评论
        jumpInfo.setJumpType("comment");
        jumpInfo.setExtraInfo("有人评论了您的帖子");

        log.info("类型1跳转信息构建完成，帖子ID: {}, 页码: {}, 评论ID: {}", postId, pageNumber, commentId);
        return jumpInfo;
    }

    /**
     * 构建类型2跳转信息：回复了我的评论
     * @param jumpInfo 跳转信息对象
     * @param parentCommentId 父评论ID (relatedId)
     * @param replyCommentId 回复评论ID (triggerEntityId)
     * @return 跳转信息
     */
    private NotificationJumpInfo buildType2JumpInfo(NotificationJumpInfo jumpInfo, Integer parentCommentId, Integer replyCommentId) {
        log.info("构建类型2跳转信息，父评论ID: {}, 回复评论ID: {}", parentCommentId, replyCommentId);

        // 查询父评论信息
        Comment parentComment = commentMapper.selectById(parentCommentId);
        if (parentComment == null || parentComment.getDeleted() == 1) {
            log.warn("父评论不存在或已删除，父评论ID: {}", parentCommentId);
            return null;
        }

        // 查询回复评论信息
        Comment replyComment = commentMapper.selectById(replyCommentId);
        if (replyComment == null || replyComment.getDeleted() == 1) {
            log.warn("回复评论不存在或已删除，回复评论ID: {}", replyCommentId);
            return null;
        }

        Integer postId = parentComment.getPostId();

        // 计算父评论所在页码
        Integer pageNumber = calculateCommentPage(postId, parentCommentId);
        if (pageNumber == null) {
            log.warn("无法计算父评论页码，帖子ID: {}, 父评论ID: {}", postId, parentCommentId);
            return null;
        }

        jumpInfo.setPostId(postId);
        jumpInfo.setPageNumber(pageNumber);
        jumpInfo.setTargetCommentId(replyCommentId);
        jumpInfo.setParentCommentId(parentCommentId);
        jumpInfo.setJumpType("comment");
        jumpInfo.setExtraInfo("有人回复了您的评论");

        log.info("类型2跳转信息构建完成，帖子ID: {}, 页码: {}, 父评论ID: {}, 回复ID: {}",
                postId, pageNumber, parentCommentId, replyCommentId);
        return jumpInfo;
    }

    /**
     * 构建类型3跳转信息：在评论中@了我
     * @param jumpInfo 跳转信息对象
     * @param postId 帖子ID (relatedId)
     * @param mentionCommentId @我的评论ID (triggerEntityId)
     * @return 跳转信息
     */
    private NotificationJumpInfo buildType3JumpInfo(NotificationJumpInfo jumpInfo, Integer postId, Integer mentionCommentId) {
        log.info("构建类型3跳转信息，帖子ID: {}, @评论ID: {}", postId, mentionCommentId);

        // 查询@我的评论信息
        Comment mentionComment = commentMapper.selectById(mentionCommentId);
        if (mentionComment == null || mentionComment.getDeleted() == 1) {
            log.warn("@评论不存在或已删除，评论ID: {}", mentionCommentId);
            return null;
        }

        Integer parentCommentId = mentionComment.getParentId();

        if (parentCommentId == null) {
            // 一级评论中@了我
            Integer pageNumber = calculateCommentPage(postId, mentionCommentId);
            if (pageNumber == null) {
                log.warn("无法计算@评论页码，帖子ID: {}, 评论ID: {}", postId, mentionCommentId);
                return null;
            }

            jumpInfo.setPostId(postId);
            jumpInfo.setPageNumber(pageNumber);
            jumpInfo.setTargetCommentId(mentionCommentId);
            jumpInfo.setParentCommentId(null);
            jumpInfo.setJumpType("comment");
            jumpInfo.setExtraInfo("有人在评论中@了您");
        } else {
            // 二级评论中@了我
            Integer pageNumber = calculateCommentPage(postId, parentCommentId);
            if (pageNumber == null) {
                log.warn("无法计算父评论页码，帖子ID: {}, 父评论ID: {}", postId, parentCommentId);
                return null;
            }

            jumpInfo.setPostId(postId);
            jumpInfo.setPageNumber(pageNumber);
            jumpInfo.setTargetCommentId(mentionCommentId);
            jumpInfo.setParentCommentId(parentCommentId);
            jumpInfo.setJumpType("comment");
            jumpInfo.setExtraInfo("有人在回复中@了您");
        }

        log.info("类型3跳转信息构建完成，帖子ID: {}, 页码: {}, 目标评论ID: {}, 父评论ID: {}",
                jumpInfo.getPostId(), jumpInfo.getPageNumber(), jumpInfo.getTargetCommentId(), jumpInfo.getParentCommentId());
        return jumpInfo;
    }

    /**
     * 构建类型4跳转信息："回复 xxx :"格式回复了我
     * @param jumpInfo 跳转信息对象
     * @param parentCommentId 父评论ID (relatedId)
     * @param replyCommentId 回复评论ID (triggerEntityId)
     * @return 跳转信息
     */
    private NotificationJumpInfo buildType4JumpInfo(NotificationJumpInfo jumpInfo, Integer parentCommentId, Integer replyCommentId) {
        log.info("构建类型4跳转信息，父评论ID: {}, 回复评论ID: {}", parentCommentId, replyCommentId);

        // 查询父评论信息
        Comment parentComment = commentMapper.selectById(parentCommentId);
        if (parentComment == null || parentComment.getDeleted() == 1) {
            log.warn("父评论不存在或已删除，父评论ID: {}", parentCommentId);
            return null;
        }

        // 查询回复评论信息
        Comment replyComment = commentMapper.selectById(replyCommentId);
        if (replyComment == null || replyComment.getDeleted() == 1) {
            log.warn("回复评论不存在或已删除，回复评论ID: {}", replyCommentId);
            return null;
        }

        Integer postId = parentComment.getPostId();

        // 计算父评论所在页码
        Integer pageNumber = calculateCommentPage(postId, parentCommentId);
        if (pageNumber == null) {
            log.warn("无法计算父评论页码，帖子ID: {}, 父评论ID: {}", postId, parentCommentId);
            return null;
        }

        jumpInfo.setPostId(postId);
        jumpInfo.setPageNumber(pageNumber);
        jumpInfo.setTargetCommentId(replyCommentId);
        jumpInfo.setParentCommentId(parentCommentId);
        jumpInfo.setJumpType("comment");
        jumpInfo.setExtraInfo("有人用'回复 xxx :'格式回复了您");

        log.info("类型4跳转信息构建完成，帖子ID: {}, 页码: {}, 父评论ID: {}, 回复ID: {}",
                postId, pageNumber, parentCommentId, replyCommentId);
        return jumpInfo;
    }

    /**
     * 计算评论所在的页码
     * @param postId 帖子ID
     * @param commentId 评论ID
     * @param pageSize 每页大小
     * @return 页码，如果找不到则返回null
     */
    private Integer calculateCommentPage(Integer postId, Integer commentId, Integer pageSize) {
        try {
            // 使用传入的页面大小，如果为空则使用默认值
            int actualPageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
            
            // 查询评论在帖子中的位置
            Integer position = commentMapper.findCommentPosition(postId, commentId);

            if (position == null || position <= 0) {
                log.warn("未找到评论 {} 在帖子 {} 中的位置", commentId, postId);
                return null;
            }

            // 计算页码（向上取整）
            int page = (position + actualPageSize - 1) / actualPageSize;
            log.info("评论 {} 在帖子 {} 中的位置是 {}，使用页面大小 {}，对应页码 {}", commentId, postId, position, actualPageSize, page);
            return page;
        } catch (Exception e) {
            log.error("计算评论 {} 所在页码时出错: {}", commentId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 计算评论所在的页码（使用默认页面大小）
     * @param postId 帖子ID
     * @param commentId 评论ID
     * @return 页码，如果找不到则返回null
     */
    private Integer calculateCommentPage(Integer postId, Integer commentId) {
        return calculateCommentPage(postId, commentId, 15); // 使用15作为默认页面大小
    }

    @Override
    public Page<Notification> getNotificationList(Integer receiverId, Integer page, Integer size, Boolean onlyUnread) {
        log.info("查询通知列表，接收者ID: {}, 页码: {}, 每页大小: {}, 只查未读: {}", receiverId, page, size, onlyUnread);
        
        // 参数校验
        if (receiverId == null || receiverId <= 0) {
            log.warn("接收者ID无效: {}", receiverId);
            return new Page<>(page != null ? page : 1, size != null ? size : DEFAULT_PAGE_SIZE);
        }
        
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1 || size > 100) {
            size = DEFAULT_PAGE_SIZE;
        }
        
        try {
            // 先尝试从缓存获取
            Page<Notification> cachedResult = notificationCacheService.getCachedNotificationList(receiverId, page, size, onlyUnread);
            if (cachedResult != null) {
                log.info("从缓存获取通知列表成功，接收者ID: {}, 页码: {}", receiverId, page);
                return cachedResult;
            }
            
            // 缓存未命中，从数据库查询
            log.debug("缓存未命中，从数据库查询通知列表，接收者ID: {}", receiverId);
            
            // 创建分页对象
            Page<Notification> pageObj = new Page<>(page, size);
            
            // 使用Mapper查询通知列表（简化版本）
            Page<Notification> result = baseMapper.getNotificationList(pageObj, receiverId, onlyUnread);
            
            // 为每个通知生成通知文字
            if (result.getRecords() != null && !result.getRecords().isEmpty()) {
                for (Notification notification : result.getRecords()) {
                    generateNotificationText(notification);
                }
            }
            
            // 将查询结果缓存（如果数据量不大的话）
            if (result.getRecords().size() <= 50) { // 只缓存记录数不太多的结果
                notificationCacheService.cacheNotificationList(receiverId, page, size, onlyUnread, result);
            }
            
            log.info("通知列表查询完成，总数: {}, 当前页记录数: {}", result.getTotal(), result.getRecords().size());
            return result;
            
        } catch (Exception e) {
            log.error("查询通知列表失败，接收者ID: {}", receiverId, e);
            return new Page<>(page, size);
        }
    }
    
    @Override
    public boolean markAsRead(Integer notificationId, Integer userId) {
        log.info("标记通知为已读，通知ID: {}, 用户ID: {}", notificationId, userId);
        
        if (notificationId == null || notificationId <= 0 || userId == null || userId <= 0) {
            log.warn("参数无效，通知ID: {}, 用户ID: {}", notificationId, userId);
            return false;
        }
        
        try {
            // 权限验证：只能标记自己的通知
            Notification notification = this.getById(notificationId);
            if (notification == null || notification.getDeleted() == 1) {
                log.warn("通知不存在或已删除，通知ID: {}", notificationId);
                return false;
            }
            
            if (!userId.equals(notification.getReceiverId())) {
                log.warn("用户无权操作此通知，用户ID: {}, 通知接收者ID: {}", userId, notification.getReceiverId());
                return false;
            }
            
            // 如果已经是已读状态，直接返回成功
            if (notification.isRead()) {
                log.info("通知已经是已读状态，通知ID: {}", notificationId);
                return true;
            }
            
            // 更新为已读
            UpdateWrapper<Notification> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", notificationId)
                        .eq("receiver_id", userId)
                        .eq("deleted", 0)
                        .set("is_read", true);
            
            boolean success = this.update(updateWrapper);
            
            // 如果更新成功，清除通知列表缓存
            if (success) {
                notificationCacheService.batchClearNotificationListCache(java.util.Collections.singletonList(userId));
            }
            
            log.info("标记通知为已读{}，通知ID: {}", success ? "成功" : "失败", notificationId);
            return success;
            
        } catch (Exception e) {
            log.error("标记通知为已读失败，通知ID: {}, 用户ID: {}", notificationId, userId, e);
            return false;
        }
    }
    
    @Override
    public int markBatchAsRead(List<Integer> notificationIds, Integer userId) {
        log.info("批量标记通知为已读，通知ID数量: {}, 用户ID: {}", 
                notificationIds != null ? notificationIds.size() : 0, userId);
        
        if (notificationIds == null || notificationIds.isEmpty() || userId == null || userId <= 0) {
            log.warn("参数无效，通知ID列表: {}, 用户ID: {}", notificationIds, userId);
            return 0;
        }
        
        int successCount = 0;
        
        try {
            // 查询出未读的通知ID，只更新这些
            LambdaQueryWrapper<Notification> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(Notification::getId, notificationIds)
                       .eq(Notification::getReceiverId, userId)
                       .eq(Notification::getDeleted, 0);
            // 使用原生SQL方式添加is_read条件
            queryWrapper.last("AND is_read = false");
            
            List<Notification> unreadNotifications = this.list(queryWrapper);
            
            if (!unreadNotifications.isEmpty()) {
                // 获取未读通知的ID列表
                List<Integer> unreadIds = unreadNotifications.stream()
                    .map(Notification::getId)
                    .collect(java.util.stream.Collectors.toList());
                
                // 批量更新为已读
                UpdateWrapper<Notification> updateWrapper = new UpdateWrapper<>();
                updateWrapper.in("id", unreadIds)
                            .eq("receiver_id", userId)
                            .set("is_read", true);
                
                boolean success = this.update(updateWrapper);
                if (success) {
                    successCount = unreadIds.size();
                    
                    // 清除通知列表缓存（使用批量清除方法，即使只有一个用户，但保持代码一致性）
                    notificationCacheService.batchClearNotificationListCache(java.util.Collections.singletonList(userId));
                }
            }
            
            log.info("批量标记通知为已读完成，成功数量: {}", successCount);
            return successCount;
            
        } catch (Exception e) {
            log.error("批量标记通知为已读失败，用户ID: {}", userId, e);
            return 0;
        }
    }
    
    /**
     * 构建点赞/点踩帖子跳转信息（类型5, 6）
     * @param jumpInfo 跳转信息对象
     * @param notification 通知对象
     * @return 跳转信息
     */
    private NotificationJumpInfo buildLikePostJumpInfo(NotificationJumpInfo jumpInfo, Notification notification) {
        Integer postId = notification.getRelatedId();
        Integer notificationType = notification.getNotificationType();
        
        log.info("构建类型{}跳转信息，帖子ID: {}", notificationType, postId);
        
        // 验证related_type是否为帖子
        if (!"1".equals(notification.getRelatedType())) {
            log.warn("通知类型{}的related_type应该是1(帖子)，实际为: {}", notificationType, notification.getRelatedType());
            return null;
        }
        
        jumpInfo.setPostId(postId);
        jumpInfo.setPageNumber(1); // 点赞/点踩帖子直接跳转到帖子首页
        jumpInfo.setTargetCommentId(null);
        jumpInfo.setParentCommentId(null);
        jumpInfo.setJumpType("post");
        
        String actionText = notificationType == 5 ? "点赞" : "点踩";
        jumpInfo.setExtraInfo("有人" + actionText + "了您的帖子");
        
        log.info("类型{}跳转信息构建完成，帖子ID: {}, 页码: 1", notificationType, postId);
        return jumpInfo;
    }
    
    /**
     * 构建点赞/点踩评论跳转信息（类型7, 8）
     * @param jumpInfo 跳转信息对象
     * @param notification 通知对象
     * @return 跳转信息
     */
    private NotificationJumpInfo buildLikeCommentJumpInfo(NotificationJumpInfo jumpInfo, Notification notification) {
        Integer commentId = notification.getRelatedId();
        Integer notificationType = notification.getNotificationType();
        
        log.info("构建类型{}跳转信息，评论ID: {}", notificationType, commentId);
        
        // 验证related_type是否为评论
        if (!"2".equals(notification.getRelatedType())) {
            log.warn("通知类型{}的related_type应该是2(评论)，实际为: {}", notificationType, notification.getRelatedType());
            return null;
        }
        
        // 查询评论信息
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == 1) {
            log.warn("评论不存在或已删除，评论ID: {}", commentId);
            return null;
        }
        
        Integer postId = comment.getPostId();
        Integer parentCommentId = comment.getParentId();
        
        if (parentCommentId == null) {
            // 一级评论被点赞/点踩
            Integer pageNumber = calculateCommentPage(postId, commentId);
            if (pageNumber == null) {
                log.warn("无法计算一级评论页码，帖子ID: {}, 评论ID: {}", postId, commentId);
                return null;
            }
            
            jumpInfo.setPostId(postId);
            jumpInfo.setPageNumber(pageNumber);
            jumpInfo.setTargetCommentId(commentId);
            jumpInfo.setParentCommentId(null);
            jumpInfo.setJumpType("comment");
            
        } else {
            // 二级评论被点赞/点踩，需要跳转到父评论所在页面
            Integer pageNumber = calculateCommentPage(postId, parentCommentId);
            if (pageNumber == null) {
                log.warn("无法计算父评论页码，帖子ID: {}, 父评论ID: {}", postId, parentCommentId);
                return null;
            }
            
            jumpInfo.setPostId(postId);
            jumpInfo.setPageNumber(pageNumber);
            jumpInfo.setTargetCommentId(commentId);
            jumpInfo.setParentCommentId(parentCommentId);
            jumpInfo.setJumpType("comment");
        }
        
        String actionText = notificationType == 7 ? "点赞" : "点踩";
        jumpInfo.setExtraInfo("有人" + actionText + "了您的评论");
        
        log.info("类型{}跳转信息构建完成，帖子ID: {}, 页码: {}, 目标评论ID: {}, 父评论ID: {}",
                notificationType, jumpInfo.getPostId(), jumpInfo.getPageNumber(), 
                jumpInfo.getTargetCommentId(), jumpInfo.getParentCommentId());
        return jumpInfo;
    }
    
    /**
     * 构建用户关注跳转信息（类型10）
     * @param jumpInfo 跳转信息对象
     * @param notification 通知对象
     * @return 跳转信息
     */
    private NotificationJumpInfo buildUserFollowJumpInfo(NotificationJumpInfo jumpInfo, Notification notification) {
        Integer followerId = notification.getTriggerEntityId(); // 关注者的用户ID，从triggerEntityId获取
        
        log.info("构建类型10跳转信息，关注者用户ID: {}", followerId);
        
        // 对于用户关注通知，我们返回关注者的用户ID，前端跳转到用户主页
        jumpInfo.setPostId(null); // 不是帖子相关
        jumpInfo.setPageNumber(null); // 不需要页码
        jumpInfo.setTargetCommentId(null); // 不是评论相关
        jumpInfo.setParentCommentId(null); // 不是评论相关
        jumpInfo.setJumpType("user"); // 跳转类型：用户主页
        jumpInfo.setUserId(followerId); // 关注者的用户ID
        jumpInfo.setExtraInfo("有用户关注了您");
        
        log.info("类型10跳转信息构建完成，关注者用户ID: {}", followerId);
        return jumpInfo;
    }

    /**
     * 为通知生成通知文字
     * @param notification 通知对象
     */
    private void generateNotificationText(Notification notification) {
        try {
            NotificationType notificationType = NotificationType.getByCode(notification.getNotificationType());
            if (notificationType == null) {
                log.warn("未知的通知类型: {}", notification.getNotificationType());
                notification.setNotificationText("您有新的通知");
                return;
            }
            
            String senderName = notification.getSenderUsername() != null ? notification.getSenderUsername() : "某用户";
            String baseText = "";
            
            // 根据通知类型生成基础文本
            switch (notification.getNotificationType()) {
                case 1: // 评论了我的帖子
                    baseText = String.format("%s 评论了您的帖子", senderName);
                    if (notification.getCommentPreview() != null) {
                        baseText += "：" + notification.getCommentPreview();
                    }
                    break;
                    
                case 2: // 回复了我的评论
                    baseText = String.format("%s 回复了您的评论", senderName);
                    if (notification.getCommentPreview() != null) {
                        baseText += "：" + notification.getCommentPreview();
                    }
                    break;
                    
                case 3: // 在评论中@了我
                    baseText = String.format("%s 在评论中@了您", senderName);
                    if (notification.getCommentPreview() != null) {
                        baseText += "：" + notification.getCommentPreview();
                    }
                    break;
                    
                case 4: // "回复 xxx :"格式回复了我
                    baseText = String.format("%s 回复了您", senderName);
                    if (notification.getCommentPreview() != null) {
                        baseText += "：" + notification.getCommentPreview();
                    }
                    break;
                    
                case 5: // 点赞了我的帖子
                    baseText = String.format("%s 点赞了您的帖子", senderName);
                    break;
                    
                case 6: // 点踩了我的帖子
                    baseText = String.format("%s 点踩了您的帖子", senderName);
                    break;
                    
                case 7: // 点赞了我的评论
                    baseText = String.format("%s 点赞了您的评论", senderName);
                    break;
                    
                case 8: // 点踩了我的评论
                    baseText = String.format("%s 点踩了您的评论", senderName);
                    break;
                    
                case 9: // 帖子被管理员删除
                    baseText = "您的帖子已被管理员删除";
                    break;
                    
                case 10: // 用户关注
                    baseText = String.format("%s 关注了您", senderName);
                    break;
                    
                case 11: // 升级通知
                    baseText = notification.getNotificationText() != null ? 
                        notification.getNotificationText() : "🎉 恭喜您升级！";
                    break;
                    
                default:
                    baseText = String.format("%s 与您发生了互动", senderName);
                    break;
            }
            
            notification.setNotificationText(baseText);
            
        } catch (Exception e) {
            log.error("生成通知文字失败，通知ID: {}", notification.getId(), e);
            notification.setNotificationText("您有新的通知");
        }
    }
}
