package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆过车记录实体。
 *
 * <p>对应数据库表 device_vehicle_pass；{@code direction} 记录入场/出场。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("device_vehicle_pass")
public class DeviceVehiclePass {

    /**
     * 主键
     */
    @TableId(value = "device_vehicle_pass_id", type = IdType.ASSIGN_UUID)
    private String deviceVehiclePassId;

    /**
     * 车闸标识
     */
    @TableField("device_vehicle_pass_gate_id")
    private String deviceVehiclePassGateId;

    /**
     * 车牌号
     */
    @TableField("device_vehicle_pass_plate_no")
    private String deviceVehiclePassPlateNo;

    /**
     * 方向：in 入场 / out 出场
     */
    @TableField("device_vehicle_pass_direction")
    private String deviceVehiclePassDirection;

    /**
     * 过车时间
     */
    @TableField("device_vehicle_pass_time")
    private LocalDateTime deviceVehiclePassTime;

    /**
     * 抓拍图片地址
     */
    @TableField("device_vehicle_pass_photo_url")
    private String deviceVehiclePassPhotoUrl;

    /**
     * 车辆类型
     */
    @TableField("device_vehicle_pass_vehicle_type")
    private String deviceVehiclePassVehicleType;

    /**
     * 创建时间
     */
    @TableField("device_vehicle_pass_created_at")
    private LocalDateTime deviceVehiclePassCreatedAt;
}
