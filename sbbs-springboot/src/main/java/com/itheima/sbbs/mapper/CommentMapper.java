package com.itheima.sbbs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.sbbs.entity.Comment;
import com.itheima.sbbs.entity.CommentWithUserDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 更新评论点赞数
     * @param commentId 评论ID
     * @param delta 增加或减少的数量 (1 或 -1)
     * @return 影响的行数
     */
    @Update("UPDATE \"comment\" SET like_count = like_count + #{delta} WHERE id = #{commentId}")
    int updateLikeCountById(@Param("commentId") Integer commentId, @Param("delta") int delta);

    /**
     * 更新评论点踩数
     * @param commentId 评论ID
     * @param delta 增加或减少的数量 (1 或 -1)
     * @return 影响的行数
     */
    @Update("UPDATE \"comment\" SET dislike_count = dislike_count + #{delta} WHERE id = #{commentId}")
    int updateDislikeCountById(@Param("commentId") Integer commentId, @Param("delta") int delta);

    /**
     * 更新评论的点赞数和点踩数
     * @param commentId 评论ID
     * @param likeDelta 点赞数变化量 (1, -1 或 0)
     * @param dislikeDelta 点踩数变化量 (1, -1 或 0)
     * @return 影响的行数
     */
    @Update("UPDATE \"comment\" SET like_count = like_count + #{likeDelta}, dislike_count = dislike_count + #{dislikeDelta} WHERE id = #{commentId}")
    int updateCountsById(@Param("commentId") Integer commentId, @Param("likeDelta") int likeDelta, @Param("dislikeDelta") int dislikeDelta);

    /**
     * 根据 postId 和游标查询一级评论列表，并联查作者信息
     * @param postId 帖子ID
     * @param lastId 游标评论ID
     * @param pageSize 分页大小
     * @return 包含作者信息的评论列表
     */
    @SelectProvider(type = CommentSqlProvider.class, method = "selectTopCommentsWithUser")
    List<CommentWithUserDto> selectTopCommentsWithUser(@Param("postId") Integer postId,
                                                      @Param("lastId") Integer lastId,
                                                      @Param("pageSize") Integer pageSize);

    /**
     * 根据父评论ID分页查询二级评论列表，并联查作者信息
     * @param parentId 父评论ID
     * @return 包含作者信息的二级评论列表
     */
    @Select("SELECT c.id, c.post_id, c.user_id, c.parent_id, c.content, c.reply_count, c.like_count, c.dislike_count, c.created, c.updated, c.deleted, " +
            "u.username, u.avatar " +
            "FROM \"comment\" c " +
            "JOIN \"user\" u ON c.user_id = u.id " +
            "WHERE c.parent_id = #{parentId} AND c.deleted = 0 AND u.deleted = 0 " + // 添加父评论ID和软删除条件
            "ORDER BY c.created ASC, c.id ASC") // 按照创建时间或 ID 升序排列
    List<CommentWithUserDto> selectRepliesByParentId(@Param("parentId") Integer parentId);
    
    /**
     * 统计用户的评论总数
     * @param userId 用户ID
     * @return 评论总数
     */
    @Select("SELECT COUNT(*) FROM \"comment\" WHERE user_id = #{userId} AND deleted = 0")
    Integer countUserComments(@Param("userId") Integer userId);
    
    /**
     * 根据 postId 分页查询一级评论列表，并联查作者信息（使用MyBatis-Plus分页）
     * @param page MyBatis-Plus 分页对象
     * @param postId 帖子ID
     * @return 包含作者信息的分页评论列表
     */
    @Select("SELECT c.id, c.post_id, c.user_id, c.parent_id, c.content, c.reply_count, c.like_count, c.dislike_count, c.created, c.updated, c.deleted, " +
            "u.username, u.avatar " +
            "FROM \"comment\" c " +
            "JOIN \"user\" u ON c.user_id = u.id " +
            "WHERE c.post_id = #{postId} AND c.parent_id IS NULL AND c.deleted = 0 AND u.deleted = 0 " +
            "ORDER BY c.created ASC, c.id ASC")
    Page<CommentWithUserDto> selectTopCommentsByPage(Page<CommentWithUserDto> page, @Param("postId") Integer postId);
    
    /**
     * 查询一级评论在帖子中的位置（使用LIMIT优化）
     * @param postId 帖子ID
     * @param commentId 评论ID
     * @return 评论在排序后的位置（从1开始计数）
     */
    @Select("SELECT COUNT(*) + 1 " +
            "FROM \"comment\" " +
            "WHERE post_id = #{postId} " +
            "AND parent_id IS NULL " +//查询的必须是一级评论
            "AND deleted = 0 " +
            "AND (created < (SELECT created FROM \"comment\" WHERE id = #{commentId}) " +//创建时间更早 / 创建时间相同但是id更小
            "     OR (created = (SELECT created FROM \"comment\" WHERE id = #{commentId}) " +
            "         AND id < #{commentId}))")
    Integer findCommentPosition(@Param("postId") Integer postId, @Param("commentId") Integer commentId);

    @Select("SELECT c.id, c.post_id, c.user_id, c.parent_id, c.content, " +
            "c.reply_count, c.like_count, c.dislike_count, c.created, c.updated, c.deleted, " +
            "u.username, u.avatar " +
            "FROM \"comment\" c " +
            "JOIN \"user\" u ON c.user_id = u.id " +
            "WHERE c.id = #{commentId} AND c.deleted = 0 AND u.deleted = 0")
    CommentWithUserDto selectCommentWithUser(@Param("commentId") Integer commentId);

    @Update("UPDATE \"comment\" SET reply_count = reply_count + 1 " +
            "WHERE deleted=0 AND id = #{commentId}")
    void incrementReplyCount(@Param("commentId") Integer commentId);
}
