package com.chua.starter.ai.support.security;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.IpUtils;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
/**
 * 网关 QPM 限流过滤器。
 *
 * <p>三个滑动维度（Window = 60 秒）：<br>
 * 1、APPKey 维度：{"@code security_rate_limit_qpm"}（单 Key，滑动窗口）；<br>
 * 2、IP 维度：{"@code security_rate_limit_per_ip_qpm"}（单 IP，滑动窗口）；<br>
 * 3、Path 维度：{"@code security_rate_limit_per_path_qpm"}（单路径，滑动窗口）。</p>
 *
 * <p>任意维度超限，写 JSON 429 并阻拦进入下游 Handler：未携带 APPKey 的管理控制台流量只
 * 受 IP 维度约束，所有维度关（均为 0/-1/空）时不把拦截做成系统故障点。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class GatewayRateLimitFilter extends AbstractGatewaySecurityFilter {

    /**
     * 限流器：Redis ZSET 滑动窗口，不可用时降级为本地 Caffeine 滑动窗口。
     */
    private final GatewayRateLimiter rateLimiter;

    /**
     * 构造器注入。
     *
     * @param globalSettingReader 全局设置读取器
     * @param rateLimiter         QPM 限流器
     */
    public GatewayRateLimitFilter(GlobalSettingReader globalSettingReader, GatewayRateLimiter rateLimiter) {
        super(globalSettingReader);
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // 总开关关闭 / 本开关关闭 / 路径未命中 → 放行。
            // security_rate_limit_enabled 曾经只在设置页登记、无人读取：把它关掉后三个维度
            // 仍然生效，管理员以为「关了限流」其实没关。这里才是它唯一的生效点。
            if (!enabled() || !matches(request)
                    || !globalSettingReader().getBool("security", "security_rate_limit_enabled", true)) {
                safePass(request, response, chain);
                return;
            }
            String appKey = resolveAppKey(request);
            int qpm = globalSettingReader().getInt("security", "security_rate_limit_qpm", 60);
            if (qpm > 0 && StringUtils.hasText(appKey)) {
                if (!rateLimiter.allow("key:" + appKey, qpm)) {
                    log.warn("[GatewayRateLimitFilter] APPKey 滑动窗口超限: {}", appKey);
                    rejectTooMany(request, response, "APPKey 限流 " + qpm + "/min");
                    return;
                }
            }
            // 2、IP 维度（所有未携带 APPKey 的控制台流量、入站流都受 IP 约束）
            int perIpQpm = globalSettingReader().getInt("security", "security_rate_limit_per_ip_qpm", 200);
            String clientIp = StringUtils.trimToNull(IpUtils.getClientIp(request));
            if (perIpQpm > 0 && StringUtils.hasText(clientIp)) {
                if (!rateLimiter.allow("ip:" + clientIp, perIpQpm)) {
                    log.warn("[GatewayRateLimitFilter] IP 滑动窗口超限: {}", clientIp);
                    rejectTooMany(request, response, "IP 限流 " + perIpQpm + "/min");
                    return;
                }
            }
            // 3、Path 维度
            int perPathQpm = globalSettingReader().getInt("security", "security_rate_limit_per_path_qpm", 600);
            if (perPathQpm > 0) {
                if (!rateLimiter.allow("path:" + request.getRequestURI(), perPathQpm)) {
                    log.warn("[GatewayRateLimitFilter] Path 滑动窗口超限: {}", request.getRequestURI());
                    rejectTooMany(request, response, "Path 限流 " + perPathQpm + "/min");
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("[GatewayRateLimitFilter] 拦截异常，放行: {}", e.getMessage());
        }
        safePass(request, response, chain);
    }
}
