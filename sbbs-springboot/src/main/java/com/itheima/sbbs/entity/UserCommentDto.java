package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户评论列表DTO - 简化版本
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserCommentDto implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Integer id;           // 评论ID
    private Integer postId;       // 帖子ID
    private Integer parentId;     // 父评论ID（对于二级评论）
    private String content;       // 评论内容
    private String replyCount;    // 回复数
    private String likeCount;     // 点赞数
    private String dislikeCount;  // 点踩数
    private Date updated;         // 更新时间
    private Date created;         // 创建时间
    
    private String username;      // 评论者用户名
    private String avatar;        // 评论者头像
    
    // 额外的帖子信息（仅标题）- 让用户知道这条评论是在哪个帖子下
    private String postTitle;
    
    /**
     * 从Comment实体和用户信息创建DTO
     */
    public static UserCommentDto fromComment(Comment comment, User user, String postTitle) {
        if (comment == null) return null;
        
        UserCommentDto dto = new UserCommentDto();
        dto.setId(comment.getId());
        dto.setPostId(comment.getPostId());
        dto.setParentId(comment.getParentId());
        dto.setContent(comment.getContent());
        dto.setReplyCount(comment.getReplyCount());
        dto.setLikeCount(comment.getLikeCount());
        dto.setDislikeCount(comment.getDislikeCount());
        dto.setCreated(comment.getCreated());
        dto.setUpdated(comment.getUpdated());
        
        if (user != null) {
            dto.setUsername(user.getUsername());
            dto.setAvatar(user.getAvatar());
        }
        
        dto.setPostTitle(postTitle);
        
        return dto;
    }
} 