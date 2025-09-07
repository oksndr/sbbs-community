package com.itheima.sbbs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itheima.sbbs.entity.PostTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostTagMapper extends BaseMapper<PostTag> {
    // 可以根据需要添加自定义的 Mapper 方法，例如根据 postId 查询所有关联的 tagId
} 