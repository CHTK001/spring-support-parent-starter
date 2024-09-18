package com.chua.report.client.starter.setting;

import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.crypto.AesCodec;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.http.HttpClient;
import com.chua.common.support.http.HttpResponse;
import com.chua.common.support.json.Json;
import com.chua.common.support.net.NetAddress;
import com.chua.common.support.protocol.ClientSetting;
import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.protocol.Protocol;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.*;
import com.chua.report.client.starter.endpoint.ModuleType;
import com.chua.report.client.starter.properties.ReportClientProperties;
import com.chua.report.client.starter.properties.ReportEndpointProperties;
import com.chua.report.client.starter.report.Report;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.zbus.support.protocol.ZbusClient;
import com.google.common.reflect.TypeToken;
import io.zbus.mq.Message;
import io.zbus.mq.Producer;
import lombok.Getter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.chua.starter.common.support.project.Project.KEY;

/**
 * 上报设置
 *
 * @author CH
 * @since 2024/9/11
 */
public class SettingFactory implements AutoCloseable, InitializingBean {
    private static final SettingFactory INSTANCE = new SettingFactory();
    private final boolean isServer;
    private final ExecutorService executorService;
    private Environment environment;
    private ReportClientProperties reportClientProperties;
    private ReportEndpointProperties reportEndpointProperties;
    private ScheduledExecutorService scheduledExecutorService;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    /**
     * -- GETTER --
     *
     * @return
     */
    @Getter
    private Integer endpointPort;
    private Set<ModuleType> endpointActive;
    private String reportServerAddress;
    private ServerProperties serverProperties;
    private String reportServerHost;
    private int reportServerPort;
    private ZbusClient zbusClient;
    @Getter
    private String reportTopic;
    private Producer producer;
    @Getter
    private Producer reportProducer;
    private Integer reportTime;

    public SettingFactory() {
        this.isServer = ClassUtils.isPresent("com.chua.report.server.starter.properties.ReportServerProperties");
        this.executorService = ThreadUtils.newVirtualThreadExecutor();
    }

    public static SettingFactory getInstance() {
        return INSTANCE;
    }

    /**
     * 是否是上报服务端
     *
     * @return 服务端
     */
    public boolean isServer() {
        return isServer;
    }

    /**
     * 注册环境配置
     * 该方法通过接收一个Environment对象来配置报告客户端的环境属性，并初始化报告客户端的配置属性
     *
     * @param environment 环境配置对象，用于初始化报告客户端的配置
     */
    public void register(Environment environment) {
        this.environment = environment;
        reportClientProperties = Binder.get(environment).bindOrCreate(ReportClientProperties.PRE, ReportClientProperties.class);
        serverProperties = Binder.get(environment).bindOrCreate("server", ServerProperties.class);
        reportEndpointProperties = Binder.get(environment).bindOrCreate(ReportEndpointProperties.PRE, ReportEndpointProperties.class);

        this.reportTime = reportClientProperties.getReportTime();
        this.reportServerAddress = reportClientProperties.getAddress();
        this.endpointPort = -1 == reportEndpointProperties.getPort() ?
                Converter.convertIfNecessary(environment.resolvePlaceholders("${server.port:8080}"), Integer.class) + 10000
                : reportEndpointProperties.getPort();
        this.endpointActive = reportEndpointProperties.getActive();
        if(null != reportServerAddress) {
            NetAddress netAddress = NetAddress.of(reportServerAddress);
            this.reportServerHost = netAddress.getHost();
            this.reportServerPort = netAddress.getPort() + 10000;
        }
    }


    private void initializeZbus() {
        zbusClient = new ZbusClient(ClientSetting.builder()
                .host(reportServerHost)
                .port(reportServerPort)
                .build());

        zbusClient.connect();
        this.reportTopic = reportServerPort + "#report";
        producer = zbusClient.createProducer();
        reportProducer = zbusClient.createProducer();
        this.report();
    }

