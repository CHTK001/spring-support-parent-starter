package com.chua.starter.lock.aspect;

import com.chua.common.support.task.layer.idempotent.IdempotentProvider;
import com.chua.starter.lock.annotation.Idempotent;
import com.chua.starter.lock.exception.IdempotentException;
import com.chua.starter.lock.properties.LockProperties;
import com.chua.starter.lock.support.MethodInvocationSupport;
import com.chua.starter.lock.support.NullValue;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 幂等切面。
 *
 * @author CH
 * @since 2026-03-28
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class IdempotentAspect {

    private static final String TOKEN_HEADER = "X-Idempotent-Token";
    private static final String TOKEN_PARAM = "_idempotentToken";
    private static final String USER_HEADER = "X-User-Id";

    private final IdempotentProvider idempotentProvider;
    private final LockProperties lockProperties;

    public IdempotentAspect(IdempotentProvider idempotentProvider, LockProperties lockProperties) {
        this.idempotentProvider = idempotentProvider;
        this.lockProperties = lockProperties;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        Method method = MethodInvocationSupport.resolveMethod(joinPoint);
        String idempotentKey = buildKey(joinPoint, method, idempotent);
        long timeout = resolveTimeout(idempotent);
        TimeUnit timeUnit = resolveTimeUnit(idempotent);

        if (!idempotentProvider.tryAcquire(idempotentKey, timeout, timeUnit)) {
            Object previousResult = idempotentProvider.getResult(idempotentKey);
            boolean hasPreviousResult = previousResult != null || previousResult == NullValue.INSTANCE;
            return handleDuplicate(joinPoint, method, idempotent, previousResult, hasPreviousResult);
        }

        try {
            Object result = joinPoint.proceed();
            if (idempotent.deleteOnSuccess()) {
                idempotentProvider.remove(idempotentKey);
            } else {
                idempotentProvider.complete(idempotentKey, result == null ? NullValue.INSTANCE : result, timeout, timeUnit);
            }
            return result;
        } catch (Throwable ex) {
            idempotentProvider.release(idempotentKey);
            throw ex;
        }
    }

    private Object handleDuplicate(ProceedingJoinPoint joinPoint, Method method, Idempotent idempotent,
                                   Object previousResult, boolean hasPreviousResult) throws Throwable {
        if (previousResult == NullValue.INSTANCE) {
            previousResult = null;
        }

        return switch (idempotent.duplicateStrategy()) {
            case RETURN_NULL -> null;
            case RETURN_PREVIOUS -> {
                if (hasPreviousResult) {
                    yield previousResult;
                }
                throw new IdempotentException(idempotent.message());
            }
            case FALLBACK -> {
                if (!StringUtils.hasText(idempotent.fallbackMethod())) {
                    throw new IdempotentException("duplicateStrategy=FALLBACK 但未配置 fallbackMethod");
                }
                yield MethodInvocationSupport.invokeFallback(joinPoint, method, idempotent.fallbackMethod());
            }
            case EXCEPTION -> throw new IdempotentException(idempotent.message());
        };
    }

    private String buildKey(ProceedingJoinPoint joinPoint, Method method, Idempotent idempotent) {
        String prefix = StringUtils.hasText(idempotent.prefix())
                ? idempotent.prefix()
                : lockProperties.getIdempotent().getKeyPrefix();

        String key = switch (idempotent.keyStrategy()) {
            case SPEL -> generateSpelKey(joinPoint, method, idempotent);
            case PARAMS_MD5 -> MethodInvocationSupport.buildArgsMd5(method, joinPoint.getArgs());
            case BODY_MD5 -> generateBodyMd5Key(joinPoint, method);
            case TOKEN -> generateTokenKey();
            case USER_METHOD -> generateUserMethodKey(method);
        };

        return prefix + key;
    }

    private String generateSpelKey(ProceedingJoinPoint joinPoint, Method method, Idempotent idempotent) {
        if (!StringUtils.hasText(idempotent.key())) {
            return MethodInvocationSupport.buildArgsMd5(method, joinPoint.getArgs());
        }

        String value = MethodInvocationSupport.evaluateToString(idempotent.key(), joinPoint, method);
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

    private long resolveTimeout(Idempotent idempotent) {
        if (idempotent.timeout() > 0) {
            return idempotent.timeout();
        }
        return lockProperties.getIdempotent().getDefaultTimeout();
    }

    private TimeUnit resolveTimeUnit(Idempotent idempotent) {
        if (idempotent.timeout() > 0) {
            return idempotent.timeUnit();
        }
        TimeUnit defaultTimeUnit = lockProperties.getIdempotent().getDefaultTimeUnit();
        return defaultTimeUnit == null ? TimeUnit.SECONDS : defaultTimeUnit;
    }
}
