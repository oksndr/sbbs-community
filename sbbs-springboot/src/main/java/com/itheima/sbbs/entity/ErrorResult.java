package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorResult {

    private String status;
    private String message;

    public static ErrorResult RegisterError() {
        return ErrorResult.builder().status("00001").message("注册失败, 用户名已存在").build();
    }

    public static ErrorResult numError() {
        return ErrorResult.builder().status("00002").message("请勿恶意调试接口").build();
    }

    public static ErrorResult likeError() {
        return ErrorResult.builder().status("00003").message("你已经点过赞了哦~").build();
    }

    public static ErrorResult cancelLikeError() {
        return ErrorResult.builder().status("00004").message("你并没有点过赞哦~ 何来的取消点赞?").build();
    }

    public static ErrorResult dislikeError() {
        return ErrorResult.builder().status("00005").message("你已经点过踩了哦~").build();
    }

    public static ErrorResult cancelDislikeError() {
        return ErrorResult.builder().status("00006").message("你并没有点过踩哦~ 何来的取消点踩?").build();
    }


    public static ErrorResult userNotExistError() {
        return ErrorResult.builder().status("10008").message("用户不存在").build();
    }

    public static ErrorResult systemBusyError() {
        return ErrorResult.builder().status("10010").message("系统繁忙，请稍后重试").build();
    }

}
