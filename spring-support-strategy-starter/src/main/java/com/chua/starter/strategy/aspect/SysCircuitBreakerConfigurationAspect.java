package com.chua.starter.strategy.aspect;

import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.strategy.entity.SysCircuitBreakerConfiguration;
import com.chua.starter.strategy.entity.SysCircuitBreakerRecord;
import com.chua.starter.strategy.service.SysCircuitBreakerConfigurationService;
import com.chua.starter.strategy.service.SysCircuitBreakerRecordService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于数据库配置的熔断切面
 * 
 * 支持从 SysCircuitBreakerConfiguration 数据表中读取熔断配置，实现动态熔断功能。
 * 通过匹配请求路径来应用熔断规则。
 * 
 * 主要功能：
 * 1. 动态加载熔断配置
 * 2. 实时创建和更新熔断器
 * 3. 记录熔断事件
 * 4. 支持降级处理
 *
 * <pre>
 * 执行流程：
 * ┌───────────────────────────────────────────────────────┐
 * │                 HTTP请求到达                         │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 * ┌───────────────────────────────────────────────────────┐
 * │     获取请求路径，查询对应的熔断配置                  │
 * │         (优先缓存，否则查询数据库)                     │
 * └─────────────────────────┬─────────────────────────────┘
 *                           ▼
 *              ┌────────────────────────┐
 *              │配置存在且status=1(启用)?│
 *              └───────────┬────────────┘
 *                    ┌─────┴─────┐
 *               No   │           │  Yes
 *                    ▼           ▼
 *          ┌─────────────┐  ┌─────────────────────────────┐
 *          │ 直接执行原方法│  │获取/创建CircuitBreaker(缓存) │
 *          └─────────────┘  └───────────┬─────────────────┘
 *                                       ▼
 *              ┌─────────────────────────────────────┐
 *              │   熔断器状态检查 (CLOSED/OPEN/HALF_OPEN) │
 *              └─────────────────┬───────────────────┘
 *                    ┌─────────┴─────────┐
 *              CLOSED│                   │OPEN
 *                    ▼                   ▼
 * ┌────────────────────────┐   ┌───────────────────────┐
 * │   执行目标方法         │   │ CallNotPermittedException│
 * └─────────┬──────────────┘   └─────────┬─────────────┘
 *           ▼                            ▼
 * ┌─────────────┐             ┌────────────────────────┐
 * │  成功/失败   │             │      记录熔断事件          │
 * │  统计更新    │             │  (写入CircuitBreakerRecord)│
 * └──────┬──────┘             └─────────┬──────────────┘
 *        ▼                               ▼
 * ┌─────────────────────┐     ┌───────────────────────┐
 * │失败率/慢调用率检查  │     │   handleFallback()       │
 * │超过阈值?→转为OPEN    │     └─────────┬─────────────┘
 * └─────────────────────┘           ┌─────┴─────┐
 *                           fallbackMethod│           │fallbackValue
 *                                   配置   ▼           ▼   配置
 *                              ┌───────────┐ ┌─────────────────┐
 *                              │执行降级方法│ │返回默认降级值/异常│
 *                              └───────────┘ └─────────────────┘
 *
 * 状态转换图：
 *        失败率/慢调用率超过阈值
 *   CLOSED ──────────────────→ OPEN
 *      ↑                         │
 *      │  调用成功            │ 等待waitDurationInOpenState
 *      │                         ▼
 *      └─────────────── HALF_OPEN
 *           调用失败→返回OPEN
 * </pre>
 * 
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
public class SysCircuitBreakerConfigurationAspect extends StaticMethodMatcherPointcutAdvisor 
        implements InitializingBean, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 默认错误消息
     */
    private static final String DEFAULT_ERROR_MESSAGE = "服务暂时不可用，请稍后再试";

    @Autowired
    private SysCircuitBreakerConfigurationService circuitBreakerConfigurationService;
    
    @Autowired
    private SysCircuitBreakerRecordService circuitBreakerRecordService;
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    /**
     * 缓存熔断器实例，key为接口路径
     */
    private final Map<String, CircuitBreaker> CIRCUIT_BREAKER_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 缓存熔断配置，key为接口路径
     */
    private final Map<String, SysCircuitBreakerConfiguration> CONFIG_CACHE = new ConcurrentHashMap<>();

    public SysCircuitBreakerConfigurationAspect(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        // 匹配所有 Controller 中的方法
        return false;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nullable MethodInvocation invocation) throws Throwable {
                if (invocation == null) {
                    throw new IllegalArgumentException("MethodInvocation cannot be null");
                }
                return invokeWithCircuitBreaker(invocation);
            }
        });
    }

    /**
     * 带熔断的执行方法
     *
     * @param invocation 方法调用
     * @return 执行结果
     * @throws Throwable 异常
     */
    private Object invokeWithCircuitBreaker(MethodInvocation invocation) throws Throwable {
        // 获取当前请求路径
        String requestPath = getCurrentRequestPath();
        if (StringUtils.isBlank(requestPath)) {
            log.debug("无法获取请求路径，直接执行方法");
            return invocation.proceed();
        }
        
        // 获取该路径对应的熔断配置
        SysCircuitBreakerConfiguration config = getCircuitBreakerConfiguration(requestPath);
        if (config == null || config.getSysCircuitBreakerStatus() == null || 
            config.getSysCircuitBreakerStatus() != 1) {
            log.debug("没有启用的熔断配置，直接执行方法，请求路径: {}", requestPath);
            return invocation.proceed();
        }
        
        // 获取或创建熔断器
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(config);
        
        try {
            log.debug("执行熔断检查，熔断器名称: {}, 状态: {}", 
                    config.getSysCircuitBreakerName(), circuitBreaker.getState());
            
            // 执行熔断检查
            return circuitBreaker.executeSupplier(() -> {
                try {
                    return invocation.proceed();
                } catch (Throwable throwable) {
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    } else if (throwable instanceof Error) {
                        throw (Error) throwable;
                    } else {
                        throw new RuntimeException(throwable);
                    }
                }
            });
        } catch (CallNotPermittedException e) {
            log.warn("熔断器已开启，请求被拒绝，请求路径: {}, 熔断器名称: {}", 
                    requestPath, config.getSysCircuitBreakerName());
            
            // 记录熔断事件
            createCircuitBreakerRecord(config, requestPath, circuitBreaker, e);
            
            // 执行降级方法或返回降级值
            return handleFallback(invocation, config, e);
        }
    }

    /**
     * 获取当前请求路径
     * 
     * @return 请求路径
     */
    private String getCurrentRequestPath() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.isNotBlank(contextPath) && !"/".equals(contextPath)) {
            if (requestUri.startsWith(contextPath)) {
                requestUri = requestUri.substring(contextPath.length());
            }
        }
        
        return requestUri;
    }

    /**
     * 获取熔断配置
     * 
     * @param requestPath 请求路径
     * @return 熔断配置
     */
    private SysCircuitBreakerConfiguration getCircuitBreakerConfiguration(String requestPath) {
        // 先从缓存中获取
        SysCircuitBreakerConfiguration cachedConfig = CONFIG_CACHE.get(requestPath);
        if (cachedConfig != null) {
            return cachedConfig;
        }
        
        // 从数据库查询
        SysCircuitBreakerConfiguration config = circuitBreakerConfigurationService.getByPath(requestPath);
        if (config != null) {
            CONFIG_CACHE.put(requestPath, config);
        }
        
        return config;
    }

    /**
     * 获取或创建熔断器
     * 
     * @param config 熔断配置
     * @return 熔断器
     */
    private CircuitBreaker getOrCreateCircuitBreaker(SysCircuitBreakerConfiguration config) {
        String path = config.getSysCircuitBreakerPath();
        
        CircuitBreaker circuitBreaker = CIRCUIT_BREAKER_CACHE.get(path);
        if (circuitBreaker != null) {
            return circuitBreaker;
        }
        
        // 创建熔断器配置
        CircuitBreakerConfig cbConfig = buildCircuitBreakerConfig(config);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker(path, cbConfig);
        
        // 注册事件监听器
        registerEventListeners(circuitBreaker, config);
        
        CIRCUIT_BREAKER_CACHE.put(path, circuitBreaker);
        log.info("创建熔断器: name={}, path={}", config.getSysCircuitBreakerName(), path);
        
        return circuitBreaker;
    }

    /**
     * 构建熔断器配置
     * 
     * @param config 熔断配置
     * @return 熔断器配置
     */
    private CircuitBreakerConfig buildCircuitBreakerConfig(SysCircuitBreakerConfiguration config) {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
        
        // 失败率阈值
        if (config.getFailureRateThreshold() != null) {
            builder.failureRateThreshold(config.getFailureRateThreshold().floatValue());
        }
        
        // 慢调用率阈值
        if (config.getSlowCallRateThreshold() != null) {
            builder.slowCallRateThreshold(config.getSlowCallRateThreshold().floatValue());
        }
        
        // 慢调用持续时间阈值
        if (config.getSlowCallDurationThresholdMs() != null) {
            builder.slowCallDurationThreshold(Duration.ofMillis(config.getSlowCallDurationThresholdMs()));
        }
        
        // 最小调用次数
        if (config.getMinimumNumberOfCalls() != null) {
            builder.minimumNumberOfCalls(config.getMinimumNumberOfCalls());
        }
        
        // 滑动窗口大小
        if (config.getSlidingWindowSize() != null) {
            builder.slidingWindowSize(config.getSlidingWindowSize());
        }
        
        // 滑动窗口类型
        if (StringUtils.isNotBlank(config.getSlidingWindowType())) {
            CircuitBreakerConfig.SlidingWindowType windowType = 
                    "TIME_BASED".equalsIgnoreCase(config.getSlidingWindowType()) 
                            ? CircuitBreakerConfig.SlidingWindowType.TIME_BASED 
                            : CircuitBreakerConfig.SlidingWindowType.COUNT_BASED;
            builder.slidingWindowType(windowType);
        }
        
        // 熔断器打开状态的等待时间
        if (config.getWaitDurationInOpenStateMs() != null) {
            builder.waitDurationInOpenState(Duration.ofMillis(config.getWaitDurationInOpenStateMs()));
        }
        
        // 半开状态允许的调用次数
        if (config.getPermittedCallsInHalfOpenState() != null) {
            builder.permittedNumberOfCallsInHalfOpenState(config.getPermittedCallsInHalfOpenState());
        }
        
        // 自动从打开状态转换到半开状态
        if (config.getAutomaticTransitionFromOpen() != null) {
            builder.automaticTransitionFromOpenToHalfOpenEnabled(config.getAutomaticTransitionFromOpen());
        }
        
        return builder.build();
    }

    /**
     * 注册事件监听器
     * 
     * @param circuitBreaker 熔断器
     * @param config         熔断配置
     */
    private void registerEventListeners(CircuitBreaker circuitBreaker, SysCircuitBreakerConfiguration config) {
        // 状态转换事件
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.info("熔断器状态转换: name={}, from={}, to={}", 
                            config.getSysCircuitBreakerName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                    
                    // 记录状态转换
                    SysCircuitBreakerRecord record = new SysCircuitBreakerRecord();
                    record.setSysCircuitBreakerId(config.getSysCircuitBreakerId());
                    record.setSysCircuitBreakerName(config.getSysCircuitBreakerName());
                    record.setSysCircuitBreakerPath(config.getSysCircuitBreakerPath());
                    record.setCircuitBreakerState(event.getStateTransition().getToState().name());
                    record.setTriggerReason("STATE_TRANSITION");
                    record.setTriggerTime(LocalDateTime.now());
                    circuitBreakerRecordService.saveCircuitBreakerRecord(record);
                })
                .onFailureRateExceeded(event -> {
                    log.warn("熔断器失败率超过阈值: name={}, failureRate={}", 
                            config.getSysCircuitBreakerName(), event.getFailureRate());
                })
                .onSlowCallRateExceeded(event -> {
                    log.warn("熔断器慢调用率超过阈值: name={}, slowCallRate={}", 
                            config.getSysCircuitBreakerName(), event.getSlowCallRate());
                });
    }

    /**
     * 创建熔断记录
     * 
     * @param config         熔断配置
     * @param requestPath    请求路径
     * @param circuitBreaker 熔断器
     * @param exception      异常
     */
    private void createCircuitBreakerRecord(SysCircuitBreakerConfiguration config, 
                                            String requestPath,
                                            CircuitBreaker circuitBreaker,
                                            Exception exception) {
        try {
            SysCircuitBreakerRecord record = new SysCircuitBreakerRecord();
            record.setSysCircuitBreakerId(config.getSysCircuitBreakerId());
            record.setSysCircuitBreakerName(config.getSysCircuitBreakerName());
            record.setSysCircuitBreakerPath(requestPath);
            record.setCircuitBreakerState(circuitBreaker.getState().name());
            record.setTriggerReason("CALL_NOT_PERMITTED");
            record.setFailureRate((double) circuitBreaker.getMetrics().getFailureRate());
            record.setSlowCallRate((double) circuitBreaker.getMetrics().getSlowCallRate());
            record.setExceptionMessage(exception.getMessage());
            record.setTriggerTime(LocalDateTime.now());
            
            HttpServletRequest request = getRequest();
            if (request != null) {
                record.setClientIp(getIpAddress(request));
                record.setRequestMethod(request.getMethod());
            }
            
            circuitBreakerRecordService.saveCircuitBreakerRecord(record);
        } catch (Exception e) {
            log.error("创建熔断记录失败", e);
        }
    }

    /**
     * 处理降级
     * 
     * @param invocation 方法调用
     * @param config     熔断配置
     * @param exception  异常
     * @return 降级结果
     */
    private Object handleFallback(MethodInvocation invocation, 
                                  SysCircuitBreakerConfiguration config,
                                  Exception exception) {
        // 尝试执行降级方法
        String fallbackMethod = config.getFallbackMethod();
        if (StringUtils.isNotBlank(fallbackMethod)) {
            try {
                return executeFallbackMethod(invocation, fallbackMethod, exception);
            } catch (Exception e) {
                log.error("执行降级方法失败: {}", fallbackMethod, e);
            }
        }
        
        // 返回降级值
        String fallbackValue = config.getFallbackValue();
        if (StringUtils.isNotBlank(fallbackValue)) {
            // TODO: 根据返回类型解析 JSON
            return fallbackValue;
        }
        
        // 抛出异常
        throw new RuntimeException(DEFAULT_ERROR_MESSAGE);
    }

    /**
     * 执行降级方法
     * 
     * @param invocation         方法调用
     * @param fallbackMethodName 降级方法名称
     * @param exception          异常
     * @return 降级方法执行结果
     */
    private Object executeFallbackMethod(MethodInvocation invocation, 
                                         String fallbackMethodName, 
                                         Exception exception) throws Exception {
        Object target = invocation.getThis();
        if (target == null) {
            throw new RuntimeException("执行降级方法失败：目标对象为空");
        }
        
        Class<?> targetClass = target.getClass();
        Method originalMethod = invocation.getMethod();
        
        // 查找降级方法
        Method fallbackMethod = findFallbackMethod(targetClass, fallbackMethodName, originalMethod);
        if (fallbackMethod == null) {
            throw new RuntimeException("未找到降级方法: " + fallbackMethodName);
        }
        
        // 执行降级方法
        Object[] args = invocation.getArguments();
        Object[] fallbackArgs = new Object[args.length + 1];
        System.arraycopy(args, 0, fallbackArgs, 0, args.length);
        fallbackArgs[args.length] = exception;
        
        fallbackMethod.setAccessible(true);
        return fallbackMethod.invoke(target, fallbackArgs);
    }

    /**
     * 查找降级方法
     * 
     * @param targetClass        目标类
     * @param fallbackMethodName 降级方法名称
     * @param originalMethod     原始方法
     * @return 降级方法
     */
    private Method findFallbackMethod(Class<?> targetClass, String fallbackMethodName, Method originalMethod) {
        try {
            // 尝试查找带异常参数的降级方法
            Class<?>[] paramTypes = originalMethod.getParameterTypes();
            Class<?>[] fallbackParamTypes = new Class[paramTypes.length + 1];
            System.arraycopy(paramTypes, 0, fallbackParamTypes, 0, paramTypes.length);
            fallbackParamTypes[paramTypes.length] = Exception.class;
            
            return targetClass.getDeclaredMethod(fallbackMethodName, fallbackParamTypes);
        } catch (NoSuchMethodException e) {
            try {
                // 尝试查找不带异常参数的降级方法
                return targetClass.getDeclaredMethod(fallbackMethodName, originalMethod.getParameterTypes());
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }

    /**
     * 获取当前 HTTP 请求
     * 
     * @return HTTP 请求对象
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取客户端 IP 地址
     * 
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0];
        }
        return ip;
    }

    /**
     * 清除配置缓存
     */
    public void clearCache() {
        CONFIG_CACHE.clear();
        CIRCUIT_BREAKER_CACHE.clear();
        log.info("熔断配置缓存已清除");
    }
}
