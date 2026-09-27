package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Objects;

/**
 * 服务发现状态摘要
 *
 * @param runningDiscoveryFilters 运行中节点上挂载的发现插件数量
 * @param availableProtocols      SPI 可用的发现协议名
 * @param discoveryServicePresent 容器内是否装配 DiscoveryService
 * @param enabled                 是否存在可用的发现能力
 * @author CH
 * @since 4.0.0.42
 */
public record DiscoveryStatusVO(
        int runningDiscoveryFilters,
        List<String> availableProtocols,
        boolean discoveryServicePresent,
        boolean enabled
) {
    /**
     * 规范构造器：可用协议列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。协议名由 SPI 定义名去空白后收集，
     * 恒不为 null，故可使用拒绝 null 的 {@link List#copyOf}。</p>
     *
     * @param availableProtocols SPI 可用的发现协议名
     */
    public DiscoveryStatusVO {
        availableProtocols = List.copyOf(
                Objects.requireNonNull(availableProtocols, "availableProtocols 不能为 null"));
    }
}
