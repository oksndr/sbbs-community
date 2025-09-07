package com.itheima.sbbs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子和标签关联实体类 (中间表)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("post_tag") // 假设中间表名为 post_tag
public class PostTag extends BasePojo {
    private Integer id;
    private Integer postId; // 帖子ID
    private Integer tagId; // 标签ID
} 