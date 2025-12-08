package com.chua.report.client.starter.configuration;

import com.chua.report.client.starter.job.JobAnnotationScanner;
import com.chua.report.client.starter.job.JobReporter;
import com.chua.report.client.starter.properties.ReportProperties;
import com.chua.report.client.starter.report.AppRegisterReporter;
import com.chua.report.client.starter.report.DeviceMetricsReporter;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.report.client.starter.sync.handler.ApiFeatureHandler;
import com.chua.report.client.starter.sync.handler.FileHandler;
import com.chua.report.client.starter.sync.handler.JobDispatchHandler;
import com.chua.report.client.starter.sync.handler.LoggingConfigHandler;
import com.chua.starter.common.support.api.feature.ApiFeatureManager;
import com.chua.sync.support.client.SyncClient;
import com.chua.sync.support.configuration.SyncAutoConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

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
@AutoConfiguration
@AutoConfigureAfter(SyncAutoConfiguration.class)
@ConditionalOnClass(SyncClient.class)
@ConditionalOnBean(SyncClient.class)
@EnableConfigurationProperties(ReportProperties.class)
public class ReportClientConfiguration {

    private final SyncClient syncClient;
    private final Environment environment;
    private final ReportProperties reportProperties;
    private final ObjectProvider<ApiFeatureManager> featureManagerProvider;

    public ReportClientConfiguration(SyncClient syncClient, Environment environment,
                                      ReportProperties reportProperties,
                                      ObjectProvider<ApiFeatureManager> featureManagerProvider) {
        this.syncClient = syncClient;
        this.environment = environment;
        this.reportProperties = reportProperties;
        this.featureManagerProvider = featureManagerProvider;
    }

    @Value("${spring.application.name:unknown}")
    private String appName;

    @Value("${server.port:8080}")
    private Integer serverPort;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @PostConstruct
    public void init() {
        log.info("[ReportClient] 初始化");

        // 注入到 JobReporter
        JobReporter.getInstance().setSyncClient(syncClient);

        log.info("[ReportClient] 已向服务端注册");

        // 订阅主题
        syncClient.subscribe(
                MonitorTopics.JOB_DISPATCH,
                MonitorTopics.JOB_CANCEL,
                MonitorTopics.FILE_REQUEST,
                MonitorTopics.LOGGING_CONFIG
        );

        // 注册 Job 处理器
        JobDispatchHandler jobHandler = new JobDispatchHandler();
        syncClient.registerHandler(MonitorTopics.JOB_DISPATCH, jobHandler);
        syncClient.registerHandler(MonitorTopics.JOB_CANCEL, jobHandler);

        // 注册 File 处理器
        FileHandler fileHandler = new FileHandler();
        syncClient.registerHandler(MonitorTopics.FILE_REQUEST, fileHandler);

        // 注册 Logging 处理器
        LoggingConfigHandler loggingHandler = new LoggingConfigHandler();
        syncClient.registerHandler(MonitorTopics.LOGGING_CONFIG, loggingHandler);
        log.info("[ReportClient] 已注册日志配置处理器");

        // 注册 API Feature 处理器（如果可用）
        ApiFeatureManager featureManager = featureManagerProvider.getIfAvailable();
        if (featureManager != null) {
            syncClient.subscribe(MonitorTopics.API_FEATURE_CONTROL);
            ApiFeatureHandler apiFeatureHandler = new ApiFeatureHandler(featureManager);
            syncClient.registerHandler(MonitorTopics.API_FEATURE_CONTROL, apiFeatureHandler);
            log.info("[ReportClient] 已注册 API 功能开关处理器");
        }

        // 启动应用信息上报
        if (reportProperties.getAppReport().isEnabled()) {
            AppRegisterReporter appReporter = AppRegisterReporter.getInstance();
            appReporter.setSyncClient(syncClient);
            appReporter.setApplicationName(appName);
            appReporter.setHost(reportProperties.getInfo().getHost());
            appReporter.setActiveProfiles(environment.getActiveProfiles());
            appReporter.setServerPort(serverPort);
            appReporter.setContextPath(contextPath);
            appReporter.setReportInterval(reportProperties.getAppReport().getInterval());
            appReporter.start();
        }

        // 启动设备指标上报
        if (reportProperties.getMetrics().isEnabled()) {
            DeviceMetricsReporter reporter = DeviceMetricsReporter.getInstance();
            reporter.setSyncClient(syncClient);
            reporter.setAppName(appName);
            reporter.setHost(reportProperties.getInfo().getHost());
            reporter.setIntervalSeconds(reportProperties.getMetrics().getInterval());
            reporter.start();
        }

        log.info("[ReportClient] 初始化完成 (AppRegister, Job, File, DeviceMetrics)");
    }

    @PreDestroy
    public void destroy() {
        // 先发送下线通知
        AppRegisterReporter.getInstance().stop();
        DeviceMetricsReporter.getInstance().stop();
    }

    /**
     * 注册 Job 注解扫描器
     * <p>
     * 自动扫描 @Job 注解并注册为任务处理器
     * </p>
     *
     * @return JobAnnotationScanner
     */
    @Bean
    @ConditionalOnMissingBean
    public JobAnnotationScanner jobAnnotationScanner() {
        return new JobAnnotationScanner();
    }
}
