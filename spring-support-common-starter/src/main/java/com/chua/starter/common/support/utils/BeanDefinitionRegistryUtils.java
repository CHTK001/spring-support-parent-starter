package com.chua.starter.common.support.utils;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
* Beandefinitionregistry tools
*
* @author CH
* @since 4.0.0
 */
public class BeanDefinitionRegistryUtils {

    /**
    * 私有构造函数，防止实例化
     */
    private BeanDefinitionRegistryUtils() {
    }

    /**
    * 注册定义
    *
    * @param registry 注册器
    * @param type     类型
     */
    public static void registerBeanDefinition(BeanDefinitionRegistry registry, Class<?> type) {
        /**
        * 注册Beandefinition。
        * @param registry registry
        * @param type.getName( 类型.获取名称(
         */
        registerBeanDefinition(registry, type.getName(), type);
    }

    /**
    * 注册定义
    *
    * @param registry 注册器
    * @param type     类型
    * @param bean Bean
    * @return 注册Beandefinition的结果
     */
    public static <T> void registerBeanDefinition(BeanDefinitionRegistry registry, Class<T> type, T bean) {
        registry.registerBeanDefinition(type.getName(),
                BeanDefinitionBuilder.genericBeanDefinition(type, () -> bean).getBeanDefinition());
    }

    /**
    * 注册定义
    *
    * @param registry 注册器
    * @param name     Bean名称
    * @param type     类型
     */
    public static void registerBeanDefinition(BeanDefinitionRegistry registry, String name, Class<?> type) {
        registry.registerBeanDefinition(name, BeanDefinitionBuilder.genericBeanDefinition(type).getBeanDefinition());
    }

    /**
    * 注册定义
    *
    * @param registry       注册器
    * @param name           Bean名称
    * @param beanDefinition 类型
     */
    public static void registerBeanDefinition(BeanDefinitionRegistry registry, String name, BeanDefinition beanDefinition) {
        registry.registerBeanDefinition(name, beanDefinition);
    }
}

