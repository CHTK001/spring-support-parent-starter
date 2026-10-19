package com.chua.starter.monitor.support.node;

import java.util.List;

/**
 * 发现服务查询结果（对齐前端 DiscoveryQueryResult 契约）。
 *
 * @param instances 服务实例列表
 * @param message   说明信息
 * @author CH
 * @since 4.0.0.42
 */
public record DiscoveryQueryResultVO(List<DiscoveryInstanceVO> instances, String message) {
}
