package com.itheima.sbbs.aspect;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.itheima.sbbs.annotation.Debounce;
import com.itheima.sbbs.util.DebounceUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 防抖切面
 */
@Aspect
@Component
@Slf4j
public class DebounceAspect {

    @Autowired
    private DebounceUtil debounceUtil;

    @Around("@annotation(com.itheima.sbbs.annotation.Debounce)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Debounce debounce = method.getAnnotation(Debounce.class);

        // 构建防抖键
        String debounceKey = buildDebounceKey(joinPoint, debounce);

        // 尝试获取防抖锁
        if (!debounceUtil.tryLock(debounceKey, debounce.timeout())) {
            log.info("防抖拦截: 方法 {} 操作过于频繁，防抖键: {}", method.getName(), debounceKey);
            return SaResult.error(debounce.message());
        }

        try {
            // 执行原方法
            Object result = joinPoint.proceed();
            log.debug("防抖通过: 方法 {} 执行成功，防抖键: {}", method.getName(), debounceKey);
            return result;
        } catch (Exception e) {
            // 区分业务异常和系统异常
            if (e.getMessage() != null && (
                e.getMessage().contains("已经点过赞") ||
                e.getMessage().contains("已经点过踩") ||
                e.getMessage().contains("取消点赞") ||
                e.getMessage().contains("取消点踩"))) {
                // 业务异常使用WARN级别
                log.warn("防抖方法业务异常: 方法 {} 执行异常，防抖键: {}, 错误: {}",
                        method.getName(), debounceKey, e.getMessage());
            } else {
                // 系统异常使用ERROR级别
                log.error("防抖方法系统异常: 方法 {} 执行异常，防抖键: {}, 错误: {}",
                         method.getName(), debounceKey, e.getMessage(), e);
            }
            // 操作失败时释放防抖锁，允许重试
            debounceUtil.releaseLock(debounceKey);
            throw e;
        }
    }

    /**
     * 构建防抖键
     */
    private String buildDebounceKey(ProceedingJoinPoint joinPoint, Debounce debounce) {
        StringBuilder keyBuilder = new StringBuilder();

        // 添加前缀
        String keyPrefix = debounce.keyPrefix();
        if (keyPrefix.isEmpty()) {
            // 如果没有指定前缀，使用方法名
            keyPrefix = joinPoint.getSignature().getName();
        }
        keyBuilder.append(keyPrefix);

        // 添加用户ID
        if (debounce.includeUserId() && StpUtil.isLogin()) {
            keyBuilder.append(":user:").append(StpUtil.getLoginIdAsInt());
        }

        // 添加方法参数（如果有PathVariable参数）
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg != null && (arg instanceof Integer || arg instanceof Long || arg instanceof String)) {
                    keyBuilder.append(":").append(arg);
                    break; // 只取第一个简单类型参数
                }
            }
        }

        return keyBuilder.toString();
    }


}
