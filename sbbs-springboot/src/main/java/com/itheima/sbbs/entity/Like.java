package com.itheima.sbbs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点赞表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("\"like\"")
public class Like extends BasePojo{
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Integer id;
    private Integer userId;
    private Integer postId;
    private Integer commentId;
    private Integer type;//1 表示点赞，-1 表示点踩
}
