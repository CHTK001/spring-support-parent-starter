// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.spi.connector.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.spi.connector.DatasourceConnector;
import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 应用主库（系统数据源）连接实现。
 *
 * <p><b>口径</b>：主库不是一个台账条目，也不该由数据源中心自己拼连接串 ——
 * 它就是应用启动时由部署侧配置、容器已经建好的那个 {@link DataSource}。
 * 因此这里直接复用容器里的 bean，不读台账、不走 {@code DriverManager}：
 * 数据源中心与业务代码看到的必须是同一个池，否则「主库」会出现第二套连接参数
 * （超时、池大小、时区都可能不同），排查时谁也说不清用的是哪一份。</p>
 *
 * <p>用 {@link ObjectProvider} 而不是直接注入：数据源中心与 JDBC 自动配置之间存在
 * 装配顺序上的互相引用，延迟取可避免构造期循环依赖；也确实存在「只引入数据源中心、
 * 不配主库」的部署方式，那时这里必须能如实报告「未配置默认数据源」而不是启动失败。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Spi(value = "datasource-app", order = 1000)
public class AppDatasourceConnector implements DatasourceConnector {

    /**
     * 应用主库类型标识：前端左栏的只读伪条目用这个值
     */
    public static final String TYPE_APP = "app";

    /**
     * 应用默认主数据源提供者：容器里的主 {@link DataSource}
     *
     * <p>SPI 实现由 {@code ClassUtils} 无参构造创建，再由 {@code SpringServiceAutowire}
     * 做字段注入，所以这里必须是字段而不是构造器参数。</p>
     */
    @Autowired(required = false)
    private ObjectProvider<DataSource> defaultDataSourceProvider;

    @Override
    public String name() {
        return "app";
    }

    @Override
    public boolean supports(String type) {
        return type != null && TYPE_APP.equalsIgnoreCase(type.trim());
    }

    @Override
    public Connection connection(Long datasourceId, MsDatasource row) throws Exception {
        return requireDataSource().getConnection();
    }

    @Override
    public Connection newConnection(Long datasourceId, MsDatasource row) throws Exception {
        // 主库由容器连接池管理，建连与归还都是它的事：这里再开一条「不经池」的连接
        // 会绕开池的容量与健康检查，正是重复实现要避免的事，故与复用路径同源。
        return requireDataSource().getConnection();
    }

    /**
     * 取主数据源，未配置时给出可执行提示。
     *
     * @return 主数据源
     */
    private DataSource requireDataSource() {
        DataSource dataSource = defaultDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("未配置默认数据源");
        }
        return dataSource;
    }

    /**
     * 取主数据源（未装配或取用失败时返回 null）。
     *
     * @return 主数据源；不可用返回 null
     */
    private DataSource defaultDataSource() {
        ObjectProvider<DataSource> provider = defaultDataSourceProvider;
        if (provider == null) {
            return null;
        }
        try {
            return provider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("[datasource-hub] 取主数据源失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public DatasourceProbeSupport.ProbeOutcome probe(Long datasourceId, MsDatasource row, DataSourceVO view,
                                                     String host, Integer port, int timeoutMs) {
        long start = System.nanoTime();
        Connection conn;
        try {
            conn = requireDataSource().getConnection();
        } catch (Exception e) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null,
                    "主库连接失败: " + e.getMessage(), null);
        }
        // 主库连接来自容器连接池：用完必须真的 close 归还，不能套缓存代理
        try (Connection closeable = conn) {
            long connectMs = (System.nanoTime() - start) / 1_000_000L;
            if (!closeable.isValid(Math.max(1, timeoutMs / 1000))) {
                return new DatasourceProbeSupport.ProbeOutcome(false, connectMs, null,
                        "连接已建立但校验未通过", null);
            }
            long sessionStart = System.nanoTime();
            try (var statement = closeable.createStatement()) {
                statement.execute("SELECT 1");
            }
            long sessionMs = (System.nanoTime() - sessionStart) / 1_000_000L;
            return new DatasourceProbeSupport.ProbeOutcome(true, connectMs, sessionMs,
                    "连接测试成功", null, null, serverVersion(closeable));
        } catch (Exception e) {
            return new DatasourceProbeSupport.ProbeOutcome(false, null, null,
                    "主库探测失败: " + e.getMessage(), null);
        }
    }

    /**
     * 读主库服务端版本；查询失败不影响连通性结论。
     *
     * @param conn 主库连接
     * @return 版本串；取不到返回 null
     */
    private String serverVersion(Connection conn) {
        try (var statement = conn.createStatement(); var rs = statement.executeQuery("SELECT version()")) {
            if (rs.next()) {
                String value = rs.getString(1);
                return value == null ? null : value.trim();
            }
            return null;
        } catch (Exception e) {
            log.debug("[datasource-hub] 主库版本查询失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public DatasourceJdbcInfo jdbcInfo(Long datasourceId, MsDatasource row) {
        // 主库的连接参数属于部署侧配置（url / 账号 / 口令由 application.yml 与运行时给出），
        // 数据源中心只是复用它，不去反读别人的配置：反读要么读到占位符，要么读到一份
        // 与实际生效不一致的值，比不提供更糟。
        throw new IllegalStateException("本操作必须显式指定数据源，不能用应用默认主数据源");
    }
}
