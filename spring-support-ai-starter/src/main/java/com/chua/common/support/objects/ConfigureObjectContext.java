package com.chua.common.support.objects;

import com.chua.common.support.objects.register.impl.MappingBeanDefinitionRegister;

/**
 * 对象配置上下文。
 *
 * @since 4.0.0.42
 * @author CH
 */
public class ConfigureObjectContext {

    /**
     * 映射 Bean 定义注册器
     */
    private final MappingBeanDefinitionRegister mappingBeanDefinitionRegister;

    /**
     * 构造对象配置上下文。
     *
     * @param mappingBeanDefinitionRegister 映射 Bean 定义注册器
     */
    public ConfigureObjectContext(MappingBeanDefinitionRegister mappingBeanDefinitionRegister) {
        this.mappingBeanDefinitionRegister = mappingBeanDefinitionRegister;
    }

    /**
     * 获取映射 Bean 定义注册器。
     *
     * @return 映射 Bean 定义注册器
     */
    public MappingBeanDefinitionRegister getMappingBeanDefinitionRegister() {
        return mappingBeanDefinitionRegister;
    }
}
