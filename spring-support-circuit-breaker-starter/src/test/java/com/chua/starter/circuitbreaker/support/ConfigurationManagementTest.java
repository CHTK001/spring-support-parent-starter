package com.chua.starter.circuitbreaker.support;

import com.chua.starter.circuitbreaker.support.controller.ConfigurationManagementController;
import com.chua.starter.circuitbreaker.support.properties.CircuitBreakerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置管理控制器测试
 * 
 * @author CH
 * @since 2024/12/20
 */
@SpringBootTest(classes = TestApplication.class)
@TestPropertySource(properties = {
    "plugin.circuit-breaker.enable=true",
    "plugin.circuit-breaker.rate-limiter.limit-for-period=10",
    "plugin.circuit-breaker.rate-limiter.enable-management=true"
})
public class ConfigurationManagementTest {

    @Autowired
    private ConfigurationManagementController configController;

    @Autowired
    private CircuitBreakerProperties properties;

    @BeforeEach
    void setUp() {
        // 确保有基本的限流器配置
        if (properties.getRateLimiter() == null) {
            properties.setRateLimiter(new CircuitBreakerProperties.RateLimiter());
        }
    }

    @Test
    public void testGetConfigurationPage() {
        // 测试获取配置页面
        String page = configController.getConfigurationPage();
        
        assertNotNull(page, "配置页面不应为空");
        assertTrue(page.contains("熔断器配置管理"), "页面应包含标题");
        assertTrue(page.contains("功能状态"), "页面应包含功能状态部分");
        assertTrue(page.contains("限流器配置"), "页面应包含限流器配置部分");
        assertTrue(page.contains("refreshConfig"), "页面应包含刷新配置功能");
    }

    @Test
    public void testGetCurrentConfiguration() {
        // 测试获取当前配置
        Map<String, Object> config = configController.getCurrentConfiguration();
        
        assertNotNull(config, "配置不应为空");
        assertTrue(config.containsKey("enable"), "应包含enable字段");
        assertTrue(config.containsKey("rateLimiter"), "应包含rateLimiter字段");
        assertTrue(config.containsKey("circuitBreaker"), "应包含circuitBreaker字段");
        
        // 验证enable状态
        assertEquals(true, config.get("enable"), "enable应为true");
    }

    @Test
    public void testUpdateRateLimiterConfig() {
        // 准备更新数据
        Map<String, Object> updateConfig = new HashMap<>();
        updateConfig.put("limitForPeriod", 20);
        updateConfig.put("enableManagement", true);
        updateConfig.put("managementPath", "/custom/rate-limiter");
        
        // 执行更新
        Map<String, Object> result = configController.updateRateLimiterConfig(updateConfig);
        
        assertNotNull(result, "更新结果不应为空");
        assertTrue((Boolean) result.get("success"), "更新应该成功");
        assertNotNull(result.get("message"), "应该有成功消息");
        assertNotNull(result.get("config"), "应该返回更新后的配置");
        
        // 验证配置已更新
        CircuitBreakerProperties.RateLimiter rateLimiter = properties.getRateLimiter();
        assertEquals(20, rateLimiter.getLimitForPeriod(), "limitForPeriod应该被更新");
        assertEquals("/custom/rate-limiter", rateLimiter.getManagementPath(), "managementPath应该被更新");
    }

    @Test
    public void testUpdateRateLimiterConfigWithNullRateLimiter() {
        // 设置rateLimiter为null来测试自动创建
        properties.setRateLimiter(null);
        
        Map<String, Object> updateConfig = new HashMap<>();
        updateConfig.put("limitForPeriod", 15);
        updateConfig.put("enableManagement", false);
        
        // 执行更新
        Map<String, Object> result = configController.updateRateLimiterConfig(updateConfig);
        
        assertTrue((Boolean) result.get("success"), "更新应该成功");
        
        // 验证rateLimiter被自动创建
        assertNotNull(properties.getRateLimiter(), "RateLimiter应该被自动创建");
        assertEquals(15, properties.getRateLimiter().getLimitForPeriod(), "配置应该被正确设置");
        assertFalse(properties.getRateLimiter().isEnableManagement(), "enableManagement应该被设置为false");
    }

    @Test
    public void testResetConfiguration() {
        // 先修改一些配置
        CircuitBreakerProperties.RateLimiter rateLimiter = properties.getRateLimiter();
        rateLimiter.setLimitForPeriod(999);
        rateLimiter.setManagementPath("/custom/path");
        
        // 执行重置
        Map<String, Object> result = configController.resetConfiguration();
        
        assertNotNull(result, "重置结果不应为空");
        assertTrue((Boolean) result.get("success"), "重置应该成功");
        assertNotNull(result.get("message"), "应该有成功消息");
        
        // 验证配置被重置
        CircuitBreakerProperties.RateLimiter newRateLimiter = properties.getRateLimiter();
        assertNotNull(newRateLimiter, "RateLimiter不应为空");
        // 验证是新的实例（默认值）
        assertEquals(10, newRateLimiter.getLimitForPeriod(), "应该恢复默认的limitForPeriod");
        assertEquals("/actuator/rate-limiter", newRateLimiter.getManagementPath(), "应该恢复默认的managementPath");
    }

    @Test
    public void testUpdateRateLimiterConfigWithInvalidData() {
        // 测试部分字段更新
        Map<String, Object> updateConfig = new HashMap<>();
        updateConfig.put("limitForPeriod", 25);
        // 不包含其他字段
        
        Map<String, Object> result = configController.updateRateLimiterConfig(updateConfig);
        
        assertTrue((Boolean) result.get("success"), "部分更新应该成功");
        
        // 验证只有指定字段被更新
        CircuitBreakerProperties.RateLimiter rateLimiter = properties.getRateLimiter();
        assertEquals(25, rateLimiter.getLimitForPeriod(), "limitForPeriod应该被更新");
        // 其他字段应该保持原值
        assertTrue(rateLimiter.isEnableManagement(), "enableManagement应该保持原值");
    }

    @Test
    public void testConfigurationPageContainsJavaScript() {
        String page = configController.getConfigurationPage();
        
        // 验证页面包含必要的JavaScript函数
        assertTrue(page.contains("refreshConfig()"), "应包含refreshConfig函数");
        assertTrue(page.contains("updateRateLimiterConfig()"), "应包含updateRateLimiterConfig函数");
        assertTrue(page.contains("resetConfig()"), "应包含resetConfig函数");
        assertTrue(page.contains("openRateLimiterManagement()"), "应包含openRateLimiterManagement函数");
        assertTrue(page.contains("showAlert"), "应包含showAlert函数");
    }

    @Test
    public void testConfigurationPageStyling() {
        String page = configController.getConfigurationPage();
        
        // 验证页面包含CSS样式
        assertTrue(page.contains("<style>"), "应包含CSS样式");
        assertTrue(page.contains("config-section"), "应包含config-section样式类");
        assertTrue(page.contains("btn"), "应包含按钮样式类");
        assertTrue(page.contains("alert"), "应包含alert样式类");
    }
}
