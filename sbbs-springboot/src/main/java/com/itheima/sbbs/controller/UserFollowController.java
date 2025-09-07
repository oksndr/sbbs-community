package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.itheima.sbbs.entity.UserFollowDto;
import com.itheima.sbbs.entity.UserFollowDetailDto;
import com.itheima.sbbs.service.UserFollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/follow")
public class UserFollowController {

    @Autowired
    private UserFollowService userFollowService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 缓存键前缀
    private static final String ANONYMOUS_FOLLOWER_LIST_CACHE_PREFIX = "anonymous:follower_list:";
    private static final String ANONYMOUS_FOLLOWING_LIST_CACHE_PREFIX = "anonymous:following_list:";
    private static final String ANONYMOUS_FOLLOWER_PAGE_CACHE_PREFIX = "anonymous:follower_page:";
    private static final String ANONYMOUS_FOLLOWING_PAGE_CACHE_PREFIX = "anonymous:following_page:";
    private static final int ANONYMOUS_CACHE_EXPIRE_MINUTES = 5; // 5分钟缓存

    /**
     * 关注用户
     */
    @SaCheckLogin
    @PostMapping("/user/{followingId}")
    public SaResult followUser(@PathVariable Integer followingId) {
        Integer followerId = StpUtil.getLoginIdAsInt();
        boolean success = userFollowService.followUser(followerId, followingId);
        if (success) {
            return SaResult.ok("关注成功");
        } else {
            return SaResult.error("关注失败，可能已关注或不能关注自己");
        }
    }

    /**
     * 取消关注用户
     */
    @SaCheckLogin
    @DeleteMapping("/user/{followingId}")
    public SaResult unfollowUser(@PathVariable Integer followingId) {
        Integer followerId = StpUtil.getLoginIdAsInt();
        boolean success = userFollowService.unfollowUser(followerId, followingId);
        if (success) {
            return SaResult.ok("取消关注成功");
        } else {
            return SaResult.error("取消关注失败");
        }
    }

    /**
     * 判断是否关注了某个用户
     */
    @SaCheckLogin
    @GetMapping("/isFollowing/{followingId}")
    public SaResult isFollowing(@PathVariable Integer followingId) {
        Integer followerId = StpUtil.getLoginIdAsInt();
        boolean isFollowing = userFollowService.isFollowing(followerId, followingId);
        return SaResult.data(isFollowing);
    }

    /**
     * 获取用户的关注列表
     */
    @SaCheckLogin
    @GetMapping("/followingList/{userId}")
    public SaResult getFollowingList(@PathVariable Integer userId) {
        List<UserFollowDto> followingList = userFollowService.getFollowingList(userId);
        return SaResult.data(followingList);
    }

    /**
     * 获取用户的粉丝列表
     */
    @SaCheckLogin
    @GetMapping("/followerList/{userId}")
    public SaResult getFollowerList(@PathVariable Integer userId) {
        List<UserFollowDto> followerList = userFollowService.getFollowerList(userId);
        return SaResult.data(followerList);
    }
    
