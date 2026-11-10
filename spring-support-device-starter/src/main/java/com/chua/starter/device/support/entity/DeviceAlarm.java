package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备告警（webhook 入站落库，供告警页查询与处理）。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("device_alarm")
public class DeviceAlarm {

    /** 主键 */
    @TableId(value = "device_alarm_id", type = IdType.ASSIGN_UUID)
    private String deviceAlarmId;

    /** 来源（appKey 名称 / 厂商标识） */
    @TableField("device_alarm_source")
    private String deviceAlarmSource;

    /** 外部事件 ID（与来源一起构成幂等键） */
    @TableField("device_alarm_external_id")
    private String deviceAlarmExternalId;

    /** 所属设备 ID */
    @TableField("device_alarm_device_id")
    private String deviceAlarmDeviceId;

    /** 通道号 */
    @TableField("device_alarm_channel_no")
    private Integer deviceAlarmChannelNo;

    /** 告警类型：MOTION / IO_ALARM / OFFLINE / ACCESS / OTHER */
    @TableField("device_alarm_type")
    private String deviceAlarmType;

    /** 级别：critical / major / minor / info */
    @TableField("device_alarm_level")
    private String deviceAlarmLevel;

    /** 告警标题 */
    @TableField("device_alarm_title")
    private String deviceAlarmTitle;

    /** 告警详情 */
    @TableField("device_alarm_detail")
    private String deviceAlarmDetail;

    /** 处理状态：unhandled / handling / resolved / ignored */
    @TableField("device_alarm_status")
    private String deviceAlarmStatus;

    /** 处理人 */
    @TableField("device_alarm_handler")
    private String deviceAlarmHandler;

    /** 处理时间 */
    @TableField("device_alarm_handled_at")
    private LocalDateTime deviceAlarmHandledAt;

    /** 发生时间 */
    @TableField("device_alarm_occurred_at")
    private LocalDateTime deviceAlarmOccurredAt;

    /** 入库时间 */
    @TableField("device_alarm_created_at")
    private LocalDateTime deviceAlarmCreatedAt;
}
