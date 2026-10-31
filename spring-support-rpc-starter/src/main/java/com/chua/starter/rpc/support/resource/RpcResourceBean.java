package com.chua.starter.rpc.support.resource;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcType;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.rpc.support.attrbute.RpcAttribute;
import com.chua.starter.rpc.support.interceptor.RpcInterceptor;
import com.chua.starter.rpc.support.properties.RpcProperties;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.StringUtils;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;

/**
 * rpc资源Bean
 * <p>
 * 通过注入的 {@link RpcClient}（按协议类型 SPI 创建）获得远程服务代理，
 * 不耦合任何具体协议实现。
 * </p>
 * <p>
 * 具备降级能力：远端 RPC 调用失败时，回退到 {@code degrade} 指定的提供者
 * （默认 {@code springbean}，即本地 Bean）
 * </p>
 *
 * @since 2024/04/07
 * @author CH
 */
@Getter
@Setter
public class RpcResourceBean<T> implements FactoryBean<T>, ApplicationContextAware,
        BeanClassLoaderAware,
        BeanNameAware,
        InitializingBean,
        DisposableBean {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(RpcResourceBean.class);

    /**
     * 标识
     */
    private String id;

    /**
     * 密钥
     */
    private String key;

    /**
     * interfaceClass
     */
    private Class<?> interfaceClass;

    /**
     * 接口名称
     */
    private String interfaceName;

    /**
     * Classloader
     */
    private ClassLoader classLoader;

    /**
     * Bean名称
     */
    private String beanName;

    /**
     * Application上下文
     */
    private ApplicationContext applicationContext;

    /**
     * RPC客户端
     */
    private RpcClient rpcClient;

    /**
     * 降级目标提供者名称（RPC 远端不可用时 回退的协议提供者名称）
     * <p>例如 {@code "springbean"}：远端不可用时降级到本地 Bean 直调</p>
     */
    private String degrade = "springbean";

    /**
     * 未找到 RPC Bean 时是否抛出异常
     * <ul>
     *   <li>{@code true}（默认）：抛出 RPC 异常</li>
     *   <li>{@code false}：静默返回 {@code null}</li>
     * </ul>
     */
    private boolean exception = true;

    /**
     * RPC属性（构建降级客户端所需配置）
     */
    private RpcProperties rpcProperties;

    /**
     * 降级客户端（懒初始化，避免未实际调用时创建）
     */
    private volatile RpcClient degradeClient;

    /**
     * 降级代理（懒初始化，远端真正失败后才创建）
     */
    private volatile T degradeProxy;

    /**
     * 获取 标识
     *
     * @return ID
     */
    public String getId() {
        return id;
    }

    @Override
    /**
     * 获取对象
     * @return 返回值
     * @throws Exception 异常
     */
    public T getObject() throws Exception {
        if (interfaceClass == null) {
            throw new IllegalStateException("The interface class of ReferenceBean is not initialized");
        }
        try {
            T remote = (T) rpcClient.get(interfaceClass);
            return intercept(withFailover(remote));
        } catch (Throwable e) {
            // 远端 RPC 不可用时，降级到 degrade 指定的提供者直调本地 Bean
            log.warn("[RPC] 远端调用失败，尝试降级到 {} provider: {}", degrade, e.getMessage());
            try {
                RpcClient client = getDegradeClient();
                if (client == null) {
                    log.error("[RPC] 降级客户端创建失败，回退原始异常: {}", e.getMessage());
                    throw e;
                }
                return intercept((T) client.get(interfaceClass));
            } catch (Throwable de) {
                if (de != e) {
                    de.addSuppressed(e);
                }
                throw de;
            }
        }
    }

    /**
     * 为已创建的远程代理加一层「调用期降级」包装
     * <p>
     * 创建阶段就失败的情况由 {@link #getObject()} 的 catch 处理；
     * 这里处理代理创建成功、调用时才失败的情况（如 JSON-RPC 每次调用才发 HTTP）。
     * </p>
     *
     * @param remote 远程协议代理
     * @return 带调用期降级的代理；无需降级时返回原对象
     */
    @SuppressWarnings("unchecked")
    private T withFailover(T remote) {
        if (rpcProperties != null && rpcProperties.getType() == RpcType.SPRINGBEAN) {
            return remote;
        }
        ClassLoader loader = classLoader != null ? classLoader : interfaceClass.getClassLoader();
        return (T) ReflectUtils.newProxy(loader, new Class<?>[]{interfaceClass},
                new RpcFailoverInvocationHandler(remote, this));
    }

    /**
     * 获取（懒创建的）降级代理
     * <p>
     * 只有远端真的失败时才创建，避免在远端健康时因为本地没有实现而提前抛错。
     * </p>
     *
     * @return 降级代理；降级客户端不可用时返回 {@code null}
     * @throws Exception 降级代理创建失败（{@code exception=true} 且无本地 Bean）
     */
    @SuppressWarnings("unchecked")
    synchronized Object getDegradeProxy() throws Exception {
        if (degradeProxy != null) {
            return degradeProxy;
        }
        RpcClient client = getDegradeClient();
        if (client == null) {
            return null;
        }
        degradeProxy = (T) client.get(interfaceClass);
        return degradeProxy;
    }

    /**
     * 降级提供者名称（用于日志）
     *
     * @return 降级提供者名称
     */
    String getDegradeName() {
        return StringUtils.hasText(degrade) ? degrade : "springbean";
    }

    /**
     * 创建或获取 降级提供者客户端
     *
     * @return 降级客户端，创建失败时返回 null
     */
    private synchronized RpcClient getDegradeClient() {
        String provider = StringUtils.hasText(degrade) ? degrade : "springbean";
        if (degradeClient != null) {
            return degradeClient;
        }
        try {
            RpcClient client = RpcClient.createClient(provider,
                    rpcProperties.getRegistry(),
                    rpcProperties.getConsumer(),
                    rpcProperties.getApplicationName());
            // 本地 Bean 客户端的未找到处理开关由注解的 exception 控制
            if (client instanceof com.chua.starter.rpc.support.spring.SpringBeanRpcClient springBean) {
                springBean.setException(exception);
            }
            degradeClient = client;
            return client;
        } catch (Exception e) {
            log.warn("[RPC] 降级客户端创建失败: {} ({})", provider, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将拦截器链（{@link RpcInterceptor}，如方法级指标统计）包装到远程调用代理外层。
     *
     * @param target 协议实现的远程代理对象
     * @return 包装后的代理对象；无拦截器时返回原对象
     */
    private T intercept(T target) {
        List<RpcInterceptor> interceptors = applicationContext.getBeanProvider(RpcInterceptor.class)
                .orderedStream().sorted(Comparator.comparingInt(RpcInterceptor::getOrder)).toList();
        if (interceptors.isEmpty()) {
            return target;
        }
        return ReflectUtils.newProxy(classLoader, new Class<?>[]{interfaceClass},
                (proxy, method, args) -> invokeWithInterceptors(target, method, args, interceptors));
    }

    /**
     * 按拦截器链执行一次远程调用（前置 / 调用 / 后置 / 异常 / 完成）。
     *
     * @param target       远程代理对象
     * @param method       调用方法
     * @param args         调用参数
     * @param interceptors 拦截器链（已按 订单 排序）
     * @return 调用结果
     * @throws Throwable 远程调用或业务异常
     */
    private Object invokeWithInterceptors(Object target, Method method, Object[] args,
                                           List<RpcInterceptor> interceptors) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return ReflectUtils.invoke(target, method.getName(), method.getReturnType(),
                    method.getParameterTypes(), args);
        }
        for (RpcInterceptor interceptor : interceptors) {
            if (!interceptor.preHandle(method, args)) {
                return null;
            }
        }
        Object result = null;
        try {
            MethodHandle handle = ReflectUtils.findMethodHandle(target.getClass(), method.getName(),
                    method.getReturnType(), method.getParameterTypes());
            if (handle == null) {
                throw new IllegalStateException("No such RPC method: " + target.getClass().getName()
                        + "." + method.getName());
            }
            result = handle.bindTo(target).invokeWithArguments(args);
            for (RpcInterceptor interceptor : interceptors) {
                result = interceptor.postHandle(method, args, result);
            }
        } catch (Throwable e) {
            Throwable failure = e;
            for (RpcInterceptor interceptor : interceptors) {
                try {
                    interceptor.handleException(method, args, failure);
                } catch (Throwable ex) {
                    failure = ex;
                    break;
                }
            }
            throw failure;
        } finally {
            for (RpcInterceptor interceptor : interceptors) {
                interceptor.afterCompletion(method, args);
            }
        }
        return result;
    }

    @Override
    public Class<?> getObjectType() {
        return interfaceClass;
    }

    @Override
    /**
     * 设置Bean类加载
     * @param classLoader 参数
     * @return 返回值
     */
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    /**
     * 设置Bean名称
     * @param name 字符串
     * @return 返回值
     */
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    /**
     * 销毁
     * @return 返回值
     * @throws Exception 异常
     */
    public void destroy() throws Exception {

    }

    @Override
    /**
     * 之后属性设置
     * @return 返回值
     * @throws Exception 异常
     */
    public void afterPropertiesSet() throws Exception {
        ConfigurableListableBeanFactory beanFactory = getBeanFactory();

        if (getId() == null || getId().isEmpty()) {
            throw new IllegalStateException("The id of ReferenceBean cannot be empty");
        }
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(getId());
        this.interfaceClass = (Class<?>) beanDefinition.getAttribute(RpcAttribute.INTERFACE_CLASS);
        this.interfaceName = (String) beanDefinition.getAttribute(RpcAttribute.INTERFACE_NAME);
        if (this.interfaceClass == null) {
            throw new IllegalStateException("The interface class of ReferenceBean is not initialized");
        }
    }

    @Override
    /**
     * 设置application上下文
     * @param applicationContext 参数
     * @return 返回值
     * @throws BeansException 异常
     */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 获取Bean工厂
     *
     * @return 获取Bean工厂的结果
     */
    private ConfigurableListableBeanFactory getBeanFactory() {
        return (ConfigurableListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
    }
}
