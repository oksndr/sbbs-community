package com.itheima.sbbs.mapper;

import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.annotations.Param;

public class PostSqlProvider {

    public String searchPostsByKeyword(
            @Param("keyword") String keyword) {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.id, p.user_id, p.title, p.comment_count, p.like_count, p.dislike_count, ");
        // 使用数据库日期时间字段
        sql.append("p.created, p.updated, ");
        sql.append("p.deleted, ");
        sql.append("u.username, u.avatar, ");
        // 直接查询tag_ids_string字段
        sql.append("p.tag_ids_string as tag_ids_string_alias ");
        sql.append("FROM post p ");
        sql.append("JOIN \"user\" u ON p.user_id = u.id ");
        // 删除连接post_tag和tag表的代码
        sql.append("WHERE p.deleted = 0 AND u.deleted = 0 ");
        // 删除中间表和标签表的软删除检查

        if (keyword != null && !keyword.isEmpty()) {
            // 添加关键词搜索条件：仍然搜索content字段，但不返回它
            sql.append("AND (p.title LIKE '%' || #{keyword} || '%' OR p.content LIKE '%' || #{keyword} || '%') ");
        }

        sql.append("GROUP BY p.id, p.user_id, p.title, p.comment_count, p.like_count, p.dislike_count, p.created, p.updated, p.deleted, u.username, u.avatar, p.tag_ids_string "); // 添加所有非聚合字段到GROUP BY
        sql.append("ORDER BY p.updated DESC, p.id DESC "); // 按创建时间倒序和ID倒序排列
        sql.append("LIMIT 20"); // 限制搜索结果最多返回20个帖子，防止数据库炸掉

        return sql.toString();
    }

    public String getPostDetailById(@Param("postId") Integer postId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.id, p.user_id, p.title, p.content, p.comment_count, p.like_count, p.dislike_count, ");
        // 直接使用数据库日期时间字段，不再提取毫秒时间戳
        sql.append("p.created, p.updated, ");
        sql.append("p.deleted, ");
        sql.append("u.username, u.avatar, ");
        // 直接查询 tag_ids_string 并别名，方便后续处理
        sql.append("p.tag_ids_string as tag_ids_string_alias ");
        sql.append("FROM post p ");
        sql.append("JOIN \"user\" u ON p.user_id = u.id ");
        sql.append("WHERE p.id = #{postId} AND p.deleted = 0 AND u.deleted = 0");

        return sql.toString();
    }
    
    /**
     * 获取用户发布的帖子列表
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return SQL语句
     */
    public String getUserPosts(@Param("userId") Integer userId, 
                              @Param("offset") Integer offset, 
                              @Param("limit") Integer limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.id, p.user_id, p.title, p.comment_count, p.like_count, p.dislike_count, ");
        sql.append("p.created, p.updated, ");
        sql.append("p.deleted, ");
        sql.append("u.username, u.avatar, ");
        sql.append("p.tag_ids_string as tag_ids_string_alias ");
        sql.append("FROM post p ");
        sql.append("JOIN \"user\" u ON p.user_id = u.id ");
        sql.append("WHERE p.user_id = #{userId} AND p.deleted = 0 AND u.deleted = 0 ");
        sql.append("ORDER BY p.updated DESC, p.id DESC ");
        sql.append("OFFSET #{offset} LIMIT #{limit}");
        
        return sql.toString();
    }
    
    /**
     * 🚀 高性能版本：获取用户发布的帖子列表（含标签名称，一次查询完成）
     * 使用CTE（公用表表达式）和字符串聚合，避免额外的标签查询
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 优化的SQL语句
     */
    public String getUserPostsWithTags(@Param("userId") Integer userId, 
                                      @Param("offset") Integer offset, 
                                      @Param("limit") Integer limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("WITH user_posts AS ( ");
        sql.append("  SELECT p.id, p.user_id, p.title, p.comment_count, p.like_count, p.dislike_count, ");
        sql.append("         p.created, p.updated, p.deleted, ");
        sql.append("         u.username, u.avatar, ");
        sql.append("         p.tag_ids_string as tag_ids_string_alias ");
        sql.append("  FROM post p ");
        sql.append("  JOIN \"user\" u ON p.user_id = u.id ");
        sql.append("  WHERE p.user_id = #{userId} AND p.deleted = 0 AND u.deleted = 0 ");
        sql.append("  ORDER BY p.updated DESC, p.id DESC ");
        sql.append("  OFFSET #{offset} LIMIT #{limit} ");
        sql.append("), ");
        sql.append("post_tags AS ( ");
        sql.append("  SELECT up.id as post_id, ");
        sql.append("         STRING_AGG(t.name, ',' ORDER BY t.id) as tag_names ");
        sql.append("  FROM user_posts up ");
        sql.append("  LEFT JOIN LATERAL (SELECT unnest(string_to_array(up.tag_ids_string_alias, ','))::integer as tag_id) as tag_ids ON true ");
        sql.append("  LEFT JOIN tag t ON tag_ids.tag_id = t.id AND t.deleted = 0 ");
        sql.append("  GROUP BY up.id ");
        sql.append(") ");
        sql.append("SELECT up.*, pt.tag_names ");
        sql.append("FROM user_posts up ");
        sql.append("LEFT JOIN post_tags pt ON up.id = pt.post_id ");
        sql.append("ORDER BY up.updated DESC, up.id DESC");
        
        return sql.toString();
    }

