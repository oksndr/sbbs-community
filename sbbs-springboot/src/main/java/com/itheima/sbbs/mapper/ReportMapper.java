package com.itheima.sbbs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itheima.sbbs.entity.Report;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReportMapper extends BaseMapper<Report> {
    // 可以根据需要添加自定义的 Mapper 方法
} 