package com.chua.starter.ai.support.manage.api;

import com.chua.deeplearning.support.agent.opencode.OpencodeServerProcess;
import com.chua.starter.ai.support.configuration.runtime.AgentProviderFactory;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 引擎运维控制器：查询引擎可用性、手动触发 opencode 的下载与启动。
 *
 * <p>与 {@code /v2/settings/runtime} 的分工：那个接口回带的是「配置 + 解析结果」，
 * 供设置页读取；本控制器是无状态的<b>动作面</b>——管理员点了「立即下载并启动」，
 * 请求立刻返回、后台虚拟线程去做下载与拉起，页面随后轮询状态即可。</p>
 *
 * <p>为什么不复用 {@code /v2/settings/runtime}：那个接口会对每个字段做脱敏与覆盖层合并，
 * 语义是「配置」；而且触发下载是副作用动作，不能挂在 GET 上。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/agents/engine")
@RequiredArgsConstructor
@Slf4j
public class ApiAgentEngineController {

    /**
     * Agent 引擎工厂
     */
    private final AgentProviderFactory agentProviderFactory;

    /**
     * 查询当前引擎解析结果与 opencode 可用性。
     *
     * @return 状态快照
     */
    @GetMapping
    public ApiResult<Map<String, Object>> status() {
        return ApiResult.ok(statusOf(agentProviderFactory.resolution()));
    }

    /**
     * 手动触发一次 opencode 的下载 + 启动（后台执行，立即返回）。
     *
     * <p>会先清掉上一次失败留下的冷却记录，让管理员点一次就是一次真实重试。</p>
     *
     * @return 触发后的状态快照（{@code started} 表示本次是否真正发起了后台任务）
     */
    @Permission("admin")
    @PostMapping("/prepare")
    public ApiResult<Map<String, Object>> prepare() {
        OpencodeServerProcess.getInstance().clearFailure();
        boolean started = agentProviderFactory.prepareAsync();
        log.info("[AgentEngine] 管理员触发 opencode 下载/启动，started={}", started);
        Map<String, Object> body = statusOf(agentProviderFactory.resolution());
        body.put("started", started);
        return ApiResult.ok(body);
    }

    /**
     * 把解析结果摊平成接口响应体。
     *
     * @param resolution 解析结果
     * @return 响应体
     */
    private Map<String, Object> statusOf(AgentProviderFactory.RuntimeResolution resolution) {
        Map<String, Object> body = new LinkedHashMap<>(8);
        body.put("selected", resolution.selected());
        body.put("requested", resolution.requested());
        body.put("effective", resolution.effective());
        body.put("degraded", resolution.degraded());
        body.put("fallback", resolution.fallback());
        body.put("reason", resolution.reason());
        body.put("preparing", resolution.preparing());
        body.put("autoInstall", agentProviderFactory.autoInstallEnabled());
        OpencodeServerProcess.Availability availability = resolution.opencode();
        if (availability == null) {
            return body;
        }
        Map<String, Object> opencode = new LinkedHashMap<>(6);
        opencode.put("installed", availability.installed());
        opencode.put("binary", availability.binary());
        opencode.put("serverReady", availability.serverReady());
        opencode.put("preparing", availability.preparing());
        opencode.put("degraded", availability.degraded());
        opencode.put("reason", availability.reason());
        opencode.put("baseUrl", availability.baseUrl());
        body.put("opencode", opencode);
        return body;
    }
}
