package com.chua.common.support.data.materialized;

/**
 * SQL 路由结果。
 *
 * @author CH
 * @since 2026/4/2
 */
public final class MaterializedSqlRoute {

    private final boolean routed;
    private final String dataSource;
    private final String cacheKey;

    private MaterializedSqlRoute(boolean routed, String dataSource, String cacheKey) {
        this.routed = routed;
        this.dataSource = dataSource;
        this.cacheKey = cacheKey;
    }

    public boolean isRouted() {
        return routed;
    }

    public String getDataSource() {
        return dataSource;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public static MaterializedSqlRoute bypass() {
        return new MaterializedSqlRoute(false, null, null);
    }

    public static MaterializedSqlRoute route(String dataSource, String cacheKey) {
        return new MaterializedSqlRoute(true, dataSource, cacheKey);
    }
}
