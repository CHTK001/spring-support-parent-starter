package com.chua.starter.rpc.support.proxy;

import java.util.List;

/**
 * RPC 代理工厂接口
 * <p>用于创建 RPC 服务代理，解耦具体实现</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/07
 */
public interface RpcProxyFactory {

    /**
     * 创建代理对象
     *
     * @param classLoader 类加载器
     * @param interfaces  接口列表
     * @param handler     调用处理器
     * @return 代理对象
     */
    Object createProxy(ClassLoader classLoader, List<Class<?>> interfaces, java.lang.reflect.InvocationHandler handler);

    /**
     * 获取优先级，值越小优先级越高
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }
}
