package com.itheima.sbbs.service;

import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.entity.User;
import com.itheima.sbbs.entity.Post;
import com.itheima.sbbs.entity.Comment;
import com.itheima.sbbs.mapper.NotificationMapper;
import com.itheima.sbbs.mapper.UserMapper;
import com.itheima.sbbs.mapper.PostMapper;
import com.itheima.sbbs.mapper.CommentMapper;
import com.itheima.sbbs.utils.SMSUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AsyncNotificationService {

    @Autowired
    private SMSUtils smsUtils;

    @Autowired
    private NotificationMapper notificationMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PostMapper postMapper;
    
    @Autowired
    private CommentMapper commentMapper;
    
    @Autowired
    private NotificationCacheService notificationCacheService;

    /**
     * 检查是否应该发送邮件通知
     * @param user 接收者用户信息
     * @param isLikeNotification 是否是点赞类通知
     * @return 是否应该发送邮件
     */
    private boolean shouldSendEmailNotification(User user, boolean isLikeNotification) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            return false; // 没有邮箱地址
        }
        
        if (isLikeNotification) {
            // 点赞类通知：检查点赞通知设置
            return Boolean.TRUE.equals(user.getEnableLikeNotification());
        } else {
            // 其他类通知：检查其他通知设置
            return Boolean.TRUE.equals(user.getEnableOtherNotification());
        }
    }

    @Async
    public void sendLikeNotification(User recipient, Integer senderId, Integer postId, Integer likeId) {
        log.info("异步发送帖子点赞通知和邮件给用户ID: {}, 发送者ID: {}, 帖子ID: {}", recipient.getId(), senderId, postId);
        try {
            Notification notification = new Notification();
            notification.setReceiverId(recipient.getId());
            notification.setSenderId(senderId);
            notification.setNotificationType(5); // 帖子被点赞
            notification.setRelatedId(postId);
            notification.setRelatedType("1"); // 1表示帖子
            notification.setRead(false);
            notification.setTriggerEntityId(likeId);
            notification.setTriggerEntityType(2); // 2表示点赞记录
            notificationMapper.insert(notification);
            
            // 检查是否应该发送邮件通知
            if (shouldSendEmailNotification(recipient, true)) {
                // 获取发送者信息和帖子标题
                User sender = userMapper.selectById(senderId);
                Post post = postMapper.selectById(postId);
                
                String senderUsername = sender != null ? sender.getUsername() : "某用户";
                String postTitle = post != null ? post.getTitle() : null;
                
                // 发送增强邮件通知
                smsUtils.sendEnhancedNotification(
                    recipient.getEmail(),
                    5, // 点赞帖子
                    senderUsername,
                    postTitle,
                    null
                );
                log.info("点赞邮件通知已发送，用户ID: {}", recipient.getId());
            } else {
                log.info("用户 {} 已关闭点赞邮件通知或无邮箱地址", recipient.getId());
            }
            
            // 清除通知列表缓存
            notificationCacheService.clearNotificationListCache(recipient.getId());
            
            log.info("帖子点赞通知发送成功 for user ID: {}", recipient.getId());
        } catch (Exception e) {
            log.error("异步发送帖子点赞通知失败 for user ID: {}: {}", recipient.getId(), e.getMessage());
        }
    }

    @Async
    public void sendDislikeNotification(User recipient, Integer senderId, Integer postId, Integer likeId) {
        log.info("异步发送帖子点踩通知和邮件给用户ID: {}, 发送者ID: {}, 帖子ID: {}", recipient.getId(), senderId, postId);
        try {
            Notification notification = new Notification();
            notification.setReceiverId(recipient.getId());
            notification.setSenderId(senderId);
            notification.setNotificationType(6); // 帖子被点踩
            notification.setRelatedId(postId);
            notification.setRelatedType("1"); // 1表示帖子
            notification.setRead(false);
            notification.setTriggerEntityId(likeId);
            notification.setTriggerEntityType(2); // 2表示点赞记录 (实际是点踩，但复用类型)
            notificationMapper.insert(notification);
            
            // 检查是否应该发送邮件通知
            if (shouldSendEmailNotification(recipient, true)) {
                // 获取发送者信息和帖子标题
                User sender = userMapper.selectById(senderId);
                Post post = postMapper.selectById(postId);
                
                String senderUsername = sender != null ? sender.getUsername() : "某用户";
                String postTitle = post != null ? post.getTitle() : null;
                
                // 发送增强邮件通知
                smsUtils.sendEnhancedNotification(
                    recipient.getEmail(),
                    6, // 点踩帖子
                    senderUsername,
                    postTitle,
                    null
                );
                log.info("点踩邮件通知已发送，用户ID: {}", recipient.getId());
            } else {
                log.info("用户 {} 已关闭点赞邮件通知或无邮箱地址", recipient.getId());
            }
            
            // 清除通知列表缓存
            notificationCacheService.clearNotificationListCache(recipient.getId());
            
            log.info("帖子点踩通知发送成功 for user ID: {}", recipient.getId());
        } catch (Exception e) {
            log.error("异步发送帖子点踩通知失败 for user ID: {}: {}", recipient.getId(), e.getMessage());
        }
    }

    @Async
    public void sendCommentLikeNotification(User recipient, Integer senderId, Integer commentId, Integer likeId) {
        log.info("异步发送评论点赞通知和邮件给用户ID: {}, 发送者ID: {}, 评论ID: {}", recipient.getId(), senderId, commentId);
        try {
            Notification notification = new Notification();
            notification.setReceiverId(recipient.getId());
            notification.setSenderId(senderId);
            notification.setNotificationType(7); // 评论被点赞
            notification.setRelatedId(commentId);
            notification.setRelatedType("2"); // 2表示评论
            notification.setRead(false);
            notification.setTriggerEntityId(likeId);
            notification.setTriggerEntityType(2); // 2表示点赞记录
            notificationMapper.insert(notification);
            
            // 检查是否应该发送邮件通知
            if (shouldSendEmailNotification(recipient, true)) {
                // 获取发送者信息和评论内容
                User sender = userMapper.selectById(senderId);
                Comment comment = commentMapper.selectById(commentId);
                
                String senderUsername = sender != null ? sender.getUsername() : "某用户";
                String commentPreview = comment != null ? 
                    com.itheima.sbbs.utils.NotificationUtils.extractCommentPreview(comment.getContent()) : null;
                
                // 发送增强邮件通知
                smsUtils.sendEnhancedNotification(
                    recipient.getEmail(),
                    7, // 点赞评论
                    senderUsername,
                    commentPreview,
                    null
                );
                log.info("评论点赞邮件通知已发送，用户ID: {}", recipient.getId());
            } else {
                log.info("用户 {} 已关闭点赞邮件通知或无邮箱地址", recipient.getId());
            }
            
            // 清除通知列表缓存
            notificationCacheService.clearNotificationListCache(recipient.getId());
            
            log.info("评论点赞通知发送成功 for user ID: {}", recipient.getId());
        } catch (Exception e) {
            log.error("异步发送评论点赞通知失败 for user ID: {}: {}", recipient.getId(), e.getMessage());
        }
    }

    @Async
    public void sendCommentDislikeNotification(User recipient, Integer senderId, Integer commentId, Integer likeId) {
        log.info("异步发送评论点踩通知和邮件给用户ID: {}, 发送者ID: {}, 评论ID: {}", recipient.getId(), senderId, commentId);
        try {
            Notification notification = new Notification();
            notification.setReceiverId(recipient.getId());
            notification.setSenderId(senderId);
            notification.setNotificationType(8); // 评论被点踩
            notification.setRelatedId(commentId);
            notification.setRelatedType("2"); // 2表示评论
            notification.setRead(false);
            notification.setTriggerEntityId(likeId);
            notification.setTriggerEntityType(3); // 3表示点踩记录
            notificationMapper.insert(notification);
            
            // 检查是否应该发送邮件通知
            if (shouldSendEmailNotification(recipient, true)) {
                // 获取发送者信息和评论内容
                User sender = userMapper.selectById(senderId);
                Comment comment = commentMapper.selectById(commentId);
                
                String senderUsername = sender != null ? sender.getUsername() : "某用户";
                String commentPreview = comment != null ? 
                    com.itheima.sbbs.utils.NotificationUtils.extractCommentPreview(comment.getContent()) : null;
                
                // 发送增强邮件通知
                smsUtils.sendEnhancedNotification(
                    recipient.getEmail(),
                    8, // 点踩评论
                    senderUsername,
                    commentPreview,
                    null
                );
                log.info("评论点踩邮件通知已发送，用户ID: {}", recipient.getId());
            } else {
                log.info("用户 {} 已关闭点赞邮件通知或无邮箱地址", recipient.getId());
            }
            
            // 清除通知列表缓存
            notificationCacheService.clearNotificationListCache(recipient.getId());
            
            log.info("评论点踩通知发送成功 for user ID: {}", recipient.getId());
        } catch (Exception e) {
            log.error("异步发送评论点踩通知失败 for user ID: {}: {}", recipient.getId(), e.getMessage());
        }
    }
} 