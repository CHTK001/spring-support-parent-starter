package com.chua.starter.config.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.config.properties.ConfigProperties;
import com.chua.starter.config.protocol.ProtocolProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.Resource;

/**
 * 配置中心配置
 * @author CH
 * @since 2022/7/30 8:34
 */
@ConditionalOnMissingClass(value = {"com.chua.starter.config.server.properties.ConfigServerProperties"})
@EnableConfigurationProperties(ConfigProperties.class)
public class ConfigConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    @Resource
    private ConfigProperties configProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if(!configProperties.isOpen()) {
            return;
        }

        String protocol = configProperties.getProtocol();
        ProtocolProvider protocolProvider = ServiceProvider.of(ProtocolProvider.class).getExtension(protocol);
        if(null == protocolProvider) {
            return;
        }

        registry.registerBeanDefinition(ProtocolProvider.class.getTypeName() + "@" + protocol, BeanDefinitionBuilder.genericBeanDefinition(protocolProvider.getClass()).getBeanDefinition());
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        configProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ConfigProperties.PRE, ConfigProperties.class);
    }
}
