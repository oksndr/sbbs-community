package com.itheima.sbbs.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.sbbs.common.BussinessException;
import com.itheima.sbbs.entity.*;
import com.itheima.sbbs.mapper.CommentMapper;
import com.itheima.sbbs.mapper.LikeMapper;
import com.itheima.sbbs.mapper.PostMapper;
import com.itheima.sbbs.mapper.UserMapper;
import com.itheima.sbbs.service.CommentService;
import com.itheima.sbbs.service.LikeCacheService;
import com.itheima.sbbs.service.NotificationCacheService;
import com.itheima.sbbs.service.NotificationService;
import com.itheima.sbbs.service.AsyncService;
import com.itheima.sbbs.utils.SMSUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import java.util.HashSet;
import java.util.Set;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import org.springframework.beans.BeanUtils;
import java.util.HashMap;

@Slf4j
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    // 正则表达式 Pattern，静态 final 可以提高效率
    // 匹配 ^回复\s+ 用户名 \s*:
    private static final Pattern REPLY_PATTERN = Pattern.compile("^回复\\s+([a-zA-Z0-9_\\p{IsIdeographic}]+)\\s*:");
    // 正则表达式 Pattern，匹配 @用户名，与一级评论相同
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?:^|\\s)@([a-zA-Z0-9_\\p{IsIdeographic}]+)");
    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private PostMapper postMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private SMSUtils smsUtils;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate; // 注入 RedisTemplate
    @Autowired
    private LikeMapper likeMapper; // Inject LikeMapper
    @Autowired
    private LikeCacheService likeCacheService;
    
    @Autowired
    private NotificationCacheService notificationCacheService;
    
    @Autowired
    private com.itheima.sbbs.service.UserService userService;

    @Autowired
    private AsyncService asyncService;

    @Autowired
    private com.itheima.sbbs.service.PostService postService; // 添加PostService注入

    private static final String TOP_COMMENTS_CACHE_PREFIX = "topComments:";
    private static final String REPLIES_CACHE_PREFIX = "replies:"; // 二级评论缓存前缀
    private static final String USER_COMMENTS_CACHE_PREFIX = "userComments:"; // 用户评论缓存前缀
    private static final int DEFAULT_PAGE_SIZE = 15; // 默认页面大小，与Controller中保持一致

    /**
     * 发布一级评论
     *
     * @param comment
     * @return CommentLocationDto 包含评论ID和所在页码
     */
    @Override
    @Transactional
    public CommentLocationDto saveTopComment(Comment comment) {
        //获取评论人id
        int loginIdAsInt = StpUtil.getLoginIdAsInt();
        //存储到comment表中
        commentMapper.insert(comment);
        Integer commentId = comment.getId();//本条评论的id

        //发送email给post作者
        AuthorEmailDto authorEmailDto = postMapper.selectUserByPostId(comment.getPostId());
        
        //处理@的情况
        String commentContent = comment.getContent();
        List<String> mentionedUsernames = new ArrayList<>();                        //被@的用户列表
        Matcher matcher = MENTION_PATTERN.matcher(commentContent);
        while (matcher.find()) {
            mentionedUsernames.add(matcher.group(1));
        }

        //存储到通知表
        ArrayList<Notification> notifications = new ArrayList<>();
        
        // 添加帖子作者的通知（如果不是自己回复自己的帖子）
        if (authorEmailDto != null && authorEmailDto.getId() != loginIdAsInt) {
            Notification authorNoti = new Notification();
            authorNoti.setReceiverId(authorEmailDto.getId());
            authorNoti.setSenderId(loginIdAsInt);
            authorNoti.setNotificationType(1);
            authorNoti.setRelatedId(comment.getPostId());
            authorNoti.setRelatedType("1");
            authorNoti.setRead(false);
            authorNoti.setTriggerEntityId(commentId);
            authorNoti.setTriggerEntityType(1);
            notifications.add(authorNoti);
        }

        // 处理@用户的通知
        if (!mentionedUsernames.isEmpty()) {
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.in(User::getUsername, mentionedUsernames);
            List<User> users = userMapper.selectList(userWrapper);
            for (User user : users) {
                if (user.getId() != loginIdAsInt) { // 不给自己发通知
                    Notification notification = new Notification();
                    notification.setReceiverId(user.getId());
                    notification.setSenderId(loginIdAsInt);
                    notification.setNotificationType(3);
                    notification.setRelatedId(comment.getPostId()); // 设置为帖子ID
                    notification.setRelatedType("1");
                    notification.setTriggerEntityId(commentId);
                    notification.setTriggerEntityType(1);
                    notification.setRead(false);
                    notifications.add(notification);
                    
                    // @邮件通知将由AsyncService的processCommentNotifications方法统一处理
                    log.info("@用户通知已创建，邮件将由异步服务发送，用户名: {}", user.getUsername());
                }
            }
        }

        // 异步处理通知和邮件
        if (authorEmailDto != null) {
            // 查询完整的作者信息（包含邮箱和通知设置）
            User authorUser = userService.getById(authorEmailDto.getId());
            asyncService.processCommentNotifications(
                notifications,
                authorEmailDto.getTitle(),
                comment.getContent(),
                authorUser,
                loginIdAsInt
            );
        }

        // 更新帖子评论数
        postCommentCountPlus1(comment.getPostId());
        
        // 计算评论位置
        Integer pageNum = calculateCommentPage(comment.getPostId(), commentId);
        
        // 同步清除缓存，防止脏数据
        clearTopCommentsCacheByPostId(comment.getPostId());
        // 同步清除用户评论缓存
        clearUserCommentCache(loginIdAsInt);
        
        // 异步更新用户经验值
        asyncService.updateUserExperience(loginIdAsInt, 5);

        //异步清理相关用户通知缓存
        if (!notifications.isEmpty()) {
            // 清除所有相关用户的通知缓存
            for (Notification notification : notifications) {
                notificationCacheService.clearNotificationListCache(notification.getReceiverId());
            }
        }
        
        // 返回评论位置信息
        CommentLocationDto locationDto = new CommentLocationDto();
        locationDto.setCommentId(commentId);
        locationDto.setPage(pageNum);
        
        return locationDto;
    }
    
    /**
     * 计算评论所在页码
     * @param postId 帖子ID
     * @param commentId 评论ID
     * @return 页码，默认为1
     */
    private Integer calculateCommentPage(Integer postId, Integer commentId) {
        try {
            // 查询评论在帖子中的位置
            Integer position = commentMapper.findCommentPosition(postId, commentId);
            
            if (position == null || position <= 0) {
                log.warn("未找到评论 {} 在帖子 {} 中的位置，默认返回第1页", commentId, postId);
                return 1;
            }
            
            // 计算页码（向上取整）
            int pageSize = DEFAULT_PAGE_SIZE; // 使用常量，与Controller中保持一致
            int page = (position + pageSize - 1) / pageSize;
            log.info("新发布的评论 {} 在帖子 {} 中的位置是 {}，对应页码 {}", commentId, postId, position, page);
            return page;
        } catch (Exception e) {
            log.error("计算评论 {} 所在页码时出错: {}", commentId, e.getMessage(), e);
            return 1; // 出错时默认返回第1页
        }
    }

    /**
     * 发布二级评论
     *
     * @param comment
     */
    @Override
    @Transactional
    public void saveSndComment(Comment comment) {
        int loginIdAsInt = StpUtil.getLoginIdAsInt();
        
        // 1. 验证父评论是否存在且未被删除
        Comment parentComment = getBaseMapper().selectById(comment.getParentId());
        if (parentComment == null || parentComment.getDeleted() == 1 || !parentComment.getPostId().equals(comment.getPostId())) {
            throw new BussinessException(ErrorResult.builder()
                    .status("00007")
                    .message("父评论不存在或已被删除")
                    .build());
        }
        
        // 2. 保存评论
        commentMapper.insert(comment);
        Integer commentId = comment.getId();
        
        // 3. 处理回复格式和@提及
        String commentContent = comment.getContent();
        List<Notification> notifications = new ArrayList<>();
        
        // 检查是否是回复特定用户
        Matcher replyMatcher = REPLY_PATTERN.matcher(commentContent);
        if (replyMatcher.find()) {
            String replyToUsername = replyMatcher.group(1);
            log.info("检测到回复特定用户格式，被回复用户: {}, 评论者: {}", replyToUsername, loginIdAsInt);
            
            // 查找被回复的用户
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(User::getUsername, replyToUsername);
            User replyToUser = userMapper.selectOne(userWrapper);
            
            if (replyToUser != null && replyToUser.getId() != loginIdAsInt) {
                // 创建回复通知
                Notification replyNotification = new Notification();
                replyNotification.setReceiverId(replyToUser.getId());
                replyNotification.setSenderId(loginIdAsInt);
                replyNotification.setNotificationType(4); // "回复 xxx :"格式
                replyNotification.setRelatedId(comment.getParentId()); // 设置为父评论ID
                replyNotification.setRelatedType("2");
                replyNotification.setTriggerEntityId(commentId);
                replyNotification.setTriggerEntityType(1);
                replyNotification.setRead(false);
                notifications.add(replyNotification);
                
                // 回复邮件通知将由AsyncService的processCommentNotifications方法统一处理
                log.info("回复用户通知已创建，邮件将由异步服务发送，用户名: {}", replyToUser.getUsername());
            } else {
                log.info("用户回复自己，跳过创建通知，用户ID: {}", loginIdAsInt);
            }
            
            log.info("检测到回复特定用户格式，跳过给一级评论作者发送通知");
        } else if (parentComment.getUserId() != loginIdAsInt) {
            // 如果不是回复特定用户，且不是自己回复自己，则通知父评论作者
            Notification notification = new Notification();
            notification.setReceiverId(parentComment.getUserId());
            notification.setSenderId(loginIdAsInt);
            notification.setNotificationType(2);
            notification.setRelatedId(comment.getParentId()); // 设置为父评论ID
            notification.setRelatedType("2");
            notification.setTriggerEntityId(commentId);
            notification.setTriggerEntityType(1);
            notification.setRead(false);
            notifications.add(notification);
        }
        
        // 处理@提及
        Matcher mentionMatcher = MENTION_PATTERN.matcher(commentContent);//被@的用户列表
        while (mentionMatcher.find()) {
            String username = mentionMatcher.group(1);
            if (username != null && !username.isEmpty()) {
                LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
                userWrapper.eq(User::getUsername, username);
                User mentionedUser = userMapper.selectOne(userWrapper);
                
                if (mentionedUser != null && mentionedUser.getId() != loginIdAsInt) {
                    Notification notification = new Notification();
                    notification.setReceiverId(mentionedUser.getId());
                    notification.setSenderId(loginIdAsInt);
                    notification.setNotificationType(3); // @在评论中
                    notification.setRelatedId(comment.getPostId()); // 设置为帖子ID
                    notification.setRelatedType("1");
                    notification.setTriggerEntityId(commentId);
                    notification.setTriggerEntityType(1);
                    notification.setRead(false);
                    notifications.add(notification);
                    
                    // @邮件通知将由AsyncService的processCommentNotifications方法统一处理
                    log.info("@用户通知已创建，邮件将由异步服务发送，用户名: {}", mentionedUser.getUsername());
                }
            }
        }
        
        // 4. 更新计数器
        postCommentCountPlus1(comment.getPostId());
        commentReplyCountPlus1(comment.getParentId());
        
        // 5. 异步处理通知和缓存
        if (!notifications.isEmpty()) {
            asyncService.processCommentNotifications(
                notifications,
                null, // 二级评论不需要发送邮件
                null,
                null, // 二级评论不需要传递接收者User对象
                loginIdAsInt
            );
            
            // 清除所有相关通知用户的通知缓存
            for (Notification notification : notifications) {
                notificationCacheService.clearNotificationListCache(notification.getReceiverId());
            }
        } else {
            log.info("没有需要保存的通知，评论ID: {}, 评论者ID: {}", commentId, loginIdAsInt);
        }
        
        // 同步清除二级评论的缓存，防止脏数据
        clearRepliesCache(comment.getParentId());
        
        // 同步清除一级评论页面缓存，防止脏数据
        Integer parentCommentPage = findCommentPage(comment.getPostId(), comment.getParentId(), DEFAULT_PAGE_SIZE);
        if (parentCommentPage != null) {
            clearTopCommentsCacheByPostId(comment.getPostId());
        }
        
        // 清除用户评论缓存
        clearUserCommentCache(loginIdAsInt);
        
        // 异步更新用户经验值
        asyncService.updateUserExperience(loginIdAsInt, 5);
    }
    
    /**
     * 查找评论所在的页码
     * 工具类代码 private
     * @param postId 帖子ID
     * @param commentId 评论ID
     * @param pageSize 页大小
     * @return 页码，如果找不到则返回null
     */
    private Integer findCommentPage(Integer postId, Integer commentId, Integer pageSize) {
        try {
            // 查询评论在帖子中的位置
            Integer position = commentMapper.findCommentPosition(postId, commentId);
            
            if (position == null || position <= 0) {
                log.warn("未找到评论 {} 在帖子 {} 中的位置", commentId, postId);
                return null;
            }
            
            // 计算页码（向上取整）
            int page = (position + pageSize - 1) / pageSize;
            log.info("评论 {} 在帖子 {} 中的位置是 {}，对应页码 {}", commentId, postId, position, page);
            return page;
        } catch (Exception e) {
            log.error("查找评论 {} 所在页码时出错: {}", commentId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 传入lastId(comment)
     * post id
     * 帖子详情界面加载顶级评论
     * @return
     */
    @Override // 确保覆盖接口方法
    public CommentDto getTopComments(Integer postId, Integer lastId, Integer pageSize) { // 移除 lastUpdated 参数
        String cacheKey = TOP_COMMENTS_CACHE_PREFIX + postId + ":" + lastId + ":" + pageSize;
        CommentDto cachedCommentsDto = (CommentDto) redisTemplate.opsForValue().get(cacheKey);

        if (cachedCommentsDto != null) {
            // Populate like/dislike status for cached comments if user is logged in
            if (StpUtil.isLogin()) {
                int currentUserId = StpUtil.getLoginIdAsInt();
                List<CommentWithUserDto> commentsWithStatus = cachedCommentsDto.getComments().stream().map(comment -> {
                    populateLikeStatusForComment(comment, currentUserId);
                    return comment;
                }).collect(Collectors.toList());
                cachedCommentsDto.setComments(commentsWithStatus);
            }
            return cachedCommentsDto;
        }

        List<CommentWithUserDto> comments = commentMapper.selectTopCommentsWithUser(postId, lastId, pageSize);

        if (StpUtil.isLogin()) {
            int currentUserId = StpUtil.getLoginIdAsInt();
            comments.forEach(comment -> populateLikeStatusForComment(comment, currentUserId));
        }

        CommentDto commentDto = new CommentDto();
        commentDto.setComments(comments);

        if (!comments.isEmpty()) {
            // 获取最后一页的最后一个评论的ID作为下一页的游标
            CommentWithUserDto lastComment = comments.get(comments.size() - 1);
            commentDto.setLastId(lastComment.getId());
        } else {
             // 如果当前页没有评论，下一页的lastId应该还是当前页的lastId
             commentDto.setLastId(lastId);
        }

        // 将结果存入缓存，设置过期时间（例如 10 分钟）
        redisTemplate.opsForValue().set(cacheKey, commentDto, 10, TimeUnit.MINUTES);

        return commentDto;
    }

    /**
     * 批量填充评论的点赞状态（🔥 极速优化版 + 性能监控）
     * @param comments 评论列表
     * @param userId 当前用户ID
     */
    private void batchPopulateLikeStatusForComments(List<CommentWithUserDto> comments, int userId) {
        if (comments == null || comments.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("⏱️ 开始批量查询 {} 条评论的点赞状态，用户ID: {}", comments.size(), userId);

        try {
            // 步骤1：提取评论ID
            long step1Start = System.currentTimeMillis();
            List<Integer> commentIds = comments.stream()
                .map(CommentWithUserDto::getId)
                .collect(Collectors.toList());
            long step1Time = System.currentTimeMillis() - step1Start;
            log.info("⏱️ 步骤1-提取评论ID完成，耗时: {}ms", step1Time);

            // 步骤2：批量查询点赞状态
            long step2Start = System.currentTimeMillis();
            Map<Integer, Map<String, Boolean>> likeStatusMap = 
                likeCacheService.batchGetCommentLikeStatus(userId, commentIds);
            long step2Time = System.currentTimeMillis() - step2Start;
            log.info("⏱️ 步骤2-批量查询点赞状态完成，耗时: {}ms", step2Time);

            // 步骤3：设置评论状态
            long step3Start = System.currentTimeMillis();
            for (CommentWithUserDto comment : comments) {
                Map<String, Boolean> status = likeStatusMap.get(comment.getId());
                if (status != null) {
                    comment.setIsLiked(status.get("liked"));
                    comment.setIsDisliked(status.get("disliked"));
                } else {
                    comment.setIsLiked(false);
                    comment.setIsDisliked(false);
                }
            }
            long step3Time = System.currentTimeMillis() - step3Start;
            log.info("⏱️ 步骤3-设置评论状态完成，耗时: {}ms", step3Time);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("⏱️ ✅ 批量查询点赞状态总耗时: {}ms（步骤1:{}ms + 步骤2:{}ms + 步骤3:{}ms）", 
                    totalTime, step1Time, step2Time, step3Time);

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("⏱️ ❌ 批量查询点赞状态失败，总耗时: {}ms，错误: {}", totalTime, e.getMessage(), e);
            // 发生错误时，将所有评论的点赞状态设置为false
            comments.forEach(comment -> {
                comment.setIsLiked(false);
                comment.setIsDisliked(false);
            });
        }
    }

    /**
     * 按页码获取一级评论列表
     * 使用MyBatis-Plus的分页功能
     * @param postId 帖子ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @return 包含评论列表的DTO
     */
    @Override
    public CommentDto getTopCommentsByPage(Integer postId, Integer pageNum, Integer pageSize) {
        // 构建缓存键
        String cacheKey = TOP_COMMENTS_CACHE_PREFIX + postId + ":page:" + pageNum + ":" + pageSize;
        
        try {
            // 🚀 简化缓存检查：直接获取缓存值
            CommentDto cachedCommentsDto = (CommentDto) redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedCommentsDto != null) {
                log.debug("缓存命中，从缓存返回评论列表，帖子ID: {}, 页码: {}, 评论数量: {}", 
                        postId, pageNum, cachedCommentsDto.getComments() != null ? cachedCommentsDto.getComments().size() : 0);
                
                // 🚀 简化缓存续期：根据页码设置不同过期时间
                int expireTime = pageNum == 1 ? 60 : 30; // 第一页1小时，其他页30分钟
                redisTemplate.expire(cacheKey, expireTime, TimeUnit.MINUTES);
                
                // 如果用户已登录，填充点赞状态
                if (StpUtil.isLogin()) {
                    int currentUserId = StpUtil.getLoginIdAsInt();
                    batchPopulateLikeStatusForComments(cachedCommentsDto.getComments(), currentUserId);
                }
                return cachedCommentsDto;
            }
        } catch (Exception e) {
            log.warn("从Redis获取缓存时发生错误: {}", e.getMessage());
        }

        log.debug("缓存未命中，从数据库查询评论列表，帖子ID: {}, 页码: {}", postId, pageNum);
        
        // 🚀 优化：先检查评论总数，避免无意义的分页查询
        long dbQueryStart = System.currentTimeMillis();
        
        // 快速检查是否有评论
        LambdaQueryWrapper<Comment> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Comment::getPostId, postId)
                   .isNull(Comment::getParentId)
                   .eq(Comment::getDeleted, 0);
        long totalComments = this.count(countWrapper);
        
        CommentDto commentDto = new CommentDto();
        
        if (totalComments == 0) {
            // 没有评论，直接返回空结果
            commentDto.setComments(new ArrayList<>());
            commentDto.setTotal(0);
            commentDto.setCurrent(pageNum);
            commentDto.setSize(pageSize);
            commentDto.setPages(0);
            commentDto.setHasNext(false);
            commentDto.setHasPrevious(false);
            
            long dbQueryTime = System.currentTimeMillis() - dbQueryStart;
            log.debug("⏱️ 帖子无评论，快速返回，耗时: {}ms", dbQueryTime);
            
            // 🚀 缓存空结果，避免重复查询
            try {
                redisTemplate.opsForValue().set(cacheKey, commentDto, 10, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("缓存空评论结果失败: {}", e.getMessage());
            }
            
            return commentDto;
        }
        
        // 执行分页查询
        Page<CommentWithUserDto> page = new Page<>(pageNum, pageSize);
        Page<CommentWithUserDto> resultPage = commentMapper.selectTopCommentsByPage(page, postId);
        List<CommentWithUserDto> comments = resultPage.getRecords();
        
        long dbQueryTime = System.currentTimeMillis() - dbQueryStart;
        log.debug("⏱️ 数据库查询完成，耗时: {}ms，查询到 {} 条评论", dbQueryTime, comments.size());
        
        // 如果用户登录，填充点赞状态
        if (StpUtil.isLogin()) {
            long likeStatusStart = System.currentTimeMillis();
            int currentUserId = StpUtil.getLoginIdAsInt();
            batchPopulateLikeStatusForComments(comments, currentUserId);
            long likeStatusTime = System.currentTimeMillis() - likeStatusStart;
            log.debug("⏱️ 点赞状态填充完成，耗时: {}ms", likeStatusTime);
        }

        // 构造返回对象
        commentDto.setComments(comments);
        commentDto.setTotal((int) resultPage.getTotal());
        commentDto.setCurrent((int) resultPage.getCurrent());
        commentDto.setSize((int) resultPage.getSize());
        commentDto.setPages((int) resultPage.getPages());
        commentDto.setHasNext(resultPage.hasNext());
        commentDto.setHasPrevious(resultPage.hasPrevious());

        // 🚀 简化缓存写入：无需验证，直接写入
        try {
            int expireTime = pageNum == 1 ? 60 : 30; // 第一页1小时，其他页30分钟
            redisTemplate.opsForValue().set(cacheKey, commentDto, expireTime, TimeUnit.MINUTES);
            log.debug("将评论列表存入缓存，帖子ID: {}, 页码: {}, 评论数量: {}", postId, pageNum, comments.size());
        } catch (Exception e) {
            log.warn("将评论列表存入Redis缓存时发生错误: {}", e.getMessage());
        }

        return commentDto;
    }

    /**
     * 接收post Id
     * 对应的post comment_count + 1
     */
    private void postCommentCountPlus1(Integer postId) {
        LambdaUpdateWrapper<Post> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Post::getId, postId)
                .setSql("comment_count = comment_count + 1")
                .setSql("updated = CURRENT_TIMESTAMP"); // 更新活跃时间，使帖子浮到前面
        postMapper.update(null, updateWrapper);
        
        // 清除帖子详情缓存，确保评论计数同步
        postService.clearPostDetailCache(postId);
        log.debug("帖子评论数增加并清除帖子详情缓存，帖子ID: {}", postId);
    }

    /**
     * 接收comment Id
     * 对应的comment reply_count + 1
     */
    private void commentReplyCountPlus1(Integer commentId) {
        LambdaUpdateWrapper<Comment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Comment::getId, commentId)
                .setSql("reply_count = reply_count + 1");
        commentMapper.update(null, updateWrapper);
    }

    // 实现获取二级评论的方法
    @Override
    public List<CommentWithUserDto> getRepliesByCommentId(Integer commentId) {
        String cacheKey = REPLIES_CACHE_PREFIX + commentId;
        
        try {
            // 🚀 简化缓存检查：直接获取缓存值
            List<CommentWithUserDto> cachedReplies = (List<CommentWithUserDto>) redisTemplate.opsForValue().get(cacheKey);

            if (cachedReplies != null) {
                log.debug("缓存命中，从缓存返回二级评论列表，评论ID: {}, 评论数量: {}", commentId, cachedReplies.size());
                
                // 🚀 简化缓存续期：固定30分钟
                redisTemplate.expire(cacheKey, 30, TimeUnit.MINUTES);
                
                // 如果用户已登录，填充点赞状态
                if (StpUtil.isLogin()) {
                    int currentUserId = StpUtil.getLoginIdAsInt();
                    batchPopulateLikeStatusForComments(cachedReplies, currentUserId);
                }
                return cachedReplies;
            }
        } catch (Exception e) {
            log.warn("从Redis获取二级评论缓存时发生错误: {}", e.getMessage());
        }

        log.debug("缓存未命中，从数据库查询二级评论列表，评论ID: {}", commentId);
        
        // 从数据库查询
        List<CommentWithUserDto> replies = commentMapper.selectRepliesByParentId(commentId);

        // 如果用户已登录，填充点赞状态
        if (StpUtil.isLogin()) {
            int currentUserId = StpUtil.getLoginIdAsInt();
            batchPopulateLikeStatusForComments(replies, currentUserId);
        }

        // 🚀 简化缓存写入：无需验证，直接写入
        try {
            redisTemplate.opsForValue().set(cacheKey, replies, 30, TimeUnit.MINUTES);
            log.debug("将二级评论列表存入缓存，评论ID: {}, 数量: {}", commentId, replies.size());
        } catch (Exception e) {
            log.warn("将二级评论列表存入Redis缓存时发生错误: {}", e.getMessage());
        }

        return replies;
    }

    private void populateLikeStatusForComment(CommentWithUserDto comment, int userId) {
        try {
            // 优先从Redis缓存获取点赞状态
            boolean liked = likeCacheService.isCommentLiked(userId, comment.getId());
            boolean disliked = likeCacheService.isCommentDisliked(userId, comment.getId());

            comment.setIsLiked(liked);
            comment.setIsDisliked(disliked);

            log.debug("从缓存获取评论点赞状态，用户ID: {}, 评论ID: {}, 点赞: {}, 点踩: {}",
                     userId, comment.getId(), liked, disliked);

        } catch (Exception e) {
            log.warn("从缓存获取评论点赞状态失败，回退到数据库查询，用户ID: {}, 评论ID: {}",
                    userId, comment.getId(), e);

            // 缓存失败时回退到数据库查询
            LambdaQueryWrapper<Like> likeQuery = new LambdaQueryWrapper<>();
            likeQuery.eq(Like::getCommentId, comment.getId()).eq(Like::getUserId, userId);
            Like like = likeMapper.selectOne(likeQuery);

            if (like != null) {
                comment.setIsLiked(like.getType() == 1);
                comment.setIsDisliked(like.getType() == -1);
            } else {
                comment.setIsLiked(false);
                comment.setIsDisliked(false);
            }
        }
    }

    // 覆盖 removeById 方法，在删除评论后清除缓存
    @Override
    public boolean removeById(java.io.Serializable id) {
        // 需要先获取评论的 postId 和 parentId，以便清除对应帖子的缓存
        Comment comment = getById(id);
        boolean success = super.removeById(id);

        if (success && comment != null) { // 确保 comment 不为 null
            // 安全地判断是否为一级评论（parent_id 为 null 或 0）
            if ((comment.getParentId() == null || comment.getParentId() == 0) && comment.getPostId() != null) {
                // 如果删除的是一级评论，清除对应帖子下所有一级评论缓存
                clearTopCommentsCacheByPostId(comment.getPostId());
                // 同时，清除这个一级评论下的二级评论缓存
                clearRepliesCache(comment.getId()); 
            } else if (comment.getParentId() != null && comment.getParentId() != 0) {
                // 如果删除的是二级评论，清除其父评论对应的二级评论缓存
                clearRepliesCache(comment.getParentId()); 
            }
        }
        return success;
    }

    // 辅助方法：清除对应帖子下所有一级评论缓存
    @Override
    public void clearTopCommentsCacheByPostId(Integer postId) {
        log.info("开始清除帖子ID为 {} 的一级评论缓存", postId);
        
        try {
            // 清除浮标式缓存
            String floatPattern = TOP_COMMENTS_CACHE_PREFIX + postId + ":*";
            log.info("浮标式缓存键匹配模式: {}", floatPattern);
            
            // 清除分页式缓存
            String pagePattern = TOP_COMMENTS_CACHE_PREFIX + postId + ":page:*";
            log.info("分页式缓存键匹配模式: {}", pagePattern);
            
            Set<String> keys = new HashSet<>();
            
            // 收集匹配的浮标式缓存键
            try {
                Set<String> floatKeys = redisTemplate.execute((RedisConnection connection) -> {
                    Set<String> matchingKeys = new HashSet<>();
                    try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(floatPattern).count(1000).build())) {
                        while (cursor.hasNext()) {
                            String key = new String(cursor.next());
                            matchingKeys.add(key);
                            log.info("找到匹配的浮标式缓存键: {}", key);
                        }
                    }
                    return matchingKeys;
                });
                if (floatKeys != null) {
                    keys.addAll(floatKeys);
                }
            } catch (Exception e) {
                log.error("扫描浮标式缓存键时发生错误: {}", e.getMessage(), e);
            }
            
            // 收集匹配的分页式缓存键
            try {
                Set<String> pageKeys = redisTemplate.execute((RedisConnection connection) -> {
                    Set<String> matchingKeys = new HashSet<>();
                    try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pagePattern).count(1000).build())) {
                        while (cursor.hasNext()) {
                            String key = new String(cursor.next());
                            matchingKeys.add(key);
                            log.info("找到匹配的分页式缓存键: {}", key);
                        }
                    }
                    return matchingKeys;
                });
                if (pageKeys != null) {
                    keys.addAll(pageKeys);
                }
            } catch (Exception e) {
                log.error("扫描分页式缓存键时发生错误: {}", e.getMessage(), e);
            }
            
            if (!keys.isEmpty()) {
                log.info("正在删除 {} 个缓存键", keys.size());
                Long deletedCount = redisTemplate.delete(keys);
                log.info("成功删除帖子ID为 {} 的一级评论缓存，删除键数量: {}", postId, deletedCount);
            } else {
                log.info("未找到帖子ID为 {} 的一级评论缓存键", postId);
            }
        } catch (Exception e) {
            log.error("清除帖子ID为 {} 的一级评论缓存时发生错误: {}", postId, e.getMessage(), e);
        }
    }

    // 辅助方法：清除二级评论缓存
    @Override
    public void clearRepliesCache(Integer commentId) {
        String cacheKey = REPLIES_CACHE_PREFIX + commentId;
        log.info("清除二级评论缓存，键名: {}", cacheKey);
        
        try {
            Boolean hasKey = redisTemplate.hasKey(cacheKey);
            if (Boolean.TRUE.equals(hasKey)) {
                Boolean deleted = redisTemplate.delete(cacheKey);
                if (Boolean.TRUE.equals(deleted)) {
                    log.info("成功删除二级评论缓存: {}", cacheKey);
                } else {
                    log.warn("删除二级评论缓存失败: {}", cacheKey);
                }
            } else {
                log.info("二级评论缓存不存在: {}", cacheKey);
            }
        } catch (Exception e) {
            log.error("清除二级评论缓存时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 智能清除评论相关缓存
     * 对于一级评论：清除其所在页面的缓存
     * 对于二级评论：清除其父评论的二级评论缓存
     */
    @Override
    public void clearCommentPageCache(Integer commentId) {
        try {
            // 查询评论信息
            Comment comment = getById(commentId);
            if (comment == null) {
                log.warn("评论 {} 不存在，无法清除缓存", commentId);
                return;
            }
            
            Integer parentId = comment.getParentId();
            if (parentId == null || parentId == 0) {
                // 一级评论：清除其所在页面的缓存
                log.info("清除一级评论 {} 所在页面的缓存", commentId);
                
                Integer pageSize = DEFAULT_PAGE_SIZE;
                Integer commentPage = findCommentPage(comment.getPostId(), commentId, pageSize);
                
                if (commentPage != null) {
                    String cacheKey = TOP_COMMENTS_CACHE_PREFIX + comment.getPostId() + ":page:" + commentPage + ":" + pageSize;
                    
                    Boolean hasKey = redisTemplate.hasKey(cacheKey);
                    if (Boolean.TRUE.equals(hasKey)) {
                        Boolean deleted = redisTemplate.delete(cacheKey);
                        log.info("清除一级评论所在页面缓存键 {}: {}", cacheKey, deleted);
                    } else {
                        // 尝试模糊匹配删除
                        String pattern = TOP_COMMENTS_CACHE_PREFIX + comment.getPostId() + ":page:" + commentPage + ":*";
                        Set<String> keys = redisTemplate.execute((RedisConnection connection) -> {
                            Set<String> matchingKeys = new HashSet<>();
                            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                                while (cursor.hasNext()) {
                                    String key = new String(cursor.next());
                                    matchingKeys.add(key);
                                }
                            }
                            return matchingKeys;
                        });
                        
                        if (keys != null && !keys.isEmpty()) {
                            Long deletedCount = redisTemplate.delete(keys);
                            log.info("通过模糊匹配删除了 {} 个一级评论页面缓存键", deletedCount);
                        }
                    }
                } else {
                    log.warn("未找到一级评论 {} 所在页码，将清除帖子 {} 的所有一级评论缓存", commentId, comment.getPostId());
                    clearTopCommentsCacheByPostId(comment.getPostId());
                }
            } else {
                // 二级评论：清除其父评论的二级评论缓存
                log.info("清除二级评论 {} 的父评论 {} 缓存", commentId, parentId);
                clearRepliesCache(parentId);
            }
        } catch (Exception e) {
            log.error("清除评论 {} 页面缓存时发生错误: {}", commentId, e.getMessage(), e);
        }
    }

    // 实现用户删除自己的评论方法
    @Override
    @Transactional
    public boolean deleteCommentByUserId(Integer commentId, Integer userId) {
        // 1. 检查评论是否存在且未被删除
        Comment comment = getById(commentId);
        if (comment == null || comment.getDeleted() == 1) {
            // 评论不存在或已被删除
            return false;
        }

        // 2. 检查当前用户是否是评论的作者
        if (!comment.getUserId().equals(userId)) {
            // 不是作者，无权删除
            return false;
        }

        // 3. 如果是一级评论（parent_id = 0 或 null），需要级联删除所有二级评论
        if (comment.getParentId() == null || comment.getParentId() == 0) {
            // 删除所有以此评论为父评论的二级评论
            LambdaQueryWrapper<Comment> repliesWrapper = new LambdaQueryWrapper<>();
            repliesWrapper.eq(Comment::getParentId, commentId)
                          .eq(Comment::getDeleted, 0); // 只查询未删除的评论
            List<Comment> replies = list(repliesWrapper);
            
            if (!replies.isEmpty()) {
                // 批量逻辑删除二级评论
                List<Integer> replyIds = replies.stream()
                                               .map(Comment::getId)
                                               .collect(java.util.stream.Collectors.toList());
                
                // 使用批量更新将所有二级评论标记为已删除
                LambdaUpdateWrapper<Comment> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.in(Comment::getId, replyIds)
                            .set(Comment::getDeleted, 1)
                            .set(Comment::getUpdated, java.time.LocalDateTime.now());
                update(updateWrapper);
                
                log.info("删除一级评论 {} 时，级联删除了 {} 条二级评论", commentId, replyIds.size());
            }
        }

        // 4. 删除主评论（一级或二级）
        boolean success = removeById(commentId); // 调用已有的逻辑删除方法，会处理相关缓存

        // 🚀 清除评论作者的用户缓存（评论数量统计已改变）
        if (success && comment.getUserId() != null) {
            userService.clearUserCache(comment.getUserId());
        }

        // 5. 更新帖子评论计数
        if (success && comment.getPostId() != null) {
            // 计算需要减少的评论数（主评论 + 被级联删除的二级评论数）
            int decrementCount = 1; // 主评论本身
            if (comment.getParentId() == null || comment.getParentId() == 0) {
                // 如果删除的是一级评论，还要加上被级联删除的二级评论数
                LambdaQueryWrapper<Comment> repliesCountWrapper = new LambdaQueryWrapper<>();
                repliesCountWrapper.eq(Comment::getParentId, commentId)
                                  .eq(Comment::getDeleted, 1); // 统计刚刚被删除的评论
                long deletedRepliesCount = count(repliesCountWrapper);
                decrementCount += (int) deletedRepliesCount;
            }
            
            // 更新帖子的评论计数
            LambdaUpdateWrapper<Post> postUpdateWrapper = new LambdaUpdateWrapper<>();
            postUpdateWrapper.eq(Post::getId, comment.getPostId())
                            .setSql("comment_count = comment_count - " + decrementCount)
                            .setSql("updated = CURRENT_TIMESTAMP"); // 删除评论也算活跃，更新时间
            postMapper.update(null, postUpdateWrapper);
        }

        return success;
    }

    @Override
    @Transactional
    public CommentWithUserDto addComment(Integer postId, Integer parentId, String content, Integer loginUserId) {
        // 创建评论
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(loginUserId);
        comment.setParentId(parentId);
        comment.setContent(content);
        commentMapper.insert(comment);

        // 异步更新计数
        asyncService.updateCommentCounts(postId, parentId);
        
        // 异步更新经验值
        asyncService.updateUserExperience(loginUserId, 5);
        
        // 如果是二级评论，同步清除缓存，防止脏数据
        if (parentId != null) {
            clearRepliesCache(parentId);
            clearTopCommentsCacheByPostId(postId);
        } else {
            // 如果是一级评论，同步清除缓存，防止脏数据
            clearTopCommentsCacheByPostId(postId);
        }

        // 返回评论信息
        return commentMapper.selectCommentWithUser(comment.getId());
    }

    /**
     * 分页获取用户发布的评论
     * @param userId 用户ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小，默认15
     * @return 评论列表
     */
    @Override
    public List<UserCommentDto> getUserComments(Integer userId, Integer pageNum, Integer pageSize) {
        // 参数校验
        if (userId == null || userId <= 0) {
            throw new BussinessException(ErrorResult.numError());
        }
        
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        
        if (pageSize == null || pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        
        // 缓存键
        String cacheKey = USER_COMMENTS_CACHE_PREFIX + userId + ":" + pageNum + ":" + pageSize;
        
        // 尝试从缓存中获取
        List<UserCommentDto> cachedComments = (List<UserCommentDto>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedComments != null) {
            log.info("从缓存中获取用户评论列表, userId={}, pageNum={}, pageSize={}", userId, pageNum, pageSize);
            return cachedComments;
        }
        
        // 缓存未命中，从数据库查询
        log.info("缓存未命中，从数据库查询用户评论列表, userId={}, pageNum={}, pageSize={}", userId, pageNum, pageSize);
        
        // 构建查询条件
        Page<Comment> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Comment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Comment::getUserId, userId)
                   .eq(Comment::getDeleted, 0) // 非删除状态
                   .orderByDesc(Comment::getUpdated); // 按更新时间倒序排序
        
        // 执行分页查询
        IPage<Comment> commentPage = commentMapper.selectPage(page, queryWrapper);
        
        // 转换为UserCommentDto列表
        List<UserCommentDto> resultList = new ArrayList<>();
        
        if (commentPage.getRecords() != null && !commentPage.getRecords().isEmpty()) {
            // 查询用户信息（只查询一次）
            User user = userMapper.selectById(userId);
            
            // 收集所有评论关联的帖子ID
            Set<Integer> postIds = commentPage.getRecords().stream()
                    .map(Comment::getPostId)
                    .collect(Collectors.toSet());
            
            // 批量查询帖子标题
            Map<Integer, String> postTitles = new HashMap<>();
            if (!postIds.isEmpty()) {
                LambdaQueryWrapper<Post> postWrapper = new LambdaQueryWrapper<>();
                postWrapper.select(Post::getId, Post::getTitle)
                          .in(Post::getId, postIds);
                List<Post> posts = postMapper.selectList(postWrapper);
                
                for (Post post : posts) {
                    postTitles.put(post.getId(), post.getTitle());
                }
            }
            
            // 批量转换评论对象
            for (Comment comment : commentPage.getRecords()) {
                String postTitle = postTitles.getOrDefault(comment.getPostId(), "未知帖子");
                UserCommentDto dto = UserCommentDto.fromComment(comment, user, postTitle);
                resultList.add(dto);
            }
            
            // 缓存结果，设置30分钟过期
            redisTemplate.opsForValue().set(cacheKey, resultList, 30, TimeUnit.MINUTES);
        }
        
        return resultList;
    }
    
    /**
     * 清除用户评论列表缓存
     * @param userId 用户ID
     */
    @Override
    public void clearUserCommentCache(Integer userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        
        log.info("清除用户评论列表缓存, userId={}", userId);
        
        try {
            // 查找并删除所有分页缓存
            String pattern = USER_COMMENTS_CACHE_PREFIX + userId + ":*";
            Set<String> keys = scanKeys(pattern);
            
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已删除用户评论缓存键, count={}", keys.size());
            }
        } catch (Exception e) {
            log.error("清除用户评论缓存失败, userId={}", userId, e);
        }
    }
    
    // 如果还没有scanKeys方法，需要添加
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try {
            redisTemplate.execute((RedisConnection connection) -> {
                try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next()));
                    }
                } catch (Exception e) {
                    log.error("Redis scan keys error", e);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Redis scan keys execution error", e);
        }
        return keys;
    }

    /**
     * 根据评论ID获取帖子ID和页码信息
     * @param commentId 评论ID
     * @return 包含帖子ID和页码的位置信息
     */
    @Override
    public CommentLocationDto getCommentLocation(Integer commentId) {
        if (commentId == null || commentId <= 0) {
            throw new BussinessException(ErrorResult.numError());
        }
        
        try {
            // 先查询评论基本信息
            Comment comment = this.getById(commentId);
            if (comment == null || comment.getDeleted() == 1) {
                throw new BussinessException(ErrorResult.numError());
            }
            
            CommentLocationDto locationDto = new CommentLocationDto();
            
            // 如果是二级评论，需要找到父评论的位置
            if (comment.getParentId() != null && comment.getParentId() > 0) {
                Comment parentComment = this.getById(comment.getParentId());
                if (parentComment == null || parentComment.getDeleted() == 1) {
                    throw new BussinessException(ErrorResult.numError());
                }
                
                // 使用父评论的位置信息
                Integer pageNumber = findCommentPage(parentComment.getPostId(), parentComment.getId(), DEFAULT_PAGE_SIZE);
                locationDto.setCommentId(commentId);
                locationDto.setPage(pageNumber != null ? pageNumber : 1);
                locationDto.setPostId(parentComment.getPostId());
                locationDto.setParentCommentId(comment.getParentId());
            } else {
                // 一级评论，直接计算位置
                Integer pageNumber = findCommentPage(comment.getPostId(), commentId, DEFAULT_PAGE_SIZE);
                locationDto.setCommentId(commentId);
                locationDto.setPage(pageNumber != null ? pageNumber : 1);
                locationDto.setPostId(comment.getPostId());
                locationDto.setParentCommentId(null);
            }
            
            return locationDto;
        } catch (BussinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取评论位置信息失败, commentId={}", commentId, e);
            throw new BussinessException(ErrorResult.numError());
        }
    }
}
