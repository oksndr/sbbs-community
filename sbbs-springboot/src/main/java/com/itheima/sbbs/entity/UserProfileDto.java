package com.itheima.sbbs.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserProfileDto {
    // 用户基本信息
    private Integer id;
    private String username;
    private String avatar;
    private LocalDateTime created;
    private String groupId;
    
    // 用户等级信息
    private Integer experience;      // 经验值
    private Integer level;           // 等级
    private String levelName;        // 等级名称
    private String levelIcon;        // 等级图标
    private Integer currentLevelMinExp;     // 当前等级最小经验值
    private Integer nextLevelRequiredExp;   // 下一等级需要的经验值
    private Double progressPercent;         // 当前等级进度百分比
    
    // 🎯 新增：更直观的经验值显示
    private Integer currentStageExp;        // 本阶段已获得的经验值
    private Integer expNeededForNextLevel;  // 升级还需要的经验值
    
    // 用户统计数据
    private Integer postCount;
    private Integer commentCount;
    private Integer followerCount;
    private Integer followingCount;
    
    // 🎯 新增：关注状态（当前登录用户是否已关注此用户）
    private Boolean isFollowing;
    
    // 用户发布的帖子列表
    private List<PostWithUserDto> posts;
    
    // 分页信息
    private Integer total;
    private Integer pageSize;
    private Integer currentPage;
} 