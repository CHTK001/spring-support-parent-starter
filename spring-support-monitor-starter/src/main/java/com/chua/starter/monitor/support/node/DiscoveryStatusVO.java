package com.chua.starter.monitor.support.node;

import java.util.List;

/**
 * 发现服务状态（对齐前端 DiscoveryStatus 契约）。
 *
 * @param runningDiscoveryFilters 运行中的发现能力数量
 * @param availableProtocols      可用发现协议
 * @param discoveryServicePresent 是否装配了发现能力
 * @param enabled                 是否启用
 * @author CH
 * @since 4.0.0.42
 */
public record DiscoveryStatusVO(int runningDiscoveryFilters,
                                List<String> availableProtocols,
                                boolean discoveryServicePresent,
                                boolean enabled) {
}
