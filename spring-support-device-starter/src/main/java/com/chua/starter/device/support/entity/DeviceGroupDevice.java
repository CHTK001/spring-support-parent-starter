package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备-分组归属
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_group_device")
public class DeviceGroupDevice extends SysBase {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 分组ID */
    private String groupId;

    /** 设备ID */
    private String deviceId;

    /** 分组内排序 */
    private Integer deviceOrder;
}
