package com.chua.report.server.starter.configuration;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.protocol.ServerSetting;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.server.starter.consumer.ReportConsumer;
import com.chua.report.server.starter.job.trigger.SchedulerTrigger;
import com.chua.report.server.starter.properties.ReportJobProperties;
import com.chua.report.server.starter.properties.ReportGenProperties;
import com.chua.report.server.starter.properties.ReportServerProperties;
import com.chua.report.server.starter.router.Router;
import com.chua.zbus.support.server.ZbusServer;
import io.zbus.mq.Broker;
import io.zbus.mq.BrokerConfig;
import io.zbus.mq.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

/**
 * 上报服务
 * @author CH
 * @since 2024/9/12
 */
@Slf4j
@MapperScan("com.chua.report.server.starter.mapper")
@ComponentScan({
        "com.chua.report.server.starter.service",
        "com.chua.report.server.starter.report.endpoint",
        "com.chua.report.server.starter.controller"
})
@EnableConfigurationProperties({ReportServerProperties.class, ReportJobProperties.class, ReportGenProperties.class})
public class ReportServerConfiguration implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, DisposableBean, CommandLineRunner, SmartInstantiationAwareBeanPostProcessor {
    private Integer serverPort;
    private int reportServerPort;
    private Broker broker;
    private Consumer consumer;
    private ReportConsumer reportConsumer;
    private ReportServerProperties reportServerProperties;
    private final Router router = new Router();

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registerZbusServer(registry);
        registry.registerBeanDefinition("TerminalSocketIOListener", BeanDefinitionBuilder.rootBeanDefinition(TerminalSocketIOListener.class)
                .getBeanDefinition());
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
    private void registerZbusServer(BeanDefinitionRegistry registry) {

        if(NetUtils.isPortInUsed(reportServerPort)) {
            log.info("MQ: {}已被占用", reportServerPort);
            return;
        }
        log.info("当前服务器 MQ: 127.0.0.1:{}", reportServerPort);
        registry.registerBeanDefinition("mqServer", BeanDefinitionBuilder.rootBeanDefinition(ZbusServer.class)
                .addConstructorArgValue(ServerSetting.builder()
                        .port(reportServerPort)
                        .host("0.0.0.0")
                        .build())
                .setDestroyMethodName("stop")
                .setInitMethodName("start")
                .getBeanDefinition()
        );
    }

    @Override
    public void setEnvironment(Environment environment) {
        ServerProperties serverProperties = Binder.get(environment).bindOrCreate("server", ServerProperties.class);
        reportServerProperties = Binder.get(environment).bindOrCreate(ReportServerProperties.PRE,  ReportServerProperties.class);
        serverPort = serverProperties.getPort();
        reportServerPort = serverPort + 10000;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = ReportJobProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
    public SchedulerTrigger schedulerTrigger() {
        log.info("开启定时任务功能");
        return new SchedulerTrigger();
    }

    @Override
    public void destroy() throws Exception {
        IoUtils.closeQuietly(reportConsumer);
        IoUtils.closeQuietly(broker);
    }

    @Override
    public void run(String... args) throws Exception {
        Thread.sleep(1000);
        registerZbusClient();
    }


    private void registerZbusClient() {
        BrokerConfig brokerConfig = new BrokerConfig();
        String reportEndpointHost = reportServerProperties.getReportEndpointHost();
        String endpoint = "";
        int reportEndpointPort = this.reportServerPort;
        if(StringUtils.isNotBlank(reportEndpointHost)) {
            endpoint = reportEndpointHost + ":" + reportServerProperties.getReportEndpointPort();
            reportServerPort = reportServerProperties.getReportEndpointPort();
        } else {
            endpoint = "127.0.0.1:" + reportServerPort;
        }
        brokerConfig.addTracker(endpoint);
        this.broker = new Broker(brokerConfig);
        this.reportConsumer = new ReportConsumer(router, broker, reportEndpointPort + "#report");
    }
}
