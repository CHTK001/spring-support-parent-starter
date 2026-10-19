package com.chua.starter.device.support.po;

import lombok.Data;

import java.util.List;

/**
 * 设备分组树节点
 */
@Data
public class DeviceGroupTreeNode {

    private String groupId;
    private String groupName;
    private String groupParent;
    private Integer groupOrder;
    private String groupRemark;
    private Integer deviceCount;      // 直接挂载的设备数
    private List<DeviceGroupTreeNode> children;
}
