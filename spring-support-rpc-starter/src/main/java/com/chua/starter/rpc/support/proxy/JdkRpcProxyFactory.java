package com.chua.starter.rpc.support.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * JDK 动态代理工厂实现
 * <p>作为默认的代理创建方式，无额外依赖</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/07
 */
public class JdkRpcProxyFactory implements RpcProxyFactory {

    @Override
    public Object createProxy(ClassLoader classLoader, List<Class<?>> interfaces, InvocationHandler handler) {
        return Proxy.newProxyInstance(
                classLoader,
                interfaces.toArray(new Class[0]),
                handler
        );
    }

    @Override
    public int getOrder() {
        // JDK 代理作为兜底，优先级最低
        return Integer.MAX_VALUE;
    }
}
