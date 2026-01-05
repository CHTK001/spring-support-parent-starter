package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 虚拟线程配置属性
 * <p>
 * 启用后，Spring Boot 会自动将以下组件切换为虚拟线程：
 * <ul>
 *     <li>Tomcat/Jetty/Undertow 请求处理线程</li>
 *     <li>@Async 异步任务执行器</li>
 *     <li>Spring MVC 异步请求处理</li>
 *     <li>Spring WebFlux 阻塞操作</li>
 * </ul>
 * </p>
 * 
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = VirtualThreadProperties.PREFIX, ignoreInvalidFields = true)
public class VirtualThreadProperties {

    public static final String PREFIX = "plugin.virtual-thread";

    /**
     * 是否启用虚拟线程
     * <p>
     * 启用后会自动配置：
     * <ul>
     *     <li>spring.threads.virtual.enabled=true</li>
     *     <li>Tomcat 使用虚拟线程执行器</li>
     *     <li>异步任务使用虚拟线程</li>
     * </ul>
     * </p>
     */
    private boolean enabled = false;

    /**
     * 虚拟线程名称前缀
     */
    private String threadNamePrefix = "virtual-";

    /**
     * 是否为 @Async 任务启用虚拟线程
     */
    private boolean asyncEnabled = true;

    /**
     * 是否为 @Scheduled 任务启用虚拟线程
     */
    private boolean scheduledEnabled = true;

    /**
     * 是否为 Web 容器（Tomcat/Jetty/Undertow）启用虚拟线程
     */
    private boolean webEnabled = true;
}
