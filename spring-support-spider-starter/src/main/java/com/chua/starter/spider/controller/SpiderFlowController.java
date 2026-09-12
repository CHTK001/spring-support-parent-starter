package com.chua.starter.spider.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.task.flow.FlowNodeMetadata;
import com.chua.flow.support.FlowEngine;
import com.chua.starter.spider.dto.FlowAggregateDTO;
import com.chua.starter.spider.dto.FlowEdgeEntityVO;
import com.chua.starter.spider.dto.FlowNodeEntityVO;
import com.chua.starter.spider.dto.FlowNodeLogVO;
import com.chua.starter.spider.dto.PageResultVO;
import com.chua.starter.spider.dto.SpiderConverter;
import com.chua.starter.spider.dto.SpiderFlowVO;
import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.entity.SpiderExecutionLog;
import com.chua.starter.spider.entity.SpiderFlow;
import com.chua.starter.spider.execute.SpiderFlowRunner;
import com.chua.starter.spider.service.SpiderDefinitionService;
import com.chua.starter.spider.service.SpiderExecutionLogService;
import com.chua.starter.spider.service.SpiderFlowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* 流程编排管理接口。
*
* <p>提供流程编排的分页/详情/保存/启停/删除/执行/调试/历史/回滚等能力，
* 路径前缀 {@code /spider/flows}，对齐前端 蜘蛛api。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Tag(name = "流程编排管理")
@RestController
@RequestMapping("${spider.path:/spider}/flows")
public class SpiderFlowController {

    /**
    * JSON 解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
    * 开始节点类型标识
     */
    private static final String NODE_TYPE_START = "start";

    /**
    * 结束节点类型标识
     */
    private static final String NODE_TYPE_END = "stop";

    /**
    * 支持的节点类型集合（与执行器映射保持一致）
     */
    private static final Set<String> SUPPORTED_NODE_TYPES = Set.of(
            "start", "stop", "request", "extract", "loop", "output",
            "transform", "condition", "map", "script", "db", "function");

    /**
    * 流程编排服务
     */
    private final SpiderFlowService flowService;

    /**
    * 爬虫定义服务
     */
    private final SpiderDefinitionService definitionService;

    /**
    * 流程执行器
     */
    private final SpiderFlowRunner flowRunner;

    /**
    * 执行日志服务
     */
    private final SpiderExecutionLogService executionLogService;

    /**
    * 构造方法。
    *
    * @param flowService         流程编排服务
    * @param definitionService   爬虫定义服务
    * @param flowRunner          流程执行器
    * @param executionLogService 执行日志服务
     */
    public SpiderFlowController(SpiderFlowService flowService,
                                SpiderDefinitionService definitionService,
                                SpiderFlowRunner flowRunner,
                                SpiderExecutionLogService executionLogService) {
        this.flowService = flowService;
        this.definitionService = definitionService;
        this.flowRunner = flowRunner;
        this.executionLogService = executionLogService;
    }

    /**
    * 流程分页列表。
    *
    * @param pageNo   页码
    * @param pageSize 每页条数
    * @param keyword  关键字（编码/名称）
    * @return 分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "流程分页列表")
    public ReturnResult<PageResultVO<SpiderFlowVO>> page(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword) {
        IPage<SpiderFlow> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SpiderFlow> wrapper =
                new LambdaQueryWrapper<SpiderFlow>()
                        .orderByDesc(SpiderFlow::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SpiderFlow::getCode, keyword)
                    .or().like(SpiderFlow::getName, keyword));
        }
        IPage<SpiderFlow> result = flowService.page(page, wrapper);
        PageResultVO<SpiderFlowVO> vo = new PageResultVO<>();
        vo.setPageNo(result.getCurrent());
        vo.setPageSize(result.getSize());
        vo.setTotal(result.getTotal());
        vo.setTotalPages(result.getPages());
        List<SpiderFlowVO> list = new ArrayList<>();
        for (SpiderFlow flow : result.getRecords()) {
            list.add(SpiderConverter.toFlowVO(flow));
        }
        vo.setRecords(list);
        vo.setData(list);
        return ReturnResult.ok(vo);
    }

    /**
    * 完整加载流程（节点 + 边）。
    *
    * @param flowCode 流程编码
    * @return 流程聚合
     */
    @GetMapping("/detail")
    @Operation(summary = "完整加载流程")
    public ReturnResult<FlowAggregateDTO> detail(@RequestParam String flowCode) {
        SpiderFlow flow = findByCode(flowCode);
        if (flow == null) {
            return ReturnResult.ok(null);
        }
        FlowAggregateDTO dto = new FlowAggregateDTO();
        dto.setFlow(SpiderConverter.toFlowVO(flow));
        dto.setNodes(parseNodeEntities(flow.getNodesJson()));
        dto.setEdges(parseEdgeEntities(flow.getEdgesJson()));
        return ReturnResult.ok(dto);
    }

