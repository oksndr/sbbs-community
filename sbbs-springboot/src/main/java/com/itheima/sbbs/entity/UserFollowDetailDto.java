package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户关注详细信息DTO - 增强版本
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserFollowDetailDto implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Integer id;           // 用户ID
    private String username;      // 用户名
    private String avatar;        // 用户头像
    private Date followTime;      // 关注时间
    private Integer followerCount;  // 粉丝数
    private Integer followingCount; // 关注数
    private Integer experience;     // 经验值
    private String groupId;         // 用户组（管理员、普通用户等）
    
    /**
     * 从基础DTO创建详细DTO
     */
    public static UserFollowDetailDto fromBasic(UserFollowDto basic) {
        if (basic == null) return null;
        
        UserFollowDetailDto detail = new UserFollowDetailDto();
        detail.setId(basic.getId());
        detail.setUsername(basic.getUsername());
        detail.setAvatar(basic.getAvatar());
        return detail;
    }
} 