package com.chua.starter.spider.support.serializer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;

import java.util.Collections;
import java.util.List;

/**
 * 将 {@link SpiderFlowDefinition} 序列化为 JSON 字符串。
 * <p>节点列表和边列表均完整输出，不丢失任何字段。</p>
 *
 * @author CH
 */
public class SpiderFlowSerializer {

    /**
     * 将编排定义序列化为紧凑 JSON 字符串。
     *
     * @param flow 编排定义，不能为 null
     * @return 合法 JSON 字符串
     */
    public String serialize(SpiderFlowDefinition flow) {
        if (flow == null) {
            throw new IllegalArgumentException("flow must not be null");
        }
        FlowJson dto = toDto(flow);
        return JSON.toJSONString(dto, JSONWriter.Feature.WriteNulls);
    }

    /**
     * 将编排定义序列化为格式化（Pretty Print）JSON 字符串。
     *
     * @param flow 编排定义，不能为 null
     * @return 人类可读的 JSON 字符串
     */
    public String serializePretty(SpiderFlowDefinition flow) {
        if (flow == null) {
            throw new IllegalArgumentException("flow must not be null");
        }
        FlowJson dto = toDto(flow);
        return JSON.toJSONString(dto, JSONWriter.Feature.WriteNulls, JSONWriter.Feature.PrettyFormat);
    }

    private FlowJson toDto(SpiderFlowDefinition flow) {
        FlowJson dto = new FlowJson();
        dto.id = flow.getId();
        dto.taskId = flow.getTaskId();
        dto.version = flow.getVersion();
        dto.nodes = flow.getNodes() != null ? flow.getNodes() : Collections.emptyList();
        dto.edges = flow.getEdges() != null ? flow.getEdges() : Collections.emptyList();
        return dto;
    }

    /** 序列化 DTO，确保 nodes/edges 字段始终输出 */
    static class FlowJson {
        public Long id;
        public Long taskId;
        public Integer version;
        public List<SpiderFlowNode> nodes;
        public List<SpiderFlowEdge> edges;
    }
}
