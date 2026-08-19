package com.chua.starter.common.support.protocol.request;

/** @author CH */
public class ServletResponse {

    /** Success */
    private final boolean success;
    /** 消息 */
    private final String message;

    private ServletResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public static ServletResponse ok() {
        return new ServletResponse(true, null);
    }

    public static ServletResponse error(String msg) {
        return new ServletResponse(false, msg);
    }
}

