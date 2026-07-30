package com.chua.common.support.objects;

import com.chua.common.support.objects.register.impl.MappingBeanDefinitionRegister;

public class ConfigureObjectContext {

    private final MappingBeanDefinitionRegister mappingBeanDefinitionRegister;

    public ConfigureObjectContext(MappingBeanDefinitionRegister mappingBeanDefinitionRegister) {
        this.mappingBeanDefinitionRegister = mappingBeanDefinitionRegister;
    }

    public MappingBeanDefinitionRegister getMappingBeanDefinitionRegister() {
        return mappingBeanDefinitionRegister;
    }
}
