package com.chua.starter.device.support.model;

/**
 * 机房拓扑节点（与 `ReRelationGraph` 的节点契约对齐：{ id, label, group, type, size }）
 *
 * @since 4.0.0.42
 * @author CH
 */
public record DeviceTopologyNode(
        /** 节点 id（业务唯一，如 device:xxx / subnet:xxx / area:xxx） */
        String id,
        /** 节点名称（展示用） */
        String label,
        /** 分组（同组同色 + 气泡集分组轮廓） */
        String group,
        /** 节点类型（rect / circle） */
        String type,
        /** 节点尺寸（直径） */
        Integer size,
        /** 是否在线（仅设备级） */
        Boolean online) {

    public DeviceTopologyNode(String id, String label, String group, String type, Integer size) {
        this(id, label, group, type, size, null);
    }
}
