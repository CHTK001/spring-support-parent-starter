package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.ChannelHealth;
import com.chua.starter.ai.support.model.ChannelCircuitSnapshot;
import com.chua.starter.ai.support.service.ChannelCircuitService;
import com.chua.starter.ai.support.service.ChannelHealthService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 渠道健康探针控制器。
 *
 * <p>提供渠道健康状态的查询/手动探针/探针间隔设置接口，
 * 页面入口 {@code /channels/health}</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@RestController
@RequestMapping("/v2/channels")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - 渠道健康")
public class AiChannelHealthController {

    private final ChannelHealthService channelHealthService;

    /**
     * 请求级熔断器（按供应商 + 模型统计真实对话失败）
     */
    private final ChannelCircuitService channelCircuitService;

    /**
     * 全部渠道健康状态列表
     *
     * @return 状态列表
     */
    @Operation(summary = "全部渠道健康状态")
    @GetMapping("/health")
    public ApiResult<List<ChannelHealth>> listHealth() {
        return ApiResult.ok(channelHealthService.listStates());
    }

    /**
     * 指定供应商的渠道健康状态统计
     *
     * @param providerId 供应商标识
     * @return 统计摘要（total + 各状态数）
     */
    @Operation(summary = "供应商渠道健康统计")
    @GetMapping("/health/{providerId}")
    public ApiResult<Map<String, Integer>> statByProvider(@Parameter(hidden = true)
                                                          @PathVariable String providerId) {
        return ApiResult.ok(channelHealthService.statByProvider(providerId));
    }

    /**
     * 连接健康状态（探活结果）
     *
     * @param connectionId 连接标识
     * @return 健康状态
     */
    @Operation(summary = "连接健康状态")
    @GetMapping("/health/{providerId}/{connectionId}")
    public ApiResult<ChannelHealth> state(
            @Parameter(hidden = true) @PathVariable String providerId,
            @Parameter(hidden = true) @PathVariable String connectionId) {
        return ApiResult.ok(channelHealthService.getState(connectionId));
    }

    /**
     * 全部探针
     *
     * @return 探针完成的连接数
     */
    @Operation(summary = "全部探针")
    @PostMapping("/health/probe")
    @Permission("admin")
    public ApiResult<Integer> probeAll() {
        return ApiResult.ok(channelHealthService.probeAll());
    }

    /**
     * 手动触发单连接探针
     *
     * @param connectionId 连接标识
     * @return 探针结果状态
     */
    @Operation(summary = "单连接探针")
    @PostMapping("/health/{connectionId}/probe")
    @Permission("admin")
    public ApiResult<ChannelHealth> probe(
            @Parameter(hidden = true) @PathVariable String connectionId) {
        return ApiResult.ok(channelHealthService.probe(connectionId));
    }

    /**
     * 设定探针间隔秒数（0 ＝ 走全局设置）
     *
     * @param connectionId 连接标识
     * @return 是否成功
     */
    @Operation(summary = "设定探针间隔")
    @PutMapping("/health/{connectionId}/interval")
    @Permission("admin")
    public ApiResult<Boolean> updateInterval(
            @Parameter(hidden = true) @PathVariable String connectionId,
            @RequestBody Map<String, Integer> body) {
        Integer interval = body.get("interval");
        channelHealthService.updateInterval(connectionId, interval == null ? 0 : Math.max(0, interval));
        return ApiResult.ok(true);
    }

    // ==================== 人工熔断开关 ====================

    /**
     * 设定单连接的人工熔断状态。
     *
     * <p>请求体：{@code {"state":"OPEN","reason":"..."}}</p>
     * <ul>
     *   <li>{@code AUTO} — 跟随探针状态（默认）；</li>
     *   <li>{@code OPEN} — 强制熔断，路由层立即剔除该连接（不发上游请求）；</li>
     *   <li>{@code CLOSED} — 强制放行，即使探针判定 DOWN 也继续路由（排障用）。</li>
     * </ul>
     *
     * @param connectionId 连接标识
     * @param body         请求体（state / reason）
     * @return 变更后的状态行
     */
    @Operation(summary = "设定人工熔断开关")
    @PutMapping("/{connectionId}/circuit")
    @Permission("admin")
    public ApiResult<ChannelHealth> updateCircuit(
            @Parameter(hidden = true) @PathVariable String connectionId,
            @RequestBody Map<String, String> body) {
        String state = body.get("state");
        String reason = body.get("reason");
        return ApiResult.ok(channelHealthService.updateCircuitState(connectionId, state, reason));
    }

    /**
     * 查询单连接的人工熔断状态。
     *
     * @param connectionId 连接标识
     * @return 状态行（含 circuitState / circuitReason / circuitUpdatedAt）
     */
    @Operation(summary = "查询人工熔断开关")
    @GetMapping("/{connectionId}/circuit")
    public ApiResult<ChannelHealth> circuit(
            @Parameter(hidden = true) @PathVariable String connectionId) {
        return ApiResult.ok(channelHealthService.getState(connectionId));
    }

    // ==================== 请求级熔断状态 ====================

    /**
     * 请求级熔断状态列表（按「供应商 + 模型」维度）。
     *
     * @return 熔断快照列表
     */
    @Operation(summary = "请求级熔断状态列表")
    @GetMapping("/circuit")
    public ApiResult<List<ChannelCircuitSnapshot>> circuitSnapshots() {
        return ApiResult.ok(channelCircuitService.snapshots());
    }

    /**
     * 复位单个「供应商 + 模型」的熔断。
     *
     * @param providerId 供应商标识
     * @param model      模型名
     * @return 复位前是否存在该条目
     */
    @Operation(summary = "复位单个熔断条目")
    @PostMapping("/circuit/{providerId}/{model}/reset")
    @Permission("admin")
    public ApiResult<Boolean> resetCircuit(
            @Parameter(hidden = true) @PathVariable String providerId,
            @Parameter(hidden = true) @PathVariable String model) {
        return ApiResult.ok(channelCircuitService.reset(providerId, model));
    }

    /**
     * 复位全部熔断条目（运维操作）。
     *
     * @return 被复位的条目数
     */
    @Operation(summary = "复位全部熔断条目")
    @PostMapping("/circuit/reset")
    @Permission("admin")
    public ApiResult<Integer> resetAllCircuits() {
        return ApiResult.ok(channelCircuitService.resetAll());
    }
}
