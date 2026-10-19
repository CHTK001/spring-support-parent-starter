package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.*;
import com.chua.starter.device.support.mapper.*;
import com.chua.starter.device.support.po.*;
import com.chua.starter.device.support.service.DeviceGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
    public void create(DeviceGroupRequest req) {
        DeviceGroup g = new DeviceGroup();
        g.setGroupName(req.getGroupName());
        g.setGroupParent(req.getGroupParent());
        g.setGroupOrder(req.getGroupOrder() != null ? req.getGroupOrder() : 0);
        g.setGroupRemark(req.getGroupRemark());
        groupMapper.insert(g);
    }

    @Override
    @Transactional
    public void update(String groupId, DeviceGroupRequest req) {
        DeviceGroup g = new DeviceGroup();
        g.setGroupId(groupId);
        if (req.getGroupName() != null) g.setGroupName(req.getGroupName());
        if (req.getGroupOrder() != null) g.setGroupOrder(req.getGroupOrder());
        if (req.getGroupRemark() != null) g.setGroupRemark(req.getGroupRemark());
        // 不允许改 parent（防止循环）
        groupMapper.updateById(g);
    }

    @Override
    @Transactional
    public void delete(String groupId) {
        // 1. 把该分组下的设备迁到根（group_parent = null 视为根）
        //    这里直接删关联，设备本身不动
        gdMapper.deleteByGroupId(groupId);
        // 2. 删子分组（递归，可选；这里只删一层）
        LambdaQueryWrapper<DeviceGroup> q = new LambdaQueryWrapper<>();
        q.eq(DeviceGroup::getGroupParent, groupId);
        List<DeviceGroup> children = groupMapper.selectList(q);
        for (DeviceGroup child : children) {
            gdMapper.deleteByGroupId(child.getGroupId());
            groupMapper.deleteById(child.getGroupId());
        }
        // 3. 删自身
        groupMapper.deleteById(groupId);
    }

    @Override
    @Transactional
    public void assignDevices(String groupId, List<String> deviceIds) {
        // 先清空该分组原设备
        gdMapper.deleteByGroupId(groupId);
        // 再批量插入
        if (deviceIds == null || deviceIds.isEmpty()) return;
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
}
