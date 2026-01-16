package com.chua.starter.rpc.support.proxy;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * RPC 代理工厂管理器
 * <p>按优先级尝试不同的代理工厂创建代理</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/07
 */
@Slf4j
public class RpcProxyFactoryManager {

    private static final RpcProxyFactoryManager INSTANCE = new RpcProxyFactoryManager();

    private final List<RpcProxyFactory> factories;

    private RpcProxyFactoryManager() {
        this.factories = new ArrayList<>();
        // 注册默认工厂
        if (JavassistRpcProxyFactory.isAvailable()) {
            factories.add(new JavassistRpcProxyFactory());
        }
        factories.add(new JdkRpcProxyFactory());
        // 按优先级排序
        factories.sort(Comparator.comparingInt(RpcProxyFactory::getOrder));
    }

    public static RpcProxyFactoryManager getInstance() {
        return INSTANCE;
    }

    /**
     * 创建代理对象
     * <p>按优先级依次尝试各代理工厂，直到成功创建</p>
     *
     * @param classLoader 类加载器
     * @param interfaces  接口列表
     * @param handler     调用处理器
     * @return 代理对象
     * @throws RpcProxyCreationException 如果所有工厂都创建失败
     */
    public Object createProxy(ClassLoader classLoader, List<Class<?>> interfaces, InvocationHandler handler) {
        Exception lastException = null;

        for (RpcProxyFactory factory : factories) {
            try {
                Object proxy = factory.createProxy(classLoader, interfaces, handler);
                if (log.isDebugEnabled()) {
                    log.debug("Created proxy using {}", factory.getClass().getSimpleName());
                }
                return proxy;
            } catch (Exception e) {
                log.warn("Proxy creation failed with {}, trying next factory: {}",
                        factory.getClass().getSimpleName(), e.getMessage());
                lastException = e;
            }
        }

        throw new RpcProxyCreationException("Failed to create proxy with all available factories. Interfaces: " + interfaces, lastException);
    }

    /**
     * 注册自定义代理工厂
     *
     * @param factory 代理工厂
     */
    public void registerFactory(RpcProxyFactory factory) {
        factories.add(factory);
        factories.sort(Comparator.comparingInt(RpcProxyFactory::getOrder));
    }
}
