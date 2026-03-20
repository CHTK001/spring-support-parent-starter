package com.chua.starter.queue.converter;

import com.chua.starter.queue.Message;

/**
 * 消息转换器接口
 * <p>
 * 用于在消息发送前和接收后进行格式转换，支持：
 * - 消息压缩/解压
 * - 消息加密/解密
 * - 消息编码转换
 * - 自定义序列化
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
public interface MessageConverter {

    /**
     * 发送前转换
     * <p>
     * 在消息发送到队列之前调用
     * </p>
     *
     * @param message 原始消息
     * @return 转换后的消息
     */
    Message convertBeforeSend(Message message);

    /**
     * 接收后转换
     * <p>
     * 在消息从队列接收后调用
     * </p>
     *
     * @param message 接收到的消息
     * @return 转换后的消息
     */
    Message convertAfterReceive(Message message);

    /**
     * 是否支持该消息
     *
     * @param message 消息
     * @return 是否支持
     */
    default boolean supports(Message message) {
        return true;
    }

    /**
     * 转换器优先级
     * <p>
     * 数值越小优先级越高，默认为0
     * </p>
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }
}
