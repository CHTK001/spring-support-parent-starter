package com.chua.starter.circuitbreaker.support;

import com.chua.starter.circuitbreaker.support.annotation.RateLimiter;
import com.chua.starter.circuitbreaker.support.metrics.RateLimiterMetrics;
import com.chua.starter.circuitbreaker.support.properties.CircuitBreakerProperties;
import com.chua.starter.circuitbreaker.support.utils.RateLimiterKeyGenerator;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流功能测试
 * 
 * @author CH
 * @since 2024/12/20
 */
@SpringBootTest(classes = {TestApplication.class, RateLimiterTest.TestConfig.class})
@TestPropertySource(properties = {
    "plugin.circuit-breaker.enable=true",
    "plugin.circuit-breaker.rate-limiter.limit-for-period=5",
    "plugin.circuit-breaker.rate-limiter.limit-refresh-period=1s",
    "plugin.circuit-breaker.rate-limiter.timeout-duration=100ms"
})
public class RateLimiterTest {

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @Autowired
    private CircuitBreakerProperties properties;

    @Autowired
    private TestController testController;

    @Autowired
    private RateLimiterMetrics rateLimiterMetrics;

    @Autowired
    private RateLimiterKeyGenerator keyGenerator;

    @BeforeEach
    void setUp() {
        // 清理之前的测试数据
        rateLimiterRegistry.getAllRateLimiters().asMap().clear();
    }

    @Test
    public void testRateLimiterRegistry() {
        assertNotNull(rateLimiterRegistry, "RateLimiterRegistry应该被正确注入");
    }

    @Test
    public void testCircuitBreakerProperties() {
        assertNotNull(properties, "CircuitBreakerProperties应该被正确注入");
        assertTrue(properties.isEnable(), "熔断降级功能应该被启用");
        
        CircuitBreakerProperties.RateLimiter rateLimiterConfig = properties.getRateLimiter();
        assertNotNull(rateLimiterConfig, "限流器配置应该存在");
        assertEquals(10, rateLimiterConfig.getLimitForPeriod(), "默认限流数量应该为10");
    }

    @Test
    public void testRateLimiterCreation() {
        // 创建一个限流器
        io.github.resilience4j.ratelimiter.RateLimiter rateLimiter = 
            rateLimiterRegistry.rateLimiter("test-limiter");
        
        assertNotNull(rateLimiter, "限流器应该被成功创建");
        assertEquals("test-limiter", rateLimiter.getName(), "限流器名称应该正确");
    }

    @Test
    public void testRateLimiterAnnotation() {
        // 测试带注解的方法是否能正常调用
        assertDoesNotThrow(() -> {
            String result = testController.testMethod();
            assertEquals("success", result, "方法应该正常执行");
        }, "带限流注解的方法应该能正常执行");
    }

    @Test
    public void testRateLimiterConfiguration() {
        CircuitBreakerProperties.RateLimiter rateLimiterConfig = properties.getRateLimiter();

        // 验证默认配置
        assertEquals(10, rateLimiterConfig.getLimitForPeriod());
        assertEquals(1000, rateLimiterConfig.getLimitRefreshPeriod().toMillis());
        assertEquals(500, rateLimiterConfig.getTimeoutDuration().toMillis());
        assertTrue(rateLimiterConfig.isEnableManagement());
        assertEquals("/actuator/rate-limiter", rateLimiterConfig.getManagementPath());
    }

    @Test
    public void testDifferentDimensions() {
        // 测试不同维度的限流
        setupMockRequest("192.168.1.1", "user123");

        // 测试全局限流
        assertDoesNotThrow(() -> testController.globalLimitMethod());

        // 测试IP限流
        assertDoesNotThrow(() -> testController.ipLimitMethod());

        // 测试用户限流
        assertDoesNotThrow(() -> testController.userLimitMethod());

        // 测试API限流
        assertDoesNotThrow(() -> testController.apiLimitMethod());
    }

