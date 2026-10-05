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
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.datasourcehub.support.service.JdbcDriverJarService;
import com.chua.starter.datasourcehub.support.service.DatasourceTunnelService;
import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;
import com.chua.starter.datasourcehub.support.support.DatasourceSecrets;
import com.chua.starter.datasourcehub.support.support.JdbcDriverSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
        return ReturnResult.ok(true);
    }

    @Override
    public ReturnResult<DataSourceTestVO> test(Long id) {
        ReturnResult<DataSourceVO> found = get(id);
        if (!found.isOk() || found.getData() == null) {
            return ReturnResult.error(found.getMsg() == null ? "数据源不存在" : found.getMsg());
        }
        DataSourceVO ds = found.getData();
        LocalDateTime testedAt = LocalDateTime.now();

        // 目标库内网不可直连时，先按隧道配置开隧道，把目标改写成隧道本地端口
        DatasourceTunnelService.TunnelEndpoint endpoint = tunnelService.openIfEnabled(id);
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
        boolean tunneled = endpoint != null;
        if (tunneled) {
            log.info("数据源 {} 经 SSH 隧道中转: {}:{}", id, targetHost, targetPort);
        }

        DatasourceProbeSupport.ProbeOutcome outcome =
                probe(ds, targetHost, targetPort);
        persistProbeResult(id, outcome, testedAt);
        return ReturnResult.ok(new DataSourceTestVO(outcome.success(), outcome.message(), ds.type(),
                outcome.connectLatencyMs(), outcome.sessionLatencyMs(), tunneled, testedAt));
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
        if ("jdbc".equalsIgnoreCase(trim(ds.type()))) {
            return probeJdbc(ds, timeoutMs);
        }
        int port = targetPort != null ? targetPort
                : DatasourceHubConstants.DS_DEFAULT_PORTS.getOrDefault(trim(ds.type()), 0);
        if (isBlank(targetHost) || port <= 0) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null, "缺少主机地址，且该类型无默认端口");
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
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null, "数据源不存在");
        }
        String url = resolveJdbcUrl(row);
        // 探活也必须尊重上传的驱动 jar 与隧道：不走的话，用户点「测试连接」会拿到
        // ClassNotFoundException 或连不上，而同一个数据源真正使用时是通的 ——
        // 「测试失败但实际能用」比「测试失败」更难解释。
        java.io.File driverJar;
        try {
            driverJar = driverJarOf(row);
        } catch (java.io.IOException e) {
            // 定位不到 jar 文件时给可读原因，而不是让整个探活抛栈
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null,
                    "驱动 jar 定位失败: " + e.getMessage());
        }
        url = applyTunnel(row, url);
        Properties props = buildJdbcProps(row, url);
        long start = System.nanoTime();
        try (Connection conn = driverJar == null
                ? DriverManager.getConnection(url, props)
                : JdbcDriverSupport.connect(driverJar, requireDriverClassName(row.getId()), url, props)) {
            long connectMs = (System.nanoTime() - start) / 1_000_000L;
            if (!conn.isValid(Math.max(1, timeoutMs / 1000))) {
                return new DatasourceProbeSupport.ProbeOutcome(false, connectMs, null, "连接已建立但校验未通过");
            }
            long sessionStart = System.nanoTime();
            try (var statement = conn.createStatement()) {
                statement.execute("SELECT 1");
            }
            long sessionMs = (System.nanoTime() - sessionStart) / 1_000_000L;
            return new DatasourceProbeSupport.ProbeOutcome(true, connectMs, sessionMs, "连接测试成功");
        } catch (Exception e) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null, "连接测试失败: " + e.getMessage());
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
        props.setProperty("user", row.getDatasourceUsername() == null ? "" : row.getDatasourceUsername());
        // 台账里明文与 b64: 前缀两种存法共存，见 DatasourceSecrets
        props.setProperty("password", DatasourceSecrets.decode(row.getDatasourcePassword()));
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
        // 文件库：没有网络连接，跳过
        if (lower.startsWith("jdbc:sqlite:") || lower.startsWith("jdbc:h2:")
                || lower.startsWith("jdbc:derby:") || lower.startsWith("jdbc:hsqldb:")) {
            return;
        }
        if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
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
    private void persistProbeResult(Long id, DatasourceProbeSupport.ProbeOutcome outcome, LocalDateTime testedAt) {
        var update = new LambdaUpdateWrapper<MsDatasource>()
                .eq(MsDatasource::getId, id)
                .set(MsDatasource::getDatasourceLastTestTime, testedAt)
                .set(MsDatasource::getDatasourceLastTestOk, outcome.success() ? 1 : 0)
                .set(MsDatasource::getDatasourceConnectLatencyMs, outcome.success() ? outcome.connectLatencyMs() : null)
                .set(MsDatasource::getDatasourceSessionLatencyMs, outcome.success() ? outcome.sessionLatencyMs() : null);
        datasourceMapper.update(null, update);
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
        if (!"jdbc".equalsIgnoreCase(trim(row.getDatasourceType()))) {
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
        if (!"jdbc".equalsIgnoreCase(trim(row.getDatasourceType()))) {
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
     * <p>空闲 TTL（{@code datasource.hub.connection-ttl-minutes}）内复用同一物理连接；
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
     * @param row 数据源配置
     * @return 可直接交给 {@link DriverManager} 的连接串
     */
    private static String resolveJdbcUrl(MsDatasource row) {
        String database = trim(row.getDatasourceDatabase());
        if (database.regionMatches(true, 0, DatasourceHubConstants.JDBC_URL_PREFIX, 0,
                DatasourceHubConstants.JDBC_URL_PREFIX.length())) {
            return database;
        }
        String host = trim(row.getDatasourceHost());
        if (host.isEmpty() || database.isEmpty()) {
            throw new IllegalStateException("JDBC 数据源缺少主机或库名");
        }
        int port = row.getDatasourcePort() != null ? row.getDatasourcePort()
                : DatasourceHubConstants.DS_DEFAULT_PORTS.get("jdbc");
        return String.format(
                "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                host, port, database);
    }

    /**
     * 按类型做真实连通性探测：jdbc 建连，其余类型建立 TCP 连接。
     *
     * @param ds 数据源配置
     * @throws Exception 连接失败
     */
    private void checkReachable(DataSourceVO ds) throws Exception {
        String type = trim(ds.type());
        if ("jdbc".equalsIgnoreCase(type)) {
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
        if (data.port() != null && (data.port() < 1 || data.port() > 65535)) {
            return "端口非法，需为 1~65535";
        }
        boolean jdbcUrlForm = "jdbc".equalsIgnoreCase(trim(data.type()))
                && nullToEmpty(data.database()).trim().startsWith(DatasourceHubConstants.JDBC_URL_PREFIX);
        if (isBlank(data.host()) && !jdbcUrlForm) {
            return "主机地址不能为空";
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
        entity.setDatasourcePassword(data.password() == null ? "" : data.password());
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
        return new DataSourceVO(row.getId(), row.getDatasourceName(), row.getDatasourceType(),
                row.getDatasourceHost(), row.getDatasourcePort(), row.getDatasourceDatabase(),
                row.getDatasourceUsername(), row.getDatasourcePassword(), row.getDatasourceRemark(),
                row.getDatasourceStatus(), row.getDatasourceDriverClassName(), row.getDatasourceDriverJarId(),
                row.getCreateTime() == null ? null : row.getCreateTime().toString(),
                row.getUpdateTime() == null ? null : row.getUpdateTime().toString(),
                row.getDatasourceConnectLatencyMs(), row.getDatasourceSessionLatencyMs(),
                row.getDatasourceLastTestTime() == null ? null : row.getDatasourceLastTestTime().toString(),
                row.getDatasourceLastTestOk(), nullToEmpty(row.getDatasourceControlMode()),
                nullToEmpty(row.getDatasourceControlTarget()));
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