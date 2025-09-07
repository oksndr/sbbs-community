package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.sbbs.entity.Like;
import com.itheima.sbbs.entity.Post;
import com.itheima.sbbs.entity.PostDto;
import com.itheima.sbbs.entity.PostRush;
import com.itheima.sbbs.entity.PostWithUserDto;
import com.itheima.sbbs.entity.User;
import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.service.ForumStatsService;
import com.itheima.sbbs.service.LikeCacheService;
import com.itheima.sbbs.service.PostService;
import com.itheima.sbbs.service.UserService;
import com.itheima.sbbs.service.NotificationService;
import com.itheima.sbbs.service.ImageUploadService;
import com.itheima.sbbs.service.ExperienceService;
import com.itheima.sbbs.utils.SMSUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequestMapping("/v2")
@RestController
public class PostController {

    @Autowired
    private PostService postService;
    @Autowired
    private ForumStatsService forumStatsService;

    @Autowired
    private UserService userService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SMSUtils smsUtils;

    @Autowired
    private ImageUploadService imageUploadService;

    @Autowired
    private LikeCacheService likeCacheService;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 缓存键前缀
    private static final String ANONYMOUS_POST_LIST_CACHE_PREFIX = "anonymous:post_list:";
    private static final String ANONYMOUS_POST_LIST_TAG_CACHE_PREFIX = "anonymous:post_list_tag:";
    private static final int ANONYMOUS_CACHE_EXPIRE_MINUTES = 10; // 10分钟缓存

    /**
     * 发布帖子接口
     */
    @SaCheckLogin
    @PostMapping("/publish")
    public SaResult publish(@RequestBody Post post) {
        //简单的数据判空
        if (post == null) {
            return SaResult.error("?");
        }
        if (post.getTitle() == null || post.getTitle().trim().isEmpty()) {
            return SaResult.error("帖子标题不能为空");
        }
        if (post.getContent() == null || post.getContent().trim().isEmpty()) {
            return SaResult.error("帖子内容不能为空");
        }
        //保存操作
        Integer userId = StpUtil.getLoginIdAsInt();//获取用户id
        post.setUserId(userId);
        boolean saveSuccess = postService.save(post);
        
        if (!saveSuccess) {
            return SaResult.error("帖子发布失败，请稍后重试");
        }

        // 异步添加发帖经验值
        try {
            experienceService.addPostExperience(userId);
        } catch (Exception e) {
            log.warn("添加发帖经验值失败，用户ID: {}", userId, e);
        }

        // 构建返回数据，包含帖子ID
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("postId", post.getId());
        resultData.put("message", "帖子发布成功");

        return SaResult.data(resultData);
    }

