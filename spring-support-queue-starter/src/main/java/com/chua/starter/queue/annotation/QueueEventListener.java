package com.chua.starter.queue.annotation;

import java.lang.annotation.*;

/**
 * 队列事件监听注解
 * <p>
   * 标注在方法上，用于监听队列消息并自动转换为Spring application事件。
   * 与@队列监听器不同，此注解会将消息反序列化为application事件并发布到Spring事件总线。
 * </p>
 *
 * <pre>
 * {@code
 * @Component
 * public class PaymentEventHandler {
 *
 *     @QueueEventListener(value = "payment.order.created", eventType = OrderCreatedEvent.class)
 *     public void handleOrderCreated(OrderCreatedEvent event) {
 *         // 处理事件
 *     }
 * }
 * }
 * </pre>erCreated(OrderCreatedEvent event) {
 *         // 处理事件
 *     }
 * }
 * }
 * </pre>
 *
 * @since 2025-03-20
 * @author CH
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueueEventListener {

    /**
      * 目标地址（topic/队列）
     *
     * @return 目标地址
     */
    String value();

    /**
     * 事件类型
     * <p>
      * 消息将被反序列化为此类型的application事件
     * </p>
     *
     * @return 事件类型
     */
    Class<? extends org.springframework.context.ApplicationEvent> eventType();

    /**
       * 消费组（Kafka/RocketMQ使用）
     *
     * @return 消费组名称
     */
    String group() default "";

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

    /**
     * 是否重新发布到Spring事件总线
     * <p>
      * 如果为true，将消息反序列化为application事件后重新发布到Spring事件总线，
      * 这样其他@事件监听器也能接收到该事件
     * </p>
     *
     * @return 是否重新发布
     */
    boolean republish() default false;
}
