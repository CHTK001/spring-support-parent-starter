package com.chua.starter.common.support.utils;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * BeanDefinitionRegistry tools
 *
 * @author CH
 */
public class BeanDefinitionRegistryUtils {
    /**
     * 注册定义
     *
     * @param registry 注册器
     * @param type     类型
     */
    public static void registerBeanDefinition(BeanDefinitionRegistry registry, Class<?> type) {
        registerBeanDefinition(registry, type.getName(), type);
    }

    /**
     * 注册定义
     *
     * @param registry 注册器
     * @param type     类型
     */
    public static <T> void registerBeanDefinition(BeanDefinitionRegistry registry, Class<T> type, T bean) {
        registry.registerBeanDefinition(type.getName(),
                BeanDefinitionBuilder.genericBeanDefinition(type, () -> bean).getBeanDefinition());
    }

    /**
     * 注册定义
     *
     * @param registry 注册器
     * @param name     beanName
     * @param type     类型
     */
    public static void registerBeanDefinition(BeanDefinitionRegistry registry, String name, Class<?> type) {
        registry.registerBeanDefinition(name, BeanDefinitionBuilder.genericBeanDefinition(type).getBeanDefinition());
    }

    /**
     * 注册定义
     *
     * @param registry       注册器
     * @param name           beanName
     * @param beanDefinition 类型
     */
    public static void registerBeanDefinition(BeanDefinitionRegistry registry, String name, BeanDefinition beanDefinition) {
        registry.registerBeanDefinition(name, beanDefinition);
    }
}
