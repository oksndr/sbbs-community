package com.itheima.sbbs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itheima.sbbs.entity.UserFollow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserFollowMapper extends BaseMapper<UserFollow> {
    /**
     * 统计用户的粉丝数量
     * @param userId 用户ID
     * @return 粉丝数量
     */
    @Select("SELECT COUNT(*) FROM user_follow WHERE following_id = #{userId} AND deleted = 0")
    Integer countFollowers(@Param("userId") Integer userId);
    
    /**
     * 统计用户关注的人数量
     * @param userId 用户ID
     * @return 关注数量
     */
    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{userId} AND deleted = 0")
    Integer countFollowing(@Param("userId") Integer userId);
} 