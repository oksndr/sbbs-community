package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通知跳转信息DTO
 * 用于前端点击通知后的页面跳转
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationJumpInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 通知ID
     */
    private Integer notificationId;
    
    /**
     * 通知类型
     * 1: 评论了我的帖子
     * 2: 回复了我的评论  
     * 3: 在评论中@了我
     * 4: "回复 xxx :"格式回复了我
     * 5: 点赞了我的帖子
     * 6: 点踩了我的帖子
     * 7: 点赞了我的评论
     * 8: 点踩了我的评论
     * 9: 帖子被管理员删除（不支持跳转）
     * 10: 用户关注了我
     */
    private Integer notificationType;
    
    /**
     * 帖子ID - 所有类型都需要
     */
    private Integer postId;
    
    /**
     * 页码 - 评论所在的页面
     */
    private Integer pageNumber;
    
    /**
     * 目标评论ID
     * - 类型1: 一级评论ID
     * - 类型2,3,4: 二级评论ID
     */
    private Integer targetCommentId;
    
    /**
     * 父评论ID (仅类型2,3,4需要)
     * 用于定位二级评论的父评论
     */
    private Integer parentCommentId;
    
    /**
     * 跳转类型
     * "post": 跳转到帖子详情页
     * "comment": 跳转到评论位置
     * "user": 跳转到用户主页
     */
    private String jumpType;
    
    /**
     * 用户ID（仅当jumpType为"user"时使用）
     * 用于跳转到用户主页的场景，如关注通知
     */
    private Integer userId;
    
    /**
     * 额外信息 (可选)
     * 比如评论内容预览等
     */
    private String extraInfo;
}
