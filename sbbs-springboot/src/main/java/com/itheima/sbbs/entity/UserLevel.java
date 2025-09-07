package com.itheima.sbbs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户等级配置类
 */
@Data
@TableName("user_level")
@AllArgsConstructor
@NoArgsConstructor
public class UserLevel extends BasePojo {
    private Integer id;
    private Integer level;
    private String name; // 包含图标的等级名称，如"🐣NAT小鸡"
    private Integer minExperience;
    private Integer maxExperience;
} 