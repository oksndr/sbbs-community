package com.itheima.sbbs.controller;

import cn.dev33.satoken.util.SaResult;
import com.itheima.sbbs.entity.Tag;
import com.itheima.sbbs.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tags") // 为标签相关的接口设置基础路径
public class TagController {

    @Autowired
    private TagService tagService;

    /**
     * 获取所有可用标签列表
     */
    @GetMapping
    public SaResult getAllTags() {
        List<Tag> tags = tagService.getAllTags();
        return SaResult.code(200).data(tags);
    }
} 