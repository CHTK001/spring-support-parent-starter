package com.chua.starter.script.configuration;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 配置
 *
 * @author CH
 */
@EnableConfigurationProperties(ScriptProperties.class)
public class ScriptConfiguration implements ApplicationContextAware, BeanDefinitionRegistryPostProcessor {

    ScriptProperties scriptProperties;


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        scriptProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ScriptProperties.PRE, ScriptProperties.class);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registry.registerBeanDefinition(ScriptRegister.class.getTypeName(),
                BeanDefinitionBuilder.rootBeanDefinition(ScriptRegister.class)
                        .addConstructorArgValue(scriptProperties)
                        .addConstructorArgValue(registry).getBeanDefinition()
        );

    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }
}
