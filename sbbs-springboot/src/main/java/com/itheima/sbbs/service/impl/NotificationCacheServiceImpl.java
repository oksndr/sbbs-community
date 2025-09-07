package com.itheima.sbbs.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.service.NotificationCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class NotificationCacheServiceImpl implements NotificationCacheService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // 缓存键前缀
    private static final String NOTIFICATION_LIST_KEY_PREFIX = "notification:list:";
    
    // 缓存过期时间
    private static final long NOTIFICATION_LIST_EXPIRE_MINUTES = 10; // 通知列表缓存10分钟
    
    /**
     * 通知列表缓存DTO
     */
    public static class NotificationListCache {
        private List<Notification> records;
        private long total;
        private long current;
        private long size;
        private long pages;
        
        // getter and setter
        public List<Notification> getRecords() { return records; }
        public void setRecords(List<Notification> records) { this.records = records; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public long getCurrent() { return current; }
        public void setCurrent(long current) { this.current = current; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public long getPages() { return pages; }
        public void setPages(long pages) { this.pages = pages; }
    }
    
    @Override
    public void cacheNotificationList(Integer userId, Integer page, Integer size, Boolean onlyUnread, Page<Notification> result) {
        if (userId == null || userId <= 0 || result == null) {
            return;
        }
        
        String key = buildNotificationListKey(userId, page, size, onlyUnread);
        
        try {
            // 转换为简单的DTO对象
            NotificationListCache cacheDto = new NotificationListCache();
            cacheDto.setRecords(result.getRecords());
            cacheDto.setTotal(result.getTotal());
            cacheDto.setCurrent(result.getCurrent());
            cacheDto.setSize(result.getSize());
            cacheDto.setPages(result.getPages());
            
            String jsonData = objectMapper.writeValueAsString(cacheDto);
            redisTemplate.opsForValue().set(key, jsonData, NOTIFICATION_LIST_EXPIRE_MINUTES, TimeUnit.MINUTES);
            
            log.debug("缓存用户 {} 通知列表，页码: {}, 每页: {}, 只查未读: {}", userId, page, size, onlyUnread);
            
        } catch (JsonProcessingException e) {
            log.error("缓存通知列表失败，序列化错误，用户ID: {}", userId, e);
        } catch (Exception e) {
            log.error("缓存通知列表失败，用户ID: {}", userId, e);
        }
    }
    
    @Override
    public Page<Notification> getCachedNotificationList(Integer userId, Integer page, Integer size, Boolean onlyUnread) {
        if (userId == null || userId <= 0) {
            return null;
        }
        
        String key = buildNotificationListKey(userId, page, size, onlyUnread);
        
        try {
            Object cachedData = redisTemplate.opsForValue().get(key);
            if (cachedData != null) {
                String jsonData = cachedData.toString();
                
                // 反序列化为DTO对象
                NotificationListCache cacheDto = objectMapper.readValue(jsonData, NotificationListCache.class);
                
                // 转换回Page对象
                Page<Notification> result = new Page<>(cacheDto.getCurrent(), cacheDto.getSize());
                result.setRecords(cacheDto.getRecords());
                result.setTotal(cacheDto.getTotal());
                result.setPages(cacheDto.getPages());
                
                log.debug("从缓存获取用户 {} 通知列表，页码: {}, 每页: {}, 只查未读: {}", userId, page, size, onlyUnread);
                return result;
            }
        } catch (Exception e) {
            log.error("获取通知列表缓存失败，用户ID: {}", userId, e);
        }
        
        return null;
    }
    
    @Override
    @Async("asyncCacheClear")
    public void clearNotificationListCache(Integer userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        
        try {
            // 清除该用户所有的通知列表缓存
            String pattern = NOTIFICATION_LIST_KEY_PREFIX + userId + ":*";
            Set<String> keys = scanKeys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("清除用户 {} 通知列表缓存，共 {} 个键", userId, keys.size());
            }
            
        } catch (Exception e) {
            log.error("清除通知列表缓存失败，用户ID: {}", userId, e);
        }
    }
    
    @Override
    @Async("asyncCacheClear")
    public void batchClearNotificationListCache(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        
        try {
            // 构建所有用户的缓存键集合
            Set<String> keysToDelete = new HashSet<>();
            
            for (Integer userId : userIds) {
                if (userId != null && userId > 0) {
                    String pattern = NOTIFICATION_LIST_KEY_PREFIX + userId + ":*";
                    Set<String> userKeys = scanKeys(pattern);
                    if (userKeys != null && !userKeys.isEmpty()) {
                        keysToDelete.addAll(userKeys);
                    }
                }
            }
            
            // 一次性批量删除所有键
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.debug("批量清除 {} 个用户的通知列表缓存，共 {} 个键", 
                        userIds.size(), keysToDelete.size());
            }
            
        } catch (Exception e) {
            log.error("批量清除通知列表缓存失败，用户数量: {}", userIds.size(), e);
        }
    }
    
    /**
     * 使用scan命令查找匹配的键，避免使用keys命令阻塞Redis
     * @param pattern 键模式
     * @return 匹配的键集合
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        
        redisTemplate.execute((RedisConnection connection) -> {
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100) // 每次扫描100个键
                    .build())) {
                
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
            } catch (Exception e) {
                log.error("扫描Redis键时出错: {}", e.getMessage(), e);
            }
            return null;
        });
        
        return keys;
    }
    
    /**
     * 构建通知列表缓存键
     */
    private String buildNotificationListKey(Integer userId, Integer page, Integer size, Boolean onlyUnread) {
        return String.format("%s%d:%d:%d:%s", NOTIFICATION_LIST_KEY_PREFIX, userId, page, size, 
                            onlyUnread != null ? onlyUnread.toString() : "false");
    }
} 