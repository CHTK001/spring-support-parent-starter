package com.chua.starter.strategy.aspect;

import com.chua.common.support.task.debounce.Debounce;
import com.chua.common.support.task.debounce.DebounceException;
import com.chua.common.support.task.debounce.DebounceManager;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Debounce 防抖注解切面
 * <p>
 * 在 Spring 环境中实现对 @Debounce 注解的 AOP 扫描和防抖处理。
 * 防止短时间内重复调用同一方法。
 * </p>
 * <p>
 * 支持多种防抖模式：全局、IP、用户、会话。
 * </p>
 *
 * <pre>
 * 执行流程：
 * ┌───────────────────────────────────────────────────────┐
 * │                    方法调用                          │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 *                  ┌────────────────┐
 *                  │  enabled=true? │
 *                  └───────┬────────┘
 *                    ┌─────┴─────┐
 *               No   │           │  Yes
 *                    ▼           ▼
 *          ┌─────────────┐  ┌─────────────────────────────┐
 *          │ 直接执行原方法│  │       构建防抖Key               │
 *          └─────────────┘  └───────────┬─────────────────┘
 *                                       ▼
 *   ┌───────────────────────────────────────────────────┐
 *   │  Key构建规则(mode)                                    │
 *   │  - global: debounce:class#method:global              │
 *   │  - ip: debounce:class#method:ip:{clientIp}           │
 *   │  - user: debounce:class#method:user:{userId}         │
 *   │  - session: debounce:class#method:session:{sessionId}│
 *   └─────────────────────────┬─────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │         解析防抖时间(value)                           │
 * │  支持格式：1000(ms), 1S, 1MIN, 1H                       │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │   debounceManager.tryAcquire(key, durationMs)          │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 *              ┌────────────────────┐
 *              │ 防抖期内是否已请求？   │
 *              └─────────┬──────────┘
 *                  ┌─────┴─────┐
 *             No   │           │  Yes
 *          (首次请求)│           │ (被防抖)
 *                  ▼           ▼
 *       ┌───────────────┐  ┌───────────────────┐
 *       │设置防抖标记       │  │ fallbackMethod配置?│
 *       └───────┬───────┘  └────────┬──────────┘
 *               ▼                 ┌────┴────┐
 *       ┌───────────────┐    Yes │         │ No
 *       │   执行目标方法   │        ▼         ▼
 *       └───────┬───────┘  ┌───────────┐ ┌─────────────────┐
 *               ▼          │执行降级方法│ │抛出DebounceException│
 *       ┌───────────────┐  └───────────┘ └─────────────────┘
 *       │    返回结果     │
 *       └───────────────┘
 * </pre>
 *
 * @author CH
 * @since 2024/12/07
 * @version 1.0.0
 */
@Slf4j
@Aspect
@Component
@Order(99)
public class DebounceAspect {

    /**
     * 防抖管理器
     */
    private final DebounceManager debounceManager = DebounceManager.getInstance();

    /**
     * 切入点：所有标注 @Debounce 注解的方法
     */
    @Pointcut("@annotation(com.chua.common.support.task.debounce.Debounce)")
    public void debouncePointcut() {
    }

    /**
     * 环绕通知：执行防抖逻辑
     *
     * @param joinPoint 切入点
     * @return 方法返回值
     * @throws Throwable 异常
     */
    @Around("debouncePointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Debounce annotation = method.getAnnotation(Debounce.class);

        // 检查是否启用防抖
        if (!annotation.enabled()) {
            return joinPoint.proceed();
        }

        // 生成防抖 Key
        String debounceKey = buildDebounceKey(annotation, joinPoint);

        // 解析防抖时间
        long durationMs = parseDuration(annotation.value());

        // 尝试获取防抖锁
        boolean acquired = debounceManager.tryAcquire(debounceKey, durationMs);

        if (acquired) {
            log.debug("[防抖]通过 - Key: {}, 方法: {}", debounceKey, method.getName());
            return joinPoint.proceed();
        }

        // 防抖触发
        log.warn("[防抖]触发 - Key: {}, 方法: {}, 消息: {}", debounceKey, method.getName(), annotation.message());

        // 检查是否有降级方法
        String fallbackMethod = annotation.fallbackMethod();
        if (StringUtils.isNotBlank(fallbackMethod)) {
            return invokeFallback(joinPoint, fallbackMethod);
        }

        throw new DebounceException(annotation.message());
    }