    /**
     * 分页获取用户的粉丝列表
     * 🚀 优化：查看他人粉丝使用Redis缓存5分钟，防止攻击
     * 
     * @param userId 用户ID
     * @param pageNum 页码，默认1
     * @param pageSize 每页大小，默认15
     * @return 用户粉丝列表
     */
    @SaCheckLogin
    @GetMapping("/followerList/{userId}/page")
    public SaResult getFollowerListByPage(
            @PathVariable("userId") Integer userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "15") Integer pageSize) {
        
        if (userId == null || userId <= 0) {
            return SaResult.error("无效的用户ID");
        }
        
        // 🚀 检查是否是查看自己的粉丝列表
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        boolean useCache = !currentUserId.equals(userId);
        
        if (useCache) {
            // 查看他人粉丝，使用缓存策略
            String cacheKey = ANONYMOUS_FOLLOWER_LIST_CACHE_PREFIX + userId + ":" + pageNum + ":" + pageSize;
            
            try {
                Object cachedResult = redisTemplate.opsForValue().get(cacheKey);
                if (cachedResult != null && cachedResult instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<UserFollowDto> cachedFollowers = (List<UserFollowDto>) cachedResult;
                    log.info("用户粉丝列表缓存命中，targetUserId: {}, currentUserId: {}, cacheKey: {}", 
                             userId, currentUserId, cacheKey);
                    return SaResult.code(200).data(cachedFollowers);
                }
            } catch (Exception e) {
                log.info("获取用户粉丝列表缓存失败，将查询数据库，targetUserId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            // 缓存未命中，查询数据库
            List<UserFollowDto> followers = userFollowService.getFollowerListByPage(userId, pageNum, pageSize);
            
            // 将结果缓存5分钟
            try {
                redisTemplate.opsForValue().set(cacheKey, followers, ANONYMOUS_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.debug("用户粉丝列表已缓存，targetUserId: {}, cacheKey: {}, 过期时间: {}分钟", 
                         userId, cacheKey, ANONYMOUS_CACHE_EXPIRE_MINUTES);
            } catch (Exception e) {
                log.warn("缓存用户粉丝列表失败，targetUserId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            return SaResult.code(200).data(followers);
        }
        
        // 查看自己的粉丝，不使用缓存（保证数据实时性）
        List<UserFollowDto> followers = userFollowService.getFollowerListByPage(userId, pageNum, pageSize);
        return SaResult.code(200).data(followers);
    }

    /**
     * 分页获取用户的关注列表
     * 🚀 优化：查看他人关注使用Redis缓存5分钟，防止攻击
     * 
     * @param userId 用户ID
     * @param pageNum 页码，默认1
     * @param pageSize 每页大小，默认15
     * @return 用户关注列表
     */
    @SaCheckLogin
    @GetMapping("/followingList/{userId}/page")
    public SaResult getFollowingListByPage(
            @PathVariable("userId") Integer userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "15") Integer pageSize) {
        
        if (userId == null || userId <= 0) {
            return SaResult.error("无效的用户ID");
        }
        
        // 🚀 检查是否是查看自己的关注列表
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        boolean useCache = !currentUserId.equals(userId);
        
        if (useCache) {
            // 查看他人关注，使用缓存策略
            String cacheKey = ANONYMOUS_FOLLOWING_LIST_CACHE_PREFIX + userId + ":" + pageNum + ":" + pageSize;
            
            try {
                Object cachedResult = redisTemplate.opsForValue().get(cacheKey);
                if (cachedResult != null && cachedResult instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<UserFollowDto> cachedFollowing = (List<UserFollowDto>) cachedResult;
                    log.info("用户关注列表缓存命中，targetUserId: {}, currentUserId: {}, cacheKey: {}", 
                             userId, currentUserId, cacheKey);
                    return SaResult.code(200).data(cachedFollowing);
                }
            } catch (Exception e) {
                log.info("获取用户关注列表缓存失败，将查询数据库，targetUserId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            // 缓存未命中，查询数据库
            List<UserFollowDto> following = userFollowService.getFollowingListByPage(userId, pageNum, pageSize);
            
            // 将结果缓存5分钟
            try {
                redisTemplate.opsForValue().set(cacheKey, following, ANONYMOUS_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.debug("用户关注列表已缓存，targetUserId: {}, cacheKey: {}, 过期时间: {}分钟", 
                         userId, cacheKey, ANONYMOUS_CACHE_EXPIRE_MINUTES);
            } catch (Exception e) {
                log.warn("缓存用户关注列表失败，targetUserId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            return SaResult.code(200).data(following);
        }
        
        // 查看自己的关注，不使用缓存（保证数据实时性）
        List<UserFollowDto> following = userFollowService.getFollowingListByPage(userId, pageNum, pageSize);
        return SaResult.code(200).data(following);
    }

    /**
     * 分页获取用户的粉丝列表（完整分页信息版本）
     * 🚀 优化：查看他人粉丝使用Redis缓存5分钟，防止攻击
     * 
     * @param userId 用户ID
     * @param pageNum 页码，默认1
     * @param pageSize 每页大小，默认15
     * @return 用户粉丝分页结果
     */
    @SaCheckLogin
    @GetMapping("/followerList/{userId}/pageDetail")
    public SaResult getFollowerPageDetail(
            @PathVariable("userId") Integer userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "15") Integer pageSize) {
        
        if (userId == null || userId <= 0) {
            return SaResult.error("无效的用户ID");
        }
        
        // 🚀 检查是否是查看自己的粉丝列表
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        boolean useCache = !currentUserId.equals(userId);
        
        if (useCache) {
            // 查看他人粉丝，使用缓存策略
            String cacheKey = ANONYMOUS_FOLLOWER_PAGE_CACHE_PREFIX + userId + ":" + pageNum + ":" + pageSize;
            
            try {
                Object cachedResult = redisTemplate.opsForValue().get(cacheKey);
                if (cachedResult != null && cachedResult instanceof IPage) {
                    @SuppressWarnings("unchecked")
                    IPage<UserFollowDetailDto> cachedPageResult = (IPage<UserFollowDetailDto>) cachedResult;
                    log.info("用户粉丝分页缓存命中，targetUserId: {}, currentUserId: {}, cacheKey: {}", 
                             userId, currentUserId, cacheKey);
                    return SaResult.code(200).data(cachedPageResult);
                }
            } catch (Exception e) {
                log.info("获取用户粉丝分页缓存失败，将查询数据库，targetUserId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            // 缓存未命中，查询数据库
            IPage<UserFollowDetailDto> pageResult = userFollowService.getFollowerPageResult(userId, pageNum, pageSize);
            
            // 将结果缓存5分钟
            try {
                redisTemplate.opsForValue().set(cacheKey, pageResult, ANONYMOUS_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.debug("用户粉丝分页已缓存，targetUserId: {}, cacheKey: {}, 过期时间: {}分钟", 
                         userId, cacheKey, ANONYMOUS_CACHE_EXPIRE_MINUTES);
            } catch (Exception e) {
                log.warn("缓存用户粉丝分页失败，targetUserId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            return SaResult.code(200).data(pageResult);
        }
        
        // 查看自己的粉丝，不使用缓存（保证数据实时性）
        IPage<UserFollowDetailDto> pageResult = userFollowService.getFollowerPageResult(userId, pageNum, pageSize);
        return SaResult.code(200).data(pageResult);
    }
    
    /**
     * 分页获取用户的关注列表（完整分页信息版本）
     * 🚀 优化：查看他人关注使用Redis缓存5分钟，防止攻击
     * 
     * @param userId 用户ID
     * @param pageNum 页码，默认1
     * @param pageSize 每页大小，默认15
     * @return 用户关注分页结果
     */
    @SaCheckLogin
    @GetMapping("/followingList/{userId}/pageDetail")
    public SaResult getFollowingPageDetail(
            @PathVariable("userId") Integer userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "15") Integer pageSize) {
        
        if (userId == null || userId <= 0) {
            return SaResult.error("无效的用户ID");
        }
        
        // 🚀 检查是否是查看自己的关注列表
        Integer currentUserId = StpUtil.getLoginIdAsInt();
        boolean useCache = !currentUserId.equals(userId);
        
        if (useCache) {
            // 查看他人关注，使用缓存策略
            String cacheKey = ANONYMOUS_FOLLOWING_PAGE_CACHE_PREFIX + userId + ":" + pageNum + ":" + pageSize;
            
            try {
                Object cachedResult = redisTemplate.opsForValue().get(cacheKey);
                if (cachedResult != null && cachedResult instanceof IPage) {
                    @SuppressWarnings("unchecked")
                    IPage<UserFollowDetailDto> cachedPageResult = (IPage<UserFollowDetailDto>) cachedResult;
                    log.info("用户关注分页缓存命中，targetUserId: {}, currentUserId: {}, cacheKey: {}", 
                             userId, currentUserId, cacheKey);
                    return SaResult.code(200).data(cachedPageResult);
                }
            } catch (Exception e) {
                log.info("获取用户关注分页缓存失败，将查询数据库，targetUserId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            // 缓存未命中，查询数据库
            IPage<UserFollowDetailDto> pageResult = userFollowService.getFollowingPageResult(userId, pageNum, pageSize);
            
            // 将结果缓存5分钟
            try {
                redisTemplate.opsForValue().set(cacheKey, pageResult, ANONYMOUS_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.debug("用户关注分页已缓存，targetUserId: {}, cacheKey: {}, 过期时间: {}分钟", 
                         userId, cacheKey, ANONYMOUS_CACHE_EXPIRE_MINUTES);
            } catch (Exception e) {
                log.warn("缓存用户关注分页失败，targetUserId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            return SaResult.code(200).data(pageResult);
        }
        
        // 查看自己的关注，不使用缓存（保证数据实时性）
        IPage<UserFollowDetailDto> pageResult = userFollowService.getFollowingPageResult(userId, pageNum, pageSize);
        return SaResult.code(200).data(pageResult);
    }
}