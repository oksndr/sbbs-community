package com.itheima.sbbs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.itheima.sbbs.entity.AuthorEmailDto;
import com.itheima.sbbs.entity.Post;
import com.itheima.sbbs.entity.PostWithUserDto;
import com.itheima.sbbs.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /**
     * 根据postId查询出用户id
     * @param postId
     * @return
     */
    @Select("select p.title, u.email, u.id " +
            "from post p " +
            "JOIN \"user\" u ON p.user_id = u.id " +
            "where p.id = #{postId} AND p.deleted = 0")
    public AuthorEmailDto selectUserByPostId(@Param("postId") Integer postId);

    /**
     * 根据关键词搜索帖子，并联查作者信息
     * @param keyword 搜索关键词
     * @return 包含作者信息的帖子列表
     */
    @SelectProvider(type = PostSqlProvider.class, method = "searchPostsByKeyword")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "tagIdsStringAlias", column = "tag_ids_string_alias")
    })
    List<PostWithUserDto> searchPostsByKeyword(@Param("keyword") String keyword);
    
    /**
     * 根据关键词搜索帖子（支持分页）
     * @param keyword 搜索关键词
     * @param page 页码
     * @param pageSize 每页大小
     * @return 帖子列表和作者信息
     */
    @SelectProvider(type = PostSqlProvider.class, method = "searchPostsByKeywordWithPaging")
    List<PostWithUserDto> searchPostsByKeywordWithPaging(@Param("keyword") String keyword, 
                                                        @Param("page") Integer page, 
                                                        @Param("pageSize") Integer pageSize);
    
    /**
     * 统计搜索结果总数
     * @param keyword 搜索关键词
     * @return 搜索结果总数
     */
    @SelectProvider(type = PostSqlProvider.class, method = "countSearchPostsByKeyword")
    Integer countSearchPostsByKeyword(@Param("keyword") String keyword);

    /**
     * 使用MyBatis-Plus分页搜索帖子
     * @param page 分页参数
     * @param keyword 搜索关键词
     * @return 分页结果
     */
    @SelectProvider(type = PostSqlProvider.class, method = "searchPostsByKeywordForPage")
    IPage<PostWithUserDto> searchPostsByKeywordWithPage(IPage<PostWithUserDto> page, @Param("keyword") String keyword);

    /**
     * 根据ID查询帖子详情，并联查作者和标签信息
     * @param postId 帖子ID
     * @return 包含作者和标签信息的帖子详情 DTO
     */
    @SelectProvider(type = PostSqlProvider.class, method = "getPostDetailById")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "createdMillis", column = "created_millis"),
        @Result(property = "updatedMillis", column = "updated_millis")
    })
    PostWithUserDto getPostDetailById(@Param("postId") Integer postId);
    
    /**
     * 根据用户ID查询用户发布的帖子列表
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 用户发布的帖子列表
     */
    @SelectProvider(type = PostSqlProvider.class, method = "getUserPosts")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "tagIdsStringAlias", column = "tag_ids_string_alias")
    })
    List<PostWithUserDto> getUserPosts(@Param("userId") Integer userId, @Param("offset") Integer offset, @Param("limit") Integer limit);
    
    /**
     * 🚀 高性能版本：根据用户ID查询用户发布的帖子列表（含标签名称）
     * 一次查询获取帖子和对应的标签名称，避免额外的标签查询，大幅提升性能
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 包含标签名称的用户发布的帖子列表
     */
    @SelectProvider(type = PostSqlProvider.class, method = "getUserPostsWithTags")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "tagIdsStringAlias", column = "tag_ids_string_alias"),
        @Result(property = "tagNames", column = "tag_names") // 新增：直接映射标签名称字符串
    })
    List<PostWithUserDto> getUserPostsWithTags(@Param("userId") Integer userId, @Param("offset") Integer offset, @Param("limit") Integer limit);
    
    /**
     * 统计用户发布的帖子总数
     * @param userId 用户ID
     * @return 帖子总数
     */
    @Select("SELECT COUNT(*) FROM post WHERE user_id = #{userId} AND deleted = 0")
    Integer countUserPosts(@Param("userId") Integer userId);

    @Update("UPDATE post SET comment_count = comment_count + 1, " +
            "updated = CURRENT_TIMESTAMP " +
            "WHERE deleted=0 AND id = #{postId}")
    void incrementCommentCount(@Param("postId") Integer postId);
}
