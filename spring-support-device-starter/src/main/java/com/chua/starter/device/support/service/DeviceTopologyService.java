package com.chua.starter.device.support.service;

import com.chua.starter.device.support.model.DeviceTopologyResult;

/**
 * 机房/网段拓扑服务
 * <p>从设备台账（{@code device_info}）与通道台账（{@code device_channel}）拼接：
 * 楼层/区域（area）→ 网段（subnet）→ 设备（device）→ 通道（channel），
 * 空区域的设备归入“未分组”。节点与边与 {@code ReRelationGraph}（G6）
 * 的节点 {@code { id, label, group, type, size }} 契约对齐。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DeviceTopologyService {

    /**
     * 机房拓扑
     *
     * @return 带分组的节点 + 边
     */
    DeviceTopologyResult topology();
}
