package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.itheima.sbbs.service.PostService;
import com.itheima.sbbs.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/search") // 为搜索相关的接口设置基础路径
public class SearchController {

    @Autowired
    private PostService postService;

    @Autowired
    private UserService userService;

    /**
     * 通用搜索接口（支持分页）
     * @param type 搜索类型 (post 或 user)
     * @param keyword 搜索关键词
     * @param page 页码，默认1
     * @param pageSize 每页数量，默认10
     * @return 搜索结果列表（包含分页信息）
     */
    @SaCheckLogin
    @GetMapping
    public SaResult search(@RequestParam String type, 
                          @RequestParam String keyword,
                          @RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer pageSize) {
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return SaResult.error("搜索关键词不能为空");
        }

        // 验证分页参数
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1 || pageSize > 50) { // 限制最大页面大小为50
            pageSize = 10;
        }

        if ("post".equalsIgnoreCase(type)) {
            // 使用MyBatis-Plus分页搜索帖子
            IPage<?> pageResult = postService.searchPostsWithPage(keyword, page, pageSize);
            
            // 构建分页响应
            Map<String, Object> response = new HashMap<>();
            response.put("list", pageResult.getRecords());
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("total", pageResult.getTotal()); // 总记录数
            response.put("totalPages", pageResult.getPages()); // 总页数
            response.put("hasNextPage", page < pageResult.getPages()); // 是否有下一页
            response.put("hasPrevPage", page > 1); // 是否有上一页
            
            return SaResult.code(200).data(response);
        } else if ("user".equalsIgnoreCase(type)) {
            // 使用MyBatis-Plus分页搜索用户
            IPage<?> pageResult = userService.searchUsersWithPage(keyword, page, pageSize);
            
            // 构建分页响应
            Map<String, Object> response = new HashMap<>();
            response.put("list", pageResult.getRecords());
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("total", pageResult.getTotal()); // 总记录数
            response.put("totalPages", pageResult.getPages()); // 总页数
            response.put("hasNextPage", page < pageResult.getPages()); // 是否有下一页
            response.put("hasPrevPage", page > 1); // 是否有上一页
            
            return SaResult.code(200).data(response);
        } else {
            return SaResult.error("无效的搜索类型");
        }
    }
} 