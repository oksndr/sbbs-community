package com.itheima.sbbs.service;

import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.entity.User;

import java.util.List;

public interface AsyncService {
    void sendEmail(String email, String subject, String content);
    void processCommentNotifications(List<Notification> notifications, String title, String content, User recipient, Integer loginUserId);
    void updateUserExperience(Integer userId, Integer experience);
    void clearCommentCaches(Integer postId, Integer commentId, Integer pageNum);
    
    /**
     * 异步清除评论缓存
     * @param commentId 评论ID
     */
    void clearCommentCache(Integer commentId);
    
    /**
     * 异步更新评论计数
     * @param postId 帖子ID
     * @param commentId 父评论ID
     */
    void updateCommentCounts(Integer postId, Integer commentId);
    
    /**
     * 异步发送@提及邮件通知
     * @param email 被@用户的邮箱
     * @param postTitle 帖子标题
     */
    void sendAtMentionEmail(String email, String postTitle);
    
    /**
     * 处理经验值变化事件（事件监听器）
     * @param event 经验值变化事件
     */
    void handleExperienceChangeEvent(com.itheima.sbbs.service.impl.ExperienceServiceImpl.ExperienceChangeEvent event);
    
    // 🗑️ **经验值异步方法已删除，改为事件监听器处理**
    // addUserExperienceAsync() 和 reduceUserExperienceAsync() 已移除
} 