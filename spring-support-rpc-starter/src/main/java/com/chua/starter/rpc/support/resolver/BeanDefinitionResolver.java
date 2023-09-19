package com.chua.starter.rpc.support.resolver;

import com.chua.starter.rpc.support.properties.RpcProperties;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * bean释义分解器
 *
 * @author CH
 */
public interface BeanDefinitionResolver {
    /**
     * 注册
     *
     * @param rpcProperties rpc属性
     * @param registry      注册表
     */
    void register(RpcProperties rpcProperties, BeanDefinitionRegistry registry);
}
