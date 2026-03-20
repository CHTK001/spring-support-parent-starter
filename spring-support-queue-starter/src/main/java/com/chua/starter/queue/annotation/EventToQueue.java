package com.chua.starter.queue.annotation;

import java.lang.annotation.*;

/**
 * 事件转队列注解
 * <p>
 * 标注在Spring ApplicationEvent类上，自动将该事件发送到指定的队列。
 * </p>
 *
 * <pre>
 * {@code
 * @EventToQueue("payment.order.created")
 * public class OrderCreatedEvent extends ApplicationEvent {
 *     // ...
 * }
 * }
 * </pre>
 *
 * @author CH
 * @since 2025-03-20
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventToQueue {

    /**
     * 目标队列地址（topic/queue）
     *
     * @return 目标地址
     */
    String value();

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
     * 是否异步发送
     *
     * @return 是否异步发送
     */
    boolean async() default true;

    /**
     * 消息头（key=value格式，多个用逗号分隔）
     * <p>
     * 例如: "priority=high,retry=3"
     * </p>
     *
     * @return 消息头
     */
    String[] headers() default {};
}
