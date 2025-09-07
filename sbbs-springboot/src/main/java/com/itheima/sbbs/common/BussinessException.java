package com.itheima.sbbs.common;

import com.itheima.sbbs.entity.ErrorResult;
import lombok.Data;

/**
 * 自定义业务异常
 */
@Data
public class BussinessException extends RuntimeException{
    private ErrorResult errorResult;

    public BussinessException(ErrorResult errorResult) {
        super(errorResult.getMessage());
        this.errorResult = errorResult;
    }
}
