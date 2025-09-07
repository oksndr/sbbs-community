package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 包含用户信息的评论 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class CommentWithUserDto extends Comment implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String username; // 用户名
    private String avatar;   // 用户头像 URL (假设用户实体有这个字段)
    private Boolean isLiked; // 是否已点赞
    private Boolean isDisliked; // 是否已点踩
} 