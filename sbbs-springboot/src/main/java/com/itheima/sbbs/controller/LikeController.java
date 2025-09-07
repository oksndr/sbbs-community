package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itheima.sbbs.annotation.Debounce;
import com.itheima.sbbs.entity.Like;
// 🗑️ **已删除LikeOperation - 不再使用队列**
// import com.itheima.sbbs.entity.LikeOperation;
import com.itheima.sbbs.service.ExperienceService;
// 🗑️ **已删除LikeQueueService导入 - 服务已删除**
// import com.itheima.sbbs.service.LikeQueueService;
import com.itheima.sbbs.service.LikeService;
import com.itheima.sbbs.service.impl.LikeServiceImpl;
import com.itheima.sbbs.common.BussinessException;

// 🗑️ **已删除LocalDateTime - 不再使用队列时间戳**
// import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
// 🗑️ **已删除分布式锁相关导入**
// import java.util.concurrent.TimeUnit;
// import org.springframework.data.redis.core.RedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/v4")
public class LikeController {

    @Autowired
    private LikeServiceImpl likeService;

    @Autowired
    private ExperienceService experienceService;

    // 🗑️ **已删除LikeQueueService - 改为直接数据库操作**
    // @Autowired
    // private LikeQueueService likeQueueService;

    // 🗑️ **已删除LikeCacheService - 移除复杂缓存同步机制**
    // @Autowired
    // private LikeCacheService likeCacheService;

    // 🗑️ **已删除PostService - 不再需要复杂状态检查**
    // @Autowired
    // private PostService postService;

    // 🗑️ **已删除LikeMapper - 直接通过Service操作**
    // @Autowired
    // private LikeMapper likeMapper;

    // 🗑️ **已删除RedisTemplate - 不再使用分布式锁**
    // @Autowired
    // private RedisTemplate<String, Object> redisTemplate;


    /**
     * 点赞帖子
     * 传入post Id
     */
    @SaCheckLogin
    @Debounce(timeout = 2, keyPrefix = "post:like", message = "点赞操作过于频繁，请稍后再试")
    @GetMapping("/post/like/{id}")
    public SaResult likePost(@PathVariable("id") Integer id) {
        int loginId = StpUtil.getLoginIdAsInt();//用户唯一id
        log.info("=== 开始处理帖子点赞请求，用户ID: {}, 帖子ID: {} ===", loginId, id);

        // 计算点赞可获得的经验值
        int expGained = 0;
        try {
            expGained = experienceService.calculateFirstLikeExperience(loginId);
            log.debug("计算点赞经验值完成，用户ID: {}, 可获得经验值: {}", loginId, expGained);
        } catch (Exception e) {
            log.warn("计算点赞经验值失败，用户ID: {}", loginId, e);
        }

        try {
            // 🚀 **直接数据库操作，删除复杂缓存同步机制**
            Like like = new Like();
            like.setUserId(loginId);
            like.setPostId(id);
            like.setType(1); // 点赞

            log.info("正在直接执行帖子点赞数据库操作，用户ID: {}, 帖子ID: {}", loginId, id);
            likeService.like(like);
            log.info("✅ 帖子点赞数据库操作完成，用户ID: {}, 帖子ID: {}", loginId, id);

        } catch (BussinessException e) {
            // 🎯 **友好处理重复点赞等业务异常**
            if (e.getMessage().contains("你已经点过赞了")) {
                log.info("用户尝试重复点赞，用户ID: {}, 帖子ID: {}", loginId, id);
                return SaResult.ok("您已经点过赞了~"); // 返回成功状态，避免前端显示错误
            }
            log.warn("帖子点赞业务异常，用户ID: {}, 帖子ID: {}, 错误: {}", loginId, id, e.getMessage());
            return SaResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("帖子点赞操作失败，用户ID: {}, 帖子ID: {}", loginId, id, e);
            return SaResult.error("点赞失败，请稍后重试");
        }

        // 如果可以获得经验值，则添加首次点赞经验值
        if (expGained > 0) {
            try {
                experienceService.addFirstLikeExperience(loginId);
            } catch (Exception e) {
                log.warn("添加首次点赞经验值失败，用户ID: {}", loginId, e);
            }
        }

        // 构建成功消息，包含经验值信息
        String message = "点赞成功";
        if (expGained > 0) {
            message += "，获得 " + expGained + " 经验值";
        }

        return SaResult.ok(message);
    }

