package com.chua.starter.common.support.protocol.request;

public class ServletResponse {

    private final boolean success;
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
