package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
* 设备门禁实体
* <p>对应数据库表 device_door。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("device_door")
public class DeviceDoor {

    /**
    * 主键 标识
    */
    @TableId(value = "device_door_id", type = IdType.ASSIGN_UUID)
    /** devicedoorid */
    private String deviceDoorId;

    /**
    * 所属设备 标识
    */
    @TableField("device_door_device_id")
    /** devicedoordeviceid */
    private String deviceDoorDeviceId;

    /**
    * 门编号
    */
    @TableField("device_door_no")
    /** devicedoorno */
    private Integer deviceDoorNo;

    /**
    * 门名称
    */
    @TableField("device_door_name")
    /** Devicedoor名称 */
    private String deviceDoorName;

    /**
    * 门状态，例如 锁、unlocked
    */
    @TableField("device_door_status")
    /** Devicedoor状态 */
    private String deviceDoorStatus;
}
