package com.chua.starter.rpc.support.resource;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.dubbo.common.bytecode.Proxy;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.spring.reference.ReferenceAttributes;
import org.apache.dubbo.config.spring.util.LazyTargetInvocationHandler;
import org.apache.dubbo.config.spring.util.LazyTargetSource;
import org.apache.dubbo.rpc.proxy.AbstractProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.PROXY_FAILED;

/**
 * rpc资源bean
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/07
 */
@Getter
@Setter
public class RpcResourceBean<T> implements FactoryBean<T>, ApplicationContextAware,
        BeanClassLoaderAware,
        BeanNameAware,
        InitializingBean,
        DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RpcResourceBean.class);
    private String id;

    private String key;

    private Class<?> interfaceClass;

    private String interfaceName;
    private ClassLoader classLoader;
    private String beanName;
    private ApplicationContext applicationContext;
    private String proxy;
    private Object lazyProxy;

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
    public T getObject() throws Exception {
        if (lazyProxy == null) {
            createLazyProxy();
        }
        return (T) lazyProxy;
    }

    @Override
    public Class<?> getObjectType() {
        return interfaceClass;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public void destroy() throws Exception {

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ConfigurableListableBeanFactory beanFactory = getBeanFactory();

        Assert.notEmptyString(getId(), "The id of ReferenceBean cannot be empty");
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(getId());
        this.interfaceClass = (Class<?>) beanDefinition.getAttribute(ReferenceAttributes.INTERFACE_CLASS);
        this.interfaceName = (String) beanDefinition.getAttribute(ReferenceAttributes.INTERFACE_NAME);
        Assert.notNull(this.interfaceClass, "The interface class of ReferenceBean is not initialized");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void createLazyProxy() {

        // set proxy interfaces
        // see also: org.apache.dubbo.rpc.proxy.AbstractProxyFactory.getProxy(org.apache.dubbo.rpc.Invoker<T>, boolean)
        List<Class<?>> interfaces = new ArrayList<>();
        interfaces.add(interfaceClass);
        Class<?>[] internalInterfaces = AbstractProxyFactory.getInternalInterfaces();
        Collections.addAll(interfaces, internalInterfaces);
        if (!StringUtils.isEquals(interfaceClass.getName(), interfaceName)) {
            // add service interface
            try {
                Class<?> serviceInterface = ClassUtils.forName(interfaceName, classLoader);
                interfaces.add(serviceInterface);
            } catch (ClassNotFoundException e) {
                // generic call maybe without service interface class locally
            }
        }

        if (StringUtils.isEmpty(this.proxy) || CommonConstants.DEFAULT_PROXY.equalsIgnoreCase(this.proxy)) {
            generateFromJavassistFirst(interfaces);
        }

        if (this.lazyProxy == null) {
            generateFromJdk(interfaces);
        }
    }

    private void generateFromJavassistFirst(List<Class<?>> interfaces) {
        try {
            this.lazyProxy = Proxy.getProxy(interfaces.toArray(new Class[0]))
                    .newInstance(new LazyTargetInvocationHandler(new DubboReferenceLazyInitTargetSource()));
        } catch (Throwable fromJavassist) {
            // try fall back to JDK proxy order
            try {
                this.lazyProxy = java.lang.reflect.Proxy.newProxyInstance(
                        classLoader,
                        interfaces.toArray(new Class[0]),
                        new LazyTargetInvocationHandler(new DubboReferenceLazyInitTargetSource()));
                log.error(
                        PROXY_FAILED,
                        "",
                        "",
                        "Failed to generate proxy by Javassist failed. Fallback to use JDK proxy success. "
                                + "Interfaces: " + interfaces,
                        fromJavassist);
            } catch (Throwable fromJdk) {
                log.error(
                        PROXY_FAILED,
                        "",
                        "",
                        "Failed to generate proxy by Javassist failed. Fallback to use JDK proxy is also failed. "
                                + "Interfaces: " + interfaces + " Javassist Error.",
                        fromJavassist);
                log.error(
                        PROXY_FAILED,
                        "",
                        "",
                        "Failed to generate proxy by Javassist failed. Fallback to use JDK proxy is also failed. "
                                + "Interfaces: " + interfaces + " JDK Error.",
                        fromJdk);
                throw fromJavassist;
            }
        }
    }

    private void generateFromJdk(List<Class<?>> interfaces) {
        try {
            this.lazyProxy = java.lang.reflect.Proxy.newProxyInstance(
                    classLoader,
                    interfaces.toArray(new Class[0]),
                    new LazyTargetInvocationHandler(new DubboReferenceLazyInitTargetSource()));
        } catch (Throwable fromJdk) {
            log.error(
                    PROXY_FAILED,
                    "",
                    "",
                    "Failed to generate proxy by Javassist failed. Fallback to use JDK proxy is also failed. "
                            + "Interfaces: " + interfaces + " JDK Error.",
                    fromJdk);
            throw fromJdk;
        }
    }

    private Object getCallProxy() throws Exception {
        synchronized (applicationContext) {
           return rpcClient == null ? null : rpcClient.get(interfaceClass);
        }
    }

    private class DubboReferenceLazyInitTargetSource implements LazyTargetSource {
        @Override
        public Object getTarget() throws Exception {
            return getCallProxy();
        }
    }


    private ConfigurableListableBeanFactory getBeanFactory() {
        return (ConfigurableListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
    }
}
