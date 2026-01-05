package com.chua.starter.common.support.configuration.environment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 虚拟线程环境后处理器
 * <p>
 * 当 plugin.virtual-thread.enabled=true 时，自动设置 Spring Boot 虚拟线程相关配置
 * </p>
 * 
 * @author CH
 * @since 4.0.0
 */
public class VirtualThreadEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "virtualThreadProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String enabled = environment.getProperty("plugin.virtual-thread.enabled", "false");
        
        if (!"true".equalsIgnoreCase(enabled)) {
            return;
        }

        Map<String, Object> properties = new HashMap<>();
        
        // 启用 Spring Boot 虚拟线程支持
        properties.put("spring.threads.virtual.enabled", "true");
        
        // 设置 Tomcat 相关配置（可选优化）
        String webEnabled = environment.getProperty("plugin.virtual-thread.web-enabled", "true");
        if ("true".equalsIgnoreCase(webEnabled)) {
            // 虚拟线程模式下，可以增加最大连接数，因为线程不再是瓶颈
            properties.putIfAbsent("server.tomcat.max-connections", "10000");
            // 增加接受队列长度
            properties.putIfAbsent("server.tomcat.accept-count", "1000");
        }

        // 添加到环境中，但优先级低于用户配置
        environment.getPropertySources().addLast(
                new MapPropertySource(PROPERTY_SOURCE_NAME, properties)
        );
    }

    @Override
    public int getOrder() {
        // 在其他 PostProcessor 之后执行，确保用户配置优先
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
