package com.chua.starter.config.server.support.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.config.server.support.controller.ConfigurationBeanController;
import com.chua.starter.config.server.support.controller.ConfigurationCenterController;
import com.chua.starter.config.server.support.properties.ConfigServerProperties;
import com.chua.starter.config.server.support.properties.ConfigUniformProperties;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import com.chua.starter.config.server.support.uniform.Uniform;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * @author CH
 */
@Slf4j
@EntityScan(basePackages = {"com.chua.starter.config.server.support.repository"})
@EnableJpaRepositories(basePackages = {"com.chua.starter.config.server.support.repository"})
@EnableConfigurationProperties({ConfigServerProperties.class, ConfigUniformProperties.class})
public class ConfigServerConfiguration implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, DisposableBean {

    private ConfigServerProperties configServerProperties;
    private ConfigUniformProperties configUniformProperties;
    private Uniform uniform;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registerConfigCenter(registry);
        registerUniformCenter(registry);
    }

    /**
     * 注册统一检测中心
     * @param registry 注册器
     */
    private void registerUniformCenter(BeanDefinitionRegistry registry) {
        if(!configUniformProperties.isOpen()) {
            return;
        }

        String protocol = configUniformProperties.getProtocol();
        if(StringUtils.isEmpty(protocol)) {
            return;
        }

        ServiceProvider<Uniform> serviceProvider = ServiceProvider.of(Uniform.class);
        this.uniform = serviceProvider.getNewExtension(protocol, configUniformProperties);
        if(null != uniform) {
            uniform.start();
        }

    }

    /**
     * 注册开启配置中心
     * @param registry 注册器
     */
    private void registerConfigCenter(BeanDefinitionRegistry registry) {
        if(!configServerProperties.isOpen()) {
            return;
        }

        String protocol = configServerProperties.getProtocol();
        if(Strings.isNullOrEmpty(protocol)) {
            protocol = ConfigServerProperties.DEFAULT_PROTOCOL;
        }
        ServiceProvider<ProtocolServer> serviceProvider = ServiceProvider.of(ProtocolServer.class);
        ProtocolServer protocolServer = serviceProvider.getExtension(protocol);
        if(null != protocolServer) {
            registry.registerBeanDefinition(protocol, BeanDefinitionBuilder.genericBeanDefinition(protocolServer.getClass()).getBeanDefinition());
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }


    @Override
    public void setEnvironment(Environment environment) {
        configServerProperties = Binder.get(environment).bindOrCreate(ConfigServerProperties.PRE, ConfigServerProperties.class);
        configUniformProperties = Binder.get(environment).bindOrCreate(ConfigUniformProperties.PRE, ConfigUniformProperties.class);
    }



    @Bean
    @ConditionalOnMissingBean
    public ConfigurationCenterController configurationCenterController() {
        return new ConfigurationCenterController();
    }
    @Bean
    @ConditionalOnMissingBean
    public ConfigurationBeanController configurationBeanController() {
        return new ConfigurationBeanController();
    }

    @Override
    public void destroy() throws Exception {
        if(null == uniform) {
            return;
        }
        try {
            uniform.stop();
        } catch (Exception ignored) {
        }
    }
}
