// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志采集模块配置项（{@code plugin.logger.*}）。
 *
 * <p>本模块把「日志采集客户端」与「日志接收服务端」合并进同一个 starter，
 * 通过 {@code plugin.logger.type} 选择角色：</p>
 * <ul>
 *   <li>{@code client}（默认）：仅注入 Appender 采集本机日志并推送远端；</li>
 *   <li>{@code server}：仅启动接收端（TCP 同步服务端 + /v2/logger 查询 API）；</li>
 *   <li>{@code both}：同进程同时启用两端（常用于单机部署与联调）。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "plugin.logger")
public class LoggerProperties {

    /**
     * 角色类型：client | server | both
     */
    private String type = "client";

    /**
     * 应用名；为空时回落到 {@code spring.application.name}，再回落 {@code unknown}
     */
    private String appName = "";

    /**
     * 传输协议，目前固定 tcp（utils 同步通道）
     */
    private String transport = "tcp";

    /**
     * slf4j 绑定探测：auto（默认，自动识别 Logback/Log4j2）| logback | log4j2 | none
     */
    private String frameworks = "auto";

    private final Client client = new Client();

    private final Server server = new Server();

    private final Storage storage = new Storage();

    /**
     * 采集客户端配置
     */
    @Data
    public static class Client {

        /**
         * 服务端在发现中心的服务名
         */
        private String serviceName = "logger-server";

        /**
         * 业务分组标识（同一服务名下区分集群），为空时回落服务名
         */
        private String scatterId = "";

        /**
         * 显式服务端地址（如 tcp://172.16.9.194:19391）；发现不可用时使用
         */
        private String serverUrl = "";

        /**
         * 批量推送条数阈值
         */
        private int batchSize = 100;

        /**
         * 刷新间隔（毫秒）
         */
        private long flushIntervalMs = 1000L;

        /**
         * 内存队列容量；队列满时丢弃新事件并计数（不阻塞业务线程）
         */
        private int queueCapacity = 8192;
    }

    /**
     * 接收服务端配置
     */
    @Data
    public static class Server {

        /**
         * TCP 监听地址
         */
        private String host = "0.0.0.0";

        /**
         * TCP 监听端口
         */
        private int port = 19391;

        /**
         * 注册到发现中心的服务名（与客户端 service-name 对齐）
         */
        private String serviceName = "logger-server";

        /**
         * 注册到发现中心的业务分组标识，为空时回落服务名
         */
        private String scatterId = "";
    }

    /**
     * 存储配置
     */
    @Data
    public static class Storage {

        /**
         * 存储引擎：datasource-bean（默认，复用容器 DataSource 落 JDBC）| memory | redis-bean（SPI 探测）
         */
        private String engine = "datasource-bean";

        /**
         * 原始日志保留时长（小时）
         */
        private long retentionHours = 72L;

        /**
         * memory 引擎的最大保留条数
         */
        private int memoryCapacity = 100000;
    }
}
