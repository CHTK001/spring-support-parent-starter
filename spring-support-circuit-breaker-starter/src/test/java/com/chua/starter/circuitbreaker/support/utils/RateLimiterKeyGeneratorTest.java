package com.chua.starter.circuitbreaker.support.utils;

import com.chua.starter.circuitbreaker.support.annotation.RateLimiter;
import com.chua.starter.circuitbreaker.support.enums.RateLimiterDimension;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RateLimiterKeyGenerator测试类
 * 
 * @author CH
 */
@ExtendWith(MockitoExtension.class)
class RateLimiterKeyGeneratorTest {

    private RateLimiterKeyGenerator keyGenerator;
    
    @Mock
    private ProceedingJoinPoint joinPoint;
    
    @Mock
    private MethodSignature methodSignature;
    
    @Mock
    private RateLimiter rateLimiterAnnotation;

    @BeforeEach
    void setUp() {
        keyGenerator = new RateLimiterKeyGenerator();
        
        // 设置基本的mock行为
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn(TestService.class);
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
    }

    @Test
    void testGenerateKeyWithCustomKey() {
        // 测试自定义键
        when(rateLimiterAnnotation.key()).thenReturn("custom-key");
        when(rateLimiterAnnotation.dimension()).thenReturn(RateLimiterDimension.GLOBAL);
        
        String key = keyGenerator.generateKey(rateLimiterAnnotation, joinPoint);
        assertEquals("custom-key", key);
    }

    @Test
    void testGenerateKeyWithGlobalDimension() {
        // 测试全局维度
        when(rateLimiterAnnotation.key()).thenReturn("");
        when(rateLimiterAnnotation.dimension()).thenReturn(RateLimiterDimension.GLOBAL);
        
        String key = keyGenerator.generateKey(rateLimiterAnnotation, joinPoint);
        assertEquals("TestService.testMethod", key);
    }

    @Test
    void testGenerateKeyWithIpDimension() {
        // 设置HTTP请求
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        
        try {
            when(rateLimiterAnnotation.key()).thenReturn("");
            when(rateLimiterAnnotation.dimension()).thenReturn(RateLimiterDimension.IP);
            
            String key = keyGenerator.generateKey(rateLimiterAnnotation, joinPoint);
            assertEquals("TestService.testMethod:ip:192.168.1.100", key);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void testGenerateKeyWithUserDimension() {
        // 设置HTTP请求，包含用户ID头
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user123");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        
        try {
            when(rateLimiterAnnotation.key()).thenReturn("");
            when(rateLimiterAnnotation.dimension()).thenReturn(RateLimiterDimension.USER);
            
            String key = keyGenerator.generateKey(rateLimiterAnnotation, joinPoint);
            assertEquals("TestService.testMethod:user:user123", key);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void testGenerateKeyWithApiDimension() {
        // 设置HTTP请求
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        
        try {
            when(rateLimiterAnnotation.key()).thenReturn("");
            when(rateLimiterAnnotation.dimension()).thenReturn(RateLimiterDimension.API);
            
            String key = keyGenerator.generateKey(rateLimiterAnnotation, joinPoint);
            assertEquals("TestService.testMethod:api:/api/test", key);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void testGetClientIpAddressWithXForwardedFor() {
        // 测试X-Forwarded-For头
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 192.168.1.100");
        request.setRemoteAddr("10.0.0.1");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        
        try {
            when(rateLimiterAnnotation.key()).thenReturn("");
            when(rateLimiterAnnotation.dimension()).thenReturn(RateLimiterDimension.IP);
            
            String key = keyGenerator.generateKey(rateLimiterAnnotation, joinPoint);
            // 应该取第一个IP
            assertEquals("TestService.testMethod:ip:203.0.113.1", key);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void testGetUserIdWithJwtToken() {
        // 测试JWT Token解析
        String jwtPayload = "{\"sub\":\"user456\",\"iat\":1234567890}";
        String encodedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(jwtPayload.getBytes());
        String jwtToken = "header." + encodedPayload + ".signature";
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtToken);
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        
        try {
            when(rateLimiterAnnotation.key()).thenReturn("");
            when(rateLimiterAnnotation.dimension()).thenReturn(RateLimiterDimension.USER);
            
            String key = keyGenerator.generateKey(rateLimiterAnnotation, joinPoint);
            assertEquals("TestService.testMethod:user:user456", key);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void testGetUserIdFallbackToAnonymous() {
        // 测试没有用户信息时的回退
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        
        try {
            when(rateLimiterAnnotation.key()).thenReturn("");
            when(rateLimiterAnnotation.dimension()).thenReturn(RateLimiterDimension.USER);
            
            String key = keyGenerator.generateKey(rateLimiterAnnotation, joinPoint);
            assertEquals("TestService.testMethod:user:anonymous", key);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
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
        public void testMethod() {
            // 测试方法
        }
    }
}
