package com.chua.starter.rpc.support.spring;

import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.network.rpc.RpcProtocolConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.network.rpc.RpcServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Spring的RPC服务器实现
 * <p>
   * 使用Spring的application上下文来注册和管理服务
 * </p>
 *
 * @since 2025-03-20
 * @author CH
 */
@Slf4j
public class SpringRpcServer implements RpcServer, ApplicationContextAware {

    /** Application上下文 */
    private ApplicationContext applicationContext;
    /** 注册表配置 */
    private final List<RpcRegistryConfig> registryConfigs;
    /** 协议配置 */
    private final RpcProtocolConfig protocolConfig;
    /** APP名称 */
    private final String appName;
    private final Map<String, Object> serviceRegistry = new ConcurrentHashMap<>(); // 服务registry
    private volatile boolean initialized = false; // 初始化

    /**
      * 创建 springrpc服务端 实例
     * @param registryConfigs registry配置
     * @param protocolConfig 协议配置
     * @param appName app名称
     */
    public SpringRpcServer(List<RpcRegistryConfig> registryConfigs,
                          RpcProtocolConfig protocolConfig,
                          String appName) {
        this.registryConfigs = registryConfigs;
        this.protocolConfig = protocolConfig;
        this.appName = appName;
    }

    @Override
    /** 设置application上下文 */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    /** 注册 */
    public RpcServer register(String name, Object bean) {
        if (bean == null) {
            log.warn("[Spring RPC] 注册服务失败，bean为空: {}", name);
            return this;
        }

        serviceRegistry.put(name, bean);
        log.info("[Spring RPC] 注册服务: {} -> {}", name, bean.getClass().getName());

        return this;
    }

    @Override
    /** 之后属性设置 */
    public void afterPropertiesSet() {
        if (initialized) {
            return;
        }

        log.info("[Spring RPC] 服务器初始化: appName={}", appName);

        // 打印注册中心配置
        if (registryConfigs != null && !registryConfigs.isEmpty()) {
            log.info("[Spring RPC] 注册中心配置:");
            for (RpcRegistryConfig config : registryConfigs) {
                log.info("  - address: {}, timeout: {}", config.getAddress(), config.getTimeout());
            }
        }

        // 打印协议配置
        if (protocolConfig != null) {
            log.info("[Spring RPC] 协议配置:");
            log.info(" - name: {}, port: {}", protocolConfig.name(), protocolConfig.port());
        }

        // 打印已注册服务
        log.info("[Spring RPC] 已注册服务数量: {}", serviceRegistry.size());
        serviceRegistry.forEach((name, bean) -> {
            log.info("  - {}", name);
        });

        initialized = true;
        log.info("[Spring RPC] 服务器初始化完成");
    }

    /**
     * 获取已注册的服务
     * @param name 名称
     * @return 获取服务的结果
     */
    public Object getService(String name) {
        return serviceRegistry.get(name);
    }

    /**
     * 获取所有已注册的服务
     * @return 获取全部服务的结果
     */
    public Map<String, Object> getAllServices() {
        return new ConcurrentHashMap<>(serviceRegistry);
    }

    @Override
    /** 关闭 */
    public void close() throws Exception {
        serviceRegistry.clear();
        initialized = false;
        log.info("[Spring RPC] 服务器已关闭");
    }
}
