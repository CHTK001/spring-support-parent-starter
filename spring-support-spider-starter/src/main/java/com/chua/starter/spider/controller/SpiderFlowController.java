package com.chua.starter.spider.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.entity.SpiderFlow;
import com.chua.starter.spider.entity.SpiderFlowHistory;
import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.entity.SpiderFlowNodeLog;
import com.chua.starter.spider.flow.NodeHandlerRegistry;
import com.chua.starter.spider.flow.SpiderFlowExecutionService;
import com.chua.starter.spider.mapper.SpiderFlowHistoryMapper;
import com.chua.starter.spider.mapper.SpiderFlowNodeLogMapper;
import com.chua.starter.spider.mapper.SpiderFlowNodeMapper;
import com.chua.starter.spider.service.SpiderFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 流程定义 Controller（RelationGraph 编排的 REST API）。
 *
 * <p>端点：</p>
 * <ul>
 *   <li>{@code POST /spider/flows/save} 保存/更新整图（含节点 + 边）</li>
 *   <li>{@code GET  /spider/flows/page} 分页查询</li>
 *   <li>{@code GET  /spider/flows/detail} 完整加载（节点 + 边）</li>
 *   <li>{@code POST /spider/flows/status} 启停切换</li>
 *   <li>{@code DELETE /spider/flows/{code}} 删除</li>
 *   <li>{@code POST /spider/flows/run} 同步执行（返回 executionNo）</li>
 *   <li>{@code POST /spider/flows/debug} 调试执行（返回所有节点日志）</li>
 *   <li>{@code GET  /spider/flows/history} 历史版本</li>
 *   <li>{@code POST /spider/flows/rollback} 回滚到指定版本</li>
 *   <li>{@code GET  /spider/flows/node-types} 已注册节点类型清单</li>
 *   <li>{@code GET  /spider/flows/node-logs} 节点执行日志</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/spider/flows")
@RequiredArgsConstructor
@Tag(name = "爬虫流程定义 - RelationGraph 编排")
public class SpiderFlowController {

    private final SpiderFlowService flowService;
    private final SpiderFlowExecutionService executionService;
    private final NodeHandlerRegistry handlerRegistry;
    private final SpiderFlowNodeMapper nodeMapper;
    private final SpiderFlowHistoryMapper historyMapper;
    private final SpiderFlowNodeLogMapper nodeLogMapper;

