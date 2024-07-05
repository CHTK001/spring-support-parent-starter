package com.chua.starter.monitor.configuration;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.endpoint.RedisEndpoint;
import com.chua.starter.monitor.factory.MonitorFactory;
import com.chua.starter.monitor.filter.EmptyFilter;
import com.chua.starter.monitor.properties.*;
import com.chua.starter.monitor.protocol.ProtocolFactory;
import com.chua.starter.monitor.service.ProtocolRegisterCenterService;
import com.chua.starter.monitor.service.RegisterCenterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointsSupplier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 监视器配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Slf4j
@AutoConfigureAfter({WebEndpointAutoConfiguration.class, WebEndpointsSupplier.class})
@EnableConfigurationProperties({MonitorProperties.class, MonitorProtocolProperties.class, MonitorMqProperties.class, MonitorSubscribeProperties.class, MonitorReportProperties.class})
public class MonitorConfiguration  implements BeanDefinitionRegistryPostProcessor,
        ApplicationContextAware, EnvironmentAware, CommandLineRunner{
    private Environment environment;
    private MonitorProperties monitorProperties;
    private MonitorMqProperties monitorMqProperties;
    private MonitorProtocolProperties monitorProtocolProperties;
    private MonitorSubscribeProperties monitorSubscribeProperties;
    private MonitorReportProperties monitorReportProperties;
    private ServletEndpointsSupplier servletEndpointsSupplier;
    List<String> endpointId = new LinkedList<>();

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

        MonitorFactory monitorFactory = MonitorFactory.getInstance();
        monitorFactory.register(environment);
        monitorFactory.registerAppName(environment.getProperty("spring.application.name"));
        monitorFactory.register(monitorProperties);
        monitorFactory.register(monitorMqProperties);
        monitorFactory.register(monitorProtocolProperties);
        monitorFactory.register(monitorSubscribeProperties);
        monitorFactory.register(monitorReportProperties);
        monitorFactory.endpoint(endpointId);
        monitorFactory.isServer(ClassUtils.isPresent("com.chua.starter.monitor.server.properties.MonitorServerProperties"));

        if(monitorFactory.isServer()) {
            return;
        }
        monitorFactory.finish();

        ProtocolFactory protocolFactory = new ProtocolFactory(registry, environment);
        protocolFactory.afterPropertiesSet();
    }

    @Override
    public void run(String... args) throws Exception {
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        monitorProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(MonitorProperties.PRE, MonitorProperties.class);
        monitorReportProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(MonitorReportProperties.PRE, MonitorReportProperties.class);
        monitorMqProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(MonitorMqProperties.PRE, MonitorMqProperties.class);
        monitorProtocolProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(MonitorProtocolProperties.PRE, MonitorProtocolProperties.class);
        monitorSubscribeProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(MonitorSubscribeProperties.PRE, MonitorSubscribeProperties.class);
        refreshEndpoint(applicationContext);
    }

    private void refreshEndpoint(ApplicationContext applicationContext) {
        try {
            String[] beanNames = BeanFactoryUtils.beanNamesForAnnotationIncludingAncestors(applicationContext,
                    Endpoint.class);
            for (String beanName : beanNames) {
                if (!ScopedProxyUtils.isScopedTarget(beanName)) {
                    try {
                        Class<?> type = applicationContext.getType(beanName);
                        if(null == type) {
                            continue;
                        }
                        MergedAnnotation<Endpoint> annotation = MergedAnnotations.from(type , MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).get(Endpoint.class);
                        String id = annotation.getString("id");
                        if(StringUtils.isEmpty(id)) {
                            continue;
                        }
                        endpointId.add(id);
                    } catch (BeansException ignored) {
                    }
                }
            }
        } catch (BeansException ignored) {
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }



    /**
     * 注解Bean，如果不存在RedisConnection类则创建RedisEndpoint实例
     * @return RedisEndpoint实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RedisConnection.class)
    public RedisEndpoint redisEndpoint() {
        return new RedisEndpoint();
    }
    /**
     * 注解Bean，如果不存在RegisterCenterService实例则创建ProtocolRegisterCenterService实例
     * @return RegisterCenterService实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RegisterCenterService registerCenterService() {
        return new ProtocolRegisterCenterService();
    }


    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<EmptyFilter> filterRegistrationBean() {
        FilterRegistrationBean<EmptyFilter> filterRegistrationBean = new FilterRegistrationBean<EmptyFilter>();
        filterRegistrationBean.setFilter(new EmptyFilter());
        filterRegistrationBean.setUrlPatterns(Collections.singletonList("/*"));
        filterRegistrationBean.setAsyncSupported(true);

        return filterRegistrationBean;
    }

}
