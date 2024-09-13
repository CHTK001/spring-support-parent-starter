package com.chua.starter.common.support.filter;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.ArrayUtils;
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
    private final ActuatorProperties.Type[] filters;
    private final String[] whitelist;


    public ActuatorAuthenticationFilter(ActuatorProperties actuatorProperties) {
        this.actuatorProperties = actuatorProperties;
        this.filters = actuatorProperties.getFilters();
        this.whitelist = actuatorProperties.getWhitelist();
        this.actuatorName = actuatorProperties.getUsername();
        this.actuatorPassword = actuatorProperties.getPassword();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {
        if (!(servletRequest instanceof HttpServletRequest request) || !(servletResponse instanceof HttpServletResponse res) || !actuatorProperties.isEnable()) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        boolean matchAccount = matchesAccount(request);
        boolean matchIp = matchesIp(request);
        if(matchAccount && matchIp) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length()).replaceAll("[/]+$", "");

        log.info("被拦截路径[{}]未获得访问权限", path);
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private boolean matchesIp(HttpServletRequest request) {
        if(!ArrayUtils.contains(filters, ActuatorProperties.Type.IP)) {
            return true;
        }

        String remoteAddress = request.getRemoteAddr();
        if(NetUtils.isAnyHost(remoteAddress)) {
            return true;
        }

        if(NetUtils.isLocalHost(remoteAddress)) {
            return true;
        }

        if(ArrayUtils.isEmpty(whitelist)) {
            return false;
        }

        for (String s : whitelist) {
            if(isMatch(s, remoteAddress)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 匹配IP
     * @param s s
     * @param remoteAddr remoteAddr
     * @return boolean
     */
    private boolean isMatch(String s, String remoteAddr) {
        if(s.contains(CommonConstant.SYMBOL_ASTERISK)) {
            return PathMatcher.INSTANCE.match(s, remoteAddr);
        }

        return s.equals(remoteAddr);
    }

    /**
     * 匹配账号
     * @param request request
     * @return boolean
     */
    private boolean matchesAccount(HttpServletRequest request) {
        if(!ArrayUtils.contains(filters, ActuatorProperties.Type.ACCOUNT)) {
            return true;
        }
        String headerAuthorization = request.getHeader(AUTHORIZATION);
        String encodeString = getKey(actuatorName, actuatorPassword);
        return headerAuthorization != null && headerAuthorization.equals(encodeString);
    }

    public static String getKey(String actuatorName, String actuatorPassword) {
        return BASIC + Base64.getEncoder().encodeToString((actuatorName + ":" + actuatorPassword).getBytes(StandardCharsets.UTF_8));
    }

}
