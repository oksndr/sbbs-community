package com.itheima.sbbs.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.sbbs.entity.Notification;

/**
 * 通知缓存服务
 */
public interface NotificationCacheService {
    
    /**
     * 缓存通知列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @param onlyUnread 是否只查未读
     * @param result 查询结果
     */
    void cacheNotificationList(Integer userId, Integer page, Integer size, Boolean onlyUnread, Page<Notification> result);
    
    /**
     * 获取缓存的通知列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @param onlyUnread 是否只查未读
     * @return 缓存的通知列表，如果不存在返回null
     */
    Page<Notification> getCachedNotificationList(Integer userId, Integer page, Integer size, Boolean onlyUnread);
    
    /**
     * 清除用户通知列表缓存
     * @param userId 用户ID
     */
    void clearNotificationListCache(Integer userId);
    
    /**
     * 批量清除多个用户的通知列表缓存
     * @param userIds 用户ID列表
     */
    void batchClearNotificationListCache(java.util.List<Integer> userIds);
} 