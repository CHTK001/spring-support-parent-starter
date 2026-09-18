package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
* 设备事件记录实体
* <p>对应数据库表 device_event_record。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("device_event_record")
public class DeviceEventRecord {

    /**
    * 主键 标识
    */
    @TableId(value = "device_event_record_id", type = IdType.ASSIGN_UUID)
    /** Device事件recordid */
    private String deviceEventRecordId;

    /**
    * 所属设备 标识
    */
    @TableField("device_event_record_device_id")
    /** Device事件recorddeviceid */
    private String deviceEventRecordDeviceId;

    /**
    * 通道号
    */
    @TableField("device_event_record_channel_no")
    /** Device事件record通道NO */
    private Integer deviceEventRecordChannelNo;

    /**
    * 事件类型
    */
    @TableField("device_event_record_event_type")
    /** Device事件record事件类型 */
    private String deviceEventRecordEventType;

    /**
    * 事件详情
    */
    @TableField("device_event_record_detail")
    /** Device事件recorddetail */
    private String deviceEventRecordDetail;

    /**
    * 发生时间
    */
    @TableField("device_event_record_occurred_at")
    /** Device事件recordoccurredat */
    private LocalDateTime deviceEventRecordOccurredAt;
}
