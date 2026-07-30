package com.chua.starter.lock.aspect;

import com.chua.common.support.task.layer.idempotent.IdempotentProvider;
import com.chua.starter.lock.annotation.Idempotent;
import com.chua.starter.lock.exception.IdempotentException;
import com.chua.starter.lock.properties.LockProperties;
import com.chua.starter.lock.support.AnnotationAttributeSupport;
import com.chua.starter.lock.support.MethodInvocationSupport;
import com.chua.starter.lock.support.NullValue;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 兼容 strategy 模块旧版 {@code @Idempotent} 注解。
 *
 * @author CH
 * @since 2026-03-28
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 45)
public class StrategyIdempotentAspect {

    private static final String ANNOTATION_NAME = "com.chua.starter.strategy.annotation.Idempotent";
    private static final String TOKEN_HEADER = "X-Idempotent-Token";
    private static final String TOKEN_PARAM = "_idempotentToken";
    private static final String USER_HEADER = "X-User-Id";

    private final IdempotentProvider idempotentProvider;
    private final LockProperties lockProperties;

    public StrategyIdempotentAspect(IdempotentProvider idempotentProvider, LockProperties lockProperties) {
        this.idempotentProvider = idempotentProvider;
        this.lockProperties = lockProperties;
    }

    @Around("@annotation(com.chua.starter.strategy.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = MethodInvocationSupport.resolveMethod(joinPoint);
        Annotation annotation = AnnotationAttributeSupport.findAnnotation(method, ANNOTATION_NAME);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        Map<String, Object> attributes = AnnotationAttributeSupport.attributes(annotation);
        String key = buildKey(joinPoint, method, attributes);
        long timeout = resolveTimeout(attributes);
        TimeUnit timeUnit = resolveTimeUnit(attributes);
        boolean deleteOnSuccess = AnnotationAttributeSupport.getBoolean(attributes, "deleteOnSuccess", false);
        String message = AnnotationAttributeSupport.getString(attributes, "message", "请勿重复提交");

        if (!idempotentProvider.tryAcquire(key, timeout, timeUnit)) {
            throw new IdempotentException(message);
        }

        try {
            Object result = joinPoint.proceed();
            if (deleteOnSuccess) {
                idempotentProvider.remove(key);
            } else {
                idempotentProvider.complete(key, result == null ? NullValue.INSTANCE : result, timeout, timeUnit);
            }
            return result;
        } catch (Throwable ex) {
            idempotentProvider.release(key);
            throw ex;
        }
    }

    private String buildKey(ProceedingJoinPoint joinPoint, Method method, Map<String, Object> attributes) {
        String prefix = AnnotationAttributeSupport.getString(attributes, "prefix", "");
        if (!StringUtils.hasText(prefix)) {
            prefix = lockProperties.getIdempotent().getKeyPrefix();
        }

        String keyStrategy = AnnotationAttributeSupport.getEnumName(attributes, "keyStrategy", Idempotent.KeyStrategy.SPEL.name());
        String keyValue = switch (Idempotent.KeyStrategy.valueOf(keyStrategy)) {
            case SPEL -> generateSpelKey(joinPoint, method, attributes);
            case PARAMS_MD5 -> MethodInvocationSupport.buildArgsMd5(method, joinPoint.getArgs());
            case BODY_MD5 -> generateBodyMd5Key(joinPoint, method);
            case TOKEN -> generateTokenKey();
            case USER_METHOD -> generateUserMethodKey(method);
        };

        return prefix + keyValue;
    }

    private String generateSpelKey(ProceedingJoinPoint joinPoint, Method method, Map<String, Object> attributes) {
        String expression = AnnotationAttributeSupport.getString(attributes, "key", "");
        if (!StringUtils.hasText(expression)) {
            return MethodInvocationSupport.buildArgsMd5(method, joinPoint.getArgs());
        }

        String value = MethodInvocationSupport.evaluateToString(expression, joinPoint, method);
        return StringUtils.hasText(value) ? value : MethodInvocationSupport.buildArgsMd5(method, joinPoint.getArgs());
    }

    private String generateBodyMd5Key(ProceedingJoinPoint joinPoint, Method method) {
        return MethodInvocationSupport.buildRequestBodyMd5(method, joinPoint.getArgs());
    }

    private String generateTokenKey() {
        HttpServletRequest request = MethodInvocationSupport.currentRequest();
        if (request == null) {
            throw new IdempotentException("当前上下文中不存在 HTTP 请求，无法解析幂等 Token");
        }

        String token = request.getHeader(TOKEN_HEADER);
        if (!StringUtils.hasText(token)) {
            token = request.getParameter(TOKEN_PARAM);
        }
        if (!StringUtils.hasText(token)) {
            throw new IdempotentException("缺少幂等 Token，请在请求头或参数中提供: " + TOKEN_HEADER);
        }
        return token;
    }

    private String generateUserMethodKey(Method method) {
        HttpServletRequest request = MethodInvocationSupport.currentRequest();
        String userId = "anonymous";
        if (request != null) {
            String userHeader = request.getHeader(USER_HEADER);
            if (StringUtils.hasText(userHeader)) {
                userId = userHeader;
            } else if (request.getUserPrincipal() != null && StringUtils.hasText(request.getUserPrincipal().getName())) {
                userId = request.getUserPrincipal().getName();
            } else if (request.getSession(false) != null) {
                userId = request.getSession(false).getId();
            }
        }

        return userId + ":" + MethodInvocationSupport.buildMethodSignature(method);
    }

    private long resolveTimeout(Map<String, Object> attributes) {
        long timeout = AnnotationAttributeSupport.getLong(attributes, "timeout", -1L);
        if (timeout > 0) {
            return timeout;
        }
        return lockProperties.getIdempotent().getDefaultTimeout();
    }

    private TimeUnit resolveTimeUnit(Map<String, Object> attributes) {
        long timeout = AnnotationAttributeSupport.getLong(attributes, "timeout", -1L);
        if (timeout > 0 && attributes.get("timeUnit") instanceof TimeUnit timeUnit) {
            return timeUnit;
        }
        TimeUnit defaultTimeUnit = lockProperties.getIdempotent().getDefaultTimeUnit();
        return defaultTimeUnit == null ? TimeUnit.SECONDS : defaultTimeUnit;
    }
}
