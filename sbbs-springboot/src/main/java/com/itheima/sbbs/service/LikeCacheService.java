package com.itheima.sbbs.service;

import java.util.List;

import java.util.Map;

/**
 * 点赞缓存服务接口
 * 用于管理Redis中的实时点赞状态
 */
public interface LikeCacheService {
    
    /**
     * 添加帖子点赞到缓存
     * @param userId 用户ID
     * @param postId 帖子ID
     */
    void addPostLike(Integer userId, Integer postId);
    
    /**
     * 移除帖子点赞从缓存
     * @param userId 用户ID
     * @param postId 帖子ID
     */
    void removePostLike(Integer userId, Integer postId);
    
    /**
     * 添加帖子点踩到缓存
     * @param userId 用户ID
     * @param postId 帖子ID
     */
    void addPostDislike(Integer userId, Integer postId);
    
    /**
     * 移除帖子点踩从缓存
     * @param userId 用户ID
     * @param postId 帖子ID
     */
    void removePostDislike(Integer userId, Integer postId);
    
    /**
     * 添加评论点赞到缓存
     * @param userId 用户ID
     * @param commentId 评论ID
     */
    void addCommentLike(Integer userId, Integer commentId);
    
    /**
     * 移除评论点赞从缓存
     * @param userId 用户ID
     * @param commentId 评论ID
     */
    void removeCommentLike(Integer userId, Integer commentId);
    
    /**
     * 添加评论点踩到缓存
     * @param userId 用户ID
     * @param commentId 评论ID
     */
    void addCommentDislike(Integer userId, Integer commentId);
    
    /**
     * 移除评论点踩从缓存
     * @param userId 用户ID
     * @param commentId 评论ID
     */
    void removeCommentDislike(Integer userId, Integer commentId);
    
    /**
     * 获取帖子的点赞状态（优化方法：一次性获取点赞和点踩状态）
     * @param userId 用户ID
     * @param postId 帖子ID
     * @return Map包含liked和disliked两个boolean值
     */
    Map<String, Boolean> getPostLikeStatus(Integer userId, Integer postId);
    
    /**
     * 检查用户是否点赞了帖子
     * @param userId 用户ID
     * @param postId 帖子ID
     * @return true表示已点赞
     */
    boolean isPostLiked(Integer userId, Integer postId);
    
    /**
     * 检查用户是否点踩了帖子
     * @param userId 用户ID
     * @param postId 帖子ID
     * @return true表示已点踩
     */
    boolean isPostDisliked(Integer userId, Integer postId);
    
    /**
     * 检查用户是否点赞了评论
     * @param userId 用户ID
     * @param commentId 评论ID
     * @return true表示已点赞
     */
    boolean isCommentLiked(Integer userId, Integer commentId);
    
    /**
     * 检查用户是否点踩了评论
     * @param userId 用户ID
     * @param commentId 评论ID
     * @return true表示已点踩
     */
    boolean isCommentDisliked(Integer userId, Integer commentId);
    
    /**
     * 获取帖子点赞数量（从缓存）
     * @param postId 帖子ID
     * @return 点赞数量
     */
    Long getPostLikeCount(Integer postId);
    
    /**
     * 获取帖子点踩数量（从缓存）
     * @param postId 帖子ID
     * @return 点踩数量
     */
    Long getPostDislikeCount(Integer postId);
    
    /**
     * 获取评论点赞数量（从缓存）
     * @param commentId 评论ID
     * @return 点赞数量
     */
    Long getCommentLikeCount(Integer commentId);
    
    /**
     * 获取评论点踩数量（从缓存）
     * @param commentId 评论ID
     * @return 点踩数量
     */
    Long getCommentDislikeCount(Integer commentId);
    
    /**
     * 从数据库同步帖子点赞状态到缓存
     * @param postId 帖子ID
     */
    void syncPostLikesFromDatabase(Integer postId);
    
    /**
     * 从数据库同步评论点赞状态到缓存
     * @param commentId 评论ID
     */
    void syncCommentLikesFromDatabase(Integer commentId);
    
    /**
     * 批量预热评论点赞缓存
     * @param commentIds 评论ID列表
     */
    void batchWarmupCommentCache(List<Integer> commentIds);
    
    /**
     * 🔥 **真正的批量查询**：一次性查询多个评论的用户点赞状态
     * @param userId 用户ID
     * @param commentIds 评论ID列表
     * @return Map<评论ID, Map<"liked"/"disliked", Boolean>>
     */
    Map<Integer, Map<String, Boolean>> batchGetCommentLikeStatus(Integer userId, List<Integer> commentIds);
}
