package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//注册时需要填写的信息
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterDto {
    public String username;
    public String password;
    public String email;
    public String verificationCode;//验证码
    public String avatar;//头像对应的链接
    public String uuid;//验证码的uuid
}
