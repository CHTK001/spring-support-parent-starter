package com.chua.starter.sync.data.support.sync.strategy;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步结果
 *
 * @author System
 * @since 2026/03/09
 */
@Data
@Builder
public class SyncResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 总记录数
     */
    private long totalRecords;

    /**
     * 成功记录数
     */
    private long successRecords;

    /**
     * 失败记录数
     */
    private long failedRecords;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 耗时(毫秒)
     */
    private long duration;

    /**
     * 平均吞吐量(条/秒)
     */
    private double avgThroughput;

    /**
     * 峰值内存(MB)
     */
    private int peakMemoryMb;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 增量字段最大值（用于下次增量同步）
     */
    private Object incrementalMaxValue;
}
