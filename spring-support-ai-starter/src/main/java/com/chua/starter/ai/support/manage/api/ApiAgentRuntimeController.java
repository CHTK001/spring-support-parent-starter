package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.configuration.agent.AgentRuntimeProfile;
import com.chua.starter.ai.support.registry.AgentManager;
import com.chua.starter.ai.support.registry.AgentRegistry;
import com.chua.starter.ai.support.service.SysAiAgentService;
import com.chua.starter.ai.support.manage.store.SysAiAgent;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 运行时 Agent 运维控制器：按 id 查看、创建、停止、销毁正在运行的 Agent。
 *
 * <p>与 {@code /api/agents/managed} 的分工：那个控制器管的是「库里怎么配」，
 * 这个管的是「现在跑着什么、要不要停」。两者按同一个 {@code agentId} 关联，
 * 运行时侧的数据来自 {@link AgentManager}。</p>
 *
 * <p>停止（interrupt）是给运维用的：三个 Agent 实现都会把当前这一轮真的收掉
 * （opencode 关掉它起的 CLI 子进程，agentscope 关闭当前 Harness，langchain4j
 * 在循环点退出），实例本身仍可继续用。销毁（DELETE）则连实例一起关掉。</p>
 *
 * @since 4.0.0.48
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/agents/runtime")
@Permission("admin")
@RequiredArgsConstructor
@Slf4j
public class ApiAgentRuntimeController {

    /**
     * 运行时 Agent 管理器
     */
    private final AgentManager agentManager;

    /**
     * 当前 managed Agent 的配置来源。
     */
    private final SysAiAgentService agentService;

    /**
     * Agent 注册表：按名称解析已注册的运行时 Agent
     */
    private final AgentRegistry agentRegistry;

