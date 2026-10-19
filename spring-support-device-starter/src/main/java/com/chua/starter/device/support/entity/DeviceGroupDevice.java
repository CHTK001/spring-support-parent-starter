package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备-分组归属
 */
@Data
@TableName("device_group_device")
public class DeviceGroupDevice {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 分组ID */
    private String groupId;

    /** 设备ID */
    private String deviceId;

    /** 分组内排序 */
    private Integer deviceOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
