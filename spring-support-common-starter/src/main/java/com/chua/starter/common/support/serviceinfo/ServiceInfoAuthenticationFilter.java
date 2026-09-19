package com.chua.starter.common.support.serviceinfo;

import com.chua.common.support.constant.CommonConstant;
import com.chua.starter.common.support.utils.IpUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/**
 * 服务信息 端点认证过滤器。
 *
 * <p>对 {@code /service-info/**} 端点进行 Basic Auth 认证,
 * 防止未授权访问应用内部端点信息。</p>
 *
 * <p>使用 {@link ServiceInfoProperties} 配置:
 * <ul>
 *     <li>{@code plugin.service-info.auth.enable} = true 开启认证 (默认 false)</li>
 *     <li>{@code plugin.service-info.auth.username} / {@code password}</li>
 *     <li>{@code plugin.service-info.auth.ip-whitelist} IP 白名单 (CIDR 支持)</li>
 * </ul>
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@WebFilter(filterName = "ServiceInfoAuthenticationFilter", urlPatterns = {"/service-info", "/service-info/**"})
public class ServiceInfoAuthenticationFilter implements Filter {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(ServiceInfoAuthenticationFilter.class);
    /** 授权 */
    private static final String AUTHORIZATION = "Authorization";
    /** 基础 */
    private static final String BASIC = "Basic ";

    /** 属性 */
    private final ServiceInfoProperties properties;

    /**
     * 创建 服务信息认证过滤器 实例
     * @param properties 属性
     */
    public ServiceInfoAuthenticationFilter(ServiceInfoProperties properties) {
        this.properties = properties;
    }

    @Override
    /** 执行过滤 */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!properties.isAuthEnable()) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(httpRequest);
        if (isIpWhitelisted(clientIp)) {
            chain.doFilter(request, response);
            return;
        }

        if (checkBasicAuth(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("[ServiceInfo] 拒绝未授权访问 {} from {}", httpRequest.getRequestURI(), clientIp);
        httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"ServiceInfo\"");
        httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    /**
     * 校验基础认证
     *
     * @param request 请求
     * @return 检查basic认证的结果
     */
    private boolean checkBasicAuth(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BASIC)) {
            return false;
        }
        try {
            String base64Credentials = authHeader.substring(BASIC.length());
            String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
            String[] values = credentials.split(CommonConstant.SYMBOL_COLON, 2);
            if (values.length != 2) {
                return false;
            }
            return properties.getAuthUsername().equals(values[0])
                    && properties.getAuthPassword().equals(values[1]);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 是否ipwhitelisted
     *
     * @param ip ip
     * @return 是否ipwhitelisted的结果
     */
    private boolean isIpWhitelisted(String ip) {
        Set<String> whitelist = properties.getAuthIpWhitelist();
        if (whitelist == null || whitelist.isEmpty() || ip == null) {
            return false;
        }
        return whitelist.stream().anyMatch(p -> IpUtils.matchIp(ip, p));
    }

    /**
     * 解析客户端ip
     *
     * @param request 请求
     * @return resolve客户端ip的结果
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
