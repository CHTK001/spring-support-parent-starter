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
 * 网关 IP 黑白名单过滤器。
 *
 * <p>仅做全局 ACL（不在本过滤器解 APPKey）：<br>
 * 1、全局白名单非空时，仅白名单内的客户端 IP 放行（黑名单不生效，白名单优先级最高）；<br>
 * 2、全局黑名单中的 IP 直接拒绝；<br>
 * 3、否则放行，交由后面的守卫与 {@code AiAccessGuardService} 做 APPKey + IP 白名单。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class GatewayIpFilter extends AbstractGatewaySecurityFilter {

    /**
     * 构造器注入：安全设置唯一来源。
     *
     * @param globalSettingReader 全局设置读取器
     */
    public GatewayIpFilter(GlobalSettingReader globalSettingReader) {
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
            // 只读，不把被拒的 IP 富到上游守卫造成附带效应
            String clientIp = StringUtils.trimToNull(IpUtils.getClientIp(request));
            if (!StringUtils.hasText(clientIp)) {
                safePass(request, response, chain);
                return;
            }
            // 1、白名单非空：仅白名单放行（黑名单不生效）
            String whitelist = globalSettingReader().getStr("security", "security_ip_global_whitelist");
            if (StringUtils.hasText(whitelist)) {
                if (!matchesIp(clientIp, whitelist)) {
                    rejectIp(request, response, "客户端 IP " + clientIp + " 不在全局白名单中");
                    return;
                }
            }
            // 2、黑名单
            String blacklist = globalSettingReader().getStr("security", "security_ip_global_blacklist");
            if (StringUtils.hasText(blacklist)) {
                if (matchesIp(clientIp, blacklist)) {
                    rejectIp(request, response, "客户端 IP " + clientIp + " 在黑名单中");
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("[GatewayIpFilter] 拦截异常，放行: {}", e.getMessage());
        }
        safePass(request, response, chain);
    }

    /**
     * 将逗号分隔的 IP 列出与客户端 IP 做白/黑名单匹配。
     *
     * @param clientIp  客户端 IP
     * @param patterns  模式列表（逗号分隔）
     * @return 匹配返回 true
     */
    private static boolean matchesIp(String clientIp, String patterns) {
        for (String pattern : patterns.split(",")) {
            String p = pattern.trim();
            if (StringUtils.hasText(p) && IpUtils.matchIp(clientIp, p)) {
                return true;
            }
        }
        return false;
    }
}
