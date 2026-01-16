package com.chua.starter.common.support.actuator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Actuator认证过滤器
 * <p>
 * 用于对Actuator端点进行认证，防止未授权访问。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/6/21
 */
@WebFilter(filterName = "ActuatorAuthenticationFilter", urlPatterns = {"/actuator/**"})
public class ActuatorAuthenticationFilter implements Filter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ActuatorAuthenticationFilter.class);


    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC = "Basic ";

    private final ActuatorProperties actuatorProperties;

    public ActuatorAuthenticationFilter(ActuatorProperties actuatorProperties) {
        this.actuatorProperties = actuatorProperties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();

        // 检查是否在排除路径中
        if (isExcludedPath(requestUri)) {
            chain.doFilter(request, response);
            return;
        }

        // 如果未开启认证，直接放行
        if (!actuatorProperties.isEnableAuth()) {
            chain.doFilter(request, response);
            return;
        }

        // IP白名单检查
        String clientIp = RequestUtils.getIpAddress(httpRequest);
        if (isIpWhitelisted(clientIp)) {
            chain.doFilter(request, response);
            return;
        }

        // Basic认证
        String authHeader = httpRequest.getHeader(AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BASIC)) {
            String base64Credentials = authHeader.substring(BASIC.length());
            String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
            String[] values = credentials.split(CommonConstant.SYMBOL_COLON, 2);

            if (values.length == 2) {
                String username = values[0];
                String password = values[1];

                if (actuatorProperties.getUsername().equals(username)
                        && actuatorProperties.getPassword().equals(password)) {
                    chain.doFilter(request, response);
                    return;
                }
            }
        }

        // 认证失败
        httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"Actuator\"");
        httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    /**
     * 检查IP是否在白名单中
     *
     * @param ip IP地址
     * @return 是否在白名单中
     */
    private boolean isIpWhitelisted(String ip) {
        if (actuatorProperties.getIpWhitelist().isEmpty()) {
            return false;
        }
        return actuatorProperties.getIpWhitelist().contains(ip);
    }

    /**
     * 检查路径是否在排除列表中
     *
     * @param path 请求路径
     * @return 是否在排除列表中
     */
    private boolean isExcludedPath(String path) {
        if (actuatorProperties.getExcludePaths().isEmpty()) {
            return false;
        }
        for (String excludePath : actuatorProperties.getExcludePaths()) {
            if (PathMatcher.INSTANCE.match(excludePath, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成Basic认证头
     *
     * @param username 用户名
     * @param password 密码
     * @return Basic认证头字符串
     */
    public static String getKey(String username, String password) {
        String credentials = username + CommonConstant.SYMBOL_COLON + password;
        return BASIC + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
