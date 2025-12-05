package com.chua.report.client.starter.report;

import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.client.SyncClient;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 应用注册上报器
 * <p>
 * 客户端连接成功后上报Spring应用基本信息，用于在线节点展示
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
@Slf4j
public class AppRegisterReporter {

    private static final AppRegisterReporter INSTANCE = new AppRegisterReporter();

    private SyncClient syncClient;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    // 实例唯一标识
    private final String instanceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

    // Spring 应用信息
    private String applicationName;
    private String[] activeProfiles;
    private String contextPath;
    private Integer serverPort;
    private String ipAddress;
    private String hostname;

    // 心跳间隔（秒）
    private long heartbeatInterval = 30;

    // 启动时间
    private long startTime;

    private AppRegisterReporter() {
    }

    public static AppRegisterReporter getInstance() {
        return INSTANCE;
    }

    public void setSyncClient(SyncClient syncClient) {
        this.syncClient = syncClient;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public void setActiveProfiles(String[] activeProfiles) {
        this.activeProfiles = activeProfiles;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public void setHeartbeatInterval(long heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * 启动注册和心跳
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        if (syncClient == null) {
            log.warn("[AppRegister] SyncClient 未设置，无法启动注册");
            return;
        }

        // 初始化网络信息
        initNetworkInfo();
        this.startTime = System.currentTimeMillis();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "app-register");
            t.setDaemon(true);
            return t;
        });

        // 延迟3秒后发送注册信息（等待连接稳定）
        scheduler.schedule(this::register, 3, TimeUnit.SECONDS);

        // 定时发送心跳
        scheduler.scheduleAtFixedRate(this::heartbeat, 10, heartbeatInterval, TimeUnit.SECONDS);

        running = true;
        log.info("[AppRegister] 启动应用注册，心跳间隔: {}秒", heartbeatInterval);
    }

    /**
     * 停止并注销
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        // 发送下线通知
        unregister();

        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
        running = false;
        log.info("[AppRegister] 停止应用注册");
    }

    /**
     * 初始化网络信息
     */
    private void initNetworkInfo() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            this.ipAddress = localHost.getHostAddress();
            this.hostname = localHost.getHostName();
        } catch (Exception e) {
            log.warn("[AppRegister] 获取网络信息失败", e);
            this.ipAddress = "127.0.0.1";
            this.hostname = "localhost";
        }
    }

    /**
     * 发送注册信息
     */
    private void register() {
        try {
            Map<String, Object> registerInfo = collectRegisterInfo();
            registerInfo.put("eventType", "REGISTER");
            syncClient.publish(MonitorTopics.APP_REGISTER, registerInfo);
            log.info("[AppRegister] 应用注册成功: {}:{} ({})", ipAddress, serverPort, applicationName);
        } catch (Exception e) {
            log.error("[AppRegister] 应用注册失败", e);
        }
    }

    /**
     * 发送心跳
     */
    private void heartbeat() {
        try {
            Map<String, Object> heartbeatInfo = collectHeartbeatInfo();
            syncClient.publish(MonitorTopics.APP_HEARTBEAT, heartbeatInfo);
            log.debug("[AppRegister] 心跳发送: {}:{}", ipAddress, serverPort);
        } catch (Exception e) {
            log.error("[AppRegister] 心跳发送失败", e);
        }
    }

    /**
     * 发送下线通知
     */
    private void unregister() {
        try {
            Map<String, Object> unregisterInfo = new HashMap<>();
            unregisterInfo.put("instanceId", instanceId);
            unregisterInfo.put("applicationName", applicationName);
            unregisterInfo.put("ipAddress", ipAddress);
            unregisterInfo.put("serverPort", serverPort);
            unregisterInfo.put("eventType", "UNREGISTER");
            unregisterInfo.put("timestamp", System.currentTimeMillis());
            syncClient.publish(MonitorTopics.APP_UNREGISTER, unregisterInfo);
            log.info("[AppRegister] 应用下线通知: {}:{}", ipAddress, serverPort);
        } catch (Exception e) {
            log.error("[AppRegister] 下线通知发送失败", e);
        }
    }

    /**
     * 采集注册信息
     */
    private Map<String, Object> collectRegisterInfo() {
        Map<String, Object> info = new HashMap<>();

        // 实例标识
        info.put("instanceId", instanceId);

        // 应用基本信息
        info.put("applicationName", applicationName);
        info.put("activeProfiles", activeProfiles != null ? String.join(",", activeProfiles) : "default");
        info.put("contextPath", contextPath != null ? contextPath : "/");

        // 网络信息
        info.put("ipAddress", ipAddress);
        info.put("hostname", hostname);
        info.put("serverPort", serverPort);

        // 构建服务地址
        String serviceUrl = String.format("http://%s:%d%s", ipAddress, serverPort != null ? serverPort : 8080,
                contextPath != null && !contextPath.isEmpty() ? contextPath : "");
        info.put("serviceUrl", serviceUrl);

        // JVM 信息
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        info.put("jvmName", runtimeBean.getVmName());
        info.put("jvmVersion", runtimeBean.getVmVersion());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("pid", runtimeBean.getPid());

        // 运行时信息
        info.put("startTime", startTime);
        info.put("registerTime", System.currentTimeMillis());
        info.put("online", true);

        // 操作系统信息
        info.put("osName", System.getProperty("os.name"));
        info.put("osVersion", System.getProperty("os.version"));
        info.put("osArch", System.getProperty("os.arch"));

        return info;
    }

    /**
     * 采集心跳信息（精简版）
     */
    private Map<String, Object> collectHeartbeatInfo() {
        Map<String, Object> info = new HashMap<>();

        // 核心标识
        info.put("instanceId", instanceId);
        info.put("applicationName", applicationName);
        info.put("ipAddress", ipAddress);
        info.put("serverPort", serverPort);

        // 运行状态
        info.put("online", true);
        info.put("uptime", System.currentTimeMillis() - startTime);
        info.put("timestamp", System.currentTimeMillis());

        // JVM 内存使用
        Runtime runtime = Runtime.getRuntime();
        info.put("heapUsed", runtime.totalMemory() - runtime.freeMemory());
        info.put("heapMax", runtime.maxMemory());
        info.put("threadCount", Thread.activeCount());

        return info;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
