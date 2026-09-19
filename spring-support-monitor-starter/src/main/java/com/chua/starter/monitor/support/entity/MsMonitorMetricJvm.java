package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 监控 JVM 明细
 *
 * @since 2026/8/4
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "监控 JVM 明细")
@TableName("ms_monitor_metric_jvm")
public class MsMonitorMetricJvm implements Serializable {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    /**
     * 标识
    */
    private Long id;

    /**
     * 监控主机 标识
     */
    @TableField("ms_monitor_metric_jvm_monitor_id")
    @Schema(description = "监控主机 id", example = "1")
    /**
     * 监控标识
    */
    private Long monitorId;

    /**
     * 堆已用(字节)
     */
    @TableField("ms_monitor_metric_jvm_heap_used_bytes")
    @Schema(description = "堆已用(字节)", example = "536870912")
    /**
     * Heapusedbytes
    */
    private Long heapUsedBytes;

    /**
     * 堆最大(字节)
     */
    @TableField("ms_monitor_metric_jvm_heap_max_bytes")
    @Schema(description = "堆最大(字节)", example = "1073741824")
    /**
     * Heap最大值bytes
    */
    private Long heapMaxBytes;

    /**
     * 堆使用率(0-100)
     */
    @TableField("ms_monitor_metric_jvm_heap_usage_percent")
    @Schema(description = "堆使用率", example = "50.0")
    /**
     * Heapusage百分比
    */
    private Double heapUsagePercent;

    /**
     * Young GC 次数
     */
    @TableField("ms_monitor_metric_jvm_young_gc_count")
    @Schema(description = "Young GC 次数", example = "1234")
    /**
     * younggc数量
    */
    private Long youngGcCount;

    /**
     * 完整 GC 次数
     */
    @TableField("ms_monitor_metric_jvm_full_gc_count")
    @Schema(description = "Full GC 次数", example = "5")
    /**
     * 完整gc数量
    */
    private Long fullGcCount;

    /**
     * 活跃线程
     */
    @TableField("ms_monitor_metric_jvm_threads")
    @Schema(description = "活跃线程", example = "120")
    /**
     * Threads
    */
    private Integer threads;

    /**
     * 守护线程
     */
    @TableField("ms_monitor_metric_jvm_daemon_threads")
    @Schema(description = "守护线程", example = "20")
    /**
     * Daemonthreads
    */
    private Integer daemonThreads;

    /**
     * 采集时间
     */
    @TableField("ms_monitor_metric_jvm_collect_time")
    @Schema(description = "采集时间", example = "2026-08-04T10:00:00")
    /**
     * Collect时间
    */
    private LocalDateTime collectTime;
}
