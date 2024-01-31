package com.chua.starter.monitor.factory;

import com.chua.common.support.utils.IoUtils;
import com.chua.starter.monitor.properties.MonitorProperties;
import lombok.Getter;
import org.springframework.core.env.Environment;
import org.zbus.broker.Broker;
import org.zbus.broker.BrokerConfig;
import org.zbus.broker.HaBroker;
import org.zbus.broker.SingleBroker;
import org.zbus.mq.MqConfig;
import org.zbus.mq.Producer;

import java.io.IOException;

/**
 * 监视器工厂
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Getter
public class MonitorFactory implements AutoCloseable{

    private static final MonitorFactory INSTANCE = new MonitorFactory();
    private MonitorProperties monitorProperties;
    private String appName;
    private Environment environment;
    private String active;
    private Broker broker;
    private Producer producer;

    public static MonitorFactory getInstance() {
        return INSTANCE;
    }

    public void register(MonitorProperties monitorProperties) {
        this.monitorProperties = monitorProperties;
        this.registerMqClient();
    }


    private void registerMqClient() {
        BrokerConfig brokerConfig = new BrokerConfig();
        String endpoint = monitorProperties.getMqHost() + ":" + monitorProperties.getMqPort();
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
        config.setMq(monitorProperties.getMqSubscriber());
        this.producer = new Producer(config);
    }

    public void registerAppName(String appName) {
        this.appName = appName;
    }

    public void register(Environment environment) {
        this.environment = environment;
        this.active = environment.getProperty("spring.profiles.active", "default");
    }

    @Override
    public void close() throws Exception {
        IoUtils.closeQuietly(producer);
        IoUtils.closeQuietly(broker);
    }
}
