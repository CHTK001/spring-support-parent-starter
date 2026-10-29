package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门禁出入记录实体（定时抽取落库）
 * <p>对应数据库表 device_access_record。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("device_access_record")
public class DeviceAccessRecord {

    /**
     * 主键 标识
     */
    @TableId(value = "device_access_record_id", type = IdType.ASSIGN_UUID)
    private String deviceAccessRecordId;

    /**
     * 所属设备 标识
     */
    @TableField("device_access_record_device_id")
    private String deviceAccessRecordDeviceId;

    /**
     * 门标识（厂商侧）
     */
    @TableField("device_access_record_door_id")
    private String deviceAccessRecordDoorId;

    /**
     * 卡号
     */
    @TableField("device_access_record_card_no")
    private String deviceAccessRecordCardNo;

    /**
     * 人员姓名
     */
    @TableField("device_access_record_user_name")
    private String deviceAccessRecordUserName;

    /**
     * 事件类型：刷卡开门 / 密码开门 / 异常 等
     */
    @TableField("device_access_record_event_type")
    private String deviceAccessRecordEventType;

    /**
     * 进出方向：in 进、out 出；厂商未回传为空
     */
    @TableField("device_access_record_direction")
    private String deviceAccessRecordDirection;

    /**
     * 抓拍照片地址
     */
    @TableField("device_access_record_photo_url")
    private String deviceAccessRecordPhotoUrl;

    /**
     * 刷卡时间
     */
    @TableField("device_access_record_occurred_at")
    private LocalDateTime deviceAccessRecordOccurredAt;
}