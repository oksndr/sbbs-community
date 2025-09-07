package com.itheima.sbbs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 标签实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tag") // 假设表名为 tag
@AllArgsConstructor
@NoArgsConstructor
public class Tag extends BasePojo {
    private Integer id;
    private String name; // 标签名称
} 