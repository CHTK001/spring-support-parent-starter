package com.chua.starter.oauth.client.support.appkey;

import com.chua.common.support.lang.datasource.dialect.Dialect;
import com.chua.common.support.lang.datasource.engine.EngineDataSource;
import com.chua.common.support.lang.datasource.engine.executor.SqlExecutor;
import com.chua.datasource.support.engine.JdbcEngine;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * AI AppKey 本地认证支持。
 *
 * <p>基于 {@link JdbcEngine} 直接查询动态配置的 AppKey 表（默认 {@code sys_ai_app_key}），
 * 无需编写 Mapper，支持通过配置指定表名与列名，适配不同命名规范的 AI 模块。</p>
 *
 * <p>当 {@code plugin.oauth.ai-app-key.enabled=true} 时生效，
 * 用于单机部署或外部 OAuth 服务不可达场景下的 AI AppKey 认证。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AiAppKeySupport {

    /**
     * 全局静态实例（由 AuthClientConfiguration 注册，供每请求新建的 WebRequest 读取）
     */
    private static volatile AiAppKeySupport INSTANCE;

    /**
     * 注册全局实例。
     *
     * @param support AI AppKey 认证支持
     */
    public static void register(AiAppKeySupport support) {
        INSTANCE = support;
    }

    /**
     * 获取全局实例。
     *
     * @return AI AppKey 认证支持，未注册返回 {@code null}
     */
    public static AiAppKeySupport get() {
        return INSTANCE;
    }

    /**
     * 认证配置
     */
    private final AuthClientProperties authProperties;

    /**
     * 应用数据源
     */
    private final DataSource dataSource;

    /**
     * 懒加载的 JDBC 引擎
     */
    private volatile JdbcEngine engine;

    /**
     * 构造 AI AppKey 认证支持。
     *
     * @param authProperties OAuth 客户端配置
     * @param dataSource     应用数据源（与 MyBatis 同源）
     */
    public AiAppKeySupport(AuthClientProperties authProperties, DataSource dataSource) {
        this.authProperties = authProperties;
        this.dataSource = dataSource;
    }

    /**
     * 是否已启用 AI AppKey 本地认证。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return authProperties.getAiAppKey() != null && authProperties.getAiAppKey().isEnabled();
    }

    /**
     * 校验 AppKey 是否有效。
     *
     * <p>查询条件：{@code <appKeyColumn> = ? AND <statusColumn> = 1}，
     * 若配置了过期时间列则追加 {@code (<expireColumn> IS NULL OR <expireColumn> &gt; now)}。</p>
     *
     * @param appKey AppKey 值
     * @return 匹配的 AppKey 行（Map，键为列名），不存在或无效返回 {@code null}
     */
    public Map<String, Object> verifyAppKey(String appKey) {
        if (appKey == null || appKey.isBlank()) {
            return null;
        }
        if (!isEnabled()) {
            return null;
        }
        try {
            SqlExecutor executor = getEngine().getExecutor();
            if (executor == null) {
                log.warn("[AiAppKey] JdbcEngine 无可用执行器，跳过 AppKey 认证");
                return null;
            }
            AuthClientProperties.AiAppKey cfg = authProperties.getAiAppKey();
            StringBuilder sql = new StringBuilder("SELECT * FROM ")
                    .append(cfg.getTableName())
                    .append(" WHERE ")
                    .append(cfg.getAppKeyColumn()).append(" = ?")
                    .append(" AND ").append(cfg.getStatusColumn()).append(" = 1");
            if (cfg.getExpireTimeColumn() != null && !cfg.getExpireTimeColumn().isBlank()) {
                sql.append(" AND (").append(cfg.getExpireTimeColumn()).append(" IS NULL OR ")
                        .append(cfg.getExpireTimeColumn()).append(" > CURRENT_TIMESTAMP)");
            }
            List<Map<String, Object>> rows = executor.query(sql.toString(), appKey);
            if (rows == null || rows.isEmpty()) {
                log.debug("[AiAppKey] AppKey 校验失败: 无匹配记录");
                return null;
            }
            return rows.get(0);
        } catch (Exception e) {
            log.warn("[AiAppKey] AppKey 校验异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 AppKey 行中提取用户名（默认取 AppKey 列值）。
     *
     * @param row  AppKey 行
     * @param appKey 原始 AppKey
     * @return 用户名
     */
    public String resolveUsername(Map<String, Object> row, String appKey) {
        AuthClientProperties.AiAppKey cfg = authProperties.getAiAppKey();
        String column = cfg.getUsernameColumn();
        if (column == null || column.isBlank()) {
            return appKey;
        }
        Object value = row.get(column);
        return value != null ? String.valueOf(value) : appKey;
    }

    /**
     * 获取（懒加载）JDBC 引擎。
     *
     * @return JDBC 引擎
     */
    private JdbcEngine getEngine() {
        JdbcEngine local = engine;
        if (local == null) {
            synchronized (this) {
                local = engine;
                if (local == null) {
                    local = createEngine();
                    engine = local;
                }
            }
        }
        return local;
    }

    /**
     * 创建包装应用数据源的 JDBC 引擎。
     *
     * @return JDBC 引擎
     */
    private JdbcEngine createEngine() {
        JdbcEngine jdbcEngine = new JdbcEngine() {
        };
        jdbcEngine.addDataSource("default", new EngineDataSource<DataSource>() {
            @Override
            public String name() {
                return "default";
            }

            @Override
            public DataSource getSource() {
                return dataSource;
            }

            @Override
            public EngineDataSource<DataSource> setSource(Object source) {
                return this;
            }

            @Override
            public Dialect getDialect() {
                return null;
            }

            @Override
            public EngineDataSource<DataSource> setDialect(Dialect dialect) {
                return this;
            }

            @Override
            public String url() {
                return null;
            }

            @Override
            public String username() {
                return null;
            }

            @Override
            public String password() {
                return null;
            }

            @Override
            public void close() {
                // 不关闭应用数据源（由 Spring 容器管理）
            }
        });
        jdbcEngine.setDefaultDataSourceName("default");
        return jdbcEngine;
    }
}
