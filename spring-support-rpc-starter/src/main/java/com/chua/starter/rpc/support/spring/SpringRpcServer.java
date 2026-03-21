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
 * 使用Spring的ApplicationContext来注册和管理服务
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
public class SpringRpcServer implements RpcServer, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private final List<RpcRegistryConfig> registryConfigs;
    private final List<RpcProtocolConfig> protocolConfigs;
    private final String appName;
    private final Map<String, Object> serviceRegistry = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    public SpringRpcServer(List<RpcRegistryConfig> registryConfigs,
                          List<RpcProtocolConfig> protocolConfigs,
                          String appName) {
        this.registryConfigs = registryConfigs;
        this.protocolConfigs = protocolConfigs;
        this.appName = appName;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
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
        if (protocolConfigs != null && !protocolConfigs.isEmpty()) {
            log.info("[Spring RPC] 协议配置:");
            for (RpcProtocolConfig config : protocolConfigs) {
                log.info("  - name: {}, port: {}, threads: {}",
                    config.getName(), config.getPort(), config.getThreads());
            }
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
     */
    public Object getService(String name) {
        return serviceRegistry.get(name);
    }

    /**
     * 获取所有已注册的服务
     */
    public Map<String, Object> getAllServices() {
        return new ConcurrentHashMap<>(serviceRegistry);
    }

    @Override
    public void close() throws Exception {
        serviceRegistry.clear();
        initialized = false;
        log.info("[Spring RPC] 服务器已关闭");
    }
}
