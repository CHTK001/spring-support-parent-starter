package com.chua.starter.hotsport.support.config;

import com.chua.starter.hotsport.support.controller.ApmController;
import com.chua.starter.hotsport.support.server.HotsportTcpServer;
import com.chua.starter.hotsport.support.service.AlertMonitorService;
import com.chua.starter.hotsport.support.service.AlertStore;
import com.chua.starter.hotsport.support.service.ApmIngestService;
import com.chua.starter.hotsport.support.service.ApmQueryService;
import com.chua.starter.hotsport.support.service.JvmMemoryService;
import com.chua.starter.hotsport.support.service.JvmMetricsService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Hotsport REST API 自动装配 —— 供宿主应用（如 monitor）嵌入时注册 APM 接口。
 *
 * <p>{@code HotsportApplication} 独立启动时靠包扫描注册 {@code @RestController}/
 * {@code @Service} Bean；宿主应用的 {@code @SpringBootApplication} 不会扫描
 * {@code com.chua.starter.hotsport.support} 包，必须由本自动配置显式导入，
 * 否则 {@code /api/apm/*} 全部 404。</p>
 *
 * <p>显式 {@code @Import} 而非 {@code @ComponentScan}：避免把 {@code SpaWebConfig}
 * 的前端路由回退（/ /dashboard /traces → index.html）带进宿主应用，劫持宿主页面路由。</p>
 *
 * <p>开关：{@code plugin.hotsport.api.enabled}（默认启用）。{@code @EnableScheduling}
 * 驱动 {@link JvmMetricsService} 的周期采样（{@code plugin.hotsport.jvm.sample-interval-ms}，
 * 默认 5s）。</p>
 *
 * @author CH
 * @since 4.1.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "plugin.hotsport.api", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@Import({ApmController.class,
        ApmQueryService.class,
        ApmIngestService.class,
        JvmMetricsService.class,
        JvmMemoryService.class,
        AlertMonitorService.class,
        AlertStore.class,
        HotsportProperties.class,
        HotsportTcpServer.class})
public class HotsportApiAutoConfiguration {
}
