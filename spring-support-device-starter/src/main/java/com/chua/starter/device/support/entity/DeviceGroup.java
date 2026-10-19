package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备分组
 */
@Data
@TableName("device_group")
public class DeviceGroup {

    @TableId(type = IdType.ASSIGN_UUID)
    private String groupId;

    /** 分组名称 */
    private String groupName;

    /** 上级分组；顶级为空 */
    private String groupParent;

    /** 排序序号 */
    private Integer groupOrder;

    /** 备注 */
    private String groupRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
