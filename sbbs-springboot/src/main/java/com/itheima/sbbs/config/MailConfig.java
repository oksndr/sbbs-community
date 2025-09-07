package com.itheima.sbbs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "mail")
public class MailConfig {
    
    /**
     * SMTP服务器地址
     */
    private String host;
    
    /**
     * SMTP服务器端口
     */
    private Integer port;
    
    /**
     * 发送邮件的账号
     */
    private String username;
    
    /**
     * 发送邮件的密码/授权码
     */
    private String password;
    
    /**
     * 发件人显示名称
     */
    private String fromName;
    
    /**
     * 是否启用SSL
     */
    private Boolean sslEnabled = true;
    
    /**
     * 是否启用TLS
     */
    private Boolean tlsEnabled = false;
}