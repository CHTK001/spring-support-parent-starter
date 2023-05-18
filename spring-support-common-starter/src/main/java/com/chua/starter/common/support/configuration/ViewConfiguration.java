package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.ViewProperties;
import com.chua.starter.common.support.view.ViewProvider;
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
 * view
 *
 * @author CH
 * @since 2022/8/1 17:07
 */
@EnableConfigurationProperties(ViewProperties.class)
public class ViewConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    ViewProperties viewProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (viewProperties.isOpenView()) {
            registry.registerBeanDefinition("ViewProvider", BeanDefinitionBuilder
                    .rootBeanDefinition(ViewProvider.class)
                    .getBeanDefinition()
            );
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        viewProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate("plugin.view", ViewProperties.class);
    }
}
