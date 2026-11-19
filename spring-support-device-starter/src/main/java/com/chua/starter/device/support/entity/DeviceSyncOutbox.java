package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备变更推送队列（outbox）。
 *
 * <p>本地写回厂商失败时入队，按 {@code next_at} 供重试；重试成功置
 * {@code sent}，判定不可重试（厂商不支持/凭据缺失）直接置 {@code dead}。
 * 表无 SysBase 审计列，故不继承 {@code SysBase}。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("device_sync_outbox")
public class DeviceSyncOutbox {

    /** 主键 */
    @TableId(value = "device_sync_outbox_id", type = IdType.ASSIGN_UUID)
    private String deviceSyncOutboxId;

    /** 设备 ID */
    @TableField("device_sync_outbox_device_id")
    private String deviceSyncOutboxDeviceId;

    /** 厂商标识 */
    @TableField("device_sync_outbox_vendor")
    private String deviceSyncOutboxVendor;

    /** 动作：create / update / delete */
    @TableField("device_sync_outbox_action")
    private String deviceSyncOutboxAction;

    /** 推送载荷（快照 JSON，排障用） */
    @TableField("device_sync_outbox_payload")
    private String deviceSyncOutboxPayload;

    /** 状态：pending / sent / failed / dead */
    @TableField("device_sync_outbox_status")
    private String deviceSyncOutboxStatus;

    /** 已重试次数 */
    @TableField("device_sync_outbox_retry")
    private Integer deviceSyncOutboxRetry;

    /** 下次重试时间 */
    @TableField("device_sync_outbox_next_at")
    private LocalDateTime deviceSyncOutboxNextAt;

    /** 失败原因 */
    @TableField("device_sync_outbox_message")
    private String deviceSyncOutboxMessage;

    /** 创建时间 */
    @TableField("device_sync_outbox_created_at")
    private LocalDateTime deviceSyncOutboxCreatedAt;
}
