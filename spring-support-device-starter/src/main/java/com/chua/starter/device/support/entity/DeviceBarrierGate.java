package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车闸（道闸）点位实体。
 *
 * <p>对应数据库表 device_barrier_gate；{@code direction} 区分入口/出口/双向。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("device_barrier_gate")
public class DeviceBarrierGate {

    /**
     * 主键
     */
    @TableId(value = "device_barrier_gate_id", type = IdType.ASSIGN_UUID)
    private String deviceBarrierGateId;

    /**
     * 车闸名称
     */
    @TableField("device_barrier_gate_name")
    private String deviceBarrierGateName;

    /**
     * 所属设备标识
     */
    @TableField("device_barrier_gate_device_id")
    private String deviceBarrierGateDeviceId;

    /**
     * 通道号
     */
    @TableField("device_barrier_gate_channel_no")
    private Integer deviceBarrierGateChannelNo;

    /**
     * 方向：in 入口 / out 出口 / both 双向
     */
    @TableField("device_barrier_gate_direction")
    private String deviceBarrierGateDirection;

    /**
     * 状态：open / closed / offline / unknown
     */
    @TableField("device_barrier_gate_status")
    private String deviceBarrierGateStatus;

    /**
     * 厂商标识
     */
    @TableField("device_barrier_gate_vendor")
    private String deviceBarrierGateVendor;

    /**
     * 厂商侧车闸标识
     */
    @TableField("device_barrier_gate_external_id")
    private String deviceBarrierGateExternalId;

    /**
     * 删除标记：1 已删除、0 正常
     */
    @TableField("device_barrier_gate_deleted")
    private Boolean deviceBarrierGateDeleted;

    /**
     * 创建时间
     */
    @TableField("device_barrier_gate_created_at")
    private LocalDateTime deviceBarrierGateCreatedAt;

    /**
     * 更新时间
     */
    @TableField("device_barrier_gate_updated_at")
    private LocalDateTime deviceBarrierGateUpdatedAt;
}
