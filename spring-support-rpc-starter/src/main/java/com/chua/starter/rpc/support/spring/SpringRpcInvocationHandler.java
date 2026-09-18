package com.chua.starter.rpc.support.spring;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.reflection.ReflectUtils;
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
* 处理RPC调用，支持拦截器、重试、熔断等功能。
* 底层委托真实的 {@link RpcClient} 发起网络调用，避免旧实现的「本地 Bean + 反射直调」伪远程问题。
* </p>
*
* @since 2025-03-20
* @author CH
 */
@Slf4j
public class SpringRpcInvocationHandler<T> implements InvocationHandler {

    /** 目标类型 */
    private final Class<T> targetType;
    /** RPC客户端 */
    private final RpcClient rpcClient;
    /** Application上下文 */
    private final ApplicationContext applicationContext;
    /** 消费者配置 */
    private final RpcConsumerConfig consumerConfig;
    /** 重试policy */
    private final RpcRetryPolicy retryPolicy;
    /** Circuitbreaker管理器 */
    private RpcCircuitBreakerManager circuitBreakerManager;
    /** 拦截器 */
    private List<RpcInterceptor> interceptors;

    /**
    * 创建 springrpcinvocation处理器 实例
    * @param targetType 目标类型
    * @param rpcClient rpc客户端
    * @param applicationContext application上下文
    * @param consumerConfig consumer配置
    */
    public SpringRpcInvocationHandler(Class<T> targetType,
                                      RpcClient rpcClient,
                                      ApplicationContext applicationContext,
                                      RpcConsumerConfig consumerConfig) {
        this.targetType = targetType;
        this.rpcClient = rpcClient;
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
    /** 调用 */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
 // 处理对象方法
        if (method.getDeclaringClass() == Object.class) {
            return ReflectUtils.invoke(this, method.getName(), method.getReturnType(), method.getParameterTypes(), args);
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
 // 通过真实 RPC 客户端获取远程服务代理（网络调用），而非本地 Bean 直调
                T remote = rpcClient.get(targetType);
                Object result = ReflectUtils.invoke(remote, method.getName(), method.getReturnType(), method.getParameterTypes(), args);

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

                // 统一业务/传输语义：业务异常不重试，避免放大非幂等接口副作用
                if (e instanceof com.chua.common.support.network.rpc.RpcException re && re.isBusiness()) {
                    throw e;
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
    *
    * @deprecated 伪远程实现遗留方法，已由 {@link #rpcClient} 网络调用替代
    * @return findBean的结果
    */
    @Deprecated
    private T findBean() {
        try {
            return (T) applicationContext.getBean(targetType);
        } catch (Exception e) {
            log.debug("[Spring RPC] 未找到Bean: {}", targetType.getName());
            return null;
        }
    }

    /**
    * 创建重试策略
    * @param config 配置
    * @return 创建重试policy的结果
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

    /**
    * 读取布尔值
    *
    * @param config 配置
    * @param methodName 方法名称
    * @return 读取布尔值的结果
    */
    private Boolean readBoolean(RpcConsumerConfig config, String methodName) {
        Object value = invokeOptionalGetter(config, methodName);
        return value instanceof Boolean bool ? bool : null;
    }

    /**
    * 读取Integer
    *
    * @param config 配置
    * @param methodName 方法名称
    * @return 读取integer的结果
    */
    private Integer readInteger(RpcConsumerConfig config, String methodName) {
        Object value = invokeOptionalGetter(config, methodName);
        return value instanceof Integer integer ? integer : null;
    }

    /**
    * 调用期权getter
    *
    * @param config 配置
    * @param methodName 方法名称
    * @return invoke期权getter的结果
    */
    private Object invokeOptionalGetter(RpcConsumerConfig config, String methodName) {
        if (config == null) {
            return null;
        }
        Method method = ReflectUtils.findMethod(config.getClass(), methodName);
        if (method == null) {
            return null;
        }
        return ReflectUtils.invoke(config, method.getName(), method.getReturnType());
    }
}
