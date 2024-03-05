package com.chua.starter.monitor.factory;

import com.chua.common.support.function.Joiner;
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
    private MonitorSubscribeProperties monitorConfigProperties;
    private MonitorReportProperties monitorReportProperties;
    private String serverPort;
    private String serverHost;
    private List<String> plugins;
    private String endpointsUrl;
    private String contextPath;
    private boolean openIpPlugin;

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
        end();
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
    public void report(MonitorRequest request) {
        if (null == request) {
            return;
        }

        try {
            Message message = new Message();
            message.setBody(Json.toJSONBytes(request));
            reportProducer.sendAsync(message);
        } catch (Throwable ignored) {
        }
    }
    private void report() {
        if (CollectionUtils.isEmpty(plugins)) {
            return;
        }

        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                for (String plugin : plugins) {
                    if(StringUtils.isEmpty(plugin)) {
                        continue;
                    }
                    MonitorRequest request = createMonitorRequest();
                    request.setType(MonitorRequestType.REPORT);
                    request.setReportType(plugin.toUpperCase());
                    request.setData(ServiceProvider.of(Report.class).getNewExtension(plugin).report());
                    Message message = new Message();
                    message.setBody(Json.toJSONBytes(request));
                    reportProducer.sendAsync(message);
                }
            } catch (Throwable ignored) {
            }
        }, 0, monitorReportProperties.getReportTime(), TimeUnit.SECONDS);
    }

    public MonitorRequest createMonitorRequest() {
        MonitorRequest request = new MonitorRequest();
        request.setAppName(appName);
        request.setProfile(active);
        request.setSubscribeAppName(Joiner.on(",").join(monitorConfigProperties.getConfig()));
        request.setServerPort(serverPort);
        request.setServerHost(StringUtils.defaultString(monitorProtocolProperties.getHost(), serverHost));
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
    }

    public void register(MonitorProtocolProperties monitorProtocolProperties) {
        this.monitorProtocolProperties = monitorProtocolProperties;
        this.serverHost = StringUtils.defaultString(monitorProtocolProperties.getHost(), environment.resolvePlaceholders("${server.address:}"));
    }

    public void register(MonitorSubscribeProperties monitorConfigProperties) {
        this.monitorConfigProperties = monitorConfigProperties;
    }

    public void register(MonitorReportProperties monitorReportProperties) {
        this.monitorReportProperties = monitorReportProperties;
        this.plugins = monitorReportProperties.getPlugins();
        this.openIpPlugin = CollectionUtils.containsIgnoreCase("ip", plugins);
    }

    public String getSubscribeConfig() {
        return Joiner.on(',').join(monitorConfigProperties.getConfig());
    }

    public String getHotspotPath() {
        return monitorConfigProperties.getHotspot();
    }

    public boolean hasSubscribers() {
        return CollectionUtils.isNotEmpty(monitorConfigProperties.getConfig());
    }

    public void end() {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            try {
                MonitorRequest request = createMonitorRequest();
                request.setType(MonitorRequestType.START);
                request.setData(monitorProtocolProperties);
                Message message = new Message();
                message.setBody(Json.toJSONBytes(request));
                producer.sendAsync(message);
            } catch (Throwable ignored) {
            }
        });

    }

    public boolean isIpEnable() {
        return openIpPlugin;
    }
}
