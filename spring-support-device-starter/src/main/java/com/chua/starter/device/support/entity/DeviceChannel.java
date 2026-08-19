package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 设备通道实体
 * <p>对应数据库表 device_channel。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("device_channel")
public class DeviceChannel {

    /**
     * 主键 ID
     */
    @TableId(value = "device_channel_id", type = IdType.ASSIGN_UUID)
    /** Device通道ID */
    private String deviceChannelId;

    /**
     * 所属设备 ID
     */
    @TableField("device_channel_device_id")
    /** Device通道deviceID */
    private String deviceChannelDeviceId;

    /**
     * 通道号
     */
    @TableField("device_channel_no")
    /** Device通道NO */
    private Integer deviceChannelNo;

    /**
     * 通道名称
     */
    @TableField("device_channel_name")
    /** Device通道名称 */
    private String deviceChannelName;

    /**
     * 通道类型，例如 video、door
     */
    @TableField("device_channel_type")
    /** Device通道类型 */
    private String deviceChannelType;

    /**
     * 通道状态：enabled 启用、disabled 禁用
     */
    @TableField("device_channel_status")
    /** Device通道状态 */
    private String deviceChannelStatus;

    /**
     * 排序字段
     */
    @TableField("device_channel_sort")
    /** Device通道sort */
    private Integer deviceChannelSort;
}