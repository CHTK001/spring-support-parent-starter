package com.chua.starter.circuitbreaker.support;

import com.chua.starter.circuitbreaker.support.listener.CircuitBreakerStartupListener;
import com.chua.starter.circuitbreaker.support.properties.CircuitBreakerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 启动监听器测试
 * 
 * @author CH
 * @since 2024/12/20
 */
@SpringBootTest(classes = TestApplication.class)
@TestPropertySource(properties = {
    "plugin.circuit-breaker.enable=true",
    "plugin.circuit-breaker.rate-limiter.enable-management=true",
    "plugin.circuit-breaker.rate-limiter.management-path=/actuator/rate-limiter",
    "server.port=8080"
})
public class StartupListenerTest {

    @MockBean
    private Environment environment;

    @Test
    public void testStartupListener() {
        // 创建配置
        CircuitBreakerProperties properties = new CircuitBreakerProperties();
        properties.setEnable(true);
        
        CircuitBreakerProperties.RateLimiter rateLimiter = new CircuitBreakerProperties.RateLimiter();
        rateLimiter.setEnableManagement(true);
        rateLimiter.setManagementPath("/actuator/rate-limiter");
        properties.setRateLimiter(rateLimiter);

        // 模拟环境变量
        when(environment.getProperty("server.port", "8080")).thenReturn("8080");
        when(environment.getProperty("server.servlet.context-path", "")).thenReturn("");
        when(environment.getProperty("management.endpoints.web.base-path", "/actuator")).thenReturn("/actuator");
        when(environment.getProperty("springdoc.swagger-ui.enabled", "false")).thenReturn("false");

        // 创建监听器
        CircuitBreakerStartupListener listener = new CircuitBreakerStartupListener(properties, environment);

        // 创建事件
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);

        // 测试监听器不抛异常
        assertDoesNotThrow(() -> listener.onApplicationEvent(event), 
                          "启动监听器应该正常执行");

        // 验证环境变量被调用
        verify(environment, atLeastOnce()).getProperty("server.port", "8080");
    }

    @Test
    public void testStartupListenerWithDisabledFeature() {
        // 创建禁用的配置
        CircuitBreakerProperties properties = new CircuitBreakerProperties();
        properties.setEnable(false);

        // 创建监听器
        CircuitBreakerStartupListener listener = new CircuitBreakerStartupListener(properties, environment);

        // 创建事件
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);

        // 测试监听器不抛异常（应该直接返回）
        assertDoesNotThrow(() -> listener.onApplicationEvent(event), 
                          "禁用功能时启动监听器应该正常执行");

        // 验证环境变量没有被调用（因为功能被禁用）
        verify(environment, never()).getProperty(anyString(), anyString());
    }

    @Test
    public void testStartupListenerWithSwaggerEnabled() {
        // 创建配置
        CircuitBreakerProperties properties = new CircuitBreakerProperties();
        properties.setEnable(true);

        // 模拟启用Swagger的环境
        when(environment.getProperty("server.port", "8080")).thenReturn("8080");
        when(environment.getProperty("server.servlet.context-path", "")).thenReturn("");
        when(environment.getProperty("management.endpoints.web.base-path", "/actuator")).thenReturn("/actuator");
        when(environment.getProperty("springdoc.swagger-ui.enabled", "false")).thenReturn("true");

        // 创建监听器
        CircuitBreakerStartupListener listener = new CircuitBreakerStartupListener(properties, environment);

        // 创建事件
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);

        // 测试监听器不抛异常
        assertDoesNotThrow(() -> listener.onApplicationEvent(event), 
                          "启用Swagger时启动监听器应该正常执行");

        // 验证Swagger配置被检查
        verify(environment).getProperty("springdoc.swagger-ui.enabled", "false");
    }
}
