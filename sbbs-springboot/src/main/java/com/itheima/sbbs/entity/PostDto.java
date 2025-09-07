package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用于显示post列表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostDto {
    private List<PostWithUserDto> list;
    private LocalDateTime lastUpdated;
    private Integer lastId;
    
    // 🚀 新增：是否有下一页（用于替代COUNT查询）
    private Boolean hasNextPage;
}
