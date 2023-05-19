package com.chua.starter.common.support.utils;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.util.Map;

/**
 * bean定义工具
 *
 * @author CH
 */
public class BeanDefinitionUtils {
    /**
     * 注册bean
     *
     * @param registry 注册器
     * @param aClass   定义
     * @param beanMap  参数
     */
    public static void registerTypePropertiesBeanDefinition(BeanDefinitionRegistry registry, Class<?> aClass, Map beanMap) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(aClass);
        beanMap.forEach((k, v) -> {
            beanDefinitionBuilder.addPropertyValue(k.toString(), v);
        });
        registry.registerBeanDefinition(aClass.getName(), beanDefinitionBuilder.getBeanDefinition());
    }

    /**
     * 注册bean
     *
     * @param pre      bean name pre
     * @param registry 注册器
     * @param aClass   定义
     * @param beanMap  参数
     */
    public static void registerTypePropertiesBeanDefinition(String pre, BeanDefinitionRegistry registry, Class<?> aClass, Map beanMap) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(aClass);
        beanMap.forEach((k, v) -> {
            beanDefinitionBuilder.addPropertyValue(k.toString(), v);
        });
        registry.registerBeanDefinition(pre + "@" + aClass.getName(), beanDefinitionBuilder.getBeanDefinition());
    }
}
