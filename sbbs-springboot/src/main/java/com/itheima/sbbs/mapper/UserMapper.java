package com.itheima.sbbs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itheima.sbbs.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("select group_id from \"user\" WHERE id = #{id}")
    public String getRole(@Param("id") Integer id);
    
    /**
     * 增加用户经验值
     * @param userId 用户ID
     * @param experience 增加的经验值
     * @return 影响的行数
     */
    @Update("UPDATE \"user\" SET experience = COALESCE(experience, 0) + #{experience} WHERE id = #{userId} AND deleted = 0")
    int addUserExperience(@Param("userId") Integer userId, @Param("experience") Integer experience);
    
    /**
     * 减少用户经验值（确保不会变成负数）
     * @param userId 用户ID
     * @param experience 减少的经验值
     * @return 影响的行数
     */
    @Update("UPDATE \"user\" SET experience = GREATEST(0, COALESCE(experience, 0) - #{experience}) WHERE id = #{userId} AND deleted = 0")
    int reduceUserExperience(@Param("userId") Integer userId, @Param("experience") Integer experience);
    
    
    /**
     * 🚀 一次性获取用户统计信息（高性能优化版）
     * 将原来的4次独立查询合并为1次查询，大幅提升性能
     * @param userId 用户ID
     * @return Map格式的统计数据: {postCount, commentCount, followerCount, followingCount}
     */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM post WHERE user_id = #{userId} AND deleted = 0) as postCount, " +
            "(SELECT COUNT(*) FROM \"comment\" WHERE user_id = #{userId} AND deleted = 0) as commentCount, " +
            "(SELECT COUNT(*) FROM user_follow WHERE following_id = #{userId} AND deleted = 0) as followerCount, " +
            "(SELECT COUNT(*) FROM user_follow WHERE follower_id = #{userId} AND deleted = 0) as followingCount")
    java.util.Map<String, Object> getUserStats(@Param("userId") Integer userId);

    @Update("UPDATE \"user\" SET experience = COALESCE(experience, 0) + #{experience} " +
            "WHERE id = #{userId} AND deleted = 0")
    void updateExperience(@Param("userId") Integer userId, @Param("experience") Integer experience);
}
