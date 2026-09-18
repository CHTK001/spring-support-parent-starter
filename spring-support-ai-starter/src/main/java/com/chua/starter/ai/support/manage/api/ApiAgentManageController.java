package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.SysAiAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* 智能体 管理控制器（主 智能体 / 子 智能体 的 CRUD 与编排）。
*
* <p>对应前端「Agent 管理」页：新增/编辑 Agent，配置模型（provider + model）、
* MCP、Skill、系统提示符 与策略；主从编排图（X6）保存/加载；
* 以及将 智能体 注册为模型（智能体-as-模型）供对话链路选择。</p>
*
* <p>数据持久化到 {@code sys_ai_agent} 表，运行时经 {@link SysAiAgentService}
* 动态构建并注册到 {@code AgentRegistry}。</p>
*
* @since 4.0.0.42
* @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/agents/managed")
@RequiredArgsConstructor
@Slf4j
public class ApiAgentManageController {

    /**
    * 智能体 管理服务
    */
    private final SysAiAgentService agentService;

    /**
    * 列举全部主 智能体（含子 智能体）。
    *
    * @return 主 智能体 列表
    */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> listAgents() {
        return ApiResult.ok(agentService.listAgents());
    }

    /**
    * 获取单个 智能体 详情（含子 智能体）。
    *
    * @param agentId 智能体 标识
    * @return Agent 详情
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    * @param @PathVariable("agentId" @路径变量("智能体id"
    * @param @PathVariable("agentId" @PathVariable("agentId"
    */
    @GetMapping("/{agentId}")
    public ApiResult<Map<String, Object>> getAgent(@PathVariable("agentId") String agentId) {
        return ApiResult.ok(agentService.getAgent(agentId));
    }

    /**
    * 创建主 智能体。
    *
    * @param body 创建请求
    * @return 创建后的 智能体
    */
    @PostMapping
    public ApiResult<Map<String, Object>> createAgent(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(agentService.createAgent(body));
    }

    /**
    * 更新主 智能体。
    *
    * @param agentId 智能体 标识
    * @param body    更新请求
    * @return 更新后的 智能体
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    */
    @PutMapping("/{agentId}")
    public ApiResult<Map<String, Object>> updateAgent(@PathVariable("agentId") String agentId,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResult.ok(agentService.updateAgent(agentId, body));
    }

    /**
    * 删除主 智能体（级联删除子 智能体）。
    *
    * @param agentId 智能体 标识
    * @return 成功
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    * @param @PathVariable("agentId" @路径变量("智能体id"
    * @param @PathVariable("agentId" @PathVariable("agentId"
    */
    @DeleteMapping("/{agentId}")
    public ApiResult<Void> deleteAgent(@PathVariable("agentId") String agentId) {
        agentService.deleteAgent(agentId);
        return ApiResult.ok();
    }

    /**
    * 创建子 智能体。
    *
    * @param agentId 主 智能体 标识
    * @param body    子 智能体 请求
    * @return 创建后的子 智能体
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    */
    @PostMapping("/{agentId}/sub-agents")
    public ApiResult<Map<String, Object>> createSubAgent(@PathVariable("agentId") String agentId,
                                                          @RequestBody Map<String, Object> body) {
        return ApiResult.ok(agentService.createSubAgent(agentId, body));
    }

    /**
    * 更新子 智能体。
    *
    * @param agentId 主 智能体 标识
    * @param subId   子 智能体 标识
    * @param body    更新请求
    * @return 更新后的子 智能体
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    */
    @PutMapping("/{agentId}/sub-agents/{subId}")
    public ApiResult<Map<String, Object>> updateSubAgent(@PathVariable("agentId") String agentId,
                                                          @PathVariable("subId") String subId,
                                                          @RequestBody Map<String, Object> body) {
        return ApiResult.ok(agentService.updateSubAgent(agentId, subId, body));
    }

    /**
    * 删除子 智能体。
    *
    * @param agentId 主 智能体 标识
    * @param subId   子 智能体 标识
    * @return 成功
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    */
    @DeleteMapping("/{agentId}/sub-agents/{subId}")
    public ApiResult<Void> deleteSubAgent(@PathVariable("agentId") String agentId,
                                           @PathVariable("subId") String subId) {
        agentService.deleteSubAgent(agentId, subId);
        return ApiResult.ok();
    }

