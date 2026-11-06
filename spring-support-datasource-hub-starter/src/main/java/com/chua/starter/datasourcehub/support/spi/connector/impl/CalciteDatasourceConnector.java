// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.spi.connector.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.spi.connector.DatasourceConnector;
import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;

/**
 * Calcite 合并数据源连接实现。
 *
 * <p>合并记录没有自身的主机/库名/凭证：建连是「按成员装配联邦 schema」，
 * 因此不能落到 {@code jdbc} 那一支（会因缺主机/库名被拒），必须单独接管。
 * 装配器本身用 {@code ObjectProvider} 懒取，理由见
 * {@code DatasourceServiceImpl} 里对循环依赖的说明。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi(value = "datasource-calcite", order = 300)
public class CalciteDatasourceConnector implements DatasourceConnector {

    /**
     * 合并数据源装配器（懒取，避免与数据源服务形成构造期循环依赖）
     */
    @Autowired
    private org.springframework.beans.factory.ObjectProvider<
            com.chua.starter.datasourcehub.support.support.CalciteMergeDataSourceFactory> factoryProvider;

    @Override
    public String name() {
        return "calcite";
    }

    @Override
    public boolean supports(String type) {
        return DatasourceHubConstants.isCalciteType(type);
    }

    @Override
    public Connection connection(Long datasourceId, MsDatasource row) throws Exception {
        return requireFactory().openConnection(requireRow(row, datasourceId));
    }

    @Override
    public Connection newConnection(Long datasourceId, MsDatasource row) throws Exception {
        // 联邦连接本身不来自连接池、也没有可复用的物理连接：每次装配一条独立连接。
        return requireFactory().openConnection(requireRow(row, datasourceId));
    }

    @Override
    public DatasourceProbeSupport.ProbeOutcome probe(Long datasourceId, MsDatasource row, DataSourceVO view,
                                                     String host, Integer port, int timeoutMs) {
        com.chua.starter.datasourcehub.support.support.CalciteMergeDataSourceFactory factory = factoryOrNull();
        if (factory == null) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null,
                    "Calcite 合并数据源组件未装配", null);
        }
        if (row == null) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null, "数据源不存在", null);
        }
        // 中间件建连（成员逐个取连接）的耗时计入 connect；SELECT 1 往返计入 session。
        // 连接数/服务端版本对联邦连接没有单一来源，统一留 null。
        long start = System.nanoTime();
        try (Connection conn = factory.openConnection(row)) {
            long connectMs = (System.nanoTime() - start) / 1_000_000L;
            if (!conn.isValid(Math.max(1, timeoutMs / 1000))) {
                return new DatasourceProbeSupport.ProbeOutcome(false, connectMs, null,
                        "连接已建立但校验未通过", null);
            }
            long sessionStart = System.nanoTime();
            try (var statement = conn.createStatement()) {
                statement.execute("SELECT 1");
            }
            long sessionMs = (System.nanoTime() - sessionStart) / 1_000_000L;
            return new DatasourceProbeSupport.ProbeOutcome(true, connectMs, sessionMs,
                    "连接测试成功", null, null, null);
        } catch (Exception e) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null,
                    "连接测试失败: " + e.getMessage(), null);
        }
    }

    @Override
    public DatasourceJdbcInfo jdbcInfo(Long datasourceId, MsDatasource row) {
        // 合并记录没有单一 JDBC 连接串（连接是成员装配出来的），拒绝提供假 URL
        throw new UnsupportedOperationException(
                "合并数据源没有单一 JDBC 连接串: " + (row == null ? datasourceId : row.getDatasourceName()));
    }

    /**
     * 取装配器，未装配时抛可读异常。
     *
     * @return 装配器
     */
    private com.chua.starter.datasourcehub.support.support.CalciteMergeDataSourceFactory requireFactory() {
        com.chua.starter.datasourcehub.support.support.CalciteMergeDataSourceFactory factory = factoryOrNull();
        if (factory == null) {
            throw new IllegalStateException(
                    "Calcite 合并数据源组件未装配：请确认已引入 calcite 支持模块");
        }
        return factory;
    }

    /**
     * 取装配器，未装配时返回 null。
     *
     * @return 装配器或 null
     */
    private com.chua.starter.datasourcehub.support.support.CalciteMergeDataSourceFactory factoryOrNull() {
        if (factoryProvider == null) {
            return null;
        }
        return factoryProvider.getIfAvailable();
    }

    /**
     * 台账记录缺失时给出与原先一致的错误文案。
     *
     * @param row          台账记录
     * @param datasourceId 数据源 ID
     * @return 台账记录
     */
    private static MsDatasource requireRow(MsDatasource row, Long datasourceId) {
        if (row == null) {
            throw new IllegalStateException("数据源不存在: " + datasourceId);
        }
        return row;
    }
}
