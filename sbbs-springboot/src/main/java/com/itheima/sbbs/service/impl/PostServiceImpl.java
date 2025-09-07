package com.itheima.sbbs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.sbbs.entity.Like;
import com.itheima.sbbs.entity.Post;
import com.itheima.sbbs.entity.PostDto;
import com.itheima.sbbs.entity.PostWithUserDto;
import com.itheima.sbbs.entity.PostTag;
import com.itheima.sbbs.entity.Tag;
import com.itheima.sbbs.mapper.LikeMapper;
import com.itheima.sbbs.mapper.PostMapper;
import com.itheima.sbbs.mapper.PostTagMapper;
import com.itheima.sbbs.mapper.TagMapper;
import com.itheima.sbbs.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import com.itheima.sbbs.service.UserService;
import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.service.NotificationCacheService;
import com.itheima.sbbs.service.NotificationService;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.druid.util.StringUtils;
import com.itheima.sbbs.entity.User;
import com.itheima.sbbs.utils.SMSUtils;
import org.springframework.beans.BeanUtils;
import java.util.HashMap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDate;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    @Autowired
    private LikeMapper likeMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostTagMapper postTagMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SMSUtils smsUtils;
    
    @Autowired
    private NotificationCacheService notificationCacheService;

    private static final String POST_DETAIL_CACHE_PREFIX = "postDetail:";
    private static final String HOT_POSTS_CACHE_KEY = "hotPosts:sevenDays"; // 热门帖子缓存键
    private static final String TAG_MAP_CACHE_KEY = "tagMap:all"; // 所有标签映射缓存键
    private static final String PINNED_POST_KEY = "homepage:pinned_post"; // 置顶帖子ID
    private static final long TAG_CACHE_EXPIRE_HOURS = 24; // 标签缓存过期时间（小时）

    // 🚀 应用内存缓存：置顶帖子ID
    private volatile Integer cachedPinnedPostId = null;
    private volatile boolean pinnedPostCacheInitialized = false;

    // 🚀 内存缓存：用户信息缓存（避免重复查询同一用户）
    private final Map<Integer, User> userInfoCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long userCacheLastClear = System.currentTimeMillis();
    private static final long USER_CACHE_CLEAR_INTERVAL = 5 * 60 * 1000; // 5分钟清理一次

    /**
     * 🚀 初始化置顶帖子缓存（服务启动时调用）
     */
    @javax.annotation.PostConstruct
    private void initPinnedPostCache() {
        refreshPinnedPostCache();
    }

    /**
     * 🚀 刷新置顶帖子内存缓存
     */
    private synchronized void refreshPinnedPostCache() {
        try {
            Object pinnedPostObj = redisTemplate.opsForValue().get(PINNED_POST_KEY);
            if (pinnedPostObj != null) {
                cachedPinnedPostId = Integer.parseInt(pinnedPostObj.toString());
            } else {
                cachedPinnedPostId = null;
            }
            pinnedPostCacheInitialized = true;
            log.debug("置顶帖子内存缓存已刷新，当前置顶帖子ID: {}", cachedPinnedPostId);
        } catch (Exception e) {
            log.warn("刷新置顶帖子缓存失败: {}", e.getMessage());
            pinnedPostCacheInitialized = true; // 即使失败也标记为已初始化
        }
    }

    /**
     * 🚀 获取置顶帖子ID（内部方法，使用内存缓存）
     */
    private Integer getInternalPinnedPostId() {
        // 如果缓存未初始化，先初始化
        if (!pinnedPostCacheInitialized) {
            refreshPinnedPostCache();
        }
        return cachedPinnedPostId;
    }
    
    /**
     * 🚀 查询置顶帖子详情
     */
    private PostWithUserDto getPinnedPost(Integer pinnedPostId) {
        if (pinnedPostId == null) {
            return null;
        }
        
        try {
            Post pinnedPost = this.getById(pinnedPostId);
            if (pinnedPost == null || pinnedPost.getDeleted() == 1) {
                return null;
            }
            
            // 获取用户信息
            User user = userService.getById(pinnedPost.getUserId());
            if (user == null || user.getDeleted() == 1) {
                return null;
            }
            
            // 转换为PostWithUserDto
            PostWithUserDto pinnedPostDto = new PostWithUserDto();
            pinnedPostDto.setId(pinnedPost.getId());
            pinnedPostDto.setUserId(pinnedPost.getUserId());
            pinnedPostDto.setTitle(pinnedPost.getTitle());
            pinnedPostDto.setLikeCount(pinnedPost.getLikeCount());
            pinnedPostDto.setDislikeCount(pinnedPost.getDislikeCount());
            pinnedPostDto.setCommentCount(pinnedPost.getCommentCount());
            pinnedPostDto.setTagIdsStringAlias(pinnedPost.getTagIdsString());
            pinnedPostDto.setCreated(pinnedPost.getCreated() != null ? pinnedPost.getCreated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);
            pinnedPostDto.setUpdated(pinnedPost.getUpdated() != null ? pinnedPost.getUpdated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);
            pinnedPostDto.setDeleted(pinnedPost.getDeleted());
            pinnedPostDto.setUsername(user.getUsername());
            pinnedPostDto.setAvatar(user.getAvatar());
            
            return pinnedPostDto;
        } catch (Exception e) {
            log.warn("查询置顶帖子详情失败，帖子ID: {}", pinnedPostId, e);
            return null;
        }
    }

    /**
     * 🚀 获取用户信息（带内存缓存优化）
     */
    private Map<Integer, User> getUserInfoBatch(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }

        // 清理过期的用户缓存
        clearExpiredUserCache();

        Map<Integer, User> result = new HashMap<>();
        List<Integer> uncachedUserIds = new ArrayList<>();

        // 检查内存缓存
        for (Integer userId : userIds) {
            User cachedUser = userInfoCache.get(userId);
            if (cachedUser != null) {
                result.put(userId, cachedUser);
            } else {
                uncachedUserIds.add(userId);
            }
        }

        // 批量查询未缓存的用户
        if (!uncachedUserIds.isEmpty()) {
            List<User> users = userService.listByIds(uncachedUserIds);
            for (User user : users) {
                result.put(user.getId(), user);
                userInfoCache.put(user.getId(), user); // 更新内存缓存
            }
        }

        return result;
    }

    /**
     * 🚀 清理过期的用户信息缓存
     */
    private void clearExpiredUserCache() {
        long now = System.currentTimeMillis();
        if (now - userCacheLastClear > USER_CACHE_CLEAR_INTERVAL) {
            userInfoCache.clear();
            userCacheLastClear = now;
            log.debug("清理用户信息内存缓存");
        }
    }

    /**
     * 流式查询没看过的帖子
     * @param lastId
     * @param lastUpdated
     * @param pageSize
     * @return
     */
    @Override
    public PostDto getPostList(Integer lastId, LocalDateTime lastUpdated, Integer pageSize) {
        log.debug("获取帖子列表，lastId: {}, lastUpdated: {}, pageSize: {}", lastId, lastUpdated, pageSize);
        
        boolean isFirstPage = (lastId == 0 && lastUpdated == null);
        Integer pinnedPostId = getInternalPinnedPostId();
        PostWithUserDto pinnedPost = null;
        
        // 🚀 如果是首页第一页且有置顶帖子，先查询置顶帖子
        if (isFirstPage && pinnedPostId != null) {
            pinnedPost = getPinnedPost(pinnedPostId);
            log.debug("查询到置顶帖子: {}", pinnedPost != null ? pinnedPost.getTitle() : "null");
        }

        // 🚀 优化：查询pageSize+1条记录来判断是否有下一页，避免COUNT查询
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Post::getId, Post::getUserId, Post::getTitle, 
                      Post::getLikeCount, Post::getDislikeCount, Post::getCommentCount,
                      Post::getTagIdsString, Post::getCreated, Post::getUpdated, Post::getDeleted);
        
        // 🚀 如果有置顶帖子，排除它以避免重复显示
        if (pinnedPostId != null) {
            wrapper.ne(Post::getId, pinnedPostId);
        }
        
        if (lastId != 0 && lastUpdated != null) {
            wrapper.lt(Post::getUpdated, lastUpdated)
                    .or(w -> w.eq(Post::getUpdated, lastUpdated).lt(Post::getId, lastId));
        }
        wrapper.orderByDesc(Post::getUpdated, Post::getId);
        
        // 🚀 查询pageSize+1条记录，用于判断是否有下一页
        Page<Post> page = new Page<>(1, pageSize + 1);
        IPage<Post> postPage = this.page(page, wrapper);
        List<Post> records = postPage.getRecords();

        PostDto postDto = new PostDto();
        
        // 🚀 判断是否有下一页
        boolean hasNextPage = records.size() > pageSize;
        if (hasNextPage) {
            records = records.subList(0, pageSize); // 只取pageSize条记录
        }
        
        if (records.isEmpty()) {
            postDto.setList(new ArrayList<>());
            postDto.setLastUpdated(lastUpdated);
            postDto.setLastId(lastId);
            postDto.setHasNextPage(false); // 新增：是否有下一页
            return postDto;
        }

        // 🚀 优化：使用批量用户信息缓存
        List<Integer> userIds = records.stream().map(Post::getUserId).distinct().collect(Collectors.toList());
        Map<Integer, User> userMap = getUserInfoBatch(userIds);

        // 将 Post 转换为 PostWithUserDto 并填充用户信息
        List<PostWithUserDto> postWithUserDtoList = new ArrayList<>();
        for (Post postRecord : records) {
            PostWithUserDto dto = new PostWithUserDto();
            // 复制 Post 属性到 PostWithUserDto
            dto.setId(postRecord.getId());
            dto.setUserId(postRecord.getUserId());
            dto.setTitle(postRecord.getTitle());
            dto.setCommentCount(postRecord.getCommentCount());
            dto.setLikeCount(postRecord.getLikeCount());
            
            // 设置真实的点踩数
            dto.setDislikeCount(postRecord.getDislikeCount() != null ? postRecord.getDislikeCount() : 0);
            
            // 日期转换
            if (postRecord.getCreated() != null) {
                dto.setCreated(postRecord.getCreated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
            if (postRecord.getUpdated() != null) {
                dto.setUpdated(postRecord.getUpdated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
            dto.setDeleted(postRecord.getDeleted());
            dto.setTagIdsStringAlias(postRecord.getTagIdsString()); // 设置原始 tagIdsString

            // 填充用户信息
            User author = userMap.get(postRecord.getUserId());
            if (author != null) {
                dto.setUsername(author.getUsername());
                dto.setAvatar(author.getAvatar());
            }
            postWithUserDtoList.add(dto);
        }

        // 🚀 如果是首页第一页且有置顶帖子，将置顶帖子插入到列表第一位
        if (isFirstPage && pinnedPost != null) {
            // 处理置顶帖子的标签信息
            List<PostWithUserDto> pinnedPostList = new ArrayList<>();
            pinnedPostList.add(pinnedPost);
            processPostTags(pinnedPostList);
            
            // 将置顶帖子插入到列表第一位
            postWithUserDtoList.add(0, pinnedPostList.get(0));
        }

        // 处理标签信息 (processPostTags 需要 List<PostWithUserDto>)
        processPostTags(postWithUserDtoList);
        
        // 🚀 为置顶帖子手动添加"置顶"标签（在所有标签处理完成后）
        if (isFirstPage && pinnedPost != null && !postWithUserDtoList.isEmpty()) {
            PostWithUserDto firstPost = postWithUserDtoList.get(0);
            // 确认第一个帖子就是置顶帖子
            if (firstPost.getId().equals(pinnedPost.getId())) {
                List<String> pinnedTags = firstPost.getTags();
                if (pinnedTags == null) {
                    pinnedTags = new ArrayList<>();
                }
                // 在标签列表开头添加"置顶"标签
                pinnedTags.add(0, "置顶");
                firstPost.setTags(pinnedTags);
            }
        }

        postDto.setList(postWithUserDtoList);
        Post lastPostInList = records.get(records.size() - 1);
        if (lastPostInList.getUpdated() != null) {
            postDto.setLastUpdated(lastPostInList.getUpdated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        } else {
            postDto.setLastUpdated(null);
        }
        postDto.setLastId(lastPostInList.getId());
        postDto.setHasNextPage(hasNextPage); // 新增：是否有下一页

        return postDto;
    }

    /**
     * 按标签流式查询帖子
     * 
     * @param tagId 标签ID
     * @param lastId 最后一个帖子ID
     * @param lastUpdated 最后更新时间
     * @param pageSize 页面大小
     * @return PostDto对象
     */
    @Override
    public PostDto getPostListByTag(Integer tagId, Integer lastId, LocalDateTime lastUpdated, Integer pageSize) {
        // 🚀 优化：查询pageSize+1条记录来判断是否有下一页，避免COUNT查询
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        
        // 指定查询字段，排除content以提升性能
        wrapper.select(Post::getId, Post::getUserId, Post::getTitle, 
                      Post::getLikeCount, Post::getDislikeCount, Post::getCommentCount,
                      Post::getTagIdsString, Post::getCreated, Post::getUpdated, Post::getDeleted);
        
        // 添加tag筛选条件 - 使用LIKE查询tag_ids_string字段
        wrapper.and(w -> w.like(Post::getTagIdsString, "," + tagId + ",")
                         .or(subW -> subW.likeRight(Post::getTagIdsString, tagId + ","))
                         .or(subW -> subW.likeLeft(Post::getTagIdsString, "," + tagId))
                         .or(subW -> subW.eq(Post::getTagIdsString, tagId.toString())));
        
        // 添加游标分页条件
        if (lastId != 0 && lastUpdated != null) {
            wrapper.and(w -> w.lt(Post::getUpdated, lastUpdated)
                    .or(subW -> subW.eq(Post::getUpdated, lastUpdated).lt(Post::getId, lastId)));
        }
        
        wrapper.orderByDesc(Post::getUpdated, Post::getId);
        
        // 🚀 查询pageSize+1条记录，用于判断是否有下一页
        Page<Post> page = new Page<>(1, pageSize + 1);
        IPage<Post> postPage = this.page(page, wrapper);
        List<Post> records = postPage.getRecords();

        PostDto postDto = new PostDto();
        
        // 🚀 判断是否有下一页
        boolean hasNextPage = records.size() > pageSize;
        if (hasNextPage) {
            records = records.subList(0, pageSize); // 只取pageSize条记录
        }
        
        if (records.isEmpty()) {
            postDto.setList(new ArrayList<>());
            postDto.setLastUpdated(lastUpdated);
            postDto.setLastId(lastId);
            postDto.setHasNextPage(false);
            return postDto;
        }

        // 🚀 优化：使用批量用户信息缓存
        List<Integer> userIds = records.stream().map(Post::getUserId).distinct().collect(Collectors.toList());
        Map<Integer, User> userMap = getUserInfoBatch(userIds);

        // 将 Post 转换为 PostWithUserDto 并填充用户信息
        List<PostWithUserDto> postWithUserDtoList = new ArrayList<>();
        for (Post postRecord : records) {
            PostWithUserDto dto = new PostWithUserDto();
            // 复制 Post 属性到 PostWithUserDto
            dto.setId(postRecord.getId());
            dto.setUserId(postRecord.getUserId());
            dto.setTitle(postRecord.getTitle());
            dto.setCommentCount(postRecord.getCommentCount());
            dto.setLikeCount(postRecord.getLikeCount());
            
            // 设置真实的点踩数
            dto.setDislikeCount(postRecord.getDislikeCount() != null ? postRecord.getDislikeCount() : 0);
            
            // 日期转换
            if (postRecord.getCreated() != null) {
                dto.setCreated(postRecord.getCreated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
            if (postRecord.getUpdated() != null) {
                dto.setUpdated(postRecord.getUpdated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
            dto.setDeleted(postRecord.getDeleted());
            dto.setTagIdsStringAlias(postRecord.getTagIdsString()); // 设置原始 tagIdsString

            // 填充用户信息
            User author = userMap.get(postRecord.getUserId());
            if (author != null) {
                dto.setUsername(author.getUsername());
                dto.setAvatar(author.getAvatar());
            }
            postWithUserDtoList.add(dto);
        }

        // 处理标签信息 (processPostTags 需要 List<PostWithUserDto>)
        processPostTags(postWithUserDtoList);

        postDto.setList(postWithUserDtoList);
        Post lastPostInList = records.get(records.size() - 1);
        if (lastPostInList.getUpdated() != null) {
            postDto.setLastUpdated(lastPostInList.getUpdated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        } else {
            postDto.setLastUpdated(null);
        }
        postDto.setLastId(lastPostInList.getId());
        postDto.setHasNextPage(hasNextPage);

        return postDto;
    }

    /**
     * 返回用户是否给某条post点过赞
     * @param loginId
     * @param postId
     * @return Like对象 (type: 1 点赞, -1 点踩) 或 null (无反应)
     */
    @Override
    public Like checkIfLiked(int loginId, Integer postId) {
        LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper();
        wrapper.eq(Like::getPostId, postId)
                .eq(Like::getUserId, loginId);
        Like like = likeMapper.selectOne(wrapper);
        return like;
    }

    // 实现根据关键词搜索帖子方法
    @Override
    public List<PostWithUserDto> searchPosts(String keyword) {
        // 直接调用 PostMapper 中实现的联表查询方法
        List<PostWithUserDto> posts = postMapper.searchPostsByKeyword(keyword);
        // 处理标签字符串，转换为标签名称列表
        processPostTags(posts);
        return posts;
    }
    
    // 实现根据关键词搜索帖子方法（支持分页）
    @Override
    public List<PostWithUserDto> searchPosts(String keyword, Integer page, Integer pageSize) {
        // 调用带分页参数的搜索方法
        List<PostWithUserDto> posts = postMapper.searchPostsByKeywordWithPaging(keyword, page, pageSize);
        // 处理标签字符串，转换为标签名称列表
        processPostTags(posts);
        return posts;
    }
    
    // 实现搜索帖子总数统计方法
    @Override
    public Integer countSearchPosts(String keyword) {
        return postMapper.countSearchPostsByKeyword(keyword);
    }
    
    // 使用MyBatis-Plus分页实现搜索
    @Override
    public IPage<PostWithUserDto> searchPostsWithPage(String keyword, Integer page, Integer pageSize) {
        // 创建分页参数
        IPage<PostWithUserDto> pageParam = new Page<>(page, pageSize);
        // 调用Mapper的分页方法
        IPage<PostWithUserDto> result = postMapper.searchPostsByKeywordWithPage(pageParam, keyword);
        // 处理分页结果中的标签
        processPostTags(result.getRecords());
        return result;
    }

    // 实现根据ID获取帖子详情方法
    @Override
    public PostWithUserDto getPostDetailById(Integer postId) {
        String cacheKey = POST_DETAIL_CACHE_PREFIX + postId;
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);

        if (cachedValue != null) {
            if (cachedValue instanceof PostWithUserDto) {
                PostWithUserDto cachedDto = (PostWithUserDto) cachedValue;
                // 检查 avatar 字段，如果为 null，则认为缓存无效，需要重新获取
                if (cachedDto.getAvatar() != null) {
                    // 从缓存中获取的数据已经包含了处理好的标签，直接返回
                    return cachedDto;
                } else {
                    // Avatar 为空，可能是旧缓存，清除它
                    log.warn("getPostDetailById: Post detail cache for postId {} has null avatar, clearing cache.", postId);
                    clearPostDetailCache(postId);
                    // 继续执行下面的数据库查询逻辑
                }
            } else if ("EMPTY_POST_MARKER".equals(cachedValue.toString())) {
                // 如果缓存的是特殊标记的空对象，则说明帖子不存在
                return null;
            }
        }

        // 缓存未命中或缓存无效，从数据库查询
        PostWithUserDto postDetail = postMapper.getPostDetailById(postId);
        
        // 只有从数据库查询的数据才需要处理标签字符串，转换为标签名称列表
        if (postDetail != null) {
            processPostTags(java.util.Arrays.asList(postDetail));
            
            // 将处理好标签的结果存入缓存，设置过期时间（例如 1 小时）
            redisTemplate.opsForValue().set(cacheKey, postDetail, 1, TimeUnit.HOURS);
        } else {
            // 缓存空标记，防止缓存穿透，设置较短的过期时间（例如 5 分钟）
            redisTemplate.opsForValue().set(cacheKey, "EMPTY_POST_MARKER", 5, TimeUnit.MINUTES);
        }
        return postDetail;
    }

    // 辅助方法：处理 PostWithUserDto 列表的标签字符串，转换为标签名称列表
    private void processPostTags(List<PostWithUserDto> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }

        // 收集所有需要查询的标签 ID
        List<Integer> tagIdsToFetch = new ArrayList<>();
        for (PostWithUserDto post : posts) {
            if (post.getTagIdsStringAlias() != null && !post.getTagIdsStringAlias().isEmpty()) {
                String[] tagIdStrings = post.getTagIdsStringAlias().split(",");
                for (String tagIdStr : tagIdStrings) {
                    try {
                        Integer tagId = Integer.parseInt(tagIdStr.trim());
                        tagIdsToFetch.add(tagId);
                    } catch (NumberFormatException e) {
                        // 处理无效的标签ID字符串，记录日志并忽略
                        log.warn("processPostTags: Invalid tag ID format: {}", tagIdStr, e);
                    }
                }
            }
        }

        if (tagIdsToFetch.isEmpty()) {
            // 没有需要查询的标签
            for (PostWithUserDto post : posts) {
                post.setTags(new ArrayList<>()); // 设置空标签列表
            }
            return;
        }

        // 获取标签名称映射，优先从缓存获取
        Map<Integer, String> tagNamesMap = getTagNamesMapFromCache();

        // 将标签名称设置回 PostWithUserDto 对象中
        for (PostWithUserDto post : posts) {
            List<String> postTagNames = new ArrayList<>();
            if (post.getTagIdsStringAlias() != null && !post.getTagIdsStringAlias().isEmpty()) {
                String[] tagIdStrings = post.getTagIdsStringAlias().split(",");
                for (String tagIdStr : tagIdStrings) {
                    try {
                        Integer tagId = Integer.parseInt(tagIdStr.trim());
                        
                        // 尝试多种方式查找标签（为了兼容可能的类型问题）
                        String tagName = tagNamesMap.get(tagId);
                        if (tagName == null) {
                            // 如果直接查找失败，尝试通过字符串键查找（兼容旧缓存）
                            Object stringResult = tagNamesMap.get(tagIdStr.trim());
                            if (stringResult instanceof String) {
                                tagName = (String) stringResult;
                            }
                        }
                        if (tagName == null) {
                            // 最后尝试遍历查找
                            for (Object key : tagNamesMap.keySet()) {
                                if (key.toString().equals(tagIdStr.trim())) {
                                    tagName = tagNamesMap.get(key);
                                    break;
                                }
                            }
                        }
                        
                        if (tagName != null) {
                            postTagNames.add(tagName);
                        }
                    } catch (NumberFormatException e) {
                        // 处理无效的标签ID字符串，记录日志并忽略
                        log.warn("processPostTags: Invalid tag ID format: {}", tagIdStr, e);
                    }
                }
            }
            post.setTags(postTagNames);
        }
    }

    /**
     * 从缓存获取标签名称映射，如果缓存不存在则从数据库查询并缓存
     * @return 标签ID到标签名称的映射
     */
    private Map<Integer, String> getTagNamesMapFromCache() {
        // 首先尝试从缓存获取
        Object cachedTagMap = redisTemplate.opsForValue().get(TAG_MAP_CACHE_KEY);
        
        if (cachedTagMap != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<Integer, String> tagMap = (Map<Integer, String>) cachedTagMap;
                
                // 自动续期：重新设置过期时间
                redisTemplate.expire(TAG_MAP_CACHE_KEY, TAG_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                
                return tagMap;
            } catch (Exception e) {
                log.warn("getTagNamesMapFromCache: 标签缓存反序列化失败，将重新从数据库查询: {}", e.getMessage());
                redisTemplate.delete(TAG_MAP_CACHE_KEY);
            }
        }

        // 缓存未命中，从数据库查询所有标签
        LambdaQueryWrapper<Tag> tagWrapper = new LambdaQueryWrapper<>();
        tagWrapper.eq(Tag::getDeleted, 0); // 只查询未删除的标签
        List<Tag> allTags = tagMapper.selectList(tagWrapper);

        // 构建标签映射，确保键是Integer类型
        Map<Integer, String> tagNamesMap = new HashMap<>();
        for (Tag tag : allTags) {
            tagNamesMap.put(tag.getId(), tag.getName());
        }

        // 将映射存入缓存，设置长期过期时间
        redisTemplate.opsForValue().set(TAG_MAP_CACHE_KEY, tagNamesMap, TAG_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        
        return tagNamesMap;
    }

    // 覆盖 ServiceImpl 的 save 方法，处理标签保存逻辑
    @Override
    @Transactional
    public boolean save(Post post) {
        // 将 tagIds 列表转换为逗号分隔的字符串
        if (post.getTagIds() != null && !post.getTagIds().isEmpty()) {
            String tagIdsString = post.getTagIds().stream()
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(","));
            post.setTagIdsString(tagIdsString);
        } else {
            post.setTagIdsString(""); // 没有标签则保存为空字符串
        }

        // 调用父类方法保存帖子 (此时 tagIdsString 会被保存到数据库)
        boolean success = super.save(post);

        if (success) {
            // 发布帖子后，清除该帖子详情缓存（如果存在的话）
            clearPostDetailCache(post.getId());
            // 发布帖子后，清除所有首页分页缓存
            clearPostListCache();
            // 🚀 清除用户缓存（帖子数量统计已改变）
            if (post.getUserId() != null) {
                userService.clearUserCache(post.getUserId());
            }
        }

        return success;
    }

    // 覆盖 updateById 方法，在更新后清除缓存
    @Override
    public boolean updateById(Post entity) {
        boolean success = super.updateById(entity);
        if (success && entity != null && entity.getId() != null) {
            clearPostDetailCache(entity.getId());
            // 更新帖子后，清除所有首页分页缓存
            clearPostListCache();
            // 🚀 清除用户缓存（帖子信息可能影响统计）
            if (entity.getUserId() != null) {
                userService.clearUserCache(entity.getUserId());
            }
        }
        return success;
    }

    // 覆盖 removeById 方法，在删除后清除缓存（不发送通知邮件）
    @Override
    @Transactional // 添加事务注解
    public boolean removeById(java.io.Serializable id) {
        // 在逻辑删除之前获取帖子信息
        Post post = getById(id);
        if (post == null) {
            // 帖子不存在，直接返回删除失败（或者根据需求返回true，如果id对应的记录确实不存在）
            return false;
        }

        // 执行逻辑删除
        boolean success = super.removeById(id);

        if (success) {
            // 清除相关缓存
            clearPostDetailCache(post.getId());
            clearPostListCache();
            // 🚀 清除用户缓存（帖子数量统计已改变）
            if (post.getUserId() != null) {
                userService.clearUserCache(post.getUserId());
            }
        }

        return success;
    }

    /**
     * 管理员删除帖子（会发送通知和邮件）
     * @param id 帖子ID
     * @return 是否删除成功
     */
    @Override
    @Transactional
    public boolean removeByIdWithNotification(java.io.Serializable id) {
        // 🚀 性能优化：一次查询获取帖子和作者信息，减少数据库查询
        PostWithUserDto postWithAuthor = postMapper.getPostDetailById((Integer) id);
        if (postWithAuthor == null) {
            // 帖子不存在，直接返回删除失败
            return false;
        }
        
        // 🚀 一次查询获取完整的作者信息（包含邮箱和通知设置）
        User author = null;
        if (postWithAuthor.getUserId() != null) {
            author = userService.getById(postWithAuthor.getUserId());
        }
        
        // 转换为Post对象进行删除操作
        Post post = new Post();
        post.setId(postWithAuthor.getId());
        post.setUserId(postWithAuthor.getUserId());
        post.setTitle(postWithAuthor.getTitle());

        // 🚀 性能优化：提前获取当前管理员ID，避免在异步操作中重复获取
        Integer currentAdminId = StpUtil.getLoginIdAsInt();

        // 执行逻辑删除
        boolean success = super.removeById(id);

        if (success) {
            // 🚀 性能优化：使用批量缓存清理，减少Redis连接开销
            clearPostRelatedCachesBatch(post.getId(), post.getUserId());
            clearPostListCache();
            // 🚀 清除用户缓存（帖子数量统计已改变）
            if (post.getUserId() != null) {
                userService.clearUserCache(post.getUserId());
            }

            // 🚀 性能优化：异步发送通知和邮件，不阻塞主流程
            // 使用已查询的作者信息，无需额外查询
            final User finalAuthor = author; // 创建final引用供lambda使用
            CompletableFuture.runAsync(() -> {
                try {
                    if (finalAuthor != null) {
                        // 发送通知
                        Notification notification = new Notification();
                        notification.setReceiverId(finalAuthor.getId());
                        notification.setSenderId(currentAdminId); // 使用提前获取的管理员ID
                        notification.setNotificationType(9); // 9 是管理员删帖的通知类型
                        notification.setRelatedId(post.getId());
                        notification.setRelatedType("1"); // 1: 帖子
                        notification.setTriggerEntityId(post.getId()); // 触发实体为被删除的帖子
                        notification.setTriggerEntityType(1); // 触发实体类型为帖子
                        notification.setRead(false);
                        notificationService.save(notification);
                        
                        // 清除接收者的通知缓存
                        notificationCacheService.clearNotificationListCache(finalAuthor.getId());

                        // 检查是否应该发送邮件通知（使用已查询的用户信息）
                        if (!StringUtils.isEmpty(finalAuthor.getEmail()) && Boolean.TRUE.equals(finalAuthor.getEnableOtherNotification())) {
                            smsUtils.sendPostDeletedNotification(finalAuthor.getEmail(), post.getTitle());
                            log.info("删帖邮件通知已发送给用户: {}", finalAuthor.getId());
                        } else {
                            log.info("用户 {} 已关闭其他邮件通知或无邮箱地址", finalAuthor.getId());
                        }
                        
                        log.info("异步发送帖子删除通知成功: 帖子ID={}, 作者ID={}", post.getId(), finalAuthor.getId());
                    }
                } catch (Exception e) {
                    log.error("异步发送帖子删除通知失败: post id = " + post.getId(), e);
                    // 异步操作失败不影响删除操作的成功
                }
            });
        }

        return success;
    }

    // 辅助方法：清除所有首页分页缓存
    @Override
    public void clearPostListCache() {
        log.info("首页帖子列表已不使用缓存，无需清除");
    }

    // 辅助方法：清除帖子详情缓存
    @Override
    public void clearPostDetailCache(Integer postId) {
        String cacheKey = POST_DETAIL_CACHE_PREFIX + postId;
        log.info("准备清除帖子详情缓存，缓存键: {}", cacheKey);
        
        try {
            // 先检查缓存是否存在
            Boolean hasKey = redisTemplate.hasKey(cacheKey);
            log.info("缓存键 {} 是否存在: {}", cacheKey, hasKey);
            
            if (Boolean.TRUE.equals(hasKey)) {
                Boolean deleted = redisTemplate.delete(cacheKey);
                if (Boolean.TRUE.equals(deleted)) {
                    log.info("成功清除帖子详情缓存: {}", cacheKey);
                } else {
                    log.warn("清除帖子详情缓存失败: {}", cacheKey);
                }
            } else {
                log.info("帖子详情缓存不存在，无需清除: {}", cacheKey);
            }
        } catch (Exception e) {
            log.error("清除帖子详情缓存时发生错误，缓存键: {}, 错误: {}", cacheKey, e.getMessage(), e);
        }
    }

    /**
     * 🚀 性能优化：批量清除删除帖子相关的所有缓存
     * @param postId 帖子ID
     * @param userId 用户ID
     */
    private void clearPostRelatedCachesBatch(Integer postId, Integer userId) {
        List<String> keysToDelete = new ArrayList<>();
        
        // 收集需要删除的缓存键
        String postDetailKey = POST_DETAIL_CACHE_PREFIX + postId;
        keysToDelete.add(postDetailKey);
        
        // 如果有用户相关的缓存键，也可以加入批量删除
        if (userId != null) {
            // 这里可以添加用户相关的缓存键，例如用户统计缓存等
            // keysToDelete.add(USER_STATS_CACHE_PREFIX + userId);
        }
        
        try {
            // 批量检查缓存键是否存在
            List<Boolean> existFlags = redisTemplate.opsForValue().multiGet(keysToDelete)
                    .stream()
                    .map(obj -> obj != null)
                    .collect(Collectors.toList());
            
            // 只删除存在的缓存键
            List<String> existingKeys = new ArrayList<>();
            for (int i = 0; i < keysToDelete.size(); i++) {
                if (existFlags.get(i)) {
                    existingKeys.add(keysToDelete.get(i));
                }
            }
            
            if (!existingKeys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(existingKeys);
                log.info("批量清除帖子相关缓存成功，删除了 {} 个缓存键: {}", deletedCount, existingKeys);
            } else {
                log.info("帖子相关缓存不存在，无需清除: {}", keysToDelete);
            }
        } catch (Exception e) {
            log.error("批量清除帖子相关缓存时发生错误，缓存键: {}, 错误: {}", keysToDelete, e.getMessage(), e);
            // 如果批量操作失败，回退到单个删除
            clearPostDetailCache(postId);
        }
    }

    // 辅助方法：清除标签缓存
    @Override
    public void clearTagCache() {
        try {
            Boolean hasKey = redisTemplate.hasKey(TAG_MAP_CACHE_KEY);
            if (Boolean.TRUE.equals(hasKey)) {
                Boolean deleted = redisTemplate.delete(TAG_MAP_CACHE_KEY);
                if (Boolean.TRUE.equals(deleted)) {
                    log.info("成功清除标签缓存: {}", TAG_MAP_CACHE_KEY);
                } else {
                    log.warn("清除标签缓存失败: {}", TAG_MAP_CACHE_KEY);
                }
            } else {
                log.info("标签缓存不存在，无需清除: {}", TAG_MAP_CACHE_KEY);
            }
        } catch (Exception e) {
            log.error("清除标签缓存时发生错误，缓存键: {}, 错误: {}", TAG_MAP_CACHE_KEY, e.getMessage(), e);
        }
    }

    @Override
    public long getTotalPostCount() {
        return this.count(new LambdaQueryWrapper<Post>().eq(Post::getDeleted, 0));
    }

    @Override
    public long getNewPostCountToday() {
        LocalDate today = LocalDate.now();
        Date startOfDay = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endOfDay = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        return this.count(
            new LambdaQueryWrapper<Post>()
                .eq(Post::getDeleted, 0)
                .ge(Post::getCreated, startOfDay)
                .lt(Post::getCreated, endOfDay)
        );
    }

    // 实现用户删除自己的帖子方法
    @Override
    @Transactional
    public boolean deletePostByUserId(Integer postId, Integer userId) {
        // 1. 检查帖子是否存在且未被删除
        Post post = getById(postId);
        if (post == null || post.getDeleted() == 1) {
            // 帖子不存在或已被删除
            return false;
        }

        // 2. 检查当前用户是否是帖子的作者
        if (!post.getUserId().equals(userId)) {
            // 不是作者，无权删除
            return false;
        }

        // 3. 调用逻辑删除方法 (会处理缓存清除)
        return removeById(postId);
    }

    // 实现用户修改自己的帖子方法
    @Override
    @Transactional
    public boolean updatePostByUserId(Post post, Integer userId) {
        // 1. 检查帖子是否存在且未被删除
        Post existingPost = getById(post.getId());
        if (existingPost == null || existingPost.getDeleted() == 1) {
            // 帖子不存在或已被删除
            return false;
        }

        // 2. 检查当前用户是否是帖子的作者
        if (!existingPost.getUserId().equals(userId)) {
            // 不是作者，无权修改
            return false;
        }

        // 3. 保留原有的一些字段
        post.setUserId(existingPost.getUserId());
        post.setCreated(existingPost.getCreated());
        post.setDeleted(existingPost.getDeleted());
        
        // 4. 处理标签更新
        if (post.getTagIds() != null && !post.getTagIds().isEmpty()) {
            String tagIdsString = post.getTagIds().stream()
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(","));
            post.setTagIdsString(tagIdsString);
        } else {
            post.setTagIdsString(""); // 没有标签则保存为空字符串
        }

        // 5. 执行更新操作
        boolean success = updateById(post);

        if (success) {
            // 6. 清除相关缓存
            clearPostDetailCache(post.getId());
            clearPostListCache();
            // 🚀 清除用户缓存（帖子标签可能影响统计）
            if (post.getUserId() != null) {
                userService.clearUserCache(post.getUserId());
            }
        }

        return success;
    }

    /**
     * 获取本周最火的5个帖子
     * 热度计算公式：点赞数 * 2 + 评论数 * 1.5 + 点踩数 * 0.3
     * 黑红也是红！点踩也算热度，本周内所有帖子公平竞争
     * @return 热门帖子列表
     */
    @Override
    public List<PostWithUserDto> getHotPostsInSevenDays() {
        // 先尝试从缓存获取
        Object cachedData = redisTemplate.opsForValue().get(HOT_POSTS_CACHE_KEY);
        if (cachedData != null) {
            try {
                @SuppressWarnings("unchecked")
                List<PostWithUserDto> cachedHotPosts = (List<PostWithUserDto>) cachedData;
                log.info("从缓存获取本周热门帖子，数量: {}", cachedHotPosts.size());
                return cachedHotPosts;
            } catch (Exception e) {
                log.warn("热门帖子缓存反序列化失败，清除缓存: {}", e.getMessage());
                redisTemplate.delete(HOT_POSTS_CACHE_KEY);
            }
        }

        // 缓存未命中，从数据库查询
        log.info("缓存未命中，从数据库查询本周热门帖子");

        // 🔥 计算本周时间范围（7天前到现在）
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        Date sevenDaysAgoDate = Date.from(sevenDaysAgo.atZone(ZoneId.systemDefault()).toInstant());

        // 🔥 查询本周内的所有活跃帖子
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getDeleted, 0) // 未删除的帖子
               .ge(Post::getCreated, sevenDaysAgoDate) // 本周内创建的帖子
               .select(Post::getId, Post::getUserId, Post::getTitle, Post::getLikeCount, 
                      Post::getCommentCount, Post::getDislikeCount, Post::getTagIdsString,
                      Post::getCreated, Post::getUpdated, Post::getDeleted);

        List<Post> allPosts = this.list(wrapper);

        if (allPosts.isEmpty()) {
            log.info("本周内没有找到帖子");
            // 缓存空结果，设置较短的过期时间
            redisTemplate.opsForValue().set(HOT_POSTS_CACHE_KEY, new ArrayList<>(), 1, TimeUnit.HOURS);
            return new ArrayList<>();
        }

        // 🔥 计算每个帖子的热度分数并排序
        // 创建一个包含热度分数的内部类
        class PostWithScore {
            Post post;
            double score;
            
            PostWithScore(Post post, double score) {
                this.post = post;
                this.score = score;
            }
        }
        
        List<Post> hotPosts = allPosts.stream()
                .map(post -> {
                    // 🔥 热度计算公式：点赞数 * 2 + 评论数 * 1.5 + 点踩数 * 0.3（黑红也是红！）
                    double hotScore = (post.getLikeCount() != null ? post.getLikeCount() : 0) * 2.0 
                                    + (post.getCommentCount() != null ? post.getCommentCount() : 0) * 1.5
                                    + (post.getDislikeCount() != null ? post.getDislikeCount() : 0) * 0.3;
                    
                    log.debug("帖子[{}] 热度分数: {}", post.getTitle(), hotScore);
                    
                    return new PostWithScore(post, hotScore);
                })
                .sorted((p1, p2) -> Double.compare(p2.score, p1.score)) // 按热度分数降序排序
                .limit(5) // 取前5个
                .map(postWithScore -> postWithScore.post) // 提取Post对象
                .collect(Collectors.toList());

        // 🚀 优化：使用批量用户信息缓存
        List<Integer> userIds = hotPosts.stream().map(Post::getUserId).distinct().collect(Collectors.toList());
        Map<Integer, User> userMap = getUserInfoBatch(userIds);

        // 将 Post 转换为 PostWithUserDto 并填充用户信息
        List<PostWithUserDto> hotPostsWithUser = new ArrayList<>();
        for (Post post : hotPosts) {
            PostWithUserDto dto = new PostWithUserDto();
            // 复制 Post 属性到 PostWithUserDto
            dto.setId(post.getId());
            dto.setUserId(post.getUserId());
            dto.setTitle(post.getTitle());
            dto.setCommentCount(post.getCommentCount());
            dto.setLikeCount(post.getLikeCount());
            
            // 设置真实的点踩数
            dto.setDislikeCount(post.getDislikeCount() != null ? post.getDislikeCount() : 0);
            
            // 日期转换
            if (post.getCreated() != null) {
                dto.setCreated(post.getCreated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
            if (post.getUpdated() != null) {
                dto.setUpdated(post.getUpdated().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
            dto.setDeleted(post.getDeleted());
            dto.setTagIdsStringAlias(post.getTagIdsString());

            // 填充用户信息
            User author = userMap.get(post.getUserId());
            if (author != null) {
                dto.setUsername(author.getUsername());
                dto.setAvatar(author.getAvatar());
            }
            
            hotPostsWithUser.add(dto);
        }

        // 处理标签信息
        processPostTags(hotPostsWithUser);

        // 将结果缓存2小时（热度相对稳定，适当延长缓存）
        redisTemplate.opsForValue().set(HOT_POSTS_CACHE_KEY, hotPostsWithUser, 2, TimeUnit.HOURS);
        log.info("成功查询并缓存本周内热门帖子，数量: {}，按纯热度排序：点赞*2 + 评论*1.5 + 点踩*0.3", hotPostsWithUser.size());

        return hotPostsWithUser;
    }
    
    @Override
    public boolean setPinnedPost(Integer postId) {
        try {
            if (postId == null) {
                // 取消置顶
                redisTemplate.delete(PINNED_POST_KEY);
                // 🚀 同步更新内存缓存
                cachedPinnedPostId = null;
                log.info("取消首页置顶帖子，内存缓存已同步更新");
            } else {
                // 验证帖子是否存在且未删除
                Post post = this.getById(postId);
                if (post == null || post.getDeleted() == 1) {
                    log.warn("设置置顶帖子失败：帖子不存在或已删除，帖子ID: {}", postId);
                    return false;
                }
                
                // 设置置顶
                redisTemplate.opsForValue().set(PINNED_POST_KEY, postId);
                // 🚀 同步更新内存缓存
                cachedPinnedPostId = postId;
                log.info("设置首页置顶帖子，帖子ID: {}，内存缓存已同步更新", postId);
            }
            
            // 清除首页列表缓存，让置顶设置生效
            clearPostListCache();
            return true;
        } catch (Exception e) {
            log.error("设置置顶帖子失败，帖子ID: {}", postId, e);
            return false;
        }
    }
    
    @Override
    public Integer getPinnedPostId() {
        return getInternalPinnedPostId();
    }
}
