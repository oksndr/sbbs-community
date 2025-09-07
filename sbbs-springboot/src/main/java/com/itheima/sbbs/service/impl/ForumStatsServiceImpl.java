package com.itheima.sbbs.service.impl;

import com.itheima.sbbs.entity.ForumStatsDto;
import com.itheima.sbbs.service.ForumStatsService;
import com.itheima.sbbs.service.PostService;
import com.itheima.sbbs.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class ForumStatsServiceImpl implements ForumStatsService {

    @Autowired
    private UserService userService;

    @Autowired
    private PostService postService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String FORUM_STATS_CACHE_KEY = "forumStats";
    private static final long CACHE_TIMEOUT_MINUTES = 60;

    @Override
    public ForumStatsDto getForumStats() {
        ForumStatsDto stats = (ForumStatsDto) redisTemplate.opsForValue().get(FORUM_STATS_CACHE_KEY);
        if (stats == null) {
            stats = new ForumStatsDto();
            stats.setTotalUsers(userService.getTotalUserCount());
            stats.setNewUsersToday(userService.getNewUserCountToday());
            stats.setTotalPosts(postService.getTotalPostCount());
            stats.setNewPostsToday(postService.getNewPostCountToday());
            redisTemplate.opsForValue().set(FORUM_STATS_CACHE_KEY, stats, CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        }
        return stats;
    }

    @Override
    public void clearForumStatsCache() {
        redisTemplate.delete(FORUM_STATS_CACHE_KEY);
    }
} 