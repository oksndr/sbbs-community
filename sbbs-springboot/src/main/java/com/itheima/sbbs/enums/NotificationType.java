package com.itheima.sbbs.enums;

/**
 * 通知类型枚举
 */
public enum NotificationType {
    
    COMMENT_POST(1, "评论了您的帖子", "%s 评论了您的帖子"),
    REPLY_COMMENT(2, "回复了您的评论", "%s 回复了您的评论"),
    MENTION_IN_COMMENT(3, "在评论中@了您", "%s 在评论中@了您"),
    REPLY_FORMAT(4, "回复了您", "%s 回复了您"),
    LIKE_POST(5, "点赞了您的帖子", "%s 点赞了您的帖子"),
    DISLIKE_POST(6, "点踩了您的帖子", "%s 点踩了您的帖子"),
    LIKE_COMMENT(7, "点赞了您的评论", "%s 点赞了您的评论"),
    DISLIKE_COMMENT(8, "点踩了您的评论", "%s 点踩了您的评论"),
    POST_DELETED(9, "您的帖子被管理员删除", "您的帖子被管理员删除"),
    USER_FOLLOW(10, "关注了您", "%s 关注了您"),
    LEVEL_UP(11, "🎉 恭喜您升级", "🎉 恭喜您升级");
    
    private final Integer code;
    private final String description;
    private final String simpleText; // 简化的通知文字
    
    NotificationType(Integer code, String description, String simpleText) {
        this.code = code;
        this.description = description;
        this.simpleText = simpleText;
    }
    
    public Integer getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getSimpleText() {
        return simpleText;
    }
    
    /**
     * 根据code获取通知类型
     * @param code 类型代码
     * @return NotificationType
     */
    public static NotificationType getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        
        for (NotificationType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
    

} 