package com.chua.starter.circuitbreaker.support.test;

import com.chua.starter.circuitbreaker.support.annotation.CircuitBreakerProtection;
import com.chua.starter.circuitbreaker.support.service.CircuitBreakerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 熔断降级功能测试
 * 
 * @author CH
 * @since 2024/12/20
 */
@SpringBootTest(classes = TestApplication.class)
public class CircuitBreakerTest {

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    @Autowired
    private TestService testService;

    @Test
    public void testCircuitBreaker() {
        // 测试熔断器功能
        String result = circuitBreakerService.executeWithCircuitBreaker(
                "test",
                () -> "success",
                () -> "fallback"
        );
        assertEquals("success", result);
    }

    @Test
    public void testRetry() {
        // 测试重试功能
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = circuitBreakerService.executeWithRetry(
                "test",
                () -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new RuntimeException("模拟失败");
                    }
                    return "success after retry";
                }
        );
        
        assertEquals("success after retry", result);
        assertEquals(3, attempts.get());
    }

    @Test
    public void testRateLimit() {
        // 测试限流功能
        boolean result1 = circuitBreakerService.executeWithRateLimit(
                "test",
                () -> true
        );
        assertTrue(result1);
    }

    @Test
    public void testAnnotation() {
        // 测试注解功能
        String result = testService.testMethod("test");
        assertEquals("test", result);
    }

    @Test
    public void testAnnotationFallback() {
        // 测试注解降级功能
        String result = testService.testMethodWithFallback("error");
        assertEquals("fallback: error", result);
    }

    @Service
    public static class TestService {

        @CircuitBreakerProtection(
                circuitBreaker = "testService",
                fallbackMethod = "testMethodFallback"
        )
        public String testMethod(String input) {
            if ("error".equals(input)) {
                throw new RuntimeException("模拟错误");
            }
            return input;
        }

        @CircuitBreakerProtection(
                circuitBreaker = "testService",
                fallbackMethod = "testMethodFallback"
        )
        public String testMethodWithFallback(String input) {
            throw new RuntimeException("模拟错误");
        }

        public String testMethodFallback(String input, Exception ex) {
            return "fallback: " + input;
        }

        @CircuitBreakerProtection(
                circuitBreaker = "testService",
                async = true,
                fallbackMethod = "asyncMethodFallback"
        )
        public CompletableFuture<String> asyncMethod(String input) {
            return CompletableFuture.supplyAsync(() -> {
                if ("error".equals(input)) {
                    throw new RuntimeException("模拟错误");
                }
                return "async: " + input;
            });
        }

        public String asyncMethodFallback(String input, Exception ex) {
            return "async fallback: " + input;
        }
    }
}
