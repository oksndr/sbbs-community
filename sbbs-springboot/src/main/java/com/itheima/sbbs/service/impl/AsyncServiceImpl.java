 package com.itheima.sbbs.service.impl;

import com.itheima.sbbs.service.AsyncService;
import com.itheima.sbbs.service.NotificationService;
import com.itheima.sbbs.service.NotificationCacheService;
import com.itheima.sbbs.service.UserService;
import com.itheima.sbbs.utils.SMSUtils;
import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import java.util.HashSet;
import com.itheima.sbbs.mapper.CommentMapper;
import com.itheima.sbbs.mapper.PostMapper;
import com.itheima.sbbs.mapper.UserMapper;
import com.itheima.sbbs.entity.AuthorEmailDto;
import com.itheima.sbbs.entity.Comment;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Map;

@Slf4j
@Service
public class AsyncServiceImpl implements AsyncService {

    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private SMSUtils smsUtils;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PostMapper postMapper;
    
    @Autowired
    private CommentMapper commentMapper;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    @Autowired
    private com.itheima.sbbs.service.UserLevelService userLevelService;
    
    @Autowired
    private NotificationCacheService notificationCacheService;
    
    private static final String TOP_COMMENTS_CACHE_PREFIX = "topComments:";

    @Async("asyncExecutor")
    @Override
    public void processCommentNotifications(List<Notification> notifications, String title, String content, User recipient, Integer loginUserId) {
        try {
            // 保存通知到数据库
            if (!notifications.isEmpty()) {
                notificationService.saveBatch(notifications);
                log.info("异步保存 {} 条通知到数据库，发送者ID: {}", notifications.size(), loginUserId);
                
                // 清除接收者的通知缓存
                for (Notification notification : notifications) {
                    try {
                        notificationCacheService.clearNotificationListCache(notification.getReceiverId());
                        log.debug("已清除用户 {} 的通知缓存", notification.getReceiverId());
                    } catch (Exception e) {
                        log.error("清除用户 {} 通知缓存失败", notification.getReceiverId(), e);
                    }
                }
                
                // 发送增强邮件通知
                for (Notification notification : notifications) {
                    try {
                        // 查询接收者用户信息
                        User receiverUser = userMapper.selectById(notification.getReceiverId());
                        if (receiverUser == null || receiverUser.getEmail() == null || receiverUser.getEmail().trim().isEmpty()) {
                            continue;
                        }
                        
                        // 检查邮件通知设置
                        boolean isLikeNotification = notification.getNotificationType() >= 5 && notification.getNotificationType() <= 8;
                        boolean shouldSendEmail = isLikeNotification ? 
                            Boolean.TRUE.equals(receiverUser.getEnableLikeNotification()) :
                            Boolean.TRUE.equals(receiverUser.getEnableOtherNotification());
                            
                        if (!shouldSendEmail) {
                            log.info("用户 {} 已关闭邮件通知，类型: {}", receiverUser.getId(), notification.getNotificationType());
                            continue;
                        }
                        
                        // 查询发送者用户信息
                        User senderUser = userMapper.selectById(notification.getSenderId());
                        String senderUsername = senderUser != null ? senderUser.getUsername() : "某用户";
                        
                        // 获取相关信息
                        String relatedTitle = null;
                        String commentPreview = null;
                        
                        if (notification.getNotificationType() <= 4) {
                            // 评论相关通知
                            if ("1".equals(notification.getRelatedType())) {
                                // 帖子类型，获取帖子标题
                                AuthorEmailDto postInfo = postMapper.selectUserByPostId(notification.getRelatedId());
                                relatedTitle = postInfo != null ? postInfo.getTitle() : null;
                            } else if ("2".equals(notification.getRelatedType())) {
                                // 评论类型，获取评论内容预览
                                Comment parentComment = commentMapper.selectById(notification.getRelatedId());
                                if (parentComment != null) {
                                    relatedTitle = com.itheima.sbbs.utils.NotificationUtils.extractCommentPreview(parentComment.getContent());
                                }
                            }
                            
                            // 获取触发评论的内容预览
                            if (notification.getTriggerEntityId() != null) {
                                Comment triggerComment = commentMapper.selectById(notification.getTriggerEntityId());
                                if (triggerComment != null) {
                                    commentPreview = notification.getNotificationType() == 4 ?
                                        com.itheima.sbbs.utils.NotificationUtils.extractReplyContent(triggerComment.getContent()) :
                                        com.itheima.sbbs.utils.NotificationUtils.extractCommentPreview(triggerComment.getContent());
                                }
                            }
                        } else if (notification.getNotificationType() >= 5 && notification.getNotificationType() <= 8) {
                            // 点赞相关通知
                            if ("1".equals(notification.getRelatedType())) {
                                // 帖子点赞，获取帖子标题
                                AuthorEmailDto postInfo = postMapper.selectUserByPostId(notification.getRelatedId());
                                relatedTitle = postInfo != null ? postInfo.getTitle() : null;
                            } else if ("2".equals(notification.getRelatedType())) {
                                // 评论点赞，获取评论内容预览
                                Comment comment = commentMapper.selectById(notification.getRelatedId());
                                if (comment != null) {
                                    relatedTitle = com.itheima.sbbs.utils.NotificationUtils.extractCommentPreview(comment.getContent());
                                }
                            }
                        }
                        
                        // 发送增强邮件通知
                        smsUtils.sendEnhancedNotification(
                            receiverUser.getEmail(),
                            notification.getNotificationType(),
                            senderUsername,
                            relatedTitle,
                            commentPreview
                        );
                        
                        log.info("增强邮件通知已发送，接收者: {}, 类型: {}", receiverUser.getId(), notification.getNotificationType());
                        
                    } catch (Exception e) {
                        log.error("发送单个通知邮件失败，通知ID: {}", notification.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理评论通知时出错: {}", e.getMessage(), e);
        }
    }

    @Async("asyncExecutor")
    @Override
    public void updateUserExperience(Integer userId, Integer experience) {
        log.info("异步更新用户 {} 经验值 {}", userId, experience);
        userMapper.updateExperience(userId, experience);
    }

    @Override
    public void clearCommentCaches(Integer postId, Integer commentId, Integer pageNum) {
        try {
            if (commentId != null) {
                clearCommentCache(commentId);
            }
            
            Set<String> keys = new HashSet<>();
            keys.add(TOP_COMMENTS_CACHE_PREFIX + postId + ":" + pageNum);
            
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("同步清除评论相关缓存，键名数量: {}", keys.size());
            }
        } catch (Exception e) {
            log.error("清除评论缓存时出错: {}", e.getMessage(), e);
        }
    }

    @Override
    public void clearCommentCache(Integer commentId) {
        log.info("同步清除二级评论缓存，键名: replies:{}", commentId);
        String key = "replies:" + commentId;
        stringRedisTemplate.delete(key);
        log.info("成功删除二级评论缓存: {}", key);
    }

    @Async
    @Override
    public void updateCommentCounts(Integer postId, Integer commentId) {
        // 更新帖子评论数
        if (postId != null) {
            postMapper.incrementCommentCount(postId);
        }
        
        // 更新父评论回复数
        if (commentId != null) {
            commentMapper.incrementReplyCount(commentId);
        }
    }

    @Async
    @Override
    public void sendEmail(String email, String subject, String content) {
        log.info("异步发送邮件到: {}, 主题: {}", email, subject);
        // 这里可以添加实际的邮件发送逻辑
    }

    @Async
    @Override
    public void sendAtMentionEmail(String email, String postTitle) {
        try {
            log.info("异步发送@邮件通知到: {}, 帖子标题: {}", email, postTitle);
            smsUtils.sendAiteMessage(email, postTitle);
            log.info("@邮件通知发送成功: {}", email);
        } catch (Exception e) {
            log.error("发送@邮件通知失败，邮箱: {}，错误: {}", email, e.getMessage(), e);
        }
    }
    
    // 🗑️ **旧的异步经验值方法已删除，改为事件监听器处理**
    
    /**
     * 从数据库获取用户经验值
     */
    private Integer getUserExperienceFromDB(Integer userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }

        try {
            Map<String, Object> levelInfo = userService.getUserLevelInfo(userId);
            if (levelInfo != null && levelInfo.get("experience") != null) {
                Integer experience = (Integer) levelInfo.get("experience");
                return Math.max(0, experience);
            }
        } catch (Exception e) {
            log.error("从数据库获取用户经验值失败，用户ID: {}", userId, e);
        }

        return 0;
    }

    /**
     * 检查用户是否升级并发送通知
     */
    private void checkAndNotifyLevelUp(Integer userId, Integer oldExp, Integer newExp) {
        try {
            if (oldExp == null || newExp == null || oldExp.equals(newExp)) {
                return;
            }
            
            com.itheima.sbbs.entity.UserLevel oldLevel = userLevelService.getLevelByExperience(oldExp);
            com.itheima.sbbs.entity.UserLevel newLevel = userLevelService.getLevelByExperience(newExp);
            
            if (oldLevel != null && newLevel != null && newLevel.getLevel() > oldLevel.getLevel()) {
                log.info("🎉 用户 {} 升级！从 {} (等级{}) 升级为 {} (等级{})", 
                        userId, oldLevel.getName(), oldLevel.getLevel(), 
                        newLevel.getName(), newLevel.getLevel());
                
                sendLevelUpNotification(userId, oldLevel, newLevel);
            } else if (oldLevel != null && newLevel != null && newLevel.getLevel() < oldLevel.getLevel()) {
                log.info("📉 用户 {} 降级：从 {} (等级{}) 降为 {} (等级{})", 
                        userId, oldLevel.getName(), oldLevel.getLevel(), 
                        newLevel.getName(), newLevel.getLevel());
            }
            
        } catch (Exception e) {
            log.error("检查用户升级失败，用户ID: {}", userId, e);
        }
    }

    /**
     * 发送升级通知
     */
    private void sendLevelUpNotification(Integer userId, com.itheima.sbbs.entity.UserLevel oldLevel, com.itheima.sbbs.entity.UserLevel newLevel) {
        try {
            com.itheima.sbbs.entity.User user = userService.getById(userId);
            if (user == null) {
                log.warn("无法发送升级通知：用户不存在，用户ID: {}", userId);
                return;
            }
            
            String content = String.format("恭喜您从【%s】升级为【%s】！继续加油！", 
                                         oldLevel.getName(), newLevel.getName());
            
            // 发送站内信通知
            com.itheima.sbbs.entity.Notification notification = new com.itheima.sbbs.entity.Notification();
            notification.setReceiverId(userId);
            notification.setSenderId(0);
            notification.setNotificationType(com.itheima.sbbs.enums.NotificationType.LEVEL_UP.getCode());
            notification.setRelatedId(newLevel.getId());
            notification.setRelatedType("level");
            notification.setTriggerEntityId(userId);
            notification.setTriggerEntityType(1);
            notification.setRead(false);
            notification.setNotificationText(content);
            notification.setCreated(new java.util.Date());
            notification.setDeleted(0);
            
            notificationService.save(notification);
            log.info("✅ 升级站内信通知已发送，用户ID: {}", userId);
            
            // 检查是否应该发送邮件通知
            if (user.getEmail() != null && !user.getEmail().trim().isEmpty() 
                && Boolean.TRUE.equals(user.getEnableOtherNotification())) {
                
                String emailContent = String.format(
                    "恭喜您在sbbs论坛升级！\n\n" +
                    "您已从【%s】成功升级为【%s】！\n\n" +
                    "感谢您对sbbs论坛的支持，请继续保持活跃！\n\n" +
                    "sbbs论坛",
                    oldLevel.getName(), newLevel.getName()
                );
                
                smsUtils.sendLevelUpNotification(user.getEmail(), newLevel.getName(), emailContent);
                log.info("✅ 升级邮件通知已发送，用户ID: {}", userId);
            }
            
        } catch (Exception e) {
            log.error("发送升级通知失败，用户ID: {}", userId, e);
        }
    }
    
    /**
     * 监听经验值变化事件，异步处理经验值更新和升级检测
     */
    @Async("asyncExecutor")
    @EventListener
    public void handleExperienceChangeEvent(com.itheima.sbbs.service.impl.ExperienceServiceImpl.ExperienceChangeEvent event) {
        Integer userId = event.getUserId();
        Integer experienceChange = event.getExperienceChange();
        String operationType = event.getOperationType();
        
        if (userId == null || experienceChange == null) {
            log.warn("经验值变化事件参数无效，userId: {}, experienceChange: {}, operation: {}", 
                    userId, experienceChange, operationType);
            return;
        }

        try {
            log.info("🚀 开始处理经验值变化事件，用户: {}, 操作: {}, 经验值变化: {}", 
                    userId, operationType, experienceChange);
            
            // 🎉 获取经验值变化前的值，用于升级检测
            Integer oldExp = getUserExperienceFromDB(userId);
            
            boolean success;
            if (experienceChange > 0) {
                // 增加经验值
                success = userService.addUserExperience(userId, experienceChange);
            } else {
                // 减少经验值
                success = userService.reduceUserExperience(userId, Math.abs(experienceChange));
            }
            
            if (success) {
                // 🎉 检查是否升级
                Integer newExp = experienceChange > 0 ? 
                    oldExp + experienceChange : 
                    Math.max(0, oldExp + experienceChange); // experienceChange为负数
                    
                checkAndNotifyLevelUp(userId, oldExp, newExp);
                
                log.info("✅ 经验值变化事件处理完成，用户: {}, 操作: {}, 经验值变化: {} -> {}", 
                        userId, operationType, oldExp, newExp);
            } else {
                log.warn("❌ 经验值变化事件处理失败，用户: {}, 操作: {}", userId, operationType);
            }
            
        } catch (Exception e) {
            log.error("❌ 处理经验值变化事件失败，用户ID: {}, 操作: {}, 经验值变化: {}", 
                    userId, operationType, experienceChange, e);
        }
    }
} 