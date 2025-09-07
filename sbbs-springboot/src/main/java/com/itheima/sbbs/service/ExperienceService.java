package com.itheima.sbbs.service;

/**
 * 用户经验值管理服务
 */
public interface ExperienceService {
    
    /**
     * 用户发帖获得经验值
     * @param userId 用户ID
     */
    void addPostExperience(Integer userId);
    
    /**
     * 用户评论获得经验值
     * @param userId 用户ID
     */
    void addCommentExperience(Integer userId);
    
    /**
     * 帖子被点赞，作者获得经验值
     * @param authorId 帖子作者ID
     */
    void addPostLikeExperience(Integer authorId);
    
    /**
     * 评论被点赞，作者获得经验值
     * @param authorId 评论作者ID
     */
    void addCommentLikeExperience(Integer authorId);
    
    /**
     * 帖子被点踩，作者减少经验值
     * @param authorId 帖子作者ID
     */
    void reducePostDislikeExperience(Integer authorId);

    /**
     * 用户每天第一次点赞获得经验值
     * @param userId 点赞用户ID
     */
    void addFirstLikeExperience(Integer userId);

    /**
     * 计算发帖可获得的经验值（不实际添加）
     * @param userId 用户ID
     * @return 可获得的经验值
     */
    int calculatePostExperience(Integer userId);

    /**
     * 计算评论可获得的经验值（不实际添加）
     * @param userId 用户ID
     * @return 可获得的经验值
     */
    int calculateCommentExperience(Integer userId);

    /**
     * 计算点赞可获得的经验值（不实际添加）
     * @param userId 用户ID
     * @return 可获得的经验值
     */
    int calculateFirstLikeExperience(Integer userId);

    /**
     * 🗑️ **已废弃：处理经验值队列（定时任务调用）**
     * 新架构：所有经验值操作直接写入数据库，无需队列处理
     */
    void processExperienceQueue();
    
    /**
     * 获取用户经验值（优先从缓存获取）
     * @param userId 用户ID
     * @return 用户经验值
     */
    Integer getUserExperience(Integer userId);
    
    /**
     * 🎉 检查用户是否升级并发送通知（供外部调用）
     * @param userId 用户ID
     * @param oldExp 经验值变化前的经验值
     * @param newExp 经验值变化后的经验值
     */
    void checkAndNotifyLevelUp(Integer userId, Integer oldExp, Integer newExp);
} 