package com.chua.starter.unified.client.support.configuration;

import com.chua.common.support.protocol.boot.Protocol;
import com.chua.starter.unified.client.support.endpoint.FileHandlerEndpoint;
import com.chua.starter.unified.client.support.factory.ExecutorFactory;
import com.chua.starter.unified.client.support.factory.ProtocolFactory;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;

/**
 * 统一客户端配置
 *
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties(UnifiedClientProperties.class)
public class UnifiedClientConfiguration implements BeanDefinitionRegistryPostProcessor,
        ApplicationContextAware, EnvironmentAware, CommandLineRunner {

    @Resource
    private UnifiedClientProperties unifiedClientProperties;

    private String appName;
    private Environment environment;
    private ExecutorFactory executorFactory;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        //注册协议
        registerProtocol(registry);
    }

    private void registerProtocol(BeanDefinitionRegistry registry) {
        if(!unifiedClientProperties.isOpen()) {
            return;
        }

        this.appName = environment.getProperty("spring.application.name");

        ProtocolFactory protocolFactory = new ProtocolFactory(unifiedClientProperties, appName, environment, registry);
        protocolFactory.afterPropertiesSet();
        Protocol protocol = protocolFactory.getProtocol();
        this.executorFactory = new ExecutorFactory(protocol, unifiedClientProperties, appName, environment);
        executorFactory.afterPropertiesSet();
    }



    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        unifiedClientProperties = Binder.get(applicationContext.getEnvironment())
                .bindOrCreate(UnifiedClientProperties.PRE, UnifiedClientProperties.class);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {
    }


    /**
     * 文件处理程序终结点
     *
     * @return {@link FileHandlerEndpoint}
     */
    @Bean
    @ConditionalOnMissingBean
    public FileHandlerEndpoint fileHandlerEndpoint() {
        return new FileHandlerEndpoint();
    }

}
