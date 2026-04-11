package com.chua.datasource.support.materialized;

import com.chua.common.support.data.materialized.MaterializedSqlDataSourceRouter;
import com.chua.common.support.data.materialized.MaterializedSqlOptions;
import com.chua.common.support.data.materialized.MaterializedSqlRequest;
import com.chua.common.support.data.materialized.MaterializedSqlRoute;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.Function;

/**
 * 物理化路由器降级实现。
 * 当前运行时依赖缺少完整 Calcite 物理化实现时统一旁路到源库，避免系统启动失败。
 *
 * @author CH
 * @since 2026/4/5
 */
public class CalciteMaterializedSqlRouter implements MaterializedSqlDataSourceRouter {

    private final DataSource defaultDataSource;
    private final Function<String, DataSource> dataSourceResolver;

    public CalciteMaterializedSqlRouter(MaterializedSqlOptions options,
                                        DataSource defaultDataSource,
                                        Function<String, DataSource> dataSourceResolver) {
        this.defaultDataSource = defaultDataSource;
        this.dataSourceResolver = dataSourceResolver;
    }

    @Override
    public MaterializedSqlRoute route(MaterializedSqlRequest request) {
        return MaterializedSqlRoute.bypass();
    }

    @Override
    public void onWriteSuccess(MaterializedSqlRequest request) {
    }

    @Override
    public DataSource resolveDataSource(MaterializedSqlRoute route) {
        if (route == null || !route.isRouted()) {
            return null;
        }
        if (dataSourceResolver == null) {
            return defaultDataSource;
        }
        DataSource target = dataSourceResolver.apply(route.getDataSource());
        return Objects.requireNonNullElse(target, defaultDataSource);
    }
}
