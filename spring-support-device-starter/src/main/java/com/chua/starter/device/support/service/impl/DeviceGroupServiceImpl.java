package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.device.support.entity.*;
import com.chua.starter.device.support.mapper.*;
import com.chua.starter.device.support.po.*;
import com.chua.starter.device.support.service.DeviceGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备分组服务实现。
 *
 * <p>分组树按 parentId 递归装配；分组与设备关联的增删在事务内维护，
 * 保证分组删除时关联记录一并清理。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Service
@RequiredArgsConstructor
public class DeviceGroupServiceImpl implements DeviceGroupService {

    private final DeviceGroupMapper groupMapper;
    private final DeviceGroupDeviceMapper gdMapper;

    @Override
    public List<DeviceGroupTreeNode> tree() {
        List<DeviceGroup> all = groupMapper.selectList(null);
        Map<String, Integer> countMap = all.stream()
                .collect(Collectors.toMap(
                        DeviceGroup::getGroupId,
                        g -> groupMapper.countDevices(g.getGroupId())
                ));
        // 转 map
        Map<String, DeviceGroupTreeNode> nodeMap = all.stream()
                .collect(Collectors.toMap(
                        DeviceGroup::getGroupId,
                        g -> {
                            DeviceGroupTreeNode n = new DeviceGroupTreeNode();
                            n.setGroupId(g.getGroupId());
                            n.setGroupName(g.getGroupName());
                            n.setGroupParent(g.getGroupParent());
                            n.setGroupOrder(g.getGroupOrder());
                            n.setGroupRemark(g.getGroupRemark());
                            n.setDeviceCount(countMap.getOrDefault(g.getGroupId(), 0));
                            n.setChildren(new ArrayList<>());
                            return n;
                        }
                ));
        // 挂载 children
        List<DeviceGroupTreeNode> roots = new ArrayList<>();
        for (DeviceGroupTreeNode n : nodeMap.values()) {
            String p = n.getGroupParent();
            if (p == null || p.isBlank() || !nodeMap.containsKey(p)) {
                roots.add(n);
            } else {
                nodeMap.get(p).getChildren().add(n);
            }
        }
        // 按 group_order 排序（根节点与子节点都排）
        Comparator<DeviceGroupTreeNode> byOrder = Comparator.comparingInt(
                n -> n.getGroupOrder() == null ? 0 : n.getGroupOrder());
        nodeMap.values().forEach(n -> n.getChildren().sort(byOrder));
        roots.sort(byOrder);
        // 根节点加一个「未分组」
        DeviceGroupTreeNode ungrouped = new DeviceGroupTreeNode();
        ungrouped.setGroupId("");
        ungrouped.setGroupName("未分组");
        ungrouped.setDeviceCount(-1); // 占位，不显示数量
        ungrouped.setChildren(new ArrayList<>());
        roots.add(0, ungrouped);
        return roots;
    }

