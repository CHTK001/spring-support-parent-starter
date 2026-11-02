package com.chua.starter.ai.support.security;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关 IP 黑白名单过滤器（顺序第二）。
 *
 * <p>仅做全局 ACL（不在本过滤器解 APPKey）：<br>
 * 1、全局白名单非空时，仅白名单内的客户端 IP 放行（黑名单不生效，白名单优先级最高）；<br>
 * 2、全局黑名单中的 IP 直接拒绝；<br>
 * 3、否则放行，交由后面的守卫与 {@code AiAccessGuardService} 做 APPKey + IP 白名单。</p>
 *
 * <h2>模式语法</h2>
 * <p>逗号分隔，单条支持四种写法（前三种植于 {@link IpUtils#matchIp}）：</p>
 * <ul>
 *   <li>精确：{@code 192.168.1.10}</li>
 *   <li>通配：{@code 192.168.1.*}</li>
 *   <li>网段：{@code 10.0.0.0/8}</li>
 *   <li>点分十进制前缀（本过滤器补充）：{@code 10.0.0.} —— 运维按段写名单时最常用的写法</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class GatewayIpFilter extends AbstractGatewaySecurityFilter {

    /**
     * 构造器注入：安全设置唯一来源。
     *
     * @param settings 安全设置快照
     */
    public GatewayIpFilter(SecuritySettings settings) {
        super(settings);
    }

    @Override
    protected boolean applies(HttpServletRequest request) {
        // 名单维度有自己的开关：总开关开着也可以单独关掉 ACL
        return super.applies(request) && settings().getBool("security_ip_enabled", true);
    }

    @Override
    protected boolean reject(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String clientIp = StringUtils.trimToNull(IpUtils.getClientIp(request));
        if (!StringUtils.hasText(clientIp)) {
            // 取不到客户端 IP（极少见）：不把「无法判定」升级成拒绝
            return false;
        }
        // 1、白名单非空：仅白名单放行（黑名单不生效，白名单优先级最高）
        String whitelist = settings().getStr("security_ip_global_whitelist");
        if (StringUtils.hasText(whitelist) && !matchesIp(clientIp, whitelist)) {
            log.warn("[GatewayIpFilter] IP 不在全局白名单: {}", clientIp);
            rejectIp(response, "客户端 IP 不在全局白名单中");
            return true;
        }
        // 2、黑名单
        String blacklist = settings().getStr("security_ip_global_blacklist");
        if (StringUtils.hasText(blacklist) && matchesIp(clientIp, blacklist)) {
            log.warn("[GatewayIpFilter] IP 命中全局黑名单: {}", clientIp);
            rejectIp(response, "客户端 IP 在黑名单中");
            return true;
        }
        return false;
    }

    /**
     * 把逗号分隔的模式列表与客户端 IP 做白/黑名单匹配。
     *
     * @param clientIp 客户端 IP
     * @param patterns 模式列表（逗号分隔）
     * @return 命中任一模式返回 true
     */
    static boolean matchesIp(String clientIp, String patterns) {
        for (String pattern : patterns.split(",")) {
            String p = pattern.trim();
            if (!StringUtils.hasText(p)) {
                continue;
            }
            if (IpUtils.matchIp(clientIp, p)) {
                return true;
            }
            // 补充点分十进制前缀写法（如 10.0.0.）：IpUtils.matchIp 不覆盖这种
            if (p.endsWith(".") && clientIp.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
