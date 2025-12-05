package com.chua.report.client.starter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上报客户端配置
 *
 * @author CH
 * @since 2024/12/05
 */
@Data
@ConfigurationProperties(prefix = "plugin.report.client")
public class ReportProperties {

    /**
     * 应用信息上报配置
     */
    private AppReport appReport = new AppReport();

    /**
     * 设备指标上报配置
     */
    private Metrics metrics = new Metrics();

    /**
     * 应用信息上报配置
     */
    @Data
    public static class AppReport {

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 上报间隔（秒）
         */
        private long interval = 30;
    }

    /**
     * 设备指标上报配置
     */
    @Data
    public static class Metrics {

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 上报间隔（秒）
         */
        private long interval = 30;
    }
}
