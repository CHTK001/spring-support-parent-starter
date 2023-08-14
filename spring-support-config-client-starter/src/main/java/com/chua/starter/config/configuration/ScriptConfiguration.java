package com.chua.starter.config.configuration;

import com.chua.common.support.utils.ClassUtils;
import com.chua.starter.config.properties.ConfigProperties;
import lombok.Data;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.Resource;

/**
 * 脚本配置
 * @author CH
 */
@Data
public class ScriptConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {
    @Resource
    private ConfigProperties configProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if(!configProperties.isOpen()) {
            return;
        }

        if(ClassUtils.isPresent("com.chua.starter.config.server.support.configuration.ConfigServerConfiguration")) {
            return;
        }

        System.out.println();
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        configProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ConfigProperties.PRE, ConfigProperties.class);
    }
}
