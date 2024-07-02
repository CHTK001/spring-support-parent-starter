package com.chua.starter.monitor.factory;

import com.chua.common.support.function.Joiner;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.ClientSetting;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.monitor.properties.*;
import com.chua.starter.monitor.report.Report;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.request.MonitorRequestType;
import com.chua.zbus.support.protocol.ZbusClient;
import io.zbus.mq.Message;
import io.zbus.mq.Producer;
import lombok.Getter;
import org.springframework.core.env.Environment;

import java.util.*;
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
    private Set<String> activeProfiles;
    private ZbusClient zbusClient;
    private final ScheduledExecutorService scheduledExecutorService = ThreadUtils.newScheduledThreadPoolExecutor(2, "com-ch-monitor-core-thread");
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
    private Producer reportProducer;
    private Producer producer;
    private String topic;
    private String reportTopic;
    private boolean isServer;
    private String activeInclude;

    public static MonitorFactory getInstance() {
        return INSTANCE;
    }


    private void registerMqClient() {
        zbusClient = new ZbusClient(ClientSetting.builder()
                .host(monitorMqProperties.getHost())
                .port(monitorMqProperties.getPort())
                .build());

        zbusClient.connect();
        this.topic = monitorMqProperties.getSubscriber();
        this.reportTopic = this.topic + "#report";
        producer = zbusClient.createProducer();
        reportProducer = zbusClient.createProducer();
        end();
    }

    public void registerAppName(String appName) {
        this.appName = appName;
    }

    public void register(Environment environment) {
        this.environment = environment;
        this.serverPort = environment.resolvePlaceholders("${server.port:8080}");
        this.active = environment.getProperty("spring.profiles.active", "default");
        this.activeInclude = environment.getProperty("spring.profiles.include", "");
        if(StringUtils.isNotBlank(active)) {
            activeProfiles = new HashSet<>();
            activeProfiles.addAll(Splitter.on(',').splitToSet(active));
        }
        this.endpointsUrl = environment.resolvePlaceholders("${management.endpoints.web.base-path:/actuator}");
        this.contextPath = environment.resolvePlaceholders("${server.servlet.context-path:}");
    }

    @Override
    public void close() throws Exception {
        IoUtils.closeQuietly(zbusClient);
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
            message.setTopic(reportTopic);
            message.setBody(Json.toJSONBytes(request));
            reportProducer.publish(message);
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
                    Report newExtension = ServiceProvider.of(Report.class).getNewExtension(plugin);
                    if(null == newExtension) {
                        continue;
                    }
                    request.setData(newExtension.report());
                    Message message = new Message();
                    message.setTopic(reportTopic);
                    message.setBody(Json.toJSONBytes(request));
                    reportProducer.publish(message);
                }
            } catch (Throwable ignored) {
            }
        }, 0, monitorReportProperties.getReportTime(), TimeUnit.SECONDS);
    }

    public MonitorRequest createMonitorRequest() {
        MonitorRequest request = new MonitorRequest();
        request.setAppName(appName);
        request.setProfile(active);
        request.setSubscribeAppName(getSubscribeApps());
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
                message.setTopic(topic);
                producer.publish(message);
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
        this.openIpPlugin = CollectionUtils.containsIgnoreCase( plugins, "ip");
    }
    public String getSubscribeApps() {
        List<String> includes = new LinkedList<>(Optional.ofNullable(monitorConfigProperties.getApps()).orElse(Collections.emptyList()));
        if(StringUtils.isNotEmpty(activeInclude)) {
            includes.addAll(Splitter.on(',').omitEmptyStrings().trimResults().splitToList(activeInclude));
        }
        return Joiner.on(',').join(includes);
    }

    public String getHotspotPath() {
        return monitorConfigProperties.getHotspot();
    }

    public boolean hasSubscribers() {
        return CollectionUtils.isNotEmpty(monitorConfigProperties.getApps()) || StringUtils.isNotBlank(activeInclude);
    }

    public void end() {
        ThreadUtils.newVirtualThreadExecutor().execute(() -> {
            try {
                MonitorRequest request = createMonitorRequest();
                request.setType(MonitorRequestType.START);
                request.setData(monitorProtocolProperties);
                Message message = new Message();
                message.setTopic(topic);
                message.setBody(Json.toJSONBytes(request));
                producer.publish(message);
            } catch (Throwable ignored) {
            }
        });

    }

    public boolean isIpEnable() {
        return openIpPlugin;
    }

    public boolean inProfile(String profile) {
        if(CollectionUtils.isEmpty(activeProfiles)) {
            return true;
        }

        return CollectionUtils.containsIgnoreCase(activeProfiles, profile);

    }

    public boolean containsKey(String name) {
        List<String> options = monitorConfigProperties.getConfig();
        if(CollectionUtils.isEmpty(options)) {
            return true;
        }
        for (String s : options) {
            if(s.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否是服务端
     * @return
     */
    public boolean isServer() {
        return isServer;
    }

    /**
     * 是否是服务端
     * @param isServer
     */
    public void isServer(boolean isServer) {
        this.isServer = isServer;
    }
}
