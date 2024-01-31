package com.chua.starter.monitor.server.configuration;

import com.chua.common.support.protocol.options.ServerOption;
import com.chua.starter.monitor.server.properties.MonitorServerProperties;
import com.chua.zbus.support.server.ZbusServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.zbus.broker.Broker;
import org.zbus.broker.BrokerConfig;
import org.zbus.broker.HaBroker;
import org.zbus.broker.SingleBroker;
import org.zbus.mq.Consumer;
import org.zbus.mq.MqConfig;
import org.zbus.net.http.Message;

import java.io.IOException;

/**
 * 监视服务器配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Slf4j
@EnableConfigurationProperties(MonitorServerProperties.class)
public class MonitorServerConfiguration implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware, DisposableBean, CommandLineRunner {

    private MonitorServerProperties monitorServerProperties;

    private Broker broker;
    private Consumer consumer;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registerMqServer(registry);
    }

    private void registerMqClient() {
        BrokerConfig brokerConfig = new BrokerConfig();
        String endpoint = monitorServerProperties.getMqHost() + ":" + monitorServerProperties.getMqPort();
        brokerConfig.setBrokerAddress(endpoint);
        try {
            if (endpoint.contains(",")) {
                this.broker = new HaBroker(brokerConfig);
            } else {
                this.broker = new SingleBroker(brokerConfig);
            }
        } catch (IOException var7) {
            throw new RuntimeException(var7);
        }

        MqConfig config = new MqConfig();
        config.setBroker(this.broker);
        config.setMq(monitorServerProperties.getMqSubscriber());
        this.consumer = new Consumer(config);
        try {
            consumer.start(new Consumer.ConsumerHandler() {
                @Override
                public void handle(Message msg, Consumer consumer) throws IOException {
                    System.out.println();
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void registerMqServer(BeanDefinitionRegistry registry) {
        registry.registerBeanDefinition("mqServer", BeanDefinitionBuilder.rootBeanDefinition(ZbusServer.class)
                .addConstructorArgValue(ServerOption.builder()
                        .port(monitorServerProperties.getMqPort())
                        .host(monitorServerProperties.getMqHost())
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
        try {
            if(null != broker) {
                broker.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if(null != consumer) {
                consumer.close();
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run(String... args) throws Exception {
        registerMqClient();
    }
}
