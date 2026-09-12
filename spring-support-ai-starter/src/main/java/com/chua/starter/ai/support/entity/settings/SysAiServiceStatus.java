package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聚合服务运行状态表，记录服务名称、运行标志、PID、启动时间与运行时长。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@TableName("sys_ai_service_status")
@Schema(name = "服务运行状态")
public class SysAiServiceStatus implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
      * 状态记录 标识
     */
    /**
      * 状态记录 标识
     */
    @TableId(value = "sys_ai_service_status_id", type = IdType.AUTO)
    @Schema(description = "状态记录 ID")
    /** 状态标识 */
    private Long statusId;

    /**
     * 服务名称
     */
    /**
     * 服务名称
     */
    @TableField("sys_ai_service_status_service_name")
    @Schema(description = "服务名称")
    /** 状态服务名称 */
    private String statusServiceName;

    /**
     * 是否运行中
     */
    /**
     * 是否运行中
     */
    @TableField("sys_ai_service_status_running")
    @Schema(description = "是否运行中")
    /** 状态running */
    private Boolean statusRunning;

    /**
     * 进程 PID
     */
    /**
     * 进程 PID
     */
    @TableField("sys_ai_service_status_pid")
    @Schema(description = "进程 PID")
    /** 状态PID */
    private Long statusPid;

    /**
     * 启动时间
     */
    /**
     * 启动时间
     */
    @TableField("sys_ai_service_status_start_time")
    @Schema(description = "启动时间")
    /** 状态开始时间 */
    private LocalDateTime statusStartTime;

    /**
     * 运行时长（秒）
     */
    /**
     * 运行时长(秒)
     */
    @TableField("sys_ai_service_status_uptime_sec")
    @Schema(description = "运行时长(秒)")
    /** 状态uptimesec */
    private Long statusUptimeSec;

    /**
     * 更新时间
     */
    /**
     * 更新时间
     */
    @TableField(value = "sys_ai_service_status_update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    /** 状态更新时间 */
    private LocalDateTime statusUpdateTime;
}