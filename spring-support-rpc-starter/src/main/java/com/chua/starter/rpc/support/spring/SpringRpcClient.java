package com.chua.starter.rpc.support.spring;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.chua.common.support.reflection.ReflectUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
* 基于Spring的RPC客户端实现
* <p>
* 委托真实的 {@link RpcClient}（json/NAT/Dubbo/sofa 等协议）发起网络调用，
* 并对远程代理附加拦截器、重试、熔断等 Spring 增强能力。
* </p>
*
* @since 2025-03-20
* @author CH
 */
@Slf4j
public class SpringRpcClient implements RpcClient, ApplicationContextAware {

    /** Application上下文 */
    private ApplicationContext applicationContext;
    /** 注册表配置 */
    private final List<RpcRegistryConfig> registryConfigs;
    /** 消费者配置 */
    private final RpcConsumerConfig consumerConfig;
    /** APP名称 */
    private final String appName;
    private final Map<Class<?>, Object> proxyCache = new ConcurrentHashMap<>(); // 代理缓存

    /**
    * 真实协议客户端委托（懒初始化），避免 SPI 构造阶段触发注册中心连接
    */
    private volatile RpcClient delegate;

    /**
    * 创建 springrpc客户端 实例
    * @param registryConfigs registry配置
    * @param consumerConfig consumer配置
    * @param appName app名称
    */
    public SpringRpcClient(List<RpcRegistryConfig> registryConfigs,
                          RpcConsumerConfig consumerConfig,
                          String appName) {
        this.registryConfigs = registryConfigs;
        this.consumerConfig = consumerConfig;
        this.appName = appName;
    }

    @Override
    /** 设置application上下文 */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
    * 获取
    *
    * @param targetType Target类型
    * @return 获取的结果
    */
    public <T> T get(Class<T> targetType) {
        return (T) proxyCache.computeIfAbsent(targetType, this::createProxy);
    }

    /**
    * 解析真实 RPC 协议名：优先取注册配置中显式指定的 RPC 协议（json/NAT/Dubbo/sofa），
    * 否则回退 JSON-RPC（纯 JDK HTTP 实现，无需额外依赖）。
    *
    * @return SPI 协议名
    */
    private String resolveProtocol() {
        if (registryConfigs != null) {
            for (RpcRegistryConfig config : registryConfigs) {
                String protocol = config.getProtocol();
                if (protocol != null) {
                    String lower = protocol.toLowerCase();
                    if ("json".equals(lower) || "native".equals(lower)
                            || "dubbo".equals(lower) || "sofa".equals(lower)) {
                        return lower;
                    }
                }
            }
        }
        return "json";
    }

    /**
    * 懒创建真实协议客户端委托
    *
    * @return 真实 RPC 客户端
    */
    private RpcClient delegate() {
        RpcClient current = delegate;
        if (current == null) {
            synchronized (this) {
                current = delegate;
                if (current == null) {
                    current = RpcClient.createClient(resolveProtocol(), registryConfigs, consumerConfig, appName);
                    delegate = current;
                }
            }
        }
        return current;
    }

    /**
    * 创建代理对象，底层委托真实 RPC 客户端发起网络调用
    * @param targetType Target类型
    * @return 创建代理的结果
    */
    private <T> Object createProxy(Class<T> targetType) {
        return ReflectUtils.newProxy(
            targetType.getClassLoader(),
            new Class<?>[]{targetType},
            new SpringRpcInvocationHandler<>(targetType, delegate(), applicationContext, consumerConfig)
        );
    }

    @Override
    /** 关闭 */
    public void close() throws Exception {
        proxyCache.clear();
        RpcClient current = delegate;
        delegate = null;
        if (current != null) {
            current.close();
        }
        log.info("[Spring RPC] 客户端已关闭");
    }
}
