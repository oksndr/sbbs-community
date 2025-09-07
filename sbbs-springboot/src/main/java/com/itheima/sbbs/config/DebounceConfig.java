package com.itheima.sbbs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 防抖配置类
 */
@Configuration
@ConfigurationProperties(prefix = "app.debounce")
public class DebounceConfig {

    /**
     * 点赞操作防抖时间（秒）
     */
    private long likeTimeout = 2;

    /**
     * 评论操作防抖时间（秒）
     */
    private long commentTimeout = 5;

    /**
     * 发帖操作防抖时间（秒）
     */
    private long postTimeout = 10;

    /**
     * 点踩操作防抖时间（秒）
     */
    private long dislikeTimeout = 2;

    // Getters and Setters
    public long getLikeTimeout() {
        return likeTimeout;
    }

    public void setLikeTimeout(long likeTimeout) {
        this.likeTimeout = likeTimeout;
    }

    public long getCommentTimeout() {
        return commentTimeout;
    }

    public void setCommentTimeout(long commentTimeout) {
        this.commentTimeout = commentTimeout;
    }

    public long getPostTimeout() {
        return postTimeout;
    }

    public void setPostTimeout(long postTimeout) {
        this.postTimeout = postTimeout;
    }

    public long getDislikeTimeout() {
        return dislikeTimeout;
    }

    public void setDislikeTimeout(long dislikeTimeout) {
        this.dislikeTimeout = dislikeTimeout;
    }
}