    /**
    * 保存编排图（节点 + 连线）。
    *
    * @param agentId 智能体 标识
    * @param body    { 节点, edges }
    * @return 保存结果
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    */
    @PostMapping("/{agentId}/orchestration")
    public ApiResult<Map<String, Object>> saveOrchestration(@PathVariable("agentId") String agentId,
                                                             @RequestBody Map<String, Object> body) {
        return ApiResult.ok(agentService.saveOrchestration(agentId, body));
    }

    /**
    * 加载编排图。
    *
    * @param agentId 智能体 标识
    * @return { 节点, edges, mode }
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    * @param @PathVariable("agentId" @路径变量("智能体id"
    * @param @PathVariable("agentId" @PathVariable("agentId"
    */
    @GetMapping("/{agentId}/orchestration")
    public ApiResult<Map<String, Object>> getOrchestration(@PathVariable("agentId") String agentId) {
        return ApiResult.ok(agentService.getOrchestration(agentId));
    }

    /**
    * 按编排图串行运行子 Agent（路由模式）。
    *
    * @param agentId 智能体 标识
    * @param body    { input }
    * @return 各子 Agent 输出与最终汇总
    */
    @PostMapping("/{agentId}/orchestration/run")
    public ApiResult<Map<String, Object>> runOrchestration(@PathVariable("agentId") String agentId,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        String input = body != null && body.get("input") != null ? body.get("input").toString() : "";
        return ApiResult.ok(agentService.runOrchestration(agentId, input));
    }

    /**
    * 注册为模型（智能体-as-模型）。
    *
    * @param agentId   智能体 标识
    * @param modelName 模型名（可选）
    * @return 注册结果
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    */
    @PostMapping("/{agentId}/register")
    public ApiResult<Map<String, Object>> registerAsModel(@PathVariable("agentId") String agentId,
                                                            @RequestParam(required = false) String model_name) {
        return ApiResult.ok(agentService.registerAsModel(agentId, model_name));
    }

    /**
    * 取消注册模型。
    *
    * @param agentId 智能体 标识
    * @return 成功
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    * @param @PathVariable("agentId" @路径变量("智能体id"
    * @param @PathVariable("agentId" @PathVariable("agentId"
    */
    @DeleteMapping("/{agentId}/register")
    public ApiResult<Void> unregisterModel(@PathVariable("agentId") String agentId) {
        agentService.unregisterModel(agentId);
        return ApiResult.ok();
    }

    /**
    * 查询 智能体 注册模型状态。
    *
    * @param agentId 智能体 标识
    * @return { 注册, 模型_名称 }
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    * @param @PathVariable("agentId" @路径变量("智能体id"
    * @param @PathVariable("agentId" @PathVariable("agentId"
    */
    @GetMapping("/{agentId}/registered-model")
    public ApiResult<Map<String, Object>> getRegisteredModel(@PathVariable("agentId") String agentId) {
        return ApiResult.ok(agentService.getRegisteredModelInfo(agentId));
    }

    /**
    * 获取所有已注册为模型的 智能体 列表（供模型管理页展示）。
    *
    * @return { 模型_名称, 智能体_名称, 智能体_标识 } 列表
    */
    @GetMapping("/registered-models")
    public ApiResult<List<Map<String, Object>>> listRegisteredModels() {
        Set<String> names = agentService.getRegisteredAgentModelNames();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : names) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("model_name", name);
 // 通过 智能体 名称反查 智能体_id（名称格式为 智能体-<name>）
            entry.put("agent_name", name.startsWith("agent-") ? name.substring(6) : name);
            result.add(entry);
        }
        return ApiResult.ok(result);
    }

    /**
    * 重建运行时 智能体（编排保存后热更新）。
    *
    * @param agentId 智能体 标识
    * @return 成功
    * @param @PathVariable("agentId" @路径变量("智能体标识"
    * @param @PathVariable("agentId" @路径变量("智能体id"
    * @param @PathVariable("agentId" @PathVariable("agentId"
    */
    @PostMapping("/{agentId}/rebuild")
    public ApiResult<Void> rebuild(@PathVariable("agentId") String agentId) {
        agentService.rebuildAgentById(agentId);
        return ApiResult.ok();
    }
}
