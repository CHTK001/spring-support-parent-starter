package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考勤记录实体。
 *
 * <p>考勤不做人工增删改，只接收 webhook 推送或由门禁记录定时归集；
 * 对应数据库表 {@code device_attendance_record}。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
@TableName("device_attendance_record")
public class DeviceAttendanceRecord {

    /**
     * 主键
     */
    @TableId(value = "device_attendance_record_id", type = IdType.ASSIGN_UUID)
    private String deviceAttendanceRecordId;

    /**
     * 设备 ID
     */
    @TableField("device_attendance_record_device_id")
    private String deviceAttendanceRecordDeviceId;

    /**
     * 人员姓名
     */
    @TableField("device_attendance_record_person_name")
    private String deviceAttendanceRecordPersonName;

    /**
     * 人员编号（工号）
     */
    @TableField("device_attendance_record_person_no")
    private String deviceAttendanceRecordPersonNo;

    /**
     * 卡号
     */
    @TableField("device_attendance_record_card_no")
    private String deviceAttendanceRecordCardNo;

    /**
     * 方向：in 上班 / out 下班
     */
    @TableField("device_attendance_record_direction")
    private String deviceAttendanceRecordDirection;

    /**
     * 考勤时间
     */
    @TableField("device_attendance_record_occurred_at")
    private LocalDateTime deviceAttendanceRecordOccurredAt;

    /**
     * 数据来源：webhook / access-sync
     */
    @TableField("device_attendance_record_source")
    private String deviceAttendanceRecordSource;

    /**
     * 来源侧幂等键（webhook 外部 ID 或门禁记录主键）
     */
    @TableField("device_attendance_record_source_id")
    private String deviceAttendanceRecordSourceId;

    /**
     * 创建时间
     */
    @TableField("device_attendance_record_created_at")
    private LocalDateTime deviceAttendanceRecordCreatedAt;
}
