package com.chua.starter.monitor.factory;

import com.chua.common.support.json.Json;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.monitor.properties.MonitorConfigProperties;
import com.chua.starter.monitor.properties.MonitorMqProperties;
import com.chua.starter.monitor.properties.MonitorProperties;
import com.chua.starter.monitor.properties.MonitorProtocolProperties;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.request.MonitorRequestType;
import lombok.Getter;
import org.springframework.core.env.Environment;
import org.zbus.broker.Broker;
import org.zbus.broker.BrokerConfig;
import org.zbus.broker.HaBroker;
import org.zbus.broker.SingleBroker;
import org.zbus.mq.MqConfig;
import org.zbus.mq.Producer;
import org.zbus.net.http.Message;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private final ScheduledExecutorService scheduledExecutorService = ThreadUtils.newScheduledThreadPoolExecutor(1, "monitor-core-thread");
    private MonitorMqProperties monitorMqProperties;
    private MonitorProtocolProperties monitorProtocolProperties;
    private MonitorConfigProperties monitorConfigProperties;

    public static MonitorFactory getInstance() {
        return INSTANCE;
    }

    public void register(MonitorProperties monitorProperties) {
        this.monitorProperties = monitorProperties;
    }


    private void registerMqClient() {
        BrokerConfig brokerConfig = new BrokerConfig();
        String endpoint = monitorMqProperties.getMqHost() + ":" + monitorMqProperties.getMqPort();
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
        config.setMq(monitorMqProperties.getMqSubscriber());
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
        ThreadUtils.closeQuietly(scheduledExecutorService);
    }

    public void finish() {
        this.registerMqClient();
        this.heartbeat();
    }

    private void heartbeat() {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                MonitorRequest request = new MonitorRequest();
                request.setType(MonitorRequestType.HEARTBEAT);
                request.setAppName(appName);
                request.setProfile(active);
                request.setSubscribeAppName(monitorConfigProperties.getConfigAppName());
                request.setServerPort(environment.resolvePlaceholders("${server.port:8080}" ));
                request.setServerHost(environment.resolvePlaceholders("${server.address:127.0.0.1}" ));
                request.setData(monitorProtocolProperties);
                Message message = new Message();
                message.setBody(Json.toJSONBytes(request));
                producer.sendAsync(message);
            } catch (Throwable ignored) {
            }
        }, 0, 1, TimeUnit.MINUTES);
    }


    public boolean isEnable() {
        return monitorProperties.isEnable();
    }

    public void register(MonitorMqProperties monitorMqProperties) {
        this.monitorMqProperties = monitorMqProperties;
    }

    public void register(MonitorProtocolProperties monitorProtocolProperties) {
        this.monitorProtocolProperties = monitorProtocolProperties;
    }

    public void register(MonitorConfigProperties monitorConfigProperties) {
        this.monitorConfigProperties = monitorConfigProperties;
    }
}
