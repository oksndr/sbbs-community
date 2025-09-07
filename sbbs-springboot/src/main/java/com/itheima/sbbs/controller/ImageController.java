package com.itheima.sbbs.controller;

import cn.dev33.satoken.util.SaResult;
import com.itheima.sbbs.service.ImageUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/v1/image") // 使用 /v1 前缀，并定义图片相关的路径
public class ImageController {

    @Autowired
    private ImageUploadService imageUploadService;

    @PostMapping("/upload")
    public SaResult upload(@RequestParam("image") MultipartFile file) {
        if (file.isEmpty()) {
            return SaResult.error("请选择要上传的图片文件");
        }
        Map<String, Object> result = imageUploadService.uploadImage(file);
        
        // 添加对result为null的检查
        if (result == null) {
            return SaResult.error("图片上传服务异常，请稍后重试");
        }

        // 根据外部接口的响应结构判断是否成功，添加对result.get("result")为null的检查
        if (result.get("result") != null && "success".equals(result.get("result"))) {
            return SaResult.data(result);
        } else {
            // 可以根据需要细化错误处理和返回信息
            String errorMessage = result.get("message") != null ? result.get("message").toString() : "未知错误";
            return SaResult.error("图片上传失败: " + errorMessage);
        }
    }
} 