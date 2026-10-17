package com.chua.starter.queue;

/**
 * 消息发送结果
 *
 * @param success     是否发送成功
 * @param messageId   消息标识
 * @param destination 目标地址
 * @param timestamp   发送时间戳
 * @param error       错误信息（仅失败时）
 * @param extra       附加信息
 * @since 2025-12-25
 * @author CH
 */
public record SendResult(
        boolean success,
        String messageId,
        String destination,
        long timestamp,
        Throwable error,
        Object extra) {

    /**
     * 构造成功结果
     *
     * @param messageId   消息标识
     * @param destination 目标地址
     * @return 发送结果
     */
    public static SendResult success(String messageId, String destination) {
        return new SendResult(true, messageId, destination, System.currentTimeMillis(), null, null);
    }

    /**
     * 构造失败结果
     *
     * @param destination 目标地址
     * @param error       错误信息
     * @return 发送结果
     */
    public static SendResult failure(String destination, Throwable error) {
        return new SendResult(false, null, destination, System.currentTimeMillis(), error, null);
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
     * 获取错误信息
     *
     * @return 获取错误的结果
     */
    public Throwable getError() {
        return error;
    }
}
