package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.alibaba.druid.util.StringUtils;
import com.itheima.sbbs.common.BussinessException;
import com.itheima.sbbs.entity.Comment;
import com.itheima.sbbs.entity.CommentDto;
import com.itheima.sbbs.entity.CommentLocationDto;
import com.itheima.sbbs.entity.CommentWithUserDto;
import com.itheima.sbbs.entity.ErrorResult;
import com.itheima.sbbs.entity.UserCommentDto;
import com.itheima.sbbs.service.CommentService;
import com.itheima.sbbs.service.ExperienceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/v3")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 缓存键前缀
    private static final String ANONYMOUS_USER_COMMENTS_CACHE_PREFIX = "anonymous:user_comments:";
    private static final int ANONYMOUS_CACHE_EXPIRE_MINUTES = 5; // 5分钟缓存

    /**
     * 发布评论接口
     * 评论post和二级评论共用接口
     */
    @SaCheckLogin
    @PostMapping("/comment")
    public SaResult comment(@RequestBody Comment comment) {
        //简单验证参数
        if (comment == null || comment.getPostId() == null || StringUtils.isEmpty(comment.getContent())) {
            return SaResult.error("请不要恶意调试接口");
        }
        comment.setUserId(StpUtil.getLoginIdAsInt());

        // 计算评论经验值
        int expGained = 0;
        try {
            expGained = experienceService.calculateCommentExperience(comment.getUserId());
        } catch (Exception e) {
            log.warn("计算评论经验值失败，用户ID: {}", comment.getUserId(), e);
        }

        // 构建成功消息，包含经验值信息
        String message = "评论发布成功";
        if (expGained > 0) {
            message += "，获得 " + expGained + " 经验值";
        }

        //根据评论类型分别进行保存操作
        if (comment.getParentId() == null || comment.getParentId() == 0) {
            //这是一条一级评论
            CommentLocationDto locationDto = commentService.saveTopComment(comment);

            // 异步添加评论经验值
            try {
                experienceService.addCommentExperience(comment.getUserId());
            } catch (Exception e) {
                log.warn("添加评论经验值失败，用户ID: {}", comment.getUserId(), e);
            }

            return SaResult.code(200).data(locationDto).setMsg(message);
        } else {
            //这是二级评论
            commentService.saveSndComment(comment);

            // 异步添加评论经验值
            try {
                experienceService.addCommentExperience(comment.getUserId());
            } catch (Exception e) {
                log.warn("添加评论经验值失败，用户ID: {}", comment.getUserId(), e);
            }

            return SaResult.ok(message);
        }
    }

    /**
     * 分页查询top评论
     * 需要传入pageNum, postId
     */
    @SaCheckLogin
    @GetMapping("/getComments")
    public SaResult getComments(@RequestParam(defaultValue = "1") Integer pageNum, 
                               @RequestParam Integer postId, 
                               @RequestParam(defaultValue = "15") Integer pageSize) {
        if (postId == null || postId == 0) {
            //postId为空: 直接抛出异常
            throw new BussinessException(ErrorResult.numError());
        }
        CommentDto dto = commentService.getTopCommentsByPage(postId, pageNum, pageSize);
        return SaResult.code(200).data(dto);
    }

    /**
     * 分页查询二级评论
     * 需要传入一级评论的commentId
     */
    @SaCheckLogin
    @GetMapping("/comment/{commentId}/replies")
    public SaResult getReplies(@PathVariable("commentId") Integer commentId) {
        if (commentId == null || commentId <= 0) {
            throw new BussinessException(ErrorResult.numError()); // 无效的评论 ID
        }
        // 调用 Service 方法获取二级评论列表
        List<CommentWithUserDto> replies = commentService.getRepliesByCommentId(commentId);
        return SaResult.code(200).data(replies);
    }

    /**
     * 用户删除自己的评论 (逻辑删除)
     *
     * @param commentId 待删除的评论ID
     * @return
     */
    @SaCheckLogin // 只有登录用户可以访问
    @DeleteMapping("/my/comment/{commentId}")
    public SaResult deleteMyComment(@PathVariable Integer commentId) {
        if (commentId == null || commentId <= 0) {
            return SaResult.error("无效的评论ID");
        }

        int loginId = StpUtil.getLoginIdAsInt();
        boolean success = commentService.deleteCommentByUserId(commentId, loginId);

        if (success) {
            return SaResult.ok("评论删除成功。");
        } else {
            // TODO: 可以在Service层抛出更具体的异常，以便这里返回更详细的错误信息
            return SaResult.error("评论删除失败，可能评论不存在、已被删除或您无权删除。");
        }
    }

    /**
     * 分页获取用户发布的评论
     * 🚀 优化：未登录用户使用Redis缓存5分钟，防止网络攻击
     * 
     * @param userId 用户ID
     * @param pageNum 页码，默认1
     * @param pageSize 每页大小，默认15
     * @return 用户评论列表
     */
    @SaCheckLogin
    @GetMapping("/user/{userId}/comments")
    public SaResult getUserComments(
            @PathVariable("userId") Integer userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "15") Integer pageSize) {
        
        if (userId == null || userId <= 0) {
            return SaResult.error("无效的用户ID");
        }
        
        // 🚀 检查是否登录（这里保留@SaCheckLogin，但内部区分缓存策略）
        // 获取当前登录用户ID，如果是查看自己的评论，不使用缓存
        Integer currentUserId = null;
        try {
            currentUserId = StpUtil.getLoginIdAsInt();
        } catch (Exception e) {
            // 理论上不会发生，因为有@SaCheckLogin注解
        }
        
        // 如果是查看他人的评论，且当前用户已登录超过一定时间，可以使用缓存
        boolean useCache = currentUserId != null && !currentUserId.equals(userId);
        
        if (useCache) {
            // 查看他人评论，使用缓存策略
            String cacheKey = ANONYMOUS_USER_COMMENTS_CACHE_PREFIX + userId + ":" + pageNum + ":" + pageSize;
            
            try {
                Object cachedResult = redisTemplate.opsForValue().get(cacheKey);
                if (cachedResult != null && cachedResult instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<UserCommentDto> cachedComments = (List<UserCommentDto>) cachedResult;
                    log.info("用户评论列表缓存命中，targetUserId: {}, currentUserId: {}, cacheKey: {}", 
                             userId, currentUserId, cacheKey);
                    return SaResult.code(200).data(cachedComments);
                }
            } catch (Exception e) {
                log.info("获取用户评论缓存失败，将查询数据库，targetUserId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            // 缓存未命中，查询数据库
            List<UserCommentDto> comments = commentService.getUserComments(userId, pageNum, pageSize);
            
            // 将结果缓存5分钟
            try {
                redisTemplate.opsForValue().set(cacheKey, comments, ANONYMOUS_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.debug("用户评论列表已缓存，targetUserId: {}, cacheKey: {}, 过期时间: {}分钟", 
                         userId, cacheKey, ANONYMOUS_CACHE_EXPIRE_MINUTES);
            } catch (Exception e) {
                log.warn("缓存用户评论列表失败，targetUserId: {}, cacheKey: {}", userId, cacheKey, e);
            }
            
            return SaResult.code(200).data(comments);
        }
        
        // 查看自己的评论，不使用缓存（保证数据实时性）
        List<UserCommentDto> comments = commentService.getUserComments(userId, pageNum, pageSize);
        return SaResult.code(200).data(comments);
    }
    
    /**
     * 根据评论ID获取帖子ID和页码信息
     * 
     * @param commentId 评论ID
     * @return 包含帖子ID、页码等位置信息
     */
    @SaCheckLogin
    @GetMapping("/location/{commentId}")
    public SaResult getCommentLocation(@PathVariable("commentId") Integer commentId) {
        if (commentId == null || commentId <= 0) {
            return SaResult.error("无效的评论ID");
        }
        
        CommentLocationDto locationDto = commentService.getCommentLocation(commentId);
        return SaResult.code(200).data(locationDto);
    }
}
