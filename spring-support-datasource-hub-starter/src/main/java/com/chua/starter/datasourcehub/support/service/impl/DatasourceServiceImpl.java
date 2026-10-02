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
import com.chua.starter.datasourcehub.support.service.DatasourceTunnelService;
import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;
import com.chua.starter.datasourcehub.support.support.DatasourceSecrets;
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
        Integer targetPort = endpoint == null ? ds.port() : endpoint.port();
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
        Properties props = buildJdbcProps(row, url);
        long start = System.nanoTime();
        try (Connection conn = DriverManager.getConnection(url, props)) {
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
        return props;
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
        return cachedJdbcConnection(row.getId(), url, props);
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
    private Connection cachedJdbcConnection(Long datasourceId, String url, Properties props) throws Exception {
        ExpireValue<Connection> holder = jdbcConnectionCache.computeIfAbsent(
                datasourceId, id -> newConnectionHolder(null));
        synchronized (holder) {
            Connection reusable = holder.getValue();
            if (reusable != null && isUsable(reusable)) {
                holder.refresh();
                return closeProtected(reusable);
            }
            closeQuietly(reusable);
            Connection fresh = DriverManager.getConnection(url, props);
            jdbcConnectionCache.put(datasourceId, newConnectionHolder(fresh));
            log.debug("[datasource-hub] 建立 JDBC 连接: id={} url={}", datasourceId, url);
            return closeProtected(fresh);
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
                row.getDatasourceStatus(), row.getDatasourceDriverClassName(),
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