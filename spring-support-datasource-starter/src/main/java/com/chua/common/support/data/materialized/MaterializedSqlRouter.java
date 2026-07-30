package com.chua.common.support.data.materialized;

/**
 * SQL 物理化路由器。
 *
 * @author CH
 * @since 2026/4/2
 */
public interface MaterializedSqlRouter {

    /**
     * 查询路由。
     *
     * @param request SQL 请求
     * @return 路由结果
     */
    MaterializedSqlRoute route(MaterializedSqlRequest request);

    /**
     * 源库写成功后的副本同步。
     *
     * @param request SQL 请求
     */
    void onWriteSuccess(MaterializedSqlRequest request);
}
