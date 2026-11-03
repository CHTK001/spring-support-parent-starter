package com.chua.starter.ai.support.security;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 网关健康检查过滤器。
 *
 * <p>将健康检查路径（摘出 {@code security_health_paths}，默认
 * {@code /healthz /ping /readyz}）从拦截链中剥离：零开销直回 200，不进入任何下游拦截器
 * （IP/QPM/并发/包体/超时）与 Controller，保证集群/容器探针永久畅通。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class GatewayHealthCheckFilter extends AbstractGatewaySecurityFilter {

    /**
     * 构造器注入：安全设置唯一来源。
     *
     * @param globalSettingReader 全局设置读取器
     */
    public GatewayHealthCheckFilter(GlobalSettingReader globalSettingReader) {
        super(globalSettingReader);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // 总开关关闭 / 路径未命中 → 放行
            if (!enabled() || !matches(request)) {
                safePass(request, response, chain);
                return;
            }
            // 健康检查路径：零开销直回 200，不消费任何资源
            if (isHealthPath(request)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("OK");
                return;
            }
        } catch (Exception e) {
            log.warn("[GatewayHealthCheckFilter] 拦截异常，放行: {}", e.getMessage());
        }
        safePass(request, response, chain);
    }
}
