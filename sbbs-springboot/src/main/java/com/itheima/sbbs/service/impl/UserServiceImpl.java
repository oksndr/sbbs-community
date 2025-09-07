package com.itheima.sbbs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.sbbs.entity.User;
import com.itheima.sbbs.entity.UserSimpleDto;
import com.itheima.sbbs.entity.UserProfileDto;
import com.itheima.sbbs.entity.PostWithUserDto;
import com.itheima.sbbs.entity.Tag;
import com.itheima.sbbs.entity.UserLevel;
import com.itheima.sbbs.entity.UserBasicInfoDto;
import com.itheima.sbbs.mapper.UserMapper;
import com.itheima.sbbs.mapper.PostMapper;
import com.itheima.sbbs.mapper.CommentMapper;
import com.itheima.sbbs.mapper.UserFollowMapper;
import com.itheima.sbbs.mapper.TagMapper;
import com.itheima.sbbs.service.UserService;
import com.itheima.sbbs.service.PostService;
import com.itheima.sbbs.service.UserLevelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PostMapper postMapper;
    
    @Autowired
    private CommentMapper commentMapper;
    
    @Autowired
    private UserFollowMapper userFollowMapper;
    
    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private UserLevelService userLevelService;
    

    private static final String USER_SEARCH_CACHE_PREFIX = "userSearch:";
    private static final String USER_PROFILE_CACHE_PREFIX = "userProfile:";
    private static final String USER_BASIC_INFO_CACHE_PREFIX = "userBasicInfo:";
    private static final String USER_POSTS_CACHE_PREFIX = "userPosts:";

    //通过邮箱检索数据库里的用户(直接返回)
    @Override
    public User getUserByEmail(String email) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        User user = userMapper.selectOne(wrapper);
        return user;
    }

    // 添加根据关键词搜索用户的方法实现
    @Override
    public List<UserSimpleDto> searchUsers(String keyword) {
        String cacheKey = USER_SEARCH_CACHE_PREFIX + (keyword != null ? keyword : "empty");
        List<UserSimpleDto> cachedList = (List<UserSimpleDto>) redisTemplate.opsForValue().get(cacheKey);

        if (cachedList != null) {
            // 返回缓存数据
            return cachedList;
        }

        // 构建查询条件，只查询未删除的用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getDeleted, 0);
        
        // 如果关键词不为空，按用户名模糊搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.like(User::getUsername, keyword.trim());
        }
        
        // 限制查询结果数量为5个，防止数据库炸掉
        queryWrapper.last("LIMIT 5");

        List<User> users = userMapper.selectList(queryWrapper);

        // 转换为 UserSimpleDto 列表
        List<UserSimpleDto> resultList = users.stream()
                .map(user -> new UserSimpleDto(user.getId(), user.getUsername(), user.getAvatar()))
                .collect(Collectors.toList());

        // 将结果存入缓存，设置过期时间（例如 30 分钟）
        redisTemplate.opsForValue().set(cacheKey, resultList, 30, TimeUnit.MINUTES);

        return resultList;
    }
    
    // 添加根据关键词搜索用户的方法实现（支持分页）
    @Override
    public List<UserSimpleDto> searchUsers(String keyword, Integer page, Integer pageSize) {
        // 验证分页参数
        if (page == null || page < 1) {
            page = 1;
        }
        if (pageSize == null || pageSize < 1 || pageSize > 50) {
            pageSize = 10;
        }
        
        String cacheKey = USER_SEARCH_CACHE_PREFIX + (keyword != null ? keyword : "empty") + ":" + page + ":" + pageSize;
        List<UserSimpleDto> cachedList = (List<UserSimpleDto>) redisTemplate.opsForValue().get(cacheKey);

        if (cachedList != null) {
            // 返回缓存数据
            return cachedList;
        }

        // 构建查询条件，只查询未删除的用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getDeleted, 0);
        
        // 如果关键词不为空，按用户名模糊搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.like(User::getUsername, keyword.trim());
        }
        
        // 计算分页参数
        int offset = (page - 1) * pageSize;
        queryWrapper.last("OFFSET " + offset + " LIMIT " + pageSize);

        List<User> users = userMapper.selectList(queryWrapper);

        // 转换为 UserSimpleDto 列表
        List<UserSimpleDto> resultList = users.stream()
                .map(user -> new UserSimpleDto(user.getId(), user.getUsername(), user.getAvatar()))
                .collect(Collectors.toList());

        // 将结果存入缓存，设置过期时间（例如 30 分钟）
        redisTemplate.opsForValue().set(cacheKey, resultList, 30, TimeUnit.MINUTES);

        return resultList;
    }
    
    // 实现搜索用户总数统计方法
    @Override
    public Integer countSearchUsers(String keyword) {
        // 构建查询条件，只查询未删除的用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getDeleted, 0);
        
        // 如果关键词不为空，按用户名模糊搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.like(User::getUsername, keyword.trim());
        }
        
        return Math.toIntExact(userMapper.selectCount(queryWrapper));
    }
    
    // 使用MyBatis-Plus分页实现搜索
    @Override
    public IPage<UserSimpleDto> searchUsersWithPage(String keyword, Integer page, Integer pageSize) {
        // 创建分页参数
        Page<User> pageParam = new Page<>(page, pageSize);
        
        // 构建查询条件，只查询未删除的用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getDeleted, 0);
        
        // 如果关键词不为空，按用户名模糊搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.like(User::getUsername, keyword.trim());
        }
        
        // 执行分页查询
        Page<User> userPage = userMapper.selectPage(pageParam, queryWrapper);
        
        // 转换为UserSimpleDto
        Page<UserSimpleDto> resultPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        List<UserSimpleDto> dtoList = userPage.getRecords().stream()
                .map(user -> {
                    UserSimpleDto dto = new UserSimpleDto();
                    dto.setId(user.getId());
                    dto.setUsername(user.getUsername());
                    dto.setAvatar(user.getAvatar());
                    return dto;
                }).collect(Collectors.toList());
        
        resultPage.setRecords(dtoList);
        return resultPage;
    }

    @Override
    public long getTotalUserCount() {
        return this.count(new LambdaQueryWrapper<User>().eq(User::getDeleted, 0));
    }

    @Override
    public long getNewUserCountToday() {
        LocalDate today = LocalDate.now();
        Date startOfDay = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endOfDay = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        return this.count(
            new LambdaQueryWrapper<User>()
                .eq(User::getDeleted, 0)
                .ge(User::getCreated, startOfDay)
                .lt(User::getCreated, endOfDay)
        );
    }
    
    @Override
    public UserProfileDto getUserProfile(Integer userId, Integer pageNo, Integer pageSize) {
        // 检查参数
        if (userId == null || userId <= 0) {
            return null;
        }
        
        if (pageNo == null || pageNo < 1) {
            pageNo = 1;
        }
        
        if (pageSize == null || pageSize < 1) {
            pageSize = 15; // 默认页面大小改为15
        }
        
        //获取当前登录用户ID（用于判断关注状态）
        Integer currentUserId = null;
        try {
            if (cn.dev33.satoken.stp.StpUtil.isLogin()) {
                currentUserId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsInt();
            }
        } catch (Exception e) {
            // 忽略异常，currentUserId保持为null
        }
        
        // 🚀 分别获取用户基本信息和帖子列表（使用分离的缓存）
        UserBasicInfoDto basicInfo = getUserBasicInfo(userId, currentUserId);
        if (basicInfo == null) {
            return null; // 用户不存在
        }
        
        List<PostWithUserDto> posts = getUserPosts(userId, pageNo, pageSize);
        
        // 🚀 组装完整的UserProfileDto
        UserProfileDto userProfileDto = new UserProfileDto();
        
        // 从UserBasicInfoDto复制基本信息
        userProfileDto.setId(basicInfo.getId());
        userProfileDto.setUsername(basicInfo.getUsername());
        userProfileDto.setAvatar(basicInfo.getAvatar());
        userProfileDto.setCreated(basicInfo.getCreated());
        userProfileDto.setGroupId(basicInfo.getGroupId());
        
        // 复制等级信息
        userProfileDto.setExperience(basicInfo.getExperience());
        userProfileDto.setLevel(basicInfo.getLevel());
        userProfileDto.setLevelName(basicInfo.getLevelName());
        userProfileDto.setLevelIcon(basicInfo.getLevelIcon());
        userProfileDto.setCurrentLevelMinExp(basicInfo.getCurrentLevelMinExp());
        userProfileDto.setNextLevelRequiredExp(basicInfo.getNextLevelRequiredExp());
        userProfileDto.setProgressPercent(basicInfo.getProgressPercent());
        
        // 🎯 复制直观的经验值显示
        userProfileDto.setCurrentStageExp(basicInfo.getCurrentStageExp());
        userProfileDto.setExpNeededForNextLevel(basicInfo.getExpNeededForNextLevel());
        
        // 复制统计数据
        userProfileDto.setPostCount(basicInfo.getPostCount());
        userProfileDto.setCommentCount(basicInfo.getCommentCount());
        userProfileDto.setFollowerCount(basicInfo.getFollowerCount());
        userProfileDto.setFollowingCount(basicInfo.getFollowingCount());
        
        // 🎯 设置关注状态
        userProfileDto.setIsFollowing(basicInfo.getIsFollowing());
        
        // 设置帖子列表和分页信息
        userProfileDto.setPosts(posts != null ? posts : new ArrayList<>());
        userProfileDto.setCurrentPage(pageNo);
        userProfileDto.setPageSize(pageSize);
        userProfileDto.setTotal(basicInfo.getPostCount());
        
        return userProfileDto;
    }
    
    // 辅助方法：处理PostWithUserDto列表的标签字符串，转换为标签名称列表
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
                        tagIdsToFetch.add(Integer.parseInt(tagIdStr.trim()));
                    } catch (NumberFormatException e) {
                        // 处理无效的标签ID字符串，可以记录日志或忽略
                        log.error("Invalid tag ID format: " + tagIdStr, e);
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

        // 批量查询标签信息
        LambdaQueryWrapper<Tag> tagWrapper = new LambdaQueryWrapper<>();
        tagWrapper.in(Tag::getId, tagIdsToFetch);
        List<Tag> tags = tagMapper.selectList(tagWrapper);

        // 将标签信息按 ID 存储到 Map，方便查找
        Map<Integer, String> tagNamesMap = tags.stream()
                                            .collect(Collectors.toMap(Tag::getId, Tag::getName));

        // 将标签名称设置回 PostWithUserDto 对象中
        for (PostWithUserDto post : posts) {
            List<String> postTagNames = new ArrayList<>();
            if (post.getTagIdsStringAlias() != null && !post.getTagIdsStringAlias().isEmpty()) {
                String[] tagIdStrings = post.getTagIdsStringAlias().split(",");
                for (String tagIdStr : tagIdStrings) {
                    try {
                        Integer tagId = Integer.parseInt(tagIdStr.trim());
                        String tagName = tagNamesMap.get(tagId);
                        if (tagName != null) {
                            postTagNames.add(tagName);
                        }
                    } catch (NumberFormatException e) {
                        // 处理无效的标签ID字符串，可以记录日志或忽略
                        log.error("Invalid tag ID format: " + tagIdStr, e);
                    }
                }
            }
            post.setTags(postTagNames);
        }
    }
    
    /**
     * 🚀 优化版本：处理PostWithUserDto列表的标签信息（高性能版本）
     * 直接从数据库查询返回的tagNames字符串解析，避免额外的数据库查询
     * @param posts 帖子列表
     */
    private void processPostTagsOptimized(List<PostWithUserDto> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }

        for (PostWithUserDto post : posts) {
            List<String> postTagNames = new ArrayList<>();
            
            // 直接从tagNames字段解析标签名称
            if (post.getTagNames() != null && !post.getTagNames().trim().isEmpty()) {
                String[] tagNameArray = post.getTagNames().split(",");
                for (String tagName : tagNameArray) {
                    String trimmedTagName = tagName.trim();
                    if (!trimmedTagName.isEmpty()) {
                        postTagNames.add(trimmedTagName);
                    }
                }
            }
            
            post.setTags(postTagNames);
        }
    }
    
    @Override
    public boolean addUserExperience(Integer userId, Integer experience) {
        if (userId == null || userId <= 0 || experience == null || experience <= 0) {
            return false;
        }
        
        try {
            // 使用原生SQL更新，避免并发问题
            int rows = userMapper.addUserExperience(userId, experience);
            
            if (rows > 0) {
                // 🚀 优化：用户信息缓存改为自然过期，不主动清除
                log.info("用户 {} 增加 {} 经验值", userId, experience);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("增加用户经验值失败，用户ID: {}, 经验值: {}", userId, experience, e);
            return false;
        }
    }
    
    @Override
    public boolean reduceUserExperience(Integer userId, Integer experience) {
        if (userId == null || userId <= 0 || experience == null || experience <= 0) {
            return false;
        }
        
        try {
            // 使用原生SQL更新，确保经验值不会变成负数
            int rows = userMapper.reduceUserExperience(userId, experience);
            
            if (rows > 0) {
                // 🚀 优化：用户信息缓存改为自然过期，不主动清除
                log.info("用户 {} 减少 {} 经验值", userId, experience);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("减少用户经验值失败，用户ID: {}, 经验值: {}", userId, experience, e);
            return false;
        }
    }
    
    
    
    @Override
    public Map<String, Object> getUserLevelInfo(Integer userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        
        User user = getById(userId);
        if (user == null || user.getDeleted() == 1) {
            return null;
        }
        
        // 确保经验值不为null
        if (user.getExperience() == null) {
            user.setExperience(0);
        }
        
        Map<String, Object> levelInfo = new java.util.HashMap<>();
        levelInfo.put("userId", user.getId());
        levelInfo.put("username", user.getUsername());
        levelInfo.put("experience", user.getExperience());
        
        // 通过UserLevelService获取等级信息
        UserLevel userLevel = userLevelService.getLevelByExperience(user.getExperience());
        if (userLevel != null) {
            levelInfo.put("level", userLevel.getLevel());
            levelInfo.put("levelName", userLevel.getName());
            levelInfo.put("levelIcon", ""); // 图标已包含在name中
            levelInfo.put("currentLevelMinExp", userLevel.getMinExperience());
            levelInfo.put("nextLevelRequiredExp", userLevel.getMaxExperience());
            
            // 计算当前等级进度百分比
            if (userLevel.getMaxExperience() != null) {
                int progressExp = user.getExperience() - userLevel.getMinExperience();
                int totalExpForLevel = userLevel.getMaxExperience() - userLevel.getMinExperience();
                double progressPercent = (double) progressExp / totalExpForLevel * 100;
                levelInfo.put("progressPercent", Math.min(100.0, Math.max(0.0, progressPercent)));
                
                // 🎯 计算本阶段总共需要多少经验值（下一级最低 - 当前级最低）
                UserLevel nextLevel = userLevelService.getNextLevel(userLevel.getLevel());
                if (nextLevel != null) {
                    levelInfo.put("currentStageExp", progressExp); // 本阶段已获得的经验值
                    levelInfo.put("expNeededForNextLevel", nextLevel.getMinExperience() - userLevel.getMinExperience()); // 本阶段总共需要的经验值
                } else {
                    // 已达最高等级
                    levelInfo.put("currentStageExp", progressExp);
                    levelInfo.put("expNeededForNextLevel", 0);
                }
            } else {
                levelInfo.put("progressPercent", 100.0); // 最高等级
                // 最高等级的情况
                levelInfo.put("currentStageExp", user.getExperience() - userLevel.getMinExperience());
                levelInfo.put("expNeededForNextLevel", 0); // 已达最高等级
            }
        } else {
            // 默认等级信息
            levelInfo.put("level", 1);
            levelInfo.put("levelName", "❓未知等级");
            levelInfo.put("levelIcon", "");
            levelInfo.put("currentLevelMinExp", 0);
            levelInfo.put("nextLevelRequiredExp", 100);
            levelInfo.put("progressPercent", 0.0);
            // 🎯 默认情况：从等级1升级到等级2总共需要的经验值
            levelInfo.put("currentStageExp", user.getExperience());
            levelInfo.put("expNeededForNextLevel", 100); // 等级1到等级2总共需要100经验值
        }
        
        return levelInfo;
    }
    
    @Override
    public void clearUserCache(Integer userId) {
        try {
            java.util.Set<String> keysToDelete = new java.util.HashSet<>();
            
            // 清除用户基本信息缓存
            String basicInfoKey = USER_BASIC_INFO_CACHE_PREFIX + userId;
            keysToDelete.add(basicInfoKey);
            
            // 清除用户主页缓存（所有分页）
            String profilePattern = USER_PROFILE_CACHE_PREFIX + userId + ":*";
            
            // 使用scan命令查找匹配的缓存key
            redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor = 
                     connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions().match(profilePattern).count(1000).build())) {
                    while (cursor.hasNext()) {
                        keysToDelete.add(new String(cursor.next()));
                    }
                }
                return null;
            }, true);
            
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.info("清除用户缓存成功，用户ID: {}, 清除key数量: {}", userId, keysToDelete.size());
            }
        } catch (Exception e) {
            log.warn("清除用户缓存失败，用户ID: {}", userId, e);
        }
    }
    
    /**
     * 🎯 查询关注状态（实时查询，不缓存）
     * @param currentUserId 当前用户ID
     * @param targetUserId 目标用户ID
     * @return 是否关注
     */
    private Boolean queryFollowingStatus(Integer currentUserId, Integer targetUserId) {
        if (currentUserId == null || currentUserId.equals(targetUserId)) {
            return false; // 未登录或查看自己
        }
        
        return userFollowMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.itheima.sbbs.entity.UserFollow>()
                .eq(com.itheima.sbbs.entity.UserFollow::getFollowerId, currentUserId)
                .eq(com.itheima.sbbs.entity.UserFollow::getFollowingId, targetUserId)
                .eq(com.itheima.sbbs.entity.UserFollow::getDeleted, 0)
        ) > 0;
    }
    
    /**
     * 🚀 优化：清除用户相关缓存（仅清理基本信息缓存）
     * @param userId 用户ID
     */
    private void clearUserRelatedCache(Integer userId) {
        try {
            java.util.Set<String> keysToDelete = new java.util.HashSet<>();
            
            // 🚀 只清除用户基本信息缓存
            String basicInfoKey = USER_BASIC_INFO_CACHE_PREFIX + userId;
            keysToDelete.add(basicInfoKey);
            
            // 清除旧版用户主页缓存（保留兼容性）
            String profilePattern = USER_PROFILE_CACHE_PREFIX + userId + ":*";
            
            // 使用scan命令查找匹配的旧版缓存key
            redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                // 扫描旧版缓存
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor = 
                     connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions().match(profilePattern).count(1000).build())) {
                    while (cursor.hasNext()) {
                        keysToDelete.add(new String(cursor.next()));
                    }
                }
                
                return null;
            }, true);
            
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.info("清除用户缓存成功，用户ID: {}, 清除key数量: {}", userId, keysToDelete.size());
            }
        } catch (Exception e) {
            log.warn("清除用户缓存失败，用户ID: {}", userId, e);
        }
    }
    
    @Override
    public UserBasicInfoDto getUserBasicInfo(Integer userId, Integer currentUserId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        
        // 🚀 优化：基本信息只缓存一份，不区分观察者
        String cacheKey = USER_BASIC_INFO_CACHE_PREFIX + userId;
        UserBasicInfoDto cachedInfo = (UserBasicInfoDto) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedInfo != null) {
            log.debug("从缓存获取用户基本信息，用户ID: {}", userId);
            // 🎯 关注状态实时查询（不缓存）
            cachedInfo.setIsFollowing(queryFollowingStatus(currentUserId, userId));
            return cachedInfo;
        }
        
        // 缓存未命中，从数据库查询
        UserBasicInfoDto basicInfo = new UserBasicInfoDto();
        
        // 1. 查询用户基本信息
        User user = getById(userId);
        if (user == null || user.getDeleted() == 1) {
            return null;
        }
        
        basicInfo.setId(user.getId());
        basicInfo.setUsername(user.getUsername());
        basicInfo.setAvatar(user.getAvatar());
        basicInfo.setCreated(user.getCreated() != null ? 
                             user.getCreated().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : 
                             null);
        basicInfo.setGroupId(user.getGroupId());
        
        // 设置等级信息
        if (user.getExperience() == null) {
            user.setExperience(0);
        }
        basicInfo.setExperience(user.getExperience());
        
        // 通过UserLevelService获取等级信息
        UserLevel userLevel = userLevelService.getLevelByExperience(user.getExperience());
        if (userLevel != null) {
            basicInfo.setLevel(userLevel.getLevel());
            basicInfo.setLevelName(userLevel.getName());
            basicInfo.setLevelIcon("");
            basicInfo.setCurrentLevelMinExp(userLevel.getMinExperience());
            basicInfo.setNextLevelRequiredExp(userLevel.getMaxExperience());
            
            if (userLevel.getMaxExperience() != null) {
                int progressExp = user.getExperience() - userLevel.getMinExperience();
                int totalExpForLevel = userLevel.getMaxExperience() - userLevel.getMinExperience();
                double progressPercent = (double) progressExp / totalExpForLevel * 100;
                basicInfo.setProgressPercent(Math.min(100.0, Math.max(0.0, progressPercent)));
                
                // 🎯 计算本阶段总共需要多少经验值（下一级最低 - 当前级最低）
                UserLevel nextLevel = userLevelService.getNextLevel(userLevel.getLevel());
                if (nextLevel != null) {
                    basicInfo.setCurrentStageExp(progressExp); // 本阶段已获得的经验值
                    basicInfo.setExpNeededForNextLevel(nextLevel.getMinExperience() - userLevel.getMinExperience()); // 本阶段总共需要的经验值
                } else {
                    // 已达最高等级
                    basicInfo.setCurrentStageExp(progressExp);
                    basicInfo.setExpNeededForNextLevel(0);
                }
            } else {
                basicInfo.setProgressPercent(100.0);
                // 最高等级的情况
                basicInfo.setCurrentStageExp(user.getExperience() - userLevel.getMinExperience());
                basicInfo.setExpNeededForNextLevel(0); // 已达最高等级
            }
        } else {
            basicInfo.setLevel(1);
            basicInfo.setLevelName("❓未知等级");
            basicInfo.setLevelIcon("");
            basicInfo.setCurrentLevelMinExp(0);
            basicInfo.setNextLevelRequiredExp(100);
            basicInfo.setProgressPercent(0.0);
            // 默认情况：从等级1升级到等级2总共需要的经验值
            basicInfo.setCurrentStageExp(user.getExperience());
            basicInfo.setExpNeededForNextLevel(100); // 等级1到等级2总共需要100经验值
        }
        
        // 2. 查询用户统计数据
        java.util.Map<String, Object> userStats = userMapper.getUserStats(userId);
        basicInfo.setPostCount(((Number) userStats.get("postcount")).intValue());
        basicInfo.setCommentCount(((Number) userStats.get("commentcount")).intValue());
        basicInfo.setFollowerCount(((Number) userStats.get("followercount")).intValue());
        basicInfo.setFollowingCount(((Number) userStats.get("followingcount")).intValue());
        
        // 🎯 查询关注状态（实时查询，不缓存）
        basicInfo.setIsFollowing(queryFollowingStatus(currentUserId, userId));
        
        // 🚀 优化：缓存结果，设置过期时间为2分钟（用户基础信息可以短时缓存）
        redisTemplate.opsForValue().set(cacheKey, basicInfo, 2, TimeUnit.MINUTES);
        
        return basicInfo;
    }
    
    @Override
    public List<PostWithUserDto> getUserPosts(Integer userId, Integer pageNo, Integer pageSize) {
        if (userId == null || userId <= 0) {
            return new java.util.ArrayList<>();
        }
        
        if (pageNo == null || pageNo < 1) {
            pageNo = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 15;
        }
        
        // 🚀 移除缓存：用户发帖记录需要实时显示，不进行缓存
        // 直接从数据库查询最新数据
        int offset = (pageNo - 1) * pageSize;
        List<PostWithUserDto> posts = postMapper.getUserPostsWithTags(userId, offset, pageSize);
        
        // 处理帖子标签信息
        if (posts != null && !posts.isEmpty()) {
            processPostTagsOptimized(posts);
        }
        
        return posts != null ? posts : new java.util.ArrayList<>();
    }

    @Override
    public void addExperience(Integer userId, Integer experience) {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, userId)
                    .eq(User::getDeleted, 0)
                    .setSql("experience = COALESCE(experience, 0) + " + experience);
        update(updateWrapper);
        
        // 清除用户缓存
        clearUserCache(userId);
        
        log.info("用户 {} 增加 {} 经验值", userId, experience);
    }
}
