package com.chua.starter.rpc.support.spring;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.network.rpc.RpcException;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.spring.support.configuration.SpringBeanUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Spring Bean 的 RPC 客户端实现（springbean 协议）
 * <p>
 * 无需网络传输：消费端与提供端直接共用于同一个 Spring Application Context，
 * 由 {@link #getLocalBean(Class)} 解析本应用上下文内同一个接口的本地 Bean 作为 RPC 代理。
 * <p>
 * 这个协议天然适合作为 Dubbo / SOFA / JSON 远端调用不可用时时的最终降级目标，
 * 配合 {@code @RpcService#degrade()} / {@code @RpcResource#degrade()} 使用「Bean 即 RPC」的永久降级。
 * </p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Spi("springbean")
public class SpringBeanRpcClient implements RpcClient, ApplicationContextAware {

    /**
     * 被调用的本地 Bean 代理缓存，避免重复创建 JDK 动态代理
     */
    private final java.util.Map<Class<?>, Object> proxyCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Application上下文
     */
    private ApplicationContext applicationContext;

    /**
     * API命名空间 deprecated unused 占位
     */
    private final List<RpcRegistryConfig> registryConfigs;

    /**
     * 消费者配置
     */
    private final RpcConsumerConfig consumerConfig;

    /**
     * 应用名称
     */
    private final String appName;

    /**
     * 未找到本地 Bean 时是否抛出异常
     */
    private boolean exception = true;

    /**
     * 创建 springbean客户端 实例
     * @param registryConfigs registry配置
     * @param consumerConfig consumer配置
     * @param appName app名称
     */
    public SpringBeanRpcClient(List<RpcRegistryConfig> registryConfigs,
                               RpcConsumerConfig consumerConfig,
                               String appName) {
        this.registryConfigs = registryConfigs;
        this.consumerConfig = consumerConfig;
        this.appName = appName;
    }

    /**
     * 设置application上下文
     * @param applicationContext 参数
     * @return 返回值
     * @throws BeansException 异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 获取Application上下文
     * @return 上下文
     */
    public ApplicationContext getApplicationContext() {
        return this.applicationContext;
    }

    /**
     * 获取目标接口的本地 Bean（可能为 null）
     * <p>
     * SPI 独立创建的实例可能没有拿到容器的 {@link #setApplicationContext}，
     * 因此 {@link #getApplicationContext()} 为空时回退到 {@code SpringBeanUtils}
     * 的全局上下文（应当时已初始化的前提下）
     *
     * @param targetType 目标类型
     * @return 本地 Bean
     */
    @SuppressWarnings("unchecked")
    public Object getLocalBean(Class<?> targetType) {
        ApplicationContext context = getApplicationContext();
        if (context == null) {
            try {
                context = SpringBeanUtils.getApplicationContextOrNull();
            } catch (IllegalStateException e) {
                // 容器尚未就绪，不阻断调用
            }
        }
        if (context == null) {
            log.debug("[SpringBean RPC] 未定位到 Application上下文，跳过本地 Bean 解析: {}", targetType.getName());
            return null;
        }
        try {
            // getBeanByType: BeanDefinition 继承链上的所有可赋值 Bean
            String[] names = context.getBeanNamesForType(targetType);
            if (names.length == 0) {
                log.debug("[SpringBean RPC] 未找到匹配的本地 Bean: {}", targetType.getName());
                return null;
            }
            if (names.length == 1) {
                return context.getBean(names[0], targetType);
            }
            for (String name : names) {
                Object bean = context.getBean(name, targetType);
                if (bean != null) {
                    return bean;
                }
            }
            log.debug("[SpringBean RPC] 本地 Bean 解析失败: {}", targetType.getName());
            return null;
        } catch (BeansException e) {
            log.debug("[SpringBean RPC] 本地 Bean 解析异常: {}", targetType.getName(), e);
            return null;
        }
    }

    /**
     * 获取目标接口的本地代理
     *
     * @param targetType 目标类型
     * @return 本地 Bean 代理（未找到时按 {@code exception} 策略处理：抛错或返回 null）
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> targetType) {
        log.debug("[SpringBean RPC] 获取目标接口的代理: {}", targetType.getName());
        Object bean = getLocalBean(targetType);
        if (bean == null) {
            String message = "未在本地 Spring Application Context 中找到本地 Bean: " + targetType.getName()
                    + (this.appName != null && !this.appName.isEmpty() ? " (app: " + this.appName + ")" : "");
            if (this.exception) {
                log.warn("[SpringBean RPC] {}", message);
                throw new RpcException(RpcException.Type.TRANSPORT, message);
            }
            log.warn("[SpringBean RPC] {}", message);
            return null;
        }
        log.debug("[SpringBean RPC] 本地 Bean 代理: {} -> {}", targetType.getName(), bean.getClass().getName());
        return (T) proxyCache.computeIfAbsent(targetType, it -> ReflectUtils.newProxy(
                it.getClassLoader(),
                new Class<?>[]{it},
                new SpringBeanRpcInvocationHandler<>(it, this)));
    }

    /**
     * 是否未找到时抛出异常
     * @return boolean
     */
    public boolean isException() {
        return exception;
    }

    /**
     * 设置未找到时是否抛出异常
     * @param exception boolean
     */
    public void setException(boolean exception) {
        this.exception = exception;
    }

    /**
     * 关闭
     */
    @Override
    public void close() throws Exception {
        proxyCache.clear();
    }
}
