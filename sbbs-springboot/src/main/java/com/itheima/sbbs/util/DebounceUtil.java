package com.itheima.sbbs.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 防抖工具类
 * 用于防止用户频繁操作（如快速点击点赞按钮）
 */
@Slf4j
@Component
public class DebounceUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 防抖锁前缀
     */
    private static final String DEBOUNCE_LOCK_PREFIX = "debounce:lock:";

    /**
     * 尝试获取防抖锁
     * @param key 锁的唯一标识
     * @param timeoutSeconds 锁的超时时间（秒）
     * @return true表示获取锁成功，可以执行操作；false表示操作过于频繁，应该拒绝
     */
    public boolean tryLock(String key, long timeoutSeconds) {
        String lockKey = DEBOUNCE_LOCK_PREFIX + key;

        try {
            // 使用 setIfAbsent 实现分布式锁
            Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", timeoutSeconds, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(success)) {
                
                log.debug("防抖锁获取成功: {}", lockKey);
                return true;
            } else {
                log.info("防抖锁获取失败，操作过于频繁: {}", lockKey);
                return false;
            }
        } catch (Exception e) {
            log.error("防抖锁操作异常: {}", e.getMessage(), e);
            // 异常情况下允许操作，避免因为Redis问题影响正常业务
            return true;
        }
    }

    /**
     * 释放防抖锁（通常不需要手动释放，依靠TTL自动过期）
     * @param key 锁的唯一标识
     */
    public void releaseLock(String key) {
        String lockKey = DEBOUNCE_LOCK_PREFIX + key;
        try {
            redisTemplate.delete(lockKey);
            log.debug("防抖锁释放成功: {}", lockKey);
        } catch (Exception e) {
            log.error("防抖锁释放异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 生成点赞操作的防抖键
     * @param userId 用户ID
     * @param targetType 目标类型（post/comment）
     * @param targetId 目标ID
     * @param action 操作类型（like/unlike/dislike/undislike）
     * @return 防抖键
     */
    public String generateLikeKey(Integer userId, String targetType, Integer targetId, String action) {
        return String.format("like:%s:%s:%d:%d", action, targetType, targetId, userId);
    }

    /**
     * 生成评论操作的防抖键
     * @param userId 用户ID
     * @param postId 帖子ID
     * @return 防抖键
     */
    public String generateCommentKey(Integer userId, Integer postId) {
        return String.format("comment:post:%d:%d", postId, userId);
    }

    /**
     * 生成发帖操作的防抖键
     * @param userId 用户ID
     * @return 防抖键
     */
    public String generatePostKey(Integer userId) {
        return String.format("post:create:%d", userId);
    }
}
