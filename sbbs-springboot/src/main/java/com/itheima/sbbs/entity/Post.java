package com.itheima.sbbs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("post")
public class Post extends BasePojo{
    private Integer id;
    private Integer userId;
    private String title;
    private String content;
    private Integer likeCount;
    private Integer dislikeCount;
    private Integer commentCount;

    // 用于存储标签ID列表的字符串 (映射数据库)
    private String tagIdsString;

    // 用于接收前端传递的标签ID列表 (不映射数据库)
    @TableField(exist = false)
    private List<Integer> tagIds;
    
    // 移除毫秒级时间戳字段，因为数据库已改为秒级精度
    // @TableField(exist = false)
    // private Long createdMillis;
    
    // @TableField(exist = false)
    // private Long updatedMillis;
}
