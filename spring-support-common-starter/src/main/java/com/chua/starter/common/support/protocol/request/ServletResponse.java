package com.chua.starter.common.support.protocol.request;

/**
 * 协议响应。
 *
 * @param success 成功
 * @param message 消息
 * @author CH
 * @since 4.0.0.51
 */
public record ServletResponse(boolean success, String message) {

    /**
     * 成功响应。
     *
     * @return 成功的结果
     */
    public static ServletResponse ok() {
        return new ServletResponse(true, null);
    }

    /**
     * 错误响应。
     *
     * @param msg 错误消息
     * @return 错误的结果
     */
    public static ServletResponse error(String msg) {
        return new ServletResponse(false, msg);
    }
}
