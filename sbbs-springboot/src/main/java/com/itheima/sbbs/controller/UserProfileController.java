package com.itheima.sbbs.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.itheima.sbbs.entity.UserProfileDto;
import com.itheima.sbbs.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@Slf4j
@RequestMapping("/v2")
@RestController
public class UserProfileController {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 缓存键前缀
    private static final String ANONYMOUS_USER_PROFILE_CACHE_PREFIX = "anonymous:user_profile:";
    private static final int ANONYMOUS_CACHE_EXPIRE_MINUTES = 5; // 5分钟缓存

    /**
     * 获取用户主页信息
     * 🚀 优化：未登录用户使用Redis缓存5分钟，防止攻击
     * @param userId 用户ID
     * @param pageNo 页码，默认1
     * @param pageSize 每页数量，默认15
     * @return 用户主页信息
     */
    @GetMapping("/user/{userId}")
    public SaResult getUserProfile(
            @PathVariable Integer userId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "15") Integer pageSize) {
            
        if (userId == null || userId <= 0) {
            return SaResult.error("无效的用户ID");
        }
        
        // 🚀 检查是否登录
        if (!StpUtil.isLogin()) {
            // 未登录用户走缓存策略
            String cacheKey = ANONYMOUS_USER_PROFILE_CACHE_PREFIX + userId + ":" + pageNo + ":" + pageSize;
            
            try {
                Object cachedResult = redisTemplate.opsForValue().get(cacheKey);
                if (cachedResult != null && cachedResult instanceof UserProfileDto) {
                    log.info("未登录用户个人主页缓存命中，userId: {}, cacheKey: {}", userId, cacheKey);
                    return SaResult.code(200).data(cachedResult);
                }
            } catch (Exception e) {
                log.info("获取个人主页缓存失败，将查询数据库，userId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            // 缓存未命中，查询数据库
            UserProfileDto userProfile = userService.getUserProfile(userId, pageNo, pageSize);
            
            if (userProfile == null) {
                return SaResult.error("用户不存在或已被删除");
            }
            
            // 将结果缓存5分钟
            try {
                redisTemplate.opsForValue().set(cacheKey, userProfile, ANONYMOUS_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.debug("未登录用户个人主页已缓存，userId: {}, cacheKey: {}, 过期时间: {}分钟", 
                         userId, cacheKey, ANONYMOUS_CACHE_EXPIRE_MINUTES);
            } catch (Exception e) {
                log.warn("缓存个人主页失败，userId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            return SaResult.code(200).data(userProfile);
        }
        
        // 已登录用户正常查询（维持现状）
        UserProfileDto userProfile = userService.getUserProfile(userId, pageNo, pageSize);
        
        if (userProfile == null) {
            return SaResult.error("用户不存在或已被删除");
        }
        
        return SaResult.code(200).data(userProfile);
    }
} 