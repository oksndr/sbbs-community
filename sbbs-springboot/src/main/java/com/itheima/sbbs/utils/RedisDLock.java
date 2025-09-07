package com.itheima.sbbs.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis分布式锁工具类
 * 
 * 特性：
 * 1. 使用UUID防止误解锁
 * 2. 自动过期避免死锁
 * 3. 原子操作保证线程安全
 * 4. 异常处理增强稳定性
 * 
 * 使用场景：
 * - 限量商品购买
 * - 防止重复操作
 * - 分布式环境下的并发控制
 */
@Slf4j
@Component
public class RedisDLock {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 加锁（使用默认15秒过期时间）
     * @param lockName 锁名称
     * @param uuid 锁标识（建议使用IdUtil.getSnowflakeNextId()）
     * @return 是否获取锁成功
     */
    public Boolean lock(String lockName, Long uuid) {
        return lock(lockName, uuid, 15);
    }

    /**
     * 加锁（自定义过期时间）
     * @param lockName 锁名称
     * @param uuid 锁标识
     * @param expireSeconds 过期时间（秒）
     * @return 是否获取锁成功
     */
    public Boolean lock(String lockName, Long uuid, Integer expireSeconds) {
        try {
            if (lockName == null || uuid == null) {
                log.warn("分布式锁参数不能为空，lockName: {}, uuid: {}", lockName, uuid);
                return false;
            }
            
            if (expireSeconds == null || expireSeconds <= 0) {
                expireSeconds = 15; // 默认15秒
            }
            
            Boolean res = redisTemplate.opsForValue().setIfAbsent(
                lockName, uuid.toString(), expireSeconds.longValue(), TimeUnit.SECONDS);
            
            boolean success = res != null && res;
            if (success) {
                log.debug("获取分布式锁成功，lockName: {}, uuid: {}, expireSeconds: {}", 
                         lockName, uuid, expireSeconds);
            } else {
                log.debug("获取分布式锁失败，lockName: {}, uuid: {}", lockName, uuid);
            }
            
            return success;
        } catch (Exception e) {
            log.error("获取分布式锁异常，lockName: {}, uuid: {}", lockName, uuid, e);
            return false;
        }
    }

    /**
     * 释放锁（原子操作版本）
     * 使用Lua脚本确保检查和删除的原子性，避免并发问题
     * @param lockName 锁名称
     * @param uuid 锁标识
     * @return 是否释放成功
     */
    public Boolean unlock(String lockName, Long uuid) {
        try {
            if (lockName == null || uuid == null) {
                log.warn("分布式锁参数不能为空，lockName: {}, uuid: {}", lockName, uuid);
                return false;
            }
            
            // Lua脚本：原子性地检查锁拥有者并删除
            String luaScript = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";
            
            Long result = redisTemplate.execute(
                new DefaultRedisScript<>(luaScript, Long.class),
                Collections.singletonList(lockName),
                uuid.toString()
            );
            
            boolean success = result != null && result > 0;
            if (success) {
                log.debug("释放分布式锁成功，lockName: {}, uuid: {}", lockName, uuid);
            } else {
                log.debug("释放分布式锁失败（可能已过期或不是锁拥有者），lockName: {}, uuid: {}", 
                         lockName, uuid);
            }
            
            return success;
        } catch (Exception e) {
            log.error("释放分布式锁异常，lockName: {}, uuid: {}", lockName, uuid, e);
            return false;
        }
    }

    /**
     * 尝试获取锁（带重试机制）
     * @param lockName 锁名称
     * @param uuid 锁标识
     * @param expireSeconds 锁过期时间（秒）
     * @param maxRetryTimes 最大重试次数
     * @param retryIntervalMs 重试间隔（毫秒）
     * @return 是否获取锁成功
     */
    public Boolean tryLock(String lockName, Long uuid, Integer expireSeconds, 
                          Integer maxRetryTimes, Integer retryIntervalMs) {
        if (maxRetryTimes == null || maxRetryTimes <= 0) {
            maxRetryTimes = 3;
        }
        if (retryIntervalMs == null || retryIntervalMs <= 0) {
            retryIntervalMs = 100;
        }
        
        for (int i = 0; i < maxRetryTimes; i++) {
            if (lock(lockName, uuid, expireSeconds)) {
                return true;
            }
            
            // 最后一次重试失败就不等待了
            if (i < maxRetryTimes - 1) {
                try {
                    Thread.sleep(retryIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("分布式锁重试被中断，lockName: {}", lockName);
                    break;
                }
            }
        }
        
        log.warn("分布式锁重试 {} 次后仍然失败，lockName: {}, uuid: {}", maxRetryTimes, lockName, uuid);
        return false;
    }
}
