package com.itheima.sbbs.service.impl;

import com.itheima.sbbs.service.ImageUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ImageUploadServiceImpl implements ImageUploadService {

    @Value("${image.upload.url}")
    private String uploadUrl;

    @Value("${image.upload.token}")
    private String uploadToken;

    private final RestTemplate restTemplate;

    public ImageUploadServiceImpl() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public Map<String, Object> uploadImage(MultipartFile file) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        try {
            // 配置检查
            if (uploadUrl == null || uploadUrl.isEmpty()) {
                log.error("图片上传URL未配置");
                errorResponse.put("result", "failed");
                errorResponse.put("message", "图片上传服务配置错误: URL未配置");
                return errorResponse;
            }
            
            if (uploadToken == null || uploadToken.isEmpty()) {
                log.error("图片上传Token未配置");
                errorResponse.put("result", "failed");
                errorResponse.put("message", "图片上传服务配置错误: Token未配置");
                return errorResponse;
            }
            
            // 文件检查
            if (file == null) {
                log.error("上传的文件为null");
                errorResponse.put("result", "failed");
                errorResponse.put("message", "没有接收到上传文件");
                return errorResponse;
            }
            
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "未命名文件";
            log.info("开始上传图片: {}, 大小: {} bytes", fileName, file.getSize());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("token", uploadToken);
            body.add("image", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("发送图片上传请求到: {}", uploadUrl);
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(
                    uploadUrl, requestEntity, Map.class);

            if (responseEntity.getStatusCode() != HttpStatus.OK) {
                log.warn("图片上传服务返回非200状态码: {}", responseEntity.getStatusCode());
                errorResponse.put("result", "failed");
                errorResponse.put("message", "图片上传服务返回错误状态: " + responseEntity.getStatusCode());
                return errorResponse;
            }
            
            Map<String, Object> responseBody = responseEntity.getBody();
            if (responseBody == null) {
                log.warn("图片上传服务返回空响应");
                errorResponse.put("result", "failed");
                errorResponse.put("message", "图片上传服务返回空响应");
                return errorResponse;
            }
            
            log.info("图片上传完成: {}, 结果: {}", fileName, responseBody.get("result"));
            return responseBody;

        } catch (RestClientException e) {
            log.error("图片上传服务连接失败: {}", e.getMessage(), e);
            errorResponse.put("result", "failed");
            errorResponse.put("message", "图片上传服务连接失败: " + e.getMessage());
            return errorResponse;
        } catch (Exception e) {
            log.error("图片上传过程发生异常: {}", e.getMessage(), e);
            errorResponse.put("result", "failed");
            errorResponse.put("message", "图片上传失败: " + e.getMessage());
            return errorResponse;
        }
    }
} 