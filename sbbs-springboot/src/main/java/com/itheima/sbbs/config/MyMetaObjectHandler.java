package com.itheima.sbbs.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    /**
     * 插入
     * @param metaObject
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Object created = getFieldValByName("created", metaObject);
        //检测是否为空: 是的话可以插入数据
        if (created == null) {
            this.setFieldValByName("created", new Date(), metaObject);
        }
        Object updated = getFieldValByName("updated", metaObject);
        //检测是否为空: 是的话可以插入数据
        if (updated == null) {
            this.setFieldValByName("updated", new Date(), metaObject);
        }

    }

    /**
     * 更新
     * @param metaObject
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        Object updated = getFieldValByName("updated", metaObject);
        //检测是否为空: 是的话可以插入数据
        if (updated == null) {
            this.setFieldValByName("updated", new Date(), metaObject);
        }
    }
}
