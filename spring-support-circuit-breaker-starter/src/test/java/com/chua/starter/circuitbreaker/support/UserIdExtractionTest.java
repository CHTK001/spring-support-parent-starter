package com.chua.starter.circuitbreaker.support;

import com.chua.starter.circuitbreaker.support.annotation.RateLimiter;
import com.chua.starter.circuitbreaker.support.utils.RateLimiterKeyGenerator;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 用户ID提取功能测试
 * 
 * 测试各种用户ID获取策略的正确性
 * 
 * @author CH
 * @since 2024/12/20
 */
public class UserIdExtractionTest {

    private RateLimiterKeyGenerator keyGenerator;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private Method method;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        keyGenerator = new RateLimiterKeyGenerator();
        
        // 设置mock对象
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(method.getDeclaringClass()).thenReturn(TestClass.class);
        when(method.getName()).thenReturn("testMethod");
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"param1"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"value1"});
    }

    @Test
    public void testJwtTokenUserIdExtraction() {
        // 创建一个简单的JWT Token（仅用于测试）
        String payload = "{\"sub\":\"jwt-user-123\",\"name\":\"Test User\"}";
        String encodedPayload = Base64.getUrlEncoder().encodeToString(payload.getBytes());
        String token = "header." + encodedPayload + ".signature";
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        RateLimiter annotation = createRateLimiterAnnotation(RateLimiter.Dimension.USER, "");
        String key = keyGenerator.generateKey(annotation, joinPoint);
        
        // 验证生成的键包含用户维度信息
        assertTrue(key.contains("user:"), "键应该包含用户维度前缀");
        
        // 清理
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testSpringSecurityUserIdExtraction() {
        // 设置Spring Security上下文
        UsernamePasswordAuthenticationToken authentication = 
            new UsernamePasswordAuthenticationToken("security-user-456", "password");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        RateLimiter annotation = createRateLimiterAnnotation(RateLimiter.Dimension.USER, "");
        String key = keyGenerator.generateKey(annotation, joinPoint);
        
        assertTrue(key.contains("user:"), "键应该包含用户维度前缀");
        
        // 清理
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testCustomHeaderUserIdExtraction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "header-user-789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        RateLimiter annotation = createRateLimiterAnnotation(RateLimiter.Dimension.USER, "");
        String key = keyGenerator.generateKey(annotation, joinPoint);
        
        assertTrue(key.contains("user:"), "键应该包含用户维度前缀");
        assertTrue(key.contains("header-user-789"), "键应该包含从请求头获取的用户ID");
        
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testSessionUserIdExtraction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", "session-user-101");
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        RateLimiter annotation = createRateLimiterAnnotation(RateLimiter.Dimension.USER, "");
        String key = keyGenerator.generateKey(annotation, joinPoint);
        
        assertTrue(key.contains("user:"), "键应该包含用户维度前缀");
        
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testPrincipalUserIdExtraction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(new Principal() {
            @Override
            public String getName() {
                return "principal-user-202";
            }
        });
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        RateLimiter annotation = createRateLimiterAnnotation(RateLimiter.Dimension.USER, "");
        String key = keyGenerator.generateKey(annotation, joinPoint);
        
        assertTrue(key.contains("user:"), "键应该包含用户维度前缀");
        assertTrue(key.contains("principal-user-202"), "键应该包含从Principal获取的用户ID");
        
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testIpDimensionExtraction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        RateLimiter annotation = createRateLimiterAnnotation(RateLimiter.Dimension.IP, "");
        String key = keyGenerator.generateKey(annotation, joinPoint);
        
        assertTrue(key.contains("ip:"), "键应该包含IP维度前缀");
        assertTrue(key.contains("192.168.1.100"), "键应该包含IP地址");
        
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testApiDimensionExtraction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/users/123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        RateLimiter annotation = createRateLimiterAnnotation(RateLimiter.Dimension.API, "");
        String key = keyGenerator.generateKey(annotation, joinPoint);
        
        assertTrue(key.contains("api:"), "键应该包含API维度前缀");
        
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testCustomKeyExpression() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "custom-user-303");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        RateLimiter annotation = createRateLimiterAnnotation(RateLimiter.Dimension.USER, "#param1");
        String key = keyGenerator.generateKey(annotation, joinPoint);
        
        // 自定义键表达式应该被使用
        assertTrue(key.contains("value1"), "键应该包含SpEL表达式的结果");
        
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testFallbackToAnonymous() {
        // 没有设置任何用户信息
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        RateLimiter annotation = createRateLimiterAnnotation(RateLimiter.Dimension.USER, "");
        String key = keyGenerator.generateKey(annotation, joinPoint);
        
        assertTrue(key.contains("user:"), "键应该包含用户维度前缀");
        assertTrue(key.contains("anonymous"), "应该回退到匿名用户");
        
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 创建RateLimiter注解的模拟对象
     */
    private RateLimiter createRateLimiterAnnotation(RateLimiter.Dimension dimension, String key) {
        return new RateLimiter() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RateLimiter.class;
            }

            @Override
            public String name() { return "test"; }

            @Override
            public String key() { return key; }

            @Override
            public int limitForPeriod() { return 10; }

            @Override
            public long limitRefreshPeriodSeconds() { return 1; }

            @Override
            public long timeoutDurationMillis() { return 500; }

            @Override
            public String fallbackMethod() { return ""; }

            @Override
            public String message() { return "限流"; }

            @Override
            public Dimension dimension() { return dimension; }
        };
    }

    /**
     * 测试类
     */
    public static class TestClass {
        public void testMethod(String param1) {
            // 测试方法
        }
    }
}
