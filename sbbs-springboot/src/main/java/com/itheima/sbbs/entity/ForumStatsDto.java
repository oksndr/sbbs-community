package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForumStatsDto {
    private long totalUsers;
    private long newUsersToday;
    private long totalPosts;
    private long newPostsToday;
} 