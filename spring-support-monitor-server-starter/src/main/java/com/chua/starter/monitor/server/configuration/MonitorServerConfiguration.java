package com.chua.starter.monitor.server.configuration;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.protocol.options.ServerSetting;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.monitor.server.consumer.MonitorConsumer;
import com.chua.starter.monitor.server.consumer.ReportConsumer;
import com.chua.starter.monitor.server.job.trigger.SchedulerTrigger;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.properties.JobProperties;
import com.chua.starter.monitor.server.properties.MonitorServerProperties;
import com.chua.starter.monitor.server.router.Router;
import com.chua.zbus.support.server.ZbusServer;
import io.zbus.mq.Broker;
import io.zbus.mq.BrokerConfig;
import io.zbus.mq.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

/**
 * 监视服务器配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Slf4j
@MapperScan("com.chua.starter.monitor.server.mapper")
@ComponentScan("com.chua.starter.monitor.server")
@EnableConfigurationProperties({MonitorServerProperties.class, GenProperties.class, JobProperties.class})
public class MonitorServerConfiguration implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware, DisposableBean, CommandLineRunner, SmartInstantiationAwareBeanPostProcessor {

    private MonitorServerProperties monitorServerProperties;

    private Broker broker;
    private Consumer consumer;
    private final Router router = new Router();
    private MonitorConsumer mqConsumer;
    private ReportConsumer reportConsumer;



    @Bean
    @ConditionalOnMissingBean
    public SchedulerTrigger schedulerTrigger() {
        return new SchedulerTrigger();
    }
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registerMqServer(registry);
    }

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        registerBean(bean);
        return SmartInstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
    }

    private void registerBean(Object bean) {
        Class<?> aClass = bean.getClass();
        ReflectionUtils.doWithMethods(aClass, method -> {
            OnRouterEvent onRouterEvent = method.getDeclaredAnnotation(OnRouterEvent.class);
            if(null == onRouterEvent) {
                return;
            }

            router.addRoute(onRouterEvent, bean, method);
        });
    }

    private void registerMqClient() {
        BrokerConfig brokerConfig = new BrokerConfig();
        String endpoint = monitorServerProperties.getMqHost() + ":" + monitorServerProperties.getMqPort();
        brokerConfig.addTracker(endpoint);
        this.broker = new Broker(brokerConfig);
        this.mqConsumer = new MonitorConsumer(router, broker, monitorServerProperties.getMqSubscriber());
        this.reportConsumer = new ReportConsumer(router, broker, monitorServerProperties.getMqSubscriber());
    }

    private void registerMqServer(BeanDefinitionRegistry registry) {
        if(NetUtils.isPortInUsed(monitorServerProperties.getMqPort())) {
            log.info("MQ: {}已被占用", monitorServerProperties.getMqPort());
            return;
        }
        registry.registerBeanDefinition("mqServer", BeanDefinitionBuilder.rootBeanDefinition(ZbusServer.class)
                .addConstructorArgValue(ServerSetting.builder()
                        .port(monitorServerProperties.getMqPort())
                        .host("0.0.0.0")
                        .build())
                .setDestroyMethodName("stop")
                .setInitMethodName("start")
                .getBeanDefinition()
        );
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setEnvironment(Environment environment) {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.monitorServerProperties = Binder.get(applicationContext.getEnvironment())
                .bindOrCreate(MonitorServerProperties.PRE, MonitorServerProperties.class);
    }


    @Override
    public void destroy() throws Exception {
        IoUtils.closeQuietly(mqConsumer);
        IoUtils.closeQuietly(reportConsumer);
        IoUtils.closeQuietly(broker);
    }

    @Override
    public void run(String... args) throws Exception {
        Thread.sleep(1000);
        registerMqClient();
    }
}
