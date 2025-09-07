package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.util.SaResult;
import com.itheima.sbbs.entity.UserSimpleDto;
import com.itheima.sbbs.entity.UserLevel;
import com.itheima.sbbs.service.UserService;
import com.itheima.sbbs.service.UserLevelService;
import com.itheima.sbbs.service.ExperienceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users") // 为用户相关的接口设置基础路径
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserLevelService userLevelService;
    
    @Autowired
    private ExperienceService experienceService;

    /**
     * 根据关键词搜索用户，用于 @ 提及功能
     * @param keyword 搜索关键词
     * @return 匹配的用户列表 (简略信息)
     */
    @GetMapping("/search")
    @SaCheckLogin
    public SaResult searchUsers(@RequestParam(required = false) String keyword) {
        // 处理空关键词的情况，转换为null
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        
        List<UserSimpleDto> users = userService.searchUsers(keyword);
        return SaResult.code(200).data(users);
    }
    
    /**
     * 获取用户等级信息
     * @param userId 用户ID，如果不传则获取当前登录用户的等级信息
     * @return 用户等级信息
     */
    @GetMapping("/level")
    @SaCheckLogin
    public SaResult getUserLevelInfo(@RequestParam(required = false) Integer userId) {
        // 如果没有传userId，则获取当前登录用户的ID
        if (userId == null) {
            userId = StpUtil.getLoginIdAsInt();
        }
        
        Map<String, Object> levelInfo = userService.getUserLevelInfo(userId);
        
        if (levelInfo == null) {
            return SaResult.error("用户不存在或已被删除");
        }
        
        return SaResult.code(200).data(levelInfo);
    }
    
    /**
     * 管理员增加用户经验值
     * @param userId 用户ID
     * @param experience 增加的经验值
     * @return 操作结果
     */
    @PostMapping("/experience/add")
    @SaCheckRole("admin")
    public SaResult addUserExperience(@RequestParam Integer userId, @RequestParam Integer experience) {
        if (userId == null || userId <= 0) {
            return SaResult.error("无效的用户ID");
        }
        
        if (experience == null || experience <= 0) {
            return SaResult.error("经验值必须大于0");
        }
        
        if (experience > 1000) {
            return SaResult.error("单次增加经验值不能超过1000");
        }
        
        // 🎉 获取经验值变化前的值，用于升级检测
        Integer oldExp = experienceService.getUserExperience(userId);
        
        boolean success = userService.addUserExperience(userId, experience);
        
        if (success) {
            // 🎉 管理员调整经验值后检查升级
            Integer newExp = oldExp + experience;
            experienceService.checkAndNotifyLevelUp(userId, oldExp, newExp);
            
            // 返回更新后的等级信息
            Map<String, Object> levelInfo = userService.getUserLevelInfo(userId);
            return SaResult.code(200).setMsg("经验值增加成功").data(levelInfo);
        } else {
            return SaResult.error("经验值增加失败，请检查用户是否存在");
        }
    }
    
    /**
     * 管理员减少用户经验值
     * @param userId 用户ID
     * @param experience 减少的经验值
     * @return 操作结果
     */
    @PostMapping("/experience/reduce")
    @SaCheckRole("admin")
    public SaResult reduceUserExperience(@RequestParam Integer userId, @RequestParam Integer experience) {
        if (userId == null || userId <= 0) {
            return SaResult.error("无效的用户ID");
        }
        
        if (experience == null || experience <= 0) {
            return SaResult.error("经验值必须大于0");
        }
        
        if (experience > 1000) {
            return SaResult.error("单次减少经验值不能超过1000");
        }
        
        // 🎉 获取经验值变化前的值，用于升级检测
        Integer oldExp = experienceService.getUserExperience(userId);
        
        boolean success = userService.reduceUserExperience(userId, experience);
        
        if (success) {
            // 🎉 管理员调整经验值后检查升级（虽然是减少，但保持一致性）
            Integer newExp = Math.max(0, oldExp - experience);
            experienceService.checkAndNotifyLevelUp(userId, oldExp, newExp);
            
            // 返回更新后的等级信息
            Map<String, Object> levelInfo = userService.getUserLevelInfo(userId);
            return SaResult.code(200).setMsg("经验值减少成功").data(levelInfo);
        } else {
            return SaResult.error("经验值减少失败，请检查用户是否存在");
        }
    }
    
    /**
     * 获取等级系统说明
     * @return 等级系统的详细说明
     */
    @GetMapping("/level/info")
    public SaResult getLevelSystemInfo() {
        Map<String, Object> levelSystem = new java.util.HashMap<>();
        
        // 从数据库获取等级列表
        List<UserLevel> userLevels = userLevelService.getAllLevels();
        java.util.List<Map<String, Object>> levels = new java.util.ArrayList<>();
        
        for (UserLevel userLevel : userLevels) {
            Map<String, Object> levelMap = new java.util.HashMap<>();
            levelMap.put("level", userLevel.getLevel());
            levelMap.put("name", userLevel.getName());
            levelMap.put("minExp", userLevel.getMinExperience());
            levelMap.put("maxExp", userLevel.getMaxExperience() != null ? userLevel.getMaxExperience() : "无上限");
            levels.add(levelMap);
        }
        
        levelSystem.put("levels", levels);
        
        // 经验值获取规则
        Map<String, Object> expRules = new java.util.HashMap<>();
        expRules.put("publishPost", "+10经验 (发布帖子)");
        expRules.put("publishComment", "+5经验 (发表评论)");
        expRules.put("postLiked", "+3经验 (帖子被点赞)");
        expRules.put("commentLiked", "+2经验 (评论被点赞)");
        expRules.put("postDisliked", "-1经验 (帖子被点踩)");
        expRules.put("firstPost", "+20经验 (首次发帖奖励)");
        
        levelSystem.put("expRules", expRules);
        
        return SaResult.code(200).data(levelSystem);
    }
} 