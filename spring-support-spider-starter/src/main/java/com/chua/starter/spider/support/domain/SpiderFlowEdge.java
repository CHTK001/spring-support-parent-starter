package com.chua.starter.spider.support.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 编排流程有向边
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpiderFlowEdge {

    /** 边唯一 ID */
    private String edgeId;

    /** 源节点 ID */
    private String sourceNodeId;

    /** 目标节点 ID */
    private String targetNodeId;
}
