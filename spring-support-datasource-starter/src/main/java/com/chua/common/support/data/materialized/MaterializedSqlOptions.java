package com.chua.common.support.data.materialized;

import lombok.Data;

/**
 * 物理化默认配置。
 *
 * @author CH
 * @since 2026/4/2
 */
@Data
public class MaterializedSqlOptions {

    /**
     * 默认阈值。
     */
    private long defaultThreshold = 1000L;

    /**
     * 副本刷新间隔，单位秒。
     */
    private long refreshIntervalSeconds = 300L;

    /**
     * 物理化数据源名称前缀。
     */
    private String cacheDataSourcePrefix = "materialized#";
}
