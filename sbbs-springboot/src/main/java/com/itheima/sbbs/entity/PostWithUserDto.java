package com.itheima.sbbs.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 包含作者信息的帖子 DTO
 */
@Data
@NoArgsConstructor
public class PostWithUserDto {
    private Integer id;
    private Integer userId;
    private String title;
    private String content; // 可以只返回部分内容或摘要
    private Integer commentCount;
    private Integer likeCount;
    private Integer dislikeCount;
    private LocalDateTime created;
    private LocalDateTime updated;
    private Integer deleted; // 软删除标志

    // 作者信息
    private String username;
    private String avatar;

    // 帖子的标签名称列表
    private List<String> tags;

    // 用于接收原始的标签ID字符串 (不映射数据库)
    private String tagIdsStringAlias;
    
    // 🚀 新增：用于接收数据库返回的标签名称字符串（高性能查询使用）
    private String tagNames;
} 