package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.ChannelHealth;
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
}
