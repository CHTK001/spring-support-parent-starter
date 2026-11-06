// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.spi.connector.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.spi.connector.DatasourceConnector;
import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;

import java.sql.Connection;

/**
 * 非 JDBC 类型（redis / kafka / prometheus / nginx …）的连接实现。
 *
 * <p>这些类型没有统一的 Java 连接对象：真正的读写由各自的
 * {@code MiddlewareClientProvider} SPI 承担，数据源中心只负责「地址能不能通」。
 * 所以这里的 {@code connection} 一律拒绝并说明该走哪个入口，探活则做 TCP 建链
 * （redis/nacos/influxdb/prometheus 再补一次协议往返）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi(value = "datasource-socket", order = 900)
public class SocketDatasourceConnector implements DatasourceConnector {

    @Override
    public String name() {
        return "socket";
    }

    @Override
    public boolean supports(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        String normalized = type.trim().toLowerCase(java.util.Locale.ROOT);
        // 应用主库有自己的实现（取容器里的 DataSource），不能落到「通用连通性探测」：
        // 它是精确类型，不是兜底类型。少了这一行，优先级列表里 order 更大的本实现
        // 会先认领 app，主库的连接就变成一条对 localhost 的裸 TCP 探测。
        if (AppDatasourceConnector.TYPE_APP.equals(normalized)) {
            return false;
        }
        // JDBC 族与合并源由各自实现接管；其余（含未知类型）走通用的连通性探测
        return !DatasourceHubConstants.isJdbcFamily(normalized);
    }

    @Override
    public Connection connection(Long datasourceId, MsDatasource row) throws Exception {
        throw new UnsupportedOperationException(
                "非 JDBC 数据源类型不提供 JDBC 连接: " + (row == null ? null : row.getDatasourceType())
                        + "（请使用对应的中间件能力）");
    }

    @Override
    public Connection newConnection(Long datasourceId, MsDatasource row) throws Exception {
        throw new UnsupportedOperationException(
                "非 JDBC 数据源类型不提供 JDBC 连接: " + (row == null ? null : row.getDatasourceType())
                        + "（请使用对应的中间件能力）");
    }

    @Override
    public DatasourceProbeSupport.ProbeOutcome probe(Long datasourceId, MsDatasource row, DataSourceVO view,
                                                     String host, Integer port, int timeoutMs) {
        String type = row != null ? row.getDatasourceType() : (view == null ? null : view.type());
        String targetHost = host != null ? host : (row != null ? row.getDatasourceHost() : null);
        Integer targetPort = port != null ? port
                : (row != null && row.getDatasourcePort() != null ? row.getDatasourcePort()
                : DatasourceHubConstants.DS_DEFAULT_PORTS.getOrDefault(
                        type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT), null));
        if (targetHost == null || targetHost.isBlank() || targetPort == null || targetPort <= 0) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null,
                    "缺少主机地址，且该类型无默认端口", null);
        }
        return DatasourceProbeSupport.probeSocket(type, targetHost.trim(), targetPort, timeoutMs);
    }

    @Override
    public DatasourceJdbcInfo jdbcInfo(Long datasourceId, MsDatasource row) {
        throw new UnsupportedOperationException(
                "非 JDBC 数据源类型没有 JDBC 连接串: " + (row == null ? null : row.getDatasourceType()));
    }
}
