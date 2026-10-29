package com.chua.starter.datasourcehub.support.constants;

import java.util.List;
import java.util.Map;

/**
 * 数据源中心常量。
 *
 * <p>支持的数据源类型与各类型默认端口（未填端口时用于连通性探测与 URL 拼装）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class DatasourceHubConstants {

    /**
     * 接口根路径
     */
    public static final String API_PREFIX = "/ms/datasource";

    /**
     * clickhouse 类型标识。
     *
     * <p>走 JDBC（驱动在应用 classpath 上），因此数据库管理台、打开表、设计表、
     * 查询与版本探测全部复用 jdbc 那套，不为它单写管理面。默认端口取 HTTP 端口
     * 8123 —— clickhouse-jdbc 走的就是 HTTP 协议，不是 9000 的原生 TCP 端口。</p>
     */
    public static final String DS_TYPE_CLICKHOUSE = "clickhouse";

    /**
     * duckdb 类型标识。
     *
     * <p>单文件嵌入式库：没有主机、没有端口、没有服务端进程，{@code datasource_database}
     * 存的是**文件路径**（或 {@code :memory:}）。走 JDBC 复用同一套管理台，
     * 但探测与建连都必须按「文件库」处理 —— 不设 TCP 超时属性，也不做 TCP 探活。</p>
     */
    public static final String DS_TYPE_DUCKDB = "duckdb";

    /**
     * calcite 合并类型标识。
     *
     * <p>「合并数据源」产生的特殊记录：自身<b>不支持编辑</b>，内部在
     * {@code datasource_database} 存 {@code {"members":[id,...]}} 成员 JSON，
     * host/port/username/password 均为空。建连时按成员逐个走
     * {@code DatasourceService.connection(memberId)} 再用 Calcite 装配成联邦连接：
     * 每次打开控制台都会重新装配（成员调整后重开即生效）。</p>
     */
    public static final String DS_TYPE_CALCITE = "calcite";

    /**
     * 支持的数据源类型
     */
    public static final List<String> DS_TYPES = List.of(
            "jdbc", "redis", "influxdb", "neo4j", "nacos", "zookeeper", "kafka", "nginx", "etcd", "prometheus", "mongodb",
            "elasticsearch", DS_TYPE_CLICKHOUSE, DS_TYPE_DUCKDB);

    /**
     * 走 JDBC 协议的类型集合。
     *
     * <p>这三个类型的建连、探测、元数据、管理台是同一条路径，只差 URL 前缀；
     * 原先各处写死 {@code "jdbc".equalsIgnoreCase(type)}，加类型时漏一处就会出现
     * 「能连上但打不开表」这种半通状态，故收口到一处判断。</p>
     */
    public static final List<String> DS_JDBC_TYPES = List.of("jdbc", DS_TYPE_CLICKHOUSE, DS_TYPE_DUCKDB);

    /**
     * 该类型是否走 JDBC 协议。
     *
     * @param type 类型标识，可为 null
     * @return 走 JDBC 返回 true
     */
    public static boolean isJdbcFamily(String type) {
        if (type == null) {
            return false;
        }
        String normalized = type.trim().toLowerCase();
        return DS_JDBC_TYPES.contains(normalized) || DS_TYPE_CALCITE.equals(normalized);
    }

    /**
     * 该类型是否为 Calcite 合并数据源。
     *
     * <p>合并记录是 <b>由「合并」动作产生</b>的特殊台账：自身不支持编辑、
     * host/port/凭证均留空（建连走成员装配）。它与普通 JDBC 类型共用管理台，
     * 但 {@code create}/{@code update} 必须拒绝外部直接写这类记录。</p>
     *
     * @param type 类型标识，可为 null
     * @return 合并数据源返回 true
     */
    public static boolean isCalciteType(String type) {
        return type != null && DS_TYPE_CALCITE.equalsIgnoreCase(type.trim());
    }

    /**
     * 该类型是否为本地文件库（连接串里带磁盘路径，需要过路径白名单）。
     *
     * @param type 类型标识，可为 null
     * @return 文件库返回 true
     */
    public static boolean isFileJdbcType(String type) {
        return type != null && DS_TYPE_DUCKDB.equalsIgnoreCase(type.trim());
    }

    /**
     * nginx 类型标识。
     *
     * <p>进程控制方式（{@link #CONTROL_SYSTEMD} / {@link #CONTROL_LOCAL}）只对
     * nginx 有意义，写入前用它做类型闸门，避免别的类型也带上这两个字段。</p>
     */
    public static final String DS_TYPE_NGINX = "nginx";

    /**
     * influxdb 类型标识。
     *
     * <p>服务端大版本（{@code datasource_version}）只对 InfluxDB 有意义：
     * {@code 2}=Flux/Bucket，{@code 3}=SQL/InfluxQL/Database。</p>
     */
    public static final String DS_TYPE_INFLUXDB = "influxdb";

    /**
     * mongodb 类型标识。
     *
     * <p>字段约定与其它类型略有不同，接线时按此理解：</p>
     * <ul>
     *   <li>{@code host}/{@code port}：MongoDB 服务地址与端口（默认 {@value #MONGODB_DEFAULT_PORT}）；</li>
     *   <li>{@code database}：默认逻辑库名，留空按 {@value #MONGODB_DEFAULT_DATABASE}（admin）；</li>
     *   <li>{@code username}/{@code password}：凭据，认证库与 {@code database} 一致。</li>
     * </ul>
     */
    public static final String DS_TYPE_MONGODB = "mongodb";

    /**
     * Kafka 类型标识。
     *
     * <p>字段约定与其它中间件一致：{@code host}/{@code port} 为 Bootstrap 地址，
     * 端口默认 {@value #KAFKA_DEFAULT_PORT}；用户名/密码用于 SASL 凭据（可选）。</p>
     */
    public static final String DS_TYPE_KAFKA = "kafka";

    /**
     * Kafka 默认端口
     */
    public static final int KAFKA_DEFAULT_PORT = 9092;

    /**
     * MongoDB 默认端口
     */
    public static final int MONGODB_DEFAULT_PORT = 27017;

    /**
     * MongoDB 未登记库名时使用的默认逻辑库（兼作认证库）
     */
    public static final String MONGODB_DEFAULT_DATABASE = "admin";

    /**
     * Elasticsearch 类型标识。
     *
     * <p>字段约定与其它中间件一致：{@code host}/{@code port} 为 HTTP 接口地址，
     * 端口默认 {@value #ELASTICSEARCH_DEFAULT_PORT}；{@code username}/{@code password}
     * 为 Basic 认证凭据（留空表示匿名读，ES 允许）。</p>
     */
    public static final String DS_TYPE_ELASTICSEARCH = "elasticsearch";

    /**
     * Elasticsearch 默认 HTTP 端口
     */
    public static final int ELASTICSEARCH_DEFAULT_PORT = 9200;

    /**
     * prometheus 类型标识。
     *
     * <p>字段约定与其它类型略有不同，接线时按此理解：</p>
     * <ul>
     *   <li>{@code host}：Prometheus 主机（查询走它，配置管理也经 SSH 连它）；</li>
     *   <li>{@code port}：Prometheus HTTP API 端口（默认 9090），用于连通性探测与查询；
     *       配置文件管理固定的 SSH 端口为 {@link #SSH_DEFAULT_PORT}；</li>
     *   <li>{@code database}：配置根目录（默认 {@code /etc/prometheus}）；
     *       若填完整 http(s) URL，则原样作为 HTTP 基地址（此时配置根目录回退默认值）；</li>
     *   <li>{@code username}/{@code password}：SSH 凭据（配置文件读写、promtool 校验用）。</li>
     * </ul>
     */
    public static final String DS_TYPE_PROMETHEUS = "prometheus";

    /**
     * Prometheus 默认 HTTP API 端口
     */
    public static final int PROMETHEUS_DEFAULT_HTTP_PORT = 9090;

    /**
     * Prometheus 默认配置根目录
     */
    public static final String PROMETHEUS_DEFAULT_CONFIG_ROOT = "/etc/prometheus";

    /**
     * 各类型的默认端口（未填端口时使用）
     */
    public static final Map<String, Integer> DS_DEFAULT_PORTS = Map.ofEntries(
            Map.entry("jdbc", 3306),
            Map.entry("redis", 6379),
            Map.entry("influxdb", 8086),
            Map.entry("neo4j", 7474),
            Map.entry("nacos", 8848),
            Map.entry("zookeeper", 2181),
            Map.entry("nginx", 80),
            Map.entry("etcd", 2379),
            Map.entry("prometheus", PROMETHEUS_DEFAULT_HTTP_PORT),
            Map.entry(DS_TYPE_MONGODB, MONGODB_DEFAULT_PORT),
            Map.entry(DS_TYPE_KAFKA, KAFKA_DEFAULT_PORT),
            Map.entry(DS_TYPE_ELASTICSEARCH, ELASTICSEARCH_DEFAULT_PORT),
            // clickhouse-jdbc 走 HTTP 协议，默认端口是 8123（不是原生 TCP 的 9000）
            Map.entry(DS_TYPE_CLICKHOUSE, 8123));

    /**
     * 完整 JDBC 连接串前缀，库名字段以此开头时按原样连接
     */
    public static final String JDBC_URL_PREFIX = "jdbc:";

    /**
     * 数据库管理/探测使用的默认数据源 ID（null 或 0 表示应用主数据源）
     */
    public static final long DEFAULT_DATASOURCE_ID = 0L;

    /**
     * 隧道类型：本地转发（对应 ssh -L）
     */
    public static final String TUNNEL_LOCAL = "LOCAL";

    /**
     * 隧道类型：反向转发（对应 ssh -R），目标库在内网时用
     */
    public static final String TUNNEL_REMOTE = "REMOTE";

    /**
     * 隧道类型：动态 SOCKS5
     */
    public static final String TUNNEL_DYNAMIC = "DYNAMIC";

    /**
     * SSH 认证方式：口令
     */
    public static final String SSH_AUTH_PASSWORD = "PASSWORD";

    /**
     * SSH 认证方式：私钥
     */
    public static final String SSH_AUTH_KEY = "KEY";

    /**
     * SSH 默认端口
     */
    public static final int SSH_DEFAULT_PORT = 22;

    /**
     * 隧道默认本地绑定地址：只监听回环，不对外暴露中转端口
     */
    public static final String TUNNEL_DEFAULT_BIND = "127.0.0.1";

    /**
     * 支持的隧道类型
     */
    public static final List<String> TUNNEL_TYPES = List.of(TUNNEL_LOCAL, TUNNEL_REMOTE, TUNNEL_DYNAMIC);

    /**
     * 进程控制方式：由 systemctl 托管
     */
    public static final String CONTROL_SYSTEMD = "systemd";

    /**
     * 进程控制方式：本地目录裸进程（直接 {@code nginx -s} 与裸启动）
     */
    public static final String CONTROL_LOCAL = "local";

    /**
     * 支持的进程控制方式。
     *
     * <p>不含「自动」：自动探测是「两个都不填」的空值语义，不是三选一里的一个选项，
     * 否则前端一旦漏传就会退化成自动探测，用户以为自己选的是显式方式。</p>
     */
    public static final List<String> CONTROL_MODES = List.of(CONTROL_SYSTEMD, CONTROL_LOCAL);

    /**
     * 工具类禁止实例化
     */
    private DatasourceHubConstants() {
    }
}