package com.chua.starter.datasourcehub.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.value.ExpireValue;
import com.chua.starter.datasourcehub.support.config.DatasourceHubProperties;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceMapper;
import com.chua.starter.datasourcehub.support.model.DataSourceTestVO;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.model.dto.DataSourceDTO;
import com.chua.starter.datasourcehub.support.service.DatasourceMetricService;
import com.chua.starter.datasourcehub.support.service.DatasourceOperLogService;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.datasourcehub.support.service.DatasourceTunnelService;
import com.chua.starter.datasourcehub.support.service.JdbcDriverJarService;
import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;
import com.chua.starter.datasourcehub.support.support.DatasourceSecrets;
import com.chua.starter.datasourcehub.support.support.FileJdbcPathGuard;
import com.chua.starter.datasourcehub.support.support.JdbcDriverSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源中心服务实现：配置落 {@code ms_datasource}，连接空闲 TTL 复用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceServiceImpl implements DatasourceService {

    /**
     * 台账 {@code datasource_version} 列的宽度（VARCHAR(64)）。
     *
     * <p>该列存两类值：influxdb 的大版本 {@code 2}/{@code 3}，以及其余类型探测到的
     * 服务端版本串。版本串长度由驱动决定（不受我们控制），回写前按此宽度截断，
     * 避免一次「测试连接」因为记账超长而整体失败。</p>
     */
    private static final int VERSION_MAX_LENGTH = 64;

    /**
     * 数据源映射器
     */
    private final MsDatasourceMapper datasourceMapper;

    /**
     * 应用默认主数据源提供者
     */
    private final ObjectProvider<DataSource> defaultDataSourceProvider;

    /**
     * 数据源中心配置
     */
    private final DatasourceHubProperties properties;

    /**
     * SSH 隧道服务：目标库内网不可直连时，先经隧道中转再测
     */
    private final DatasourceTunnelService tunnelService;

    /**
     * 指标服务（可选）：删除数据源时清理其历史指标。
     *
     * <p><b>必须走 {@link ObjectProvider}</b>：{@code DatasourceMetricServiceImpl}
     * 探测时要回调本服务的 {@link #probeOnly(Long)}，直接注入会形成
     * 构造期循环依赖（Spring 只能靠三级缓存硬解，Boot 3+ 默认禁止）。
     * 可选装配还顺带保证：指标模块没引入时删除流程照常工作。</p>
     */
    private final ObjectProvider<DatasourceMetricService> metricServiceProvider;

    /**
     * 操作日志服务（可选）：删除数据源时清理其历史日志。
     */
    private final ObjectProvider<DatasourceOperLogService> operLogServiceProvider;

    /**
     * 驱动 jar 库（数据源引用上传的驱动时用它建连）
     */
    private final JdbcDriverJarService driverJarService;

    /**
     * JDBC 连接缓存：datasourceId -&gt; 带空闲过期的连接。
     *
     * <p>每次操作都经 {@link #connection(Long)} 取连接，直连会反复建连/断连；
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
     *
     * <p>与复用路径的互斥：{@link #cachedJdbcConnection} 在 {@code synchronized (holder)}
     * 里复用并 {@code refresh()}，清扫同样持锁后再触发回调 —— 要么清扫先关
     * （复用方随后拿到 null 走重建），要么复用方先刷新（清扫随后看到未过期跳过），
     * 不会出现「用到一半被关」。空壳 holder 不从 map 移除：下次取连接会复用它重建，
     * 避免 remove 与 {@code computeIfAbsent} 的竞争。</p>
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

    @Override
    public List<String> supportedTypes() {
        return DatasourceHubConstants.DS_TYPES;
    }

    @Override
    public ReturnResult<List<DataSourceVO>> list() {
        try {
            List<MsDatasource> rows = datasourceMapper.selectList(
                    new LambdaQueryWrapper<MsDatasource>().orderByAsc(MsDatasource::getId));
            List<DataSourceVO> result = new ArrayList<>(rows.size());
            for (MsDatasource row : rows) {
                result.add(toVO(row));
            }
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("[datasource-hub] 查询数据源列表失败", e);
            return ReturnResult.error("查询数据源列表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<DataSourceVO> get(Long id) {
        if (id == null) {
            return ReturnResult.error("数据源 ID 不能为空");
        }
        MsDatasource row = datasourceMapper.selectById(id);
        if (row == null) {
            return ReturnResult.error("数据源不存在");
        }
        return ReturnResult.ok(toVO(row));
    }

    @Override
    public ReturnResult<DataSourceVO> create(DataSourceDTO data) {
        String error = validate(data);
        if (error != null) {
            return ReturnResult.error(error);
        }
        try {
            if (existsName(data.name(), null)) {
                return ReturnResult.error("数据源名称已存在: " + data.name());
            }
            MsDatasource entity = new MsDatasource();
            copy(data, entity);
            entity.setDatasourcePassword(DatasourceSecrets.encode(data.password()));
            entity.setCreateTime(LocalDateTime.now());
            datasourceMapper.insert(entity);
            return ReturnResult.ok(toVO(datasourceMapper.selectById(entity.getId())));
        } catch (Exception e) {
            log.error("[datasource-hub] 新增数据源失败", e);
            return ReturnResult.error("新增数据源失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<DataSourceVO> update(Long id, DataSourceDTO data) {
        if (id == null) {
            return ReturnResult.error("数据源 ID 不能为空");
        }
        String error = validate(data);
        if (error != null) {
            return ReturnResult.error(error);
        }
        MsDatasource exists = datasourceMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("数据源不存在");
        }
        try {
            if (existsName(data.name(), id)) {
                return ReturnResult.error("数据源名称已存在: " + data.name());
            }
            MsDatasource entity = new MsDatasource();
            entity.setId(id);
            copy(data, entity);
            // 口令留空 = 不修改：接口不再回传口令明文（VO 只有 passwordSet 标记），
            // 编辑弹窗打开时口令框本来就是空的。若按空串写回，用户改个备注就会
            // 把口令清掉 —— 那是实打实的数据丢失。
            String password = data.password();
            entity.setDatasourcePassword(password == null || password.isBlank()
                    ? exists.getDatasourcePassword()
                    : DatasourceSecrets.encode(password));
            entity.setCreateTime(exists.getCreateTime());
            entity.setUpdateTime(LocalDateTime.now());
            datasourceMapper.updateById(entity);
            // 连接参数可能已变：清退旧连接，下次访问按新配置重建
            evictJdbcConnection(id);
            return ReturnResult.ok(toVO(datasourceMapper.selectById(id)));
        } catch (Exception e) {
            log.error("[datasource-hub] 更新数据源失败 id={}", id, e);
            return ReturnResult.error("更新数据源失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> delete(Long id) {
        if (id == null) {
            return ReturnResult.error("数据源 ID 不能为空");
        }
        if (datasourceMapper.selectById(id) == null) {
            return ReturnResult.error("数据源不存在");
        }
        datasourceMapper.deleteById(id);
        evictJdbcConnection(id);
        // 从属数据一起清。三张表都只以 datasource_id 关联、没有外键级联，
        // 不清就会留下一批指向「已不存在的数据源」的孤儿行 —— 而按 id 查指标/日志时
        // 仍会把它们查出来，界面上表现为「删了数据源，监控指标还在跳」。
        // 刻意不走事务：主删除已经成功，从属清理失败不该回滚用户的删除动作。
        purgeDependents(id);
        return ReturnResult.ok(true);
    }

    /**
     * 清理某数据源的从属数据：隧道配置、指标历史、操作日志。
     *
     * <p>隧道还要先关掉：只删配置不断开，已经建好的转发会继续挂在目标库上，
     * 变成一个没人认领、也关不掉的 SSH 进程。</p>
     *
     * @param id 数据源 ID
     */
    private void purgeDependents(Long id) {
        try {
            tunnelService.delete(id);
        } catch (RuntimeException e) {
            log.warn("[datasource-hub] 删除数据源时关闭隧道失败 id={} reason={}", id, e.getMessage());
        }
        DatasourceMetricService metricService = metricServiceProvider.getIfAvailable();
        if (metricService != null) {
            try {
                metricService.purge(id);
            } catch (RuntimeException e) {
                log.warn("[datasource-hub] 删除数据源时清理指标失败 id={} reason={}", id, e.getMessage());
            }
        }
        DatasourceOperLogService operLogService = operLogServiceProvider.getIfAvailable();
        if (operLogService != null) {
            try {
                operLogService.purge(id);
            } catch (RuntimeException e) {
                log.warn("[datasource-hub] 删除数据源时清理操作日志失败 id={} reason={}", id, e.getMessage());
            }
        }
    }

    @Override
    public ReturnResult<DataSourceTestVO> test(Long id) {
        return doTest(id, true);
    }

    @Override
    public ReturnResult<DataSourceTestVO> probeOnly(Long id) {
        return doTest(id, false);
    }

    /**
     * 探测连通性。
     *
     * @param id      数据源 ID
     * @param persist 是否把结果回写台账（{@code lastTestOk / lastTestTime / 延迟}）
     * @return 测试结果
     */
    private ReturnResult<DataSourceTestVO> doTest(Long id, boolean persist) {
        ReturnResult<DataSourceVO> found = get(id);
        if (!found.isOk() || found.getData() == null) {
            return ReturnResult.error(found.getMsg() == null ? "数据源不存在" : found.getMsg());
        }
        DataSourceVO ds = found.getData();
        LocalDateTime testedAt = LocalDateTime.now();

        // 目标库内网不可直连时，先按隧道配置开隧道，把目标改写成隧道本地端口。
        // 打不开就按失败返回，不静默降级成直连：配了隧道却直连，
        // 用户看到的是「测试通过」而实际请求的是另一条链路（多半根本连不上），
        // 与建连路径（openTunnel 打不开直接抛）的策略也就此对齐。
        DatasourceTunnelService.TunnelEndpoint endpoint;
        try {
            endpoint = tunnelService.openIfEnabled(id);
        } catch (RuntimeException e) {
            // 不在这里再拼一次「SSH 隧道打开失败: 」，异常 message 自带该前缀
            // （DatasourceTunnelServiceImpl#doOpen 拼过），再拼一次用户看到的是
            // 「SSH 隧道打开失败: SSH 隧道打开失败: ...」这种叠头。
            String reason = isBlank(e.getMessage()) ? "SSH 隧道打开失败" : e.getMessage();
            log.warn("[datasource-hub] 数据源 {} {}", id, reason);
            if (persist) {
                persistProbeResult(id,
                        ds.type(),
                        DatasourceProbeSupport.ProbeOutcome.failed(reason),
                        testedAt);
            }
            // tunneled 传 null 而不是 true：隧道没打开，这次探测压根没经过隧道，
            // 说成「经隧道」会让指标面板显示一条根本没走隧道的假指标。
            return ReturnResult.ok(new DataSourceTestVO(false, reason, ds.type(),
                    null, null, null, testedAt, null));
        }
        String targetHost = endpoint == null ? ds.host() : endpoint.host();
        // 【坑】这里必须保持装箱：三元表达式 `cond ? Integer : int` 会被推断成 int，
        // 于是 Integer 那一支被自动拆箱，端口为 null 时当场 NPE。
        // 文件库（H2 / SQLite）与整串 JDBC URL 形态的数据源都没有端口，
        // 原先写成 `endpoint == null ? ds.port() : endpoint.port()`，
        // 表现为每个文件库点「测试连接」都是 500（实测 2026-10-05）。
        Integer targetPort = endpoint == null ? ds.port() : Integer.valueOf(endpoint.port());
        // 文件库（H2 / SQLite）与「整串 JDBC URL」形态的数据源**没有端口**。
        // 原先这里把 ds.port() 直接交给下游按 int 取值，端口为 null 时抛 NPE，
        // 界面上表现为「测试连接」报系统异常 —— 而真因只是「这个库本来就没有端口」。
        // 实测 2026-10-05：每个 H2/SQLite 数据源点测试连接都是 500。
        if (targetPort == null) {
            log.debug("[datasource-hub] 数据源 {} 无端口（文件库或整串 JDBC URL），不做 TCP 目标推断", id);
        }
        Boolean tunneled = endpoint != null;
        if (tunneled) {
            log.info("数据源 {} 经 SSH 隧道中转: {}:{}", id, targetHost, targetPort);
        }

        DatasourceProbeSupport.ProbeOutcome outcome =
                probe(ds, targetHost, targetPort);
        if (persist) {
            persistProbeResult(id, ds.type(), outcome, testedAt);
        }
        return ReturnResult.ok(new DataSourceTestVO(outcome.success(), outcome.message(), ds.type(),
                outcome.connectLatencyMs(), outcome.sessionLatencyMs(), tunneled, testedAt,
                outcome.connectionCount(), outcome.serverStats(), outcome.serverVersion()));
    }

    /**
     * 读目标库的服务端版本（{@code 产品名 版本号}）。
     *
     * <p>用 {@link java.sql.DatabaseMetaData} 而不是按方言写 {@code SELECT VERSION()}：
     * 元数据接口对每个驱动都成立，而 SQL 版本函数各家写法不同
     * （MySQL/PG 是 {@code version()}、Oracle 要查 {@code v$instance}、
     * SQL Server 是 {@code @@VERSION}、H2/SQLite 又各是一套），
     * 为一个「显示一行字」的需求铺一套方言分支不划算。</p>
     *
     * <p>版本串里常有换行与驱动附带信息（MySQL 会给
     * {@code 8.0.36-0ubuntu0.22.04.1}，SQL Server 的 {@code @@VERSION} 更长），
     * 这里压成单行并去掉多余空白，避免把右侧面板的「版本」行撑成一片。</p>
     *
     * @param conn 探测连接
     * @return 版本串；取不到返回 null（页面显示「—」比显示半截字符串诚实）
     */
    private String jdbcServerVersion(Connection conn) {
        try {
            java.sql.DatabaseMetaData meta = conn.getMetaData();
            String product = trim(meta.getDatabaseProductName());
            String version = meta.getDatabaseProductVersion();
            version = version == null ? "" : version.replaceAll("\\s+", " ").trim();
            if (product.isEmpty() && version.isEmpty()) {
                return null;
            }
            if (version.isEmpty()) {
                return product;
            }
            return product.isEmpty() ? version : product + " " + version;
        } catch (Exception e) {
            // 版本只是展示项：驱动不实现元数据时不该影响连通性结论
            log.debug("[datasource-hub] 读取数据库版本失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 按类型执行探活并测量两个延迟。
     *
     * <p>jdbc 走完整握手 + {@code SELECT 1}；其余类型走 TCP/HTTP/RESP 探测。</p>
     *
     * @param ds         数据源视图
     * @param targetHost 实际目标主机（可能是隧道本地地址）
     * @param targetPort 实际目标端口（可能是隧道本地端口）
     * @return 探活结果
     */
    private DatasourceProbeSupport.ProbeOutcome probe(DataSourceVO ds, String targetHost, Integer targetPort) {
        int timeoutMs = properties.getProbeTimeoutMs();
        if (DatasourceHubConstants.isJdbcFamily(ds.type())) {
            return probeJdbc(ds, timeoutMs);
        }
        int port = targetPort != null ? targetPort
                : DatasourceHubConstants.DS_DEFAULT_PORTS.getOrDefault(trim(ds.type()), 0);
        if (isBlank(targetHost) || port <= 0) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null, "缺少主机地址，且该类型无默认端口", null);
        }
        return DatasourceProbeSupport.probeSocket(ds.type(), targetHost.trim(), port, timeoutMs);
    }

    /**
     * JDBC 探活：连接延迟 = 完整握手耗时，会话延迟 = {@code SELECT 1} 往返耗时。
     *
     * <p>刻意走 {@link DriverManager} 直连而不用 {@link #connection(Long)}：后者命中连接缓存时
     * 返回的是已有连接，计时会得到接近 0 的假值。探活连接用完即关，不进缓存、不污染复用池。</p>
     *
     * @param ds       数据源视图
     * @param timeoutMs 超时（毫秒）
     * @return 探活结果
     */
    private DatasourceProbeSupport.ProbeOutcome probeJdbc(DataSourceVO ds, int timeoutMs) {
        MsDatasource row = datasourceMapper.selectById(ds.id());
        if (row == null) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null, "数据源不存在", null);
        }
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
                : JdbcDriverSupport.connect(driverJar, requireDriverClassName(row.getId()), url, props)) {
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
            String serverVersion = jdbcServerVersion(conn);
            return new DatasourceProbeSupport.ProbeOutcome(true, connectMs, sessionMs,
                    "连接测试成功", connectionCount, serverStats, serverVersion);
        } catch (Exception e) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null,
                    "连接测试失败: " + e.getMessage(), null);
        }
    }

    /**
     * 在探测连接上查询目标库当前的客户端连接数（按方言尽力而为）。
     *
     * <p>覆盖 MySQL/MariaDB（{@code Threads_connected}）、PostgreSQL
     * （{@code pg_stat_activity}）、ClickHouse（{@code system.metrics.TCPConnection}）、
     * Oracle（{@code v$session}）、SQL Server（{@code sys.dm_exec_sessions}）——
     * 都是各自生态里「现在有多少客户端连着」的标准口径。</p>
     *
     * <p>文件型/嵌入式库（SQLite / DuckDB / H2）没有服务器进程，也就没有这个概念，
     * 返回 null。</p>
     *
     * <p>查询失败不抛：连接数缺一格只影响曲线，不该把一次成功的连通性探测判成失败。</p>
     *
     * @param conn 探测连接（用完即关的那条）
     * @param url  实际使用的 JDBC URL，用于判断方言
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
                // MySQL 的 SHOW STATUS 是两列（Variable_name/Value），PG 是一列计数：
                // 按「第二列优先、否则第一列」读可以同时兼容两种形态。
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
            // 方言前缀（如 jdbc:mysql）只用于日志定位，截前 16 个字符足够，不做复杂解析
            log.debug("[datasource-hub] 连接数查询失败 url-prefix={} reason={}",
                    lower.substring(0, Math.min(16, lower.length())), e.getMessage());
            return null;
        }
    }

    /**
     * 在探测连接上查询目标服务器的一组运行指标（按方言尽力而为）。
     *
     * <p>覆盖范围：MySQL/MariaDB（{@code SHOW GLOBAL STATUS}）、PostgreSQL
     * （{@code pg_stat_activity} / {@code pg_postmaster_start_time()}）、
     * ClickHouse（{@code system.metrics} / {@code system.events} / {@code uptime()}）、
     * Oracle（{@code v$session} / {@code v$sysstat} / {@code v$instance} /
     * {@code v$resource_limit}）、SQL Server（{@code sys.dm_exec_requests} /
     * {@code sys.dm_os_sys_info}）。</p>
     *
     * <p><strong>取不到就留 null，不填 0、不估算</strong>：慢查询数在 PostgreSQL /
     * ClickHouse / Oracle / SQL Server 上都没有「累计慢查询」的通用口径
     * （PG 要装 {@code pg_stat_statements} 扩展；ClickHouse 要开 {@code query_log}
     * 再扫日志，代价不适合每次探活）；PostgreSQL 与 SQL Server 也没有累计查询计数。
     * 前端对这些 null 显示「—」，而不是显示一个假 0。</p>
     *
     * <p><strong>关于 CPU</strong>：数据库协议本身不暴露宿主机 CPU 使用率，
     * 因此这里不硬造一个「CPU %」；活跃会话/查询数（{@code threadsRunning}）
     * 是 SQL 侧负载最接近的公开口径，前端会如实标注。</p>
     *
     * <p>文件型/嵌入式库（SQLite / DuckDB / H2）没有独立服务器进程，返回 null，
     * 由前端说明「无服务器侧指标」，而不是含糊地写「仅支持 MySQL」。</p>
     *
     * <p>查询失败不抛：服务器指标缺失只影响面板展示，不该把一次成功的连通性探测
     * 判成失败。</p>
     *
     * @param conn 探测连接（用完即关的那条）
     * @param url  实际使用的 JDBC URL，用于判断方言
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
     * <p>只认前缀而不是解析整条 URL：这里只需要区分族别，URL 里可能带一堆参数，
     * 解析反而是多余的失败点。</p>
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
     * 读一列的 long 值，SQL NULL 归一成 Java null（{@code getLong} 会把 NULL 读成 0，
     * 不判 {@code wasNull} 就会把「取不到」变成「确实是 0」）。
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
     * <p>取 {@code performance_schema.memory_summary_global_by_event_name} 中
     * InnoDB 相关事件的当前占用字节合计（缓冲池 / 数据字典等）。协议不暴露宿主机
     * 内存，这个口径是「MySQL 进程<b>内部</b>用了多少内存」最接近的公开数据；
     * performance_schema 关闭或当前账号无权限时返回 null（面板显示「暂未采集」）。</p>
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
     * <p>累计查询数没有无扩展口径（{@code pg_stat_statements} 默认不装），
     * 因此 QPS 一栏留空；用事务数冒充查询数会把面板上的「平均 QPS」变成
     * 一个语义不同的数字，那比留空更糟。</p>
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
     * <p>慢查询没有累计计数器（要么开 {@code query_log} 扫日志，要么用
     * {@code system.query_log} 聚合），探活阶段不做这种重查询，留 null。</p>
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
     * <p>这些 {@code v$} 视图需要相应权限；没有权限时整条查询失败，
     * 上层捕获后返回 null，面板显示「—」，不影响连通性结论。</p>
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
     * <p>累计查询数在 SQL Server 上只有性能计数器（且是每秒速率，不是累计值），
     * 拿来算「平均 QPS」会算错，故留 null。</p>
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
     * 构建 JDBC 连接属性：账号口令 + MySQL 系元数据开关。
     *
     * <p>与 {@link #connection(Long)} 保持同一套属性，避免探活与真实连接的元数据能力不一致。</p>
     *
     * @param row 数据源台账
     * @param url JDBC URL
     * @return 连接属性
     */
    private Properties buildJdbcProps(MsDatasource row, String url) {
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
     * 按驱动族把「建连超时（秒）」翻译成该驱动认识的属性与单位。
     *
     * <p><b>单位不一致是最容易踩的坑</b>：MySQL/MariaDB、Oracle 的
     * {@code connectTimeout} 收毫秒，PostgreSQL 的同名属性收**秒**，
     * SQL Server 根本不认 {@code connectTimeout}、只认 {@code loginTimeout}（秒）。
     * 统一按毫秒写会得到「30 秒」实际变成 30 毫秒（必超时），
     * 统一按秒写则变成 30 万毫秒（等于没设）。所以这里逐族处理，
     * 并且最后兜一个 {@code loginTimeout} 给不认得的驱动。</p>
     *
     * <p>文件库（{@code jdbc:sqlite:} / {@code jdbc:h2:}）不设任何超时 ——
     * 它们不建 TCP 连接，超时属性对它们无意义，写进去反而可能被驱动判为非法参数。</p>
     *
     * @param props   连接属性（原地写入）
     * @param url     JDBC 连接串，用于判断驱动族
     */
    private void applyConnectTimeout(Properties props, String url) {
        int seconds = Math.max(1, properties.getConnectTimeoutSeconds());
        String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        // 文件库：没有网络连接，跳过（DuckDB 与 SQLite/H2 同理 —— 写进去的属性
        // 会被驱动当成非法连接参数直接拒连）
        if (lower.startsWith("jdbc:sqlite:") || lower.startsWith("jdbc:h2:")
                || lower.startsWith("jdbc:derby:") || lower.startsWith("jdbc:hsqldb:")
                || lower.startsWith("jdbc:duckdb:")) {
            return;
        }
        if (lower.startsWith("jdbc:clickhouse:")) {
            // clickhouse-jdbc 0.11 的连接参数是**白名单**校验的：不认识的键直接抛
            // ClientMisconfigurationException: Unknown and unmapped config properties
            // 把整条连接判死（实测 connect_timeout 就被拒，正确的键是 connection_timeout）。
            // 所以这里只设它认的那一个，不顺手把 MySQL 那套参数搬过来。
            props.setProperty("connection_timeout", String.valueOf(seconds * 1000L));
        } else if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
            props.setProperty("connectTimeout", String.valueOf(seconds * 1000L));
            props.setProperty("socketTimeout", String.valueOf(seconds * 1000L));
        } else if (lower.startsWith("jdbc:oracle:")) {
            props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(seconds * 1000L));
            props.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(seconds * 1000L));
        } else if (lower.startsWith("jdbc:sqlserver:")) {
            // SQL Server 只认 loginTimeout（秒）
            props.setProperty("loginTimeout", String.valueOf(seconds));
        } else if (lower.startsWith("jdbc:postgresql:")) {
            // PostgreSQL 的 connectTimeout 是**秒**
            props.setProperty("connectTimeout", String.valueOf(seconds));
        } else if (lower.startsWith("jdbc:db2:")) {
            props.setProperty("connectTimeout", String.valueOf(seconds));
        } else {
            // 兜底：多数驱动（含 H2 的 TCP 模式）认 loginTimeout（秒）
            props.setProperty("loginTimeout", String.valueOf(seconds));
        }
    }

    /**
     * 回写探活结果：延迟、测试时间与是否连通，覆盖写、不留历史。
     *
     * <p>失败时把两个延迟清空：失败耗时不能当成功指标展示，否则卡片会一直挂着误导性的数字。</p>
     *
     * @param id       数据源 ID
     * @param outcome  探活结果
     * @param testedAt 测试时间
     */
    /**
     * 回写探活结果：延迟、测试时间、是否连通，以及**探测到的服务端版本**。
     *
     * <p>版本必须落台账，不能只留在前端会话里：原先版本是前端一个 ref，
     * 切换数据源不重置，于是「检测过 ClickHouse 之后，点任何别的数据源，
     * 版本行都显示 ClickHouse 24.8.14.39」—— 把别人的版本按到了当前这条上。
     * 落到 {@code datasource_version} 后，每条记录各存各的，刷新页面也不会丢。</p>
     *
     * <p><b>influxdb 例外</b>：它的 {@code datasource_version} 存的是用户选的
     * 服务端大版本（{@code 2}/{@code 3}），是「用 Flux 还是 InfluxQL」的路由依据，
     * 被探测结果覆盖会直接把查询语法打错，所以这一类型一律不写。</p>
     *
     * @param id       数据源 ID
     * @param type     数据源类型（决定版本字段的语义）
     * @param outcome  探活结果
     * @param testedAt 测试时间
     */
    private void persistProbeResult(Long id, String type,
                                    DatasourceProbeSupport.ProbeOutcome outcome, LocalDateTime testedAt) {
        var update = new LambdaUpdateWrapper<MsDatasource>()
                .eq(MsDatasource::getId, id)
                .set(MsDatasource::getDatasourceLastTestTime, testedAt)
                .set(MsDatasource::getDatasourceLastTestOk, outcome.success() ? 1 : 0)
                .set(MsDatasource::getDatasourceConnectLatencyMs, outcome.success() ? outcome.connectLatencyMs() : null)
                .set(MsDatasource::getDatasourceSessionLatencyMs, outcome.success() ? outcome.sessionLatencyMs() : null);
        // 只有探测成功且确实读到了版本才写：失败时留着上一次的值，
        // 比清成空更接近事实（连不上不代表版本变了）。
        if (outcome.success() && !isBlank(outcome.serverVersion())
                && !DatasourceHubConstants.DS_TYPE_INFLUXDB.equalsIgnoreCase(trim(type))) {
            update.set(MsDatasource::getDatasourceVersion, capVersion(outcome.serverVersion()));
        }
        // 台账回写失败不能把「连接成功」报成失败：这段是记账，不是探测本身。
        // 实测过一次真实故障 —— datasource_version 还是 VARCHAR(16) 时，
        // ClickHouse 的 "ClickHouse 24.8.14.39"（21 字符）触发
        // Data too long for column，异常一路抛到 advice，
        // 用户看到的是「数据校验失败」，而其实连接是通的、版本也读到了。
        try {
            datasourceMapper.update(null, update);
        } catch (RuntimeException e) {
            log.warn("[datasource-hub] 数据源 {} 探活结果回写台账失败（不影响本次探测结论）: {}",
                    id, e.getMessage());
        }
    }

    /**
     * 版本串按台账列宽截断（{@code datasource_version} 为 VARCHAR(64)）。
     *
     * <p>驱动给出的版本串长度不受我们控制（有的会把 build hash、OS 一起拼进来），
     * 与其让一次「测试连接」因为记账超长而整体失败，不如截断后照常展示。</p>
     *
     * @param version 原始版本串
     * @return 不超过列宽的版本串
     */
    private static String capVersion(String version) {
        String trimmed = version.trim();
        return trimmed.length() <= VERSION_MAX_LENGTH ? trimmed : trimmed.substring(0, VERSION_MAX_LENGTH);
    }

    @Override
    public Connection connection(Long datasourceId) throws Exception {
        if (datasourceId == null || datasourceId == DatasourceHubConstants.DEFAULT_DATASOURCE_ID) {
            DataSource defaultDataSource = defaultDataSourceProvider.getIfAvailable();
            if (defaultDataSource == null) {
                throw new IllegalStateException("未配置默认数据源");
            }
            return defaultDataSource.getConnection();
        }
        MsDatasource row = datasourceMapper.selectById(datasourceId);
        if (row == null) {
            throw new IllegalStateException("数据源不存在: " + datasourceId);
        }
        if (!DatasourceHubConstants.isJdbcFamily(row.getDatasourceType())) {
            throw new UnsupportedOperationException("非 JDBC 数据源类型: " + row.getDatasourceType());
        }
        String url = resolveJdbcUrl(row);
        // 隧道先行：配置了 SSH 隧道时把连接串的 authority 换成隧道本地端点。
        // 【实测 2026-10-05】这一步之前完全没接：tunnelService 字段注入了却从未使用，
        // 于是「配了隧道、内核仍在直连」，目标库只在内网可达时表现为
        // Communications link failure / The driver has not received any packets，
        // 看起来像网络故障，实际是隧道没接线。
        url = applyTunnel(row, url);
        // 复用同一份 props 构造（原先这里内联抄了一遍 buildJdbcProps 的内容，
        // 改一处漏一处：加隧道/超时的人只会改其中一份）
        Properties props = buildJdbcProps(row, url);
        return cachedJdbcConnection(row.getId(), url, props, driverJarOf(row));
    }

    /**
     * 取该数据源引用的驱动 jar 文件；没配就返回 null（走内置驱动）。
     *
     * @param row 数据源台账
     * @return jar 文件；未配置返回 null
     * @throws IOException 定位 jar 文件失败
     */
    private java.io.File driverJarOf(MsDatasource row) throws java.io.IOException {
        return driverJarService.jarFileOf(row.getDatasourceDriverJarId());
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
    private String applyTunnel(MsDatasource row, String url) {
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
     * 打开该数据源的 SSH 隧道（未配置或停用时返回 {@code null}）。
     *
     * <p>隧道打不开时**不静默降级成直连**：直连必然失败，而且报出来的错
     * （连不上目标主机）会把用户引到「网络/白名单」方向，真正原因是隧道没起来。
     * 这里直接把隧道的异常原样抛出去。</p>
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

    @Override
    public DatasourceJdbcInfo jdbcInfo(Long datasourceId) {
        if (datasourceId == null || datasourceId == DatasourceHubConstants.DEFAULT_DATASOURCE_ID) {
            throw new IllegalStateException("本操作必须显式指定数据源，不能用应用默认主数据源");
        }
        MsDatasource row = datasourceMapper.selectById(datasourceId);
        if (row == null) {
            throw new IllegalStateException("数据源不存在: " + datasourceId);
        }
        if (!DatasourceHubConstants.isJdbcFamily(row.getDatasourceType())) {
            throw new UnsupportedOperationException("非 JDBC 数据源类型: " + row.getDatasourceType());
        }
        return new DatasourceJdbcInfo(
                resolveJdbcUrl(row),
                row.getDatasourceUsername() == null ? "" : row.getDatasourceUsername(),
                // 台账里明文与 b64: 前缀两种存法共存，见 DatasourceSecrets
                DatasourceSecrets.decode(row.getDatasourcePassword()),
                row.getDatasourceDriverClassName());
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
     * @return 可用连接
     * @throws Exception 建连失败
     */
    private Connection cachedJdbcConnection(Long datasourceId, String url, Properties props,
                                           java.io.File driverJar) throws Exception {
        ExpireValue<Connection> holder = jdbcConnectionCache.computeIfAbsent(
                datasourceId, id -> newConnectionHolder(null));
        synchronized (holder) {
            Connection reusable = holder.getValue();
            if (reusable != null && isUsable(reusable)) {
                holder.refresh();
                return closeProtected(reusable);
            }
            closeQuietly(reusable);
            // 有上传的驱动 jar 就走它自己的类加载器；否则用 DriverManager（应用 classpath 上的内置驱动）
            Connection fresh = driverJar == null
                    ? DriverManager.getConnection(url, props)
                    : JdbcDriverSupport.connect(driverJar, requireDriverClassName(datasourceId), url, props);
            jdbcConnectionCache.put(datasourceId, newConnectionHolder(fresh));
            log.debug("[datasource-hub] 建立 JDBC 连接: id={} url={} driverJar={}",
                    datasourceId, url, driverJar == null ? "内置" : driverJar.getName());
            return closeProtected(fresh);
        }
    }

    /**
     * 取数据源上登记的驱动类名；没登记就用 jar 台账里的。
     *
     * <p>两者都没有时给出可执行的提示：光说「需要驱动类名」用户不知道去哪填，
     * 而这正是「上传了 jar 却没填驱动类名」这个最常见的情形。</p>
     *
     * @param datasourceId 数据源 ID
     * @return 驱动类全名
     */
    private String requireDriverClassName(Long datasourceId) {
        MsDatasource row = datasourceMapper.selectById(datasourceId);
        String fromRow = row == null ? null : row.getDatasourceDriverClassName();
        String fromJar = driverJarService.driverClassNameOf(
                row == null ? null : row.getDatasourceDriverJarId(), null);
        String name = fromJar != null ? fromJar : (fromRow == null ? null : fromRow.trim());
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException("数据源 " + datasourceId
                    + " 使用上传的驱动 jar，但既没登记驱动类名、jar 台账里也没有；"
                    + "请在数据源上填写驱动类名（如 org.h2.Driver）或重新上传时填写");
        }
        return name;
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
                .onExpireNotify(DatasourceServiceImpl::closeQuietly);
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
                DatasourceServiceImpl.class.getClassLoader(),
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
     * 移除并关闭数据源的缓存连接（配置变更/删除时调用）。
     *
     * @param datasourceId 数据源 ID
     */
    private void evictJdbcConnection(Long datasourceId) {
        ExpireValue<Connection> holder = jdbcConnectionCache.remove(datasourceId);
        if (holder != null) {
            closeQuietly(holder.getValue());
        }
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
    private String resolveJdbcUrl(MsDatasource row) {
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
     * 文件库 JDBC 路径守卫：越界直接拒绝建连。
     *
     * <p>逻辑在 {@link FileJdbcPathGuard}：穿越段 + 白名单两道闸，
     * 保存时 {@link #validate(DataSourceDTO)} 已拦一道，这里是兜底 ——
     * 台账若被直改库绕过保存校验，建连前仍会拦下。</p>
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
     * 保存前的文件库路径校验。
     *
     * @param url 整串 JDBC URL
     * @return 错误文案；null 表示通过或与本地文件路径无关
     */
    private String fileJdbcPathError(String url) {
        return FileJdbcPathGuard.pathError(url, properties.getFileJdbcAllowedDirs());
    }

    /**
     * 按类型做真实连通性探测：jdbc 建连，其余类型建立 TCP 连接。
     *
     * @param ds 数据源配置
     * @throws Exception 连接失败
     */
    private void checkReachable(DataSourceVO ds) throws Exception {
        String type = trim(ds.type());
        if (DatasourceHubConstants.isJdbcFamily(type)) {
            try (Connection conn = connection(ds.id())) {
                if (!conn.isValid(Math.max(1, properties.getProbeTimeoutMs() / 1000))) {
                    throw new IllegalStateException("连接已建立但校验未通过");
                }
            }
            return;
        }
        int port = ds.port() != null ? ds.port() : DatasourceHubConstants.DS_DEFAULT_PORTS.getOrDefault(type, 0);
        if (isBlank(ds.host()) || port <= 0) {
            throw new IllegalArgumentException("缺少主机地址，且该类型无默认端口");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ds.host().trim(), port), properties.getProbeTimeoutMs());
        }
    }

    /**
     * 校验数据源配置。
     *
     * @param data 数据源配置
     * @return 错误提示，通过校验时返回 null
     */
    private String validate(DataSourceDTO data) {
        if (data == null) {
            return "数据源配置不能为空";
        }
        if (isBlank(data.name())) {
            return "数据源名称不能为空";
        }
        if (isBlank(data.type())) {
            return "数据源类型不能为空";
        }
        if (!DatasourceHubConstants.DS_TYPES.contains(trim(data.type()).toLowerCase())) {
            return "不支持的数据源类型: " + data.type();
        }
        // 端口范围校验对「无端口」的文件库（DuckDB）不适用：该类型前端表单
        // 用 port=0 占位（没有主机/端口等式样），按 1~65535 会把它误伤成「端口非法」。
        if (data.port() != null && (data.port() < 1 || data.port() > 65535)
                && !DatasourceHubConstants.isFileJdbcType(trim(data.type()))) {
            return "端口非法，需为 1~65535";
        }
        if ("influxdb".equalsIgnoreCase(trim(data.type())) && !isBlank(data.version())
                && !"2".equals(trim(data.version())) && !"3".equals(trim(data.version()))) {
            return "InfluxDB 版本仅支持 2 或 3";
        }
        boolean jdbcFamily = DatasourceHubConstants.isJdbcFamily(trim(data.type()));
        boolean jdbcUrlForm = jdbcFamily
                && nullToEmpty(data.database()).trim().startsWith(DatasourceHubConstants.JDBC_URL_PREFIX);
        // DuckDB 是单文件库：没有主机，库「名」就是磁盘路径
        boolean fileDbForm = DatasourceHubConstants.isFileJdbcType(trim(data.type()));
        if (fileDbForm && isBlank(data.database())) {
            return "DuckDB 数据源必须填写库文件路径（如 /data/warehouse.db 或 :memory:）";
        }
        if (isBlank(data.host()) && !jdbcUrlForm && !fileDbForm) {
            return "主机地址不能为空";
        }
        // 保存时就拦文件库越界路径：等到建连才发现的话，台账里已经躺着一条
        // 「存得下、连不上」的记录，用户只能看到连接失败，看不到真正的原因。
        if (jdbcUrlForm) {
            String pathError = fileJdbcPathError(nullToEmpty(data.database()).trim());
            if (pathError != null) {
                return pathError;
            }
        }
        if (fileDbForm && !jdbcUrlForm) {
            String pathError = fileJdbcPathError("jdbc:duckdb:" + nullToEmpty(data.database()).trim());
            if (pathError != null) {
                return pathError;
            }
        }
        return null;
    }

    /**
     * 判断数据源名称是否已被占用。
     *
     * @param name      数据源名称
     * @param excludeId 更新时排除的自身 ID
     * @return true=已存在
     */
    private boolean existsName(String name, Long excludeId) {
        LambdaQueryWrapper<MsDatasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsDatasource::getDatasourceName, trim(name));
        if (excludeId != null) {
            wrapper.ne(MsDatasource::getId, excludeId);
        }
        return datasourceMapper.selectCount(wrapper) > 0;
    }

    /**
     * 把请求体字段复制到实体，空值统一写空串。
     *
     * <p>口令<b>不在这里处理</b>：新建要加密、更新要区分「留空=不修改」，
     * 两条路径语义不同，由 {@code create}/{@code update} 各自写入。</p>
     *
     * @param data   请求体
     * @param entity 实体
     */
    private void copy(DataSourceDTO data, MsDatasource entity) {
        entity.setDatasourceName(trim(data.name()));
        entity.setDatasourceType(trim(data.type()).toLowerCase());
        entity.setDatasourceHost(nullToEmpty(data.host()).trim());
        entity.setDatasourcePort(data.port());
        entity.setDatasourceDatabase(nullToEmpty(data.database()).trim());
        entity.setDatasourceUsername(nullToEmpty(data.username()).trim());
        entity.setDatasourceRemark(nullToEmpty(data.remark()).trim());
        // 状态为空按启用处理，老调用方（只传 8 个字段）不会把数据源误停用
        entity.setDatasourceStatus(data.status() == null ? 1 : data.status());
        entity.setDatasourceDriverClassName(nullToEmpty(data.driverClassName()).trim());
        // 引用上传的驱动 jar；为空表示用应用 classpath 上的内置驱动
        entity.setDatasourceDriverJarId(data.driverJarId());
        // 控制方式只对 nginx 有意义，其余类型一律清空。
        //
        // 踩过的坑：原先只校验取值是否在白名单里、没看 type，
        // 于是把一个 nginx 数据源 PUT 成 type=jdbc 时，controlMode=systemd
        // 与 controlTarget 会被照单全收地留在库里。实测是验收脚本直调
        // PUT 接口时发现的——前端的 onTypeChange 虽然不清这两个字段，
        // 但接口能被绕过，数据不干净就是数据不干净，不能指望调用方守规矩。
        // 留在库里的代价：一条 jdbc 数据源带着「systemd 托管」这种无意义状态，
        // 将来排查时无从判断它是脏数据还是新语义。
        boolean nginx = DatasourceHubConstants.DS_TYPE_NGINX.equals(trim(data.type()).toLowerCase());
        String mode = nginx ? trim(data.controlMode()).toLowerCase() : "";
        // 取值不在白名单内则按空处理（自动探测），不报错也不静默生效
        entity.setDatasourceControlMode(DatasourceHubConstants.CONTROL_MODES.contains(mode) ? mode : "");
        // 单元名只有 systemd 方式用得上；local 方式存进去会造成「配置与实际不符」的迷惑
        entity.setDatasourceControlTarget(
                DatasourceHubConstants.CONTROL_SYSTEMD.equals(entity.getDatasourceControlMode())
                        ? nullToEmpty(data.controlTarget()).trim() : "");
        // 服务端版本字段的语义按类型分：
        //  - influxdb：用户选的大版本（2/3），是 Flux/InfluxQL 的路由依据，只认这两个值；
        //  - 其余类型：由「立即检测」探测回写（MySQL 8.4.13 / ClickHouse 24.8.14.39 …）。
        //    这里**不写**该字段（保持 null），让 updateById 跳过它 —— 原先写 "" 会在
        //    每次编辑保存时把探测到的版本抹掉，用户看到版本行突然变回「—」。
        boolean influxdb = DatasourceHubConstants.DS_TYPE_INFLUXDB.equalsIgnoreCase(trim(data.type()).toLowerCase());
        String version = trim(data.version());
        if (influxdb) {
            entity.setDatasourceVersion(("2".equals(version) || "3".equals(version)) ? version : "");
        }
    }

    /**
     * 实体转视图。
     *
     * @param row 实体
     * @return 数据源视图
     */
    private DataSourceVO toVO(MsDatasource row) {
        if (row == null) {
            return null;
        }
        String stored = row.getDatasourcePassword();
        return new DataSourceVO(row.getId(), row.getDatasourceName(), row.getDatasourceType(),
                row.getDatasourceHost(), row.getDatasourcePort(), row.getDatasourceDatabase(),
                row.getDatasourceUsername(), DatasourceSecrets.decode(stored),
                row.getDatasourceRemark(),
                row.getDatasourceStatus(), row.getDatasourceDriverClassName(), row.getDatasourceDriverJarId(),
                row.getCreateTime() == null ? null : row.getCreateTime().toString(),
                row.getUpdateTime() == null ? null : row.getUpdateTime().toString(),
                row.getDatasourceConnectLatencyMs(), row.getDatasourceSessionLatencyMs(),
                row.getDatasourceLastTestTime() == null ? null : row.getDatasourceLastTestTime().toString(),
                row.getDatasourceLastTestOk(), nullToEmpty(row.getDatasourceControlMode()),
                nullToEmpty(row.getDatasourceControlTarget()), nullToEmpty(row.getDatasourceVersion()),
                !nullToEmpty(stored).isEmpty(), fastLoadOf(row));
    }

    /**
     * 是否支持 CSV 快速导入（LOAD/COPY 直灌）：按 JDBC 方言自动计算。
     *
     * <p>当前仅 MySQL / PostgreSQL 有 {JdbcCsvLoader} SPI 实现（LOAD DATA INFILE /
     * COPY FROM STDIN），其余 JDBC 方言或非 JDBC 类型返回 null（前端据此显示
     * 「不支持快速导入」）。判断依据是台账 database 字段（JDBC 类型存完整 URL
     * 或库名，两者都能带出方言前缀）。</p>
     *
     * @param row 台账实体
     * @return true 支持 / false 不支持 / null 无法判断
     */
    private Boolean fastLoadOf(MsDatasource row) {
        if (!DatasourceHubConstants.isJdbcFamily(row.getDatasourceType())) {
            return null;
        }
        // 多条线索判断方言：database 可能是完整 URL（jdbc:mysql:...）也可能是裸库名（report）；
        // driverClassName / version（探测回写如「MySQL 8.4.13」）兜底。任一命中即认为支持 LOAD 直灌。
        String database = trim(row.getDatasourceDatabase()).toLowerCase(java.util.Locale.ROOT);
        if (database.startsWith("jdbc:mysql:") || database.startsWith("jdbc:mariadb:")
                || database.startsWith("jdbc:postgresql:")) {
            return Boolean.TRUE;
        }
        String driver = trim(row.getDatasourceDriverClassName()).toLowerCase(java.util.Locale.ROOT);
        if (driver.contains("mysql") || driver.contains("mariadb") || driver.contains("postgresql")) {
            return Boolean.TRUE;
        }
        String version = trim(row.getDatasourceVersion()).toLowerCase(java.util.Locale.ROOT);
        if (version.contains("mysql") || version.contains("postgresql")) {
            return Boolean.TRUE;
        }
        // 仍是 jdbc 但无法判断方言（老数据没探测过版本）：不武断判 false，返回 null 由前端按「不确定」展示
        return null;
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

    /**
     * null 转空串。
     *
     * @param text 文本
     * @return 结果
     */
    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    /**
     * 判断文本是否为空白。
     *
     * @param text 文本
     * @return true=空白
     */
    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}