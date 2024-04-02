package com.chua.starter.oauth.client.support.filter;


import com.chua.common.support.log.Log;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
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
        WebRequest webRequest = new WebRequest(this.webRequest.getAuthProperties(), (HttpServletRequest) request, requestMappingHandlerMapping);
        ((HttpServletRequest) request).getSession().setAttribute("codec", true);
        if (webRequest.isPass()) {
            ((HttpServletRequest) request).getSession().setAttribute("codec", false);
            chain.doFilter(request, response);
            return;
        }
        log.info("拦截到请求: {}", request instanceof HttpServletRequest ? ((HttpServletRequest) request).getRequestURI() : request.getRemoteAddr());
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
        render(authentication, (HttpServletRequest) request);
        webRequest.doChain(chain, (HttpServletResponse) response);
    }

    private void render(AuthenticationInformation authentication, HttpServletRequest request) {
        if(authentication.getInformation() != Information.OK) {
            return;
        }
        HttpSession session = request.getSession();
        UserResume userResume = authentication.getReturnResult();
        session.setAttribute("username", userResume.getUsername());
        session.setAttribute("userId", MapUtils.getString(userResume.getExt(), "userId"));
    }
}