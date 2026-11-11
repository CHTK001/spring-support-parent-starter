package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆档案实体。
 *
 * <p>对应数据库表 device_vehicle，本地台账；厂商侧有车辆接口时通过
 * {@code DeviceSession#upsertVehicle} 推送。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("device_vehicle")
public class DeviceVehicle {

    /**
     * 主键
     */
    @TableId(value = "device_vehicle_id", type = IdType.ASSIGN_UUID)
    private String deviceVehicleId;

    /**
     * 车牌号
     */
    @TableField("device_vehicle_plate_no")
    private String deviceVehiclePlateNo;

    /**
     * 车牌颜色
     */
    @TableField("device_vehicle_plate_color")
    private String deviceVehiclePlateColor;

    /**
     * 车辆类型
     */
    @TableField("device_vehicle_type")
    private String deviceVehicleType;

    /**
     * 车主姓名
     */
    @TableField("device_vehicle_owner_name")
    private String deviceVehicleOwnerName;

    /**
     * 车主电话
     */
    @TableField("device_vehicle_owner_phone")
    private String deviceVehicleOwnerPhone;

    /**
     * 名单类型：white / black / normal
     */
    @TableField("device_vehicle_list_type")
    private String deviceVehicleListType;

    /**
     * 有效期起
     */
    @TableField("device_vehicle_valid_from")
    private LocalDateTime deviceVehicleValidFrom;

    /**
     * 有效期止
     */
    @TableField("device_vehicle_valid_to")
    private LocalDateTime deviceVehicleValidTo;

    /**
     * 厂商标识
     */
    @TableField("device_vehicle_vendor")
    private String deviceVehicleVendor;

    /**
     * 厂商侧车辆标识
     */
    @TableField("device_vehicle_external_id")
    private String deviceVehicleExternalId;

    /**
     * 绑定设备 ID（车闸所在设备）
     */
    @TableField("device_vehicle_device_id")
    private String deviceVehicleDeviceId;

    /**
     * 车牌所属（单位/车场，自由文本）
     */
    @TableField("device_vehicle_belonging")
    private String deviceVehicleBelonging;

    /**
     * 备注
     */
    @TableField("device_vehicle_remark")
    private String deviceVehicleRemark;

    /**
     * 删除标记：1 已删除、0 正常
     */
    @TableField("device_vehicle_deleted")
    private Boolean deviceVehicleDeleted;

    /**
     * 创建时间
     */
    @TableField("device_vehicle_created_at")
    private LocalDateTime deviceVehicleCreatedAt;

    /**
     * 更新时间
     */
    @TableField("device_vehicle_updated_at")
    private LocalDateTime deviceVehicleUpdatedAt;
}
