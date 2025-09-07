package com.itheima.sbbs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.sbbs.entity.Comment;
import com.itheima.sbbs.entity.CommentDto;
import com.itheima.sbbs.entity.CommentLocationDto;
import com.itheima.sbbs.entity.CommentWithUserDto;
import com.itheima.sbbs.entity.UserCommentDto;

import java.util.List;

public interface CommentService extends IService<Comment> {
    /**
     * 发布一级评论
     * @param comment 评论对象
     * @return 包含评论ID和所在页码的DTO
     */
    CommentLocationDto saveTopComment(Comment comment);

    void saveSndComment(Comment comment);

    CommentDto getTopComments(Integer postId, Integer lastId, Integer pageSize);
    
    /**
     * 按页码获取一级评论
     * @param postId 帖子ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @return 包含评论列表的DTO
     */
    CommentDto getTopCommentsByPage(Integer postId, Integer pageNum, Integer pageSize);

    List<CommentWithUserDto> getRepliesByCommentId(Integer commentId);

    void clearTopCommentsCacheByPostId(Integer postId);

    void clearRepliesCache(Integer commentId);
    
    /**
     * 清除评论所在页面的缓存
     * @param commentId 评论ID
     */
    void clearCommentPageCache(Integer commentId);

    /**
     * 用户删除自己的评论
     * @param commentId 评论ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteCommentByUserId(Integer commentId, Integer userId);
    
    /**
     * 添加评论（优化版）
     * @param postId 帖子ID
     * @param parentId 父评论ID
     * @param content 评论内容
     * @param loginUserId 登录用户ID
     * @return 评论详情
     */
    CommentWithUserDto addComment(Integer postId, Integer parentId, String content, Integer loginUserId);

    /**
     * 分页获取用户发布的评论
     * @param userId 用户ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小，默认15
     * @return 评论列表
     */
    List<UserCommentDto> getUserComments(Integer userId, Integer pageNum, Integer pageSize);
    
    /**
     * 清除用户评论列表缓存
     * @param userId 用户ID
     */
    void clearUserCommentCache(Integer userId);
    
    /**
     * 根据评论ID获取帖子ID和页码信息
     * @param commentId 评论ID
     * @return 包含帖子ID和页码的位置信息
     */
    CommentLocationDto getCommentLocation(Integer commentId);
}
