package com.chua.starter.sync.data.support.sync.strategy;

/**
 * 同步模式枚举
 *
 * @author System
 * @since 2026/03/09
 */
public enum SyncMode {
    /**
     * 全量同步
     */
    FULL,

    /**
     * 增量同步
     */
    INCREMENTAL,

    /**
     * 双向同步
     */
    BIDIRECTIONAL
}
