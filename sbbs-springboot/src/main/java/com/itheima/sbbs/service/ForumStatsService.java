package com.itheima.sbbs.service;

import com.itheima.sbbs.entity.ForumStatsDto;
 
public interface ForumStatsService {
    ForumStatsDto getForumStats();
    void clearForumStatsCache();
} 