    /**
    * 保存流程（含节点 + 边）。
    *
    * @param aggregate 流程聚合
    * @return 保存成功返回流程版本号
     */
    @PostMapping("/save")
    @Operation(summary = "保存流程")
    public ReturnResult<Integer> save(@RequestBody FlowAggregateDTO aggregate) {
        if (aggregate == null || aggregate.getFlow() == null) {
            return ReturnResult.error("流程信息不能为空");
        }
        ReturnResult<Integer> validate = validateFlow(aggregate.getFlow().getFlowCode(),
                aggregate.getNodes(), aggregate.getEdges());
        if (!validate.isOk()) {
            return validate;
        }
        SpiderFlow flow = findByCode(aggregate.getFlow().getFlowCode());
        if (flow == null) {
            flow = new SpiderFlow();
            flow.setCode(aggregate.getFlow().getFlowCode());
            flow.setEnabled(1);
            SpiderDefinition definition = definitionService.lambdaQuery()
                    .eq(SpiderDefinition::getCode, aggregate.getFlow().getFlowCode()).one();
            if (definition != null) {
                flow.setDefinitionId(definition.getId());
            }
        }
        flow.setName(aggregate.getFlow().getFlowName());
        flow.setNodesJson(serializeNodes(aggregate.getNodes()));
        flow.setEdgesJson(serializeEdges(aggregate.getEdges()));
        if (flow.getId() == null) {
            flowService.save(flow);
        } else {
            flowService.updateById(flow);
        }
        return ReturnResult.ok(1);
    }

    /**
    * 启停切换。
    *
    * @param flowCode 流程编码
    * @param status   目标状态
    * @return 是否成功
     */
    @PostMapping("/status")
    @Operation(summary = "启用/禁用流程")
    public ReturnResult<Boolean> status(@RequestParam String flowCode,
                                        @RequestParam Integer status) {
        SpiderFlow flow = findByCode(flowCode);
        if (flow == null) {
            return ReturnResult.ok(false);
        }
        SpiderFlow update = new SpiderFlow();
        update.setId(flow.getId());
        update.setEnabled(status);
        return ReturnResult.ok(flowService.updateById(update));
    }

    /**
    * 按编码删除流程。
    *
    * @param flowCode 流程编码
    * @return 是否成功
     */
    @DeleteMapping("/{flowCode}")
    @Operation(summary = "删除流程")
    public ReturnResult<Boolean> remove(@PathVariable String flowCode) {
        SpiderFlow flow = findByCode(flowCode);
        if (flow == null) {
            return ReturnResult.ok(false);
        }
        return ReturnResult.ok(flowService.removeById(flow.getId()));
    }

    /**
    * 执行流程（同步执行，返回 执行no）。
    *
    * @param flowCode 流程编码
    * @return 执行号
     */
    @PostMapping("/run")
    @Operation(summary = "执行流程")
    public ReturnResult<String> run(@RequestParam String flowCode) {
        SpiderFlow flow = findByCode(flowCode);
        if (flow == null) {
            return ReturnResult.error("流程不存在: " + flowCode);
        }
        SpiderDefinition definition = definitionService.getById(flow.getDefinitionId());
        if (definition == null) {
            definition = new SpiderDefinition();
            definition.setId(flow.getDefinitionId());
            definition.setCode(flow.getCode());
        }
        return ReturnResult.ok(flowRunner.run(definition));
    }

    /**
    * 调试执行（返回节点级执行日志）。
    *
    * @param flowCode 流程编码
    * @return 节点日志列表
     */
    @PostMapping("/debug")
    @Operation(summary = "调试执行流程")
    public ReturnResult<List<FlowNodeLogVO>> debug(@RequestParam String flowCode) {
        ReturnResult<String> runResult = run(flowCode);
        if (!runResult.isOk()) {
            return ReturnResult.error(runResult.getMsg());
        }
        String executionNo = runResult.getData();
        return ReturnResult.ok(SpiderConverter.toNodeLogVOList(
                executionLogService.listByExecutionNo(executionNo)));
    }

