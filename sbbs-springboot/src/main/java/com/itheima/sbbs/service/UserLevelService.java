package com.itheima.sbbs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.sbbs.entity.UserLevel;
import java.util.List;

public interface UserLevelService extends IService<UserLevel> {
    
    /**
     * 根据经验值获取用户等级
     * @param experience 经验值
     * @return 用户等级配置
     */
    UserLevel getLevelByExperience(Integer experience);
    
    /**
     * 获取所有等级配置（带缓存）
     * @return 等级配置列表
     */
    List<UserLevel> getAllLevels();
    
    /**
     * 清除等级缓存
     */
    void clearLevelCache();
    
    /**
     * 获取下一级等级信息
     * @param currentLevel 当前等级
     * @return 下一级等级配置，如果没有则返回null
     */
    UserLevel getNextLevel(Integer currentLevel);
} 