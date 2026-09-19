package com.chua.common.support.data.materialized;

import lombok.Builder;
import lombok.Data;

/**
 * SQL 路由结果。
 *
 * @since 2026/4/2
 * @author CH
 */
@Data
@Builder
public class MaterializedSqlRoute {

    /**
     * Routed
    */
    private boolean routed;
    /**
     * 数据来源
    */
    private String dataSource;
    /**
     * 缓存密钥
    */
    private String cacheKey;

    /**
     * notrouted
     *
     * @return notRouted的结果
     */
    public static MaterializedSqlRoute notRouted() {
        return MaterializedSqlRoute.builder().routed(false).build();
    }

    /**
     * Routed
     *
     * @param dataSource 数据源
     * @return routed的结果
     */
    public static MaterializedSqlRoute routed(String dataSource) {
        return MaterializedSqlRoute.builder()
                .routed(true)
                .dataSource(dataSource)
                .build();
    }

    /**
     * Routed
     *
     * @param dataSource 数据源
     * @param cacheKey 缓存Key
     * @return routed的结果
     */
    public static MaterializedSqlRoute routed(String dataSource, String cacheKey) {
        return MaterializedSqlRoute.builder()
                .routed(true)
                .dataSource(dataSource)
                .cacheKey(cacheKey)
                .build();
    }
}