    /**
     * 构建防抖 Key
     *
     * @param annotation 注解
     * @param joinPoint  切入点
     * @return 防抖 Key
     */
    private String buildDebounceKey(Debounce annotation, ProceedingJoinPoint joinPoint) {
        StringBuilder keyBuilder = new StringBuilder("debounce:");

        // 添加名称或方法标识
        if (StringUtils.isNotBlank(annotation.name())) {
            keyBuilder.append(annotation.name()).append(":");
        } else {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            keyBuilder.append(signature.getDeclaringTypeName())
                    .append("#")
                    .append(signature.getName())
                    .append(":");
        }

        // 根据 mode 构建 key
        String mode = annotation.mode();
        switch (mode.toLowerCase()) {
            case "ip" -> {
                keyBuilder.append("ip:");
                keyBuilder.append(getClientIp());
            }
            case "user" -> {
                keyBuilder.append("user:");
                keyBuilder.append(getUserId());
            }
            case "session" -> {
                keyBuilder.append("session:");
                keyBuilder.append(getSessionId());
            }
            default -> keyBuilder.append("global");
        }

        // 添加自定义 key
        if (StringUtils.isNotBlank(annotation.key())) {
            keyBuilder.append(":").append(annotation.key());
        }

        return keyBuilder.toString();
    }

    /**
     * 解析防抖时间
     *
     * @param value 时间表达式
     * @return 毫秒数
     */
    private long parseDuration(String value) {
        if (value == null || value.isEmpty()) {
            return 1000L;
        }

        value = value.toUpperCase().trim();

        try {
            // 纯数字，按毫秒处理
            if (value.matches("\\d+")) {
                return Long.parseLong(value);
            }

            // 带单位
            if (value.endsWith("MS")) {
                return Long.parseLong(value.substring(0, value.length() - 2));
            } else if (value.endsWith("S")) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 1000;
            } else if (value.endsWith("MIN")) {
                return Long.parseLong(value.substring(0, value.length() - 3)) * 60 * 1000;
            } else if (value.endsWith("H")) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 60 * 60 * 1000;
            }
        } catch (NumberFormatException e) {
            log.warn("[防抖]无法解析时间表达式: {}，使用默认值 1000ms", value);
        }

        return 1000L;
    }

    /**
     * 调用降级方法
     *
     * @param joinPoint      切入点
     * @param fallbackMethod 降级方法名
     * @return 降级方法返回值
     */
    private Object invokeFallback(ProceedingJoinPoint joinPoint, String fallbackMethod) {
        try {
            Object target = joinPoint.getTarget();
            Method method = target.getClass().getDeclaredMethod(fallbackMethod, 
                    ((MethodSignature) joinPoint.getSignature()).getParameterTypes());
            method.setAccessible(true);
            return method.invoke(target, joinPoint.getArgs());
        } catch (NoSuchMethodException e) {
            log.warn("[防抖]降级方法不存在: {}", fallbackMethod);
        } catch (Exception e) {
            log.error("[防抖]调用降级方法失败: {}", fallbackMethod, e);
        }
        return null;
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                var request = attrs.getRequest();
                String ip = RequestUtils.getIpAddress(request);
                if (ip != null) {
                    return ip;
                }
                ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                return ip != null ? ip.split(",")[0].trim() : "unknown";
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    /**
     * 获取用户 ID
     */
    private String getUserId() {
        try {
            Object userInfo = RequestUtils.getUserId();
            if (userInfo != null) {
                return userInfo.toString();
            }
        } catch (Exception ignored) {
        }

        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                Object userId = attrs.getRequest().getAttribute("userId");
                if (userId != null) {
                    return userId.toString();
                }
            }
        } catch (Exception ignored) {
        }

        return "anonymous";
    }

    /**
     * 获取会话 ID
     */
    private String getSessionId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getSession().getId();
            }
        } catch (Exception ignored) {
        }
        return "no-session";
    }
}
