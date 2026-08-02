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
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("device_channel")
public class DeviceChannel {

    /**
     * 主键 ID
     */
    @TableId(value = "device_channel_id", type = IdType.ASSIGN_UUID)
    private String deviceChannelId;

    /**
     * 所属设备 ID
     */
    @TableField("device_channel_device_id")
    private String deviceChannelDeviceId;

    /**
     * 通道号
     */
    @TableField("device_channel_no")
    private Integer deviceChannelNo;

    /**
     * 通道名称
     */
    @TableField("device_channel_name")
    private String deviceChannelName;

    /**
     * 通道类型，例如 video、door
     */
    @TableField("device_channel_type")
    private String deviceChannelType;

    /**
     * 通道状态：enabled 启用、disabled 禁用
     */
    @TableField("device_channel_status")
    private String deviceChannelStatus;

    /**
     * 排序字段
     */
    @TableField("device_channel_sort")
    private Integer deviceChannelSort;
}