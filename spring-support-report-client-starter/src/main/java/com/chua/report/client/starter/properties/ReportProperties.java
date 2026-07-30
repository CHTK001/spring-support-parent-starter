package com.chua.report.client.starter.properties;

import com.chua.report.client.starter.report.MetricType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
     * 应用信息上报配置
     */
    private AppReport appReport = new AppReport();

    /**
     * 设备指标上报配置
     */
    private Metrics metrics = new Metrics();
    /**
     * 是否启用同步
     */
    private boolean enable = false;

    /**
     * 通信协议: rsocket
     */
    private String protocol = "rsocket-sync";

    /**
     * 服务端地址
     */
    private String host = "localhost";

    /**
     * 服务端端口
     */
    private int port = 29170;
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

        /**
         * 要上报的指标类型列表
         * <p>
         * 可选值: CPU, MEMORY, DISK, NETWORK, LOAD, PROCESS, TEMPERATURE
         * 默认为空表示全部上报
         * </p>
         */
        private Set<MetricType> types = new HashSet<>();

        /**
         * 获取要上报的指标类型
         *
         * @return 指标类型集合，为空返回全部
         * @author CH
         * @since 1.0.0
         */
        public Set<MetricType> getEffectiveTypes() {
            if (types == null || types.isEmpty()) {
                return new HashSet<>(Arrays.asList(MetricType.values()));
            }
            return types;
        }
    }

    /**
     * 客户端信息配置
     * <p>
     * 用于多网卡场景下指定客户端信息
     * </p>
     */
    private ClientInfo info = new ClientInfo();

    /**
     * URL QPS 统计配置
     */
    private UrlQps urlQps = new UrlQps();


    /**
     * 客户端信息配置
     */
    @Data
    public static class ClientInfo {

        /**
         * 客户端 IP 地址
         * <p>
         * 多网卡场景下指定使用的 IP 地址，为空则自动获取
         * </p>
         */
        private String host;

    }

    /**
     * URL QPS 统计配置
     */
    @Data
    public static class UrlQps {

        /**
         * 是否启用 URL QPS 统计
         */
        private boolean enabled = true;

        /**
         * 上报间隔（秒）
         */
        private long interval = 30;

        /**
         * 排除的 URL 前缀
         * <p>
         * 如: /actuator, /health
         * </p>
         */
        private Set<String> excludePatterns = new HashSet<>(Arrays.asList(
                "/actuator",
                "/health",
                "/favicon.ico"
        ));
    }

}
