package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 设备预置位实体
 * <p>对应数据库表 device_preset。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("device_preset")
public class DevicePreset {

    /**
     * 主键 ID
     */
    @TableId(value = "device_preset_id", type = IdType.ASSIGN_UUID)
    /** DevicepresetID */
    private String devicePresetId;

    /**
     * 所属设备 ID
     */
    @TableField("device_preset_device_id")
    /** DevicepresetdeviceID */
    private String devicePresetDeviceId;

    /**
     * 预置位编号
     */
    @TableField("device_preset_no")
    /** DevicepresetNO */
    private Integer devicePresetNo;

    /**
     * 预置位名称
     */
    @TableField("device_preset_name")
    /** Devicepreset名称 */
    private String devicePresetName;
}