package com.chua.starter.discovery.support.configuration;

import com.chua.common.support.network.discovery.*;
import com.chua.common.support.network.discovery.Event;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.core.utils.DigestUtils;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.project.Project;
import com.chua.starter.discovery.support.properties.DiscoveryListProperties;
import com.chua.starter.discovery.support.properties.DiscoveryNodeProperties;
import com.chua.starter.discovery.support.properties.DiscoveryProperties;
import com.chua.starter.discovery.support.service.DiscoveryEnvironment;
import com.chua.starter.discovery.support.service.DiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.chua.starter.common.support.logger.ModuleLog.*;
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
    private static final Logger log = LoggerFactory.getLogger(DiscoveryConfiguration.class);
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
      this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        DiscoveryListProperties properties = Binder.get(environment).bindOrCreate(DiscoveryListProperties.PRE, DiscoveryListProperties.class);
        if (!properties.isEnable()) {
            log.info("[Discovery] 服务状态 [{}]", disabled());
            registry.registerBeanDefinition("discoveryService#embedd", BeanDefinitionBuilder.rootBeanDefinition(DiscoveryService.class, () -> {
                        return new DiscoveryService(new DefaultServiceDiscovery());
                    })
                    .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE)
                    .getBeanDefinition());
            return;
        }

        log.info("[Discovery] 服务状态 [{}]", enabled());
        List<DiscoveryProperties> properties1 = properties.getProperties();
        boolean hasServiceDiscovery = false;
        for (DiscoveryProperties discoveryProperties : properties1) {
            if (discoveryProperties.isEnabled()) {
                boolean created = registryBean(registry, discoveryProperties, !hasServiceDiscovery);
                if (created && !hasServiceDiscovery) {
                    hasServiceDiscovery = true;
                }
            }
        }
        if (!hasServiceDiscovery) {
            log.warn("[Discovery] 未发现可用的服务发现配置, 将使用默认实现");
            String baseBeanName = "serviceDiscovery#default";
            String beanName = baseBeanName;
            int index = 0;
            while (registry.containsBeanDefinition(beanName)) {
                beanName = baseBeanName + "#" + index++;
            }
            registry.registerBeanDefinition(beanName, BeanDefinitionBuilder.rootBeanDefinition(ServiceDiscovery.class, DefaultServiceDiscovery::new)
                    .setPrimary(true)
                    .getBeanDefinition());
        }

        registry.registerBeanDefinition("discoveryService#embedd", BeanDefinitionBuilder.rootBeanDefinition(DiscoveryService.class)
                .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE)
                .getBeanDefinition());
    }

    /**
     * 注册服务发现实现
     *
     * @param registry            Bean 注册器
     * @param discoveryProperties 当前发现配置
     * @param primaryCandidate    是否作为首选实现
     * @return 是否成功注册 ServiceDiscovery Bean
     */
    private boolean registryBean(BeanDefinitionRegistry registry, DiscoveryProperties discoveryProperties, boolean primaryCandidate) {
        DiscoveryOption discoveryOption = new DiscoveryOption();
        discoveryOption.setAddress(discoveryProperties.getAddress());
        discoveryOption.setUser(discoveryProperties.getUsername());
        discoveryOption.setPassword(discoveryProperties.getPassword());
        discoveryOption.setConnectionTimeoutMillis(discoveryProperties.getConnectionTimeoutMillis());
        discoveryOption.setSessionTimeoutMillis(discoveryProperties.getSessionTimeoutMillis());

        ServiceProvider<ServiceDiscovery> serviceProvider = ServiceProvider.of(ServiceDiscovery.class);
        ServiceDiscovery serviceDiscovery = serviceProvider.getNewExtension(discoveryProperties.getProtocol(), discoveryOption);
        String baseBeanName = "serviceDiscovery#" + discoveryProperties.getProtocol();
        String beanName = baseBeanName;
        int index = 0;
        while (registry.containsBeanDefinition(beanName)) {
            beanName = baseBeanName + "#" + index++;
        }
        if (null == serviceDiscovery) {
            log.warn("[Discovery] 未发现可用的服务实现");
            registry.registerBeanDefinition(beanName, BeanDefinitionBuilder.rootBeanDefinition(ServiceDiscovery.class, DefaultServiceDiscovery::new)
                    .setPrimary(primaryCandidate)
                    .getBeanDefinition());
            return true;
        }

        List<Discovery> discoveryList = new LinkedList<>();
        List<DiscoveryNodeProperties> node = discoveryProperties.getNode();
        for (DiscoveryNodeProperties discoveryNodeProperties : node) {
            discoveryList.add(registryNode(discoveryNodeProperties));
            log.info("[Discovery] 注册服务: {} -> {}", highlight(discoveryProperties.getProtocol()), discoveryNodeProperties.getNamespace());
        }
        registry.registerBeanDefinition(
                beanName,
                createBeanDefinitionDiscovery(serviceDiscovery, discoveryList, primaryCandidate));
        return true;
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

    /**
     * 创建服务发现 BeanDefinition
     *
     * @param serviceDiscovery 服务发现实现
     * @param discovery        发现配置列表
     * @param primary          是否作为首选实现
     * @return BeanDefinition
     */
    private BeanDefinition createBeanDefinitionDiscovery(ServiceDiscovery serviceDiscovery, List<Discovery> discovery, boolean primary) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(ServiceDiscovertyFactoryBean.class);
        beanDefinitionBuilder.addConstructorArgValue(serviceDiscovery);
        beanDefinitionBuilder.addConstructorArgValue(discovery);
        beanDefinitionBuilder.setDestroyMethodName("close");
        beanDefinitionBuilder.setInitMethodName("start");
        beanDefinitionBuilder.setPrimary(primary);
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


        public void start() throws Exception {
            serviceDiscovery.start();
            for (Discovery discovery : discoveryList) {
                serviceDiscovery.registerService(discovery.getUriSpec(), discovery);
                if(serviceDiscovery.isSupportSubscribe()) {
                    serviceDiscovery.subscribe(discovery.getUriSpec(), new ServiceDiscoveryListener() {
                        @Override
                        public void listen(String s, Discovery discovery, Event event) {
                            DiscoveryConfiguration.log.info("[Discovery] 服务变更: {} -> {}", highlight(discovery.getUriSpec()), event);
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
