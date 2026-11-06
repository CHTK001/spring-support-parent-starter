// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.spi.connector.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.spi.connector.DatasourceConnector;
import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;
import com.chua.starter.datasourcehub.support.support.DatasourceSecrets;
import com.chua.starter.datasourcehub.support.support.JdbcDatasourceSupport;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;

/**
 * 台账里的 JDBC 族数据源（jdbc / clickhouse / duckdb）连接实现。
 *
 * <p>连接串拼装、连接属性、SSH 隧道、空闲复用与方言指标都在
 * {@link JdbcDatasourceSupport} 里：这些机制只有一份，扩展点这一层只做「把台账
 * 记录交给它」的适配，不再复制任何建连细节。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi(value = "datasource-jdbc", order = 200)
public class JdbcDatasourceConnector implements DatasourceConnector {

    /**
     * JDBC 建连与探活机制
     */
    @Autowired
    private JdbcDatasourceSupport jdbcSupport;

    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    public boolean supports(String type) {
        return DatasourceHubConstants.isJdbcFamily(type) && !DatasourceHubConstants.isCalciteType(type);
    }

    @Override
    public Connection connection(Long datasourceId, MsDatasource row) throws Exception {
        requireRow(row, datasourceId);
        return jdbcSupport.connection(row);
    }

    @Override
    public Connection newConnection(Long datasourceId, MsDatasource row) throws Exception {
        requireRow(row, datasourceId);
        return jdbcSupport.newConnection(row);
    }

    @Override
    public DatasourceProbeSupport.ProbeOutcome probe(Long datasourceId, MsDatasource row, DataSourceVO view,
                                                     String host, Integer port, int timeoutMs) {
        if (row == null) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null, "数据源不存在", null);
        }
        return jdbcSupport.probeJdbc(row, timeoutMs);
    }

    @Override
    public DatasourceJdbcInfo jdbcInfo(Long datasourceId, MsDatasource row) {
        requireRow(row, datasourceId);
        return new DatasourceJdbcInfo(
                jdbcSupport.resolveJdbcUrl(row),
                row.getDatasourceUsername() == null ? "" : row.getDatasourceUsername(),
                // 台账里明文与 b64: 前缀两种存法共存，见 DatasourceSecrets
                DatasourceSecrets.decode(row.getDatasourcePassword()),
                row.getDatasourceDriverClassName());
    }

    @Override
    public void evict(Long datasourceId) {
        jdbcSupport.evict(datasourceId);
    }

    /**
     * 台账记录缺失时给出与原先一致的错误文案。
     *
     * @param row          台账记录
     * @param datasourceId 数据源 ID
     */
    private static void requireRow(MsDatasource row, Long datasourceId) {
        if (row == null) {
            throw new IllegalStateException("数据源不存在: " + datasourceId);
        }
    }
}
