package com.itheima.sbbs.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.sbbs.entity.User;
import com.itheima.sbbs.entity.UserSimpleDto;
import com.itheima.sbbs.entity.UserProfileDto;
import com.itheima.sbbs.entity.UserBasicInfoDto;
import com.itheima.sbbs.entity.PostWithUserDto;

import java.util.List;
import java.util.Map;

public interface UserService extends IService<User> {
    User getUserByEmail(String email);

    List<UserSimpleDto> searchUsers(String keyword);
    
    /**
     * 搜索用户（旧方法，保留兼容）
     */
    List<UserSimpleDto> searchUsers(String keyword, Integer page, Integer pageSize);
    
    /**
     * 统计用户搜索结果总数（旧方法，保留兼容）
     */
    Integer countSearchUsers(String keyword);
    
    /**
     * 使用MyBatis-Plus分页搜索用户
     * @param keyword 搜索关键词
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    IPage<UserSimpleDto> searchUsersWithPage(String keyword, Integer page, Integer pageSize);

    long getTotalUserCount();

    long getNewUserCountToday();
    
    /**
     * 获取用户主页信息
     * @param userId 用户ID
     * @param pageNo 当前页码
     * @param pageSize 每页数量
     * @return 用户主页信息DTO
     */
    UserProfileDto getUserProfile(Integer userId, Integer pageNo, Integer pageSize);
    
    /**
     * 🚀 获取用户基本信息（缓存优化版）
     * @param userId 用户ID
     * @param currentUserId 当前登录用户ID（用于判断关注状态，可为null）
     * @return 用户基本信息DTO
     */
    UserBasicInfoDto getUserBasicInfo(Integer userId, Integer currentUserId);
    
    /**
     * 🚀 获取用户帖子列表（缓存优化版）
     * @param userId 用户ID
     * @param pageNo 当前页码
     * @param pageSize 每页数量
     * @return 用户帖子列表
     */
    List<PostWithUserDto> getUserPosts(Integer userId, Integer pageNo, Integer pageSize);

    /**
     * 增加用户经验值
     * @param userId 用户ID
     * @param experience 增加的经验值
     * @return 是否成功
     */
    boolean addUserExperience(Integer userId, Integer experience);

    /**
     * 减少用户经验值
     * @param userId 用户ID
     * @param experience 减少的经验值
     * @return 是否成功
     */
    boolean reduceUserExperience(Integer userId, Integer experience);


    /**
     * 获取用户当前经验值和等级信息
     * @param userId 用户ID
     * @return 用户经验值信息
     */
    Map<String, Object> getUserLevelInfo(Integer userId);
    
    /**
     * 🚀 清除用户相关缓存（供其他Service调用）
     * @param userId 用户ID
     */
    void clearUserCache(Integer userId);

    /**
     * 增加用户经验值
     * @param userId 用户ID
     * @param experience 要增加的经验值
     */
    void addExperience(Integer userId, Integer experience);
    
}
