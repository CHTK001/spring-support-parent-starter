package com.chua.starter.sync.data.support.sync.transformer;

/**
 * 转换异常
 */
public class TransformException extends RuntimeException {
    
    public TransformException(String message) {
        super(message);
    }
    
    public TransformException(String message, Throwable cause) {
        super(message, cause);
    }
}
