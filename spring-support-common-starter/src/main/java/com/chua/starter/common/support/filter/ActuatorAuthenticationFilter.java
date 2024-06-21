package com.chua.starter.common.support.filter;

import com.chua.starter.common.support.properties.ActuatorProperties;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * ActuatorAuthenticationFilter 类用于实现对 Actuator 端点的认证过滤。
 * 该过滤器会检查对 Actuator 端点的请求是否经过了适当的认证，如果没有，则阻止访问。
 *
 * @author Administrator
 * @since 2024/6/21
 */
@Slf4j
@WebFilter(filterName = "ActuatorAuthenticationFilter", urlPatterns = {"/actuator/**"})
public class ActuatorAuthenticationFilter implements Filter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC = "Basic ";

    private final ActuatorProperties actuatorProperties;
    /**
     * 配置的actuator账号
     */
    private final String actuatorName;

    /**
     * 配置的actuator密码
     */
    private final String actuatorPassword;


    public ActuatorAuthenticationFilter(ActuatorProperties actuatorProperties) {
        this.actuatorProperties = actuatorProperties;
        this.actuatorName = actuatorProperties.getUsername();
        this.actuatorPassword = actuatorProperties.getPassword();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {
        if (!(servletRequest instanceof HttpServletRequest request) || !(servletResponse instanceof HttpServletResponse res) || !actuatorProperties.isEnable()) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length()).replaceAll("[/]+$", "");
        String headerAuthorization = request.getHeader(AUTHORIZATION);
        String encodeString = BASIC + Base64.getEncoder().encodeToString((actuatorName + ":" + actuatorPassword).getBytes(StandardCharsets.UTF_8));
        if (headerAuthorization != null && headerAuthorization.equals(encodeString)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        log.info("被拦截路径[{}]未获得访问权限", path);
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

}