    /**
     * 取消点赞帖子
     * 传入post Id
     */
    @SaCheckLogin
    @Debounce(timeout = 2, keyPrefix = "post:cancelLike", message = "取消点赞操作过于频繁，请稍后再试")
    @GetMapping("/post/cancelLike/{id}")
    public SaResult cancelLPost(@PathVariable("id") Integer id) {
        int loginId = StpUtil.getLoginIdAsInt();//用户唯一id

        try {
            // 🚀 **直接数据库操作，删除复杂缓存同步机制**
            Like like = new Like();
            like.setUserId(loginId);
            like.setPostId(id);

            log.info("正在直接执行取消帖子点赞数据库操作，用户ID: {}, 帖子ID: {}", loginId, id);
            likeService.cancelLike(like);
            log.info("✅ 取消帖子点赞数据库操作完成，用户ID: {}, 帖子ID: {}", loginId, id);

        } catch (Exception e) {
            log.error("取消帖子点赞操作失败，用户ID: {}, 帖子ID: {}", loginId, id, e);
            return SaResult.error("取消点赞失败，请稍后重试");
        }

        return SaResult.ok("取消点赞成功");
    }

    /**
     * 点踩帖子
     * 传入post Id
     */
    @SaCheckLogin
    @Debounce(timeout = 2, keyPrefix = "post:dislike", message = "点踩操作过于频繁，请稍后再试")
    @GetMapping("/post/dislike/{id}")
    public SaResult dislikePost(@PathVariable("id") Integer id) {
        int loginId = StpUtil.getLoginIdAsInt();//用户唯一id

        try {
            // 🚀 **直接数据库操作，删除复杂缓存同步机制**
            Like like = new Like();
            like.setUserId(loginId);
            like.setPostId(id);
            like.setType(-1); // 点踩

            log.info("正在直接执行帖子点踩数据库操作，用户ID: {}, 帖子ID: {}", loginId, id);
            likeService.dislike(like);
            log.info("✅ 帖子点踩数据库操作完成，用户ID: {}, 帖子ID: {}", loginId, id);

        } catch (Exception e) {
            log.error("帖子点踩操作失败，用户ID: {}, 帖子ID: {}", loginId, id, e);
            return SaResult.error("点踩失败，请稍后重试");
        }

        return SaResult.ok("点踩成功");
    }

    /**
     * 取消点踩帖子
     * 传入post Id
     */
    @SaCheckLogin
    @Debounce(timeout = 2, keyPrefix = "post:cancelDislike", message = "取消点踩操作过于频繁，请稍后再试")
    @GetMapping("/post/cancelDislike/{id}")
    public SaResult cancelDislikePost(@PathVariable("id") Integer id) {
        int loginId = StpUtil.getLoginIdAsInt();//用户唯一id

        try {
            // 🚀 **直接数据库操作，删除复杂缓存同步机制**
            Like like = new Like();
            like.setUserId(loginId);
            like.setPostId(id);

            log.info("正在直接执行取消帖子点踩数据库操作，用户ID: {}, 帖子ID: {}", loginId, id);
            likeService.cancelDislike(like);
            log.info("✅ 取消帖子点踩数据库操作完成，用户ID: {}, 帖子ID: {}", loginId, id);

        } catch (Exception e) {
            log.error("取消帖子点踩操作失败，用户ID: {}, 帖子ID: {}", loginId, id, e);
            return SaResult.error("取消点踩失败，请稍后重试");
        }

        return SaResult.ok("取消点踩成功");
    }

    /**
     * 点赞评论（简化版：直接数据库操作）
     * 传入comment Id
     */
    @SaCheckLogin
    @Debounce(timeout = 2, keyPrefix = "comment:like", message = "点赞操作过于频繁，请稍后再试")
    @GetMapping("/comment/like/{id}")
    public SaResult likeComment(@PathVariable("id") Integer id) {
        int loginId = StpUtil.getLoginIdAsInt();
        log.info("开始处理评论点赞，用户ID: {}, 评论ID: {}", loginId, id);

        // 🔧 修复BUG: 先计算经验值，但不立即添加
        int expGained = 0;
        try {
            expGained = experienceService.calculateFirstLikeExperience(loginId);
            log.debug("计算首次点赞经验值完成，用户ID: {}, 可获得经验值: {}", loginId, expGained);
        } catch (Exception e) {
            log.warn("计算首次点赞经验值失败，用户ID: {}", loginId, e);
        }

        try {
            // 🚀 **直接数据库操作，删除复杂缓存同步机制**
            Like like = new Like();
            like.setUserId(loginId);
            like.setCommentId(id);
            like.setType(1); // 点赞

            log.info("正在直接执行评论点赞数据库操作，用户ID: {}, 评论ID: {}", loginId, id);
            likeService.likeComment(like);
            log.info("✅ 评论点赞数据库操作完成，用户ID: {}, 评论ID: {}", loginId, id);

        } catch (Exception e) {
            log.error("评论点赞操作失败，用户ID: {}, 评论ID: {}", loginId, id, e);
            return SaResult.error("点赞失败，请稍后重试");
        }

        // 🔧 修复BUG: 只有点赞成功后才添加经验值
        if (expGained > 0) {
            try {
                experienceService.addFirstLikeExperience(loginId);
                log.info("🎉 用户 {} 今日首次点赞成功，获得 {} 经验值", loginId, expGained);
            } catch (Exception e) {
                log.warn("添加首次点赞经验值失败，用户ID: {}", loginId, e);
            }
        }

        // 构建成功消息
        String message = "点赞成功";
        if (expGained > 0) {
            message += "，获得 " + expGained + " 经验值";
        }

        return SaResult.ok(message);
    }

