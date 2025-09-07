package com.itheima.sbbs.mapper;

import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.annotations.Param;

public class CommentSqlProvider {

    public String selectTopCommentsWithUser(
            @Param("postId") Integer postId,
            @Param("lastId") Integer lastId,
            @Param("pageSize") Integer pageSize) {

        return new SQL() {{
            SELECT("c.id, c.post_id, c.user_id, c.parent_id, c.content, c.reply_count, c.like_count, c.dislike_count, c.created, c.updated, c.deleted");
            SELECT("u.username, u.avatar");
            FROM("\"comment\" c");
            JOIN("\"user\" u ON c.user_id = u.id");
            WHERE("c.post_id = #{postId}");
            WHERE("c.parent_id IS NULL");
            WHERE("c.deleted = 0");
            WHERE("u.deleted = 0");

            if (lastId != null && lastId != 0) {
                WHERE("c.id > #{lastId}");
            }

        }}.toString() + " ORDER BY c.id ASC LIMIT #{pageSize}";
    }
} 