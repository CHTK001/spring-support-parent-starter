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
 * @author CH
 * @since 2026/4/2
 */
public class CalciteMaterializedSqlRouter implements MaterializedSqlDataSourceRouter {

    private final MaterializedSqlOptions options;
    private final DataSource defaultDataSource;
    private final Function<String, DataSource> dataSourceResolver;

    public CalciteMaterializedSqlRouter(MaterializedSqlOptions options,
                                        DataSource defaultDataSource,
                                        Function<String, DataSource> dataSourceResolver) {
        this.options = options;
        this.defaultDataSource = defaultDataSource;
        this.dataSourceResolver = dataSourceResolver;
    }

    @Override
    public MaterializedSqlRoute route(MaterializedSqlRequest request) {
        if (request == null || request.getCommandType() != MaterializedSqlCommandType.SELECT) {
            return MaterializedSqlRoute.notRouted();
        }
        if (request.getDefinition() == null || request.getTables() == null || request.getTables().isEmpty()) {
            return MaterializedSqlRoute.notRouted();
        }

        String targetDataSource = buildCacheDataSourceName(request.getDefinition(), request.getTables().get(0));
        DataSource resolved = resolveByName(targetDataSource);
        if (resolved == null) {
            return MaterializedSqlRoute.notRouted();
        }
        return MaterializedSqlRoute.routed(targetDataSource);
    }

    @Override
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
    public void onWriteSuccess(MaterializedSqlRequest request) {
        // 当前仓库缺失完整物理化实现，先保持 no-op，确保路由链可编译和可安全降级。
    }

    private String buildCacheDataSourceName(MaterializedRouteDefinition definition, String tableName) {
        String prefix = options == null || options.getCacheDataSourcePrefix() == null
                ? "materialized#"
                : options.getCacheDataSourcePrefix();
        if (definition != null && definition.getDataSource() != null && !definition.getDataSource().isBlank()) {
            return prefix + definition.getDataSource() + "#" + tableName;
        }
        return prefix + tableName;
    }

    private DataSource resolveByName(String dataSourceName) {
        if (dataSourceName == null || dataSourceResolver == null) {
            return null;
        }
        return dataSourceResolver.apply(dataSourceName);
    }
}
