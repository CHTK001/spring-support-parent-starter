package com.chua.starter.monitor.factory;

import com.chua.common.support.json.Json;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.monitor.properties.*;
import com.chua.starter.monitor.report.Report;
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
import java.util.List;
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
public class MonitorFactory implements AutoCloseable {

    private static final MonitorFactory INSTANCE = new MonitorFactory();
    private MonitorProperties monitorProperties;
    private String appName;
    private Environment environment;
    private String active;
    private Broker broker;
    private Producer reportProducer;
    private Producer producer;
    private final ScheduledExecutorService scheduledExecutorService = ThreadUtils.newScheduledThreadPoolExecutor(2, "monitor-core-thread");
    private MonitorMqProperties monitorMqProperties;
    private MonitorProtocolProperties monitorProtocolProperties;
    private MonitorConfigProperties monitorConfigProperties;
    private MonitorReportProperties monitorReportProperties;
    private String serverPort;
    private String serverHost;
    private List<String> plugins;
    private String endpointsUrl;
    private String contextPath;

    public static MonitorFactory getInstance() {
        return INSTANCE;
    }


    private void registerMqClient() {
        BrokerConfig brokerConfig = new BrokerConfig();
        String endpoint = monitorMqProperties.getHost() + ":" + monitorMqProperties.getPort();
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
        config.setMq(monitorMqProperties.getSubscriber());
        this.producer = new Producer(config);
        MqConfig configReport = new MqConfig();
        configReport.setBroker(this.broker);
        configReport.setMq(monitorMqProperties.getSubscriber() + "#report");
        this.reportProducer = new Producer(configReport);
    }

    public void registerAppName(String appName) {
        this.appName = appName;
    }

    public void register(Environment environment) {
        this.environment = environment;
        this.serverPort = environment.resolvePlaceholders("${server.port:8080}");
        this.active = environment.getProperty("spring.profiles.active", "default");
        this.endpointsUrl = environment.resolvePlaceholders("${management.endpoints.web.base-path:/actuator}");
        this.contextPath = environment.resolvePlaceholders("${server.servlet.context-path:}");
    }

    @Override
    public void close() throws Exception {
        IoUtils.closeQuietly(producer);
        IoUtils.closeQuietly(reportProducer);
        IoUtils.closeQuietly(broker);
        ThreadUtils.closeQuietly(scheduledExecutorService);
    }

    public void finish() {
        this.registerMqClient();
        this.heartbeat();
        this.report();
    }

    private void report() {
        if (CollectionUtils.isEmpty(plugins)) {
            return;
        }

        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                for (String plugin : plugins) {
                    MonitorRequest request = createMonitorRequest();
                    request.setType(MonitorRequestType.REPORT);
                    request.setReportType(plugin);
                    request.setData(ServiceProvider.of(Report.class).getNewExtension(plugin).report());
                    Message message = new Message();
                    message.setBody(Json.toJSONBytes(request));
                    reportProducer.sendAsync(message);
                }
            } catch (Throwable ignored) {
            }
        }, 0, 70, TimeUnit.SECONDS);
    }

    private MonitorRequest createMonitorRequest() {
        MonitorRequest request = new MonitorRequest();
        request.setAppName(appName);
        request.setProfile(active);
        request.setSubscribeAppName(monitorConfigProperties.getConfigAppName());
        request.setServerPort(serverPort);
        request.setServerHost(serverHost);
        request.setContextPath(contextPath);
        request.setEndpointsUrl(endpointsUrl);
        return request;
    }

    private void heartbeat() {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                MonitorRequest request = createMonitorRequest();
                request.setType(MonitorRequestType.HEARTBEAT);
                request.setData(monitorProtocolProperties);
                Message message = new Message();
                message.setBody(Json.toJSONBytes(request));
                producer.sendAsync(message);
            } catch (Throwable ignored) {
            }
        }, 0, monitorProperties.getKeepAliveTime(), TimeUnit.SECONDS);
    }


    public boolean isEnable() {
        return monitorProperties.isEnable();
    }

    public void register(MonitorMqProperties monitorMqProperties) {
        this.monitorMqProperties = monitorMqProperties;
    }

    public void register(MonitorProperties monitorProperties) {
        this.monitorProperties = monitorProperties;
        this.serverHost = StringUtils.defaultString(monitorProperties.getAddress(), environment.resolvePlaceholders("${server.address:}"));
    }

    public void register(MonitorProtocolProperties monitorProtocolProperties) {
        this.monitorProtocolProperties = monitorProtocolProperties;
    }

    public void register(MonitorConfigProperties monitorConfigProperties) {
        this.monitorConfigProperties = monitorConfigProperties;
    }

    public void register(MonitorReportProperties monitorReportProperties) {
        this.monitorReportProperties = monitorReportProperties;
        this.plugins = monitorReportProperties.getPlugins();
    }
}
