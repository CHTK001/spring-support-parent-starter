package com.chua.starter.rpc.support.spring;

import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.starter.rpc.support.circuitbreaker.RpcCircuitBreaker;
import com.chua.starter.rpc.support.circuitbreaker.RpcCircuitBreakerManager;
import com.chua.starter.rpc.support.interceptor.RpcInterceptor;
import com.chua.starter.rpc.support.retry.RpcRetryPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

/**
 * Spring RPC调用处理器
 * <p>
 * 处理RPC调用，支持拦截器、重试、熔断等功能
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
public class SpringRpcInvocationHandler<T> implements InvocationHandler {

    private final Class<T> targetType;
    private final ApplicationContext applicationContext;
    private final RpcConsumerConfig consumerConfig;
    private final RpcRetryPolicy retryPolicy;
    private RpcCircuitBreakerManager circuitBreakerManager;
    private List<RpcInterceptor> interceptors;

    public SpringRpcInvocationHandler(Class<T> targetType,
                                     ApplicationContext applicationContext,
                                     RpcConsumerConfig consumerConfig) {
        this.targetType = targetType;
        this.applicationContext = applicationContext;
        this.consumerConfig = consumerConfig;
        this.retryPolicy = createRetryPolicy(consumerConfig);

        // 尝试获取熔断器管理器
        try {
            this.circuitBreakerManager = applicationContext.getBean(RpcCircuitBreakerManager.class);
        } catch (Exception e) {
            log.debug("[Spring RPC] 未找到熔断器管理器");
        }

        // 获取所有拦截器
        try {
            this.interceptors = applicationContext.getBeansOfType(RpcInterceptor.class)
                .values().stream()
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .toList();
        } catch (Exception e) {
            log.debug("[Spring RPC] 未找到拦截器");
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 处理Object方法
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        String serviceName = targetType.getName() + "." + method.getName();

        // 前置拦截
        if (interceptors != null) {
            for (RpcInterceptor interceptor : interceptors) {
                if (!interceptor.preHandle(method, args)) {
                    log.warn("[Spring RPC] 拦截器拒绝调用: {}", serviceName);
                    return null;
                }
            }
        }

        // 熔断器检查
        RpcCircuitBreaker circuitBreaker = null;
        Boolean circuitBreakerEnabled = readBoolean(consumerConfig, "getCircuitBreakerEnabled");
        if (circuitBreakerManager != null && Boolean.TRUE.equals(circuitBreakerEnabled)) {
            circuitBreaker = circuitBreakerManager.getOrCreate(serviceName);
            if (!circuitBreaker.tryAcquire()) {
                throw new RuntimeException("服务熔断中: " + serviceName);
            }
        }

        // 重试逻辑
        int retryCount = 0;
        Throwable lastException = null;

        while (retryCount <= retryPolicy.getMaxRetries()) {
            try {
                // 查找Spring Bean
                T bean = findBean();
                if (bean == null) {
                    throw new RuntimeException("未找到服务实现: " + targetType.getName());
                }

                // 执行调用
                Object result = method.invoke(bean, args);

                // 记录成功
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess();
                }

                // 后置处理
                if (interceptors != null) {
                    for (RpcInterceptor interceptor : interceptors) {
                        result = interceptor.postHandle(method, args, result);
                    }
                }

                // 最终处理
                if (interceptors != null) {
                    for (RpcInterceptor interceptor : interceptors) {
                        interceptor.afterCompletion(method, args);
                    }
                }

                return result;

            } catch (Throwable e) {
                lastException = e;

                // 记录失败
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }

                // 异常处理
                if (interceptors != null) {
                    for (RpcInterceptor interceptor : interceptors) {
                        try {
                            interceptor.handleException(method, args, e);
                        } catch (Throwable ignored) {
                            // 拦截器可能会抛出新异常
                        }
                    }
                }

                // 判断是否可重试
                if (retryPolicy.isRetryableException(e) && retryPolicy.shouldRetry(retryCount)) {
                    log.warn("[Spring RPC] 调用失败，准备重试: service={}, retryCount={}",
                        serviceName, retryCount);
                    Thread.sleep(retryPolicy.calculateDelay(retryCount).toMillis());
                    retryCount++;
                } else {
                    throw e;
                }
            }
        }

        throw lastException != null ? lastException : new RuntimeException("调用失败");
    }

    /**
     * 查找Spring Bean
     */
    private T findBean() {
        try {
            return applicationContext.getBean(targetType);
        } catch (Exception e) {
            log.debug("[Spring RPC] 未找到Bean: {}", targetType.getName());
            return null;
        }
    }

    /**
     * 创建重试策略
     */
    private RpcRetryPolicy createRetryPolicy(RpcConsumerConfig config) {
        RpcRetryPolicy policy = new RpcRetryPolicy();

        Boolean retryEnabled = readBoolean(config, "getRetryEnabled");
        if (retryEnabled != null) {
            policy.setEnabled(retryEnabled);
        }

        if (config.getRetries() != null) {
            policy.setMaxRetries(config.getRetries());
        }

        Integer retryDelay = readInteger(config, "getRetryDelay");
        if (retryDelay != null) {
            policy.setInitialDelay(Duration.ofMillis(retryDelay));
        }

        return policy;
    }

    private Boolean readBoolean(RpcConsumerConfig config, String methodName) {
        Object value = invokeOptionalGetter(config, methodName);
        return value instanceof Boolean bool ? bool : null;
    }

    private Integer readInteger(RpcConsumerConfig config, String methodName) {
        Object value = invokeOptionalGetter(config, methodName);
        return value instanceof Integer integer ? integer : null;
    }

    private Object invokeOptionalGetter(RpcConsumerConfig config, String methodName) {
        if (config == null) {
            return null;
        }
        try {
            Method method = config.getClass().getMethod(methodName);
            return method.invoke(config);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
