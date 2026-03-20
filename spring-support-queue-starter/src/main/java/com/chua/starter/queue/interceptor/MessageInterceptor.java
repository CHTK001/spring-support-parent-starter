package com.chua.starter.queue.interceptor;

import com.chua.starter.queue.Message;

/**
 * 消息拦截器接口
 * <p>
 * 用于在消息发送前和接收后进行拦截处理，支持：
 * - 消息加密/解密
 * - 消息签名/验签
 * - 消息过滤
 * - 消息转换
 * - 日志记录
 * - 性能监控
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
public interface MessageInterceptor {

    /**
     * 发送前拦截
     * <p>
     * 在消息发送到队列之前调用，可以修改消息内容、添加消息头等
     * </p>
     *
     * @param message 原始消息
     * @return 处理后的消息，返回null表示拦截该消息不发送
     */
    default Message beforeSend(Message message) {
        return message;
    }

    /**
     * 发送后回调
     * <p>
     * 在消息成功发送到队列后调用
     * </p>
     *
     * @param message 已发送的消息
     * @param success 是否发送成功
     * @param error   发送失败时的异常（成功时为null）
     */
    default void afterSend(Message message, boolean success, Throwable error) {
        // 默认不处理
    }

    /**
     * 接收前拦截
     * <p>
     * 在消息从队列接收后、处理前调用，可以修改消息内容、验证消息等
     * </p>
     *
     * @param message 接收到的消息
     * @return 处理后的消息，返回null表示拦截该消息不处理
     */
    default Message beforeReceive(Message message) {
        return message;
    }

    /**
     * 接收后回调
     * <p>
     * 在消息处理完成后调用
     * </p>
     *
     * @param message 已处理的消息
     * @param success 是否处理成功
     * @param error   处理失败时的异常（成功时为null）
     */
    default void afterReceive(Message message, boolean success, Throwable error) {
        // 默认不处理
    }

    /**
     * 拦截器优先级
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
