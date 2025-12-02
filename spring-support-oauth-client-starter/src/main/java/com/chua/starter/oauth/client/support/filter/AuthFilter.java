package com.chua.starter.oauth.client.support.filter;


import com.chua.common.support.log.Log;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.principal.OAuthPrincipal;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
import com.chua.starter.oauth.client.support.wrapper.OAuthHttpServletRequestWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.Objects;

/**
 * *
 * 鉴权拦截器
 *
 * @author CH
 */
public class AuthFilter implements Filter {

    private static final Log log = Log.getLogger(AuthFilter.class);

    private final WebRequest webRequest;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public AuthFilter(WebRequest webRequest, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.webRequest = webRequest;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        WebRequest webRequest = new WebRequest(this.webRequest.getAuthProperties(), httpRequest, requestMappingHandlerMapping);

        httpRequest.getSession().setAttribute("codec", true);

        if (webRequest.isPass()) {
            httpRequest.getSession().setAttribute("codec", false);
            chain.doFilter(request, response);
            return;
        }

        log.info("拦截到请求: {}", httpRequest.getRequestURI());

        if (webRequest.isFailure()) {
            webRequest.doFailureChain(chain, (HttpServletResponse) response);
            return;
        }

        //鉴权
        AuthenticationInformation authentication = webRequest.authentication();
        if (!Objects.equals(authentication.getInformation().getCode(), 200)) {
            webRequest.doFailureChain(chain, (HttpServletResponse) response, authentication.getInformation());
            return;
        }

        // 渲染用户信息到Session
        render(authentication, httpRequest);

        // 创建增强的HttpServletRequestWrapper，集成Principal支持
        UserResume userResume = authentication.getReturnResult();
        String authType = determineAuthType(webRequest);
        OAuthHttpServletRequestWrapper wrappedRequest = OAuthHttpServletRequestWrapper.authenticated(
            httpRequest, userResume, authType);

        // 设置 userId 到 request 属性，供拦截器和业务代码使用
        if (userResume != null && userResume.getUserId() != null) {
            wrappedRequest.setAttribute("userId", userResume.getUserId());
        }

        log.debug("创建OAuth增强请求包装器 - 用户: {}, 用户ID: {}, 认证类型: {}",
                 userResume != null ? userResume.getUsername() : "anonymous",
                 userResume != null ? userResume.getUserId() : null,
                 authType);

        // 使用包装后的请求继续过滤链
        chain.doFilter(wrappedRequest, response);
    }

    /**
     * 确定认证类型
     *
     * @param webRequest Web请求
     * @return 认证类型
     */
    private String determineAuthType(WebRequest webRequest) {
        // 根据OAuth客户端配置确定认证类型
        String protocol = webRequest.getAuthProperties().getProtocol();
        if (protocol != null) {
            switch (protocol.toLowerCase()) {
                case "http":
                case "lite":
                    return "OAUTH_HTTP";
                case "static":
                    return "OAUTH_STATIC";
                case "websocket":
                    return "OAUTH_WEBSOCKET";
                default:
                    return "OAUTH_" + protocol.toUpperCase();
            }
        }
        return "OAUTH";
    }

    private void render(AuthenticationInformation authentication, HttpServletRequest request) {
        if(authentication.getInformation() != Information.OK) {
            return;
        }
        HttpSession session = request.getSession();
        UserResume userResume = authentication.getReturnResult();

        // 存储用户信息到Session
        session.setAttribute("username", userResume.getUsername());
        session.setAttribute("userId", MapUtils.getString(userResume.getExt(), "userId"));
        session.setAttribute("userResume", userResume);

        // 创建并存储Principal
        String authType = determineAuthType(new WebRequest(this.webRequest.getAuthProperties(), request, requestMappingHandlerMapping));
        OAuthPrincipal principal = OAuthPrincipal.authenticated(userResume, authType);
        session.setAttribute("principal", principal);

        log.debug("用户信息已存储到Session - 用户: {}, ID: {}, 认证类型: {}",
                 userResume.getUsername(), userResume.getUserId(), authType);
    }
}