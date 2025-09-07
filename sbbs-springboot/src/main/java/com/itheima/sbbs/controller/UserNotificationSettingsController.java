package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.itheima.sbbs.entity.User;
import com.itheima.sbbs.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户通知设置控制器
 */
@RestController
@RequestMapping("/api/user/notifications")
@Slf4j
public class UserNotificationSettingsController {

    @Autowired
    private UserService userService;

    /**
     * 获取当前用户的通知设置
     */
    @GetMapping("/settings")
    @SaCheckLogin
    public SaResult getNotificationSettings() {
        try {
            Integer userId = StpUtil.getLoginIdAsInt();
            User user = userService.getById(userId);
            
            if (user == null) {
                return SaResult.error("用户不存在");
            }
            
            Map<String, Object> settings = new HashMap<>();
            settings.put("enableLikeNotification", user.getEnableLikeNotification() != null ? user.getEnableLikeNotification() : true);
            settings.put("enableOtherNotification", user.getEnableOtherNotification() != null ? user.getEnableOtherNotification() : true);
            
            return SaResult.data(settings);
        } catch (Exception e) {
            log.error("获取用户通知设置失败", e);
            return SaResult.error("获取通知设置失败");
        }
    }

    /**
     * 更新用户的通知设置
     */
    @PostMapping("/settings")
    @SaCheckLogin
    public SaResult updateNotificationSettings(@RequestBody Map<String, Boolean> settings) {
        try {
            Integer userId = StpUtil.getLoginIdAsInt();
            User user = userService.getById(userId);
            
            if (user == null) {
                return SaResult.error("用户不存在");
            }
            
            // 更新通知设置
            Boolean enableLikeNotification = settings.get("enableLikeNotification");
            Boolean enableOtherNotification = settings.get("enableOtherNotification");
            
            if (enableLikeNotification != null) {
                user.setEnableLikeNotification(enableLikeNotification);
                log.info("用户 {} 更新点赞通知设置为: {}", userId, enableLikeNotification);
            }
            
            if (enableOtherNotification != null) {
                user.setEnableOtherNotification(enableOtherNotification);
                log.info("用户 {} 更新其他通知设置为: {}", userId, enableOtherNotification);
            }
            
            boolean updated = userService.updateById(user);
            
            if (updated) {
                // 清除用户缓存，确保设置立即生效
                userService.clearUserCache(userId);
                return SaResult.ok("通知设置更新成功");
            } else {
                return SaResult.error("通知设置更新失败");
            }
            
        } catch (Exception e) {
            log.error("更新用户通知设置失败", e);
            return SaResult.error("更新通知设置失败");
        }
    }

    /**
     * 快速开启/关闭点赞通知
     */
    @PostMapping("/like-notification/{enabled}")
    @SaCheckLogin
    public SaResult toggleLikeNotification(@PathVariable Boolean enabled) {
        try {
            Integer userId = StpUtil.getLoginIdAsInt();
            User user = userService.getById(userId);
            
            if (user == null) {
                return SaResult.error("用户不存在");
            }
            
            user.setEnableLikeNotification(enabled);
            boolean updated = userService.updateById(user);
            
            if (updated) {
                userService.clearUserCache(userId);
                String action = enabled ? "开启" : "关闭";
                log.info("用户 {} {} 了点赞通知", userId, action);
                return SaResult.ok(action + "点赞通知成功");
            } else {
                return SaResult.error("设置失败");
            }
            
        } catch (Exception e) {
            log.error("切换点赞通知设置失败", e);
            return SaResult.error("设置失败");
        }
    }

    /**
     * 快速开启/关闭其他通知
     */
    @PostMapping("/other-notification/{enabled}")
    @SaCheckLogin
    public SaResult toggleOtherNotification(@PathVariable Boolean enabled) {
        try {
            Integer userId = StpUtil.getLoginIdAsInt();
            User user = userService.getById(userId);
            
            if (user == null) {
                return SaResult.error("用户不存在");
            }
            
            user.setEnableOtherNotification(enabled);
            boolean updated = userService.updateById(user);
            
            if (updated) {
                userService.clearUserCache(userId);
                String action = enabled ? "开启" : "关闭";
                log.info("用户 {} {} 了其他邮件通知", userId, action);
                return SaResult.ok(action + "其他通知成功");
            } else {
                return SaResult.error("设置失败");
            }
            
        } catch (Exception e) {
            log.error("切换其他通知设置失败", e);
            return SaResult.error("设置失败");
        }
    }
} 