    /**
     * 列出当前全部托管的运行时 Agent。
     *
     * @return 状态列表（id、名称、实现、是否已中断、连续重复次数、最近一次工具等）
     */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(agentManager.snapshot());
    }

    /**
     * 按 id 查看单个运行时 Agent 的状态。
     *
     * @param agentId Agent 标识
     * @return 状态
     */
    @GetMapping("/{agentId}")
    public ApiResult<Map<String, Object>> status(@PathVariable("agentId") String agentId) {
        Map<String, Object> status = agentManager.status(agentId);
        if (status == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "运行时 Agent 不存在: " + agentId);
        }
        return ApiResult.ok(status);
    }

    /**
     * 按配置中心「Agent 配置」分组的唯一名称解析运行时 Agent。
     *
     * <p>缺省读取全局设置 {@code agent.agent_name}，经 {@link AgentRegistry#getByName}
     * 解析已注册的 Agent；传入 {@code name} 参数时改为解析该名称 —— 供配置中心在
     * <b>保存前</b>做「测试解析」（不读库，直接验证名称能否命中注册表）。
     * 命中的 Agent 若同时被 {@link AgentManager} 托管，附带其运行态快照。
     * 未配置名称、名称未注册分别返回明确的业务错误。
     * 管理员专属：agent 分组字段 adminOnly，写路径也仅管理员可改。</p>
     *
     * <p>响应里同时带 {@code profile}（当前生效的运行时基线快照）与 {@code preview}：
     * 传 {@code name} 时 preview 是「把名称换成它」后的基线，便于保存前核对。</p>
     *
     * @param name 待解析的 Agent 名称（可选；缺省解析当前已保存配置）
     * @return 配置名称、解析到的 Agent 概要（含运行态）、运行时基线与预览
     */
    @Permission("admin")
    @GetMapping("/configured")
    public ApiResult<Map<String, Object>> configured(
            @RequestParam(value = "name", required = false) String name) {
        SysAiAgent record = requireManaged(name);
        AgentRuntimeProfile profile = AgentRuntimeProfile.fromManaged(record);
        String resolvedName = record.getName();
        Agent agent = Boolean.TRUE.equals(record.getEnabled()) ? agentManager.get(record.getAgentId()) : null;
        if (agent == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "Agent 不存在: " + resolvedName);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", resolvedName);
        result.put("found", true);
        Map<String, Object> summary = agentSummary(agent);
        result.put("agent", summary);
        Object definitionId = summary.get("id");
        if (definitionId instanceof String id && agentManager.contains(id)) {
            result.put("runtime", agentManager.status(id));
        }
        result.put("profile", profileOf(profile));
        result.put("preview", profileOf(profile.withAgentName(resolvedName)));
        return ApiResult.ok(result);
    }

    /**
     * 读取当前生效的 Agent 运行时基线（配置中心「Agent 配置」的只读快照）。
     *
     * <p>与 {@code AiChatFacadeServiceImpl#maybeWrapAgent} 走同一个解析入口
     * （{@link AgentRuntimeProfile#resolve}），因此页面上看到的基线与真正装配到
     * 会话 Agent 上的值同源，不存在「显示一套、跑另一套」。</p>
     *
     * @return 运行时基线快照
     */
    @Permission("admin")
    @GetMapping("/baseline")
    public ApiResult<Map<String, Object>> baseline(@RequestParam(value = "name", required = false) String name) {
        return ApiResult.ok(profileOf(AgentRuntimeProfile.fromManaged(requireManaged(name))));
    }

    /**
     * 按页面当前选择读取主记录，禁止缺省回落到其它 Agent。
     * @param name 当前名称
     * @return managed 记录
     */
    private SysAiAgent requireManaged(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "请选择 Agent");
        }
        SysAiAgent record = agentService.findAgentByName(name);
        if (record == null) throw new ApiException(ApiErrorCode.NOT_FOUND, "Agent 不存在: " + name);
        return record;
    }

    /**
     * 把运行时基线档案摊成前端可读的 Map。
     *
     * @param profile 基线档案
     * @return 字段视图
     */
    private static Map<String, Object> profileOf(AgentRuntimeProfile profile) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("agentName", profile.agentName());
        view.put("mcp", profile.mcp().name().toLowerCase(Locale.ROOT));
        view.put("mcpServers", profile.mcpServers());
        view.put("subAgent", profile.subAgent().name().toLowerCase(Locale.ROOT));
        view.put("defaultSkills", profile.defaultSkills());
        view.put("memory", profile.memory());
        view.put("planning", profile.planning());
        view.put("maxToolIterations", profile.maxToolIterations());
        view.put("maxTotalTokens", profile.maxTotalTokens());
        view.put("requiresAgent", profile.requiresAgent());
        return view;
    }

    /**
     * 列出全部已注册 Agent 的名称。
     *
     * <p>配置中心「Agent 唯一名称」下拉的可选集：名称必须与这里的一项一致，
     * {@link #configured} 才能解析成功。字典序升序，方便在下拉里定位。</p>
     *
     * @return 已注册 Agent 名称列表（字典序升序）
     */
    @Permission("admin")
    @GetMapping("/names")
    public ApiResult<List<String>> names() {
        return ApiResult.ok(agentRegistry.getNames().stream().sorted().toList());
    }

    /**
     * 组装 Agent 概要（静态信息，不含运行态）。
     *
     * @param agent 已解析的 Agent
     * @return 概要字段：实现类、定义 id/名称/描述/角色/是否主控
     */
    private static Map<String, Object> agentSummary(Agent agent) {
        AgentDefinition definition = null;
        try {
            definition = agent.getDefinition();
        } catch (RuntimeException e) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Agent 未暴露可用于会话的定义");
        }
        if (definition == null) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Agent 未提供可用于会话的定义");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("impl", agent.getClass().getName());
        if (definition != null) {
            summary.put("id", definition.getId());
            summary.put("name", definition.getName());
            summary.put("description", definition.getDescription());
            summary.put("role", definition.getRole());
            summary.put("leader", definition.isLeader());
        }
        return summary;
    }

    /**
     * 创建一个运行时 Agent 并纳入托管。
     *
     * <p>用于「不在库里的临时 Agent」或者调试：只认传入的定义字段，
     * 模型等资源走引擎默认装配。库里配置的 Agent 请用 {@code /api/agents/managed}
     * 新增后由服务自行构建。</p>
     *
     * @param body 创建请求：agentId（必填）、name、description、role、instruction、
     *             systemPrompt、leader、mcp、memory、planning、maxToolIterations
     * @return 创建后的状态
     */
    @Permission("admin")
    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String agentId = str(body, "agentId");
        if (agentId == null || agentId.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "agentId 不能为空");
        }
        if (agentManager.contains(agentId)) {
            // 同 id 重复创建会让「按 id 停止」停到另一个实例，这里直接返回现状而不是静默换掉。
            throw new ApiException(ApiErrorCode.CONFLICT, "运行时 Agent 已存在: " + agentId);
        }
        AgentDefinition.Builder builder = AgentDefinition.builder()
                .id(agentId)
                .name(defaultIfBlank(str(body, "name"), agentId))
                .description(defaultIfBlank(str(body, "description"), ""))
                .role(str(body, "role"))
                .instruction(str(body, "instruction"))
                .leader(bool(body, "leader", false))
                .mcp(bool(body, "mcp", true))
                .memory(bool(body, "memory", true))
                .planning(bool(body, "planning", false));
        String systemPrompt = str(body, "systemPrompt");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.systemPrompt(systemPrompt);
        }
        int maxToolIterations = intv(body, "maxToolIterations", 0);
        if (maxToolIterations > 0) {
            builder.maxToolIterations(maxToolIterations);
        }
        Agent agent = agentManager.create(agentId, builder.build(), null);
        log.info("[AgentRuntime] 已创建运行时 Agent: id={}, impl={}", agentId, agent.getClass().getName());
        return ApiResult.ok(agentManager.status(agentId));
    }

    /**
     * 停掉该 Agent 当前这一轮（三个实现各自落地），实例保留可继续用。
     *
     * @param agentId Agent 标识
     * @return { interrupted: true }
     */
    @Permission("admin")
    @PostMapping("/{agentId}/interrupt")
    public ApiResult<Map<String, Object>> interrupt(@PathVariable("agentId") String agentId) {
        if (!agentManager.interrupt(agentId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "运行时 Agent 不存在: " + agentId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", agentId);
        result.put("interrupted", true);
        return ApiResult.ok(result);
    }

    /**
     * 停掉全部托管 Agent 当前这一轮。
     *
     * @return { count: 实际发出中断的个数 }
     */
    @Permission("admin")
    @PostMapping("/interrupt-all")
    public ApiResult<Map<String, Object>> interruptAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", agentManager.interruptAll());
        return ApiResult.ok(result);
    }

    /**
     * 销毁运行时 Agent：先中断，再关闭实例，最后移出托管。
     *
     * @param agentId Agent 标识
     * @return 成功
     */
    @Permission("admin")
    @DeleteMapping("/{agentId}")
    public ApiResult<Void> destroy(@PathVariable("agentId") String agentId) {
        if (!agentManager.destroy(agentId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "运行时 Agent 不存在: " + agentId);
        }
        return ApiResult.ok();
    }

    /**
     * 取字符串字段。
     *
     * @param body 请求体
     * @param key  字段名
     * @return 字符串或 {@code null}
     */
    private static String str(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 空串回退。
     *
     * @param value        原值
     * @param defaultValue 默认值
     * @return 非空原值或默认值
     */
    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 取布尔字段。
     *
     * @param body         请求体
     * @param key          字段名
     * @param defaultValue 默认值
     * @return 布尔值
     */
    private static boolean bool(Map<String, Object> body, String key, boolean defaultValue) {
        Object value = body == null ? null : body.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 取整数字段。
     *
     * @param body         请求体
     * @param key          字段名
     * @param defaultValue 默认值
     * @return 整数值
     */
    private static int intv(Map<String, Object> body, String key, int defaultValue) {
        Object value = body == null ? null : body.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
