package com.itheima.sbbs.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于 @ 提及用户时返回的简单用户信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSimpleDto {
    private Integer id;
    private String username;
    private String avatar;
} 