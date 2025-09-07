package com.itheima.sbbs.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 基础类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BasePojo implements Serializable {
    private static final long serialVersionUID = 1L;//序列化uid
    @TableField(fill = FieldFill.INSERT, value = "created")
    private Date created;
    @TableField(fill = FieldFill.INSERT_UPDATE, value = "updated")
    private Date updated;
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
