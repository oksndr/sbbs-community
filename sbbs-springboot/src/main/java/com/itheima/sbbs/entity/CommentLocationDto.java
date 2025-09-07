package com.itheima.sbbs.entity;

import lombok.Data;

/**
 * 评论位置DTO，包含评论ID和所在页码信息
 */
@Data
public class CommentLocationDto {
    /**
     * 评论ID
     */
    private Integer commentId;
    
    /**
     * 评论所在页码
     */
    private Integer page;
    
    /**
     * 帖子ID
     */
    private Integer postId;
    
    /**
     * 父评论ID（二级评论时使用）
     */
    private Integer parentCommentId;
} 