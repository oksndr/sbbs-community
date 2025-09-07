package com.itheima.sbbs.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.sbbs.entity.Tag;
import com.itheima.sbbs.mapper.TagMapper;
import com.itheima.sbbs.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String ALL_TAGS_CACHE_KEY = "allTags";

    // 实现获取所有标签的方法
    @Override
    public List<Tag> getAllTags() {
        // 尝试从缓存读取
        List<Tag> cachedList = (List<Tag>) redisTemplate.opsForValue().get(ALL_TAGS_CACHE_KEY);
        if (cachedList != null) {
            return cachedList;
        }

        // 缓存未命中，从数据库查询
        List<Tag> tagList = list();

        // 将结果存入缓存，设置过期时间（例如 24 小时）
        redisTemplate.opsForValue().set(ALL_TAGS_CACHE_KEY, tagList, 24, TimeUnit.HOURS);

        return tagList;
    }

    // 覆盖 save 方法，在添加/更新标签后清除缓存
    @Override
    public boolean save(Tag entity) {
        boolean success = super.save(entity);
        if (success) {
            clearAllTagsCache();
        }
        return success;
    }

    // 覆盖 updateById 方法，在更新标签后清除缓存
    @Override
    public boolean updateById(Tag entity) {
        boolean success = super.updateById(entity);
        if (success) {
            clearAllTagsCache();
        }
        return success;
    }

    // 覆盖 removeById 方法，在删除标签后清除缓存
    @Override
    public boolean removeById(java.io.Serializable id) {
        boolean success = super.removeById(id);
        if (success) {
            clearAllTagsCache();
        }
        return success;
    }

    // 辅助方法：清除所有标签缓存
    private void clearAllTagsCache() {
        redisTemplate.delete(ALL_TAGS_CACHE_KEY);
    }
} 