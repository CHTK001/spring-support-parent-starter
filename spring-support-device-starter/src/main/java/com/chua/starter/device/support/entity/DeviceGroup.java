package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备分组
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_group")
public class DeviceGroup extends SysBase {

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
}
