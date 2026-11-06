// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.support;

import com.chua.common.support.value.ExpireValue;
import com.chua.starter.datasourcehub.support.config.DatasourceHubProperties;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.service.DatasourceTunnelService;
import com.chua.starter.datasourcehub.support.service.JdbcDriverJarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 建连与探活的公共机制：连接串拼装、连接属性、SSH 隧道、空闲复用、方言指标。
 *
 * <p><b>为什么单独拆出来</b>：这些逻辑原先都是 {@code DatasourceServiceImpl} 的私有方法，
 * 与台账增删改查揉在一个类里。引入 {@code DatasourceConnector} 扩展点后，JDBC 分支
 * 需要被独立实现复用，而这些方法又必须只有一份（会话超时单位、隧道改写、
 * 文件库路径白名单这些坑，复制一份就必然分叉）。所以抽成组件：扩展点实现调用它，
 * 服务层不再直接持有建连细节。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcDatasourceSupport {

    /**
     * 台账 {@code datasource_version} 列的宽度（VARCHAR(64)）。
     */
    private static final int VERSION_MAX_LENGTH = 64;

    /**
     * 数据源中心配置
     */
    private final DatasourceHubProperties properties;

    /**
     * SSH 隧道服务：目标库内网不可直连时，先经隧道中转再测
     */
    private final DatasourceTunnelService tunnelService;

    /**
     * 驱动 jar 库（数据源引用上传的驱动时用它建连）
     */
    private final JdbcDriverJarService driverJarService;

    /**
     * JDBC 连接缓存：datasourceId -&gt; 带空闲过期的连接。
     *
     * <p>每次操作都经这里的 {@code connection} 取连接，直连会反复建连/断连；
     * 这里用 {@link ExpireValue} 做空闲 TTL 复用：TTL 内复用同一物理连接，
     * 过期回调自动关闭，连接被服务端断开时自动重建。</p>
     */
    private final Map<Long, ExpireValue<Connection>> jdbcConnectionCache = new ConcurrentHashMap<>();

    /**
     * 后台清扫：把闲置超过 TTL 的 JDBC 连接真正关闭掉。
     *
     * <p>为什么需要：{@link ExpireValue} 的过期是<b>惰性</b>的 —— 只有下一次取连接时
     * 才会触发过期回调；数据源一旦闲置（页面关了、当天不再查询），期间没有任何取连接
     * 动作，物理连接就会一直挂到进程重启或数据源被删除，表现为后端 socket 泄漏。
     * 这里按 60s 的固定节拍主动核对，到期的持锁走一次 {@code getValue()} 触发同一套
     * 过期回调（{@code onExpireNotify → closeQuietly}）关闭连接。</p>
     */
    @Scheduled(fixedDelay = 60_000L)
    public void sweepExpiredJdbcConnections() {
        if (jdbcConnectionCache.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, ExpireValue<Connection>> entry : jdbcConnectionCache.entrySet()) {
            ExpireValue<Connection> holder = entry.getValue();
            // 空壳（值已清除）没有连接可关；未过期的复用连接不碰
            if (holder.isNull() || !holder.isExpire()) {
                continue;
            }
            synchronized (holder) {
                if (holder.isNull() || !holder.isExpire()) {
                    continue;
                }
                holder.getValue();
            }
            log.debug("[datasource-hub] 清扫空闲过期连接: datasourceId={}", entry.getKey());
        }
    }

    /**
     * 取可复用的 JDBC 连接。
     *
     * @param row 台账记录
     * @return 屏蔽了 close 的连接代理
     * @throws Exception 建连失败
     */
    public Connection connection(MsDatasource row) throws Exception {
        String url = resolveJdbcUrl(row);
        url = applyTunnel(row, url);
        Properties props = buildJdbcProps(row, url);
        return cachedJdbcConnection(row, url, props, driverJarOf(row));
    }

    /**
     * 新建一条独立连接（进缓存前的那一份，用后即关）。
     *
     * @param row 台账记录
     * @return 新建连接
     * @throws Exception 建连失败
     */
    public Connection newConnection(MsDatasource row) throws Exception {
        String url = resolveJdbcUrl(row);
        url = applyTunnel(row, url);
        Properties props = buildJdbcProps(row, url);
        java.io.File driverJar = driverJarOf(row);
        Connection fresh = driverJar == null
                ? DriverManager.getConnection(url, props)
                : JdbcDriverSupport.connect(driverJar, requireDriverClassName(row), url, props);
        log.debug("[datasource-hub] 新建独立 JDBC 连接: id={} url={}", row.getId(), url);
        return fresh;
    }

    /**
     * JDBC 探活：连接延迟 = 完整握手耗时，会话延迟 = {@code SELECT 1} 往返耗时。
     *
     * <p>刻意走 {@link DriverManager} 直连而不用复用连接：后者命中缓存时
     * 返回的是已有连接，计时会得到接近 0 的假值。探活连接用完即关，不进缓存。</p>
     *
     * @param row       台账记录
     * @param timeoutMs 超时（毫秒）
     * @return 探活结果
     */
    public DatasourceProbeSupport.ProbeOutcome probeJdbc(MsDatasource row, int timeoutMs) {
        String url;
        try {
            url = resolveJdbcUrl(row);
        } catch (IllegalStateException e) {
            // 文件库路径越界等台账配置错误：收成一次「测试失败」而不是抛给 advice 变 409 ——
            // 用户点「测试连接」期待的是结果面板里的红字，不是一个 HTTP 状态码。
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null, e.getMessage(), null);
        }
        // 探活也必须尊重上传的驱动 jar 与隧道：不走的话，用户点「测试连接」会拿到
        // ClassNotFoundException 或连不上，而同一个数据源真正使用时是通的 ——
        // 「测试失败但实际能用」比「测试失败」更难解释。
        java.io.File driverJar;
        try {
            driverJar = driverJarOf(row);
        } catch (java.io.IOException e) {
            // 定位不到 jar 文件时给可读原因，而不是让整个探活抛栈
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null,
                    "驱动 jar 定位失败: " + e.getMessage(), null);
        }
        url = applyTunnel(row, url);
        Properties props = buildJdbcProps(row, url);
        long start = System.nanoTime();
        try (Connection conn = driverJar == null
                ? DriverManager.getConnection(url, props)
                : JdbcDriverSupport.connect(driverJar, requireDriverClassName(row), url, props)) {
            long connectMs = (System.nanoTime() - start) / 1_000_000L;
            if (!conn.isValid(Math.max(1, timeoutMs / 1000))) {
                return new DatasourceProbeSupport.ProbeOutcome(false, connectMs, null, "连接已建立但校验未通过", null);
            }
            long sessionStart = System.nanoTime();
            try (var statement = conn.createStatement()) {
                statement.execute("SELECT 1");
            }
            long sessionMs = (System.nanoTime() - sessionStart) / 1_000_000L;
            // 连接数是顺手采的瞬时值：同一条探测连接上多跑一条只读查询，代价可忽略；
            // 方言不认识就留 null，图上缺一格，比编个 0 好。
            Long connectionCount = queryJdbcConnectionCount(conn, url);
            DatasourceProbeSupport.ServerStats serverStats = queryJdbcServerStats(conn, url);
            String version = queryJdbcServerVersion(conn, url);
            return new DatasourceProbeSupport.ProbeOutcome(true, connectMs, sessionMs,
                    "连接测试成功", connectionCount, serverStats, version);
        } catch (Exception e) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null,
                    "连接测试失败: " + e.getMessage(), null);
        }
    }

    /**
     * 取该数据源引用的驱动 jar 文件；没配就返回 null（走内置驱动）。
     *
     * @param row 数据源台账
     * @return jar 文件；未配置返回 null
     * @throws java.io.IOException 定位 jar 文件失败
     */
    public java.io.File driverJarOf(MsDatasource row) throws java.io.IOException {
        return driverJarService.jarFileOf(row.getDatasourceDriverJarId());
    }

    /**
     * 取数据源上登记的驱动类名；没登记就用 jar 台账里的。
     *
     * @param row 数据源台账
     * @return 驱动类全名
     */
    public String requireDriverClassName(MsDatasource row) {
        String fromRow = row == null ? null : row.getDatasourceDriverClassName();
        String fromJar = driverJarService.driverClassNameOf(
                row == null ? null : row.getDatasourceDriverJarId(), null);
        String name = fromJar != null ? fromJar : (fromRow == null ? null : fromRow.trim());
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException("数据源 " + (row == null ? null : row.getId())
                    + " 使用上传的驱动 jar，但既没登记驱动类名、jar 台账里也没有；"
                    + "请在数据源上填写驱动类名（如 org.h2.Driver）或重新上传时填写");
        }
        return name;
    }

    /**
     * 把连接串的 authority 换成 SSH 隧道的本地端点；没配隧道时原样返回。
     *
     * <p>探活与真实建连都必须过这一段：只改一处的话，「测试连接」和实际使用
     * 会连到不同的地方，用户看到的是「测试通了但打开表报错」。</p>
     *
     * @param row 数据源台账
     * @param url 已解析出的连接串
     * @return 走隧道后的连接串
     */
    public String applyTunnel(MsDatasource row, String url) {
        DatasourceTunnelService.TunnelEndpoint endpoint = openTunnel(row);
        if (endpoint == null) {
            return url;
        }
        String rewritten = rewriteAuthority(url, endpoint.host(), endpoint.port());
        if (!rewritten.equals(url)) {
            log.info("[datasource-hub] 数据源走 SSH 隧道: id={} {} -> {}:{}",
                    row.getId(), url, endpoint.host(), endpoint.port());
        }
        return rewritten;
    }

    /**
     * 构建 JDBC 连接属性：账号口令 + MySQL 系元数据开关 + 建连超时。
     *
     * @param row 数据源台账
     * @param url JDBC URL
     * @return 连接属性
     */
    public Properties buildJdbcProps(MsDatasource row, String url) {
        Properties props = new Properties();
        // DuckDB 是本地文件库，没有账号概念：塞空的 user/password 会被驱动当成
        // 非法连接属性直接拒连，所以整段跳过。
        if (!(url != null && url.regionMatches(true, 0, "jdbc:duckdb:", 0, "jdbc:duckdb:".length()))) {
            props.setProperty("user", row.getDatasourceUsername() == null ? "" : row.getDatasourceUsername());
            // 台账里明文与 b64: 前缀两种存法共存，见 DatasourceSecrets
            props.setProperty("password", DatasourceSecrets.decode(row.getDatasourcePassword()));
        }
        // MySQL Connector/J 默认不读 information_schema：列注释/长度、索引元数据会缺失，
        // 数据库管理器的表结构/索引页依赖这些元数据，故显式开启。
        if (url.regionMatches(true, 0, "jdbc:mysql:", 0, "jdbc:mysql:".length())
                || url.regionMatches(true, 0, "jdbc:mariadb:", 0, "jdbc:mariadb:".length())) {
            props.setProperty("useInformationSchema", "true");
        }
        applyConnectTimeout(props, url);
        return props;
    }

    /**
     * 解析 JDBC 连接串。
     *
     * <p>库名字段允许直接存放完整 JDBC URL，否则按 MySQL 的 主机/端口/库名 三段拼装。</p>
     *
     * <p>整串 URL 形态且是文件库（SQLite / H2 / Derby / HSQLDB）时，先过
     * {@link #guardFileJdbcPath(String)}：连接串里带的是磁盘路径，
     * 不校验就等于台账能指挥应用读写任意文件。探活、建连、事务三条路都从这里过。</p>
     *
     * @param row 数据源配置
     * @return 可直接交给 {@link DriverManager} 的连接串
     * @throws IllegalStateException 文件库路径越界
     */
    public String resolveJdbcUrl(MsDatasource row) {
        String type = trim(row.getDatasourceType()).toLowerCase(java.util.Locale.ROOT);
        String database = trim(row.getDatasourceDatabase());
        if (database.regionMatches(true, 0, DatasourceHubConstants.JDBC_URL_PREFIX, 0,
                DatasourceHubConstants.JDBC_URL_PREFIX.length())) {
            guardFileJdbcPath(database);
            return database;
        }
        // DuckDB：database 存的是文件路径（或 :memory:），没有主机与端口
        if (DatasourceHubConstants.DS_TYPE_DUCKDB.equals(type)) {
            if (database.isEmpty()) {
                throw new IllegalStateException("DuckDB 数据源缺少库文件路径");
            }
            String url = "jdbc:duckdb:" + database;
            guardFileJdbcPath(url);
            return url;
        }
        String host = trim(row.getDatasourceHost());
        if (host.isEmpty() || database.isEmpty()) {
            throw new IllegalStateException("JDBC 数据源缺少主机或库名");
        }
        // ClickHouse：走 HTTP 协议（clickhouse-jdbc），默认 8123
        if (DatasourceHubConstants.DS_TYPE_CLICKHOUSE.equals(type)) {
            int chPort = row.getDatasourcePort() != null ? row.getDatasourcePort()
                    : DatasourceHubConstants.DS_DEFAULT_PORTS.get(DatasourceHubConstants.DS_TYPE_CLICKHOUSE);
            return String.format("jdbc:clickhouse://%s:%d/%s", host, chPort, database);
        }
        int port = row.getDatasourcePort() != null ? row.getDatasourcePort()
                : DatasourceHubConstants.DS_DEFAULT_PORTS.get("jdbc");
        return String.format(
                "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                host, port, database);
    }

    /**
     * 移除并关闭数据源的缓存连接（配置变更/删除时调用）。
     *
     * @param datasourceId 数据源 ID
     */
    public void evict(Long datasourceId) {
        if (datasourceId == null) {
            return;
        }
        ExpireValue<Connection> holder = jdbcConnectionCache.remove(datasourceId);
        if (holder != null) {
            closeQuietly(holder.getValue());
        }
    }

    /**
     * 创建带空闲过期的连接持有者：过期即关闭真实连接并清除。
     *
     * @param connection 初始连接，允许空
     * @return ExpireValue 持有者
     */
    private ExpireValue<Connection> newConnectionHolder(Connection connection) {
        long minutes = Math.max(1L, properties.getConnectionTtlMinutes());
        return ExpireValue.of(connection, Duration.ofMinutes(minutes))
                .onExpireNotify(JdbcDatasourceSupport::closeQuietly);
    }

    /**
     * 获取（或重建）可复用的 JDBC 连接。
     *
     * <p>空闲 TTL（{@code plugin.datasource.hub.connection-ttl-minutes}）内复用同一物理连接；
     * 过期由 {@link ExpireValue} 过期回调关闭；连接被服务端断开时探测失效并自动重建。
     * 返回的连接屏蔽了 close，调用方沿用 try-with-resources 不会误关缓存连接。</p>
     *
     * @param datasourceId 数据源 ID
     * @param url          JDBC URL
     * @param props        连接属性
     * @param driverJar    驱动 jar，允许 null
     * @return 可用连接
     * @throws Exception 建连失败
     */
    private Connection cachedJdbcConnection(MsDatasource row, String url, Properties props,
                                            java.io.File driverJar) throws Exception {
        Long datasourceId = row.getId();
        ExpireValue<Connection> holder = jdbcConnectionCache.computeIfAbsent(
                datasourceId, id -> newConnectionHolder(null));
        synchronized (holder) {
            Connection reusable = holder.getValue();
            if (reusable != null && isUsable(reusable)) {
                holder.refresh();
                return closeProtected(reusable);
            }
            closeQuietly(reusable);
            // 有上传的驱动 jar 就走它自己的类加载器；否则用 DriverManager
            Connection fresh = driverJar == null
                    ? DriverManager.getConnection(url, props)
                    : JdbcDriverSupport.connect(driverJar, requireDriverClassName(row), url, props);
            jdbcConnectionCache.put(datasourceId, newConnectionHolder(fresh));
            log.debug("[datasource-hub] 建立 JDBC 连接: id={} url={} driverJar={}",
                    datasourceId, url, driverJar == null ? "内置" : driverJar.getName());
            return closeProtected(fresh);
        }
    }

    /**
     * 连接是否仍可用。
     *
     * @param connection 连接
     * @return 可用返回 true
     */
    private static boolean isUsable(Connection connection) {
        try {
            return !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 关闭连接（忽略异常）。
     *
     * @param connection 连接，允许空
     */
    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            log.debug("[datasource-hub] 关闭连接失败: {}", e.getMessage());
        }
    }

    /**
     * 包装连接：屏蔽 close（真实关闭交给缓存 TTL 过期），其余方法原样委托。
     *
     * @param connection 真实连接
     * @return 屏蔽 close 的连接代理
     */
    private static Connection closeProtected(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                JdbcDatasourceSupport.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                });
    }

    /**
     * 文件库 JDBC 路径守卫：越界直接拒绝建连。
     *
     * @param url 整串 JDBC URL
     * @throws IllegalStateException 路径不在白名单目录内
     */
    private void guardFileJdbcPath(String url) {
        String error = FileJdbcPathGuard.pathError(url, properties.getFileJdbcAllowedDirs());
        if (error != null) {
            throw new IllegalStateException(error);
        }
    }

    /**
     * 打开该数据源的 SSH 隧道（未配置或停用时返回 {@code null}）。
     *
     * <p>隧道打不开时**不静默降级成直连**：直连必然失败，而且报出来的错
     * （连不上目标主机）会把用户引到「网络/白名单」方向，真正原因是隧道没起来。</p>
     *
     * @param row 数据源台账
     * @return 隧道本地端点；未启用返回 null
     */
    private DatasourceTunnelService.TunnelEndpoint openTunnel(MsDatasource row) {
        try {
            return tunnelService.openIfEnabled(row.getId());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "数据源 " + row.getId() + " 配置了 SSH 隧道但隧道建立失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把 JDBC 连接串里的 authority（主机:端口）替换掉。
     *
     * <p>只处理带 {@code //} 的网络型 URL；{@code jdbc:sqlite:}/{@code jdbc:h2:}
     * 这类文件库没有 authority，原样返回 —— 隧道对文件库无意义，
     * 硬改会把路径改坏。</p>
     *
     * @param url  原连接串
     * @param host 新主机（隧道本地绑定地址）
     * @param port 新端口（隧道实际监听端口）
     * @return 替换后的连接串
     */
    private static String rewriteAuthority(String url, String host, int port) {
        if (url == null || host == null || host.isBlank()) {
            return url;
        }
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int authorityStart = scheme + 3;
        // authority 到 '/' '?' ';' 或串尾为止
        int authorityEnd = url.length();
        for (int i = authorityStart; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == ';') {
                authorityEnd = i;
                break;
            }
        }
        String authority = url.substring(authorityStart, authorityEnd);
        // authority 形如 host:port 或 host；保留可能存在的 userinfo（@ 之前）
        String userInfo = "";
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            userInfo = authority.substring(0, at + 1);
        }
        String replacement = userInfo + (port > 0 ? host + ":" + port : host);
        return url.substring(0, authorityStart) + replacement + url.substring(authorityEnd);
    }

    /**
     * 按驱动族把「建连超时（秒）」翻译成该驱动认识的属性与单位。
     *
     * <p><b>单位不一致是最容易踩的坑</b>：MySQL/MariaDB、Oracle 的
     * {@code connectTimeout} 收毫秒，PostgreSQL 的同名属性收**秒**，
     * SQL Server 根本不认 {@code connectTimeout}、只认 {@code loginTimeout}（秒）。</p>
     *
     * @param props 连接属性（原地写入）
     * @param url   JDBC 连接串，用于判断驱动族
     */
    private void applyConnectTimeout(Properties props, String url) {
        int seconds = Math.max(1, properties.getConnectTimeoutSeconds());
        String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        // 文件库：没有网络连接，跳过
        if (lower.startsWith("jdbc:sqlite:") || lower.startsWith("jdbc:h2:")
                || lower.startsWith("jdbc:derby:") || lower.startsWith("jdbc:hsqldb:")
                || lower.startsWith("jdbc:duckdb:")) {
            return;
        }
        if (lower.startsWith("jdbc:clickhouse:")) {
            // clickhouse-jdbc 0.11 的连接参数是白名单校验的，只设它认的那一个
            props.setProperty("connection_timeout", String.valueOf(seconds * 1000L));
        } else if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
            props.setProperty("connectTimeout", String.valueOf(seconds * 1000L));
            props.setProperty("socketTimeout", String.valueOf(seconds * 1000L));
        } else if (lower.startsWith("jdbc:oracle:")) {
            props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(seconds * 1000L));
            props.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(seconds * 1000L));
        } else if (lower.startsWith("jdbc:sqlserver:")) {
            props.setProperty("loginTimeout", String.valueOf(seconds));
        } else if (lower.startsWith("jdbc:postgresql:") || lower.startsWith("jdbc:db2:")) {
            // PostgreSQL / DB2 的 connectTimeout 是**秒**
            props.setProperty("connectTimeout", String.valueOf(seconds));
        } else {
            props.setProperty("loginTimeout", String.valueOf(seconds));
        }
    }

    /**
     * 在探测连接上查询目标库当前的客户端连接数（按方言尽力而为）。
     *
     * @param conn 探测连接
     * @param url  实际使用的 JDBC URL
     * @return 客户端连接数；方言不支持或查询失败返回 null
     */
    private Long queryJdbcConnectionCount(Connection conn, String url) {
        String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        String sql = switch (jdbcDialect(url)) {
            case "mysql" -> "SHOW STATUS LIKE 'Threads_connected'";
            case "postgresql" -> "SELECT COUNT(*) FROM pg_stat_activity";
            case "clickhouse" -> "SELECT sum(value) FROM system.metrics"
                    + " WHERE metric IN ('TCPConnection', 'HTTPConnection')";
            case "oracle" -> "SELECT COUNT(*) FROM v$session WHERE type = 'USER'";
            case "sqlserver" -> "SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE is_user_process = 1";
            // 文件型/嵌入式库没有服务器进程，也就没有「客户端连接数」
            default -> null;
        };
        if (sql == null) {
            return null;
        }
        try (var statement = conn.createStatement(); var rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                // MySQL 的 SHOW STATUS 是两列，PG 是一列计数：按「第二列优先、否则第一列」读
                String value = null;
                try {
                    value = rs.getString(2);
                } catch (SQLException ignored) {
                    // 单列结果集：落到第一列
                }
                if (value == null) {
                    value = rs.getString(1);
                }
                return value == null ? null : Long.parseLong(value.trim());
            }
            return null;
        } catch (Exception e) {
            log.debug("[datasource-hub] 连接数查询失败 url-prefix={} reason={}",
                    lower.substring(0, Math.min(16, lower.length())), e.getMessage());
            return null;
        }
    }

    /**
     * 在探测连接上查询目标服务器的一组运行指标（按方言尽力而为）。
     *
     * @param conn 探测连接
     * @param url  实际使用的 JDBC URL
     * @return 服务器运行指标；方言不支持或查询失败返回 null
     */
    private DatasourceProbeSupport.ServerStats queryJdbcServerStats(Connection conn, String url) {
        try {
            return switch (jdbcDialect(url)) {
                case "mysql" -> mySqlServerStats(conn);
                case "postgresql" -> postgreSqlServerStats(conn);
                case "clickhouse" -> clickHouseServerStats(conn);
                case "oracle" -> oracleServerStats(conn);
                case "sqlserver" -> sqlServerServerStats(conn);
                default -> null;
            };
        } catch (Exception e) {
            log.debug("[datasource-hub] 服务器状态查询失败 url-prefix={} reason={}",
                    url == null ? "" : url.substring(0, Math.min(16, url.length())), e.getMessage());
            return null;
        }
    }

    /**
     * 按 URL 前缀识别 JDBC 方言。
     *
     * @param url 实际使用的 JDBC URL
     * @return 方言键；未知返回 {@code unknown}
     */
    private static String jdbcDialect(String url) {
        String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
            return "mysql";
        }
        if (lower.startsWith("jdbc:postgresql:")) {
            return "postgresql";
        }
        if (lower.startsWith("jdbc:clickhouse:")) {
            return "clickhouse";
        }
        if (lower.startsWith("jdbc:oracle:")) {
            return "oracle";
        }
        if (lower.startsWith("jdbc:sqlserver:")) {
            return "sqlserver";
        }
        if (lower.startsWith("jdbc:sqlite:")) {
            return "sqlite";
        }
        if (lower.startsWith("jdbc:h2:")) {
            return "h2";
        }
        if (lower.startsWith("jdbc:duckdb:")) {
            return "duckdb";
        }
        return "unknown";
    }

    /**
     * 读一列的 long 值，SQL NULL 归一成 Java null。
     *
     * @param rs    结果集
     * @param index 列序号
     * @return 值或 null
     * @throws SQLException 读取失败
     */
    private static Long nullableLong(java.sql.ResultSet rs, int index) throws SQLException {
        long value = rs.getLong(index);
        return rs.wasNull() ? null : value;
    }

    /**
     * MySQL / MariaDB：{@code SHOW GLOBAL STATUS} 一次取齐五个累计/瞬时值。
     *
     * @param conn 探测连接
     * @return 服务器指标
     * @throws SQLException 查询失败
     */
    private DatasourceProbeSupport.ServerStats mySqlServerStats(Connection conn) throws SQLException {
        try (var statement = conn.createStatement();
             var rs = statement.executeQuery("SHOW GLOBAL STATUS WHERE Variable_name IN ("
                     + "'Slow_queries', 'Threads_running', 'Questions', 'Uptime', 'Max_used_connections')")) {
            Map<String, Long> values = new HashMap<>();
            while (rs.next()) {
                String name = rs.getString(1);
                String raw = rs.getString(2);
                if (name != null && raw != null) {
                    try {
                        values.put(name.trim().toLowerCase(java.util.Locale.ROOT),
                                Long.valueOf(raw.trim()));
                    } catch (NumberFormatException ignored) {
                        // 个别版本对某变量返回空串/非数字时跳过该行，其余指标照常
                    }
                }
            }
            return new DatasourceProbeSupport.ServerStats(
                    values.get("slow_queries"),
                    values.get("threads_running"),
                    values.get("questions"),
                    values.get("uptime"),
                    values.get("max_used_connections"),
                    instanceMemoryBytes(conn)
            );
        }
    }

    /**
     * MySQL 实例内存占用（字节）。
     *
     * @param conn 探测连接
     * @return 实例内存字节数；取不到为 null
     */
    private static Long instanceMemoryBytes(Connection conn) {
        try (var statement = conn.createStatement();
             var rs = statement.executeQuery(
                     "SELECT SUM(CURRENT_NUMBER_OF_BYTES_USED)"
                             + " FROM performance_schema.memory_summary_global_by_event_name"
                             + " WHERE EVENT_NAME LIKE 'memory/innodb/%'")) {
            if (!rs.next()) {
                return null;
            }
            long value = rs.getLong(1);
            return rs.wasNull() ? null : value;
        } catch (SQLException e) {
            // 无权限 / performance_schema 关闭：内存一栏留空，不影响其余五个指标
            return null;
        }
    }

    /**
     * PostgreSQL：活跃会话数 + 实例启动至今秒数。
     *
     * @param conn 探测连接
     * @return 服务器指标
     * @throws SQLException 查询失败
     */
    private DatasourceProbeSupport.ServerStats postgreSqlServerStats(Connection conn) throws SQLException {
        try (var statement = conn.createStatement();
             var rs = statement.executeQuery(
                     "SELECT (SELECT count(*) FROM pg_stat_activity"
                             + " WHERE state = 'active' AND pid <> pg_backend_pid()),"
                             + " (SELECT CAST(extract(epoch FROM now() - pg_postmaster_start_time()) AS bigint))")) {
            if (!rs.next()) {
                return null;
            }
            return new DatasourceProbeSupport.ServerStats(
                    null, nullableLong(rs, 1), null, nullableLong(rs, 2), null, null);
        }
    }

    /**
     * ClickHouse：正在执行的查询数、累计查询数、启动至今秒数。
     *
     * @param conn 探测连接
     * @return 服务器指标
     * @throws SQLException 查询失败
     */
    private DatasourceProbeSupport.ServerStats clickHouseServerStats(Connection conn) throws SQLException {
        try (var statement = conn.createStatement();
             var rs = statement.executeQuery(
                     "SELECT (SELECT value FROM system.metrics WHERE metric = 'Query'),"
                             + " (SELECT value FROM system.events WHERE event = 'Query'),"
                             + " uptime()")) {
            if (!rs.next()) {
                return null;
            }
            return new DatasourceProbeSupport.ServerStats(
                    null, nullableLong(rs, 1), nullableLong(rs, 2), nullableLong(rs, 3), null, null);
        }
    }

    /**
     * Oracle：活跃用户会话、累计执行次数、启动至今秒数、会话数历史峰值。
     *
     * @param conn 探测连接
     * @return 服务器指标
     * @throws SQLException 查询失败
     */
    private DatasourceProbeSupport.ServerStats oracleServerStats(Connection conn) throws SQLException {
        try (var statement = conn.createStatement();
             var rs = statement.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM v$session WHERE status = 'ACTIVE' AND type = 'USER'),"
                             + " (SELECT value FROM v$sysstat WHERE name = 'execute count'),"
                             + " (SELECT ROUND((SYSDATE - startup_time) * 86400) FROM v$instance),"
                             + " (SELECT MAX_UTILIZATION FROM v$resource_limit WHERE resource_name = 'sessions')"
                             + " FROM dual")) {
            if (!rs.next()) {
                return null;
            }
            return new DatasourceProbeSupport.ServerStats(
                    null, nullableLong(rs, 1), nullableLong(rs, 2), nullableLong(rs, 3), nullableLong(rs, 4), null);
        }
    }

    /**
     * SQL Server：正在执行的请求数、启动至今秒数。
     *
     * @param conn 探测连接
     * @return 服务器指标
     * @throws SQLException 查询失败
     */
    private DatasourceProbeSupport.ServerStats sqlServerServerStats(Connection conn) throws SQLException {
        try (var statement = conn.createStatement();
             var rs = statement.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM sys.dm_exec_requests WHERE session_id <> @@SPID),"
                             + " (SELECT DATEDIFF(SECOND, sqlserver_start_time, GETDATE()) FROM sys.dm_os_sys_info)")) {
            if (!rs.next()) {
                return null;
            }
            return new DatasourceProbeSupport.ServerStats(
                    null, nullableLong(rs, 1), null, nullableLong(rs, 2), null, null);
        }
    }

    /**
     * 在探测连接上查询数据库服务端版本（按方言尽力而为）。
     *
     * <p>查询失败不抛 —— 版本取不到只影响弹窗内「立即检测」自动补全的版本字段，
     * 不该把一次成功的连通性探测判成失败。</p>
     *
     * @param conn 探测连接
     * @param url  实际使用的 JDBC URL
     * @return 版本号文本；方言不支持或查询失败返回 null
     */
    private String queryJdbcServerVersion(Connection conn, String url) {
        String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        String sql;
        if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
            sql = "SELECT VERSION()";
        } else if (lower.startsWith("jdbc:postgresql:")) {
            sql = "SHOW server_version";
        } else {
            sql = "SELECT version()";
        }
        try (var statement = conn.createStatement(); var rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                String value = rs.getString(1);
                return value == null ? null : value.trim();
            }
            return null;
        } catch (Exception e) {
            log.debug("[datasource-hub] 服务端版本查询失败 url-prefix={} reason={}",
                    lower.substring(0, Math.min(16, lower.length())), e.getMessage());
            return null;
        }
    }

    /**
     * 去除首尾空白，null 安全。
     *
     * @param text 文本
     * @return 结果，null 时为空串
     */
    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }
}
