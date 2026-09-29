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
     * 支持的数据源类型
     */
    public static final List<String> DS_TYPES = List.of(
            "jdbc", "redis", "influxdb", "neo4j", "nacos", "zookeeper", "nginx");

    /**
     * nginx 类型标识。
     *
     * <p>进程控制方式（{@link #CONTROL_SYSTEMD} / {@link #CONTROL_LOCAL}）只对
     * nginx 有意义，写入前用它做类型闸门，避免别的类型也带上这两个字段。</p>
     */
    public static final String DS_TYPE_NGINX = "nginx";

    /**
     * 各类型的默认端口（未填端口时使用）
     */
    public static final Map<String, Integer> DS_DEFAULT_PORTS = Map.of(
            "jdbc", 3306, "redis", 6379, "influxdb", 8086,
            "neo4j", 7474, "nacos", 8848, "zookeeper", 2181, "nginx", 80);

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