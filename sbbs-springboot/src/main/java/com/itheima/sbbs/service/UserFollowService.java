package com.itheima.sbbs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.itheima.sbbs.entity.UserFollow;
import com.itheima.sbbs.entity.UserFollowDto;
import com.itheima.sbbs.entity.UserFollowDetailDto;

import java.util.List;

public interface UserFollowService extends IService<UserFollow> {

    /**
     * 关注用户
     *
     * @param followerId 关注者用户ID
     * @param followingId 被关注者用户ID
     * @return 是否成功
     */
    boolean followUser(Integer followerId, Integer followingId);

    /**
     * 取消关注用户
     *
     * @param followerId 关注者用户ID
     * @param followingId 被关注者用户ID
     * @return 是否成功
     */
    boolean unfollowUser(Integer followerId, Integer followingId);

    /**
     * 判断用户是否关注了另一个用户
     *
     * @param followerId 关注者用户ID
     * @param followingId 被关注者用户ID
     * @return 是否关注
     */
    boolean isFollowing(Integer followerId, Integer followingId);

    /**
     * 获取用户的关注数
     *
     * @param userId 用户ID
     * @return 关注数
     */
    int getFollowingCount(Integer userId);

    /**
     * 获取用户的粉丝数
     *
     * @param userId 用户ID
     * @return 粉丝数
     */
    int getFollowerCount(Integer userId);

    /**
     * 获取用户的关注列表
     *
     * @param userId 用户ID
     * @return 关注的用户列表
     */
    List<UserFollowDto> getFollowingList(Integer userId);

    /**
     * 分页获取用户的关注列表
     *
     * @param userId 用户ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小，默认15
     * @return 关注的用户列表
     */
    List<UserFollowDto> getFollowingListByPage(Integer userId, Integer pageNum, Integer pageSize);

    /**
     * 清除用户关注列表缓存
     *
     * @param userId 用户ID
     */
    void clearFollowingCache(Integer userId);

    /**
     * 获取用户的粉丝列表
     *
     * @param userId 用户ID
     * @return 粉丝列表
     */
    List<UserFollowDto> getFollowerList(Integer userId);

    /**
     * 分页获取用户的粉丝列表
     *
     * @param userId 用户ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小，默认15
     * @return 粉丝列表
     */
    List<UserFollowDto> getFollowerListByPage(Integer userId, Integer pageNum, Integer pageSize);

    /**
     * 清除用户粉丝列表缓存
     *
     * @param userId 用户ID
     */
    void clearFollowerCache(Integer userId);

    /**
     * 分页获取用户的粉丝列表（返回分页信息）
     *
     * @param userId 用户ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小，默认15
     * @return 粉丝分页结果
     */
    IPage<UserFollowDetailDto> getFollowerPageResult(Integer userId, Integer pageNum, Integer pageSize);

    /**
     * 分页获取用户的关注列表（返回分页信息）
     *
     * @param userId 用户ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小，默认15
     * @return 关注分页结果
     */
    IPage<UserFollowDetailDto> getFollowingPageResult(Integer userId, Integer pageNum, Integer pageSize);
} 