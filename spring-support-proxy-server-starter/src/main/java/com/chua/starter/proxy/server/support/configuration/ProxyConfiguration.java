package com.chua.starter.proxy.server.support.configuration;

import com.chua.common.support.discovery.DiscoveryOption;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.proxy.server.support.factory.ProxyFactoryBean;
import com.chua.starter.proxy.server.support.properties.ProxyServerProperties;
import com.chua.starter.proxy.support.properties.ProxyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.List;

/**
 * ProxyConfiguration 类用于配置代理设置。
 *
 * @author CH
 * @since 2024/5/11
 */
@Slf4j
@EnableConfigurationProperties({ProxyProperties.class, ProxyServerProperties.class})
public class ProxyConfiguration implements BeanDefinitionRegistryPostProcessor , ApplicationContextAware{


    private ServiceDiscovery serviceDiscovery;
    private ProxyServerProperties proxyServerProperties;


    @ConditionalOnMissingBean
    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ProxyProperties proxyProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ProxyProperties.PRE, ProxyProperties.class);
        this.proxyServerProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ProxyServerProperties.PRE, ProxyServerProperties.class);
        String url = proxyProperties.getUrl();
        if(StringUtils.isBlank(url)) {
            return;
        }

        this.serviceDiscovery = ServiceProvider.of(ServiceDiscovery.class)
                .getNewExtension(proxyProperties.getType(),
                        new DiscoveryOption()
                                .setPassword(proxyProperties.getPassword())
                                .setUser(proxyProperties.getUsername())
                                .setAddress(url));
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        List<Integer> ports = proxyServerProperties.getPorts();
        if(CollectionUtils.isEmpty(ports)) {
            return;
        }

        for (Integer port : ports) {
            registry.registerBeanDefinition("proxyServer" + port, BeanDefinitionBuilder
                    .rootBeanDefinition(ProxyFactoryBean.class)
                    .addConstructorArgValue(serviceDiscovery)
                    .addConstructorArgValue(port)
                    .setInitMethodName("start")
                    .setDestroyMethodName("stop")
                    .getBeanDefinition()
            );
        }

    }
}
