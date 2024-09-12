package com.chua.starter.discovery.support.configuration;

import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.discovery.DiscoveryOption;
import com.chua.common.support.discovery.ServiceDiscovery;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.project.Project;
import com.chua.starter.discovery.support.properties.DiscoveryListProperties;
import com.chua.starter.discovery.support.properties.DiscoveryProperties;
import com.chua.starter.discovery.support.service.DiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发现服务
 * @author CH
 * @since 2024/9/9
 */
@Slf4j
@EnableConfigurationProperties(DiscoveryListProperties.class)
public class DiscoveryConfiguration implements EnvironmentAware, BeanDefinitionRegistryPostProcessor {
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
      this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        DiscoveryListProperties properties = Binder.get(environment).bindOrCreate(DiscoveryListProperties.PRE, DiscoveryListProperties.class);
        List<DiscoveryProperties> properties1 = properties.getProperties();
        for (DiscoveryProperties discoveryProperties : properties1) {
            if(discoveryProperties.isEnabled()) {
                registryBean(registry, discoveryProperties);
            }
        }
    }

    private void registryBean(BeanDefinitionRegistry registry, DiscoveryProperties discoveryProperties) {
        DiscoveryOption discoveryOption = new DiscoveryOption();
        discoveryOption.setAddress(discoveryProperties.getAddress());
        discoveryOption.setUser(discoveryProperties.getUsername());
        discoveryOption.setPassword(discoveryProperties.getPassword());
        discoveryOption.setConnectionTimeoutMillis(discoveryProperties.getConnectionTimeoutMillis());
        discoveryOption.setSessionTimeoutMillis(discoveryProperties.getSessionTimeoutMillis());

        ServiceProvider<ServiceDiscovery> serviceProvider = ServiceProvider.of(ServiceDiscovery.class);
        ServiceDiscovery serviceDiscovery = serviceProvider.getNewExtension(discoveryProperties.getProtocol(), discoveryOption);
        if(null == serviceDiscovery) {
            log.warn("未发现可用的 discovery 服务");
            return;
        }
        Project project = Project.getInstance();
        String serverId = discoveryProperties.getServerId();
        if(StringUtils.isEmpty(serverId)) {
            serverId = project.calcApplicationUuid();
        }
        Map<String, String> newMetaData = new LinkedHashMap<>(project.getProject());

        Discovery discovery = Discovery.builder()
                .id(serverId)
                .uriSpec(discoveryProperties.getNamespace())
                .port(project.getApplicationPort())
                .host(project.getApplicationHost())
                .protocol(discoveryProperties.getProtocol())
                .metadata(newMetaData)
                .build();
        registry.registerBeanDefinition(discoveryProperties.getNamespace() + discoveryProperties.getProtocol() + project.calcApplicationUuid(),
                createBeanDefinitionDiscovery(discoveryProperties, serviceDiscovery, discovery));

        registry.registerBeanDefinition("discoveryService#embedd", BeanDefinitionBuilder.rootBeanDefinition(DiscoveryService.class)
                        .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE)
                .getBeanDefinition());
    }

    private BeanDefinition createBeanDefinitionDiscovery(DiscoveryProperties discoveryProperties, ServiceDiscovery serviceDiscovery, Discovery discovery) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(ServiceDiscovertyFactoryBean.class);
        beanDefinitionBuilder.addConstructorArgValue(discoveryProperties);
        beanDefinitionBuilder.addConstructorArgValue(serviceDiscovery);
        beanDefinitionBuilder.addConstructorArgValue(discovery);
        beanDefinitionBuilder.setDestroyMethodName("close");
        beanDefinitionBuilder.setInitMethodName("start");
        return beanDefinitionBuilder.getBeanDefinition();
    }



    static class ServiceDiscovertyFactoryBean implements FactoryBean<ServiceDiscovery>, AutoCloseable{

        private final DiscoveryProperties discoveryProperties;
        final ServiceDiscovery serviceDiscovery;
        private final Discovery discovery;

        public ServiceDiscovertyFactoryBean(DiscoveryProperties discoveryProperties, ServiceDiscovery serviceDiscovery, Discovery discovery) {
            this.discoveryProperties = discoveryProperties;
            this.serviceDiscovery = serviceDiscovery;
            this.discovery = discovery;
        }

        @Override
        public ServiceDiscovery getObject() throws Exception {
            return serviceDiscovery;
        }

        @Override
        public Class<?> getObjectType() {
            return ServiceDiscovery.class;
        }


        public void start() throws IOException {
            serviceDiscovery.start();
            if(StringUtils.isBlank(discoveryProperties.getNamespace())) {
                return;
            }
            serviceDiscovery.registerService(discoveryProperties.getNamespace(), discovery);
            if(serviceDiscovery.isSupportSubscribe()) {
                serviceDiscovery.subscribe(discoveryProperties.getNamespace(), (serverName, discovery, event) -> {
                    log.info("发现服务:{} -> {}", discovery, event);
                });
            }
        }

        @Override
        public void close() throws Exception {
            serviceDiscovery.close();
        }
    }
}
