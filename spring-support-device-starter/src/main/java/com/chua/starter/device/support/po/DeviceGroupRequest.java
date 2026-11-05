package com.chua.starter.device.support.po;

/**
 * 设备分组 - 创建/更新请求
 *
 * @author CH
 * @since 4.0.0.51
 */
public record DeviceGroupRequest(
        String groupName,
        String groupParent,
        Integer groupOrder,
        String groupRemark
) {
}
