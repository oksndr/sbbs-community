package com.itheima.sbbs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.sbbs.entity.UserLevel;
import com.itheima.sbbs.mapper.UserLevelMapper;
import com.itheima.sbbs.service.UserLevelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserLevelServiceImpl extends ServiceImpl<UserLevelMapper, UserLevel> implements UserLevelService {

    @Autowired
    private UserLevelMapper userLevelMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String USER_LEVELS_CACHE_KEY = "userLevels:all";
    private static final long CACHE_EXPIRE_HOURS = 24; // 缓存24小时

    @Override
    public UserLevel getLevelByExperience(Integer experience) {
        if (experience == null || experience < 0) {
            experience = 0;
        }

        // 🚀 优化：获取所有等级配置，在内存中计算匹配
        List<UserLevel> allLevels = getAllLevels();
        if (allLevels == null || allLevels.isEmpty()) {
            return null;
        }

        // 在内存中找到对应等级（按等级从低到高排序）
        UserLevel matchedLevel = null;
        for (UserLevel level : allLevels) {
            if (experience >= level.getMinExperience() && 
                (level.getMaxExperience() == null || experience <= level.getMaxExperience())) {
                matchedLevel = level;
                break;
            }
        }

        // 如果没找到，返回最低等级
        if (matchedLevel == null && !allLevels.isEmpty()) {
            matchedLevel = allLevels.get(0);
        }

        log.debug("根据经验值{}计算等级: {}", experience, 
                 matchedLevel != null ? matchedLevel.getLevel() : "未知");
        
        return matchedLevel;
    }

    @Override
    public List<UserLevel> getAllLevels() {
        try {
            // 尝试从缓存获取
            @SuppressWarnings("unchecked")
            List<UserLevel> cachedLevels = (List<UserLevel>) redisTemplate.opsForValue().get(USER_LEVELS_CACHE_KEY);
            if (cachedLevels != null && !cachedLevels.isEmpty()) {
                log.debug("从缓存获取所有等级配置，数量: {}", cachedLevels.size());
                // 自动续期
                redisTemplate.expire(USER_LEVELS_CACHE_KEY, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                return cachedLevels;
            }
        } catch (Exception e) {
            log.warn("获取等级配置缓存失败: {}", e.getMessage());
        }

        // 缓存未命中，从数据库查询
        LambdaQueryWrapper<UserLevel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLevel::getDeleted, 0)
               .orderByAsc(UserLevel::getLevel);
        List<UserLevel> levels = baseMapper.selectList(wrapper);

        if (levels != null && !levels.isEmpty()) {
            try {
                // 缓存结果
                redisTemplate.opsForValue().set(USER_LEVELS_CACHE_KEY, levels, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                log.info("缓存所有等级配置，数量: {}", levels.size());
            } catch (Exception e) {
                log.warn("缓存等级配置失败: {}", e.getMessage());
            }
        }

        return levels;
    }

    @Override
    public void clearLevelCache() {
        try {
            // 🚀 优化：只需要清除等级配置缓存
            redisTemplate.delete(USER_LEVELS_CACHE_KEY);
            log.info("清除等级缓存成功");
        } catch (Exception e) {
            log.warn("清除等级缓存失败: {}", e.getMessage());
        }
    }

    @Override
    public UserLevel getNextLevel(Integer currentLevel) {
        if (currentLevel == null) {
            return null;
        }
        
        try {
            List<UserLevel> allLevels = getAllLevels();
            return allLevels.stream()
                    .filter(level -> level.getLevel().equals(currentLevel + 1))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("获取下一级等级失败，当前等级: {}", currentLevel, e);
            return null;
        }
    }
} 