    @Override
    @Transactional
    /**
     * 创建
     * @param req 参数
     * @return 返回值
    */
    public void create(DeviceGroupRequest req) {
        if (req.groupName() == null || req.groupName().isBlank()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        DeviceGroup g = new DeviceGroup();
        g.setGroupName(req.groupName().trim());
        g.setGroupParent(req.groupParent() == null || req.groupParent().isBlank() ? null : req.groupParent());
        g.setGroupOrder(req.groupOrder() != null ? req.groupOrder() : 0);
        g.setGroupRemark(req.groupRemark());
        groupMapper.insert(g);
    }

    @Override
    @Transactional
    /**
     * 更新
     * @param groupId 字符串
     * @param req 参数
     * @return 返回值
    */
    public void update(String groupId, DeviceGroupRequest req) {
        LambdaUpdateWrapper<DeviceGroup> uw = new LambdaUpdateWrapper<>();
        uw.eq(DeviceGroup::getGroupId, groupId);
        if (req.groupName() != null) {
            uw.set(DeviceGroup::getGroupName, req.groupName());
        }
        if (req.groupOrder() != null) {
            uw.set(DeviceGroup::getGroupOrder, req.groupOrder());
        }
        if (req.groupRemark() != null) {
            uw.set(DeviceGroup::getGroupRemark, req.groupRemark());
        }
        if (req.groupParent() != null) {
            String parent = req.groupParent().isBlank() ? null : req.groupParent();
            if (parent != null) {
                if (parent.equals(groupId)) {
                    throw new IllegalArgumentException("上级分组不能是自身");
                }
                // 防环：上级不能是自己的子孙
                Set<String> descendants = new HashSet<>();
                collectDescendants(groupId, descendants);
                if (descendants.contains(parent)) {
                    throw new IllegalArgumentException("上级分组不能是自己的子分组");
                }
            }
            uw.set(DeviceGroup::getGroupParent, parent);
        }
        groupMapper.update(null, uw);
    }

    @Override
    @Transactional
    /**
     * 删除
     * @param groupId 字符串
     * @return 返回值
    */
    public void delete(String groupId) {
        // 递归清理：先删所有子孙分组及其设备关联，再删自身
        Set<String> ids = new HashSet<>();
        ids.add(groupId);
        collectDescendants(groupId, ids);
        for (String id : ids) {
            gdMapper.deleteByGroupId(id);
            groupMapper.deleteById(id);
        }
    }

    /**
     * collect Descendants
     * @param groupId 字符串
     * @param out 参数
     * @return 返回值
    */
    private void collectDescendants(String groupId, Set<String> out) {
        LambdaQueryWrapper<DeviceGroup> q = new LambdaQueryWrapper<>();
        q.eq(DeviceGroup::getGroupParent, groupId);
        for (DeviceGroup child : groupMapper.selectList(q)) {
            if (out.add(child.getGroupId())) {
                collectDescendants(child.getGroupId(), out);
            }
        }
    }

    @Override
    @Transactional
    /**
     * assign Devices
     * @param deviceIds 参数
     * @param groupId 字符串
     * @return 返回值
    */
    public void assignDevices(String groupId, List<String> deviceIds) {
        // 先清空该分组原设备
        gdMapper.deleteByGroupId(groupId);
        // 再批量插入
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        for (int i = 0; i < deviceIds.size(); i++) {
            DeviceGroupDevice gd = new DeviceGroupDevice();
            gd.setId(UUID.randomUUID().toString());
            gd.setGroupId(groupId);
            gd.setDeviceId(deviceIds.get(i));
            gd.setDeviceOrder(i);
            gdMapper.insert(gd);
        }
    }

    @Override
    public List<String> groupDeviceIds(String groupId) {
        return gdMapper.findDeviceIds(groupId);
    }

    @Override
    public List<String> deviceGroupIds(String deviceId) {
        return groupMapper.findGroupIdsByDeviceId(deviceId);
    }

    @Override
    public List<DeviceGroupDevice> assignments() {
        return gdMapper.selectList(null);
    }

    @Override
    @Transactional
    /**
     * assign Device Groups
     * @param deviceId 字符串
     * @param groupIds 参数
     * @return 返回值
    */
    public void assignDeviceGroups(String deviceId, List<String> groupIds) {
        LambdaQueryWrapper<DeviceGroupDevice> q = new LambdaQueryWrapper<>();
        q.eq(DeviceGroupDevice::getDeviceId, deviceId);
        gdMapper.delete(q);
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }
        int order = 0;
        for (String groupId : new LinkedHashSet<>(groupIds)) {
            if (groupId == null || groupId.isBlank()) {
                continue;
            }
            DeviceGroupDevice gd = new DeviceGroupDevice();
            gd.setId(UUID.randomUUID().toString());
            gd.setGroupId(groupId);
            gd.setDeviceId(deviceId);
            gd.setDeviceOrder(order++);
            gdMapper.insert(gd);
        }
    }
}
