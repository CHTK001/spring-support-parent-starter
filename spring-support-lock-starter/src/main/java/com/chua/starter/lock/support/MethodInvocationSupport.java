package com.chua.starter.lock.support;

import com.chua.common.support.core.utils.ElUtils;
import com.chua.common.support.lang.proxy.ProxyMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.DigestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 方法调用辅助工具。
 *
 * @author CH
 * @since 2026-03-28
 */
public final class MethodInvocationSupport {

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final Method HEADER_FUNCTION = ReflectionUtils.findMethod(MethodInvocationSupport.class, "header", String.class);
    private static final Method PARAM_FUNCTION = ReflectionUtils.findMethod(MethodInvocationSupport.class, "param", String.class);
    private static final Method SESSION_ATTR_FUNCTION = ReflectionUtils.findMethod(MethodInvocationSupport.class, "sessionAttr", String.class);

    private MethodInvocationSupport() {
    }

    public static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        if (target == null) {
            return method;
        }
        return AopUtils.getMostSpecificMethod(method, AopUtils.getTargetClass(target));
    }

    public static String evaluateToString(String expression, ProceedingJoinPoint joinPoint, Method method) {
        if (!StringUtils.hasText(expression)) {
            return "";
        }
        if (!containsExpression(expression)) {
            return expression;
        }

        try {
            Object value = SPEL_PARSER.parseExpression(expression).getValue(buildEvaluationContext(joinPoint, method));
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ex) {
            ProxyMethod proxyMethod = ProxyMethod.builder()
                    .target(joinPoint.getTarget())
                    .proxy(joinPoint.getThis())
                    .method(method)
                    .args(joinPoint.getArgs())
                    .build();
            String normalizedExpression = expression.contains("#{") ? expression : "#{" + expression + "}";
            return ElUtils.parse(normalizedExpression, proxyMethod);
        }
    }

    public static Object invokeFallback(ProceedingJoinPoint joinPoint, Method sourceMethod, String fallbackMethodName) throws Throwable {
        if (!StringUtils.hasText(fallbackMethodName)) {
            return null;
        }

        Object target = joinPoint.getTarget();
        Class<?> targetClass = target == null ? sourceMethod.getDeclaringClass() : AopUtils.getTargetClass(target);
        Method fallbackMethod = ReflectionUtils.findMethod(targetClass, fallbackMethodName, sourceMethod.getParameterTypes());
        if (fallbackMethod == null) {
            throw new IllegalStateException("未找到降级方法: " + fallbackMethodName);
        }

        ReflectionUtils.makeAccessible(fallbackMethod);
        try {
            return fallbackMethod.invoke(target, joinPoint.getArgs());
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }

    public static String buildMethodSignature(Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    public static String buildArgsMd5(Method method, Object[] args) {
        String source = method.toGenericString() + ":" + Arrays.deepToString(args == null ? new Object[0] : args);
        return DigestUtils.md5DigestAsHex(source.getBytes(StandardCharsets.UTF_8));
    }

    public static String buildRequestBodyMd5(Method method, Object[] args) {
        byte[] body = extractCachedRequestBody(currentRequest());
        if (body.length == 0) {
            return buildArgsMd5(method, args);
        }
        return DigestUtils.md5DigestAsHex(body);
    }

    public static HttpServletRequest currentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    public static String header(String name) {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader(name);
    }

    public static String param(String name) {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getParameter(name);
    }

    public static Object sessionAttr(String name) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        return session == null ? null : session.getAttribute(name);
    }

    private static EvaluationContext buildEvaluationContext(ProceedingJoinPoint joinPoint, Method method) {
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(),
                method,
                joinPoint.getArgs(),
                PARAMETER_NAME_DISCOVERER
        );
        context.registerFunction("header", HEADER_FUNCTION);
        context.registerFunction("param", PARAM_FUNCTION);
        context.registerFunction("sessionAttr", SESSION_ATTR_FUNCTION);
        HttpServletRequest request = currentRequest();
        if (request != null) {
            context.setVariable("request", request);
            context.setVariable("session", request.getSession(false));
            context.setVariable("principal", request.getUserPrincipal());
        }
        return context;
    }

    private static boolean containsExpression(String expression) {
        return expression.contains("#");
    }

    private static byte[] extractCachedRequestBody(HttpServletRequest request) {
        if (request == null) {
            return new byte[0];
        }

        if (request instanceof ContentCachingRequestWrapper contentCachingRequestWrapper) {
            byte[] content = contentCachingRequestWrapper.getContentAsByteArray();
            if (content.length > 0) {
                return content;
            }
        }

        byte[] reflectedBody = extractBodyByReflection(request);
        if (reflectedBody.length > 0) {
            return reflectedBody;
        }

        if (request instanceof HttpServletRequestWrapper wrapper && wrapper.getRequest() instanceof HttpServletRequest nestedRequest) {
            return extractCachedRequestBody(nestedRequest);
        }

        return new byte[0];
    }

    private static byte[] extractBodyByReflection(HttpServletRequest request) {
        Method method = ReflectionUtils.findMethod(request.getClass(), "getBody");
        if (method == null) {
            return new byte[0];
        }

        ReflectionUtils.makeAccessible(method);
        try {
            Object value = method.invoke(request);
            if (value instanceof byte[] bytes) {
                return bytes;
            }
            if (value instanceof CharSequence charSequence) {
                return charSequence.toString().getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return new byte[0];
    }
}
