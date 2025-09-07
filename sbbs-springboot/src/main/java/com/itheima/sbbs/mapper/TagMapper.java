package com.itheima.sbbs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itheima.sbbs.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {
    // 可以根据需要添加自定义的 Mapper 方法
} 