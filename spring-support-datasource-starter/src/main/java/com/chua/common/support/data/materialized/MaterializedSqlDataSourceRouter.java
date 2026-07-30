package com.chua.common.support.data.materialized;

import javax.sql.DataSource;

/**
 * 能返回路由目标数据源的物理化路由器。
 *
 * @author CH
 * @since 2026/4/2
 */
public interface MaterializedSqlDataSourceRouter extends MaterializedSqlRouter {

    /**
     * 解析路由后的目标数据源。
     *
     * @param route 路由结果
     * @return 目标数据源
     */
    DataSource resolveDataSource(MaterializedSqlRoute route);
}
