package com.chua.starter.oauth.client.support.web;

import com.chua.common.support.annotations.Ignore;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.oauth.client.support.annotation.AuthIgnore;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.Protocol;
import com.google.common.base.Strings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * webrequest
 *
 * @author CH
 */
@Slf4j
public class WebRequest {
    @Getter
    private final AuthClientProperties authProperties;
    private final String contextPath;
    private final HttpServletRequest request;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();


    public WebRequest(AuthClientProperties authProperties, HttpServletRequest request, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.authProperties = authProperties;
        this.request = request;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.contextPath = SpringBeanUtils.getEnvironment().resolvePlaceholders("${server.servlet.context-path:}");
    }

    public WebRequest(AuthClientProperties authProperties) {
        this(authProperties, null, null);
    }

    private static final Set<HandlerMethod> PASS = new LinkedHashSet<>();

    /**
     * 是否通过
     *
     * @return 是否通过
     */
    public boolean isPass() {
        List<String> whitelist = authProperties.getWhitelist();
        if (null == whitelist) {
            return false;
        }


        String uri = request.getRequestURI();
        uri = StringUtils.isNotBlank(contextPath) ? StringUtils.removeStart(uri, contextPath) : uri;
        if(isResource(uri)) {
            return true;
        }

        for (String s : whitelist) {
            if (PATH_MATCHER.match(s, uri)) {
                return true;
            }
        }

        String authUrl = authProperties.getLoginPage();
        if (uri.equalsIgnoreCase(authUrl)) {
            return true;
        }

        String authUrl1 = authProperties.getNoPermissionPage();
        if (uri.equalsIgnoreCase(authUrl1)) {
            return true;
        }

        if (null != requestMappingHandlerMapping) {
            HandlerMethod handlerMethod = null;
            try {
                handlerMethod = (HandlerMethod) requestMappingHandlerMapping.getHandler(request).getHandler();
            } catch (Exception ignored) {
            }
            if (null != handlerMethod) {
                if (PASS.contains(handlerMethod)) {
                    return true;
                }
                Method method = handlerMethod.getMethod();
                boolean annotationPresent = method.isAnnotationPresent(AuthIgnore.class);
                if (annotationPresent) {
                    PASS.add(handlerMethod);
                    return true;
                }
                boolean annotationPresent11 = method.isAnnotationPresent(Ignore.class);
                if (annotationPresent11) {
                    PASS.add(handlerMethod);
                    return true;
                }

                boolean annotationPresent1 = handlerMethod.getBeanType().isAnnotationPresent(AuthIgnore.class);
                if (annotationPresent1) {
                    PASS.add(handlerMethod);
                    return true;
                }

                boolean annotationPresent12 = handlerMethod.getBeanType().isAnnotationPresent(Ignore.class);
                if (annotationPresent12) {
                    PASS.add(handlerMethod);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isResource(String uri) {
        return uri.endsWith(".js") ||
                uri.endsWith(".css") ||
                uri.endsWith("favicon.ico");
    }

    /**
     * 鉴权失败
     *
     * @return 鉴权失败
     */
    public boolean isFailure() {
        if (Strings.isNullOrEmpty(authProperties.getLoginAddress())) {
            log.error("登录地址不存在");
            return true;
        }
        //判断cookie
        Cookie[] tokenCookie = getCookie();

        String token = getToken();

        return (null == tokenCookie || tokenCookie.length == 0) && Strings.isNullOrEmpty(token);
    }

    /**
     * 鉴权token
     *
     * @return token
     */
    private String getToken() {
        String header = request.getHeader(authProperties.getTokenName());
        return Strings.isNullOrEmpty(header) ? StringUtils.defaultString(
                request.getParameter(authProperties.getTokenName()),
                request.getAttribute(authProperties.getTokenName()) + ""
        ) : header;
    }

    /**
     * 鉴权token
     *
     * @return token
     */
    private Cookie[] getCookie() {
        return request.getCookies();
    }

    /**
     * 鉴权链路
     *
     * @param chain    链路
     * @param response 响应
     */
    public void doFailureChain(FilterChain chain, HttpServletResponse response) throws IOException, ServletException {
        WebResponse webResponse = new WebResponse(authProperties, chain, request, response);
        webResponse.doFailureChain(Information.AUTHENTICATION_FAILURE);
    }

    /**
     * 鉴权链路
     *
     * @param chain       链路
     * @param response    响应
     * @param information 状态码
     */
    public void doFailureChain(FilterChain chain, HttpServletResponse response, Information information) {
        WebResponse webResponse = new WebResponse(authProperties, chain, request, response);
        webResponse.doFailureChain(information);
    }

    /**
     * 鉴权
     *
     * @return 鉴权
     */
    public AuthenticationInformation authentication() {
        Cookie[] cookie = getCookie();
        String token = getToken();
        Protocol protocol = ServiceProvider.of(Protocol.class).getExtension(authProperties.getProtocol());
        return protocol.approve(cookie, token);
    }



    /**
     * 是嵌入
     *
     * @return boolean
     */
    public static boolean isEmbed(AuthClientProperties authProperties) {
        String oauthUrl = authProperties.getAddress();
        return StringUtils.isEmpty(oauthUrl);
    }

    /**
     * 成功
     *
     * @param chain    链路
     * @param response 响应
     */
    public void doChain(FilterChain chain, HttpServletResponse response) throws IOException, ServletException {
        chain.doFilter(request, response);
    }

    /**
     * 刷新token
     * @param upgradeType 升级类型
     */
    public void upgrade(UpgradeType upgradeType) {
        Cookie[] cookie = getCookie();
        String token = getToken();
        Protocol protocol = ServiceProvider.of(Protocol.class).getExtension(authProperties.getProtocol());
        protocol.upgrade(cookie, token, upgradeType);
    }
}
