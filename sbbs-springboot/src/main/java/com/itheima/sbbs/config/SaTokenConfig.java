package com.itheima.sbbs.config;

import cn.dev33.satoken.interceptor.SaAnnotationInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    private static final Logger log = LoggerFactory.getLogger(SaTokenConfig.class);

    /**
     * 添加saToken拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaAnnotationInterceptor()).addPathPatterns("/**");
    }


    /**
     * 放行静态文件
     * 由于前端部署在nginx上 这段代码暂时失效
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("开始映射静态资源");
        registry.addResourceHandler("/backend/**").addResourceLocations("classpath:/backend/");
        log.info("静态资源映射完毕...");
    }

}
