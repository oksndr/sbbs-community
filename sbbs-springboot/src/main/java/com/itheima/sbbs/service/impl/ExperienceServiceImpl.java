package com.itheima.sbbs.service.impl;

import com.itheima.sbbs.service.ExperienceService;
import com.itheima.sbbs.service.UserService;
import com.itheima.sbbs.service.UserLevelService;
import com.itheima.sbbs.service.NotificationService;
import com.itheima.sbbs.entity.UserLevel;
import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.enums.NotificationType;
import com.itheima.sbbs.utils.SMSUtils;
import com.itheima.sbbs.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ExperienceServiceImpl implements ExperienceService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserLevelService userLevelService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private SMSUtils smsUtils;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // Redis key 常量
    // 🗑️ **已删除经验值队列 - 改为直接数据库操作**
    // private static final String EXP_QUEUE_KEY = "experience:queue";
    // 🗑️ **已删除经验值缓存 - 改为直接数据库查询**
    // private static final String USER_EXP_CACHE_PREFIX = "user_exp:";
    // private static final long USER_EXP_CACHE_EXPIRE_HOURS = 6;
    private static final String DAILY_FIRST_POST_PREFIX = "daily_first_post:";
    private static final String DAILY_FIRST_LIKE_PREFIX = "daily_first_like:";

    // 经验值配置
    private static final int POST_EXP = 10;              // 发帖经验
    private static final int FIRST_POST_EXP = 20;        // 首次发帖额外经验
    private static final int COMMENT_EXP = 5;            // 评论经验
    private static final int POST_LIKE_EXP = 3;          // 帖子被赞经验
    private static final int COMMENT_LIKE_EXP = 2;       // 评论被赞经验
    private static final int POST_DISLIKE_EXP = -1;      // 帖子被踩扣除经验
    private static final int FIRST_LIKE_EXP = 1;         // 每天首次点赞经验

    @Override
    public void addPostExperience(Integer userId) {
        if (userId == null || userId <= 0) {
            log.warn("添加发帖经验值失败：用户ID无效 {}", userId);
            return;
        }

        log.debug("开始处理用户 {} 的发帖经验值", userId);
        try {
            // 检查是否是首次发帖
            boolean isFirstPost = checkIfFirstPost(userId);
            int expToAdd = isFirstPost ? (POST_EXP + FIRST_POST_EXP) : POST_EXP;

            log.debug("用户 {} 发帖经验值计算：基础经验={}, 首次发帖={}, 总经验={}",
                     userId, POST_EXP, isFirstPost, expToAdd);

            // 🚀发布经验值变化事件，异步处理
            eventPublisher.publishEvent(new ExperienceChangeEvent(userId, expToAdd, "发帖" + (isFirstPost ? "(首次)" : "")));
            log.info("✅ 用户 {} 发帖经验值事件已发布", userId);

            log.info("✅ 用户 {} 发帖获得 {} 经验值 (首次发帖: {}) (异步处理中)", userId, expToAdd, isFirstPost);

        } catch (Exception e) {
            log.error("❌ 添加发帖经验值失败，用户ID: {}", userId, e);
        }
    }

    @Override
    public void addCommentExperience(Integer userId) {
        if (userId == null || userId <= 0) {
            return;
        }

        try {
            // 🚀 **发布经验值变化事件，异步处理**
            eventPublisher.publishEvent(new ExperienceChangeEvent(userId, COMMENT_EXP, "评论"));
            
            log.debug("✅ 用户 {} 评论获得 {} 经验值（异步处理中）", userId, COMMENT_EXP);
            
        } catch (Exception e) {
            log.error("❌ 添加评论经验值失败，用户ID: {}", userId, e);
        }
    }

    @Override
    public void addPostLikeExperience(Integer authorId) {
        if (authorId == null || authorId <= 0) {
            return;
        }

        try {
            // 🚀 **发布经验值变化事件，异步处理**
            eventPublisher.publishEvent(new ExperienceChangeEvent(authorId, POST_LIKE_EXP, "帖子被赞"));
            
            log.debug("✅ 用户 {} 帖子被赞获得 {} 经验值（异步处理中）", authorId, POST_LIKE_EXP);
            
        } catch (Exception e) {
            log.error("❌ 添加帖子点赞经验值失败，用户ID: {}", authorId, e);
        }
    }

    @Override
    public void addCommentLikeExperience(Integer authorId) {
        if (authorId == null || authorId <= 0) {
            return;
        }

        try {
            // 🚀 **发布经验值变化事件，异步处理**
            eventPublisher.publishEvent(new ExperienceChangeEvent(authorId, COMMENT_LIKE_EXP, "评论被赞"));
            
            log.debug("✅ 用户 {} 评论被赞获得 {} 经验值（异步处理中）", authorId, COMMENT_LIKE_EXP);
            
        } catch (Exception e) {
            log.error("❌ 添加评论点赞经验值失败，用户ID: {}", authorId, e);
        }
    }

    @Override
    public void reducePostDislikeExperience(Integer authorId) {
        if (authorId == null || authorId <= 0) {
            return;
        }

        try {
            // 🚀 **发布经验值变化事件，异步处理**
            eventPublisher.publishEvent(new ExperienceChangeEvent(authorId, POST_DISLIKE_EXP, "帖子被踩"));

            log.debug("✅ 用户 {} 帖子被踩扣除 {} 经验值（异步处理中）", authorId, Math.abs(POST_DISLIKE_EXP));

        } catch (Exception e) {
            log.error("❌ 扣除帖子点踩经验值失败，用户ID: {}", authorId, e);
        }
    }

    @Override
    public void addFirstLikeExperience(Integer userId) {
        if (userId == null || userId <= 0) {
            log.warn("添加首次点赞经验值失败：用户ID无效 {}", userId);
            return;
        }

        log.debug("开始检查用户 {} 的首次点赞经验值", userId);
        try {
            // 检查是否是今天首次点赞
            boolean isFirstLike = checkIfFirstLike(userId);
            if (!isFirstLike) {
                log.debug("用户 {} 今天已经点过赞了，跳过首次点赞经验值", userId);
                return; // 今天已经点过赞了，不给经验值
            }

            log.debug("用户 {} 今天首次点赞，准备添加 {} 经验值", userId, FIRST_LIKE_EXP);

            // 🚀 **发布经验值变化事件，异步处理**
            eventPublisher.publishEvent(new ExperienceChangeEvent(userId, FIRST_LIKE_EXP, "首次点赞"));
            log.debug("✅ 用户 {} 首次点赞经验值事件已发布", userId);

            log.info("🎉 用户 {} 今日首次点赞获得 {} 经验值（异步处理中）", userId, FIRST_LIKE_EXP);

        } catch (Exception e) {
            log.error("❌ 添加首次点赞经验值失败，用户ID: {}", userId, e);
        }
    }

    @Override
    public int calculatePostExperience(Integer userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }

        try {
            // 检查是否是今天首次发帖（不实际标记）
            boolean isFirstPost = checkIfFirstPostWithoutMarking(userId);
            return isFirstPost ? (POST_EXP + FIRST_POST_EXP) : POST_EXP;
        } catch (Exception e) {
            log.warn("计算发帖经验值失败，用户ID: {}", userId, e);
            return POST_EXP; // 返回基础经验值
        }
    }

    @Override
    public int calculateCommentExperience(Integer userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        return COMMENT_EXP; // 评论经验值是固定的
    }

    @Override
    public int calculateFirstLikeExperience(Integer userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }

        try {
            // 检查是否是今天首次点赞（不实际标记）
            boolean isFirstLike = checkIfFirstLikeWithoutMarking(userId);
            return isFirstLike ? FIRST_LIKE_EXP : 0;
        } catch (Exception e) {
            log.warn("计算首次点赞经验值失败，用户ID: {}", userId, e);
            return 0;
        }
    }

    /**
     * 🗑️ **已删除定时处理经验值队列 - 改为直接数据库操作**
     * 原方法：每2分钟执行一次，批量处理Redis队列
     * 新方案：立即写入数据库，无需队列和定时任务
     */
    @Override
    public void processExperienceQueue() {
        // 🚀 **队列机制已废弃，所有经验值操作直接写入数据库**
        log.info("💡 经验值队列已废弃，所有操作直接写入数据库，无需定时处理");
    }

    @Override
    public Integer getUserExperience(Integer userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }

        try {
            // 直接从数据库获取经验值
            Map<String, Object> levelInfo = userService.getUserLevelInfo(userId);
            
            if (levelInfo != null && levelInfo.get("experience") != null) {
                Integer experience = (Integer) levelInfo.get("experience");
                
                // 确保经验值有效
                if (experience < 0) {
                    log.warn("用户 {} 的经验值为负数，重置为0: {}", userId, experience);
                    experience = 0;
                }
                
                log.debug("从数据库获取用户 {} 的经验值: {}", userId, experience);
                return experience;
            }

        } catch (Exception e) {
            log.error("获取用户经验值失败，用户ID: {}", userId, e);
        }

        log.warn("无法获取用户 {} 的经验值，返回默认值0", userId);
        return 0;
    }

    /**
     * 检查是否是今天首次发帖
     */
    private boolean checkIfFirstPost(Integer userId) {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String key = DAILY_FIRST_POST_PREFIX + userId + ":" + today;

            // 检查今天是否已经发过帖
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                log.debug("用户 {} 今天已经发过帖了，不是今日首次发帖", userId);
                return false; // 今天已经发过帖了
            }

            // 标记今天已发帖，设置过期时间到明天凌晨
            long secondsUntilMidnight = getSecondsUntilMidnight();
            redisTemplate.opsForValue().set(key, "1", secondsUntilMidnight, TimeUnit.SECONDS);

            log.debug("用户 {} 今天首次发帖，将获得首次发帖奖励", userId);
            return true; // 今天首次发帖

        } catch (Exception e) {
            log.warn("检查首次发帖失败，用户ID: {}", userId, e);
            return false;
        }
    }

    /**
     * 检查是否是今天首次点赞
     */
    private boolean checkIfFirstLike(Integer userId) {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String key = DAILY_FIRST_LIKE_PREFIX + userId + ":" + today;

            // 检查今天是否已经点过赞
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                return false; // 今天已经点过赞了
            }

            // 标记今天已点赞，设置过期时间到明天凌晨
            long secondsUntilMidnight = getSecondsUntilMidnight();
            redisTemplate.opsForValue().set(key, "1", secondsUntilMidnight, TimeUnit.SECONDS);

            return true; // 今天首次点赞

        } catch (Exception e) {
            log.warn("检查首次点赞失败，用户ID: {}", userId, e);
            return false;
        }
    }

    /**
     * 计算到明天凌晨0点的秒数
     */
    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return java.time.Duration.between(now, midnight).getSeconds();
    }

    /**
     * 检查是否是今天首次发帖（不实际标记）
     */
    private boolean checkIfFirstPostWithoutMarking(Integer userId) {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String key = DAILY_FIRST_POST_PREFIX + userId + ":" + today;

            // 只检查今天是否已经发过帖，不进行标记
            Boolean exists = redisTemplate.hasKey(key);
            return !Boolean.TRUE.equals(exists); // 如果不存在，说明是首次发帖

        } catch (Exception e) {
            log.warn("检查首次发帖失败，用户ID: {}", userId, e);
            return false;
        }
    }

    /**
     * 检查是否是今天首次点赞（不实际标记）
     */
    private boolean checkIfFirstLikeWithoutMarking(Integer userId) {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String key = DAILY_FIRST_LIKE_PREFIX + userId + ":" + today;

            // 只检查今天是否已经点过赞，不进行标记
            Boolean exists = redisTemplate.hasKey(key);
            return !Boolean.TRUE.equals(exists); // 如果不存在，说明是首次点赞

        } catch (Exception e) {
            log.warn("检查首次点赞失败，用户ID: {}", userId, e);
            return false;
        }
    }

    // 🗑️ **已删除经验值缓存相关方法 - 改为直接数据库查询**
    // updateUserExpCache() 和 convertToInteger() 方法已移除

    /**
     * 🎉 检查用户是否升级并发送通知
     * @param userId 用户ID
     * @param oldExp 经验值变化前的经验值
     * @param newExp 经验值变化后的经验值
     */
    @Override
    public void checkAndNotifyLevelUp(Integer userId, Integer oldExp, Integer newExp) {
        try {
            // 🛡️ 安全检查：确保经验值有效
            if (oldExp == null || newExp == null || oldExp.equals(newExp)) {
                return; // 经验值无变化或无效，无需检查
            }
            
            // 获取变化前后的等级
            UserLevel oldLevel = userLevelService.getLevelByExperience(oldExp);
            UserLevel newLevel = userLevelService.getLevelByExperience(newExp);
            
            // 🎯 只在真正升级时发送通知
            if (oldLevel != null && newLevel != null && newLevel.getLevel() > oldLevel.getLevel()) {
                log.info("🎉 用户 {} 升级！从 {} (等级{}) 升级为 {} (等级{})", 
                        userId, oldLevel.getName(), oldLevel.getLevel(), 
                        newLevel.getName(), newLevel.getLevel());
                
                // 发送升级通知
                sendLevelUpNotification(userId, oldLevel, newLevel);
            } else if (oldLevel != null && newLevel != null && newLevel.getLevel() < oldLevel.getLevel()) {
                // 🔽 降级情况：记录日志但不发送通知
                log.info("📉 用户 {} 降级：从 {} (等级{}) 降为 {} (等级{})", 
                        userId, oldLevel.getName(), oldLevel.getLevel(), 
                        newLevel.getName(), newLevel.getLevel());
            } else {
                // 🔹 同级变化：记录调试日志
                log.debug("用户 {} 经验值变化但等级未变：{}({}) -> {}({})", 
                         userId, oldExp, oldLevel != null ? oldLevel.getLevel() : "未知", 
                         newExp, newLevel != null ? newLevel.getLevel() : "未知");
            }
            
        } catch (Exception e) {
            log.error("检查用户升级失败，用户ID: {}", userId, e);
        }
    }

    /**
     * 🎉 发送升级通知（站内信+邮件）
     * @param userId 用户ID
     * @param oldLevel 旧等级
     * @param newLevel 新等级
     */
    private void sendLevelUpNotification(Integer userId, UserLevel oldLevel, UserLevel newLevel) {
        try {
            // 🚀 一次查询获取用户完整信息（包括邮箱和通知设置）
            com.itheima.sbbs.entity.User user = userService.getById(userId);
            if (user == null) {
                log.warn("无法发送升级通知：用户不存在，用户ID: {}", userId);
                return;
            }
            
            // 构建通知内容
            String title = "🎉 恭喜升级！";
            
            // 计算下一级信息
            String nextLevelInfo = "";
            if (newLevel.getMaxExperience() != null) {
                // 还有下一级
                UserLevel nextLevel = getNextLevel(newLevel.getLevel());
                if (nextLevel != null) {
                    Map<String, Object> levelInfo = userService.getUserLevelInfo(userId);
                    Integer expNeeded = (Integer) levelInfo.get("expNeededForNextLevel");
                    nextLevelInfo = String.format("，距离下一级【%s】还需要%d经验值", nextLevel.getName(), expNeeded);
                }
            } else {
                nextLevelInfo = "，您已达到最高等级！";
            }
            
            String content = String.format("恭喜您从【%s】升级为【%s】！%s继续加油！", 
                                         oldLevel.getName(), newLevel.getName(), nextLevelInfo);
            
            // 发送站内信通知
            Notification notification = new Notification();
            notification.setReceiverId(userId);
            notification.setSenderId(0); // 系统通知  
            notification.setNotificationType(NotificationType.LEVEL_UP.getCode()); // 🎉 升级通知
            notification.setRelatedId(newLevel.getId());
            notification.setRelatedType("level");
            notification.setTriggerEntityId(userId);
            notification.setTriggerEntityType(1); // 用户类型
            notification.setRead(false);
            notification.setNotificationText(content); // 通知内容
            notification.setCreated(new java.util.Date());
            notification.setDeleted(0);
            
            notificationService.save(notification);
            log.info("✅ 升级站内信通知已发送，用户ID: {}", userId);
            
            // 检查是否应该发送邮件通知（使用已查询的用户信息）
            if (user.getEmail() != null && !user.getEmail().trim().isEmpty() 
                && Boolean.TRUE.equals(user.getEnableOtherNotification())) {
                
                String emailContent = String.format(
                    "恭喜您在sbbs论坛升级！\n\n" +
                    "您已从【%s】成功升级为【%s】！%s\n\n" +
                    "感谢您对sbbs论坛的支持，请继续保持活跃！\n\n" +
                    "sbbs论坛",
                    oldLevel.getName(), newLevel.getName(), nextLevelInfo
                );
                
                // 异步发送邮件
                smsUtils.sendLevelUpNotification(user.getEmail(), newLevel.getName(), emailContent);
                log.info("✅ 升级邮件通知已异步发送，用户ID: {}", userId);
            } else {
                log.info("用户 {} 已关闭其他邮件通知或无邮箱地址，跳过升级邮件发送", userId);
            }
            
        } catch (Exception e) {
            log.error("发送升级通知失败，用户ID: {}", userId, e);
        }
    }

    /**
     * 获取下一级等级信息
     */
    private UserLevel getNextLevel(Integer currentLevel) {
        try {
            java.util.List<UserLevel> allLevels = userLevelService.getAllLevels();
            return allLevels.stream()
                    .filter(level -> level.getLevel() == currentLevel + 1)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("获取下一级等级失败，当前等级: {}", currentLevel, e);
            return null;
        }
    }

    /**
     * 经验值变化事件
     */
    public static class ExperienceChangeEvent {
        private final Integer userId;
        private final Integer experienceChange;
        private final String operationType;

        public ExperienceChangeEvent(Integer userId, Integer experienceChange, String operationType) {
            this.userId = userId;
            this.experienceChange = experienceChange;//发布事件时请使用已经定义好的常量
            this.operationType = operationType;//用于标识是哪种情况导致了experience / balance发生变化
        }

        public Integer getUserId() { return userId; }
        public Integer getExperienceChange() { return experienceChange; }
        public String getOperationType() { return operationType; }
    }
} 