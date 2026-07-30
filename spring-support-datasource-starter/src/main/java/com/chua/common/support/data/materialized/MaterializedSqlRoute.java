package com.chua.common.support.data.materialized;

import lombok.Builder;
import lombok.Data;

/**
 * SQL 路由结果。
 *
 * @author CH
 * @since 2026/4/2
 */
@Data
@Builder
public class MaterializedSqlRoute {

    private boolean routed;
    private String dataSource;
    private String cacheKey;

    public static MaterializedSqlRoute notRouted() {
        return MaterializedSqlRoute.builder().routed(false).build();
    }

    public static MaterializedSqlRoute routed(String dataSource) {
        return MaterializedSqlRoute.builder()
                .routed(true)
                .dataSource(dataSource)
                .build();
    }

    public static MaterializedSqlRoute routed(String dataSource, String cacheKey) {
        return MaterializedSqlRoute.builder()
                .routed(true)
                .dataSource(dataSource)
                .cacheKey(cacheKey)
                .build();
    }
}