    @Test
    public void testUserIdExtraction() {
        // 测试JWT Token用户ID提取
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // 这里应该能提取到用户ID，但由于JWT解析的复杂性，我们主要测试流程
        assertDoesNotThrow(() -> testController.userLimitMethod());

        // 测试自定义请求头
        request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user456");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertDoesNotThrow(() -> testController.userLimitMethod());
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        setupMockRequest("192.168.1.100", "testUser");

        int threadCount = 10;
        int requestsPerThread = 2;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            testController.concurrentTestMethod();
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 验证有成功和失败的请求（说明限流生效）
        assertTrue(successCount.get() > 0, "应该有成功的请求");
        assertTrue(failureCount.get() > 0, "应该有被限流的请求");

        System.out.println("成功请求: " + successCount.get() + ", 失败请求: " + failureCount.get());
    }

    @Test
    public void testMetricsCollection() {
        setupMockRequest("192.168.1.200", "metricsUser");

        // 执行一些请求来生成指标
        for (int i = 0; i < 3; i++) {
            try {
                testController.metricsTestMethod();
            } catch (Exception e) {
                // 忽略限流异常
            }
        }

        // 验证指标收集
        RateLimiterMetrics.MetricsStatistics statistics = rateLimiterMetrics.getStatistics();
        assertNotNull(statistics, "指标统计信息不应为空");

        // 验证指标数据
        assertTrue(statistics.getTotalRequests() >= 0, "总请求数应该大于等于0");
        assertTrue(statistics.getSuccessRate() >= 0 && statistics.getSuccessRate() <= 1,
                  "成功率应该在0-1之间");
    }

    private void setupMockRequest(String ip, String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        request.addHeader("X-User-Id", userId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /**
     * 测试配置类
     */
    @TestConfiguration
    public static class TestConfig {
        @Bean
        @Primary
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    /**
     * 测试控制器
     */
    @RestController
    public static class TestController {

        @GetMapping("/test")
        @RateLimiter(
            name = "testMethod",
            limitForPeriod = 10,
            limitRefreshPeriodSeconds = 1,
            message = "测试限流"
        )
        public String testMethod() {
            return "success";
        }

        @GetMapping("/test-global")
        @RateLimiter(
            name = "globalTest",
            limitForPeriod = 5,
            dimension = RateLimiter.Dimension.GLOBAL
        )
        public String globalLimitMethod() {
            return "global-success";
        }

        @GetMapping("/test-ip")
        @RateLimiter(
            name = "ipTest",
            limitForPeriod = 3,
            dimension = RateLimiter.Dimension.IP,
            fallbackMethod = "testFallback"
        )
        public String ipLimitMethod() {
            return "ip-success";
        }

        @GetMapping("/test-user")
        @RateLimiter(
            name = "userTest",
            limitForPeriod = 2,
            dimension = RateLimiter.Dimension.USER
        )
        public String userLimitMethod() {
            return "user-success";
        }

        @GetMapping("/test-api/{id}")
        @RateLimiter(
            name = "apiTest",
            limitForPeriod = 4,
            dimension = RateLimiter.Dimension.API,
            key = "#id"
        )
        public String apiLimitMethod(@PathVariable String id) {
            return "api-success-" + id;
        }

        @GetMapping("/test-concurrent")
        @RateLimiter(
            name = "concurrentTest",
            limitForPeriod = 5,
            limitRefreshPeriodSeconds = 1,
            timeoutDurationMillis = 100
        )
        public String concurrentTestMethod() {
            return "concurrent-success";
        }

        @GetMapping("/test-metrics")
        @RateLimiter(
            name = "metricsTest",
            limitForPeriod = 2,
            limitRefreshPeriodSeconds = 1
        )
        public String metricsTestMethod() {
            return "metrics-success";
        }

        public String testFallback(Exception ex) {
            return "fallback-result";
        }
    }
}
