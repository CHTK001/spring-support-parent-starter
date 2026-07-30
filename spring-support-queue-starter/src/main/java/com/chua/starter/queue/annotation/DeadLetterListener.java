package com.chua.starter.queue.annotation;

import java.lang.annotation.*;

/**
 * 死信队列监听注解
 * <p>
 * 标注在方法上，用于监听死信队列消息。
 * 当消息处理失败达到最大重试次数后，会被发送到死信队列。
 * </p>
 *
 * <pre>
 * {@code
 * @Component
 * public class DeadLetterHandler {
 *
 *     @DeadLetterListener("payment.order.created.dlq")
 *     public void handleDeadLetter(Message message) {
 *         // 处理死信消息
 *         log.error("消息处理失败: {}", message.getPayloadAsString());
 *     }
 * }
 * }
 * </pre>
 *
 * @author CH
 * @since 2025-03-20
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeadLetterListener {

    /**
     * 死信队列地址
     *
     * @return 死信队列地址
     */
    String value();

    /**
     * 消费组
     *
     * @return 消费组名称
     */
    String group() default "";

    /**
     * 消息队列类型
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
