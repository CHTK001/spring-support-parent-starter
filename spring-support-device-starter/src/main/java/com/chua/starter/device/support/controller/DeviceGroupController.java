package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.entity.DeviceGroupDevice;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.po.DeviceGroupRequest;
import com.chua.starter.device.support.po.DeviceGroupTreeNode;
import com.chua.starter.device.support.service.DeviceGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备分组接口。
 *
 * <p>分组是树形结构（group_parent 指向上级），设备与分组是多对多（device_group_device）；
 * 一台设备可以同时出现在多个分组里。</p>
 *
 * <p>接口前缀与其他设备接口一致：{@code /api/device/group}</p>
 */
@RestController
@RequestMapping("/api/device/group")
@RequiredArgsConstructor
public class DeviceGroupController {

    private final DeviceGroupService groupService;
    private final DeviceInfoMapper deviceInfoMapper;

    /** 分组树（含每个分组的直属设备数；根节点里额外带一个「未分组」占位） */
    /**
     * tree
     * @return 返回值
    */
    public List<DeviceGroupTreeNode> tree() {
        return groupService.tree();
    }

    /** 新建分组 */
    /**
     * 创建
     * @param req 参数
     * @return 返回值
    */
    public void create(@RequestBody DeviceGroupRequest req) {
        groupService.create(req);
    }

    /** 更新分组（名称 / 备注 / 上级分组，带防环校验） */
    /**
     * 更新
     * @param groupId 字符串
     * @param req 参数
     * @return 返回值
    */
    public void update(@PathVariable String groupId, @RequestBody DeviceGroupRequest req) {
        groupService.update(groupId, req);
    }

    /** 删除分组（子孙分组一并删除，设备本身不动） */
    /**
     * 删除
     * @param groupId 字符串
     * @return 返回值
    */
    public void delete(@PathVariable String groupId) {
        groupService.delete(groupId);
    }

    /** 分组下的设备列表 */
    /**
     * group Devices
     * @param groupId 字符串
     * @return 返回值
    */
    public List<DeviceInfo> groupDevices(@PathVariable String groupId) {
        List<String> ids = groupService.groupDeviceIds(groupId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(deviceInfoMapper::selectById)
                .filter(d -> d != null && d.getDeviceInfoId() != null)
                .toList();
    }

    /** 分配设备到分组（全量替换） */
    /**
     * assign Devices
     * @param deviceIds 参数
     * @param groupId 字符串
     * @return 返回值
    */
    public void assignDevices(@PathVariable String groupId, @RequestBody List<String> deviceIds) {
        groupService.assignDevices(groupId, deviceIds);
    }

    /** 设备所属分组ID列表 */
    /**
     * device Groups
     * @param deviceId 字符串
     * @return 返回值
    */
    public List<String> deviceGroups(@PathVariable String deviceId) {
        return groupService.deviceGroupIds(deviceId);
    }

    /** 设置设备所属分组（全量替换该设备的关联） */
    /**
     * assign Device Groups
     * @param deviceId 字符串
     * @param groupIds 参数
     * @return 返回值
    */
    public void assignDeviceGroups(@PathVariable String deviceId, @RequestBody List<String> groupIds) {
        groupService.assignDeviceGroups(deviceId, groupIds);
    }

    /** 所有设备-分组关联（前端批量回显用） */
    /**
     * assignments
     * @return 返回值
    */
    public List<DeviceGroupDevice> assignments() {
        return groupService.assignments();
    }
}
