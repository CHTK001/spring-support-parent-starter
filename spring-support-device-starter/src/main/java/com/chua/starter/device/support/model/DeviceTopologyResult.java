package com.chua.starter.device.support.model;

import java.util.List;

/**
 * 机房拓扑查询结果
 *
 * @since 4.0.0.42
 * @author CH
 */
public record DeviceTopologyResult(
        /** 节点列表 */
        List<DeviceTopologyNode> nodes,
        /** 边列表 */
        List<DeviceTopologyEdge> edges) {
}
