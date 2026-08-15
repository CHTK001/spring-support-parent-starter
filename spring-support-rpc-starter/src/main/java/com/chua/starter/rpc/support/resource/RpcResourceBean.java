package com.chua.starter.rpc.support.resource;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.starter.rpc.support.attrbute.RpcAttribute;
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

/**
 * rpc资源bean
 * <p>
 * 通过注入的 {@link RpcClient}（按协议类型 SPI 创建）获得远程服务代理，
 * 不耦合任何具体协议实现。
 * </p>
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
        if (interfaceClass == null) {
            throw new IllegalStateException("The interface class of ReferenceBean is not initialized");
        }
        return (T) rpcClient.get(interfaceClass);
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
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private ConfigurableListableBeanFactory getBeanFactory() {
        return (ConfigurableListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
    }
}