package com.chua.starter.ai.support.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 网关健康检查过滤器（顺序第一）。
 *
 * <p>把健康检查路径（{@code security_health_paths}，默认 {@code /healthz /ping /readyz}）
 * 从整条拦截链中剥离：直接回 200，不进入后续的 IP / 限流 / 并发 / 包体 / 超时过滤器与
 * Controller，保证容器与集群探针永久畅通。</p>
 *
 * <h2>为什么不受总开关与前缀约束</h2>
 * <p>探针路径不在 {@code /v2/v1/} 前缀下，且探针一旦被安全开关连坐，编排层会误判实例
 * 不健康并整批摘除 —— 那比关掉防护危险得多。因此这里覆盖 {@code applies}：
 * <b>健康路径无条件短路</b>，其余路径仍遵守总开关 + 前缀。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class GatewayHealthCheckFilter extends AbstractGatewaySecurityFilter {

    /**
     * 构造器注入：安全设置唯一来源。
     *
     * @param settings 安全设置快照
     */
    public GatewayHealthCheckFilter(SecuritySettings settings) {
        super(settings);
    }

    @Override
    protected boolean applies(HttpServletRequest request) {
        return isHealthPath(request) || super.applies(request);
    }

    @Override
    protected boolean reject(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!isHealthPath(request)) {
            return false;
        }
        // 探针：零开销直回 200，不消费任何限流/并发预算
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain;charset=UTF-8");
        try {
            response.getWriter().write("OK");
        } catch (IOException e) {
            log.debug("[GatewayHealthCheckFilter] 探针响应写失败（客户端已断开）: {}", e.getMessage());
        }
        return true;
    }
}
