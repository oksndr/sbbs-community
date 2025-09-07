package com.itheima.sbbs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itheima.sbbs.entity.UserLevel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserLevelMapper extends BaseMapper<UserLevel> {
    
    /**
     * 根据经验值查询用户等级
     * @param experience 经验值
     * @return 用户等级配置
     */
    @Select("SELECT * FROM user_level WHERE deleted = 0 AND min_experience <= #{experience} AND (max_experience IS NULL OR max_experience >= #{experience}) ORDER BY level DESC LIMIT 1")
    UserLevel getLevelByExperience(@Param("experience") Integer experience);
} 