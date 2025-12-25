package com.chua.starter.queue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 统一消息模板接口
 * <p>
 * 提供统一的消息队列操作API，支持MQTT、Kafka、RabbitMQ、RocketMQ等实现。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-25
 */
public interface MessageTemplate {

    /**
     * 同步发送消息
     *
     * @param destination 目标地址（topic/queue）
     * @param payload     消息内容
     * @return 发送结果
     */
    SendResult send(String destination, Object payload);

    /**
     * 异步发送消息
     *
     * @param destination 目标地址
     * @param payload     消息内容
     * @return 异步发送结果
     */
    CompletableFuture<SendResult> sendAsync(String destination, Object payload);

    /**
     * 发送带Header的消息
     *
     * @param destination 目标地址
     * @param payload     消息内容
     * @param headers     消息头
     * @return 发送结果
     */
    SendResult send(String destination, Object payload, Map<String, Object> headers);

    /**
     * 发送延迟消息
     * <p>
     * 注意：并非所有消息队列都支持延迟消息，不支持的实现可能会抛出UnsupportedOperationException
     * </p>
     *
     * @param destination 目标地址
     * @param payload     消息内容
     * @param delay       延迟时间
     * @return 发送结果
     */
    default SendResult sendDelayed(String destination, Object payload, Duration delay) {
        throw new UnsupportedOperationException("Delayed message is not supported by this implementation");
    }

    /**
     * 订阅消息
     *
     * @param destination 目标地址
     * @param handler     消息处理器
     */
    void subscribe(String destination, MessageHandler handler);

    /**
     * 订阅消息（指定消费组）
     *
     * @param destination 目标地址
     * @param group       消费组
     * @param handler     消息处理器
     */
    default void subscribe(String destination, String group, MessageHandler handler) {
        subscribe(destination, handler);
    }

    /**
     * 取消订阅
     *
     * @param destination 目标地址
     */
    void unsubscribe(String destination);

    /**
     * 获取消息队列类型
     *
     * @return 类型名称（mqtt/kafka/rabbitmq/rocketmq）
     */
    String getType();

    /**
     * 检查连接状态
     *
     * @return 是否已连接
     */
    boolean isConnected();

    /**
     * 关闭连接
     */
    void close();
}
