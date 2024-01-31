package com.chua.starter.monitor.configuration;

import com.chua.common.support.utils.ClassUtils;
import com.chua.starter.monitor.factory.MonitorFactory;
import com.chua.starter.monitor.properties.MonitorProperties;
import com.chua.starter.monitor.protocol.ProtocolFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * 监视器配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Slf4j
@EnableConfigurationProperties(MonitorProperties.class)
public class MonitorConfiguration  implements BeanDefinitionRegistryPostProcessor,
        ApplicationContextAware, EnvironmentAware, CommandLineRunner{
    private Environment environment;
    private MonitorProperties monitorProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        //注册协议
        registerProtocol(registry);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    private void registerProtocol(BeanDefinitionRegistry registry) {
        if(!monitorProperties.isEnable()) {
            return;
        }

        if(ClassUtils.isPresent("com.chua.starter.monitor.server.properties.MonitorServerProperties")) {
            return;
        }

        MonitorFactory monitorFactory = MonitorFactory.getInstance();
        monitorFactory.register(environment);
        monitorFactory.registerAppName(environment.getProperty("spring.application.name"));
        monitorFactory.register(monitorProperties);
        monitorFactory.finish();

        ProtocolFactory protocolFactory = new ProtocolFactory(registry);
        protocolFactory.afterPropertiesSet();
    }

    @Override
    public void run(String... args) throws Exception {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        monitorProperties = Binder.get(applicationContext.getEnvironment())
                .bindOrCreate(MonitorProperties.PRE, MonitorProperties.class);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