    public String searchPostsByKeywordWithPaging(
            @Param("keyword") String keyword,
            @Param("page") Integer page,
            @Param("pageSize") Integer pageSize) {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.id, p.user_id, p.title, p.comment_count, p.like_count, p.dislike_count, ");
        // 使用数据库日期时间字段
        sql.append("p.created, p.updated, ");
        sql.append("p.deleted, ");
        sql.append("u.username, u.avatar, ");
        // 直接查询tag_ids_string字段
        sql.append("p.tag_ids_string as tag_ids_string_alias ");
        sql.append("FROM post p ");
        sql.append("JOIN \"user\" u ON p.user_id = u.id ");
        sql.append("WHERE p.deleted = 0 AND u.deleted = 0 ");

        if (keyword != null && !keyword.isEmpty()) {
            // 添加关键词搜索条件：仍然搜索content字段，但不返回它
            sql.append("AND (p.title LIKE '%' || #{keyword} || '%' OR p.content LIKE '%' || #{keyword} || '%') ");
        }

        sql.append("GROUP BY p.id, p.user_id, p.title, p.comment_count, p.like_count, p.dislike_count, p.created, p.updated, p.deleted, u.username, u.avatar, p.tag_ids_string "); // 添加所有非聚合字段到GROUP BY
        sql.append("ORDER BY p.updated DESC, p.id DESC "); // 按创建时间倒序和ID倒序排列
        
        // 计算OFFSET
        int offset = (page - 1) * pageSize;
        sql.append("OFFSET ").append(offset).append(" LIMIT ").append(pageSize);

        return sql.toString();
    }
    
    public String countSearchPostsByKeyword(@Param("keyword") String keyword) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(DISTINCT p.id) ");
        sql.append("FROM post p ");
        sql.append("JOIN \"user\" u ON p.user_id = u.id ");
        sql.append("WHERE p.deleted = 0 AND u.deleted = 0 ");

        if (keyword != null && !keyword.isEmpty()) {
            // 添加关键词搜索条件
            sql.append("AND (p.title LIKE '%' || #{keyword} || '%' OR p.content LIKE '%' || #{keyword} || '%') ");
        }

        return sql.toString();
    }
    
    /**
     * 使用MyBatis-Plus分页搜索帖子
     * @param keyword 搜索关键词
     * @return SQL语句
     */
    public String searchPostsByKeywordForPage(@Param("keyword") String keyword) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.id, p.user_id, p.title, p.comment_count, p.like_count, p.dislike_count, ");
        // 使用数据库日期时间字段
        sql.append("p.created, p.updated, ");
        sql.append("p.deleted, ");
        sql.append("u.username, u.avatar, ");
        // 直接查询tag_ids_string字段
        sql.append("p.tag_ids_string as tag_ids_string_alias ");
        sql.append("FROM post p ");
        sql.append("JOIN \"user\" u ON p.user_id = u.id ");
        sql.append("WHERE p.deleted = 0 AND u.deleted = 0 ");

        if (keyword != null && !keyword.isEmpty()) {
            // 添加关键词搜索条件：仍然搜索content字段，但不返回它
            sql.append("AND (p.title LIKE '%' || #{keyword} || '%' OR p.content LIKE '%' || #{keyword} || '%') ");
        }

        sql.append("GROUP BY p.id, p.user_id, p.title, p.comment_count, p.like_count, p.dislike_count, p.created, p.updated, p.deleted, u.username, u.avatar, p.tag_ids_string "); // 添加所有非聚合字段到GROUP BY
        sql.append("ORDER BY p.updated DESC, p.id DESC "); // 按创建时间倒序和ID倒序排列
        // 注意：不要在这里添加LIMIT子句，MyBatis-Plus分页插件会自动添加
        
        return sql.toString();
    }
} 