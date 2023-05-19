package com.chua.starter.config.server.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.config.server.properties.ConfigServerProperties;
import com.chua.starter.config.server.protocol.ProtocolServer;
import com.chua.starter.config.server.provider.ConfigurationCenterProvider;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * 配置中心配置
 * @author CH
 * @since 2022/8/1 8:51
 */
@Slf4j
@EnableConfigurationProperties(ConfigServerProperties.class)
public class ConfigServerConfiguration implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private ConfigServerProperties configServerProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if(!configServerProperties.isOpen()) {
            return;
        }

        String protocol = configServerProperties.getProtocol();
        if(Strings.isNullOrEmpty(protocol)) {
            protocol = "http";
        }
        ServiceProvider<ProtocolServer> serviceProvider = ServiceProvider.of(ProtocolServer.class);
        ProtocolServer protocolServer = serviceProvider.getExtension(protocol);
        if(null != protocolServer) {
            registry.registerBeanDefinition(ProtocolServer.class.getTypeName() + "@" + protocol, BeanDefinitionBuilder.genericBeanDefinition(protocolServer.getClass()).getBeanDefinition());
        }

        registry.registerBeanDefinition(ConfigurationCenterProvider.class.getTypeName(), BeanDefinitionBuilder.genericBeanDefinition(ConfigurationCenterProvider.class).getBeanDefinition());
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }


    @Override
    public void setEnvironment(Environment environment) {
        configServerProperties = Binder.get(environment).bindOrCreate(ConfigServerProperties.PRE, ConfigServerProperties.class);
    }


}
