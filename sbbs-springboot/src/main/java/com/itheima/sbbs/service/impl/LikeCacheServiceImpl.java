package com.itheima.sbbs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itheima.sbbs.entity.Like;
import com.itheima.sbbs.mapper.LikeMapper;
import com.itheima.sbbs.service.LikeCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;

/**
 * 点赞缓存服务实现
 */
@Service
@Slf4j
public class LikeCacheServiceImpl implements LikeCacheService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private LikeMapper likeMapper;
    
    // Redis键前缀
    private static final String POST_LIKES_PREFIX = "post:likes:";
    private static final String POST_DISLIKES_PREFIX = "post:dislikes:";
    private static final String COMMENT_LIKES_PREFIX = "comment:likes:";
    private static final String COMMENT_DISLIKES_PREFIX = "comment:dislikes:";
    
    // 缓存过期时间（24小时）
    private static final long CACHE_EXPIRE_HOURS = 24;
    
    // 同步锁映射，防止重复同步
    private static final ConcurrentHashMap<String, Object> syncLocks = new ConcurrentHashMap<>();

    /**
     * 构造函数，清理可能存在的旧格式缓存键
     */
    public LikeCacheServiceImpl() {
        // 可以在这里添加清理旧缓存的逻辑，或者通过管理接口手动清理
    }

    /**
     * 清理旧格式的缓存键（postlikes:*, postdislikes:*等）
     * 这个方法可以手动调用或者定时清理
     */
    public void cleanupOldCacheKeys() {
        try {
            // 清理可能存在的旧格式缓存键
            // 注意：这些是可能的旧格式，需要根据实际情况调整
            String[] oldPatterns = {
                "postlikes:*",
                "postdislikes:*", 
                "commentlikes:*",
                "commentdislikes:*"
            };
            
            for (String pattern : oldPatterns) {
                try {
                    Set<String> keys = redisTemplate.keys(pattern);
                    if (keys != null && !keys.isEmpty()) {
                        log.info("发现旧格式缓存键 {}，数量: {}", pattern, keys.size());
                        // 可以选择删除这些键
                        // redisTemplate.delete(keys);
                        // log.info("已清理旧格式缓存键: {}", pattern);
                    }
                } catch (Exception e) {
                    log.warn("清理旧缓存键失败: {}", pattern, e);
                }
            }
        } catch (Exception e) {
            log.error("清理旧缓存键时发生错误", e);
        }
    }

    @Override
    public void addPostLike(Integer userId, Integer postId) {
        String key = POST_LIKES_PREFIX + postId;
        log.info("执行Redis缓存操作：添加点赞，键: {}, 用户ID: {}", key, userId);
        
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        redisTemplate.expire(key, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        
        log.info("✅ Redis缓存操作完成：点赞已添加，键: {}, 用户ID: {}, 操作结果: {}", 
                key, userId, result > 0 ? "新增" : "已存在");
    }
    
    @Override
    public void removePostLike(Integer userId, Integer postId) {
        String key = POST_LIKES_PREFIX + postId;
        log.info("执行Redis缓存操作：移除点赞，键: {}, 用户ID: {}", key, userId);
        
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        
        log.info("✅ Redis缓存操作完成：点赞已移除，键: {}, 用户ID: {}, 操作结果: {}", 
                key, userId, result > 0 ? "成功移除" : "不存在");
    }
    
    @Override
    public void addPostDislike(Integer userId, Integer postId) {
        String key = POST_DISLIKES_PREFIX + postId;
        redisTemplate.opsForSet().add(key, userId.toString());
        redisTemplate.expire(key, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        log.debug("添加帖子点踩到缓存，用户ID: {}, 帖子ID: {}", userId, postId);
    }
    
    @Override
    public void removePostDislike(Integer userId, Integer postId) {
        String key = POST_DISLIKES_PREFIX + postId;
        redisTemplate.opsForSet().remove(key, userId.toString());
        log.debug("移除帖子点踩从缓存，用户ID: {}, 帖子ID: {}", userId, postId);
    }
    
    @Override
    public void addCommentLike(Integer userId, Integer commentId) {
        String key = COMMENT_LIKES_PREFIX + commentId;
        redisTemplate.opsForSet().add(key, userId.toString());
        redisTemplate.expire(key, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        log.debug("添加评论点赞到缓存，用户ID: {}, 评论ID: {}", userId, commentId);
    }
    
    @Override
    public void removeCommentLike(Integer userId, Integer commentId) {
        String key = COMMENT_LIKES_PREFIX + commentId;
        redisTemplate.opsForSet().remove(key, userId.toString());
        log.debug("移除评论点赞从缓存，用户ID: {}, 评论ID: {}", userId, commentId);
    }
    
    @Override
    public void addCommentDislike(Integer userId, Integer commentId) {
        String key = COMMENT_DISLIKES_PREFIX + commentId;
        redisTemplate.opsForSet().add(key, userId.toString());
        redisTemplate.expire(key, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        log.debug("添加评论点踩到缓存，用户ID: {}, 评论ID: {}", userId, commentId);
    }
    
    @Override
    public void removeCommentDislike(Integer userId, Integer commentId) {
        String key = COMMENT_DISLIKES_PREFIX + commentId;
        redisTemplate.opsForSet().remove(key, userId.toString());
        log.debug("移除评论点踩从缓存，用户ID: {}, 评论ID: {}", userId, commentId);
    }
    
    /**
     * 获取帖子的点赞状态（优化方法：一次性获取点赞和点踩状态）
     * @param userId 用户ID
     * @param postId 帖子ID
     * @return Map包含liked和disliked两个boolean值
     */
    public Map<String, Boolean> getPostLikeStatus(Integer userId, Integer postId) {
        Map<String, Boolean> result = new HashMap<>();
        
        String likeKey = POST_LIKES_PREFIX + postId;
        String dislikeKey = POST_DISLIKES_PREFIX + postId;
        
        // 确保缓存存在
        ensurePostCacheExists(postId);
        
        // 获取状态（如果Set不存在，isMember会返回false，这正是我们想要的）
        Boolean liked = redisTemplate.opsForSet().isMember(likeKey, userId.toString());
        Boolean disliked = redisTemplate.opsForSet().isMember(dislikeKey, userId.toString());
        
        result.put("liked", Boolean.TRUE.equals(liked));
        result.put("disliked", Boolean.TRUE.equals(disliked));
        
        return result;
    }
    
    @Override
    public boolean isPostLiked(Integer userId, Integer postId) {
        String key = POST_LIKES_PREFIX + postId;
        
        // 确保缓存存在
        ensurePostCacheExists(postId);
        
        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId.toString());
        return Boolean.TRUE.equals(isMember);
    }
    
    @Override
    public boolean isPostDisliked(Integer userId, Integer postId) {
        String key = POST_DISLIKES_PREFIX + postId;
        
        // 确保缓存存在
        ensurePostCacheExists(postId);
        
        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId.toString());
        return Boolean.TRUE.equals(isMember);
    }

    @Override
    public boolean isCommentLiked(Integer userId, Integer commentId) {
        String key = COMMENT_LIKES_PREFIX + commentId;

        // 确保缓存存在（使用优化后的方法）
        ensureCommentCacheExists(commentId);

        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId.toString());
        return Boolean.TRUE.equals(isMember);
    }

    @Override
    public boolean isCommentDisliked(Integer userId, Integer commentId) {
        String key = COMMENT_DISLIKES_PREFIX + commentId;

        // 确保缓存存在（使用优化后的方法）
        ensureCommentCacheExists(commentId);

        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId.toString());
        return Boolean.TRUE.equals(isMember);
    }

    @Override
    public Long getPostLikeCount(Integer postId) {
        String key = POST_LIKES_PREFIX + postId;

        // 确保缓存存在
        ensurePostCacheExists(postId);

        Long count = redisTemplate.opsForSet().size(key);
        return count != null ? count : 0L;
    }

    @Override
    public Long getPostDislikeCount(Integer postId) {
        String key = POST_DISLIKES_PREFIX + postId;

        // 确保缓存存在
        ensurePostCacheExists(postId);

        Long count = redisTemplate.opsForSet().size(key);
        return count != null ? count : 0L;
    }

    @Override
    public Long getCommentLikeCount(Integer commentId) {
        String key = COMMENT_LIKES_PREFIX + commentId;

        // 确保缓存存在（使用优化后的方法）
        ensureCommentCacheExists(commentId);

        Long count = redisTemplate.opsForSet().size(key);
        return count != null ? count : 0L;
    }

    @Override
    public Long getCommentDislikeCount(Integer commentId) {
        String key = COMMENT_DISLIKES_PREFIX + commentId;

        // 确保缓存存在（使用优化后的方法）
        ensureCommentCacheExists(commentId);

        Long count = redisTemplate.opsForSet().size(key);
        return count != null ? count : 0L;
    }

    /**
     * 确保帖子缓存存在（防重复同步的优化方法）
     * @param postId 帖子ID
     */
    private void ensurePostCacheExists(Integer postId) {
        String likeKey = POST_LIKES_PREFIX + postId;
        String dislikeKey = POST_DISLIKES_PREFIX + postId;
        
        // 使用批量操作检查所有相关键是否存在，减少网络往返
        List<String> keysToCheck = java.util.Arrays.asList(
            likeKey, likeKey + ":synced", 
            dislikeKey, dislikeKey + ":synced"
        );
        
        // 批量检查键是否存在
        List<Boolean> existResults = redisTemplate.execute((RedisCallback<List<Boolean>>) connection -> {
            List<Boolean> results = new ArrayList<>();
            for (String key : keysToCheck) {
                results.add(connection.exists(key.getBytes()));
            }
            return results;
        });
        
        // 解析结果：任一点赞相关键存在 AND 任一点踩相关键存在
        boolean likeExists = existResults.get(0) || existResults.get(1);  // likeKey 或 likeKey:synced
        boolean dislikeExists = existResults.get(2) || existResults.get(3);  // dislikeKey 或 dislikeKey:synced
        
        // 如果任何一个缓存不存在（包括同步标记），就同步数据库数据
        if (!likeExists || !dislikeExists) {
            syncPostLikesFromDatabase(postId);
        }
    }

    /**
     * 确保评论缓存存在（防重复同步的优化方法）
     * @param commentId 评论ID
     */
    private void ensureCommentCacheExists(Integer commentId) {
        String likeKey = COMMENT_LIKES_PREFIX + commentId;
        String dislikeKey = COMMENT_DISLIKES_PREFIX + commentId;
        
        // 使用批量操作检查所有相关键是否存在，减少网络往返
        List<String> keysToCheck = java.util.Arrays.asList(
            likeKey, likeKey + ":synced", 
            dislikeKey, dislikeKey + ":synced"
        );
        
        // 批量检查键是否存在
        List<Boolean> existResults = redisTemplate.execute((RedisCallback<List<Boolean>>) connection -> {
            List<Boolean> results = new ArrayList<>();
            for (String key : keysToCheck) {
                results.add(connection.exists(key.getBytes()));
            }
            return results;
        });
        
        // 解析结果：任一点赞相关键存在 AND 任一点踩相关键存在
        boolean likeExists = existResults.get(0) || existResults.get(1);  // likeKey 或 likeKey:synced
        boolean dislikeExists = existResults.get(2) || existResults.get(3);  // dislikeKey 或 dislikeKey:synced
        
        // 如果任何一个缓存不存在（包括同步标记），就同步数据库数据
        if (!likeExists || !dislikeExists) {
            syncCommentLikesFromDatabase(commentId);
        }
    }

    @Override
    public void syncPostLikesFromDatabase(Integer postId) {
        long startTime = System.currentTimeMillis();
        log.info("开始同步帖子点赞状态，帖子ID: {}", postId);
        
        String lockKey = "sync:post:" + postId;
        Object lock = syncLocks.computeIfAbsent(lockKey, k -> new Object());
        
        long lockWaitStart = System.currentTimeMillis();
        synchronized (lock) {
            long lockAcquiredTime = System.currentTimeMillis();
            log.info("获取同步锁耗时: {} ms，帖子ID: {}", lockAcquiredTime - lockWaitStart, postId);
            
            try {
                String likeKey = POST_LIKES_PREFIX + postId;
                String dislikeKey = POST_DISLIKES_PREFIX + postId;
                
                // 双重检查：如果缓存已经存在，就不需要同步了
                long doubleCheckStart = System.currentTimeMillis();
                if (Boolean.TRUE.equals(redisTemplate.hasKey(likeKey)) && 
                    Boolean.TRUE.equals(redisTemplate.hasKey(dislikeKey))) {
                    log.debug("帖子点赞缓存已存在，跳过同步，帖子ID: {}", postId);
                    return;
                }
                long doubleCheckEnd = System.currentTimeMillis();
                log.info("双重检查缓存耗时: {} ms，帖子ID: {}", doubleCheckEnd - doubleCheckStart, postId);
                
                // 查询帖子的所有点赞记录
                long dbQueryStart = System.currentTimeMillis();
                LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Like::getPostId, postId)
                       .isNull(Like::getCommentId)
                       .eq(Like::getDeleted, 0);
                List<Like> likes = likeMapper.selectList(wrapper);
                long dbQueryEnd = System.currentTimeMillis();
                log.info("数据库查询点赞记录耗时: {} ms，帖子ID: {}，查询到 {} 条记录", 
                         dbQueryEnd - dbQueryStart, postId, likes.size());

                // 清空现有缓存
                long redisClearStart = System.currentTimeMillis();
                redisTemplate.delete(likeKey);
                redisTemplate.delete(dislikeKey);
                long redisClearEnd = System.currentTimeMillis();
                log.info("清空Redis缓存耗时: {} ms，帖子ID: {}", redisClearEnd - redisClearStart, postId);

                // 重新构建缓存
                long redisBuildStart = System.currentTimeMillis();
                for (Like like : likes) {
                    if (like.getType() == 1) {
                        // 点赞
                        redisTemplate.opsForSet().add(likeKey, like.getUserId().toString());
                    } else if (like.getType() == -1) {
                        // 点踩
                        redisTemplate.opsForSet().add(dislikeKey, like.getUserId().toString());
                    }
                }
                long redisBuildEnd = System.currentTimeMillis();
                log.info("构建Redis缓存耗时: {} ms，帖子ID: {}", redisBuildEnd - redisBuildStart, postId);

                // 为了避免缓存穿透，即使没有数据也要创建缓存标记
                long syncMarkStart = System.currentTimeMillis();
                
                // 使用pipeline批量执行Redis操作，减少网络往返
                redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    // 检查并设置同步标记
                    if (!connection.exists(likeKey.getBytes())) {
                        // 设置同步标记，表示此帖子已经同步过但没有点赞数据
                        connection.setEx((likeKey + ":synced").getBytes(), 
                                       CACHE_EXPIRE_HOURS * 3600, "1".getBytes());
                    } else {
                        // 为实际存在的Set设置过期时间
                        connection.expire(likeKey.getBytes(), CACHE_EXPIRE_HOURS * 3600);
                    }
                    
                    if (!connection.exists(dislikeKey.getBytes())) {
                        // 设置同步标记，表示此帖子已经同步过但没有点踩数据
                        connection.setEx((dislikeKey + ":synced").getBytes(), 
                                       CACHE_EXPIRE_HOURS * 3600, "1".getBytes());
                    } else {
                        // 为实际存在的Set设置过期时间
                        connection.expire(dislikeKey.getBytes(), CACHE_EXPIRE_HOURS * 3600);
                    }
                    
                    return null;
                });
                
                long syncMarkEnd = System.currentTimeMillis();
                log.info("设置同步标记和过期时间耗时: {} ms，帖子ID: {}", syncMarkEnd - syncMarkStart, postId);

                long totalTime = System.currentTimeMillis() - startTime;
                log.info("同步帖子点赞状态到缓存完成，帖子ID: {}, 点赞数: {}, 点踩数: {}, 总耗时: {} ms",
                        postId,
                        redisTemplate.opsForSet().size(likeKey),
                        redisTemplate.opsForSet().size(dislikeKey),
                        totalTime);

            } catch (Exception e) {
                log.error("同步帖子点赞状态到缓存失败，帖子ID: {}", postId, e);
            } finally {
                // 清理锁映射，避免内存泄漏
                syncLocks.remove(lockKey);
            }
        }
    }

    @Override
    public void syncCommentLikesFromDatabase(Integer commentId) {
        String lockKey = "sync:comment:" + commentId;
        Object lock = syncLocks.computeIfAbsent(lockKey, k -> new Object());
        
        synchronized (lock) {
            try {
                String likeKey = COMMENT_LIKES_PREFIX + commentId;
                String dislikeKey = COMMENT_DISLIKES_PREFIX + commentId;
                
                // 双重检查：如果缓存已经存在，就不需要同步了
                boolean likeExists = Boolean.TRUE.equals(redisTemplate.hasKey(likeKey)) || 
                                    Boolean.TRUE.equals(redisTemplate.hasKey(likeKey + ":synced"));
                boolean dislikeExists = Boolean.TRUE.equals(redisTemplate.hasKey(dislikeKey)) ||
                                       Boolean.TRUE.equals(redisTemplate.hasKey(dislikeKey + ":synced"));
                
                if (likeExists && dislikeExists) {
                    log.debug("评论点赞缓存已存在，跳过同步，评论ID: {}", commentId);
                    return;
                }
                
                // 查询评论的所有点赞记录
                LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Like::getCommentId, commentId)
                       .isNull(Like::getPostId)
                       .eq(Like::getDeleted, 0);
                List<Like> likes = likeMapper.selectList(wrapper);

                // 清空现有缓存
                redisTemplate.delete(likeKey);
                redisTemplate.delete(dislikeKey);
                redisTemplate.delete(likeKey + ":synced");
                redisTemplate.delete(dislikeKey + ":synced");

                // 重新构建缓存
                for (Like like : likes) {
                    if (like.getType() == 1) {
                        // 点赞
                        redisTemplate.opsForSet().add(likeKey, like.getUserId().toString());
                    } else if (like.getType() == -1) {
                        // 点踩
                        redisTemplate.opsForSet().add(dislikeKey, like.getUserId().toString());
                    }
                }

                // 为了避免缓存穿透，即使没有数据也要创建缓存标记
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(likeKey))) {
                    // 设置一个特殊的字符串值来标记此评论已经同步过，但没有点赞数据
                    redisTemplate.opsForValue().set(likeKey + ":synced", "1", CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                }
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(dislikeKey))) {
                    // 设置一个特殊的字符串值来标记此评论已经同步过，但没有点踩数据
                    redisTemplate.opsForValue().set(dislikeKey + ":synced", "1", CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                }

                // 为实际存在数据的Set设置过期时间
                if (Boolean.TRUE.equals(redisTemplate.hasKey(likeKey))) {
                    redisTemplate.expire(likeKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                }
                if (Boolean.TRUE.equals(redisTemplate.hasKey(dislikeKey))) {
                    redisTemplate.expire(dislikeKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                }

                log.info("同步评论点赞状态到缓存完成，评论ID: {}, 点赞数: {}, 点踩数: {}",
                        commentId,
                        redisTemplate.opsForSet().size(likeKey),
                        redisTemplate.opsForSet().size(dislikeKey));

            } catch (Exception e) {
                log.error("同步评论点赞状态到缓存失败，评论ID: {}", commentId, e);
            } finally {
                // 清理锁映射，避免内存泄漏
                syncLocks.remove(lockKey);
            }
        }
    }
    
    @Override
    public void batchWarmupCommentCache(List<Integer> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return;
        }
        
        log.info("开始批量预热评论缓存，评论数量: {}", commentIds.size());
        
        // 并行处理缓存预热，提高效率
        commentIds.parallelStream().forEach(commentId -> {
            try {
                ensureCommentCacheExists(commentId);
            } catch (Exception e) {
                log.warn("预热评论缓存失败，评论ID: {}", commentId, e);
            }
        });
        
        log.info("批量预热评论缓存完成，评论数量: {}", commentIds.size());
    }
    
    @Override
    public Map<Integer, Map<String, Boolean>> batchGetCommentLikeStatus(Integer userId, List<Integer> commentIds) {
        Map<Integer, Map<String, Boolean>> result = new HashMap<>();
        
        if (commentIds == null || commentIds.isEmpty()) {
            return result;
        }
        
        long startTime = System.currentTimeMillis();
        log.info("⏱️ 🚀 开始批量查询用户 {} 对 {} 个评论的点赞状态", userId, commentIds.size());
        
        try {
            // 步骤A：查询用户的点赞记录
            long stepAStart = System.currentTimeMillis();
            LambdaQueryWrapper<Like> batchQuery = new LambdaQueryWrapper<>();
            batchQuery.eq(Like::getUserId, userId)
                     .in(Like::getCommentId, commentIds)
                     .isNull(Like::getPostId)
                     .eq(Like::getDeleted, 0);
            
            List<Like> userLikes = likeMapper.selectList(batchQuery);
            long stepATime = System.currentTimeMillis() - stepAStart;
            log.info("⏱️ 步骤A-查询用户点赞记录完成，耗时: {}ms，查询到 {} 条记录", stepATime, userLikes.size());
            
            // 步骤B：构建用户点赞映射
            long stepBStart = System.currentTimeMillis();
            Map<Integer, Integer> userLikeMap = userLikes.stream()
                .collect(Collectors.toMap(Like::getCommentId, Like::getType));
            long stepBTime = System.currentTimeMillis() - stepBStart;
            log.info("⏱️ 步骤B-构建点赞映射完成，耗时: {}ms", stepBTime);
            
            // 步骤C：为每个评论构建结果
            long stepCStart = System.currentTimeMillis();
            for (Integer commentId : commentIds) {
                Map<String, Boolean> status = new HashMap<>();
                Integer likeType = userLikeMap.get(commentId);
                
                if (likeType != null) {
                    status.put("liked", likeType == 1);
                    status.put("disliked", likeType == -1);
                } else {
                    status.put("liked", false);
                    status.put("disliked", false);
                }
                
                result.put(commentId, status);
            }
            long stepCTime = System.currentTimeMillis() - stepCStart;
            log.info("⏱️ 步骤C-构建结果Map完成，耗时: {}ms", stepCTime);
            
            // 步骤D：暂时跳过缓存更新（Redis太慢）
            long stepDStart = System.currentTimeMillis();
            log.info("🚫 **暂时禁用缓存更新**，Redis操作太慢！改为纯数据库方案");
            // smartUpdateCommentCache(commentIds, userLikes);  // 暂时禁用
            long stepDTime = System.currentTimeMillis() - stepDStart;
            log.info("⏱️ 步骤D-跳过缓存更新，耗时: {}ms", stepDTime);
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("⏱️ ✅ 批量查询评论点赞状态总耗时: {}ms（A:{}ms + B:{}ms + C:{}ms + D:{}ms）", 
                    totalTime, stepATime, stepBTime, stepCTime, stepDTime);
            
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("⏱️ ❌ 批量查询评论点赞状态失败，总耗时: {}ms，错误: {}", totalTime, e.getMessage(), e);
            // 失败时返回默认状态
            for (Integer commentId : commentIds) {
                Map<String, Boolean> status = new HashMap<>();
                status.put("liked", false);
                status.put("disliked", false);
                result.put(commentId, status);
            }
        }
        
        return result;
    }
    
    /**
     * 智能缓存更新：只更新缺失的缓存，避免无意义的Redis操作
     */
    private void smartUpdateCommentCache(List<Integer> commentIds, List<Like> userLikes) {
        try {
            long smartStart = System.currentTimeMillis();
            log.info("⏱️ 开始智能缓存更新，评论数量: {}", commentIds.size());
            
            // 步骤1：快速检查哪些缓存缺失
            List<Integer> missingCacheIds = new ArrayList<>();
            for (Integer commentId : commentIds) {
                String likeKey = COMMENT_LIKES_PREFIX + commentId;
                String dislikeKey = COMMENT_DISLIKES_PREFIX + commentId;
                String syncedKey = likeKey + ":synced";
                
                // 只有缓存完全缺失才需要更新
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(likeKey)) && 
                    !Boolean.TRUE.equals(redisTemplate.hasKey(dislikeKey)) &&
                    !Boolean.TRUE.equals(redisTemplate.hasKey(syncedKey))) {
                    missingCacheIds.add(commentId);
                }
            }
            
            long checkTime = System.currentTimeMillis() - smartStart;
            log.info("⏱️ 缓存缺失检查完成，耗时: {}ms，需要更新: {}/{}", 
                    checkTime, missingCacheIds.size(), commentIds.size());
            
            // 步骤2：只为缺失的评论更新缓存
            if (!missingCacheIds.isEmpty()) {
                log.info("⚡ 只更新 {} 个缺失的评论缓存，跳过 {} 个已有缓存", 
                        missingCacheIds.size(), commentIds.size() - missingCacheIds.size());
                        
                // 只查询缺失评论的数据
                LambdaQueryWrapper<Like> missingQuery = new LambdaQueryWrapper<>();
                missingQuery.in(Like::getCommentId, missingCacheIds)
                           .isNull(Like::getPostId)
                           .eq(Like::getDeleted, 0);
                List<Like> missingLikes = likeMapper.selectList(missingQuery);
                
                // 更新缺失的缓存
                updateSpecificCommentCache(missingCacheIds, missingLikes);
            } else {
                log.info("🎯 所有评论缓存已存在，跳过更新！");
            }
            
            long totalSmartTime = System.currentTimeMillis() - smartStart;
            log.info("⏱️ ✅ 智能缓存更新完成，总耗时: {}ms", totalSmartTime);
            
        } catch (Exception e) {
            log.error("⏱️ ❌ 智能缓存更新失败", e);
        }
    }
    
    /**
     * 更新指定评论的缓存（轻量版）
     */
    private void updateSpecificCommentCache(List<Integer> commentIds, List<Like> likes) {
        try {
            // 按评论ID分组likes
            Map<Integer, List<Like>> likesByComment = likes.stream()
                .collect(Collectors.groupingBy(Like::getCommentId));
            
            // 批量操作Redis（使用pipeline提高效率）
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Integer commentId : commentIds) {
                    String likeKey = COMMENT_LIKES_PREFIX + commentId;
                    String dislikeKey = COMMENT_DISLIKES_PREFIX + commentId;
                    
                    // 获取该评论的所有点赞记录
                    List<Like> commentLikes = likesByComment.getOrDefault(commentId, new ArrayList<>());
                    
                    // 添加点赞/点踩用户
                    for (Like like : commentLikes) {
                        if (like.getType() == 1) {
                            connection.sAdd(likeKey.getBytes(), like.getUserId().toString().getBytes());
                        } else if (like.getType() == -1) {
                            connection.sAdd(dislikeKey.getBytes(), like.getUserId().toString().getBytes());
                        }
                    }
                    
                    // 设置过期时间和同步标记
                    connection.expire(likeKey.getBytes(), CACHE_EXPIRE_HOURS * 3600);
                    connection.expire(dislikeKey.getBytes(), CACHE_EXPIRE_HOURS * 3600);
                    connection.setEx((likeKey + ":synced").getBytes(), CACHE_EXPIRE_HOURS * 3600, "1".getBytes());
                }
                return null;
            });
            
            log.info("✅ 使用Pipeline批量更新 {} 个评论缓存", commentIds.size());
            
        } catch (Exception e) {
            log.error("❌ 更新指定评论缓存失败", e);
        }
    }
}
