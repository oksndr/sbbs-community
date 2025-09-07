package com.itheima.sbbs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.sbbs.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
    
    /**
     * 分页查询通知列表，包含发送者用户名、帖子标题、评论内容预览等信息
     * @param page 分页对象
     * @param receiverId 接收者ID
     * @param onlyUnread 是否只查询未读
     * @return 通知列表
     */
    @Select({
        "<script>",
        "SELECT n.*, ",
        "u.username as sender_username, ",
        "u.avatar as sender_avatar, ",
        "CASE ",
        "  WHEN n.related_type = '1' THEN p.title ",
        "  WHEN n.related_type = '2' THEN CASE ",
        "    WHEN LENGTH(parent_c.content) > 50 THEN CONCAT(SUBSTRING(REPLACE(REPLACE(parent_c.content, CHR(10), ' '), CHR(13), ' '), 1, 50), '...') ",
        "    ELSE REPLACE(REPLACE(parent_c.content, CHR(10), ' '), CHR(13), ' ') ",
        "  END ",
        "  ELSE NULL ",
        "END as related_title, ",
        "CASE ",
        "  WHEN n.notification_type IN (1, 2, 3, 4) THEN ",
        "    CASE ",
        "      WHEN n.notification_type = 4 AND trigger_c.content LIKE '回复%:%' THEN ",
        "        CASE ",
        "          WHEN LENGTH(SUBSTRING(trigger_c.content, POSITION(':' IN trigger_c.content) + 1)) > 50 THEN ",
        "            CONCAT(SUBSTRING(REPLACE(REPLACE(SUBSTRING(trigger_c.content, POSITION(':' IN trigger_c.content) + 1), CHR(10), ' '), CHR(13), ' '), 1, 50), '...') ",
        "          ELSE REPLACE(REPLACE(SUBSTRING(trigger_c.content, POSITION(':' IN trigger_c.content) + 1), CHR(10), ' '), CHR(13), ' ') ",
        "        END ",
        "      ELSE ",
        "        CASE ",
        "          WHEN LENGTH(trigger_c.content) > 50 THEN CONCAT(SUBSTRING(REPLACE(REPLACE(trigger_c.content, CHR(10), ' '), CHR(13), ' '), 1, 50), '...') ",
        "          ELSE REPLACE(REPLACE(trigger_c.content, CHR(10), ' '), CHR(13), ' ') ",
        "        END ",
        "    END ",
        "  ELSE NULL ",
        "END as comment_preview ",
        "FROM notification n ",
        "LEFT JOIN \"user\" u ON n.sender_id = u.id AND u.deleted = 0 ",
        "LEFT JOIN post p ON n.related_type = '1' AND n.related_id = p.id AND p.deleted = 0 ",
        "LEFT JOIN comment parent_c ON n.related_type = '2' AND n.related_id = parent_c.id AND parent_c.deleted = 0 ",
        "LEFT JOIN comment trigger_c ON n.notification_type IN (1, 2, 3, 4) AND n.trigger_entity_id = trigger_c.id AND trigger_c.deleted = 0 ",
        "WHERE n.receiver_id = #{receiverId} AND n.deleted = 0 ",
        "<if test='onlyUnread != null and onlyUnread == true'>",
        "  AND n.is_read = false ",
        "</if>",
        "ORDER BY n.created DESC",
        "</script>"
    })
    Page<Notification> getNotificationList(Page<Notification> page, 
                                          @Param("receiverId") Integer receiverId, 
                                          @Param("onlyUnread") Boolean onlyUnread);
}
