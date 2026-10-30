package com.chua.starter.rpc.support.proxy;

import com.chua.common.support.reflection.ReflectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.util.List;

/**
 * Javassist 代理工厂实现
 * <p>当 Dubbo 依赖存在时可用，性能优于 JDK 代理</p>
 *
 * @since 2024/04/07
 * @author CH
 */
public class JavassistRpcProxyFactory implements RpcProxyFactory {

    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(JavassistRpcProxyFactory.class);

    /**
     * Javassist_可用
    */
    private static final boolean JAVASSIST_AVAILABLE;

    static {
        JAVASSIST_AVAILABLE = ReflectUtils.forName("org.apache.dubbo.common.bytecode.Proxy") != null;
    }

    @Override
    /**
     * 创建代理
     * @param classLoader 参数
     * @param handler 参数
     * @param interfaces 参数
     * @return 返回值
    */
    public Object createProxy(ClassLoader classLoader, List<Class<?>> interfaces, InvocationHandler handler) {
        if (!JAVASSIST_AVAILABLE) {
            /**
             * unsupportedoperation异常。
             * @param available 可用
             * @param missing" missing"
             * @return unsupportedoperation异常的结果
             */
            throw new UnsupportedOperationException("Javassist proxy is not available, Dubbo dependency is missing");
        }
        
        try {
            return org.apache.dubbo.common.bytecode.Proxy
                    .getProxy(interfaces.toArray(new Class[0]))
                    .newInstance(handler);
        } catch (Exception e) {
            log.warn("Failed to create proxy using Javassist, will fallback to JDK proxy: {}", e.getMessage());
            /**
             * runtime异常。
             * @param failed" 失败"
             * @param e e
             * @return runtime异常的结果
             */
            throw new RuntimeException("Javassist proxy creation failed", e);
        }
    }

    @Override
    /**
     * 获取订单
     * @return 返回值
    */
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
