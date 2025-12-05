package com.chua.report.client.starter.configuration;

import com.chua.report.client.starter.job.JobReporter;
import com.chua.report.client.starter.report.DeviceMetricsReporter;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.report.client.starter.sync.handler.FileHandler;
import com.chua.report.client.starter.sync.handler.JobDispatchHandler;
import com.chua.sync.support.client.SyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * 上报客户端配置
 * <p>
 * 依赖 spring-support-sync-starter 提供的 SyncClient
 * </p>
 *
 * @author CH
 * @since 2024/12/05
 */
@Slf4j
@Configuration
@ConditionalOnClass(SyncClient.class)
@ConditionalOnBean(SyncClient.class)
public class ReportClientConfiguration {

    @Autowired
    private SyncClient syncClient;

    @Value("${spring.application.name:unknown}")
    private String appName;

    @Value("${plugin.report.client.metrics.interval:30}")
    private long metricsInterval;

    @Value("${plugin.report.client.metrics.enabled:true}")
    private boolean metricsEnabled;

    @PostConstruct
    public void init() {
        log.info("[ReportClient] 初始化");

        // 注入到 JobReporter
        JobReporter.getInstance().setSyncClient(syncClient);

        // 订阅主题
        syncClient.subscribe(
                MonitorTopics.JOB_DISPATCH,
                MonitorTopics.JOB_CANCEL,
                MonitorTopics.FILE_REQUEST
        );

        // 注册 Job 处理器
        JobDispatchHandler jobHandler = new JobDispatchHandler();
        jobHandler.setSyncClient(syncClient);
        syncClient.registerHandler(MonitorTopics.JOB_DISPATCH, jobHandler);
        syncClient.registerHandler(MonitorTopics.JOB_CANCEL, jobHandler);

        // 注册 File 处理器
        FileHandler fileHandler = new FileHandler();
        syncClient.registerHandler(MonitorTopics.FILE_REQUEST, fileHandler);

        // 启动设备指标上报
        if (metricsEnabled) {
            DeviceMetricsReporter reporter = DeviceMetricsReporter.getInstance();
            reporter.setSyncClient(syncClient);
            reporter.setAppName(appName);
            reporter.setIntervalSeconds(metricsInterval);
            reporter.start();
        }

        log.info("[ReportClient] 初始化完成 (Job, File, DeviceMetrics)");
    }

    @PreDestroy
    public void destroy() {
        DeviceMetricsReporter.getInstance().stop();
    }
}
