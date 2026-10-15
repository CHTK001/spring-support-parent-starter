package com.chua.starter.monitor.support.entity;

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
 * 维护计划实体（{@code ms_maintenance_plan}）。
 *
 * <p>维护窗口供值班人员规划升级、备份、重启等窗口期任务；本表只保存计划台账，
 * 不负责调度执行，状态由运维流程或后续编排任务推进。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "维护计划")
@TableName("ms_maintenance_plan")
public class MsMaintenancePlan extends SysBase {

    /**
     * 串行版本 UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 计划名称
     */
    @TableField("ms_maintenance_plan_name")
    @Schema(description = "计划名称", example = "核心服务升级")
    private String planName;

    /**
     * 计划类型：MAINTENANCE / UPGRADE / BACKUP / REBOOT
     */
    @TableField("ms_maintenance_plan_type")
    @Schema(description = "计划类型 MAINTENANCE/UPGRADE/BACKUP/REBOOT", example = "MAINTENANCE")
    private String planType;

    /**
     * 开始时间
     */
    @TableField("ms_maintenance_plan_start_time")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @TableField("ms_maintenance_plan_end_time")
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /**
     * 目标主机 ID 的 JSON 数组，如 {@code [1,2]}
     */
    @TableField("ms_maintenance_plan_target_host_ids")
    @Schema(description = "目标主机 ID JSON 数组", example = "[1,2]")
    private String targetHostIds;

    /**
     * 计划状态：PENDING / RUNNING / COMPLETED / CANCELLED
     */
    @TableField("ms_maintenance_plan_status")
    @Schema(description = "计划状态 PENDING/RUNNING/COMPLETED/CANCELLED", example = "PENDING")
    private String status;

    /**
     * 计划说明
     */
    @TableField("ms_maintenance_plan_description")
    @Schema(description = "计划说明")
    private String description;
}