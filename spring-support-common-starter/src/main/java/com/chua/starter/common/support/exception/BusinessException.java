package com.chua.starter.common.support.exception;

import com.chua.common.support.lang.code.ResultCode;
import lombok.Getter;

/**
 * 自定义业务异�?
 *
 * @author CH
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

