package com.chua.starter.rpc.support.spring;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.network.rpc.RpcException;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.rpc.support.resource.RpcResourceBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

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
     * 防止自引用循环：{@link #getLocalBean(Class)} 在解析接口 Bean 列表时，
     * 若被解析的接口本身由 {@link com.chua.starter.rpc.support.resource.RpcResourceBean}
     * 的 FactoryBean 暴露（即「Consumer Bean 的代理就是自身」），
     * 则会从 {@code getObject()} 重新进入 {@link #get(Class)}，形成
     * {@code get() → getLocalBean() → getBean(RpcResourceBean) → getObject() → get()}
     * 的无限自循环，最终抛出 {@code StackOverflowError}。
     * <p>
     * 同一个线程中，第二次解析已经处于同一目标接口时直接返回 {@code null} 打断循环。
     */
    private static final ThreadLocal<Class<?>> RESOLVING_TARGET = new ThreadLocal<>();

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
            // 排除 @RpcResource 注册的远程引用占位（RpcResourceBean 是 FactoryBean，
            // 其 decorated definition 让 getBeanNamesForType 也返回它），否则
            // getBean -> RpcResourceBean.getObject() -> 降级 client.get() 会回到本方法，形成自引用
            List<String> candidates = new ArrayList<>(names.length);
            for (String name : names) {
                if (isRpcReference(context, name)) {
                    log.debug("[SpringBean RPC] 跳过 RPC 引用占位 Bean: {}", name);
                    continue;
                }
                candidates.add(name);
            }
            if (candidates.isEmpty()) {
                log.debug("[SpringBean RPC] 未找到匹配的本地 Bean: {}", targetType.getName());
                return null;
            }
            if (candidates.size() == 1) {
                return context.getBean(candidates.get(0), targetType);
            }
            for (String name : candidates) {
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
     * 判断 Bean 是否由 @RpcResource 注册的远程引用占位（{@link RpcResourceBean} FactoryBean）暴露
     * <p>
     * 这类 Bean 的实例来自远端 RPC 或其降级客户端，并非本地真实实现，
     * 参与本地 Bean 解析会导致「代理解析自己」的循环。
     *
     * @param context 上下文
     * @param name    Bean 名称
     * @return true 表示远程引用占位
     */
    private boolean isRpcReference(ApplicationContext context, String name) {
        if (!(context instanceof ConfigurableApplicationContext configurableContext)) {
            return false;
        }
        ConfigurableListableBeanFactory beanFactory = configurableContext.getBeanFactory();
        if (!beanFactory.containsBeanDefinition(name)) {
            return false;
        }
        BeanDefinition definition = beanFactory.getBeanDefinition(name);
        return RpcResourceBean.class.getName().equals(definition.getBeanClassName());
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
        if (RESOLVING_TARGET.get() != null && RESOLVING_TARGET.get() == targetType) {
            log.debug("[SpringBean RPC] 自引用检测，跳过重复解析: {}", targetType.getName());
            return null;
        }
        RESOLVING_TARGET.set(targetType);
        try {
            return doGet(targetType);
        } finally {
            RESOLVING_TARGET.remove();
        }
    }

    /**
     * 实际获取目标接口的本地代理（{@link #get(Class)} 保护后调用）
     * <p>
     * 永远返回代理：
     * <ul>
     *   <li>{@code exception=true}（默认）时，本地 Bean 不存在则在代理创建期抛出 {@link RpcException}
     *       （消费端 Bean 的创建期即呈现错误）；</li>
     *   <li>{@code exception=false} 时，返回的代理承载调用期语义：
     *       {@link SpringBeanRpcInvocationHandler} 在解析不到本地 Bean 时静默返回 {@code null}。</li>
     * </ul>
     *
     * @param targetType 目标类型
     */
    @SuppressWarnings("unchecked")
    private <T> T doGet(Class<T> targetType) {
        log.debug("[SpringBean RPC] 获取目标接口的代理: {}", targetType.getName());
        Object bean = getLocalBean(targetType);
        if (bean == null && this.exception) {
            String message = "未在本地 Spring Application Context 中找到本地 Bean: " + targetType.getName()
                    + (this.appName != null && !this.appName.isEmpty() ? " (app: " + this.appName + ")" : "");
            log.warn("[SpringBean RPC] {}", message);
            throw new RpcException(RpcException.Type.TRANSPORT, message);
        }
        log.debug("[SpringBean RPC] 本地 Bean 代理: {} -> {}", targetType.getName(),
                bean != null ? bean.getClass().getName() : targetType.getName());
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
