package com.chua.starter.queue.annotation;

import java.lang.annotation.*;

/**
 * 消息监听注解
 * <p>
 * 标注在方法上，用于监听指定目标地址的消息。
 * </p>
 *
 * <pre>
 * {@code
 * @Component
 * public class OrderMessageHandler {
 *
 *     @OnMessage("order-topic")
 *     public void handleOrder(Message message) {
 *         // 处理消息
 *     }
 *
 *     @OnMessage(value = "payment-queue", group = "payment-group")
 *     public void handlePayment(PaymentDTO payment) {
 *         // 直接接收反序列化后的对象
 *     }
 * }
 * }
 * </pre>
 *
 * @author CH
 * @since 2025-12-25
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnMessage {

    /**
     * 目标地址（topic/queue）
     *
     * @return 目标地址
     */
    String value();

    /**
     * 消费组（Kafka/RocketMQ使用）
     *
     * @return 消费组名称
     */
    String group() default "";

    /**
     * 消息内容的目标类型
     * <p>
     * 如果指定，将自动将消息内容反序列化为该类型
     * </p>
     *
     * @return 目标类型
     */
    Class<?> payloadType() default Object.class;

    /**
     * 消息队列类型（用于区分不同的消息队列实现）
     * <p>
     * 如果不指定，将使用默认配置的消息队列
     * </p>
     *
     * @return 消息队列类型
     */
    String type() default "";

    /**
     * 是否自动确认消息
     *
     * @return 是否自动确认
     */
    boolean autoAck() default true;

    /**
     * 并发消费者数量
     *
     * @return 并发数
     */
    int concurrency() default 1;
}
