package com.chua.starter.queue;

import lombok.Builder;
import lombok.Data;

/**
 * 消息发送结果
 *
 * @author CH
 * @since 2025-12-25
 */
@Data
@Builder
public class SendResult {

    /**
     * 是否发送成功
     */
    private boolean success;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 目标地址
     */
    private String destination;

    /**
     * 发送时间戳
     */
    private long timestamp;

    /**
     * 错误信息（如果失败）
     */
    private Throwable error;

    /**
     * 额外信息
     */
    private Object extra;

    /**
     * 创建成功结果
     *
     * @param messageId   消息ID
     * @param destination 目标地址
     * @return 发送结果
     */
    public static SendResult success(String messageId, String destination) {
        return SendResult.builder()
                .success(true)
                .messageId(messageId)
                .destination(destination)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建失败结果
     *
     * @param destination 目标地址
     * @param error       错误信息
     * @return 发送结果
     */
    public static SendResult failure(String destination, Throwable error) {
        return SendResult.builder()
                .success(false)
                .destination(destination)
                .timestamp(System.currentTimeMillis())
                .error(error)
                .build();
    }
}
