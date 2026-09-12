package com.chua.starter.spider.execute;

import com.chua.common.support.task.flow.Flow;
import com.chua.common.support.task.flow.FlowDefinition;
import com.chua.common.support.task.flow.FlowInstance;
import com.chua.common.support.task.flow.FlowStatus;
import com.chua.common.support.task.flow.FlowTrace;
import com.chua.flow.support.FlowEngine;
import com.chua.starter.spider.entity.SpiderDataRecord;
import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.entity.SpiderExecutionLog;
import com.chua.starter.spider.entity.SpiderFlow;
import com.chua.starter.spider.service.SpiderDefinitionService;
import com.chua.starter.spider.service.SpiderExecutionLogService;
import com.chua.starter.spider.service.SpiderFlowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 爬虫流程执行器。
 *
 * <p>负责把持久化的流程（前端保存的节点/连线 JSON）转换为 utils-flow 引擎可执行的
 * {@link FlowDefinition}，并运行一次：</p>
 * <ol>
 *   <li>按爬虫定义 ID 加载其关联流程</li>
 *   <li>解析节点 JSON（flowNodeKey/flowNodeType/flowNodeConfig）与连线 JSON（flowEdgeFrom/flowEdgeTo）</li>
 *   <li>映射节点类型到引擎已注册类型（request/extract/loop/output 等）</li>
 *   <li>通过 {@link FlowEngine} 运行流程，记录节点级执行日志</li>
 *   <li>回写爬虫定义最近执行时间与状态</li>
 * </ol>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class SpiderFlowRunner {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(SpiderFlowRunner.class);

    /**
     * JSON 解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 前端节点类型 → 引擎节点类型映射
     */
    private static final Map<String, String> NODE_TYPE_MAP = Map.ofEntries(
            Map.entry("start", "start"),
            Map.entry("stop", "end"),
            Map.entry("request", "spider.request"),
            Map.entry("extract", "spider.extract"),
            Map.entry("loop", "spider.subLoop"),
            Map.entry("output", "spider.output"),
            Map.entry("transform", "spider.transform"),
            Map.entry("condition", "spider.condition"),
            Map.entry("map", "spider.map"),
            Map.entry("script", "spider.script"),
            Map.entry("db", "spider.db"),
            Map.entry("function", "spider.function")
    );

    /**
     * 爬虫定义服务
     */
    private final SpiderDefinitionService definitionService;

    /**
     * 流程编排服务
     */
    private final SpiderFlowService flowService;

    /**
     * 执行日志服务
     */
    private final SpiderExecutionLogService executionLogService;

    /**
     * 构造执行器。
     *
     * @param definitionService   爬虫定义服务
     * @param flowService         流程编排服务
     * @param executionLogService 执行日志服务
     */
    public SpiderFlowRunner(SpiderDefinitionService definitionService,
                            SpiderFlowService flowService,
                            SpiderExecutionLogService executionLogService) {
        this.definitionService = definitionService;
        this.flowService = flowService;
        this.executionLogService = executionLogService;
    }

    /**
     * 按爬虫定义执行一次流程。
     *
     * <p>执行成功返回执行号，流程不存在或无有效节点时抛出异常。</p>
     *
     * @param definition 爬虫定义
     * @return 执行号（executionNo）
     */
    @Transactional(rollbackFor = Exception.class)
    public String run(SpiderDefinition definition) {
        if (definition == null) {
            /**
             * illegal参数异常。
             * @param "爬虫定义不存在" "爬虫定义不存在"
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("爬虫定义不存在");
        }
        Long flowId = definition.getFlowId();
        SpiderFlow flow = flowId == null ? null : flowService.getById(flowId);
        if (flow == null) {
            /**
             * illegal状态异常。
             * @param definition.getCode( definition.获取编码(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("爬虫 [" + definition.getCode() + "] 未关联流程编排");
        }
        FlowDefinition definitionModel = toFlowDefinition(flow);
        if (definitionModel.getNodes().isEmpty()) {
            /**
             * illegal状态异常。
             * @param "流程编排为空，无法执行" "流程编排为空，无法执行"
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("流程编排为空，无法执行");
        }
        String executionNo = UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        long start = System.currentTimeMillis();
        LocalDateTime startAt = LocalDateTime.now();
        try {
            Flow flowModel = FlowEngine.createFlow(definitionModel);
            FlowInstance instance = flowModel.createGraph().createInstance(executionParams(definition, executionNo));
            instance.run();
            /**
             * record追踪。
             * @param definition definition
             * @param flow 流
             * @param executionNo 执行no
             * @param instance instance
             * @param startAt 启动at
             */
            recordTraces(definition, flow, executionNo, instance, startAt);
            /**
             * 饰面。
             * @param definition definition
             * @param "SUCCESS" "成功"
             * @param (System.currentTimeMillis( (系统.当前时间millis(
             */
            finish(definition, "SUCCESS", "执行成功，耗时 " + (System.currentTimeMillis() - start) + "ms");
            log.info("爬虫 [{}] 执行成功，executionNo={}", definition.getCode(), executionNo);
            return executionNo;
        } catch (Exception e) {
            /**
             * record失败。
             * @param definition definition
             * @param flow 流
             * @param executionNo 执行no
             * @param startAt 启动at
             * @param e e
             */
            recordFailure(definition, flow, executionNo, startAt, e);
            /**
             * illegal状态异常。
             * @param definition.getCode( definition.获取编码(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("爬虫 [" + definition.getCode() + "] 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构造实例运行参数。
     *
     * <p>将执行号与爬虫编码写入上下文，供 output 节点落库时读取。</p>
     *
     * @param definition  爬虫定义
     * @param executionNo 执行号
     * @return 运行参数映射
     */
    private Map<String, Object> executionParams(SpiderDefinition definition, String executionNo) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("executionNo", executionNo);
        params.put("definitionCode", definition.getCode());
        return params;
    }

    /**
     * 将持久化流程转换为引擎可执行的流程定义。
     *
     * @param flow 持久化流程
     * @return 引擎流程定义
     */
    private FlowDefinition toFlowDefinition(SpiderFlow flow) {
        FlowDefinition definition = new FlowDefinition();
        definition.setId(String.valueOf(flow.getId()));
        definition.setName(flow.getName());
        definition.setNodes(parseNodes(flow.getNodesJson()));
        definition.setEdges(parseEdges(flow.getEdgesJson()));
        return definition;
    }

    /**
     * 解析节点 JSON 数组。
     *
     * @param nodesJson 节点 JSON 数组
     * @return 引擎节点定义列表
     */
    private List<FlowDefinition.FlowNodeDef> parseNodes(String nodesJson) {
        List<FlowDefinition.FlowNodeDef> result = new ArrayList<>();
        if (!StringUtils.hasText(nodesJson)) {
            return result;
        }
        try {
            JsonNode array = MAPPER.readTree(nodesJson);
            if (!array.isArray()) {
                return result;
            }
            for (JsonNode node : array) {
                String key = text(node, "flowNodeKey");
                String type = text(node, "flowNodeType");
                String engineType = NODE_TYPE_MAP.getOrDefault(type, type);
                Map<String, Object> props = parseConfig(node.get("flowNodeConfig"));
                double x = node.hasNonNull("flowNodeX") ? node.get("flowNodeX").asDouble() : 0;
                double y = node.hasNonNull("flowNodeY") ? node.get("flowNodeY").asDouble() : 0;
                result.add(new FlowDefinition.FlowNodeDef(key, engineType, props, x, y));
            }
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param e.getMessage( e.获取消息(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("解析节点 JSON 失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 解析连线 JSON 数组。
     *
     * @param edgesJson 连线 JSON 数组
     * @return 引擎连线定义列表
     */
    private List<FlowDefinition.FlowEdgeDef> parseEdges(String edgesJson) {
        List<FlowDefinition.FlowEdgeDef> result = new ArrayList<>();
        if (!StringUtils.hasText(edgesJson)) {
            return result;
        }
        try {
            JsonNode array = MAPPER.readTree(edgesJson);
            if (!array.isArray()) {
                return result;
            }
            for (JsonNode edge : array) {
                String from = text(edge, "flowEdgeFrom");
                String to = text(edge, "flowEdgeTo");
                if (from == null || to == null) {
                    continue;
                }
                result.add(FlowDefinition.FlowEdgeDef.of(from, to));
            }
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param e.getMessage( e.获取消息(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("解析连线 JSON 失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 解析节点配置 JSON 字符串为属性映射。
     *
     * @param configNode 配置 JSON 节点
     * @return 属性映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(JsonNode configNode) {
        if (configNode == null || configNode.isNull()) {
            return new LinkedHashMap<>();
        }
        try {
            if (configNode.isTextual()) {
                return MAPPER.readValue(configNode.asText(), new TypeReference<Map<String, Object>>() {
                });
            }
            if (configNode.isObject()) {
                return MAPPER.convertValue(configNode, new TypeReference<Map<String, Object>>() {
                });
            }
        } catch (Exception e) {
            log.warn("解析节点配置失败，忽略: {}", e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    /**
     * 读取 JSON 节点字符串字段。
     *
     * @param node JSON 节点
     * @param key  字段名
     * @return 字符串值，不存在时为 空
     */
    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 记录节点级执行日志。
     *
     * @param definition  爬虫定义
     * @param flow        流程
     * @param executionNo 执行号
     * @param instance    流程实例
     * @param startAt     开始时间
     */
    private void recordTraces(SpiderDefinition definition, SpiderFlow flow,
                              String executionNo, FlowInstance instance, LocalDateTime startAt) {
        List<FlowTrace> traces = instance.getContext() == null
                ? List.of() : instance.getContext().getTraces();
        if (traces.isEmpty()) {
            return;
        }
        long base = startAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (FlowTrace trace : traces) {
            SpiderExecutionLog entry = new SpiderExecutionLog();
            entry.setExecutionNo(executionNo);
            entry.setDefinitionCode(definition.getCode());
            entry.setFlowId(flow.getId());
            entry.setNodeId(trace.nodeId());
            entry.setNodeType(trace.nodeId());
            entry.setStatus(instance.getStatus() == FlowStatus.COMPLETED ? "SUCCESS" : "FAIL");
            entry.setInputData(safeJson(trace.input()));
            entry.setOutputData(safeJson(trace.output()));
            entry.setStartAt(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(trace.timestamp()), java.time.ZoneId.systemDefault()));
            entry.setEndAt(LocalDateTime.now());
            executionLogService.save(entry);
        }
    }

    /**
     * 记录失败信息。
     *
     * @param definition  爬虫定义
     * @param flow        流程
     * @param executionNo 执行号
     * @param startAt     开始时间
     * @param e           异常
     */
    private void recordFailure(SpiderDefinition definition, SpiderFlow flow,
                               String executionNo, LocalDateTime startAt, Exception e) {
        SpiderExecutionLog entry = new SpiderExecutionLog();
        entry.setExecutionNo(executionNo);
        entry.setDefinitionCode(definition.getCode());
        entry.setFlowId(flow.getId());
        entry.setStatus("FAIL");
        entry.setErrorMessage(e.getMessage());
        entry.setStartAt(startAt);
        entry.setEndAt(LocalDateTime.now());
        executionLogService.save(entry);
    }

    /**
     * 回写爬虫定义最近执行结果。
     *
     * @param definition 爬虫定义
     * @param status     执行状态
     * @param message    执行消息
     */
    private void finish(SpiderDefinition definition, String status, String message) {
        SpiderDefinition update = new SpiderDefinition();
        update.setId(definition.getId());
        update.setLastRunAt(LocalDateTime.now());
        update.setLastStatus(status);
        update.setLastMessage(message);
        definitionService.updateById(update);
    }

    /**
     * 序列化对象为 JSON 字符串。
     *
     * @param value 对象
     * @return JSON 字符串
     */
    private String safeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
