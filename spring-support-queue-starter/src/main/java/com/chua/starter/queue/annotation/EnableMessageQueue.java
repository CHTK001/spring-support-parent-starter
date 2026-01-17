package com.chua.starter.queue.annotation;

import com.chua.starter.queue.configuration.QueueAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用消息队列功能
 * <p>
 * 在Spring Boot应用的配置类上添加此注解以启用消息队列功能。
 * </p>
 *
 * <pre>
 * {@code
 * @SpringBootApplication
 * @EnableMessageQueue
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }
 * </pre>
 *
 * @author CH
 * @since 2025-12-25
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(QueueAutoConfiguration.class)
public @interface EnableMessageQueue {
}
