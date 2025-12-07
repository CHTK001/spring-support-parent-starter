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
public class RuntimeMessageException extends RuntimeException {

    public ResultCode resultCode;

    public RuntimeMessageException(ResultCode errorCode) {
        super(errorCode.getMsg());
        this.resultCode = errorCode;
    }

    public RuntimeMessageException(String message) {
        super(message);
    }

    public RuntimeMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public RuntimeMessageException(Throwable cause) {
        super(cause);
    }


}

