package com.itheima.sbbs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.sbbs.entity.Like;

public interface LikeService extends IService<Like> {

    void like(Like like);

    void cancelLike(Like like);

    void dislike(Like like);

    void cancelDislike(Like like);

    void likeComment(Like like);

    void dislikeComment(Like like);

    void cancelLikeComment(Like like);

    void cancelDislikeComment(Like like);

    // 异步版本方法（传递用户ID，避免StpUtil在非Web上下文中的问题）
    void likeCommentAsync(Like like, Integer currentUserId);

    void dislikeCommentAsync(Like like, Integer currentUserId);

    void cancelLikeCommentAsync(Like like, Integer currentUserId);

    void cancelDislikeCommentAsync(Like like, Integer currentUserId);
}
