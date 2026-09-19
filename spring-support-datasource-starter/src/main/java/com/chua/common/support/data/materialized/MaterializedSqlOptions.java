package com.chua.common.support.data.materialized;

import lombok.Data;

/**
 * 物理化 SQL 选项。
 *
 * @since 2026/4/2
 * @author CH
 */
@Data
public class MaterializedSqlOptions {

    /** 默认阈值 */
    private long defaultThreshold = 1000L;
    /** Refresh间隔秒 */
    private long refreshIntervalSeconds = 300L;
    /** 缓存数据来源前缀 */
    private String cacheDataSourcePrefix = "materialized#";
}
