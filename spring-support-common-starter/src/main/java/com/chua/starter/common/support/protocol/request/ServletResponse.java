package com.chua.starter.common.support.protocol.request;

/** @author CH */
public class ServletResponse {

    /** Success */
    private final boolean success;
    /** 消息 */
    private final String message;

    /**
     * 创建 ServletResponse 实例
     * @param success success
     * @param String String
     */
    private ServletResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    /** 是否Success */
    public boolean isSuccess() {
        return success;
    }

    /** 获取Message */
    public String getMessage() {
        return message;
    }

    /** Ok */
    public static ServletResponse ok() {
        return new ServletResponse(true, null);
    }

    /** 记录错误 */
    public static ServletResponse error(String msg) {
        return new ServletResponse(false, msg);
    }
}

