package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 服务拓扑。
 *
 * @param nodes 节点
 * @param calls 边
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingTopologyVO(List<TopologyNodeVO> nodes, List<TopologyCallVO> calls) {
}
