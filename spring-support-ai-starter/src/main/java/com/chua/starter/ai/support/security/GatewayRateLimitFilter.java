package com.chua.starter.ai.support.security;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关 QPM 限流过滤器（顺序第三）。
 *
 * <p>三个滑动维度（窗口 = 60 秒，对应 Sub2API 的 per-key rate limit）：<br>
 * 1、APPKey 维度：{@code security_rate_limit_qpm}；<br>
 * 2、IP 维度：{@code security_rate_limit_per_ip_qpm}；<br>
 * 3、Path 维度：{@code security_rate_limit_per_path_qpm}。</p>
 *
 * <p>任一维度超限，写 JSON 429 并阻拦进入下游 Handler。未携带 APPKey 的管理控制台流量
 * 只受 IP 与 Path 维度约束；某维度配成 {@code <= 0} 表示该维度不限制，不会把拦截变成
 * 系统故障点。</p>
 *
 * <h2>判定顺序</h2>
 * <p>先 Key（最贵也最准）→ 再 IP → 最后 Path。任一维先拒就立即返回，省掉后续维度的
 * Redis 往返。</p>
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
     * @param settings    安全设置快照
     * @param rateLimiter QPM 限流器
     */
    public GatewayRateLimitFilter(SecuritySettings settings, GatewayRateLimiter rateLimiter) {
        super(settings);
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean applies(HttpServletRequest request) {
        return super.applies(request) && settings().getBool("security_rate_limit_enabled", true);
    }

    @Override
    protected boolean reject(HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 1、APPKey 维度
        String appKey = resolveAppKey(request);
        int qpm = settings().getInt("security_rate_limit_qpm", 60);
        if (qpm > 0 && StringUtils.hasText(appKey) && !rateLimiter.allow("key:" + appKey, qpm)) {
            log.warn("[GatewayRateLimitFilter] APPKey 滑动窗口超限: {}", appKey);
            rejectTooMany(response, qpm);
            return true;
        }
        // 2、IP 维度（未携带 APPKey 的控制台流量、入站流都受 IP 约束）
        int perIpQpm = settings().getInt("security_rate_limit_per_ip_qpm", 200);
        String clientIp = StringUtils.trimToNull(IpUtils.getClientIp(request));
        if (perIpQpm > 0 && StringUtils.hasText(clientIp) && !rateLimiter.allow("ip:" + clientIp, perIpQpm)) {
            log.warn("[GatewayRateLimitFilter] IP 滑动窗口超限: {}", clientIp);
            rejectTooMany(response, perIpQpm);
            return true;
        }
        // 3、Path 维度
        int perPathQpm = settings().getInt("security_rate_limit_per_path_qpm", 600);
        if (perPathQpm > 0 && !rateLimiter.allow("path:" + request.getRequestURI(), perPathQpm)) {
            log.warn("[GatewayRateLimitFilter] Path 滑动窗口超限: {}", request.getRequestURI());
            rejectTooMany(response, perPathQpm);
            return true;
        }
        return false;
    }
}