    /**
     * 点踩评论（简化版：直接数据库操作）
     * 传入comment Id
     */
    @SaCheckLogin
    @Debounce(timeout = 2, keyPrefix = "comment:dislike", message = "点踩操作过于频繁，请稍后再试")
    @GetMapping("/comment/dislike/{id}")
    public SaResult dislikeComment(@PathVariable("id") Integer id) {
        int loginId = StpUtil.getLoginIdAsInt();
        log.info("开始处理评论点踩，用户ID: {}, 评论ID: {}", loginId, id);

        try {
            // 🚀 **直接数据库操作，删除复杂异步逻辑**
            Like like = new Like();
            like.setUserId(loginId);
            like.setCommentId(id);
            like.setType(-1); // 点踩

            log.info("正在直接执行评论点踩数据库操作，用户ID: {}, 评论ID: {}", loginId, id);
            likeService.dislikeComment(like);
            log.info("✅ 评论点踩数据库操作完成，用户ID: {}, 评论ID: {}", loginId, id);

        } catch (Exception e) {
            log.error("评论点踩操作失败，用户ID: {}, 评论ID: {}", loginId, id, e);
            return SaResult.error("点踩失败，请稍后重试");
        }

        return SaResult.ok("点踩成功");
    }

    /**
     * 取消点赞评论（简化版：直接数据库操作）
     * 传入comment Id
     */
    @SaCheckLogin
    @Debounce(timeout = 2, keyPrefix = "comment:cancelLike", message = "取消点赞操作过于频繁，请稍后再试")
    @GetMapping("/comment/cancelLike/{id}")
    public SaResult cancelLikeComment(@PathVariable("id") Integer id) {
        int loginId = StpUtil.getLoginIdAsInt();
        log.info("开始处理取消评论点赞，用户ID: {}, 评论ID: {}", loginId, id);

        try {
            // 🚀 **直接数据库操作，删除复杂异步逻辑**
            Like like = new Like();
            like.setUserId(loginId);
            like.setCommentId(id);

            log.info("正在直接执行取消评论点赞数据库操作，用户ID: {}, 评论ID: {}", loginId, id);
            likeService.cancelLikeComment(like);
            log.info("✅ 取消评论点赞数据库操作完成，用户ID: {}, 评论ID: {}", loginId, id);

        } catch (Exception e) {
            log.error("取消评论点赞操作失败，用户ID: {}, 评论ID: {}", loginId, id, e);
            return SaResult.error("取消点赞失败，请稍后重试");
        }

        return SaResult.ok("取消点赞成功");
    }

    /**
     * 取消点踩评论（简化版：直接数据库操作）
     * 传入comment Id
     */
    @SaCheckLogin
    @Debounce(timeout = 2, keyPrefix = "comment:cancelDislike", message = "取消点踩操作过于频繁，请稍后再试")
    @GetMapping("/comment/cancelDislike/{id}")
    public SaResult cancelDislikeComment(@PathVariable("id") Integer id) {
        int loginId = StpUtil.getLoginIdAsInt();
        log.info("开始处理取消评论点踩，用户ID: {}, 评论ID: {}", loginId, id);

        try {
            // 🚀 **直接数据库操作，删除复杂异步逻辑**
            Like like = new Like();
            like.setUserId(loginId);
            like.setCommentId(id);

            log.info("正在直接执行取消评论点踩数据库操作，用户ID: {}, 评论ID: {}", loginId, id);
            likeService.cancelDislikeComment(like);
            log.info("✅ 取消评论点踩数据库操作完成，用户ID: {}, 评论ID: {}", loginId, id);

        } catch (Exception e) {
            log.error("取消评论点踩操作失败，用户ID: {}, 评论ID: {}", loginId, id, e);
            return SaResult.error("取消点踩失败，请稍后重试");
        }

        return SaResult.ok("取消点踩成功");
    }

}
