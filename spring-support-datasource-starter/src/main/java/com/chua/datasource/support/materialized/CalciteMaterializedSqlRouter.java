package com.chua.datasource.support.materialized;

import com.chua.common.support.data.materialized.MaterializedRouteDefinition;
import com.chua.common.support.data.materialized.MaterializedSqlCommandType;
import com.chua.common.support.data.materialized.MaterializedSqlDataSourceRouter;
import com.chua.common.support.data.materialized.MaterializedSqlOptions;
import com.chua.common.support.data.materialized.MaterializedSqlRequest;
import com.chua.common.support.data.materialized.MaterializedSqlRoute;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.Function;

/**
 * 基于 Calcite 物理化能力的最小路由实现。
 * 当前仓库仍以安全降级为第一原则，缺失完整依赖时自动旁路源库。
 *
 * @since 2026/4/2
 * @author CH
 */
public class CalciteMaterializedSqlRouter implements MaterializedSqlDataSourceRouter {

    /** 期权 */
    private final MaterializedSqlOptions options;
    /** 默认数据来源 */
    private final DataSource defaultDataSource;
    private final Function<String, DataSource> dataSourceResolver; // 数据源解析器

    /**
     * 创建 calcite物化sqlrouter 实例
     * @param options 期权
     * @param defaultDataSource 默认数据源
     * @param dataSourceResolver 数据源解析器
     */
    public CalciteMaterializedSqlRouter(MaterializedSqlOptions options,
                                        DataSource defaultDataSource,
                                        Function<String, DataSource> dataSourceResolver) {
        this.options = options;
        this.defaultDataSource = defaultDataSource;
        this.dataSourceResolver = dataSourceResolver;
    }

    @Override
    /** Route */
    public MaterializedSqlRoute route(MaterializedSqlRequest request) {
        if (request == null || request.getCommandType() != MaterializedSqlCommandType.SELECT) {
            return MaterializedSqlRoute.notRouted();
        }
        if (request.getDefinition() == null || request.getTables() == null || request.getTables().isEmpty()) {
            return MaterializedSqlRoute.notRouted();
        }

        String targetDataSource = buildCacheDataSourceName(request.getDefinition(), request.getTables().getFirst());
        DataSource resolved = resolveByName(targetDataSource);
        if (resolved == null) {
            return MaterializedSqlRoute.notRouted();
        }
        return MaterializedSqlRoute.routed(targetDataSource);
    }

    @Override
    /** 解析数据源 */
    public DataSource resolveDataSource(MaterializedSqlRoute route) {
        if (route == null || !route.isRouted()) {
            return defaultDataSource;
        }
        if (dataSourceResolver == null) {
            return defaultDataSource;
        }
        DataSource target = dataSourceResolver.apply(route.getDataSource());
        return Objects.requireNonNullElse(target, defaultDataSource);
    }

    @Override
    /** On写入成功 */
    public void onWriteSuccess(MaterializedSqlRequest request) {
        // 当前仓库缺失完整物理化实现，先保持 no-op，确保路由链可编译和可安全降级。
    }

    /**
     * 构建缓存数据源名称
     *
     * @param definition definition
     * @param tableName table名称
     * @return 构建缓存数据源名称的结果
     */
    private String buildCacheDataSourceName(MaterializedRouteDefinition definition, String tableName) {
        String prefix = options == null || options.getCacheDataSourcePrefix() == null
                ? "materialized#"
                : options.getCacheDataSourcePrefix();
        if (definition != null && definition.getDataSource() != null && !definition.getDataSource().isBlank()) {
            return prefix + definition.getDataSource() + "#" + tableName;
        }
        return prefix + tableName;
    }

    /**
     * 解析by名称
     *
     * @param dataSourceName 数据源名称
     * @return resolveby名称的结果
     */
    private DataSource resolveByName(String dataSourceName) {
        if (dataSourceName == null || dataSourceResolver == null) {
            return null;
        }
        return dataSourceResolver.apply(dataSourceName);
    }
}
