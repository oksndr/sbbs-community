package com.itheima.sbbs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

/**
 * 举报实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("report")
public class Report extends BasePojo {
    private Integer id;
    private Integer reporterId;
    private Integer reportedPostId;
    private String reason;
    private String status; // 举报处理状态 (e.g., pending, processed, rejected)
} 