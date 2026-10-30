package com.chua.starter.rpc.support.spring;

import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.network.rpc.RpcConnectionInfo;
import com.chua.common.support.network.rpc.RpcMonitor;
import com.chua.common.support.network.rpc.RpcProtocolConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.network.rpc.RpcServer;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.spring.support.configuration.SpringBeanUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Spring 容器的本地 Bean RPC 服务端（springbean 协议）
 *
 * <p>不监听网络端口、不管理协议连接：它只把暴露的服务注册回 Application Context，
 * 生成一个与接口同类型的 Bean，使同应用上下文内的消费端可以直调它，从而实现：</p>
 * <ul>
 *   <li>Dubbo / SOFA / JSON 等远端调用不可用时，降级到本地 Bean 直调（degrade provider）</li>
 *   <li>无需注册中心即可完成的同工程内接口调用（"springbean"）</li>
 * </ul>
 *
 * <p>这个协议天然与 Spring 容器的生命周期绑定：注册的服务 Bean 与容器一同创建和销毁。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Spi("springbean")
@Service
public class SpringBeanRpcServer implements RpcServer, ApplicationContextAware, InitializingAware, RpcMonitor {

    /**
     * 协议名称
     */
    private static final String PROTOCOL = "springbean";

    /**
     * 已注册的服务名（幂等，避免重复注册）
     */
    private final Set<String> registeredBeans = ConcurrentHashMap.newKeySet();

    /**
     * Application上下文
     */
    private ApplicationContext applicationContext;

    /**
     * 是否已经初始化
     */
    private volatile boolean initialized;

    /**
     * 创建 SpringBeanRpc 服务 Server 实例（SPI 扩展点需提供公共无参构造）
     */
    public SpringBeanRpcServer() {
        // 无参构造：SPI 注册在首次调用时解析上下文，不耦合生命周期
    }

    /**
     * 创建 springbeanrpc服务端 实例
     *
     * @param registryConfigs registry配置
     * @param protocolConfig  protocol配置
     * @param appName         app名称
     */
    public SpringBeanRpcServer(List<RpcRegistryConfig> registryConfigs,
                               RpcProtocolConfig protocolConfig,
                               String appName) {
        this();
        log.info("[SpringBean RPC] 服务端已初始化: app={}", appName);
    }

    /**
     * 之后属性设置
     */
    @Override
    public void afterPropertiesSet() {
        synchronized (this) {
            if (initialized) {
                return;
            }
            initialized = true;
        }
        log.debug("[SpringBean RPC] 服务端已初始化: app={}", PROTOCOL);
    }

    /**
     * 设置application上下文
     *
     * @param applicationContext 参数
     * @return 返回值
     * @throws BeansException 异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.afterPropertiesSet();
    }

    /**
     * 获取应用上下文：SPI 创建时上下文可能未激活，允许延迟解析
     *
     * @return 上下文
     */
    private ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            try {
                applicationContext = SpringBeanUtils.getApplicationContextOrNull();
            } catch (IllegalStateException e) {
                // 容器未就绪时返回 null，交由调用方静默兜底
            }
        }
        return this.applicationContext;
    }

    /**
     * 向 Spring 容器注册本地 Bean
     *
     * @param serviceName 服务名（通常为接口全限定名）
     * @param service     服务实现
     * @return 注册结果
     */
    private boolean registerLocalBean(String serviceName, Object service) {
        if (serviceName == null || service == null) {
            return false;
        }
        if (registeredBeans.contains(serviceName)) {
            return false;
        }
        ApplicationContext context = getApplicationContext();
        if (context == null) {
            log.warn("[SpringBean RPC] 未定位 Application 上下文，忽略服务注册: {}", serviceName);
            return false;
        }
        BeanDefinitionRegistry registry;
        try {
            registry = (BeanDefinitionRegistry) context.getAutowireCapableBeanFactory();
        } catch (BeansException e) {
            log.warn("[SpringBean RPC] 未定位可写 Bean 工厂: {}", serviceName, e);
            return false;
        }
        if (registry == null) {
            log.warn("[SpringBean RPC] 未定位可写 Bean 工厂: {}", serviceName);
            return false;
        }
        try {
            BeanDefinition definition = new RootBeanDefinition(service.getClass());
            definition.setScope(ConfigurableBeanFactory.SCOPE_SINGLETON);
            registry.registerBeanDefinition(serviceName, definition);
            return true;
        } catch (BeansException e) {
            log.debug("[SpringBean RPC] 本地 Bean 注册失败: {} ({})", serviceName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 注册服务到本地 Bean 容器
     *
     * @param serviceName 服务名
     * @param service     服务实现
     * @return 当前实例
     */
    @Override
    public RpcServer register(String serviceName, Object service) {
        if (service == null) {
            return this;
        }
        try {
            if (!registerLocalBean(serviceName, service)) {
                return this;
            }
            registeredBeans.add(serviceName);
            log.info("[SpringBean RPC] 服务已注册为本地 Bean: {} -> {}", serviceName,
                    service.getClass().getName());
        } catch (Throwable e) {
            log.warn("[SpringBean RPC] 本地 Bean 注册失败: {} ({})", serviceName, e.getMessage(), e);
        }
        return this;
    }

    /**
     * 关闭
     */
    @Override
    public void close() throws Exception {
        log.info("[SpringBean RPC] 服务端已关闭");
    }

    /**
     * 获取协议名称
     *
     * @return 协议名称
     */
    @Override
    public String getProtocol() {
        return PROTOCOL;
    }

    /**
     * 获取已暴露的服务数量
     *
     * @return 服务数量
     */
    @Override
    public int getServiceCount() {
        return registeredBeans.size();
    }

    /**
     * 获取连接信息（本地 Bean 无连接）
     *
     * @return 连接信息列表
     */
    @Override
    public List<RpcConnectionInfo> getConnections() {
        return Collections.emptyList();
    }
}