package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.mapper.AgentBrowserHostMapper;
import com.chua.starter.ai.support.manage.mapper.AgentBrowserLaneMapper;
import com.chua.starter.ai.support.manage.mapper.AgentBrowserSettingMapper;
import com.chua.starter.ai.support.manage.mapper.AgentBrowserTabMapper;
import com.chua.starter.ai.support.manage.store.AgentBrowserHost;
import com.chua.starter.ai.support.manage.store.AgentBrowserLane;
import com.chua.starter.ai.support.manage.store.AgentBrowserSetting;
import com.chua.starter.ai.support.manage.store.AgentBrowserTab;
import com.chua.starter.ai.support.util.AiUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent Browser 管理控制器。
 *
 * <p>提供浏览器实例（Lane）、标签页（Tab）、宿主进程（Host）和全局设置的 CRUD 操作。
 * 对接前端 {@code /api/browser/*} 端点，替代原 Rust 后端的浏览器管理能力。</p>
 *
 * <p>核心概念：</p>
 * <ul>
 *   <li><b>Lane</b> - 浏览器调度单元，对应一个托管的浏览器实例</li>
 *   <li><b>Tab</b> - 浏览器标签页，每个 Lane 可包含多个 Tab</li>
 *   <li><b>Host</b> - 浏览器宿主进程，负责管理多个 Lane</li>
 *   <li><b>Setting</b> - 全局设置（显示模式、资源策略等）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/browser")
@RequiredArgsConstructor
@Tag(name = "Agent Browser 管理", description = "浏览器实例、标签页、宿主进程和全局设置的 CRUD 操作")
public class AgentBrowserController {

    /**
     * Lane 映射器
     */
    private final AgentBrowserLaneMapper laneMapper;

    /**
     * Tab 映射器
     */
    private final AgentBrowserTabMapper tabMapper;

    /**
     * 主机 映射器
     */
    private final AgentBrowserHostMapper hostMapper;

    /**
     * Setting 映射器
     */
    private final AgentBrowserSettingMapper settingMapper;

    // ==================== Overview ====================

    /**
     * 获取浏览器总览信息。
     *
     * <p>返回全局视图，包括支持状态、运行/排队数量、容量信息、Host 列表等。
     * 前端通过此接口判断浏览器功能是否可用，以及当前的资源压力状态。</p>
     *
     * @return 浏览器总览信息
     */
    @GetMapping("/overview")
    @Operation(summary = "获取浏览器总览", description = "返回全局视图，包括支持状态、运行/排队数量、容量信息、Host 列表等")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取成功")
    })
    /**
     * 获取overview。
     * @return 获取overview的结果
     */
    public ApiResult<Map<String, Object>> getOverview() {
        long now = System.currentTimeMillis();

 // 统计各状态的 Lane 数量：管理员看全量，普通用户只统计本人 Lane，避免跨用户数量泄露
        List<AgentBrowserLane> statsLanes = laneMapper.selectList(scopeToOwner(new LambdaQueryWrapper<AgentBrowserLane>()
                .in(AgentBrowserLane::getLifecycleState, "running", "queued", "starting", "frozen")));
        int runningLanes = countState(statsLanes, "running");
        int queuedLanes = countState(statsLanes, "queued");
        int totalLanes = statsLanes.size();

 // 获取 主机 列表
        List<AgentBrowserHost> hosts = hostMapper.selectList(null);

 // 构建 主机 列表
        List<Map<String, Object>> hostList = hosts.stream().map(h -> {
            Map<String, Object> hostMap = new LinkedHashMap<>();
            hostMap.put("host_id", h.getHostId());
            hostMap.put("state", h.getState());
            hostMap.put("epoch", h.getEpoch());
            hostMap.put("headful", h.getHeadful());
            hostMap.put("identity_mode", h.getIdentityMode());
            hostMap.put("lane_count", h.getLaneCount());
            hostMap.put("rss_bytes", h.getRssBytes());
            return hostMap;
        }).collect(Collectors.toList());

        // 判断压力状态
        String pressureState = "normal";
        if (runningLanes > 10) {
            pressureState = "pressured";
        }
        if (runningLanes > 20) {
            pressureState = "critical";
        }

        // 构建总览响应
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("supported", true);
        overview.put("enabled", true);
        overview.put("running_lanes", runningLanes);
        overview.put("queued_lanes", queuedLanes);
        overview.put("total_lanes", totalLanes);
        overview.put("pressure_state", pressureState);
        overview.put("hosts", hostList);
        overview.put("managed_host_count", hosts.size());
        overview.put("pending_cleanup_count", 0);
        overview.put("can_close_all", true);
        overview.put("can_manage_browser_settings", true);
        overview.put("can_manage_primary_identity", true);
        overview.put("updated_at", now);

        // 容量信息
        Map<String, Object> capacity = new LinkedHashMap<>();
        capacity.put("max_active", 10);
        capacity.put("max_open_lanes", 20);
        capacity.put("global_memory_pressure_threshold_bytes", 4L * 1024 * 1024 * 1024);
        capacity.put("max_task_memory_bytes", 512L * 1024 * 1024);
        capacity.put("max_task_active_operations", 5);
        capacity.put("max_task_open_lanes", 5);
        capacity.put("max_task_tabs", 10);
        capacity.put("recommended_concurrency", 3);
        overview.put("capacity", capacity);

        return ApiResult.ok(overview);
    }

    // ==================== Lane CRUD ====================

    /**
     * 获取所有 Lane 列表。
     *
     * <p>返回所有浏览器 Lane 的详细信息，包括状态、Owner、Identity、Tab 等。
     * 前端通过此接口渲染左侧导航树和详情面板。</p>
     *
     * @return Lane 列表
     */
    @GetMapping("/lanes")
    @Operation(summary = "获取所有 Lane", description = "返回所有浏览器 Lane 的详细信息")
    public ApiResult<List<Map<String, Object>>> getLanes() {
        List<AgentBrowserLane> lanes = laneMapper.selectList(scopeToOwner(new LambdaQueryWrapper<AgentBrowserLane>()));
        List<Map<String, Object>> result = lanes.stream()
                .map(this::toLaneResponse)
                .collect(Collectors.toList());
        return ApiResult.ok(result);
    }

    /**
     * 获取单个 Lane 详情。
     *
     * @param laneId Lane 标识
     * @return Lane 详情
     */
    @GetMapping("/lanes/{lane_id}")
    @Operation(summary = "获取 Lane 详情", description = "根据 ID 获取单个 Lane 的详细信息")
    public ApiResult<Map<String, Object>> getLane(
            @Parameter(description = "Lane ID") @PathVariable("lane_id") String laneId) {
        AgentBrowserLane lane = requireReadableLane(laneId);
        return ApiResult.ok(toLaneResponse(lane));
    }

    /**
     * 创建新 Lane。
     *
     * @param body Lane 创建参数
     * @return 创建后的 Lane 信息
     */
    @PostMapping("/lanes")
    @Operation(summary = "创建 Lane", description = "创建新的浏览器 Lane")
    public ApiResult<Map<String, Object>> createLane(@RequestBody Map<String, Object> body) {
        long now = System.currentTimeMillis();
        String laneId = UUID.randomUUID().toString();

        AgentBrowserLane lane = new AgentBrowserLane();
        lane.setLaneId(laneId);
        lane.setLaneName(str(body, "lane_name", "New Lane"));
        lane.setLifecycleState("running");
        lane.setBrowserEpoch(1L);
 // 归属由服务端按当前登录身份写入：请求体里的 user_id 一律忽略，避免越权冒名
        lane.setUserId(AiUserContext.requireUserId());
        lane.setConversationId(str(body, "conversation_id", null));
        lane.setRuntimeInstanceId(str(body, "runtime_instance_id", null));
        lane.setExecutionId(str(body, "execution_id", null));
        lane.setAgentId(str(body, "agent_id", null));
        lane.setAgentName(str(body, "agent_name", null));
        lane.setSurface(str(body, "surface", "web"));
        lane.setIdentityMode(str(body, "identity_mode", "anonymous"));
        lane.setResourceEstimateBytes(0L);
        lane.setActiveOperationCount(0);
        lane.setRecoverable(true);
        lane.setCreatedAt(now);
        lane.setUpdatedAt(now);

        laneMapper.insert(lane);

        // 创建默认 Tab
        AgentBrowserTab tab = new AgentBrowserTab();
        tab.setTabId(UUID.randomUUID().toString());
        tab.setLaneId(laneId);
 // Tab 归属随所属 Lane 同源写入，不接受客户端传值
        tab.setUserId(lane.getUserId());
        tab.setTitle("New Tab");
        tab.setActive(true);
        tab.setCrashed(false);
        tab.setCreatedAt(now);
        tab.setUpdatedAt(now);
        tabMapper.insert(tab);

        lane.setActiveTabId(tab.getTabId());
        laneMapper.updateById(lane);

        log.info("Lane created: {} [{}]", lane.getLaneName(), laneId);
        return ApiResult.ok(toLaneResponse(lane));
    }

    /**
     * 更新 Lane 信息。
     *
     * @param laneId Lane 标识
     * @param body   更新参数
     * @return 更新后的 Lane 信息
     */
    @PutMapping("/lanes/{lane_id}")
    @Operation(summary = "更新 Lane", description = "更新指定 Lane 的信息")
    public ApiResult<Map<String, Object>> updateLane(
            @Parameter(description = "Lane ID") @PathVariable("lane_id") String laneId,
            @RequestBody Map<String, Object> body) {
        AgentBrowserLane lane = requireWritableLane(laneId);

        long now = System.currentTimeMillis();
        if (body.containsKey("lane_name")) {
            lane.setLaneName(str(body, "lane_name", lane.getLaneName()));
        }
        if (body.containsKey("lifecycle_state")) {
            lane.setLifecycleState(str(body, "lifecycle_state", lane.getLifecycleState()));
        }
        if (body.containsKey("identity_mode")) {
            lane.setIdentityMode(str(body, "identity_mode", lane.getIdentityMode()));
        }
        if (body.containsKey("title")) {
            lane.setTitle(str(body, "title", lane.getTitle()));
        }
        if (body.containsKey("url")) {
            lane.setUrl(str(body, "url", lane.getUrl()));
        }
        lane.setUpdatedAt(now);
        laneMapper.updateById(lane);

        return ApiResult.ok(toLaneResponse(lane));
    }

    /**
     * 关闭指定 Lane。
     *
     * <p>将 Lane 状态设置为 stopping，删除关联的 Tab，并更新 Host 的 lane_count。</p>
     *
     * @param laneId Lane 标识
     * @return 关闭结果
     */
    @PostMapping("/lanes/{lane_id}/close")
    @Operation(summary = "关闭 Lane", description = "关闭指定的浏览器 Lane")
    @Transactional
    public ApiResult<Map<String, Object>> closeLane(
            @Parameter(description = "Lane ID") @PathVariable("lane_id") String laneId) {
        AgentBrowserLane lane = requireWritableLane(laneId);

        // 删除关联的 Tab
        tabMapper.deleteByLaneId(laneId);

        // 删除 Lane
        laneMapper.deleteById(laneId);

        log.info("Lane closed: {}", laneId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("closed", 1);
        result.put("lane_id", laneId);
        return ApiResult.ok(result);
    }

    /**
     * 将 Lane 前台化（显示窗口）。
     *
     * @param laneId Lane 标识
     * @return 前台化结果
     */
    @PostMapping("/lanes/{lane_id}/foreground")
    @Operation(summary = "前台化 Lane", description = "将 Lane 切换到前台显示模式")
    public ApiResult<Map<String, Object>> foregroundLane(
            @Parameter(description = "Lane ID") @PathVariable("lane_id") String laneId) {
        requireWritableLane(laneId);

 // 更新 主机 为 headful 模式
        List<AgentBrowserHost> hosts = hostMapper.selectList(null);
        for (AgentBrowserHost host : hosts) {
            host.setHeadful(true);
            host.setUpdatedAt(System.currentTimeMillis());
            hostMapper.updateById(host);
        }

        log.info("Lane foregrounded: {}", laneId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("foregrounded", true);
        result.put("lane_id", laneId);
        return ApiResult.ok(result);
    }

    /**
     * 将 Lane 后台化（返回无头）。
     *
     * @param laneId Lane 标识
     * @return 后台化结果
     */
    @PostMapping("/lanes/{lane_id}/background")
    @Operation(summary = "后台化 Lane", description = "将 Lane 切换到后台无头模式")
    public ApiResult<Map<String, Object>> backgroundLane(
            @Parameter(description = "Lane ID") @PathVariable("lane_id") String laneId) {
        requireWritableLane(laneId);

 // 更新 主机 为 headless 模式
        List<AgentBrowserHost> hosts = hostMapper.selectList(null);
        for (AgentBrowserHost host : hosts) {
            host.setHeadful(false);
            host.setUpdatedAt(System.currentTimeMillis());
            hostMapper.updateById(host);
        }

        log.info("Lane backgrounded: {}", laneId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("backgrounded", true);
        result.put("lane_id", laneId);
        return ApiResult.ok(result);
    }

    // ==================== 对话关联操作 ====================

    /**
     * 关闭指定对话的所有 Lane。
     *
     * @param conversationId 对话 标识
     * @return 关闭结果
     */
    @PostMapping("/conversations/{conversation_id}/close")
    @Operation(summary = "关闭对话的 Lane", description = "关闭指定对话关联的所有浏览器 Lane")
    @Transactional
    public ApiResult<Map<String, Object>> closeConversationLanes(
            @Parameter(description = "对话 ID") @PathVariable("conversation_id") String conversationId) {
        List<AgentBrowserLane> lanes = laneMapper.selectList(new LambdaQueryWrapper<AgentBrowserLane>()
                .eq(AgentBrowserLane::getConversationId, conversationId));

        // 先整批校验归属再删除：避免中途拒绝导致部分关闭
        List<AgentBrowserLane> writable = new ArrayList<>();
        for (AgentBrowserLane lane : lanes) {
            writable.add(requireWritableLane(lane.getLaneId()));
        }

        int closedCount = 0;

        for (AgentBrowserLane lane : writable) {
            tabMapper.deleteByLaneId(lane.getLaneId());
            laneMapper.deleteById(lane.getLaneId());
            closedCount++;
        }

        log.info("SysAiConversation {} lanes closed: {}", conversationId, closedCount);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("closed", closedCount);
        return ApiResult.ok(result);
    }

    /**
     * 全局关闭所有浏览器资源。
     *
     * @return 关闭结果
     */
    @PostMapping("/close-all")
    @Operation(summary = "全局关闭", description = "关闭所有浏览器 Lane 和 Tab")
    @Transactional
    public ApiResult<Map<String, Object>> closeAll() {
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
 // 只关闭本人名下的 Lane：管理员同样不得删除他人数据
        String userId = AiUserContext.requireUserId();
        List<AgentBrowserLane> lanes = laneMapper.selectList(new LambdaQueryWrapper<AgentBrowserLane>()
                .ne(AgentBrowserLane::getLifecycleState, "failed")
                .eq(AgentBrowserLane::getUserId, userId));
        int closedCount = lanes.size();

        // 删除所有 Tab
        for (AgentBrowserLane lane : lanes) {
            tabMapper.deleteByLaneId(lane.getLaneId());
        }

        // 仅删除本人 Lane
        laneMapper.delete(new LambdaQueryWrapper<AgentBrowserLane>().eq(AgentBrowserLane::getUserId, userId));

        long remaining = laneMapper.selectCount(new LambdaQueryWrapper<AgentBrowserLane>()
                .ne(AgentBrowserLane::getLifecycleState, "failed"));
        if (remaining == 0) {
 // 重置 主机 lane_数量：仅在全局确无活跃 Lane 时执行，避免越权篡改宿主统计
            List<AgentBrowserHost> hosts = hostMapper.selectList(null);
            long now = System.currentTimeMillis();
            for (AgentBrowserHost host : hosts) {
                host.setLaneCount(0);
                host.setUpdatedAt(now);
                hostMapper.updateById(host);
            }
        }

        log.info("Browser resources closed: {} lanes [{}]", closedCount, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("closed", closedCount);
        result.put("remaining_lane_count", remaining);
        result.put("remaining_cleanup_count", 0);
        result.put("remaining_managed_host_count", hostMapper.selectCount(null));
        return ApiResult.ok(result);
    }

    // ==================== Display Mode ====================

    /**
     * 获取全局显示策略。
     *
     * @return 显示策略
     */
    @GetMapping("/display-mode")
    @Operation(summary = "获取显示策略", description = "获取全局浏览器显示策略（headless/auto/external）")
    public ApiResult<Map<String, Object>> getDisplayMode() {
        AgentBrowserSetting setting = settingMapper.selectById("display_mode");
        String displayMode = "auto";
        if (setting != null && setting.getSettingValue() != null) {
            displayMode = setting.getSettingValue().replace("\"", "");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("display_mode", displayMode);
        result.put("effective_visibility", "headless");
        return ApiResult.ok(result);
    }

    /**
     * 设置全局显示策略。
     *
     * @param body 显示策略参数
     * @return 设置结果
     */
    @PutMapping("/display-mode")
    @Operation(summary = "设置显示策略", description = "设置全局浏览器显示策略")
    public ApiResult<Map<String, Object>> setDisplayMode(@RequestBody Map<String, Object> body) {
        String displayMode = str(body, "display_mode", "auto");

        long now = System.currentTimeMillis();
        AgentBrowserSetting setting = settingMapper.selectById("display_mode");
        if (setting == null) {
            setting = new AgentBrowserSetting();
            setting.setSettingKey("display_mode");
            setting.setSettingValue(displayMode);
            setting.setUpdatedAt(now);
            settingMapper.insert(setting);
        } else {
            setting.setSettingValue(displayMode);
            setting.setUpdatedAt(now);
            settingMapper.updateById(setting);
        }

        log.info("Display mode set to: {}", displayMode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("display_mode", displayMode);
        result.put("effective_visibility", "headless");
        return ApiResult.ok(result);
    }

    // ==================== Resource Policy ====================

    /**
     * 获取资源策略。
     *
     * @return 资源策略
     */
    @GetMapping("/resource-policy")
    @Operation(summary = "获取资源策略", description = "获取浏览器资源策略配置")
    public ApiResult<Map<String, Object>> getResourcePolicy() {
        AgentBrowserSetting setting = settingMapper.selectById("resource_policy");
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("preset", "automatic");
        if (setting != null && setting.getSettingValue() != null) {
            try {
                // 简单解析 JSON
                String json = setting.getSettingValue();
                if (json.contains("resource_saving")) {
                    policy.put("preset", "resource_saving");
                } else if (json.contains("high_concurrency")) {
                    policy.put("preset", "high_concurrency");
                } else if (json.contains("custom")) {
                    policy.put("preset", "custom");
                }
            } catch (Exception e) {
                log.warn("Failed to parse resource policy: {}", e.getMessage());
            }
        }
        return ApiResult.ok(policy);
    }

    /**
     * 更新资源策略。
     *
     * @param body 资源策略参数
     * @return 更新结果
     */
    @PutMapping("/resource-policy")
    @Operation(summary = "更新资源策略", description = "更新浏览器资源策略配置")
    public ApiResult<Map<String, Object>> updateResourcePolicy(@RequestBody Map<String, Object> body) {
        String preset = str(body, "preset", "automatic");
        String jsonValue = "{\"preset\":\"" + preset + "\"}";

        long now = System.currentTimeMillis();
        AgentBrowserSetting setting = settingMapper.selectById("resource_policy");
        if (setting == null) {
            setting = new AgentBrowserSetting();
            setting.setSettingKey("resource_policy");
            setting.setSettingValue(jsonValue);
            setting.setUpdatedAt(now);
            settingMapper.insert(setting);
        } else {
            setting.setSettingValue(jsonValue);
            setting.setUpdatedAt(now);
            settingMapper.updateById(setting);
        }

        log.info("Resource policy updated: {}", preset);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("preset", preset);
        return ApiResult.ok(result);
    }

    // ==================== Login ====================

    /**
     * 打开登录窗口。
     *
     * @return 操作结果
     */
    @PostMapping("/login/open")
    @Operation(summary = "打开登录窗口", description = "打开浏览器登录窗口（Primary 身份 Lane）")
    public ApiResult<Map<String, Object>> openLogin() {
        long now = System.currentTimeMillis();
        AgentBrowserSetting setting = settingMapper.selectById("login_status");
        if (setting == null) {
            setting = new AgentBrowserSetting();
            setting.setSettingKey("login_status");
            setting.setSettingValue("{\"open\":true}");
            setting.setUpdatedAt(now);
            settingMapper.insert(setting);
        } else {
            setting.setSettingValue("{\"open\":true}");
            setting.setUpdatedAt(now);
            settingMapper.updateById(setting);
        }

        log.info("Login window opened");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("open", true);
        return ApiResult.ok(result);
    }

    /**
     * 关闭登录窗口。
     *
     * @return 操作结果
     */
    @PostMapping("/login/close")
    @Operation(summary = "关闭登录窗口", description = "关闭浏览器登录窗口")
    public ApiResult<Map<String, Object>> closeLogin() {
        long now = System.currentTimeMillis();
        AgentBrowserSetting setting = settingMapper.selectById("login_status");
        if (setting == null) {
            setting = new AgentBrowserSetting();
            setting.setSettingKey("login_status");
            setting.setSettingValue("{\"open\":false}");
            setting.setUpdatedAt(now);
            settingMapper.insert(setting);
        } else {
            setting.setSettingValue("{\"open\":false}");
            setting.setUpdatedAt(now);
            settingMapper.updateById(setting);
        }

        log.info("Login window closed");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("open", false);
        return ApiResult.ok(result);
    }

    /**
     * 查询登录窗口状态。
     *
     * @return 登录状态
     */
    @GetMapping("/login/status")
    @Operation(summary = "查询登录状态", description = "查询浏览器登录窗口状态")
    public ApiResult<Map<String, Object>> getLoginStatus() {
        AgentBrowserSetting setting = settingMapper.selectById("login_status");
        boolean isOpen = false;
        if (setting != null && setting.getSettingValue() != null) {
            isOpen = setting.getSettingValue().contains("true");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("open", isOpen);
        return ApiResult.ok(result);
    }

    // ==================== 辅助方法 ====================

    /**
     * 将 Lane 实体转换为前端期望的响应格式。
     *
     * @param lane Lane 实体
     * @return 前端格式的 Lane 数据
     */
    private Map<String, Object> toLaneResponse(AgentBrowserLane lane) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("lane_id", lane.getLaneId());
        resp.put("lane_name", lane.getLaneName());
        resp.put("lifecycle_state", lane.getLifecycleState());
        resp.put("browser_epoch", lane.getBrowserEpoch());

        // Owner 信息
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("user_id", lane.getUserId());
        owner.put("conversation_id", lane.getConversationId());
        owner.put("runtime_instance_id", lane.getRuntimeInstanceId());
        owner.put("execution_id", lane.getExecutionId());
        owner.put("agent_id", lane.getAgentId());
        owner.put("agent_name", lane.getAgentName());
        owner.put("surface", lane.getSurface());
        resp.put("owner", owner);

        // Identity 信息
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("mode", lane.getIdentityMode());
        identity.put("label", lane.getIdentityLabel());
        resp.put("identity", identity);

        // Tab 信息
        List<AgentBrowserTab> tabs = tabMapper.selectByLaneId(lane.getLaneId());
        List<Map<String, Object>> tabList = tabs.stream().map(t -> {
            Map<String, Object> tabMap = new LinkedHashMap<>();
            tabMap.put("tab_id", t.getTabId());
            tabMap.put("title", t.getTitle());
            tabMap.put("url", t.getUrl());
            tabMap.put("active", t.getActive());
            tabMap.put("crashed", t.getCrashed());
            return tabMap;
        }).collect(Collectors.toList());
        resp.put("tabs", tabList);
        resp.put("active_tab_id", lane.getActiveTabId());

        resp.put("title", lane.getTitle());
        resp.put("url", lane.getUrl());
        resp.put("last_active_at", lane.getLastActiveAt());
        resp.put("created_at", lane.getCreatedAt());
        resp.put("resource_estimate_bytes", lane.getResourceEstimateBytes());
        resp.put("active_operation_count", lane.getActiveOperationCount());
        resp.put("error_code", lane.getErrorCode());
        resp.put("error_message", lane.getErrorMessage());
        resp.put("recoverable", lane.getRecoverable());

        return resp;
    }

    /**
     * 从 映射 中安全获取字符串值。
     *
     * @param map          源 映射
     * @param key          键名
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private String str(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    /**
     * 给 Lane 查询条件按当前登录用户收窄。
     *
     * <p>管理员可跨用户查看；普通用户仅本人。无身份时条件恒不匹配，读取失败即关闭。</p>
     *
     * @param wrapper 查询条件
     * @return 追加归属条件后的查询条件
     */
    private LambdaQueryWrapper<AgentBrowserLane> scopeToOwner(LambdaQueryWrapper<AgentBrowserLane> wrapper) {
        if (!AiUserContext.isAdmin()) {
            wrapper.eq(AgentBrowserLane::getUserId, AiUserContext.currentUserId());
        }
        return wrapper;
    }

    /**
     * 统计指定生命周期状态的 Lane 数量。
     *
     * @param lanes 待统计的 Lane 列表
     * @param state 生命周期状态
     * @return 匹配数量
     */
    private int countState(List<AgentBrowserLane> lanes, String state) {
        int count = 0;
        for (AgentBrowserLane lane : lanes) {
            if (state.equals(lane.getLifecycleState())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 读取出口归属校验。
     *
     * @param laneId Lane 标识
     * @return Lane 实体
     */
    private AgentBrowserLane requireReadableLane(String laneId) {
        AgentBrowserLane lane = laneMapper.selectById(laneId);
        if (lane == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "Lane 不存在: " + laneId);
        }
        if (AiUserContext.isAdmin()) {
            return lane;
        }
        String userId = AiUserContext.requireUserId();
        if (!userId.equals(lane.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人浏览器实例");
        }
        return lane;
    }

    /**
     * 写出口归属校验。
     *
     * @param laneId Lane 标识
     * @return Lane 实体
     */
    private AgentBrowserLane requireWritableLane(String laneId) {
        AgentBrowserLane lane = laneMapper.selectById(laneId);
        if (lane == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "Lane 不存在: " + laneId);
        }
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        String userId = AiUserContext.requireUserId();
        if (!userId.equals(lane.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
        }
        return lane;
    }
}
