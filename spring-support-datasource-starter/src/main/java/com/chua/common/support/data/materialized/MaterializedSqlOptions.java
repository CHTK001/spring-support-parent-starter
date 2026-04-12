package com.chua.common.support.data.materialized;

import lombok.Data;

/**
 * 物理化 SQL 选项。
 *
 * @author CH
 * @since 2026/4/2
 */
@Data
public class MaterializedSqlOptions {

    private long defaultThreshold = 1000L;
    private long refreshIntervalSeconds = 300L;
    private String cacheDataSourcePrefix = "materialized#";
}
