package com.itheima.sbbs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

/**
 * 论坛用户类
 */
@Data
@TableName("\"user\"")
@AllArgsConstructor
@NoArgsConstructor
public class User extends BasePojo{
    private Integer id;
    private String username;
    private String password;
    private String groupId;//角色分组
    private String email;
    private String avatar;
    private Integer experience = 0; // 用户经验值，默认为0
    
    // 邮件通知设置
    private Boolean enableLikeNotification = true; // 是否接收点赞通知邮件，默认开启
    private Boolean enableOtherNotification = true; // 是否接收其他类型通知邮件，默认开启
}
