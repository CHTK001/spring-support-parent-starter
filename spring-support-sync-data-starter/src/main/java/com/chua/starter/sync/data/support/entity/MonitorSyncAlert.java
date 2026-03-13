package com.chua.starter.sync.data.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 同步告警实体类
 *
 * @author System
 * @since 2026/03/09
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "同步告警")
@Data
@TableName(value = "monitor_sync_alert")
public class MonitorSyncAlert extends SysBase {

    @TableId(value = "alert_id", type = IdType.AUTO)
    @Schema(description = "告警ID")
    private Long alertId;

    @TableField(value = "sync_task_id")
    @Schema(description = "任务ID")
    private Long syncTaskId;

    @TableField(value = "alert_type")
    @Schema(description = "告警类型: ERROR/PERFORMANCE/MEMORY")
    private String alertType;

    @TableField(value = "alert_level")
    @Schema(description = "告警级别: INFO/WARNING/ERROR/CRITICAL")
    private String alertLevel;

    @TableField(value = "alert_message")
    @Schema(description = "告警消息")
    private String alertMessage;

    @TableField(value = "alert_time")
    @Schema(description = "告警时间")
    private LocalDateTime alertTime;

    @TableField(value = "is_resolved")
    @Schema(description = "是否已解决: 0否 1是")
    private Integer isResolved;

    @TableField(value = "resolved_time")
    @Schema(description = "解决时间")
    private LocalDateTime resolvedTime;
}
