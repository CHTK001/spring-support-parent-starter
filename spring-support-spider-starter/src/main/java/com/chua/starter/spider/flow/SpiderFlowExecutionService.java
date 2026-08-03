package com.chua.starter.spider.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.spider.entity.SpiderExecutionRecord;
import com.chua.starter.spider.entity.SpiderFlow;
import com.chua.starter.spider.entity.SpiderFlowEdge;
import com.chua.starter.spider.entity.SpiderFlowHistory;
import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.entity.SpiderFlowNodeLog;
import com.chua.starter.spider.enums.SpiderExecutionStatus;
import com.chua.starter.spider.flow.handler.LoopNodeHandler;
import com.chua.starter.spider.mapper.SpiderExecutionRecordMapper;
import com.chua.starter.spider.mapper.SpiderFlowEdgeMapper;
import com.chua.starter.spider.mapper.SpiderFlowHistoryMapper;
import com.chua.starter.spider.mapper.SpiderFlowMapper;
import com.chua.starter.spider.mapper.SpiderFlowNodeLogMapper;
import com.chua.starter.spider.mapper.SpiderFlowNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 流程执行服务：编排 RelationGraph 流程，从起始节点开始按边条件流转。
 *
 * <p>核心流程：</p>
 * <ol>
 *   <li>根据 flowCode 加载流程定义与节点、边</li>
 *   <li>找到起始节点（type=start 的唯一节点）</li>
 *   <li>顺序执行节点：对每个节点，调用 {@link NodeHandler#execute(SpiderFlowNode, SpiderFlowContext)}，
 *       把结果摘要写入 {@code spider_flow_node_log} 表</li>
 *   <li>从当前节点的出边（按 priority 升序）求值，第一个条件成立的边 = 下一跳；直到 type=stop 或无下一跳</li>
 *   <li>循环节点：维护 _loop_position/_loop_item/_loop_index 三个 ctx 变量；
 *       流程执行器在每次回到循环节点时检查 _loop_position，超出列表长度则跳出循环</li>
 *   <li>遇到 stop 节点或被外部 interrupt，结束</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderFlowExecutionService {

    /**
     * 节点最大执行数（防止异常导致死循环）
     */
    private static final int MAX_NODE_EXECUTIONS = 500;

    /**
     * 起始节点类型
     */
    private static final String TYPE_START = "start";

    /**
     * 终止节点类型
     */
    private static final String TYPE_STOP = "stop";

    /**
     * 循环节点类型
     */
    private static final String TYPE_LOOP = "loop";

    /**
     * 等于操作符
     */
    private static final String OP_EQ = "=";

    /**
     * 不等于操作符
     */
    private static final String OP_NEQ = "!=";

    /**
     * 大于
     */
    private static final String OP_GT = ">";

    /**
     * * 大于等于
     */
    private static final String OP_GTE = ">=";

    /**
     * 小于
     */
    private static final String OP_LT = "<";

    /**
     * 小于等于
     */
    private static final String OP_LTE = "<=";

    /**
     * 包含
     */
    private static final String OP_CONTAINS = "contains";

    /**
     * 为空
     */
    private static final String OP_EMPTY = "empty";

    /**
     * 不为空
     */
    private static final String OP_NOT_EMPTY = "notEmpty";

    private final SpiderFlowMapper flowMapper;
    private final SpiderFlowNodeMapper nodeMapper;
    private final SpiderFlowEdgeMapper edgeMapper;
    private final SpiderFlowHistoryMapper historyMapper;
    private final SpiderFlowNodeLogMapper nodeLogMapper;
    private final SpiderExecutionRecordMapper executionMapper;
    private final NodeHandlerRegistry handlerRegistry;

    /**
     * 同步执行指定流程（阻塞当前线程直到流程结束）。
     *
     * @param flowCode 流程编码
     * @return 执行批次号
     */
    @Transactional(rollbackFor = Exception.class)
    public String run(String flowCode) {
        SpiderFlow flow = loadByCode(flowCode);
        if (flow == null) {
            throw new IllegalArgumentException("流程不存在: " + flowCode);
        }
        if (flow.getFlowStatus() != null && flow.getFlowStatus() == 0) {
            throw new IllegalStateException("流程已禁用: " + flowCode);
        }

        Map<String, SpiderFlowNode> nodeMap = loadNodeMap(flow.getFlowId());
        List<SpiderFlowEdge> edges = loadEdges(flow.getFlowId());
        SpiderFlowNode start = findStartNode(nodeMap);
        if (start == null) {
            throw new IllegalStateException("流程没有起始节点: " + flowCode);
        }

        String executionNo = UUID.randomUUID().toString().replace("-", "");
        SpiderExecutionRecord execRecord = new SpiderExecutionRecord();
        execRecord.setSpiderExecutionNo(executionNo);
        execRecord.setSpiderDefinitionCode(flowCode);
        execRecord.setSpiderTriggerType("FLOW");
        execRecord.setSpiderExecutionStatus(SpiderExecutionStatus.RUNNING.getCode());
        execRecord.setSpiderStartedAt(LocalDateTime.now());
        execRecord.setCreateTime(LocalDateTime.now());
        executionMapper.insert(execRecord);

        SpiderFlowContext ctx = SpiderFlowContext.builder()
                .flowCode(flowCode)
                .executionNo(executionNo)
                .vars(new LinkedHashMap<>())
                .build();

        log.info("[Flow {}] 开始执行流程: code={}, node={}, edge={}",
                executionNo, flowCode, nodeMap.size(), edges.size());

        String status = walk(start, nodeMap, edges, ctx);

        // 写执行记录终态
        execRecord.setSpiderExecutionStatus(
                "stopped".equals(status) || ctx.isInterrupted()
                        ? SpiderExecutionStatus.STOPPED.getCode()
                        : SpiderExecutionStatus.SUCCESS.getCode());
        execRecord.setSpiderFinishedAt(LocalDateTime.now());
        execRecord.setSpiderCollectedCount(0);
        execRecord.setSpiderUrlCompleted(0);
        executionMapper.updateById(execRecord);

        return executionNo;
    }

    /**
     * 同步执行 + 测试模式（返回所有节点日志，便于前端调试面板展示）。
     *
     * @param flowCode 流程编码
     * @return 节点执行日志列表（按执行顺序）
     */
    public List<SpiderFlowNodeLog> debugRun(String flowCode) {
        SpiderFlow flow = loadByCode(flowCode);
        if (flow == null) {
            throw new IllegalArgumentException("流程不存在: " + flowCode);
        }
        Map<String, SpiderFlowNode> nodeMap = loadNodeMap(flow.getFlowId());
        List<SpiderFlowEdge> edges = loadEdges(flow.getFlowId());
        SpiderFlowNode start = findStartNode(nodeMap);
        if (start == null) {
            throw new IllegalStateException("流程没有起始节点: " + flowCode);
        }

        String executionNo = "DEBUG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        SpiderFlowContext ctx = SpiderFlowContext.builder()
                .flowCode(flowCode)
                .executionNo(executionNo)
                .vars(new LinkedHashMap<>())
                .build();
        walk(start, nodeMap, edges, ctx);
        // debug 模式只读日志，不清理
        LambdaQueryWrapper<SpiderFlowNodeLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderFlowNodeLog::getFlowExecutionNo, executionNo)
                .orderByAsc(SpiderFlowNodeLog::getFlowNodeLogId);
        return nodeLogMapper.selectList(wrapper);
    }

    /**
     * 加载流程定义。
     */
    private SpiderFlow loadByCode(String flowCode) {
        LambdaQueryWrapper<SpiderFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderFlow::getFlowCode, flowCode);
        wrapper.last("LIMIT 1");
        return flowMapper.selectOne(wrapper);
    }

    /**
     * 加载节点映射（key = flowNodeKey）。
     */
    private Map<String, SpiderFlowNode> loadNodeMap(Long flowId) {
        LambdaQueryWrapper<SpiderFlowNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderFlowNode::getFlowId, flowId);
        List<SpiderFlowNode> list = nodeMapper.selectList(wrapper);
        Map<String, SpiderFlowNode> map = new HashMap<>();
        for (SpiderFlowNode n : list) {
            map.put(n.getFlowNodeKey(), n);
        }
        return map;
    }

    /**
     * 加载所有边。
     */
    private List<SpiderFlowEdge> loadEdges(Long flowId) {
        LambdaQueryWrapper<SpiderFlowEdge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderFlowEdge::getFlowId, flowId);
        return edgeMapper.selectList(wrapper);
    }

    /**
     * 找到 type=start 的节点。
     */
    private SpiderFlowNode findStartNode(Map<String, SpiderFlowNode> nodeMap) {
        for (SpiderFlowNode n : nodeMap.values()) {
            if (TYPE_START.equalsIgnoreCase(n.getFlowNodeType())) {
                return n;
            }
        }
        return null;
    }

    /**
     * 拓扑遍历：从 start 开始执行，按边流转。
     *
     * @param start   起始节点
     * @param nodeMap 节点映射
     * @param edges   所有边
     * @param ctx     流程上下文
     * @return 最终状态描述（"stopped" / "interrupted" / "ended"）
     */
    private String walk(SpiderFlowNode start,
                        Map<String, SpiderFlowNode> nodeMap,
                        List<SpiderFlowEdge> edges,
                        SpiderFlowContext ctx) {
        SpiderFlowNode current = start;
        int executed = 0;
        while (current != null && executed < MAX_NODE_EXECUTIONS) {
            if (ctx.isInterrupted()) {
                log.info("[Flow {}] 流程被中断", ctx.getExecutionNo());
                return "interrupted";
            }
            if (TYPE_STOP.equalsIgnoreCase(current.getFlowNodeType())) {
                executeNode(current, ctx);
                log.info("[Flow {}] 流程到达 STOP 节点", ctx.getExecutionNo());
                return "stopped";
            }
            executeNode(current, ctx);
            executed++;

            // 处理循环节点：自动按 _loop_position 推进
            if (TYPE_LOOP.equalsIgnoreCase(current.getFlowNodeType())) {
                advanceLoop(current, ctx);
            }
            current = nextNode(current, nodeMap, edges, ctx);
        }
        if (executed >= MAX_NODE_EXECUTIONS) {
            log.warn("[Flow {}] 流程达到最大节点执行数 {}", ctx.getExecutionNo(), MAX_NODE_EXECUTIONS);
        }
        return "ended";
    }

    /**
     * 循环节点推进：将 _loop_position +1 并把当前元素 / 索引写入 ctx。
     * 同时把"循环体入口边的目标节点 key"放到 ctx._loopNextNode，
     * 让 {@link #nextNode} 优先取这个目标。
     *
     * @param loopNode 循环节点
     * @param ctx      流程上下文
     */
    @SuppressWarnings("unchecked")
    private void advanceLoop(SpiderFlowNode loopNode, SpiderFlowContext ctx) {
        Object listObj = ctx.getVar(LoopNodeHandler.LOOP_LIST_KEY);
        if (!(listObj instanceof List<?> list)) {
            return;
        }
        int position = ctx.getVar(LoopNodeHandler.LOOP_POSITION_KEY, -1);
        position++;
        ctx.putVar(LoopNodeHandler.LOOP_POSITION_KEY, position);

        if (position >= list.size()) {
            // 循环结束：清理循环状态，让 nextNode 按正常出边选下一节点（loopEnd 边）
            ctx.putVar(LoopNodeHandler.LOOP_LIST_KEY, null);
            ctx.putVar(LoopNodeHandler.LOOP_POSITION_KEY, null);
            ctx.putVar("_loopNextNode", null);
            log.info("[Flow {}] 循环结束: total={}", ctx.getExecutionNo(), list.size());
            return;
        }

        // 把当前元素 / 索引写入 ctx
        Object item = list.get(position);
        String itemVar = asString(getLoopConfigValue(loopNode, ctx, "itemVar", "item"));
        String indexVar = asString(getLoopConfigValue(loopNode, ctx, "indexVar", "index"));
        ctx.putVar(itemVar, item);
        ctx.putVar(indexVar, position);
        // 下一节点 = loop 出边的目标（循环体入口）；流程执行器会在 nextNode 里优先读取此 key
        ctx.putVar("_loopNextNode", "_loopBodyEntry_" + loopNode.getFlowNodeKey());
    }

    /**
     * 根据 from 节点 key 过滤边，按 priority 升序、edgeId 升序排列。
     */
    private List<SpiderFlowEdge> edgesByFrom(List<SpiderFlowEdge> edges, String fromKey) {
        List<SpiderFlowEdge> list = new ArrayList<>();
        for (SpiderFlowEdge e : edges) {
            if (fromKey.equals(e.getFlowEdgeFrom())) {
                list.add(e);
            }
        }
        list.sort(Comparator.comparing(SpiderFlowEdge::getFlowEdgePriority,
                Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SpiderFlowEdge::getFlowEdgeId));
        return list;
    }

    /**
     * 从 loop 节点 config JSON 取出字段值（itemVar / indexVar）。
     */
    private Object getLoopConfigValue(SpiderFlowNode loopNode, SpiderFlowContext ctx, String key, String def) {
        String config = loopNode.getFlowNodeConfig();
        if (StringUtils.isBlank(config)) {
            return def;
        }
        try {
            Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(config, Map.class);
            Object v = map.get(key);
            return v == null ? def : v;
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 求下一节点：按出边顺序（priority 升序）逐条求值，
     * 第一个条件成立的边即为下一跳；无条件边 = 必走；若无出边 = 终止。
     */
    private SpiderFlowNode nextNode(SpiderFlowNode current,
                                    Map<String, SpiderFlowNode> nodeMap,
                                    List<SpiderFlowEdge> edges,
                                    SpiderFlowContext ctx) {
        // 循环节点推进：如果循环还没结束，下一节点 = 循环体入口
        Object loopNext = ctx.getVar("_loopNextNode");
        if (loopNext != null) {
            ctx.putVar("_loopNextNode", null);
            // 第一次进入循环：找到循环节点 priority 最小的出边的目标
            List<SpiderFlowEdge> outEdges = edgesByFrom(edges, current.getFlowNodeKey());
            if (!outEdges.isEmpty()) {
                return nodeMap.get(outEdges.get(0).getFlowEdgeTo());
            }
        }
        List<SpiderFlowEdge> outEdges = edgesByFrom(edges, current.getFlowNodeKey());
        if (outEdges.isEmpty()) {
            return null;
        }
        for (SpiderFlowEdge edge : outEdges) {
            if (evalCondition(edge, ctx)) {
                SpiderFlowNode next = nodeMap.get(edge.getFlowEdgeTo());
                if (next != null) {
                    return next;
                }
            }
        }
        return null;
    }

    /**
     * 求值单条边的条件。
     */
    private boolean evalCondition(SpiderFlowEdge edge, SpiderFlowContext ctx) {
        String field = edge.getFlowEdgeCondField();
        if (StringUtils.isBlank(field)) {
            return true;
        }
        String op = edge.getFlowEdgeCondOp();
        String value = edge.getFlowEdgeCondValue();
        Object actual = ctx.getVar(field);

        if (op == null) {
            return true;
        }
        switch (op) {
            case OP_EQ:
                return equalsString(actual, value);
            case OP_NEQ:
                return !equalsString(actual, value);
            case OP_GT:
                return compareNumber(actual, value) > 0;
            case OP_GTE:
                return compareNumber(actual, value) >= 0;
            case OP_LT:
                return compareNumber(actual, value) < 0;
            case OP_LTE:
                return compareNumber(actual, value) <= 0;
            case OP_CONTAINS:
                return containsString(actual, value);
            case OP_EMPTY:
                return actual == null || actual.toString().isEmpty();
            case OP_NOT_EMPTY:
                return actual != null && !actual.toString().isEmpty();
            default:
                return true;
        }
    }

    /**
     * 字符串相等比较（避免 NPE）。
     */
    private boolean equalsString(Object actual, String expected) {
        if (actual == null) {
            return expected == null || expected.isEmpty();
        }
        return actual.toString().equals(expected);
    }

    /**
     * 字符串包含比较。
     */
    private boolean containsString(Object actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return actual.toString().contains(expected);
    }

    /**
     * 数字比较（实际 vs 期望，期望按 double 解析）。
     */
    private int compareNumber(Object actual, String expected) {
        Double a = toDouble(actual);
        Double b = toDouble(expected);
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return Double.compare(a, b);
    }

    /**
     * 转 Double（失败返回 null）。
     */
    private Double toDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Object 转 String（避免 NPE）。
     */
    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * 执行单个节点并写日志。
     */
    private void executeNode(SpiderFlowNode node, SpiderFlowContext ctx) {
        ctx.setCurrentNodeKey(node.getFlowNodeKey());
        NodeHandler handler = handlerRegistry.get(node.getFlowNodeType());
        if (handler == null) {
            log.warn("[Flow {}] 节点 {} 类型 {} 无处理器，跳过",
                    ctx.getExecutionNo(), node.getFlowNodeKey(), node.getFlowNodeType());
            writeNodeLog(ctx, node, "SKIPPED", "no handler", 0L);
            return;
        }
        long start = System.currentTimeMillis();
        String status = "SUCCESS";
        String message;
        try {
            message = handler.execute(node, ctx);
        } catch (Throwable t) {
            status = "FAILED";
            message = t.getClass().getSimpleName() + ": " + t.getMessage();
            log.error("[Flow {}] 节点 {} 执行失败: {}", ctx.getExecutionNo(), node.getFlowNodeKey(), t);
            // 不中断流程，继续走后续节点（与 spider-flow 一致）
        }
        long cost = System.currentTimeMillis() - start;
        writeNodeLog(ctx, node, status, message, cost);
    }

    /**
     * 写节点执行日志。
     */
    private void writeNodeLog(SpiderFlowContext ctx, SpiderFlowNode node, String status, String message, long costMs) {
        try {
            SpiderFlowNodeLog entry = new SpiderFlowNodeLog();
            entry.setFlowExecutionNo(ctx.getExecutionNo());
            entry.setFlowId(node.getFlowId());
            entry.setFlowNodeKey(node.getFlowNodeKey());
            entry.setFlowNodeType(node.getFlowNodeType());
            entry.setFlowNodeStatus(status);
            entry.setFlowNodeMessage(truncate(message, 65000));
            entry.setFlowNodeCostMs(costMs);
            entry.setCreateTime(LocalDateTime.now());
            nodeLogMapper.insert(entry);
        } catch (Exception ex) {
            log.warn("[Flow {}] 节点日志写入失败: {}", ctx.getExecutionNo(), ex.getMessage());
        }
    }

    /**
     * 字符串截断（避免超长入库失败）。
     */
    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
