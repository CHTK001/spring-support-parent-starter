package com.chua.starter.device.support.service;

import com.chua.starter.device.support.po.*;

import java.util.List;

public interface DeviceGroupService {

    /** 查询分组树（用于左侧树） */
    List<DeviceGroupTreeNode> tree();

    /** 创建分组 */
    void create(DeviceGroupRequest req);

    /** 更新分组 */
    void update(String groupId, DeviceGroupRequest req);

    /** 删除分组（自动迁移设备到根） */
    void delete(String groupId);

    /** 批量分配设备到分组 */
    void assignDevices(String groupId, List<String> deviceIds);

    /** 查询分组下的设备ID列表 */
    List<String> groupDeviceIds(String groupId);

    /** 查询设备所属分组ID列表 */
    List<String> deviceGroupIds(String deviceId);
}
