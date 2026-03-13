package com.chua.starter.sync.data.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 同步统计实体类
 *
 * @author System
 * @since 2026/03/09
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "同步统计")
@Data
@TableName(value = "monitor_sync_statistics")
public class MonitorSyncStatistics extends SysBase {

    @TableId(value = "stat_id", type = IdType.AUTO)
    @Schema(description = "统计ID")
    private Long statId;

    @TableField(value = "sync_task_id")
    @Schema(description = "任务ID")
    private Long syncTaskId;

    @TableField(value = "stat_date")
    @Schema(description = "统计日期")
    private LocalDate statDate;

    @TableField(value = "total_records")
    @Schema(description = "总记录数")
    private Long totalRecords;

    @TableField(value = "success_records")
    @Schema(description = "成功记录数")
    private Long successRecords;

    @TableField(value = "failed_records")
    @Schema(description = "失败记录数")
    private Long failedRecords;

    @TableField(value = "avg_throughput")
    @Schema(description = "平均吞吐量(条/秒)")
    private BigDecimal avgThroughput;

    @TableField(value = "avg_latency")
    @Schema(description = "平均延迟(毫秒)")
    private BigDecimal avgLatency;

    @TableField(value = "peak_memory_mb")
    @Schema(description = "峰值内存(MB)")
    private Integer peakMemoryMb;

    @TableField(value = "create_time")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
