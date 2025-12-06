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
@ConfigurationProperties(prefix = ReportProperties.PRE)
public class ReportProperties {

    public static final String PRE = "plugin.report.client";

    /**
     * 是否启用上报客户端
     */
    private boolean enable = true;

    /**
     * 应用信息上报配置
     */
    private AppReport appReport = new AppReport();

    /**
     * 设备指标上报配置
     */
    private Metrics metrics = new Metrics();

    /**
     * 同步配置（配置后自动串联到 sync 模块）
     */
    private SyncConfig sync = new SyncConfig();

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

    /**
     * 同步配置
     */
    @Data
    public static class SyncConfig {

        /**
         * 是否启用同步
         */
        private boolean enable = false;

        /**
         * 通信协议: rsocket, websocket
         */
        private String protocol = "rsocket";

        /**
         * 服务端地址
         */
        private String serverHost = "localhost";

        /**
         * 服务端端口
         */
        private int serverPort = 19380;
    }
}