    /**
     * 分页查询流程列表。
     */
    @Operation(summary = "分页查询流程列表")
    @GetMapping("/page")
    public ReturnResult<Page<SpiderFlow>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ReturnResult.ok(flowService.pageFlows(pageNo, pageSize, keyword));
    }

    /**
     * 完整加载一个流程（含节点 + 边）。
     */
    @Operation(summary = "完整加载流程（节点 + 边）")
    @GetMapping("/detail")
    public ReturnResult<SpiderFlowService.SpiderFlowAggregate> detail(@RequestParam String flowCode) {
        SpiderFlowService.SpiderFlowAggregate agg = flowService.getByCode(flowCode);
        if (agg == null) {
            return ReturnResult.error("流程不存在: " + flowCode);
        }
        return ReturnResult.ok(agg);
    }

    /**
     * 保存/更新完整流程（前端 RelationGraph 调用）。
     */
    @Operation(summary = "保存完整流程（含节点 + 边 + graphJson）")
    @PostMapping("/save")
    public ReturnResult<Integer> save(
            @RequestBody SpiderFlowService.SpiderFlowAggregate aggregate,
            @Parameter(description = "版本变更说明") @RequestParam(required = false) String changeNote,
            @Parameter(description = "修改人") @RequestParam(required = false) String changeUser) {
        try {
            int version = flowService.save(aggregate, changeNote, changeUser);
            return ReturnResult.ok(version);
        } catch (Exception e) {
            log.error("[Flow] 保存失败: {}", e.getMessage(), e);
            return ReturnResult.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 启停切换。
     */
    @Operation(summary = "启停切换")
    @PostMapping("/status")
    public ReturnResult<Boolean> toggle(@RequestParam String flowCode, @RequestParam int status) {
        return ReturnResult.ok(flowService.toggle(flowCode, status));
    }

    /**
     * 删除流程（级联）。
     */
    @Operation(summary = "删除流程")
    @DeleteMapping("/{flowCode}")
    public ReturnResult<Boolean> delete(@PathVariable String flowCode) {
        return ReturnResult.ok(flowService.delete(flowCode));
    }

    /**
     * 同步执行流程（阻塞当前线程）。
     */
    @Operation(summary = "同步执行流程")
    @PostMapping("/run")
    public ReturnResult<String> run(@RequestParam String flowCode) {
        try {
            String executionNo = executionService.run(flowCode);
            return ReturnResult.ok(executionNo);
        } catch (Exception e) {
            log.error("[Flow] 执行失败: {}", e.getMessage(), e);
            return ReturnResult.error("执行失败: " + e.getMessage());
        }
    }

    /**
     * 调试执行：返回所有节点日志供前端回放。
     */
    @Operation(summary = "调试执行（返回所有节点日志）")
    @PostMapping("/debug")
    public ReturnResult<List<SpiderFlowNodeLog>> debug(@RequestParam String flowCode) {
        try {
            return ReturnResult.ok(executionService.debugRun(flowCode));
        } catch (Exception e) {
            log.error("[Flow] 调试执行失败: {}", e.getMessage(), e);
            return ReturnResult.error("调试执行失败: " + e.getMessage());
        }
    }

    /**
     * 历史版本列表。
     */
    @Operation(summary = "历史版本列表")
    @GetMapping("/history")
    public ReturnResult<List<SpiderFlowHistory>> history(@RequestParam String flowCode) {
        return ReturnResult.ok(flowService.listHistory(flowCode));
    }

    /**
     * 回滚到指定版本。
     */
    @Operation(summary = "回滚到指定版本")
    @PostMapping("/rollback")
    public ReturnResult<Integer> rollback(@RequestParam String flowCode, @RequestParam int version) {
        try {
            return ReturnResult.ok(flowService.rollback(flowCode, version));
        } catch (Exception e) {
            log.error("[Flow] 回滚失败: {}", e.getMessage(), e);
            return ReturnResult.error("回滚失败: " + e.getMessage());
        }
    }

    /**
     * 已注册的节点类型清单（前端用来画"节点面板"）。
     */
    @Operation(summary = "已注册的节点类型清单")
    @GetMapping("/node-types")
    public ReturnResult<Map<String, Object>> nodeTypes() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        handlerRegistry.all().forEach((type, handler) -> {
            map.put(type, Map.of(
                    "type", type,
                    "className", handler.getClass().getSimpleName()
            ));
        });
        return ReturnResult.ok(map);
    }

    /**
     * 按 executionNo 查询节点执行日志。
     */
    @Operation(summary = "查询节点执行日志")
    @GetMapping("/node-logs")
    public ReturnResult<List<SpiderFlowNodeLog>> nodeLogs(
            @RequestParam(required = false) String executionNo,
            @RequestParam(required = false) Long flowId) {
        LambdaQueryWrapper<SpiderFlowNodeLog> wrapper = new LambdaQueryWrapper<>();
        if (executionNo != null) {
            wrapper.eq(SpiderFlowNodeLog::getFlowExecutionNo, executionNo);
        }
        if (flowId != null) {
            wrapper.eq(SpiderFlowNodeLog::getFlowId, flowId);
        }
        wrapper.orderByAsc(SpiderFlowNodeLog::getFlowNodeLogId);
        if (executionNo == null && flowId == null) {
            wrapper.last("LIMIT 200");
        }
        return ReturnResult.ok(nodeLogMapper.selectList(wrapper));
    }

    /**
     * 按 flowId 查询节点列表（用于详情面板）。
     */
    @Operation(summary = "查询流程节点列表")
    @GetMapping("/nodes")
    public ReturnResult<List<SpiderFlowNode>> nodes(@RequestParam Long flowId) {
        LambdaQueryWrapper<SpiderFlowNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderFlowNode::getFlowId, flowId);
        return ReturnResult.ok(nodeMapper.selectList(wrapper));
    }
}
