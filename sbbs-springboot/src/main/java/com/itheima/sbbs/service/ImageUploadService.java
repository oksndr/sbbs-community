package com.itheima.sbbs.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
 
public interface ImageUploadService {
    Map<String, Object> uploadImage(MultipartFile file);
} 