    /**
    * 流程历史（简化：返回当前版本记录）。
    *
    * @param flowCode 流程编码
    * @return 历史快照列表
     */
    @GetMapping("/history")
    @Operation(summary = "流程历史版本")
    public ReturnResult<List<Map<String, Object>>> history(@RequestParam String flowCode) {
        SpiderFlow flow = findByCode(flowCode);
        if (flow == null) {
            return ReturnResult.ok(List.of());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("flowHistoryId", flow.getId());
        item.put("flowId", flow.getId());
        item.put("flowVersion", 1);
        item.put("flowChangeNote", "初始版本");
        item.put("flowChangeUser", "system");
        item.put("createTime", flow.getCreateTime());
        result.add(item);
        return ReturnResult.ok(result);
    }

    /**
    * 回滚流程版本（当前仅单版本，返回当前版本号）。
    *
    * @param flowCode 流程编码
    * @param version  目标版本
    * @return 当前版本号
     */
    @PostMapping("/rollback")
    @Operation(summary = "回滚流程版本")
    public ReturnResult<Integer> rollback(@RequestParam String flowCode,
                                          @RequestParam Integer version) {
        return ReturnResult.ok(1);
    }

    /**
    * 节点类型清单。
    *
    * @return 节点类型元信息
     */
    @GetMapping("/node-types")
    @Operation(summary = "节点类型清单")
    public ReturnResult<List<FlowNodeMetadata>> nodeTypes() {
        return ReturnResult.ok(FlowEngine.listNodeTypes());
    }

    /**
    * 节点执行日志（按 执行no 回放）。
    *
    * @param executionNo 执行号，可空
    * @param flowId      流程 标识，可空
    * @return 节点日志列表
     */
    @GetMapping("/node-logs")
    @Operation(summary = "节点执行日志")
    public ReturnResult<List<FlowNodeLogVO>> nodeLogs(
            @RequestParam(required = false) String executionNo,
            @RequestParam(required = false) Long flowId) {
        if (StringUtils.hasText(executionNo)) {
            return ReturnResult.ok(SpiderConverter.toNodeLogVOList(
                    executionLogService.listByExecutionNo(executionNo)));
        }
        return ReturnResult.ok(List.of());
    }

    /**
    * 校验流程结构合法性。
    *
    * <p>检查规则：</p>
    * <ul>
    *   <li>流程编码非空</li>
    *   <li>节点列表非空</li>
    *   <li>恰好存在一个开始节点</li>
    *   <li>节点类型均在支持集合内</li>
    *   <li>每条连线的起点与终点都必须是已存在的节点 key</li>
    *   <li>至少存在一个结束节点</li>
    * </ul>
    *
    * @param flowCode 流程编码
    * @param nodes    节点列表
    * @param edges    连线列表
    * @return 校验通过返回成功；否则返回错误信息
     */
    private ReturnResult<Integer> validateFlow(String flowCode,
                                               List<FlowNodeEntityVO> nodes,
                                               List<FlowEdgeEntityVO> edges) {
        if (!StringUtils.hasText(flowCode)) {
            return ReturnResult.error("流程编码不能为空");
        }
        List<FlowNodeEntityVO> nodeList = nodes != null ? nodes : List.of();
        if (nodeList.isEmpty()) {
            return ReturnResult.error("流程至少需要一个开始节点");
        }
        int startCount = 0;
        int endCount = 0;
        Set<String> nodeKeys = new HashSet<>();
        for (FlowNodeEntityVO node : nodeList) {
            String key = node.getFlowNodeKey();
            String type = node.getFlowNodeType();
            if (!StringUtils.hasText(key)) {
                return ReturnResult.error("存在无 key 的节点");
            }
            if (nodeKeys.contains(key)) {
                return ReturnResult.error("存在重复的节点 key: " + key);
            }
            nodeKeys.add(key);
            if (NODE_TYPE_START.equals(type)) {
                startCount++;
            } else if (NODE_TYPE_END.equals(type)) {
                endCount++;
            }
            if (!SUPPORTED_NODE_TYPES.contains(type)) {
                return ReturnResult.error("不支持的节点类型: " + type);
            }
        }
        if (startCount != 1) {
            return ReturnResult.error("流程必须且仅有一个开始节点，当前数量: " + startCount);
        }
        if (endCount == 0) {
            return ReturnResult.error("流程至少需要一个结束节点");
        }
        List<FlowEdgeEntityVO> edgeList = edges != null ? edges : List.of();
        for (FlowEdgeEntityVO edge : edgeList) {
            String from = edge.getFlowEdgeFrom();
            String to = edge.getFlowEdgeTo();
            if (!nodeKeys.contains(from)) {
                return ReturnResult.error("连线起点不存在该节点: " + from);
            }
            if (!nodeKeys.contains(to)) {
                return ReturnResult.error("连线终点不存在该节点: " + to);
            }
        }
        return ReturnResult.ok(1);
    }

    /**
    * 按编码查询流程。
    *
    * @param flowCode 流程编码
    * @return 流程实体
     */
    private SpiderFlow findByCode(String flowCode) {
        if (!StringUtils.hasText(flowCode)) {
            return null;
        }
        return flowService.lambdaQuery().eq(SpiderFlow::getCode, flowCode).one();
    }

    /**
    * 序列化节点列表为 JSON 数组。
    *
    * @param nodes 节点视图列表
    * @return JSON 字符串
     */
    private String serializeNodes(List<FlowNodeEntityVO> nodes) {
        if (nodes == null) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(nodes);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
    * 序列化连线列表为 JSON 数组。
    *
    * @param edges 连线视图列表
    * @return JSON 字符串
     */
    private String serializeEdges(List<FlowEdgeEntityVO> edges) {
        if (edges == null) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(edges);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
    * 解析节点 JSON 数组为视图列表。
    *
    * @param json JSON 数组字符串
    * @return 节点视图列表
     */
    private List<FlowNodeEntityVO> parseNodeEntities(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<FlowNodeEntityVO>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
    * 解析连线 JSON 数组为视图列表。
    *
    * @param json JSON 数组字符串
    * @return 连线视图列表
     */
    private List<FlowEdgeEntityVO> parseEdgeEntities(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<FlowEdgeEntityVO>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}