package com.chua.starter.monitor.support.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;
import java.util.Collections;

/**
 * monitor 客户端引擎自动装配。
 *
 * <p>仅在宿主显式声明 {@code plugin.monitor.mode=client} 时装配（默认不生效，
 * 服务端宿主与存量宿主零影响）。引擎以 HTTP 通道向 monitor 服务端推送心跳与
 * 指标；OSHI 在 classpath 时启用本机采集，否则退化为「仅心跳 + 能力探测」。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.monitor", name = "mode", havingValue = "client")
@EnableConfigurationProperties(MonitorClientProperties.class)
public class MonitorClientAutoConfiguration {

    /**
     * 客户端推送引擎（含能力探测与心跳/推送调度）。
     *
     * @param properties 客户端配置
     * @param collectors 自定义采集器（存在则优先于默认 OSHI 采集器）
     * @return 已启动的推送引擎
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public MetricPushClient monitorMetricPushClient(MonitorClientProperties properties,
                                                    ObjectProvider<MonitorMetricCollector> collectors) {
        MonitorMetricCollector collector = collectors.getIfAvailable(this::defaultCollector);
        HttpClient httpClient = MonitorHttp.create(properties);
        MonitorCapabilityDetector detector = new MonitorCapabilityDetector(properties, httpClient);
        MetricPushClient client = new MetricPushClient(properties, collector, detector, httpClient);
        client.start();
        return client;
    }

    /**
     * 默认采集器：OSHI 在 classpath 时实例化 {@link OshiMetricCollector}，
     * 否则退化为空采集（推送引擎自动跳过空结果，仅保留心跳）。
     *
     * @return 采集器实例
     */
    private MonitorMetricCollector defaultCollector() {
        try {
            Class.forName("oshi.SystemInfo", false, MonitorClientAutoConfiguration.class.getClassLoader());
            return (MonitorMetricCollector) Class.forName(
                            "com.chua.starter.monitor.support.client.OshiMetricCollector",
                            true, MonitorClientAutoConfiguration.class.getClassLoader())
                    .getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            log.info("[monitor-client] OSHI 不可用（{}），本机指标采集停用，仅保留心跳与能力探测；"
                            + "如需本机指标请添加依赖 com.github.oshi:oshi-core",
                    e.getClass().getSimpleName());
            return Collections::emptyList;
        }
    }
}
