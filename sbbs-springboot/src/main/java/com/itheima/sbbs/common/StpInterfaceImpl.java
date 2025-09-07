package com.itheima.sbbs.common;

import cn.dev33.satoken.stp.StpInterface;
//import com.itheima.sbbs.service.UserService;
import com.itheima.sbbs.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    /**
     * 查询角色
     * @param loginId
     * @param loginType
     * @return
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        //转换成Integer
        Integer userId = Integer.valueOf(loginId.toString());
        String role = userMapper.getRole(userId);
        List<String> list = new ArrayList<>();
        list.add(role);
        return list;
    }
}
