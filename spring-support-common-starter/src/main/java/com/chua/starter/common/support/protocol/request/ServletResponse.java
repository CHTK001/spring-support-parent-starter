package com.chua.starter.common.support.protocol.request;

/** @作者 CH */
public class ServletResponse {

    /** 成功 */
    private final boolean success;
    /** 消息 */
    private final String message;

    /**
      * 创建 servlet响应 实例
     * @param success 成功
     * @param message 字符串
     * @param message 消息
     */
    private ServletResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    /**
     * 是否成功
     *
     * @return 是否成功的结果
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取消息
     *
     * @return 获取消息的结果
     */
    public String getMessage() {
        return message;
    }

    /**
     * Ok
     *
     * @return ok的结果
     */
    public static ServletResponse ok() {
        /**
         * servlet响应。
         * @param true true
         * @param null 空
         */
        return new ServletResponse(true, null);
    }

    /**
     * 记录错误
     *
     * @param msg msg
     * @return 错误的结果
     */
    public static ServletResponse error(String msg) {
        /**
         * servlet响应。
         * @param false false
         * @param msg msg
         */
        return new ServletResponse(false, msg);
    }
}

