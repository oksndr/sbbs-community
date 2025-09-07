package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserFollowDto {

    private Integer id; // 用户ID
    private String username; // 用户名
    private String avatar; // 用户头像

} 