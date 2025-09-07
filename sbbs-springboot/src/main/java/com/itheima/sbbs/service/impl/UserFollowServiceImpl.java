package com.itheima.sbbs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.entity.UserFollow;
import com.itheima.sbbs.entity.UserFollowDto;
import com.itheima.sbbs.entity.UserFollowDetailDto;
import com.itheima.sbbs.mapper.UserFollowMapper;
import com.itheima.sbbs.mapper.UserMapper;
import com.itheima.sbbs.service.NotificationCacheService;
import com.itheima.sbbs.service.NotificationService;
import com.itheima.sbbs.service.UserFollowService;
import com.itheima.sbbs.utils.SMSUtils;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserFollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow> implements UserFollowService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private SMSUtils smsUtils;
    
    @Autowired
    private NotificationCacheService notificationCacheService;
    
    @Autowired
    private com.itheima.sbbs.service.UserService userService;

    private static final String FOLLOWING_LIST_CACHE_PREFIX = "followingList:";
    private static final String FOLLOWER_LIST_CACHE_PREFIX = "followerList:";
    private static final String FOLLOWER_PAGE_CACHE_PREFIX = "followerPage:"; // 分页粉丝列表缓存前缀
    private static final String FOLLOWING_PAGE_CACHE_PREFIX = "followingPage:"; // 分页关注列表缓存前缀
    private static final String FOLLOWER_PAGE_DETAIL_CACHE_PREFIX = "followerPageDetail:"; // 分页粉丝详细列表缓存前缀
    private static final String FOLLOWING_PAGE_DETAIL_CACHE_PREFIX = "followingPageDetail:"; // 分页关注详细列表缓存前缀
    private static final int DEFAULT_PAGE_SIZE = 15; // 默认页面大小

    @Override
    public boolean followUser(Integer followerId, Integer followingId) {
        // 避免自己关注自己
        if (followerId.equals(followingId)) {
            return false;
        }
        
        // 🚀 优化：一次查询检查所有关注状态（包括已删除和未删除的记录）
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
               .eq(UserFollow::getFollowingId, followingId); // 不限制deleted状态，查询所有记录
        List<UserFollow> allRecords = this.baseMapper.selectList(wrapper);
        
        UserFollow existingRecord = null;
        UserFollow deletedRecord = null;
        
        // 分类现有记录
        for (UserFollow record : allRecords) {
            if (record.getDeleted() == 0) {
                existingRecord = record; // 未删除的关注记录
            } else if (record.getDeleted() == 1) {
                deletedRecord = record; // 已删除的关注记录
            }
        }
        
        // 如果已经关注且未删除，直接返回
        if (existingRecord != null) {
            return false;
        }

        if (deletedRecord != null) {
            // 找到了已逻辑删除的记录，恢复它
            deletedRecord.setDeleted(0);
            // 可能需要手动设置更新时间，如果BasePojo中updated字段没有自动填充更新时间的话
            // deletedRecord.setUpdated(LocalDateTime.now()); 
            this.baseMapper.updateById(deletedRecord);
            
            // 🚀 异步创建关注通知（避免阻塞主流程）
            createFollowNotificationAsync(followerId, followingId);
            
            // 清除缓存
            clearFollowCache(followerId, followingId);
            
            // 🚀 清除两个用户的缓存（关注数和粉丝数统计已改变）
            userService.clearUserCache(followerId); // 关注者的关注数+1
            userService.clearUserCache(followingId); // 被关注者的粉丝数+1
            
            return true;
        } else {
            // 没有找到任何记录（包括已删除的），插入新关注记录
            UserFollow userFollow = new UserFollow();
            userFollow.setFollowerId(followerId);
            userFollow.setFollowingId(followingId);
            // deleted字段默认为0，不需要显式设置
            boolean saved = save(userFollow);

            if (saved) {
                // 🚀 异步创建关注通知（避免阻塞主流程）
                createFollowNotificationAsync(followerId, followingId);
                
                // 清除缓存
                clearFollowCache(followerId, followingId);
                
                // 🚀 清除两个用户的缓存（关注数和粉丝数统计已改变）
                userService.clearUserCache(followerId); // 关注者的关注数+1
                userService.clearUserCache(followingId); // 被关注者的粉丝数+1
            }

            return saved;
        }
    }

    @Override
    public boolean unfollowUser(Integer followerId, Integer followingId) {
        LambdaQueryWrapper<UserFollow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFollow::getFollowerId, followerId)
                    .eq(UserFollow::getFollowingId, followingId);
        // MyBatis-Plus的remove方法会自动处理逻辑删除（如果实体类中配置了@TableLogic）
        boolean removed = remove(queryWrapper);
        if (removed) {
             // 清除缓存
             clearFollowCache(followerId, followingId);
             
             // 🚀 清除两个用户的缓存（关注数和粉丝数统计已改变）
             userService.clearUserCache(followerId); // 关注者的关注数-1
             userService.clearUserCache(followingId); // 被关注者的粉丝数-1
        }
        return removed;
    }

    @Override
    public boolean isFollowing(Integer followerId, Integer followingId) {
        LambdaQueryWrapper<UserFollow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFollow::getFollowerId, followerId)
                    .eq(UserFollow::getFollowingId, followingId);
        // MyBatis-Plus的count方法会自动处理逻辑删除（只查deleted=0的）
        return count(queryWrapper) > 0;
    }

    @Override
    public int getFollowingCount(Integer userId) {
        LambdaQueryWrapper<UserFollow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFollow::getFollowerId, userId);
        // MyBatis-Plus的count方法会自动处理逻辑删除
        return count(queryWrapper);
    }

    @Override
    public int getFollowerCount(Integer userId) {
        LambdaQueryWrapper<UserFollow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFollow::getFollowingId, userId);
        // MyBatis-Plus的count方法会自动处理逻辑删除
        return count(queryWrapper);
    }

    @Override
    public List<UserFollowDto> getFollowingList(Integer userId) {
        String cacheKey = FOLLOWING_LIST_CACHE_PREFIX + userId;
        List<UserFollowDto> cachedList = (List<UserFollowDto>) redisTemplate.opsForValue().get(cacheKey);

        if (cachedList != null) {
            return cachedList;
        }

        LambdaQueryWrapper<UserFollow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFollow::getFollowerId, userId);
        List<UserFollow> followingList = list(queryWrapper);

        List<Integer> followingUserIds = followingList.stream()
                .map(UserFollow::getFollowingId)
                .collect(Collectors.toList());

        if (followingUserIds.isEmpty()) {
            // 缓存空列表，避免缓存穿透
            redisTemplate.opsForValue().set(cacheKey, java.util.Collections.emptyList(), 1, TimeUnit.HOURS);
            return java.util.Collections.emptyList();
        }

        LambdaQueryWrapper<com.itheima.sbbs.entity.User> userQueryWrapper = new LambdaQueryWrapper<>();
        userQueryWrapper.in(com.itheima.sbbs.entity.User::getId, followingUserIds);
        List<com.itheima.sbbs.entity.User> users = userMapper.selectList(userQueryWrapper);

        List<UserFollowDto> resultList = users.stream()
                .map(user -> new UserFollowDto(user.getId(), user.getUsername(), user.getAvatar()))
                .collect(Collectors.toList());

        // 缓存结果，设置过期时间（例如1小时）
        redisTemplate.opsForValue().set(cacheKey, resultList, 1, TimeUnit.HOURS);

        return resultList;
    }

    @Override
    public List<UserFollowDto> getFollowerList(Integer userId) {
        String cacheKey = FOLLOWER_LIST_CACHE_PREFIX + userId;
        List<UserFollowDto> cachedList = (List<UserFollowDto>) redisTemplate.opsForValue().get(cacheKey);

        if (cachedList != null) {
            return cachedList;
        }

        LambdaQueryWrapper<UserFollow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFollow::getFollowingId, userId);
        List<UserFollow> followerList = list(queryWrapper);

        List<Integer> followerUserIds = followerList.stream()
                .map(UserFollow::getFollowerId)
                .collect(Collectors.toList());

        if (followerUserIds.isEmpty()) {
            // 缓存空列表，避免缓存穿透
            redisTemplate.opsForValue().set(cacheKey, java.util.Collections.emptyList(), 1, TimeUnit.HOURS);
            return java.util.Collections.emptyList();
        }

        LambdaQueryWrapper<com.itheima.sbbs.entity.User> userQueryWrapper = new LambdaQueryWrapper<>();
        userQueryWrapper.in(com.itheima.sbbs.entity.User::getId, followerUserIds);
        List<com.itheima.sbbs.entity.User> users = userMapper.selectList(userQueryWrapper);

        List<UserFollowDto> resultList = users.stream()
                .map(user -> new UserFollowDto(user.getId(), user.getUsername(), user.getAvatar()))
                .collect(Collectors.toList());

        // 缓存结果，设置过期时间（例如1小时）
        redisTemplate.opsForValue().set(cacheKey, resultList, 1, TimeUnit.HOURS);

        return resultList;
    }

    /**
     * 分页获取用户的粉丝列表
     *
     * @param userId 用户ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小，默认15
     * @return 粉丝列表
     */
    @Override
    public List<UserFollowDto> getFollowerListByPage(Integer userId, Integer pageNum, Integer pageSize) {
        // 参数校验
        if (userId == null || userId <= 0) {
            throw new RuntimeException("无效的用户ID");
        }
        
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        
        if (pageSize == null || pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        
        // 缓存键
        String cacheKey = FOLLOWER_PAGE_CACHE_PREFIX + userId + ":" + pageNum + ":" + pageSize;
        
        // 尝试从缓存中获取
        List<UserFollowDto> cachedFollowers = (List<UserFollowDto>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedFollowers != null) {
            log.info("从缓存中获取用户粉丝列表, userId={}, pageNum={}, pageSize={}", userId, pageNum, pageSize);
            return cachedFollowers;
        }
        
        // 缓存未命中，从数据库查询
        log.info("缓存未命中，从数据库查询用户粉丝列表, userId={}, pageNum={}, pageSize={}", userId, pageNum, pageSize);
        
        // 构建查询条件，按关注时间倒序（最新关注的在前）
        Page<UserFollow> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<UserFollow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFollow::getFollowingId, userId)
                   .eq(UserFollow::getDeleted, 0) // 非删除状态
                   .orderByDesc(UserFollow::getUpdated); // 按更新时间倒序排序
        
        // 执行分页查询
        IPage<UserFollow> followPage = this.page(page, queryWrapper);
        
        // 转换为UserFollowDto列表
        List<UserFollowDto> resultList = java.util.Collections.emptyList();
        
        if (followPage.getRecords() != null && !followPage.getRecords().isEmpty()) {
            // 收集所有粉丝的用户ID
            List<Integer> followerUserIds = followPage.getRecords().stream()
                    .map(UserFollow::getFollowerId)
                    .collect(Collectors.toList());
            
            // 批量查询用户信息
            LambdaQueryWrapper<com.itheima.sbbs.entity.User> userQueryWrapper = new LambdaQueryWrapper<>();
            userQueryWrapper.select(com.itheima.sbbs.entity.User::getId, 
                                   com.itheima.sbbs.entity.User::getUsername, 
                                   com.itheima.sbbs.entity.User::getAvatar,
                                   com.itheima.sbbs.entity.User::getExperience,
                                   com.itheima.sbbs.entity.User::getGroupId)
                           .in(com.itheima.sbbs.entity.User::getId, followerUserIds)
                           .eq(com.itheima.sbbs.entity.User::getDeleted, 0);
            List<com.itheima.sbbs.entity.User> users = userMapper.selectList(userQueryWrapper);
            
            // 批量查询用户统计信息
            Map<Integer, Map<String, Integer>> userStatsMap = batchGetUserStats(followerUserIds);
            
            // 转换为详细DTO
            List<UserFollowDetailDto> detailList = followPage.getRecords().stream()
                    .map(follow -> {
                        com.itheima.sbbs.entity.User user = users.stream()
                                .filter(u -> u.getId().equals(follow.getFollowerId()))
                                .findFirst()
                                .orElse(null);
                        
                        if (user == null) return null;
                        
                        UserFollowDetailDto detail = new UserFollowDetailDto();
                        detail.setId(user.getId());
                        detail.setUsername(user.getUsername());
                        detail.setAvatar(user.getAvatar());
                        detail.setFollowTime(follow.getUpdated());
                        detail.setExperience(user.getExperience());
                        detail.setGroupId(user.getGroupId());
                        
                        Map<String, Integer> stats = userStatsMap.get(user.getId());
                        if (stats != null) {
                            detail.setFollowerCount(stats.get("followerCount"));
                            detail.setFollowingCount(stats.get("followingCount"));
                        }
                        
                        return detail;
                    })
                    .filter(detail -> detail != null)
                    .collect(Collectors.toList());
            
            // 转换为UserFollowDto（为了兼容现有接口）
            resultList = users.stream()
                    .map(user -> new UserFollowDto(user.getId(), user.getUsername(), user.getAvatar()))
                    .collect(Collectors.toList());
        }
        
        // 缓存结果，设置20分钟过期
        redisTemplate.opsForValue().set(cacheKey, resultList, 20, TimeUnit.MINUTES);
        
        return resultList;
    }
    
    /**
     * 清除用户粉丝列表缓存
     *
     * @param userId 用户ID
     */
    @Override
    public void clearFollowerCache(Integer userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        
        log.info("清除用户粉丝列表缓存, userId={}", userId);
        
        try {
            // 删除原有的粉丝列表缓存
            redisTemplate.delete(FOLLOWER_LIST_CACHE_PREFIX + userId);
            
            // 查找并删除所有分页缓存
            String pattern1 = FOLLOWER_PAGE_CACHE_PREFIX + userId + ":*";
            String pattern2 = FOLLOWER_PAGE_DETAIL_CACHE_PREFIX + userId + ":*";
            
            Set<String> keys = new HashSet<>();
            keys.addAll(scanKeys(pattern1));
            keys.addAll(scanKeys(pattern2));
            
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已删除用户粉丝分页缓存键, count={}, keys={}", keys.size(), keys);
            }
        } catch (Exception e) {
            log.error("清除用户粉丝缓存失败, userId={}", userId, e);
        }
    }
    
    // 添加scanKeys方法（如果不存在）
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try {
            redisTemplate.execute((RedisConnection connection) -> {
                try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next()));
                    }
                } catch (Exception e) {
                    log.error("Redis scan keys error", e);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Redis scan keys execution error", e);
        }
        return keys;
    }

    /**
     * 分页获取用户的关注列表
     *
     * @param userId 用户ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小，默认15
     * @return 关注的用户列表
     */
    @Override
    public List<UserFollowDto> getFollowingListByPage(Integer userId, Integer pageNum, Integer pageSize) {
        // 参数校验
        if (userId == null || userId <= 0) {
            throw new RuntimeException("无效的用户ID");
        }
        
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        
        if (pageSize == null || pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        
        // 缓存键
        String cacheKey = FOLLOWING_PAGE_CACHE_PREFIX + userId + ":" + pageNum + ":" + pageSize;
        
        // 尝试从缓存中获取
        List<UserFollowDto> cachedFollowing = (List<UserFollowDto>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedFollowing != null) {
            log.info("从缓存中获取用户关注列表, userId={}, pageNum={}, pageSize={}", userId, pageNum, pageSize);
            return cachedFollowing;
        }
        
        // 缓存未命中，从数据库查询
        log.info("缓存未命中，从数据库查询用户关注列表, userId={}, pageNum={}, pageSize={}", userId, pageNum, pageSize);
        
        // 构建查询条件，按关注时间倒序（最新关注的在前）
        Page<UserFollow> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<UserFollow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFollow::getFollowerId, userId)
                   .eq(UserFollow::getDeleted, 0) // 非删除状态
                   .orderByDesc(UserFollow::getUpdated); // 按更新时间倒序排序
        
        // 执行分页查询
        IPage<UserFollow> followPage = this.page(page, queryWrapper);
        
        // 转换为UserFollowDto列表
        List<UserFollowDto> resultList = java.util.Collections.emptyList();
        
        if (followPage.getRecords() != null && !followPage.getRecords().isEmpty()) {
            // 收集所有关注用户的ID
            List<Integer> followingUserIds = followPage.getRecords().stream()
                    .map(UserFollow::getFollowingId)
                    .collect(Collectors.toList());
            
            // 批量查询用户信息
            LambdaQueryWrapper<com.itheima.sbbs.entity.User> userQueryWrapper = new LambdaQueryWrapper<>();
            userQueryWrapper.select(com.itheima.sbbs.entity.User::getId, 
                                   com.itheima.sbbs.entity.User::getUsername, 
                                   com.itheima.sbbs.entity.User::getAvatar,
                                   com.itheima.sbbs.entity.User::getExperience,
                                   com.itheima.sbbs.entity.User::getGroupId)
                           .in(com.itheima.sbbs.entity.User::getId, followingUserIds)
                           .eq(com.itheima.sbbs.entity.User::getDeleted, 0);
            List<com.itheima.sbbs.entity.User> users = userMapper.selectList(userQueryWrapper);
            
            // 转换为DTO
            resultList = users.stream()
                    .map(user -> new UserFollowDto(user.getId(), user.getUsername(), user.getAvatar()))
                    .collect(Collectors.toList());
        }
        
        // 缓存结果，设置20分钟过期
        redisTemplate.opsForValue().set(cacheKey, resultList, 20, TimeUnit.MINUTES);
        
        return resultList;
    }
    
    /**
     * 清除用户关注列表缓存
     *
     * @param userId 用户ID
     */
    @Override
    public void clearFollowingCache(Integer userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        
        log.info("清除用户关注列表缓存, userId={}", userId);
        
        try {
            // 删除原有的关注列表缓存
            redisTemplate.delete(FOLLOWING_LIST_CACHE_PREFIX + userId);
            
            // 查找并删除所有分页缓存
            String pattern1 = FOLLOWING_PAGE_CACHE_PREFIX + userId + ":*";
            String pattern2 = FOLLOWING_PAGE_DETAIL_CACHE_PREFIX + userId + ":*";
            
            Set<String> keys = new HashSet<>();
            keys.addAll(scanKeys(pattern1));
            keys.addAll(scanKeys(pattern2));
            
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已删除用户关注分页缓存键, count={}, keys={}", keys.size(), keys);
            }
        } catch (Exception e) {
            log.error("清除用户关注缓存失败, userId={}", userId, e);
        }
    }

    private void clearFollowCache(Integer followerId, Integer followingId) {
        // 清除关注者的关注列表缓存（包括分页缓存）
        clearFollowingCache(followerId);
        
        // 清除被关注者的粉丝列表缓存（包括分页缓存）
        clearFollowerCache(followingId);
    }
    
    /**
     * 🚀 异步创建关注通知（提升性能）
     * @param followerId 关注者ID
     * @param followingId 被关注者ID
     */
    private void createFollowNotificationAsync(Integer followerId, Integer followingId) {
        CompletableFuture.runAsync(() -> {
            createFollowNotification(followerId, followingId);
        });
    }
    
    /**
     * 创建关注通知
     * @param followerId 关注者ID
     * @param followingId 被关注者ID
     */
    private void createFollowNotification(Integer followerId, Integer followingId) {
        try {
            // 🚀 优化：批量查询两个用户信息
            List<Integer> userIds = java.util.Arrays.asList(followerId, followingId);
            LambdaQueryWrapper<com.itheima.sbbs.entity.User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.in(com.itheima.sbbs.entity.User::getId, userIds)
                      .eq(com.itheima.sbbs.entity.User::getDeleted, 0);
            List<com.itheima.sbbs.entity.User> users = userMapper.selectList(userWrapper);
            
            if (users.size() != 2) {
                log.warn("用户信息不完整，查询到{}个用户，关注者ID: {}, 被关注者ID: {}", users.size(), followerId, followingId);
                return;
            }
            
            // 分离关注者和被关注者
            com.itheima.sbbs.entity.User follower = null;
            com.itheima.sbbs.entity.User following = null;
            for (com.itheima.sbbs.entity.User user : users) {
                if (user.getId().equals(followerId)) {
                    follower = user;
                } else if (user.getId().equals(followingId)) {
                    following = user;
                }
            }
            
            if (follower == null || following == null) {
                log.warn("用户信息匹配失败，关注者ID: {}, 被关注者ID: {}", followerId, followingId);
                return;
            }
            
            // 2. 创建数据库通知
            Notification notification = new Notification();
            notification.setReceiverId(followingId); // 被关注的人收到通知
            notification.setSenderId(followerId); // 关注的人发送通知
            notification.setNotificationType(10); // 类型10：用户关注
            notification.setRelatedId(followingId); // relatedId设置为被关注者的用户ID
            notification.setRelatedType("3"); // 3表示用户，区别于帖子(1)和评论(2)
            notification.setTriggerEntityId(followerId); // 触发实体ID设置为关注者ID
            notification.setTriggerEntityType(4); // 4表示关注操作
            notification.setRead(false);
            
            notificationService.save(notification);
            log.info("创建关注通知成功，关注者ID: {}, 被关注者ID: {}", followerId, followingId);
            
            // 清除通知缓存
            notificationCacheService.clearNotificationListCache(followingId);
            
            // 3. 发送邮件通知（如果被关注者有邮箱且开启了其他通知）
            if (following.getEmail() != null && !following.getEmail().trim().isEmpty() && Boolean.TRUE.equals(following.getEnableOtherNotification())) {
                smsUtils.sendFollowNotification(following.getEmail(), follower.getUsername());
                log.info("发送关注邮件通知成功，关注者: {}, 被关注者邮箱: {}", follower.getUsername(), following.getEmail());
            } else {
                log.info("被关注者没有邮箱或已关闭其他邮件通知，跳过邮件发送，被关注者ID: {}", followingId);
            }
            
        } catch (Exception e) {
            log.error("创建关注通知失败，关注者ID: {}, 被关注者ID: {}", followerId, followingId, e);
            // 通知创建失败不应该影响关注操作，所以只记录日志
        }
    }

    /**
     * 分页获取用户的粉丝列表（返回分页信息）
     */
    @Override
    public IPage<UserFollowDetailDto> getFollowerPageResult(Integer userId, Integer pageNum, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new RuntimeException("无效的用户ID");
        }
        
        if (pageNum == null || pageNum < 1) pageNum = 1;
        if (pageSize == null || pageSize < 1) pageSize = DEFAULT_PAGE_SIZE;
        
        String cacheKey = FOLLOWER_PAGE_DETAIL_CACHE_PREFIX + userId + ":" + pageNum + ":" + pageSize;
        IPage<UserFollowDetailDto> cachedPage = (IPage<UserFollowDetailDto>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedPage != null) {
            log.info("从缓存中获取用户粉丝详细分页, userId={}", userId);
            return cachedPage;
        }
        
        // 分页查询关注记录
        Page<UserFollow> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowingId, userId)
               .eq(UserFollow::getDeleted, 0)
               .orderByDesc(UserFollow::getUpdated);
        
        IPage<UserFollow> followPage = this.page(page, wrapper);
        
        // 创建结果分页对象
        IPage<UserFollowDetailDto> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setTotal(followPage.getTotal());
        resultPage.setPages(followPage.getPages());
        resultPage.setCurrent(followPage.getCurrent());
        resultPage.setSize(followPage.getSize());
        
        if (followPage.getRecords() != null && !followPage.getRecords().isEmpty()) {
            List<Integer> userIds = followPage.getRecords().stream()
                    .map(UserFollow::getFollowerId)
                    .collect(Collectors.toList());
            
            // 批量查询用户信息
            LambdaQueryWrapper<com.itheima.sbbs.entity.User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.select(com.itheima.sbbs.entity.User::getId, 
                              com.itheima.sbbs.entity.User::getUsername, 
                              com.itheima.sbbs.entity.User::getAvatar,
                              com.itheima.sbbs.entity.User::getExperience,
                              com.itheima.sbbs.entity.User::getGroupId)
                      .in(com.itheima.sbbs.entity.User::getId, userIds)
                      .eq(com.itheima.sbbs.entity.User::getDeleted, 0);
            List<com.itheima.sbbs.entity.User> users = userMapper.selectList(userWrapper);
            
            // 批量查询统计信息
            Map<Integer, Map<String, Integer>> statsMap = batchGetUserStats(userIds);
            
            // 转换为DTO
            List<UserFollowDetailDto> detailList = followPage.getRecords().stream()
                    .map(follow -> {
                        com.itheima.sbbs.entity.User user = users.stream()
                                .filter(u -> u.getId().equals(follow.getFollowerId()))
                                .findFirst()
                                .orElse(null);
                        if (user == null) return null;
                        
                        UserFollowDetailDto detail = new UserFollowDetailDto();
                        detail.setId(user.getId());
                        detail.setUsername(user.getUsername());
                        detail.setAvatar(user.getAvatar());
                        detail.setFollowTime(follow.getUpdated());
                        detail.setExperience(user.getExperience());
                        detail.setGroupId(user.getGroupId());
                        
                        Map<String, Integer> stats = statsMap.get(user.getId());
                        if (stats != null) {
                            detail.setFollowerCount(stats.get("followerCount"));
                            detail.setFollowingCount(stats.get("followingCount"));
                        }
                        return detail;
                    })
                    .filter(detail -> detail != null)
                    .collect(Collectors.toList());
            
            resultPage.setRecords(detailList);
        } else {
            resultPage.setRecords(java.util.Collections.emptyList());
        }
        
        redisTemplate.opsForValue().set(cacheKey, resultPage, 15, TimeUnit.MINUTES);
        return resultPage;
    }
    
    /**
     * 分页获取用户的关注列表（返回分页信息）
     */
    @Override
    public IPage<UserFollowDetailDto> getFollowingPageResult(Integer userId, Integer pageNum, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new RuntimeException("无效的用户ID");
        }
        
        if (pageNum == null || pageNum < 1) pageNum = 1;
        if (pageSize == null || pageSize < 1) pageSize = DEFAULT_PAGE_SIZE;
        
        String cacheKey = FOLLOWING_PAGE_DETAIL_CACHE_PREFIX + userId + ":" + pageNum + ":" + pageSize;
        IPage<UserFollowDetailDto> cachedPage = (IPage<UserFollowDetailDto>) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedPage != null) {
            log.info("从缓存中获取用户关注详细分页, userId={}", userId);
            return cachedPage;
        }
        
        // 分页查询关注记录
        Page<UserFollow> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
               .eq(UserFollow::getDeleted, 0)
               .orderByDesc(UserFollow::getUpdated);
        
        IPage<UserFollow> followPage = this.page(page, wrapper);
        
        // 创建结果分页对象
        IPage<UserFollowDetailDto> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setTotal(followPage.getTotal());
        resultPage.setPages(followPage.getPages());
        resultPage.setCurrent(followPage.getCurrent());
        resultPage.setSize(followPage.getSize());
        
        if (followPage.getRecords() != null && !followPage.getRecords().isEmpty()) {
            List<Integer> userIds = followPage.getRecords().stream()
                    .map(UserFollow::getFollowingId)
                    .collect(Collectors.toList());
            
            // 批量查询用户信息
            LambdaQueryWrapper<com.itheima.sbbs.entity.User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.select(com.itheima.sbbs.entity.User::getId, 
                              com.itheima.sbbs.entity.User::getUsername, 
                              com.itheima.sbbs.entity.User::getAvatar,
                              com.itheima.sbbs.entity.User::getExperience,
                              com.itheima.sbbs.entity.User::getGroupId)
                      .in(com.itheima.sbbs.entity.User::getId, userIds)
                      .eq(com.itheima.sbbs.entity.User::getDeleted, 0);
            List<com.itheima.sbbs.entity.User> users = userMapper.selectList(userWrapper);
            
            // 批量查询统计信息
            Map<Integer, Map<String, Integer>> statsMap = batchGetUserStats(userIds);
            
            // 转换为DTO
            List<UserFollowDetailDto> detailList = followPage.getRecords().stream()
                    .map(follow -> {
                        com.itheima.sbbs.entity.User user = users.stream()
                                .filter(u -> u.getId().equals(follow.getFollowingId()))
                                .findFirst()
                                .orElse(null);
                        if (user == null) return null;
                        
                        UserFollowDetailDto detail = new UserFollowDetailDto();
                        detail.setId(user.getId());
                        detail.setUsername(user.getUsername());
                        detail.setAvatar(user.getAvatar());
                        detail.setFollowTime(follow.getUpdated());
                        detail.setExperience(user.getExperience());
                        detail.setGroupId(user.getGroupId());
                        
                        Map<String, Integer> stats = statsMap.get(user.getId());
                        if (stats != null) {
                            detail.setFollowerCount(stats.get("followerCount"));
                            detail.setFollowingCount(stats.get("followingCount"));
                        }
                        return detail;
                    })
                    .filter(detail -> detail != null)
                    .collect(Collectors.toList());
            
            resultPage.setRecords(detailList);
        } else {
            resultPage.setRecords(java.util.Collections.emptyList());
        }
        
        redisTemplate.opsForValue().set(cacheKey, resultPage, 15, TimeUnit.MINUTES);
        return resultPage;
    }
    
    /**
     * 批量获取用户统计信息
     * @param userIds 用户ID列表
     * @return 用户统计信息Map
     */
    private Map<Integer, Map<String, Integer>> batchGetUserStats(List<Integer> userIds) {
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<Integer, Map<String, Integer>> statsMap = new HashMap<>();
        
        // 批量查询粉丝数
        LambdaQueryWrapper<UserFollow> followerWrapper = new LambdaQueryWrapper<>();
        followerWrapper.select(UserFollow::getFollowingId)
                       .in(UserFollow::getFollowingId, userIds)
                       .eq(UserFollow::getDeleted, 0);
        List<UserFollow> followers = this.list(followerWrapper);
        
        Map<Integer, Long> followerCountMap = followers.stream()
                .collect(Collectors.groupingBy(UserFollow::getFollowingId, Collectors.counting()));
        
        // 批量查询关注数
        LambdaQueryWrapper<UserFollow> followingWrapper = new LambdaQueryWrapper<>();
        followingWrapper.select(UserFollow::getFollowerId)
                        .in(UserFollow::getFollowerId, userIds)
                        .eq(UserFollow::getDeleted, 0);
        List<UserFollow> followings = this.list(followingWrapper);
        
        Map<Integer, Long> followingCountMap = followings.stream()
                .collect(Collectors.groupingBy(UserFollow::getFollowerId, Collectors.counting()));
        
        // 组装结果
        for (Integer userId : userIds) {
            Map<String, Integer> stats = new HashMap<>();
            stats.put("followerCount", followerCountMap.getOrDefault(userId, 0L).intValue());
            stats.put("followingCount", followingCountMap.getOrDefault(userId, 0L).intValue());
            statsMap.put(userId, stats);
        }
        
        return statsMap;
    }
} 