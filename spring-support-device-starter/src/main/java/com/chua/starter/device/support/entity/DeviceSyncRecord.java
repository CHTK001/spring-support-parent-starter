package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步执行记录。
 *
 * <p>每次厂商目录拉取（设备/组织/人员）或本地写回（推送/删除远端）都落一行，
 * 先以 {@code running} 入库，结束时回写计数与结果；前端「同步记录」视图直接读本表。
 * 表无 SysBase 审计列，起止时间即审计口径，故不继承 {@code SysBase}。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("device_sync_record")
public class DeviceSyncRecord {

    /** 主键 */
    @TableId(value = "device_sync_record_id", type = IdType.ASSIGN_UUID)
    private String deviceSyncRecordId;

    /** 任务 ID：凭据同步 = 凭据 ID；按厂商同步 = 厂商标识；写回 = 设备 ID */
    @TableField("device_sync_record_task_id")
    private String deviceSyncRecordTaskId;

    /** 厂商标识 */
    @TableField("device_sync_record_vendor")
    private String deviceSyncRecordVendor;

    /** 方向：pull 拉取 / push 写回 / preview 预览 */
    @TableField("device_sync_record_mode")
    private String deviceSyncRecordMode;

    /** 阶段：devices / organizations / persons / device / device-delete */
    @TableField("device_sync_record_phase")
    private String deviceSyncRecordPhase;

    /** 总数 */
    @TableField("device_sync_record_total")
    private Integer deviceSyncRecordTotal;

    /** 新增数 */
    @TableField("device_sync_record_added")
    private Integer deviceSyncRecordAdded;

    /** 更新数 */
    @TableField("device_sync_record_updated")
    private Integer deviceSyncRecordUpdated;

    /** 删除数 */
    @TableField("device_sync_record_removed")
    private Integer deviceSyncRecordRemoved;

    /** 失败数 */
    @TableField("device_sync_record_failed")
    private Integer deviceSyncRecordFailed;

    /** 状态：running / success / partial / failed */
    @TableField("device_sync_record_status")
    private String deviceSyncRecordStatus;

    /** 说明（失败原因 / 结果摘要） */
    @TableField("device_sync_record_message")
    private String deviceSyncRecordMessage;

    /** 开始时间 */
    @TableField("device_sync_record_started_at")
    private LocalDateTime deviceSyncRecordStartedAt;

    /** 结束时间 */
    @TableField("device_sync_record_finished_at")
    private LocalDateTime deviceSyncRecordFinishedAt;
}
