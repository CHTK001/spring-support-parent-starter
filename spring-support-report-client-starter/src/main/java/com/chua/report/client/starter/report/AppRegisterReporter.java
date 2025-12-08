package com.chua.report.client.starter.report;

import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.client.SyncClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 应用信息上报器
 * <p>
 * 定时上报Spring应用基本信息，服务端覆盖更新
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AppRegisterReporter {

    private static final AppRegisterReporter INSTANCE = new AppRegisterReporter();

    @Setter
    private SyncClient syncClient;

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    // Spring 应用信息
    @Setter
    private String applicationName;
    @Setter
    private String[] activeProfiles;
    @Setter
    private String host;
    @Setter
    private String contextPath;
    @Setter
    private Integer serverPort;

    private String ipAddress;
    private String hostname;

    // 上报间隔（秒）
    @Setter
    private long reportInterval = 30;

    // 启动时间
    private long startTime;

    public static AppRegisterReporter getInstance() {
        return INSTANCE;
    }

    /**
     * 启动定时上报
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        if (syncClient == null) {
            log.warn("[AppReport] SyncClient 未设置，无法启动上报");
            return;
        }

        // 初始化网络信息
        initNetworkInfo();
        this.startTime = System.currentTimeMillis();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "app-reporter");
            t.setDaemon(true);
            return t;
        });

        // 定时上报应用信息
        scheduler.scheduleAtFixedRate(this::report, 3, reportInterval, TimeUnit.SECONDS);

        running = true;
        log.info("[AppReport] 启动应用信息上报，间隔: {}秒", reportInterval);
    }

    /**
     * 停止上报
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
        running = false;
        log.info("[AppReport] 停止应用信息上报");
    }

    /**
     * 初始化网络信息
     */
    private void initNetworkInfo() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            this.ipAddress = StringUtils.defaultIfBlank(host, localHost.getHostAddress());
            this.hostname = localHost.getHostName();
        } catch (Exception e) {
            log.warn("[AppReport] 获取网络信息失败", e);
            this.ipAddress = "127.0.0.1";
            this.hostname = "localhost";
        }
    }

    /**
     * 上报应用信息
     */
    private void report() {
        try {
            Map<String, Object> info = collectAppInfo();
            syncClient.publish(MonitorTopics.APP_REPORT, info);
            log.debug("[AppReport] 上报应用信息: {}:{} ({})", ipAddress, serverPort, applicationName);
        } catch (Exception e) {
            log.error("[AppReport] 上报应用信息失败", e);
        }
    }

    /**
     * 采集应用信息
     */
    private Map<String, Object> collectAppInfo() {
        Map<String, Object> info = new HashMap<>();

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
        info.put("uptime", System.currentTimeMillis() - startTime);
        info.put("timestamp", System.currentTimeMillis());

        // 操作系统信息
        info.put("osName", System.getProperty("os.name"));
        info.put("osVersion", System.getProperty("os.version"));
        info.put("osArch", System.getProperty("os.arch"));

        // JVM 内存
        Runtime runtime = Runtime.getRuntime();
        info.put("heapUsed", runtime.totalMemory() - runtime.freeMemory());
        info.put("heapMax", runtime.maxMemory());
        info.put("threadCount", Thread.activeCount());

        return info;
    }


}
