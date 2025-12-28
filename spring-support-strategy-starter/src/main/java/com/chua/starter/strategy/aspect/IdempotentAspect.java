package com.chua.starter.strategy.aspect;

import com.chua.starter.strategy.annotation.Idempotent;
import com.chua.starter.strategy.exception.IdempotentException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性控制切面
 * <p>
 * 实现接口防重复提交功能，支持：
 * - 多种幂等键生成策略
 * - Redis分布式存储
 * - 本地内存存储（无Redis时降级）
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Aspect
public class IdempotentAspect {

    private static final String TOKEN_HEADER = "X-Idempotent-Token";

    private static final String TOKEN_PARAM = "_idempotentToken";

    private final ExpressionParser parser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 本地缓存（Redis不可用时的降级方案）
     */
    private final Map<String, Long> localCache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 生成幂等键
        String idempotentKey = generateKey(joinPoint, idempotent, method);

        // 检查是否重复提交
        boolean isFirstRequest = checkAndMark(idempotentKey, idempotent);

        if (!isFirstRequest) {
            log.warn("重复提交被拦截: key={}, method={}", idempotentKey, method.getName());
            throw new IdempotentException(idempotent.message());
        }

        try {
            Object result = joinPoint.proceed();

            // 成功后是否删除key
            if (idempotent.deleteOnSuccess()) {
                removeKey(idempotentKey);
            }

            return result;
        } catch (Throwable e) {
            // 异常时删除key，允许重试
            removeKey(idempotentKey);
            throw e;
        }
    }

    /**
     * 生成幂等键
     *
     * @param joinPoint  切点
     * @param idempotent 注解
     * @param method     方法
     * @return 幂等键
     */
    private String generateKey(ProceedingJoinPoint joinPoint, Idempotent idempotent, Method method) {
        String prefix = idempotent.prefix();
        Idempotent.KeyStrategy strategy = idempotent.keyStrategy();

        String key = switch (strategy) {
            case SPEL -> generateSpelKey(joinPoint, idempotent, method);
            case PARAMS_MD5 -> generateParamsMd5Key(joinPoint, method);
            case BODY_MD5 -> generateBodyMd5Key();
            case TOKEN -> generateTokenKey();
            case USER_METHOD -> generateUserMethodKey(method);
        };

        return prefix + key;
    }

    /**
     * 使用SpEL表达式生成key
     */
    private String generateSpelKey(ProceedingJoinPoint joinPoint, Idempotent idempotent, Method method) {
        String keyExpression = idempotent.key();

        if (!StringUtils.hasText(keyExpression)) {
            // 默认使用方法签名 + 参数MD5
            return generateParamsMd5Key(joinPoint, method);
        }

        if (!keyExpression.contains("#")) {
            return keyExpression;
        }

        EvaluationContext context = new MethodBasedEvaluationContext(
                null, method, joinPoint.getArgs(), parameterNameDiscoverer);

        // 添加request变量
        HttpServletRequest request = getRequest();
        if (request != null) {
            context.setVariable("request", request);
            context.setVariable("session", request.getSession(false));
        }

        String parsedKey = parser.parseExpression(keyExpression).getValue(context, String.class);
        return StringUtils.hasText(parsedKey) ? parsedKey : generateParamsMd5Key(joinPoint, method);
    }

    /**
     * 使用参数MD5生成key
     */
    private String generateParamsMd5Key(ProceedingJoinPoint joinPoint, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getDeclaringClass().getName());
        sb.append("#").append(method.getName());

        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg != null) {
                    sb.append(":").append(arg.hashCode());
                }
            }
        }

        return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 使用请求体MD5生成key
     */
    private String generateBodyMd5Key() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return "unknown";
        }

        // 使用请求参数作为替代
        Map<String, String[]> parameterMap = request.getParameterMap();
        TreeMap<String, String[]> sortedParams = new TreeMap<>(parameterMap);

        StringBuilder sb = new StringBuilder();
        sb.append(request.getRequestURI());
        sortedParams.forEach((k, v) -> sb.append(":").append(k).append("=").append(String.join(",", v)));

        return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 使用Token生成key
     */
    private String generateTokenKey() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return "unknown";
        }

        // 从请求头获取
        String token = request.getHeader(TOKEN_HEADER);

        // 从参数获取
        if (!StringUtils.hasText(token)) {
            token = request.getParameter(TOKEN_PARAM);
        }

        if (!StringUtils.hasText(token)) {
            throw new IdempotentException("缺少幂等性Token，请在请求头或参数中提供: " + TOKEN_HEADER);
        }

        return token;
    }

    /**
     * 使用用户+方法生成key
     */
    private String generateUserMethodKey(Method method) {
        HttpServletRequest request = getRequest();
        String userId = "anonymous";

        if (request != null) {
            // 尝试从常见位置获取用户标识
            String userHeader = request.getHeader("X-User-Id");
            if (StringUtils.hasText(userHeader)) {
                userId = userHeader;
            } else {
                String sessionId = request.getSession(true).getId();
                userId = sessionId;
            }
        }

        return userId + ":" + method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }

    /**
     * 检查并标记
     *
     * @param key        幂等键
     * @param idempotent 注解
     * @return 是否为首次请求
     */
    private boolean checkAndMark(String key, Idempotent idempotent) {
        long timeout = idempotent.timeout();
        TimeUnit timeUnit = idempotent.timeUnit();
        long timeoutMillis = timeUnit.toMillis(timeout);

        // 优先使用Redis
        if (stringRedisTemplate != null) {
            Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                    key, String.valueOf(System.currentTimeMillis()), timeout, timeUnit);
            return Boolean.TRUE.equals(success);
        }

        // 降级使用本地缓存
        return checkAndMarkLocal(key, timeoutMillis);
    }

    /**
     * 本地缓存检查并标记
     */
    private synchronized boolean checkAndMarkLocal(String key, long timeoutMillis) {
        long now = System.currentTimeMillis();

        // 清理过期数据
        localCache.entrySet().removeIf(entry -> now - entry.getValue() > timeoutMillis);

        Long existingTime = localCache.get(key);
        if (existingTime != null && now - existingTime < timeoutMillis) {
            return false;
        }

        localCache.put(key, now);
        return true;
    }

    /**
     * 删除幂等键
     */
    private void removeKey(String key) {
        if (stringRedisTemplate != null) {
            stringRedisTemplate.delete(key);
        } else {
            localCache.remove(key);
        }
    }

    /**
     * 获取HttpServletRequest
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
