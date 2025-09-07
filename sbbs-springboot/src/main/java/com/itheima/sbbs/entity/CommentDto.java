package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import com.itheima.sbbs.entity.CommentWithUserDto;

/**
 * 用于查询顶级评论
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentDto implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private List<CommentWithUserDto> comments;
    private Integer lastId; // 用于游标分页
    
    // 以下为标准分页属性
    private Integer total; // 总记录数
    private Integer size; // 每页大小
    private Integer current; // 当前页码
    private Integer pages; // 总页数
    private Boolean hasNext; // 是否有下一页
    private Boolean hasPrevious; // 是否有上一页
}
