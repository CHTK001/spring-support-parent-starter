package com.chua.starter.rpc.support.resource;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.rpc.support.attrbute.RpcAttribute;
import com.chua.starter.rpc.support.interceptor.RpcInterceptor;
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

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;

/**
 * rpc资源bean
 * <p>
 * 通过注入的 {@link RpcClient}（按协议类型 SPI 创建）获得远程服务代理，
 * 不耦合任何具体协议实现。
 * </p>
 *
 * @version 1.0.0
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

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(RpcResourceBean.class);
    /** ID */
    private String id;

    /** 密钥 */
    private String key;

    private Class<?> interfaceClass;

    /** 接口名称 */
    private String interfaceName;
    /** Classloader */
    private ClassLoader classLoader;
    /** Bean名称 */
    private String beanName;
    /** Application上下文 */
    private ApplicationContext applicationContext;

    /** RPC客户端 */
    private RpcClient rpcClient;

    /**
     * 获取 ID
     *
     * @return ID
     */
    public String getId() {
        return id;
    }

    @Override
    /** 获取Object */
    public T getObject() throws Exception {
        if (interfaceClass == null) {
            throw new IllegalStateException("The interface class of ReferenceBean is not initialized");
        }
        return intercept((T) rpcClient.get(interfaceClass));
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
     * @param interceptors 拦截器链（已按 order 排序）
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
    /** 设置BeanClassLoader */
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    /** 设置BeanName */
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    /** 销毁 */
    public void destroy() throws Exception {

    }

    @Override
    /** AfterProperties设置 */
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
    /** 设置ApplicationContext */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /** 获取BeanFactory */
    private ConfigurableListableBeanFactory getBeanFactory() {
        return (ConfigurableListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
    }
}