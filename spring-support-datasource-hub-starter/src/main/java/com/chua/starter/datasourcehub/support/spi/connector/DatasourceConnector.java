// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.spi.connector;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;

import java.sql.Connection;

/**
 * 数据源连接扩展点：把「一种数据源怎么建连、怎么探活」收敛成一个实现。
 *
 * <p><b>为什么要有这一层</b>：本模块原先在
 * {@code DatasourceServiceImpl#connection(Long)} 里用 {@code if} 硬编码了三类分支
 * —— 应用主库取 Spring 的 {@link javax.sql.DataSource}、Calcite 合并源走成员装配、
 * 其余 JDBC 走 {@code DriverManager} 自建连接。三类分支散落在
 * {@code connection} / {@code newConnection} / {@code probe} / {@code jdbcInfo}
 * 四处，每加一种来源就要改四遍，改漏一处就是「能连上但打不开表」这类半通状态。</p>
 *
 * <p>改为扩展点之后，服务层只做一次分发（{@link DatasourceConnectorRegistry}），
 * 新增一种数据源只需提交一个实现类并注册别名，不必再动调用方。四类实现：</p>
 * <ul>
 *   <li>{@code app} —— 应用主库，直接复用容器里的 {@code DataSource} bean；</li>
 *   <li>{@code jdbc} —— 台账里的 JDBC 族（jdbc / clickhouse / duckdb），自建连接并做空闲复用；</li>
 *   <li>{@code calcite} —— 合并数据源，按成员装配联邦 schema；</li>
 *   <li>{@code socket} —— 非 JDBC 的中间件（redis / kafka / prometheus …），只做 TCP 探活。</li>
 * </ul>
 *
 * <p><strong>实现约定</strong>：实现类由 SPI 以无参构造创建，再由
 * {@code SpringServiceAutowire} 补字段注入，因此依赖必须写成
 * {@code @Autowired} 字段，不能只依赖构造器注入。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi(DatasourceConnector.SPI_NAME)
public interface DatasourceConnector {

    /**
     * 扩展点名称。
     */
    String SPI_NAME = "datasource-connector";

    /**
     * 扩展别名，用于日志与排障。
     *
     * @return 别名，如 {@code app} / {@code jdbc} / {@code calcite} / {@code socket}
     */
    String name();

    /**
     * 是否接管该类型的数据源。
     *
     * @param type 归一后的小写类型；应用主库传入 {@code app}
     * @return 接管返回 true
     */
    boolean supports(String type);

    /**
     * 取可复用连接。
     *
     * <p>调用方沿用 try-with-resources 不会误关底层连接：可复用的实现必须返回
     * 屏蔽了 {@code close} 的代理，真实关闭交给各自的生命周期管理。</p>
     *
     * @param datasourceId 数据源 ID；应用主库为 null 或 {@code 0}
     * @param row          台账记录；应用主库为 null
     * @return 连接
     * @throws Exception 建连失败
     */
    Connection connection(Long datasourceId, MsDatasource row) throws Exception;

    /**
     * 取一条独立连接（用后即关，不进复用池）。
     *
     * @param datasourceId 数据源 ID；应用主库为 null 或 {@code 0}
     * @param row          台账记录；应用主库为 null
     * @return 新建连接
     * @throws Exception 建连失败
     */
    Connection newConnection(Long datasourceId, MsDatasource row) throws Exception;

    /**
     * 探活并测量两个延迟。
     *
     * @param datasourceId 数据源 ID；应用主库为 null 或 {@code 0}
     * @param row          台账记录；应用主库为 null
     * @param view         数据源视图，用于取类型与展示名；可为 null
     * @param host         实际目标主机（可能已被 SSH 隧道改写）
     * @param port         实际目标端口（可能已被 SSH 隧道改写），允许 null
     * @param timeoutMs    探测超时（毫秒）
     * @return 探活结果
     */
    DatasourceProbeSupport.ProbeOutcome probe(Long datasourceId, MsDatasource row, DataSourceVO view,
                                              String host, Integer port, int timeoutMs);

    /**
     * 取可直接交给 {@code DriverManager} 的 JDBC 三元组。
     *
     * <p>只有真正能拼出单一连接串的实现才有意义；应用主库与合并数据源没有单一
     * 连接串，应抛出 {@link UnsupportedOperationException} 并在信息里说明原因，
     * 而不是返回一个假 URL。</p>
     *
     * @param datasourceId 数据源 ID
     * @param row          台账记录
     * @return JDBC 三元组
     */
    DatasourceJdbcInfo jdbcInfo(Long datasourceId, MsDatasource row);

    /**
     * 清退该数据源的缓存连接（配置变更 / 删除时调用）。
     *
     * @param datasourceId 数据源 ID
     */
    default void evict(Long datasourceId) {
        // 无缓存连接的类型不需要做任何事
    }
}
