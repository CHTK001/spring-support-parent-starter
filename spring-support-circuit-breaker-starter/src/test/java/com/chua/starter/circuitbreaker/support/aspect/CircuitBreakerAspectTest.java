package com.chua.starter.circuitbreaker.support.aspect;

import com.chua.starter.circuitbreaker.support.annotation.CircuitBreakerProtection;
import com.chua.starter.circuitbreaker.support.service.CircuitBreakerService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CircuitBreakerAspect测试类
 * 
 * @author CH
 */
@ExtendWith(MockitoExtension.class)
class CircuitBreakerAspectTest {

    private CircuitBreakerAspect circuitBreakerAspect;
    
    @Mock
    private CircuitBreakerService circuitBreakerService;
    
    @Mock
    private ProceedingJoinPoint joinPoint;
    
    @Mock
    private MethodSignature methodSignature;
    
    @Mock
    private CircuitBreakerProtection protection;

    @BeforeEach
    void setUp() {
        circuitBreakerAspect = new CircuitBreakerAspect(circuitBreakerService);
        
        // 设置基本的mock行为
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn(TestService.class);
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.getTarget()).thenReturn(new TestService());
    }

    @Test
    void testAroundWithNullJoinPoint() {
        // 测试空的JoinPoint
        assertThrows(IllegalArgumentException.class, () -> {
            circuitBreakerAspect.around(null, protection);
        });
    }

    @Test
    void testAroundWithNullProtection() throws Throwable {
        // 测试空的Protection注解
        when(joinPoint.proceed()).thenReturn("test result");
        
        Object result = circuitBreakerAspect.around(joinPoint, null);
        
        assertEquals("test result", result);
        verify(joinPoint).proceed();
    }

    @Test
    void testSyncExecutionWithCircuitBreaker() throws Throwable {
        // 测试同步执行熔断器
        when(protection.async()).thenReturn(false);
        when(protection.circuitBreaker()).thenReturn("test-circuit-breaker");
        when(protection.retry()).thenReturn("");
        when(protection.rateLimiter()).thenReturn("");
        when(protection.bulkhead()).thenReturn("");
        when(protection.fallbackMethod()).thenReturn("");
        
        when(circuitBreakerService.executeWithCircuitBreaker(eq("test-circuit-breaker"), any(Supplier.class), any(Supplier.class)))
                .thenReturn("circuit breaker result");
        
        Object result = circuitBreakerAspect.around(joinPoint, protection);
        
        assertEquals("circuit breaker result", result);
        verify(circuitBreakerService).executeWithCircuitBreaker(eq("test-circuit-breaker"), any(Supplier.class), any(Supplier.class));
    }

    @Test
    void testSyncExecutionWithRetry() throws Throwable {
        // 测试同步执行重试
        when(protection.async()).thenReturn(false);
        when(protection.circuitBreaker()).thenReturn("");
        when(protection.retry()).thenReturn("test-retry");
        when(protection.rateLimiter()).thenReturn("");
        when(protection.bulkhead()).thenReturn("");
        
        when(circuitBreakerService.executeWithRetry(eq("test-retry"), any(Supplier.class)))
                .thenReturn("retry result");
        
        Object result = circuitBreakerAspect.around(joinPoint, protection);
        
        assertEquals("retry result", result);
        verify(circuitBreakerService).executeWithRetry(eq("test-retry"), any(Supplier.class));
    }

    @Test
    void testSyncExecutionWithRateLimit() throws Throwable {
        // 测试同步执行限流
        when(protection.async()).thenReturn(false);
        when(protection.circuitBreaker()).thenReturn("");
        when(protection.retry()).thenReturn("");
        when(protection.rateLimiter()).thenReturn("test-rate-limiter");
        when(protection.bulkhead()).thenReturn("");
        
        when(circuitBreakerService.executeWithRateLimit(eq("test-rate-limiter"), any(Supplier.class)))
                .thenReturn("rate limit result");
        
        Object result = circuitBreakerAspect.around(joinPoint, protection);
        
        assertEquals("rate limit result", result);
        verify(circuitBreakerService).executeWithRateLimit(eq("test-rate-limiter"), any(Supplier.class));
    }

    @Test
    void testSyncExecutionWithBulkhead() throws Throwable {
        // 测试同步执行隔离
        when(protection.async()).thenReturn(false);
        when(protection.circuitBreaker()).thenReturn("");
        when(protection.retry()).thenReturn("");
        when(protection.rateLimiter()).thenReturn("");
        when(protection.bulkhead()).thenReturn("test-bulkhead");
        
        when(circuitBreakerService.executeWithBulkhead(eq("test-bulkhead"), any(Supplier.class)))
                .thenReturn("bulkhead result");
        
        Object result = circuitBreakerAspect.around(joinPoint, protection);
        
        assertEquals("bulkhead result", result);
        verify(circuitBreakerService).executeWithBulkhead(eq("test-bulkhead"), any(Supplier.class));
    }

    @Test
    void testSyncExecutionWithCombined() throws Throwable {
        // 测试同步执行组合功能
        when(protection.async()).thenReturn(false);
        when(protection.circuitBreaker()).thenReturn("test-circuit-breaker");
        when(protection.retry()).thenReturn("test-retry");
        when(protection.rateLimiter()).thenReturn("");
        when(protection.bulkhead()).thenReturn("");
        when(protection.fallbackMethod()).thenReturn("");
        
        when(circuitBreakerService.executeWithCombined(eq("test-circuit-breaker"), eq("test-retry"), eq("default"), any(Supplier.class), any(Supplier.class)))
                .thenReturn("combined result");
        
        Object result = circuitBreakerAspect.around(joinPoint, protection);
        
        assertEquals("combined result", result);
        verify(circuitBreakerService).executeWithCombined(eq("test-circuit-breaker"), eq("test-retry"), eq("default"), any(Supplier.class), any(Supplier.class));
    }

    @Test
    void testAsyncExecution() throws Throwable {
        // 测试异步执行
        when(protection.async()).thenReturn(true);
        when(protection.circuitBreaker()).thenReturn("");
        when(protection.retry()).thenReturn("");
        when(protection.rateLimiter()).thenReturn("");
        when(protection.bulkhead()).thenReturn("");
        when(protection.timeLimiter()).thenReturn("");
        
        when(joinPoint.proceed()).thenReturn("async result");
        
        Object result = circuitBreakerAspect.around(joinPoint, protection);
        
        assertInstanceOf(CompletionStage.class, result);
    }

    @Test
    void testAsyncExecutionWithTimeLimit() throws Throwable {
        // 测试带超时限制的异步执行
        when(protection.async()).thenReturn(true);
        when(protection.circuitBreaker()).thenReturn("");
        when(protection.retry()).thenReturn("");
        when(protection.rateLimiter()).thenReturn("");
        when(protection.bulkhead()).thenReturn("");
        when(protection.timeLimiter()).thenReturn("test-time-limiter");
        
        CompletableFuture<String> expectedResult = CompletableFuture.completedFuture("time limited result");
        when(circuitBreakerService.executeWithTimeLimit(eq("test-time-limiter"), any(Supplier.class)))
                .thenReturn(expectedResult);
        
        Object result = circuitBreakerAspect.around(joinPoint, protection);
        
        assertEquals(expectedResult, result);
        verify(circuitBreakerService).executeWithTimeLimit(eq("test-time-limiter"), any(Supplier.class));
    }

    @Test
    void testDirectExecution() throws Throwable {
        // 测试没有保护功能的直接执行
        when(protection.async()).thenReturn(false);
        when(protection.circuitBreaker()).thenReturn("");
        when(protection.retry()).thenReturn("");
        when(protection.rateLimiter()).thenReturn("");
        when(protection.bulkhead()).thenReturn("");
        
        when(joinPoint.proceed()).thenReturn("direct result");
        
        Object result = circuitBreakerAspect.around(joinPoint, protection);
        
        assertEquals("direct result", result);
        verify(joinPoint).proceed();
    }

    @Test
    void testFallbackExecution() throws Throwable {
        // 测试降级方法执行
        TestService testService = new TestService();
        when(joinPoint.getTarget()).thenReturn(testService);
        when(protection.fallbackMethod()).thenReturn("fallbackMethod");
        
        // 使用反射调用私有方法进行测试
        Method executeFallbackMethod = CircuitBreakerAspect.class.getDeclaredMethod(
                "executeFallback", Object.class, Method.class, Object[].class, String.class);
        executeFallbackMethod.setAccessible(true);
        
        Object result = executeFallbackMethod.invoke(circuitBreakerAspect, 
                testService, getTestMethod(), new Object[0], "fallbackMethod");
        
        assertEquals("fallback result", result);
    }

    private Method getTestMethod() {
        try {
            return TestService.class.getMethod("testMethod");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    // 测试用的服务类
    public static class TestService {
        public String testMethod() {
            return "test result";
        }
        
        public String fallbackMethod() {
            return "fallback result";
        }
    }
}
