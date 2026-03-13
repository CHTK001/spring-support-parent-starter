package com.chua.starter.sync.data.support.sync.strategy;

import com.chua.starter.sync.data.support.adapter.ReadConfig;
import com.chua.starter.sync.data.support.adapter.WriteConfig;
import com.chua.starter.sync.data.support.sync.transformer.TransformConfig;
import lombok.Data;

/**
 * 同步上下文
 *
 * @author System
 * @since 2026/03/09
 */
@Data
public class SyncContext {

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 同步模式
     */
    private SyncMode syncMode;

    /**
     * 读取配置
     */
    private ReadConfig readConfig;

    /**
     * 写入配置
     */
    private WriteConfig writeConfig;

    /**
     * 转换配置
     */
    private TransformConfig transformConfig;

    /**
     * 批次大小
     */
    private int batchSize = 1000;

    /**
     * 增量同步字段
     */
    private String incrementalField;

    /**
     * 增量同步字段值（上次同步的最大值）
     */
    private Object incrementalValue;

    /**
     * 冲突策略
     */
    private ConflictStrategy conflictStrategy = ConflictStrategy.OVERWRITE;

    /**
     * 最大内存限制(MB)
     */
    private int maxMemoryMb = 512;

    /**
     * 线程池大小
     */
    private int threadPoolSize = 5;

    /**
     * 冲突策略枚举
     */
    public enum ConflictStrategy {
        OVERWRITE,
        SKIP,
        MERGE
    }
}
