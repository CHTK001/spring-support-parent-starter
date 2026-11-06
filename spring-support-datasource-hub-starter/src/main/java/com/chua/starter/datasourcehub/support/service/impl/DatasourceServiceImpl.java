// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
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
import com.chua.starter.datasourcehub.support.spi.connector.DatasourceConnectorRegistry;
import com.chua.starter.datasourcehub.support.spi.connector.impl.AppDatasourceConnector;
import com.chua.starter.datasourcehub.support.support.CalciteMergeDataSourceFactory;
import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;
import com.chua.starter.datasourcehub.support.support.DatasourceSecrets;
import com.chua.starter.datasourcehub.support.support.FileJdbcPathGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
     * 连接扩展点路由器：按类型把建连 / 探活 / 连接串解析分发给具体实现。
     *
     * <p>应用主库、台账 JDBC、Calcite 合并源与非 JDBC 中间件各自一个实现，
     * 本类不再持有任何建连细节（原先这里是四处 {@code if} 分支）。</p>
     */
    private final DatasourceConnectorRegistry connectorRegistry;

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
     * Calcite 合并数据源装配器（懒取）。
     *
     * <p><b>必须走 {@link ObjectProvider}</b>：{@link CalciteMergeDataSourceFactory}
     * 内部持有本服务的 {@code ObjectProvider}（成员建连要回调 {@link #connection(Long)}），
     * 若两边都强注入会形成构造期循环依赖（Boot 3+ 默认禁止）。
     * 这里只声明提供者，运行到 calcite 分支时才解析。</p>
     */
    private final ObjectProvider<CalciteMergeDataSourceFactory> calciteMergeFactoryProvider;

    @Override
    public List<String> supportedTypes() {
        return DatasourceHubConstants.DS_TYPES;
    }

    @Override
    public ReturnResult<List<DataSourceVO>> list() {
        try {
            // 置顶在前、其余按 ID 升序：置顶是用户偏好，不随连通性变化，排序口径保持稳定
            List<MsDatasource> rows = datasourceMapper.selectList(
                    new LambdaQueryWrapper<MsDatasource>()
                            .orderByDesc(MsDatasource::getDatasourceTopFlag)
                            .orderByAsc(MsDatasource::getId));
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
        if (data != null && DatasourceHubConstants.isCalciteType(data.type())) {
            return ReturnResult.error("合并数据源由「合并」动作创建，不能直接在新增表单里写 calcite 类型");
        }
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
        MsDatasource exists = datasourceMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("数据源不存在");
        }
        if (DatasourceHubConstants.isCalciteType(exists.getDatasourceType())) {
            return ReturnResult.error("合并数据源不支持编辑，请删除后通过「合并」动作重建");
        }
        String error = validate(data);
        if (error != null) {
            return ReturnResult.error(error);
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
    public ReturnResult<DataSourceVO> merge(String name, List<Long> memberIds) {
        if (isBlank(name)) {
            return ReturnResult.error("合并数据源名称不能为空");
        }
        List<Long> ids = memberIds == null ? List.of()
                : memberIds.stream().filter(e -> e != null).distinct().toList();
        if (ids.isEmpty()) {
            return ReturnResult.error("合并数据源至少需要一个成员");
        }
        try {
            if (existsName(name, null)) {
                return ReturnResult.error("数据源名称已存在: " + name);
            }
            CalciteMergeDataSourceFactory factory = calciteMergeFactoryProvider.getIfAvailable();
            if (factory == null) {
                return ReturnResult.error("Calcite 合并数据源组件未装配");
            }
            MsDatasource entity = new MsDatasource();
            entity.setDatasourceName(trim(name));
            entity.setDatasourceType(DatasourceHubConstants.DS_TYPE_CALCITE);
            entity.setDatasourceDatabase(factory.membersPayload(ids));
            entity.setDatasourceStatus(1);
            entity.setDatasourceTopFlag(0);
            entity.setCreateTime(LocalDateTime.now());
            // 入库前复用装配器的成员校验：成员必须存在、非合并且属于 JDBC 族
            String error = factory.validateMembers(entity);
            if (error != null) {
                return ReturnResult.error(error);
            }
            datasourceMapper.insert(entity);
            return ReturnResult.ok(toVO(datasourceMapper.selectById(entity.getId())));
        } catch (Exception e) {
            log.error("[datasource-hub] 新建合并数据源失败", e);
            return ReturnResult.error("新建合并数据源失败: " + e.getMessage());
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

    @Override
    public ReturnResult<DataSourceVO> toggleTop(Long id, boolean top) {
        if (id == null) {
            return ReturnResult.error("数据源 ID 不能为空");
        }
        MsDatasource row = datasourceMapper.selectById(id);
        if (row == null) {
            return ReturnResult.error("数据源不存在");
        }
        if (DatasourceHubConstants.isCalciteType(row.getDatasourceType())) {
            return ReturnResult.error("合并数据源不支持置顶，置顶仅对常规数据源有意义");
        }
        MsDatasource update = new MsDatasource();
        update.setId(id);
        update.setDatasourceTopFlag(top ? 1 : 0);
        datasourceMapper.updateById(update);
        row.setDatasourceTopFlag(top ? 1 : 0);
        return ReturnResult.ok(toVO(row));
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
        // 应用主库不在台账里（左栏那条是只读伪条目），所以不能走 get(id)：
        // 主库要有自己的「测试连接」，但结果无从回写台账 —— 它没有台账行。
        if (id == null || id == DatasourceHubConstants.DEFAULT_DATASOURCE_ID) {
            return doTestApp();
        }
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
                outcome.connectionCount(), outcome.serverStats(), outcome.version()));
    }

    /**
     * 应用主库的连通性测试。
     *
     * <p>主库没有台账行，所以既不读台账、也不回写台账：探活结论只作为本次响应返回。
     * 这也是「左栏主库点测试连接必然失败」的修复点 —— 原先这里直接走
     * {@code get(0)} 查不到记录，返回的是「数据源不存在」。</p>
     *
     * @return 测试结果
     */
    private ReturnResult<DataSourceTestVO> doTestApp() {
        LocalDateTime testedAt = LocalDateTime.now();
        DatasourceProbeSupport.ProbeOutcome outcome;
        try {
            outcome = connectorRegistry.resolve((String) null)
                    .probe(null, null, null, null, null, properties.getProbeTimeoutMs());
        } catch (RuntimeException e) {
            outcome = DatasourceProbeSupport.ProbeOutcome.failed(e.getMessage());
        }
        return ReturnResult.ok(new DataSourceTestVO(outcome.success(), outcome.message(),
                AppDatasourceConnector.TYPE_APP, outcome.connectLatencyMs(), outcome.sessionLatencyMs(),
                null, testedAt, outcome.connectionCount(), outcome.serverStats(), outcome.version()));
    }

    /**
     * 按类型执行探活并测量两个延迟。
     *
     * <p>分发交给 {@link DatasourceConnectorRegistry}：JDBC 走完整握手 +
     * {@code SELECT 1}，Calcite 走成员装配，其余类型走 TCP/HTTP/RESP 探测。
     * 这里只负责把台账记录取出来交给实现，不再关心某类数据源怎么连。</p>
     *
     * @param ds         数据源视图
     * @param targetHost 实际目标主机（可能是隧道本地地址）
     * @param targetPort 实际目标端口（可能是隧道本地端口）
     * @return 探活结果
     */
    private DatasourceProbeSupport.ProbeOutcome probe(DataSourceVO ds, String targetHost, Integer targetPort) {
        MsDatasource row = ds.id() == null ? null : datasourceMapper.selectById(ds.id());
        return connectorRegistry.resolve(ds.id(), row)
                .probe(ds.id(), row, ds, targetHost, targetPort, properties.getProbeTimeoutMs());
    }

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
        if (outcome.success() && !isBlank(outcome.version())
                && !DatasourceHubConstants.DS_TYPE_INFLUXDB.equalsIgnoreCase(trim(type))) {
            update.set(MsDatasource::getDatasourceVersion, capVersion(outcome.version()));
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
        // 主库（null / 0）与台账数据源都由扩展点接管：主库取容器里的 DataSource，
        // 台账记录按类型分发给 jdbc / calcite 实现。这里不再有按类型的 if 分支。
        MsDatasource row = rowOf(datasourceId);
        return connectorRegistry.resolve(datasourceId, row).connection(datasourceId, row);
    }

    @Override
    public Connection newConnection(Long datasourceId) throws Exception {
        MsDatasource row = rowOf(datasourceId);
        return connectorRegistry.resolve(datasourceId, row).newConnection(datasourceId, row);
    }

    @Override
    public DatasourceJdbcInfo jdbcInfo(Long datasourceId) {
        MsDatasource row = rowOf(datasourceId);
        return connectorRegistry.resolve(datasourceId, row).jdbcInfo(datasourceId, row);
    }

    /**
     * 取台账记录，并把「主库」与「ID 不存在」两件事分清。
     *
     * <p>{@code null} 与 {@code 0} 是既有的「应用主库」约定，返回 {@code null}
     * 表示没有台账行可读；其余 ID 查不到记录必须<b>立即报错</b>。少了这道闸，
     * 路由会把「记录缺失」当成「没有类型」而落到主库实现上 ——
     * 一个写错的 ID 会静默连到应用主库，比直接失败危险得多。</p>
     *
     * @param datasourceId 数据源 ID
     * @return 台账记录；主库返回 null
     * @throws IllegalStateException ID 非主库但记录不存在
     */
    private MsDatasource rowOf(Long datasourceId) {
        if (datasourceId == null || datasourceId == DatasourceHubConstants.DEFAULT_DATASOURCE_ID) {
            return null;
        }
        MsDatasource row = datasourceMapper.selectById(datasourceId);
        if (row == null) {
            throw new IllegalStateException("数据源不存在: " + datasourceId);
        }
        return row;
    }

    /**
     * 移除并关闭数据源的缓存连接（配置变更/删除时调用）。
     *
     * @param datasourceId 数据源 ID
     */
    private void evictJdbcConnection(Long datasourceId) {
        connectorRegistry.evict(datasourceId);
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
     * 按类型做真实连通性探测：交给扩展点，与「测试连接」同一条路径。
     *
     * @param ds 数据源配置
     * @throws Exception 连接失败
     */
    private void checkReachable(DataSourceVO ds) throws Exception {
        MsDatasource row = ds.id() == null ? null : datasourceMapper.selectById(ds.id());
        DatasourceProbeSupport.ProbeOutcome outcome = connectorRegistry.resolve(ds.id(), row)
                .probe(ds.id(), row, ds, ds.host(), ds.port(), properties.getProbeTimeoutMs());
        if (!outcome.success()) {
            throw new IllegalStateException(outcome.message());
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
        entity.setDatasourceVersion(influxdb && ("2".equals(version) || "3".equals(version)) ? version : "");
        // 置顶标记：null 按不置顶处理；其余值归一为 0/1（避免库里灌进 7 这类哨兵值）
        entity.setDatasourceTopFlag(data.topFlag() == null ? 0 : (data.topFlag() == 1 ? 1 : 0));
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
                !nullToEmpty(stored).isEmpty(), fastLoadOf(row),
                row.getDatasourceTopFlag() == null ? 0 : row.getDatasourceTopFlag());
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