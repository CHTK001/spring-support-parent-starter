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
        return (T) rpcClient.get(interfaceClass);
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