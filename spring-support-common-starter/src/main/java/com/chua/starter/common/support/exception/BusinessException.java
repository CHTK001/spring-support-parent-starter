package com.chua.starter.common.support.exception;

import com.chua.starter.common.support.result.ResultCode;
import lombok.Getter;

/**
 * 自定义业务异常
 *
 * @author haoxr
 * @since 2022/7/31
 */
@Getter
public class BusinessException extends RuntimeException {

    public ResultCode resultCode;

    public BusinessException(ResultCode errorCode) {
        super(errorCode.getMsg());
        this.resultCode = errorCode;
    }

    public BusinessException(String message){
        super(message);
    }

    public BusinessException(String message, Throwable cause){
        super(message, cause);
    }

    public BusinessException(Throwable cause){
        super(cause);
    }


}
