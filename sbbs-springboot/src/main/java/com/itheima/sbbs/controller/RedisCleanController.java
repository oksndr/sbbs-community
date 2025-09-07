package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.itheima.sbbs.service.NotificationCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis缓存清理控制器
 * 提供手动清理各种缓存的接口，仅管理员可用
 */
@RestController
@RequestMapping("/api/admin/cache")
@Slf4j
public class RedisCleanController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private NotificationCacheService notificationCacheService;

    /**
     * 根据模式清除Redis缓存
     * @param pattern 缓存键模式，例如 "post*" 将清除所有以post开头的缓存
     * @return 清除的缓存键数量
     */
    @DeleteMapping("/clear")
    // 移除SaIgnore注解，采用公开访问方式，但要求请求必须来自本地
    public int clearCache(@RequestParam String pattern, @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedIp,
                          @RequestHeader(value = "X-Real-IP", required = false) String realIp,
                          @RequestHeader(value = "Host", required = false) String host) {
        
        // 仅允许本地请求访问此API，作为简单的安全措施
        String clientIp = realIp != null ? realIp : (forwardedIp != null ? forwardedIp : "unknown");
        if (!isLocalRequest(clientIp, host)) {
            log.warn("非本地请求尝试清除缓存，IP: {}, Host: {}", clientIp, host);
            return -1; // 非本地请求返回错误码
        }
        
        Set<String> keys = scanKeys(pattern);
        if (keys != null && !keys.isEmpty()) {
            log.info("清除Redis缓存，模式: {}, 键数量: {}", pattern, keys.size());
            redisTemplate.delete(keys);
            return keys.size();
        }
        return 0;
    }

    /**
     * 检查是否为本地请求
     */
    private boolean isLocalRequest(String ip, String host) {
        return "127.0.0.1".equals(ip) || "localhost".equals(host) || 
               host != null && host.startsWith("localhost:") || 
               "0:0:0:0:0:0:0:1".equals(ip);
    }

    /**
     * 扫描匹配的Redis键
     */
    private Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisConnection connection) -> {
            Set<String> matchingKeys = new HashSet<>();
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                while (cursor.hasNext()) {
                    matchingKeys.add(new String(cursor.next()));
                }
            } catch (Exception e) {
                log.error("扫描Redis键出错: {}", e.getMessage(), e);
            }
            return matchingKeys;
        }, true);
    }

    /**
     * 批量清除多个用户的通知列表缓存
     * @param userIds 用户ID列表
     * @return 操作结果
     */
    @PostMapping("/notification/batch")
    @SaCheckRole("admin")
    public SaResult batchClearNotificationCache(@RequestBody List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return SaResult.error("用户ID列表不能为空");
        }
        
        try {
            notificationCacheService.batchClearNotificationListCache(userIds);
            log.info("管理员批量清除 {} 个用户的通知缓存", userIds.size());
            return SaResult.ok("已清除" + userIds.size() + "个用户的通知缓存");
        } catch (Exception e) {
            log.error("批量清除通知缓存失败", e);
            return SaResult.error("批量清除通知缓存失败：" + e.getMessage());
        }
    }
    
    /**
     * 清除单个用户的通知列表缓存
     * @param userId 用户ID
     * @return 操作结果
     */
    @DeleteMapping("/notification/user/{userId}")
    @SaCheckRole("admin")
    public SaResult clearUserNotificationCache(@PathVariable Integer userId) {
        if (userId == null || userId <= 0) {
            return SaResult.error("用户ID无效");
        }
        
        try {
            notificationCacheService.clearNotificationListCache(userId);
            log.info("管理员清除用户 {} 的通知缓存", userId);
            return SaResult.ok("已清除用户的通知缓存");
        } catch (Exception e) {
            log.error("清除用户通知缓存失败，用户ID: {}", userId, e);
            return SaResult.error("清除通知缓存失败：" + e.getMessage());
        }
    }
} 