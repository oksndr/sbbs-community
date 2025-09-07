package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于展示post详情
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostRush {
    private PostWithUserDto post;
    private boolean liked;
    private boolean disLiked;
}
