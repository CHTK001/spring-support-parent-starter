package com.chua.starter.common.support.discovery;

import com.chua.common.support.discovery.DiscoveryOption;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

/**
 * 服务发现配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/5/13
 */
@EnableConfigurationProperties(ServiceDiscoveryProperties.class)
@Configuration
public class ServiceDiscoveryConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    private ServiceDiscoveryProperties serviceDiscoveryProperties;


    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if(!serviceDiscoveryProperties.isEnable()) {
            return;
        }

        DiscoveryOption discoveryOption = new DiscoveryOption();
        discoveryOption.setAddress(serviceDiscoveryProperties.getUrl());
        discoveryOption.setUser(serviceDiscoveryProperties.getUsername());
        discoveryOption.setPassword(serviceDiscoveryProperties.getPassword());

        ServiceDefinition serviceDefinition = ServiceProvider.of(ServiceDiscovery.class).getDefinition(serviceDiscoveryProperties.getType());
        registry.registerBeanDefinition(ServiceDiscovery.class.getTypeName() + "#" +serviceDiscoveryProperties.getType(),
                BeanDefinitionBuilder.rootBeanDefinition(serviceDefinition.getImplClass())
                .addConstructorArgValue(discoveryOption)
                .setInitMethodName("start")
                .setDestroyMethodName("close")
                .getBeanDefinition()
        );

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.serviceDiscoveryProperties = Binder.get(applicationContext.getEnvironment())
                .bindOrCreate(ServiceDiscoveryProperties.PRE, ServiceDiscoveryProperties.class);
    }
}
