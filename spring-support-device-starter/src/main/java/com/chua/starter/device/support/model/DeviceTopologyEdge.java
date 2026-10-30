package com.chua.starter.device.support.model;

/**
 * 机房拓扑：边
 *
 * @since 4.0.0.42
 * @author CH
 */
public record DeviceTopologyEdge(
        /** 起点（源分组 id / 设备 id） */
        String source,
        /** 终点（设备 id / 通道 id） */
        String target) {
}
