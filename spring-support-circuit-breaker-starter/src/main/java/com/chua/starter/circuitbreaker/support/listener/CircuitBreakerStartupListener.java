package com.chua.starter.circuitbreaker.support.listener;

import com.chua.starter.circuitbreaker.support.properties.CircuitBreakerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 熔断器启动监听器
 * 
 * 在应用启动完成后输出管理页面地址和相关信息
 * 
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private final CircuitBreakerProperties properties;
    private final Environment environment;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.isEnable()) {
            return;
        }

        try {
            printStartupInfo();
        } catch (Exception e) {
            log.warn("输出启动信息失败", e);
        }
    }

    /**
     * 打印启动信息
     */
    private void printStartupInfo() {
        String serverPort = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");

        // 获取本机IP地址
        String hostAddress = getHostAddress();

        // 构建基础URL
        String baseUrl = "http://" + hostAddress + ":" + serverPort + contextPath;

        // 输出启动横幅
        printBanner();

        // 输出功能状态
        printFeatureStatus();

        // 输出管理页面地址
        printManagementUrls(baseUrl);

        // 输出使用提示
        printUsageTips();

        log.info("╚" + "═".repeat(78) + "╝");
        log.info("");
    }

    /**
     * 打印启动横幅
     */
    private void printBanner() {
        log.info("");
        log.info("╔" + "═".repeat(78) + "╗");
        log.info("║" + " ".repeat(78) + "║");
        log.info("║" + centerText("🚀 Spring Support Circuit Breaker 启动成功！", 78) + "║");
        log.info("║" + centerText("基于 Resilience4j 的企业级容错解决方案", 78) + "║");
        log.info("║" + " ".repeat(78) + "║");
        log.info("╠" + "═".repeat(78) + "╣");
    }

    /**
     * 打印功能状态
     */
    private void printFeatureStatus() {
        log.info("║ 📊 功能状态:" + " ".repeat(66) + "║");
        log.info("║   ├─ 熔断器 (Circuit Breaker): {}" + " ".repeat(44) + "║",
                 properties.getCircuitBreaker() != null ? "✅ 已启用" : "❌ 未配置");
        log.info("║   ├─ 限流器 (Rate Limiter): {}" + " ".repeat(47) + "║",
                 properties.getRateLimiter() != null ? "✅ 已启用" : "❌ 未配置");
        log.info("║   ├─ 重试机制 (Retry): {}" + " ".repeat(53) + "║",
                 properties.getRetry() != null ? "✅ 已启用" : "❌ 未配置");
        log.info("║   ├─ 舱壁隔离 (Bulkhead): {}" + " ".repeat(49) + "║",
                 properties.getBulkhead() != null ? "✅ 已启用" : "❌ 未配置");
        log.info("║   ├─ 超时控制 (Time Limiter): {}" + " ".repeat(45) + "║",
                 properties.getTimeLimiter() != null ? "✅ 已启用" : "❌ 未配置");
        log.info("║   └─ 缓存 (Cache): {}" + " ".repeat(56) + "║",
                 properties.getCache() != null ? "✅ 已启用" : "❌ 未配置");
        log.info("║" + " ".repeat(78) + "║");
        log.info("╠" + "═".repeat(78) + "╣");
    }

    /**
     * 打印管理页面地址
     */
    private void printManagementUrls(String baseUrl) {
        log.info("║ 🌐 管理页面地址:" + " ".repeat(64) + "║");

        // 限流器管理页面
        if (properties.getRateLimiter() != null && properties.getRateLimiter().isEnableManagement()) {
            String rateLimiterPath = properties.getRateLimiter().getManagementPath();
            String fullUrl = baseUrl + rateLimiterPath;
            log.info("║   ├─ 限流器管理: {}" + " ".repeat(Math.max(0, 78 - 19 - fullUrl.length())) + "║", fullUrl);
            log.info("║   │  ├─ 功能: 实时监控、动态配置、指标查看" + " ".repeat(32) + "║");
            log.info("║   │  └─ 支持: 创建/更新/删除限流器、查看QPS统计" + " ".repeat(28) + "║");
        }

        // Actuator端点
        String actuatorBasePath = environment.getProperty("management.endpoints.web.base-path", "/actuator");
        String actuatorUrl = baseUrl + actuatorBasePath;
        log.info("║   ├─ Actuator端点: {}" + " ".repeat(Math.max(0, 78 - 18 - actuatorUrl.length())) + "║", actuatorUrl);
        log.info("║   │  ├─ 健康检查: {}/health" + " ".repeat(Math.max(0, 78 - 19 - actuatorUrl.length() - 7)) + "║", actuatorUrl);
        log.info("║   │  ├─ 指标监控: {}/metrics" + " ".repeat(Math.max(0, 78 - 19 - actuatorUrl.length() - 8)) + "║", actuatorUrl);
        log.info("║   │  └─ 熔断器状态: {}/circuitbreakers" + " ".repeat(Math.max(0, 78 - 23 - actuatorUrl.length() - 16)) + "║", actuatorUrl);

        // API文档（如果启用了Swagger）
        if (isSwaggerEnabled()) {
            String swaggerUrl = baseUrl + "/swagger-ui.html";
            log.info("║   └─ API文档: {}" + " ".repeat(Math.max(0, 78 - 14 - swaggerUrl.length())) + "║", swaggerUrl);
        }

        log.info("║" + " ".repeat(78) + "║");
        log.info("╠" + "═".repeat(78) + "╣");
    }

    /**
     * 打印使用提示
     */
    private void printUsageTips() {
        log.info("║ 💡 使用提示:" + " ".repeat(66) + "║");
        log.info("║   • 使用 @RateLimiter 注解实现方法级限流" + " ".repeat(36) + "║");
        log.info("║   • 使用 @CircuitBreakerProtection 注解实现容错保护" + " ".repeat(26) + "║");
        log.info("║   • 支持 GLOBAL、IP、USER、API 四种限流维度" + " ".repeat(32) + "║");
        log.info("║   • 集成 Prometheus 指标，支持监控和告警" + " ".repeat(34) + "║");
        log.info("║   • 访问管理页面进行实时配置和监控" + " ".repeat(40) + "║");
        log.info("║" + " ".repeat(78) + "║");
    }

    /**
     * 文本居中
     */
    private String centerText(String text, int width) {
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text + " ".repeat(width - text.length() - padding);
    }

    /**
     * 获取主机地址
     */
    private String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    /**
     * 检查是否启用了Swagger
     */
    private boolean isSwaggerEnabled() {
        String swaggerEnabled = environment.getProperty("springdoc.swagger-ui.enabled", "false");
        return "true".equalsIgnoreCase(swaggerEnabled);
    }
}
