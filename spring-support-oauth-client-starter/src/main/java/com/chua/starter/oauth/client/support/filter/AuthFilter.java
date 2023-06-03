package com.chua.starter.oauth.client.support.filter;


import com.chua.common.support.log.Log;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.web.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
        if (log.isDebugEnabled()) {
            log.debug("拦截到请求: {}", request instanceof HttpServletRequest ? ((HttpServletRequest) request).getRequestURI() : request.getRemoteAddr());
        }
        WebRequest webRequest = new WebRequest(this.webRequest.getAuthProperties(), (HttpServletRequest) request, requestMappingHandlerMapping);
        if (webRequest.isPass()) {
            chain.doFilter(request, response);
            return;
        }

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

        webRequest.doChain(chain, (HttpServletResponse) response);
    }
}