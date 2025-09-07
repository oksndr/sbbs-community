package com.itheima.sbbs.controller;

import cn.dev33.satoken.util.SaResult;
import com.itheima.sbbs.entity.ForumStatsDto;
import com.itheima.sbbs.service.ForumStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/stats") // 使用 /v1 前缀与其他接口保持一致
public class StatsController {

    @Autowired
    private ForumStatsService forumStatsService;

    @GetMapping("/overview")
    public SaResult getForumOverview() {
        ForumStatsDto stats = forumStatsService.getForumStats();
        return SaResult.data(stats);
    }
} 