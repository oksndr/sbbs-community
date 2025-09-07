package com.itheima.sbbs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.sbbs.entity.Tag;

import java.util.List;

public interface TagService extends IService<Tag> {
    // 添加获取所有标签的方法声明
    List<Tag> getAllTags();
} 