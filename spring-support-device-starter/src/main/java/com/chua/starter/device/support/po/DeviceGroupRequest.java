package com.chua.starter.device.support.po;

import lombok.Data;

/**
 * 设备分组 - 创建/更新请求
 */
@Data
public class DeviceGroupRequest {

    private String groupName;
    private String groupParent;
    private Integer groupOrder;
    private String groupRemark;
}