    private void report() {
        Set<ReportEvent.ReportType> reportCollect = reportClientProperties.getReport();
        if (CollectionUtils.isEmpty(reportCollect)) {
            return;
        }

        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                for (ReportEvent.ReportType reportType : reportCollect) {
                    if (null == reportType) {
                        continue;
                    }
                    Report<?> report = ServiceProvider.of(Report.class).getNewExtension(reportType);
                    if (null == report) {
                        continue;
                    }
                    Message message = new Message();
                    message.setTopic(reportTopic);
                    message.setBody(Json.toJSONBytes(report.report()));
                    reportProducer.publish(message);
                }
            } catch (Throwable ignored) {
            }
        }, 0, reportTime, TimeUnit.SECONDS);
    }


    /**
     * 检查报告客户端是否启用
     * 通过检查报告客户端的配置属性来确定报告客户端是否被启用
     *
     * @return 如果报告客户端被启用，则返回true；否则返回false
     */
    public boolean isEnable() {
        return reportClientProperties.isEnable() && StringUtils.isNotBlank(reportServerAddress);
    }

    /**
     * 获取报告客户端的协议服务器
     * 通过创建一个ProtocolSetting对象来配置报告客户端的协议服务器，并使用Protocol.create()方法创建一个协议服务器对象
     *
     * @return 协议服务器对象
     */
    public Protocol getProtocol() {
        InetAddress address = serverProperties.getAddress();
        String bindHost = "0.0.0.0";
        if(null != address) {
            bindHost = address.getHostAddress();
        }
        Codec codec = new AesCodec("1234567890123456".getBytes(StandardCharsets.UTF_8));
        ProtocolSetting protocolSetting = ProtocolSetting.builder()
                .host(bindHost)
                .port(getEndpointPort())
                .codec(codec)
                .build();
        return Protocol.create(reportEndpointProperties.getProtocol(), protocolSetting);
    }

    /**
     * 检查当前环境是否匹配给定的活动
     * 通过比较当前环境中的激活环境列表和给定的活动名称来确定当前环境是否匹配给定的活动
     *
     * @param active 活动名称
     * @return 如果当前环境匹配给定的活动，则返回true；否则返回false
     */
    public boolean isProfileActive(String active) {
        return ArrayUtils.containsIgnoreCase(environment.getActiveProfiles(), active);
    }

    /**
     * 检查给定的功能名称是否在当前激活的环境中
     * 通过比较给定的功能名称和当前激活的环境列表来确定给定的功能名称是否在当前激活的环境中
     *
     * @param moduleType 功能名称
     * @return 如果给定的功能名称在当前激活的环境中，则返回true；否则返回false
     */
    public boolean isEndpointActive(ModuleType moduleType) {
        for (ModuleType item : endpointActive) {
            if (item == moduleType) {
                return true;
            }
        }
        return false;
    }

    /**
     * 发送请求
     * 通过给定的功能名称来发送请求，并返回请求的结果
     *
     * @param moduleType 功能名称
     * @return 请求的结果
     */
    public <E> E sendRequest(ModuleType moduleType, TypeToken<E> type) {
        HttpResponse response = HttpClient.post()
                .url(reportServerAddress + "/receive")
                .json()
                .body("data", DigestUtils.aesEncrypt(
                        Json.toJson(ImmutableBuilder.builderOfStringStringMap()
                                .put("type", moduleType.name())
                                .put("param", Json.toJson(SettingFactory.getInstance().endpoint()))
                                .newHashMap())
                        , KEY))
                .newInvoker().execute();
        String content = response.content(String.class);
        return Json.fromJson(content, type);
    }

    /**
     * 获取端点信息
     * 返回一个包含端点信息的Map对象，其中包含端口、协议和主机名等字段
     *
     * @return 端点信息
     */
    private Object endpoint() {
        return ImmutableBuilder.builderOfStringStringMap()
                .put("port", String.valueOf(getEndpointPort()))
                .put("protocol", reportEndpointProperties.getProtocol())
                .put("host", String.valueOf(getEndpointPort()))
                .newHashMap();
    }

    /**
     * 发送请求
     * 通过给定的功能名称来发送请求，并返回请求的结果
     *
     * @param moduleType 功能名称
     * @return 请求的结果
     */
    public void sendReportRequest(ModuleType moduleType, String params) {
        executorService.execute(() -> {
            HttpResponse response = HttpClient.post()
                    .url(reportServerAddress + "/report")
                    .json()
                    .body("data", DigestUtils.aesEncrypt(
                            Json.toJson(ImmutableBuilder.builderOfStringStringMap()
                                    .put("type", moduleType.name())
                                    .put("param", params)
                                    .newHashMap())
                            , KEY))
                    .newInvoker().execute();
        });
    }

    @Override
    public void close() throws Exception {
        IoUtils.closeQuietly(scheduledExecutorService);
        zbusClient.close();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!RUNNING.compareAndSet(false, true)) {
            return;
        }
        scheduledExecutorService = ThreadUtils.newScheduledThreadPoolExecutor(1, "com-ch-monitor-core-thread");
        initializeZbus();
    }
}
