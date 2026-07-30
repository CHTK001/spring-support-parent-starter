package com.chua.starter.rpc.support.spring;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Spring的RPC客户端实现
 * <p>
 * 使用Spring的ApplicationContext来查找和调用远程服务
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
public class SpringRpcClient implements RpcClient, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private final List<RpcRegistryConfig> registryConfigs;
    private final RpcConsumerConfig consumerConfig;
    private final String appName;
    private final Map<Class<?>, Object> proxyCache = new ConcurrentHashMap<>();

    public SpringRpcClient(List<RpcRegistryConfig> registryConfigs,
                          RpcConsumerConfig consumerConfig,
                          String appName) {
        this.registryConfigs = registryConfigs;
        this.consumerConfig = consumerConfig;
        this.appName = appName;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> targetType) {
        return (T) proxyCache.computeIfAbsent(targetType, this::createProxy);
    }

    /**
     * 创建代理对象
     */
    private <T> Object createProxy(Class<T> targetType) {
        return Proxy.newProxyInstance(
            targetType.getClassLoader(),
            new Class<?>[]{targetType},
            new SpringRpcInvocationHandler<>(targetType, applicationContext, consumerConfig)
        );
    }

    @Override
    public void close() throws Exception {
        proxyCache.clear();
        log.info("[Spring RPC] 客户端已关闭");
    }
}
