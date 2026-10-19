package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.po.*;
import com.chua.starter.device.support.service.DeviceGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/device/group")
@RequiredArgsConstructor
@Tag(name = "设备分组")
public class DeviceGroupController {

    private final DeviceGroupService groupService;
    private final DeviceInfoMapper deviceInfoMapper;

    @GetMapping("/tree")
    @Operation(summary = "分组树（含设备数）")
    public List<DeviceGroupTreeNode> tree() {
        return groupService.tree();
    }

    @PostMapping
    @Operation(summary = "新建分组")
    public void create(@RequestBody DeviceGroupRequest req) {
        groupService.create(req);
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "更新分组")
    public void update(@PathVariable String groupId, @RequestBody DeviceGroupRequest req) {
        groupService.update(groupId, req);
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "删除分组")
    public void delete(@PathVariable String groupId) {
        groupService.delete(groupId);
    }

    @GetMapping("/{groupId}/devices")
    @Operation(summary = "分组下设备列表")
    public List<DeviceInfo> groupDevices(@PathVariable String groupId) {
        List<String> ids = groupService.groupDeviceIds(groupId);
        if (ids.isEmpty()) return List.of();
        return ids.stream()
                .map(deviceInfoMapper::selectById)
                .filter(d -> d != null && d.getDeviceInfoId() != null)
                .toList();
    }

    @PutMapping("/{groupId}/devices")
    @Operation(summary = "分配设备到分组（全量替换）")
    public void assignDevices(@PathVariable String groupId, @RequestBody List<String> deviceIds) {
        groupService.assignDevices(groupId, deviceIds);
    }

    @GetMapping("/device/{deviceId}/groups")
    @Operation(summary = "设备所属分组ID列表")
    public List<String> deviceGroups(@PathVariable String deviceId) {
        return groupService.deviceGroupIds(deviceId);
    }
}
