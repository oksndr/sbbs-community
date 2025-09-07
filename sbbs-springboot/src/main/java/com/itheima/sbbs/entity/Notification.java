package com.itheima.sbbs.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Notification extends BasePojo {
    private Integer id;
    private Integer receiverId;
    private Integer senderId;
    private Integer notificationType;//通知类型 (1: 评论了我的帖子, 2: 回复了我的评论 (给父评论作者), 3: 在评论(包括二级评论)中 @了我, 4: "回复 xxx :", 5: 点赞了我的帖子, 6: 点踩了我的帖子, 7: 点赞了我的评论/回复, 8: 点踩了我的评论/回复 9: 帖子被管理员删除 10: 关注了我)
    private Integer relatedId;
    private String relatedType;//1: 帖子, 2: 评论
    private Integer triggerEntityId; // 触发此通知的具体实体ID
    private Integer triggerEntityType; // 触发此通知的具体实体类型 1: 评论, 2: 点赞, 3:点踩记录
    private boolean isRead;
    
    // 以下字段用于通知列表查询时的附加信息，不对应数据库字段
    @TableField(exist = false)
    private String notificationText; // 通知文字内容
    @TableField(exist = false)
    private String senderUsername; // 发送者用户名
    @TableField(exist = false)
    private String senderAvatar; // 发送者头像
    @TableField(exist = false)
    private String relatedTitle; // 相关帖子标题或评论内容预览
    @TableField(exist = false)
    private String commentPreview; // 评论内容预览（前50字符）
}
