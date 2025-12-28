package com.chua.starter.rpc.support.proxy;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.util.List;

/**
 * Javassist 代理工厂实现
 * <p>当 Dubbo 依赖存在时可用，性能优于 JDK 代理</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/07
 */
@Slf4j
public class JavassistRpcProxyFactory implements RpcProxyFactory {

    private static final boolean JAVASSIST_AVAILABLE;

    static {
        boolean available = false;
        try {
            Class.forName("org.apache.dubbo.common.bytecode.Proxy");
            available = true;
        } catch (ClassNotFoundException ignored) {
            // Javassist/Dubbo not available
        }
        JAVASSIST_AVAILABLE = available;
    }

    @Override
    public Object createProxy(ClassLoader classLoader, List<Class<?>> interfaces, InvocationHandler handler) {
        if (!JAVASSIST_AVAILABLE) {
            throw new UnsupportedOperationException("Javassist proxy is not available, Dubbo dependency is missing");
        }
        
        try {
            return org.apache.dubbo.common.bytecode.Proxy
                    .getProxy(interfaces.toArray(new Class[0]))
                    .newInstance(handler);
        } catch (Exception e) {
            log.warn("Failed to create proxy using Javassist, will fallback to JDK proxy: {}", e.getMessage());
            throw new RuntimeException("Javassist proxy creation failed", e);
        }
    }

    @Override
    public int getOrder() {
        // Javassist 性能更好，优先级较高
        return JAVASSIST_AVAILABLE ? 0 : Integer.MAX_VALUE;
    }

    /**
     * 检查 Javassist 是否可用
     *
     * @return true 如果可用
     */
    public static boolean isAvailable() {
        return JAVASSIST_AVAILABLE;
    }
}
