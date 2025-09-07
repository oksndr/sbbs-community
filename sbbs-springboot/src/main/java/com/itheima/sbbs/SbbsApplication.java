package com.itheima.sbbs;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@Slf4j
@SpringBootApplication(scanBasePackages = {"com.itheima.sbbs"})
@ServletComponentScan
@EnableTransactionManagement
@EnableCaching
@EnableAspectJAutoProxy
@EnableConfigurationProperties
@EnableAsync
@EnableScheduling
@MapperScan("com.itheima.sbbs.mapper")
public class SbbsApplication {
    public static void main(String[] args) {
        log.info("sbbs论坛程序正在启动...");
        SpringApplication.run(SbbsApplication.class, args);
        log.info("sbbs论坛程序启动成功!");
    }
}
