package com.chua.starter.spider.support.domain;

import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 编排流程节点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpiderFlowNode {

    /** 节点唯一 ID */
    private String nodeId;

    /** 节点类型 */
    private SpiderNodeType nodeType;

    /** 节点显示标签 */
    private String label;

    /** 节点配置（键值对） */
    private Map<String, Object> config;

    /** 节点级 AI 助手配置 */
    private SpiderAiProfile aiAssistantConfig;

    /** 节点在画布上的 X 坐标 */
    private Double positionX;

    /** 节点在画布上的 Y 坐标 */
    private Double positionY;
}
