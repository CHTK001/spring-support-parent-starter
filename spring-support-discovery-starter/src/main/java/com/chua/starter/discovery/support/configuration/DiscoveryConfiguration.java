package com.chua.starter.discovery.support.configuration;

import com.chua.common.support.discovery.*;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.project.Project;
import com.chua.starter.discovery.support.properties.DiscoveryListProperties;
import com.chua.starter.discovery.support.properties.DiscoveryNodeProperties;
import com.chua.starter.discovery.support.properties.DiscoveryProperties;
import com.chua.starter.discovery.support.service.DiscoveryEnvironment;
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
import java.util.*;

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
        if(!properties.isEnable()) {
            log.warn("未开启 discovery 服务, 注册默认的服务");
            registry.registerBeanDefinition("discoveryService#embedd", BeanDefinitionBuilder.rootBeanDefinition(DiscoveryService.class, () ->{
                            return new DiscoveryService(new DefaultServiceDiscovery());
                    })
                    .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE)
                    .getBeanDefinition());
            return;
        }

        log.info(">>>>>>> 开启 discovery 服务");
        List<DiscoveryProperties> properties1 = properties.getProperties();
        for (DiscoveryProperties discoveryProperties : properties1) {
            if(discoveryProperties.isEnabled()) {
                registryBean(registry, discoveryProperties);
            }
        }

        registry.registerBeanDefinition("discoveryService#embedd", BeanDefinitionBuilder.rootBeanDefinition(DiscoveryService.class)
                .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE)
                .getBeanDefinition());
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
            registry.registerBeanDefinition(
                    discoveryProperties.getProtocol(), BeanDefinitionBuilder.rootBeanDefinition(ServiceDiscovery.class, DefaultServiceDiscovery::new).getBeanDefinition());
            return;
        }

        List<Discovery> discoveryList = new LinkedList<>();
        List<DiscoveryNodeProperties> node = discoveryProperties.getNode();
        for (DiscoveryNodeProperties discoveryNodeProperties : node) {
            discoveryList.add(registryNode(discoveryNodeProperties));
            log.warn("注册发现服务{} => {}", discoveryProperties.getProtocol(), discoveryNodeProperties.getNamespace());
        }
        registry.registerBeanDefinition(
                        discoveryProperties.getProtocol(),
                createBeanDefinitionDiscovery(serviceDiscovery, discoveryList));
    }

    private Discovery registryNode(DiscoveryNodeProperties discoveryNodeProperties) {
        Project project = Project.getInstance();
        String serverId = discoveryNodeProperties.getServerId();
        if(StringUtils.isEmpty(serverId)) {
            serverId = project.calcApplicationUuid();
        }
        Map<String, String> newMetaData = new LinkedHashMap<>(project.getProject());
        String serverIdValue = DigestUtils.md5Hex(project.getApplicationHost() + project.getApplicationPort());
        newMetaData.put("serverId", serverIdValue);
        Collection<DiscoveryEnvironment> beanList = SpringBeanUtils.getBeanList(DiscoveryEnvironment.class);
        for (DiscoveryEnvironment discoveryEnvironment : beanList) {
            String name = discoveryEnvironment.getName();
            if(null == name) {
                continue;
            }

            if(!name.equals(discoveryNodeProperties.getNamespace())) {
                continue;
            }

            Properties properties = discoveryEnvironment.getProperties();
            properties.forEach((key, value) -> {
                if(null == value) {
                    return;
                }
                newMetaData.put(key.toString(), value.toString());
            });
        }

        return Discovery.builder()
                .id(serverId)
                .serverId(serverIdValue)
                .uriSpec(discoveryNodeProperties.getNamespace())
                .port(project.getApplicationPort())
                .host(project.getApplicationHost())
                .protocol(discoveryNodeProperties.getProtocol())
                .metadata(newMetaData)
                .build();
    }

    private BeanDefinition createBeanDefinitionDiscovery(ServiceDiscovery serviceDiscovery, List<Discovery> discovery) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(ServiceDiscovertyFactoryBean.class);
        beanDefinitionBuilder.addConstructorArgValue(serviceDiscovery);
        beanDefinitionBuilder.addConstructorArgValue(discovery);
        beanDefinitionBuilder.setDestroyMethodName("close");
        beanDefinitionBuilder.setInitMethodName("start");
        return beanDefinitionBuilder.getBeanDefinition();
    }



    static class ServiceDiscovertyFactoryBean implements FactoryBean<ServiceDiscovery>, AutoCloseable{

        final ServiceDiscovery serviceDiscovery;
        private final List<Discovery> discoveryList;

        public ServiceDiscovertyFactoryBean(ServiceDiscovery serviceDiscovery, List<Discovery> discoveryList) {
            this.serviceDiscovery = serviceDiscovery;
            this.discoveryList = discoveryList;
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
            for (Discovery discovery : discoveryList) {
                serviceDiscovery.registerService(discovery.getUriSpec(), discovery);
                if(serviceDiscovery.isSupportSubscribe()) {
                    serviceDiscovery.subscribe(discovery.getUriSpec(), new ServiceDiscoveryListener() {
                        @Override
                        public void listen(String s, Discovery discovery, Event event) {
                            log.info("发现服务:{} -> {}", discovery, event);
                        }
                    });
                }
            }
        }

        @Override
        public void close() throws Exception {
            serviceDiscovery.close();
        }
    }
}
