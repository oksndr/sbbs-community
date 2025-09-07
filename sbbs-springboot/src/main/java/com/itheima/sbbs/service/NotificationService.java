package com.itheima.sbbs.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.entity.NotificationJumpInfo;

public interface NotificationService extends IService<Notification> {

    /**
     * 根据通知ID获取跳转信息
     * @param notificationId 通知ID
     * @return 跳转信息，如果通知不存在或无权访问则返回null
     */
    NotificationJumpInfo getJumpInfo(Integer notificationId);
    
    /**
     * 分页查询用户的通知列表
     * @param receiverId 接收者用户ID
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @param onlyUnread 是否只查询未读通知
     * @return 通知列表分页结果
     */
    Page<Notification> getNotificationList(Integer receiverId, Integer page, Integer size, Boolean onlyUnread);
    
    /**
     * 标记通知为已读
     * @param notificationId 通知ID
     * @param userId 当前用户ID（用于权限验证）
     * @return 是否操作成功
     */
    boolean markAsRead(Integer notificationId, Integer userId);
    
    /**
     * 批量标记通知为已读
     * @param notificationIds 通知ID列表
     * @param userId 当前用户ID（用于权限验证）
     * @return 操作成功的数量
     */
    int markBatchAsRead(java.util.List<Integer> notificationIds, Integer userId);
}