    /**
     * 文件上传接口
     */
    @SaCheckLogin
    @PostMapping("/upload")
    public SaResult uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return SaResult.error("请选择要上传的文件");
        }
        
        Map<String, Object> result = imageUploadService.uploadImage(file);
        
        // 添加对result为null的检查
        if (result == null) {
            return SaResult.error("文件上传服务异常，请稍后重试");
        }
        
        // 根据外部接口的响应结构判断是否成功
        if (result.get("result") != null && "success".equals(result.get("result"))) {
            // 构建前端需要的响应格式
            Map<String, Object> data = new HashMap<>();
            data.put("url", result.get("url"));
            data.put("id", result.get("id") != null ? result.get("id") : "");
            return SaResult.data(data);
        } else {
            // 上传失败
            String errorMessage = result.get("message") != null ? result.get("message").toString() : "未知错误";
            return SaResult.error("文件上传失败: " + errorMessage);
        }
    }

    /**
     * 分页查询帖子接口
     * 🚀 优化：未登录用户使用Redis缓存10分钟，防止攻击
     */
    @GetMapping("/list")
    public SaResult list(@RequestParam(defaultValue = "0") Integer lastId,
                         @RequestParam(defaultValue = "15") Integer pageSize,
                         @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastUpdated) {
        
        // 🚀 检查是否登录
        if (!StpUtil.isLogin()) {
            // 未登录用户走缓存策略
            String cacheKey = ANONYMOUS_POST_LIST_CACHE_PREFIX + lastId + ":" + 
                             (lastUpdated != null ? lastUpdated.toString() : "null") + ":" + pageSize;
            
            try {
                Object cachedResult = redisTemplate.opsForValue().get(cacheKey);
                if (cachedResult != null && cachedResult instanceof PostDto) {
                    log.info("未登录用户帖子列表缓存命中，cacheKey: {}", cacheKey);
                    return SaResult.code(200).data(cachedResult);
                }
            } catch (Exception e) {
                log.info("获取帖子列表缓存失败，将查询数据库，cacheKey: {}", cacheKey, e);
            }
            
            // 缓存未命中，查询数据库
            PostDto postList = postService.getPostList(lastId, lastUpdated, pageSize);
            
            // 将结果缓存10分钟
            try {
                redisTemplate.opsForValue().set(cacheKey, postList, ANONYMOUS_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.debug("未登录用户帖子列表已缓存，cacheKey: {}, 过期时间: {}分钟", cacheKey, ANONYMOUS_CACHE_EXPIRE_MINUTES);
            } catch (Exception e) {
                log.warn("缓存帖子列表失败，cacheKey: {}", cacheKey, e);
            }
            
            return SaResult.code(200).data(postList);
        }
        
        // 已登录用户正常查询（维持现状）
        PostDto postList = postService.getPostList(lastId, lastUpdated, pageSize);
        return SaResult.code(200).data(postList);
    }

    /**
     * 按标签分页查询帖子接口
     * 使用游标式分页，与主页面保持一致的用户体验
     * 🚀 优化：未登录用户使用Redis缓存10分钟，防止攻击
     */
    @GetMapping("/list/tag/{tagId}")
    public SaResult listByTag(@PathVariable Integer tagId,
                              @RequestParam(defaultValue = "0") Integer lastId,
                              @RequestParam(defaultValue = "15") Integer pageSize,
                              @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastUpdated) {
        
        // 验证tagId参数
        if (tagId == null || tagId <= 0) {
            return SaResult.error("无效的标签ID");
        }
        
        // 🚀 检查是否登录
        if (!StpUtil.isLogin()) {
            // 未登录用户走缓存策略
            String cacheKey = ANONYMOUS_POST_LIST_TAG_CACHE_PREFIX + tagId + ":" + lastId + ":" + 
                             (lastUpdated != null ? lastUpdated.toString() : "null") + ":" + pageSize;
            
            try {
                Object cachedResult = redisTemplate.opsForValue().get(cacheKey);
                if (cachedResult != null && cachedResult instanceof PostDto) {
                    log.debug("未登录用户标签帖子列表缓存命中，tagId: {}, cacheKey: {}", tagId, cacheKey);
                    return SaResult.code(200).data(cachedResult);
                }
            } catch (Exception e) {
                log.warn("获取标签帖子列表缓存失败，将查询数据库，tagId: {}, cacheKey: {}", tagId, cacheKey, e);
            }
            
            // 缓存未命中，查询数据库
            PostDto postList = postService.getPostListByTag(tagId, lastId, lastUpdated, pageSize);
            
            // 将结果缓存10分钟
            try {
                redisTemplate.opsForValue().set(cacheKey, postList, ANONYMOUS_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.debug("未登录用户标签帖子列表已缓存，tagId: {}, cacheKey: {}, 过期时间: {}分钟", 
                         tagId, cacheKey, ANONYMOUS_CACHE_EXPIRE_MINUTES);
            } catch (Exception e) {
                log.warn("缓存标签帖子列表失败，tagId: {}, cacheKey: {}", tagId, cacheKey, e);
            }
            
            return SaResult.code(200).data(postList);
        }
        
        // 已登录用户正常查询（维持现状）
        PostDto postList = postService.getPostListByTag(tagId, lastId, lastUpdated, pageSize);
        return SaResult.code(200).data(postList);
    }

    /**
     * 查询帖子详情
     */
    @GetMapping("/post/{postId}")
    public SaResult getPost(@PathVariable Integer postId) {
        if (!(postId > 0)) {
            return SaResult.error("请注意: 你访问了一个不存在的页面");
        }
        
        // 调用新的 Service 方法获取包含标签的帖子详情
        PostWithUserDto postDetail = postService.getPostDetailById(postId);

        if (postDetail == null) {
            return SaResult.error("请注意: 你访问了一个不存在的页面");
        }
        
        // 记录帖子信息，用于监控评论计数是否正确
        log.debug("获取帖子详情，ID: {}, 标题: {}, 评论数: {}", 
                 postId, postDetail.getTitle(), postDetail.getCommentCount());
                 
        // 获取点赞/点踩状态（直接查询数据库，简单高效）
        boolean liked = false;
        boolean disLiked = false;

        if (StpUtil.isLogin()) {
            int currentUserId = StpUtil.getLoginIdAsInt();

            // 直接查询数据库获取点赞状态，单次查询很快
            Like res = postService.checkIfLiked(currentUserId, postId);
            if (res != null && res.getType() != null) {
                liked = res.getType() == 1;
                disLiked = res.getType() == -1;
            }
            
            log.debug("查询用户点赞状态，用户ID: {}, 帖子ID: {}, 点赞: {}, 点踩: {}",
                     currentUserId, postId, liked, disLiked);
        }

        // 封装返回结果到 PostRush 对象
        PostRush postRush = new PostRush();
        postRush.setPost(postDetail); // 设置包含作者和标签的帖子详情
        postRush.setLiked(liked);
        postRush.setDisLiked(disLiked);

        return SaResult.code(200).data(postRush);
    }

    /**
     * 管理员删除帖子 (逻辑删除)
     *
     * @param postId 待删除的帖子ID
     * @return
     */
    @SaCheckRole("管理员") // 只有管理员角色可以访问
    @DeleteMapping("/post/{postId}")
    public SaResult deletePost(@PathVariable Integer postId) {
        if (postId == null || postId <= 0) {
            return SaResult.error("无效的帖子ID");
        }

        // 调用Service层的管理员删除方法，会发送通知和邮件给帖子作者
        boolean success = postService.removeByIdWithNotification(postId);

        if (success) {
            return SaResult.ok("帖子删除成功。");
        } else {
            // TODO: 可以在Service层抛出更具体的异常，以便这里返回更详细的错误信息
            return SaResult.error("帖子删除失败，可能帖子不存在或已被删除。");
        }
    }

    /**
     * 用户删除自己的帖子 (逻辑删除)
     *
     * @param postId 待删除的帖子ID
     * @return
     */
    @SaCheckLogin // 只有登录用户可以访问
    @DeleteMapping("/my/post/{postId}") // 使用不同的路径以区分管理员删除
    public SaResult deleteMyPost(@PathVariable Integer postId) {
        if (postId == null || postId <= 0) {
            return SaResult.error("无效的帖子ID");
        }

        int loginId = StpUtil.getLoginIdAsInt();
        boolean success = postService.deletePostByUserId(postId, loginId);

        if (success) {
            return SaResult.ok("帖子删除成功。");
        } else {
            // TODO: 可以在Service层抛出更具体的异常，以便这里返回更详细的错误信息
            return SaResult.error("帖子删除失败，可能帖子不存在、已被删除或您无权删除。");
        }
    }

    /**
     * 用户修改自己的帖子
     *
     * @param postId 待修改的帖子ID
     * @param post 帖子修改数据
     * @return
     */
    @SaCheckLogin // 只有登录用户可以访问
    @PutMapping("/my/post/{postId}")
    public SaResult updateMyPost(@PathVariable Integer postId, @RequestBody Post post) {
        if (postId == null || postId <= 0) {
            return SaResult.error("无效的帖子ID");
        }

        if (post == null) {
            return SaResult.error("帖子数据不能为空");
        }

        if (post.getTitle() == null || post.getTitle().trim().isEmpty()) {
            return SaResult.error("帖子标题不能为空");
        }

        if (post.getContent() == null || post.getContent().trim().isEmpty()) {
            return SaResult.error("帖子内容不能为空");
        }

        // 设置帖子ID
        post.setId(postId);

        int loginId = StpUtil.getLoginIdAsInt();
        boolean success = postService.updatePostByUserId(post, loginId);

        if (success) {
            return SaResult.ok("帖子修改成功。");
        } else {
            return SaResult.error("帖子修改失败，可能帖子不存在、已被删除或您无权修改。");
        }
    }

    /**
     * 获取本周最火的5个帖子
     * 热度计算公式：点赞数 * 2 + 评论数 * 1.5 + 点踩数 * 0.3
     * 黑红也是红！点踩也算热度，本周内所有帖子公平竞争
     * @return 热门帖子列表
     */
    @GetMapping("/hot/posts")
    public SaResult getHotPosts() {
        try {
            List<PostWithUserDto> hotPosts = postService.getHotPostsInSevenDays();
            return SaResult.code(200).data(hotPosts).setMsg("获取热门帖子成功");
        } catch (Exception e) {
            log.error("获取热门帖子失败", e);
            return SaResult.error("获取热门帖子失败，请稍后重试");
        }
    }
    
    /**
     * 🚀 管理员设置首页置顶帖子
     */
    @SaCheckRole("管理员")
    @PostMapping("/admin/pinned")
    public SaResult setPinnedPost(@RequestParam Integer postId) {
        boolean result = postService.setPinnedPost(postId);
        if (result) {
            return SaResult.ok("置顶帖子设置成功，帖子ID: " + postId);
        } else {
            return SaResult.error("设置置顶帖子失败，请检查帖子是否存在");
        }
    }
    
    /**
     * 🚀 管理员取消首页置顶帖子
     */
    @SaCheckRole("管理员")
    @DeleteMapping("/admin/pinned")
    public SaResult removePinnedPost() {
        boolean result = postService.setPinnedPost(null);
        if (result) {
            return SaResult.ok("已取消首页置顶帖子");
        } else {
            return SaResult.error("取消置顶帖子失败");
        }
    }
    
    /**
     * 🚀 获取当前置顶帖子信息
     */
    @GetMapping("/admin/pinned")
    @SaCheckRole("管理员")
    public SaResult getPinnedPost() {
        Integer pinnedPostId = postService.getPinnedPostId();
        if (pinnedPostId != null) {
            PostWithUserDto pinnedPost = postService.getPostDetailById(pinnedPostId);
            if (pinnedPost != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("postId", pinnedPostId);
                result.put("title", pinnedPost.getTitle());
                result.put("author", pinnedPost.getUsername());
                return SaResult.data(result);
            } else {
                return SaResult.error("置顶帖子不存在或已删除");
            }
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("postId", null);
            result.put("message", "当前无置顶帖子");
            return SaResult.data(result);
        }
    }

    /**
     * 🚀 管理员接口：清理所有用户相关缓存（帖子列表+个人主页）
     * 用于主动刷新缓存，比如有新帖子发布或重要更新时
     */
    @SaCheckRole("管理员")
    @DeleteMapping("/admin/cache/anonymous")
    public SaResult clearAnonymousCache() {
        try {
            int totalClearedKeys = 0;
            
            // 清理帖子列表相关缓存
            Set<String> postListKeys = redisTemplate.keys(ANONYMOUS_POST_LIST_CACHE_PREFIX + "*");
            if (postListKeys != null && !postListKeys.isEmpty()) {
                redisTemplate.delete(postListKeys);
                totalClearedKeys += postListKeys.size();
                log.info("已清理帖子列表缓存，数量: {}", postListKeys.size());
            }
            
            Set<String> tagListKeys = redisTemplate.keys(ANONYMOUS_POST_LIST_TAG_CACHE_PREFIX + "*");
            if (tagListKeys != null && !tagListKeys.isEmpty()) {
                redisTemplate.delete(tagListKeys);
                totalClearedKeys += tagListKeys.size();
                log.info("已清理标签帖子列表缓存，数量: {}", tagListKeys.size());
            }
            
            // 清理个人主页相关缓存
            Set<String> userProfileKeys = redisTemplate.keys("anonymous:user_profile:*");
            if (userProfileKeys != null && !userProfileKeys.isEmpty()) {
                redisTemplate.delete(userProfileKeys);
                totalClearedKeys += userProfileKeys.size();
                log.info("已清理个人主页缓存，数量: {}", userProfileKeys.size());
            }
            
            Set<String> userCommentsKeys = redisTemplate.keys("anonymous:user_comments:*");
            if (userCommentsKeys != null && !userCommentsKeys.isEmpty()) {
                redisTemplate.delete(userCommentsKeys);
                totalClearedKeys += userCommentsKeys.size();
                log.info("已清理用户评论缓存，数量: {}", userCommentsKeys.size());
            }
            
            // 清理关注/粉丝相关缓存
            String[] followCachePrefixes = {
                "anonymous:follower_list:*",
                "anonymous:following_list:*", 
                "anonymous:follower_page:*",
                "anonymous:following_page:*"
            };
            
            for (String prefix : followCachePrefixes) {
                Set<String> keys = redisTemplate.keys(prefix);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                    totalClearedKeys += keys.size();
                    log.info("已清理缓存 {}，数量: {}", prefix, keys.size());
                }
            }
            
            return SaResult.ok("所有用户相关缓存清理成功，共清理 " + totalClearedKeys + " 个缓存键");
        } catch (Exception e) {
            log.error("清理用户相关缓存失败", e);
            return SaResult.error("缓存清理失败：" + e.getMessage());
        }
    }

    /**
     * 🚀 管理员接口：查看所有用户相关缓存状态（包括个人主页）
     */
    @SaCheckRole("管理员")
    @GetMapping("/admin/cache/anonymous/status")
    public SaResult getAnonymousCacheStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // 统计帖子列表缓存数量
            Set<String> postListKeys = redisTemplate.keys(ANONYMOUS_POST_LIST_CACHE_PREFIX + "*");
            int postListCacheCount = postListKeys != null ? postListKeys.size() : 0;
            
            // 统计标签帖子列表缓存数量
            Set<String> tagListKeys = redisTemplate.keys(ANONYMOUS_POST_LIST_TAG_CACHE_PREFIX + "*");
            int tagListCacheCount = tagListKeys != null ? tagListKeys.size() : 0;
            
            // 统计个人主页相关缓存数量
            Set<String> userProfileKeys = redisTemplate.keys("anonymous:user_profile:*");
            int userProfileCacheCount = userProfileKeys != null ? userProfileKeys.size() : 0;
            
            Set<String> userCommentsKeys = redisTemplate.keys("anonymous:user_comments:*");
            int userCommentsCacheCount = userCommentsKeys != null ? userCommentsKeys.size() : 0;
            
            Set<String> followerListKeys = redisTemplate.keys("anonymous:follower_list:*");
            int followerListCacheCount = followerListKeys != null ? followerListKeys.size() : 0;
            
            Set<String> followingListKeys = redisTemplate.keys("anonymous:following_list:*");
            int followingListCacheCount = followingListKeys != null ? followingListKeys.size() : 0;
            
            Set<String> followerPageKeys = redisTemplate.keys("anonymous:follower_page:*");
            int followerPageCacheCount = followerPageKeys != null ? followerPageKeys.size() : 0;
            
            Set<String> followingPageKeys = redisTemplate.keys("anonymous:following_page:*");
            int followingPageCacheCount = followingPageKeys != null ? followingPageKeys.size() : 0;
            
            int totalProfileCacheCount = userProfileCacheCount + userCommentsCacheCount + 
                                       followerListCacheCount + followingListCacheCount + 
                                       followerPageCacheCount + followingPageCacheCount;
            
            // 设置缓存统计信息
            status.put("postListCacheCount", postListCacheCount);
            status.put("tagListCacheCount", tagListCacheCount);
            status.put("userProfileCacheCount", userProfileCacheCount);
            status.put("userCommentsCacheCount", userCommentsCacheCount);
            status.put("followerListCacheCount", followerListCacheCount);
            status.put("followingListCacheCount", followingListCacheCount);
            status.put("followerPageCacheCount", followerPageCacheCount);
            status.put("followingPageCacheCount", followingPageCacheCount);
            status.put("totalProfileCacheCount", totalProfileCacheCount);
            status.put("totalCacheCount", postListCacheCount + tagListCacheCount + totalProfileCacheCount);
            status.put("postCacheExpireMinutes", ANONYMOUS_CACHE_EXPIRE_MINUTES);
            status.put("profileCacheExpireMinutes", 5);
            
            return SaResult.data(status);
        } catch (Exception e) {
            log.error("查看用户相关缓存状态失败", e);
            return SaResult.error("查看缓存状态失败：" + e.getMessage());
        }
    }

    /**
     * 🚀 管理员接口：清理所有个人主页相关缓存
     */
    @SaCheckRole("管理员")
    @DeleteMapping("/admin/cache/profile")
    public SaResult clearProfileCache() {
        try {
            int totalClearedKeys = 0;
            
            // 清理个人主页缓存
            Set<String> userProfileKeys = redisTemplate.keys("anonymous:user_profile:*");
            if (userProfileKeys != null && !userProfileKeys.isEmpty()) {
                redisTemplate.delete(userProfileKeys);
                totalClearedKeys += userProfileKeys.size();
                log.info("已清理个人主页缓存，数量: {}", userProfileKeys.size());
            }
            
            // 清理用户评论缓存
            Set<String> userCommentsKeys = redisTemplate.keys("anonymous:user_comments:*");
            if (userCommentsKeys != null && !userCommentsKeys.isEmpty()) {
                redisTemplate.delete(userCommentsKeys);
                totalClearedKeys += userCommentsKeys.size();
                log.info("已清理用户评论缓存，数量: {}", userCommentsKeys.size());
            }
            
            // 清理关注/粉丝列表缓存
            Set<String> followerListKeys = redisTemplate.keys("anonymous:follower_list:*");
            if (followerListKeys != null && !followerListKeys.isEmpty()) {
                redisTemplate.delete(followerListKeys);
                totalClearedKeys += followerListKeys.size();
                log.info("已清理粉丝列表缓存，数量: {}", followerListKeys.size());
            }
            
            Set<String> followingListKeys = redisTemplate.keys("anonymous:following_list:*");
            if (followingListKeys != null && !followingListKeys.isEmpty()) {
                redisTemplate.delete(followingListKeys);
                totalClearedKeys += followingListKeys.size();
                log.info("已清理关注列表缓存，数量: {}", followingListKeys.size());
            }
            
            // 清理关注/粉丝分页缓存
            Set<String> followerPageKeys = redisTemplate.keys("anonymous:follower_page:*");
            if (followerPageKeys != null && !followerPageKeys.isEmpty()) {
                redisTemplate.delete(followerPageKeys);
                totalClearedKeys += followerPageKeys.size();
                log.info("已清理粉丝分页缓存，数量: {}", followerPageKeys.size());
            }
            
            Set<String> followingPageKeys = redisTemplate.keys("anonymous:following_page:*");
            if (followingPageKeys != null && !followingPageKeys.isEmpty()) {
                redisTemplate.delete(followingPageKeys);
                totalClearedKeys += followingPageKeys.size();
                log.info("已清理关注分页缓存，数量: {}", followingPageKeys.size());
            }
            
            return SaResult.ok("个人主页相关缓存清理成功，共清理 " + totalClearedKeys + " 个缓存键");
        } catch (Exception e) {
            log.error("清理个人主页相关缓存失败", e);
            return SaResult.error("缓存清理失败：" + e.getMessage());
        }
    }
}
