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
 * <p><b>完整 YAML 配置示例：</b></p>
 * <pre>{@code
 * plugin:
 *   logger:
 *     type: both                    # 角色：client | server | both（默认 client）
 *     app-name: order-service       # 应用名；空则回落 spring.application.name，再回落 unknown
 *     transport: tcp                # 传输协议，目前固定 tcp
 *     frameworks: auto              # slf4j 绑定探测：auto | logback | log4j2 | none
 *     client:
 *       service-name: logger-server # 服务端在发现中心的服务名
 *       scatter-id: prod-cluster    # 业务分组；空则回落 service-name
 *       server-url: ""              # 显式服务端地址（如 tcp://172.16.9.194:19391），发现不可用时兜底
 *       batch-size: 100             # 批量推送条数阈值（<=0 按 1 生效）
 *       flush-interval-ms: 1000     # 刷新间隔毫秒（<50 按 50 生效）
 *       queue-capacity: 8192        # 内存队列容量（<256 按 256 生效），满则丢弃并计数
 *     server:
 *       host: 0.0.0.0               # TCP 监听地址
 *       port: 19391                 # TCP 监听端口
 *       service-name: logger-server # 注册到发现中心的服务名（与客户端对齐）
 *       scatter-id: prod-cluster    # 注册分组；空则回落 service-name
 *       ingest-token: change-me     # POST /v2/logger/ingest 共享密钥；空不校验（请求头 x-logger-token）
 *     storage:
 *       engine: datasource-bean     # 存储引擎：datasource-bean | memory | redis-bean
 *       retention-hours: 72         # 原始日志保留时长（小时）
 *       memory-capacity: 100000     # memory 引擎最大保留条数
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "plugin.logger")
public class LoggerProperties {

    /**
     * 角色类型：client | server | both。
     *
     * <p>默认 {@code client}；{@code both} 表示同进程同时启用采集端与接收端。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   logger:
     *     type: both
     * }</pre>
     */
    private String type = "client";

    /**
     * 应用名；为空时回落到 {@code spring.application.name}，再回落 {@code unknown}。
     *
     * <p>默认 {@code ""}（走回落链）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   logger:
     *     app-name: order-service
     * }</pre>
     */
    private String appName = "";

    /**
     * 传输协议，目前固定 tcp（utils 同步通道）。
     *
     * <p>默认 {@code tcp}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   logger:
     *     transport: tcp
     * }</pre>
     */
    private String transport = "tcp";

    /**
     * slf4j 绑定探测：auto（默认，自动识别 Logback/Log4j2）| logback | log4j2 | none。
     *
     * <p>指定单一绑定时只安装对应 Appender；{@code none} 表示不注入任何 Appender。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   logger:
     *     frameworks: auto
     * }</pre>
     */
    private String frameworks = "auto";

    private final Client client = new Client();

    private final Server server = new Server();

    private final Storage storage = new Storage();

    /**
     * 采集客户端配置。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   logger:
     *     client:
     *       service-name: logger-server
     *       batch-size: 100
     *       flush-interval-ms: 1000
     * }</pre>
     */
    @Data
    public static class Client {

        /**
         * 服务端在发现中心的服务名。
         *
         * <p>默认 {@code logger-server}；需与服务端注册名对齐。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     client:
         *       service-name: logger-server
         * }</pre>
         */
        private String serviceName = "logger-server";

        /**
         * 业务分组标识（同一服务名下区分集群），为空时回落服务名。
         *
         * <p>默认 {@code ""}（回落 {@code client.service-name}）。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     client:
         *       scatter-id: prod-cluster
         * }</pre>
         */
        private String scatterId = "";

        /**
         * 显式服务端地址（如 tcp://172.16.9.194:19391）；发现不可用时使用。
         *
         * <p>默认 {@code ""}（优先走服务发现）；{@code both} 角色且留空时回环 {@code tcp://127.0.0.1:server.port}。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     client:
         *       server-url: tcp://172.16.9.194:19391
         * }</pre>
         */
        private String serverUrl = "";

        /**
         * 批量推送条数阈值。
         *
         * <p>默认 {@code 100}；配置 ≤ 0 时按 {@code 1} 生效。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     client:
         *       batch-size: 100
         * }</pre>
         */
        private int batchSize = 100;

        /**
         * 刷新间隔（毫秒）。
         *
         * <p>默认 {@code 1000}；配置 &lt; 50 时按 {@code 50} 生效。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     client:
         *       flush-interval-ms: 1000
         * }</pre>
         */
        private long flushIntervalMs = 1000L;

        /**
         * 内存队列容量；队列满时丢弃新事件并计数（不阻塞业务线程）。
         *
         * <p>默认 {@code 8192}；配置 &lt; 256 时按 {@code 256} 生效。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     client:
         *       queue-capacity: 8192
         * }</pre>
         */
        private int queueCapacity = 8192;
    }

    /**
     * 接收服务端配置。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   logger:
     *     server:
     *       host: 0.0.0.0
     *       port: 19391
     *       ingest-token: change-me
     * }</pre>
     */
    @Data
    public static class Server {

        /**
         * TCP 监听地址。
         *
         * <p>默认 {@code 0.0.0.0}（监听全部网卡）。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     server:
         *       host: 0.0.0.0
         * }</pre>
         */
        private String host = "0.0.0.0";

        /**
         * TCP 监听端口。
         *
         * <p>默认 {@code 19391}。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     server:
         *       port: 19391
         * }</pre>
         */
        private int port = 19391;

        /**
         * 注册到发现中心的服务名（与客户端 service-name 对齐）。
         *
         * <p>默认 {@code logger-server}。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     server:
         *       service-name: logger-server
         * }</pre>
         */
        private String serviceName = "logger-server";

        /**
         * 注册到发现中心的业务分组标识，为空时回落服务名。
         *
         * <p>默认 {@code ""}（回落 {@code server.service-name}）。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     server:
         *       scatter-id: prod-cluster
         * }</pre>
         */
        private String scatterId = "";

        /**
         * HTTP 推送（{@code POST /v2/logger/ingest}）共享密钥；为空表示不校验。
         * 校验方式：请求头 {@code x-logger-token} 必须与该值相等。
         *
         * <p>默认 {@code ""}（不校验）；生产环境建议显式配置。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     server:
         *       ingest-token: change-me
         * }</pre>
         */
        private String ingestToken = "";
    }

    /**
     * 存储配置。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   logger:
     *     storage:
     *       engine: datasource-bean
     *       retention-hours: 72
     *       memory-capacity: 100000
     * }</pre>
     */
    @Data
    public static class Storage {

        /**
         * 存储引擎：datasource-bean（默认，复用容器 DataSource 落 JDBC）| memory | redis-bean（SPI 探测）。
         *
         * <p>默认 {@code datasource-bean}；容器无 DataSource 或 redis-bean 的 SPI 缺失时回落 {@code memory} 并告警。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     storage:
         *       engine: datasource-bean
         * }</pre>
         */
        private String engine = "datasource-bean";

        /**
         * 原始日志保留时长（小时）。
         *
         * <p>默认 {@code 72}。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     storage:
         *       retention-hours: 72
         * }</pre>
         */
        private long retentionHours = 72L;

        /**
         * memory 引擎的最大保留条数。
         *
         * <p>默认 {@code 100000}；配置 &lt; 1000 时按 {@code 1000} 生效；超出容量丢最旧事件。</p>
         *
         * <p>YAML 示例：</p>
         * <pre>{@code
         * plugin:
         *   logger:
         *     storage:
         *       memory-capacity: 100000
         * }</pre>
         */
        private int memoryCapacity = 100000;
    }
}
