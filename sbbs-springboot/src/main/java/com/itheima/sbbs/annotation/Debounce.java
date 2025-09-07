package com.itheima.sbbs.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防抖注解
 * 用于防止用户频繁操作
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Debounce {

    /**
     * 防抖时间（秒）
     * 默认2秒
     */
    long timeout() default 2;

    /**
     * 防抖键的前缀
     * 默认使用方法名
     */
    String keyPrefix() default "";

    /**
     * 错误消息
     */
    String message() default "操作过于频繁，请稍后再试";

    /**
     * 是否包含用户ID在防抖键中
     * 默认true，表示每个用户独立防抖
     */
    boolean includeUserId() default true;
}
