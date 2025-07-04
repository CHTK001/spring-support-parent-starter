package com.chua.starter.oauth.client.support.web;

import com.chua.common.support.annotations.Ignore;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.oauth.client.support.annotation.AuthIgnore;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.Protocol;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.google.common.base.Strings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

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

    private static final ConcurrentReferenceHashMap<HandlerMethod, HandlerMethod> PASS = new ConcurrentReferenceHashMap<>(1024);

    /**
     * 是否通过
     *
     * @return 是否通过
     */
    public boolean isPass() {
        if (!authProperties.isEnable()) {
            return true;
        }
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
            } catch (HttpRequestMethodNotSupportedException exception) {
                try {
                    throw exception;
                } catch (HttpRequestMethodNotSupportedException e) {
                    throw new RuntimeException(e);
                }
            } catch (Exception ignored) {
            }
            if (null != handlerMethod) {
                if (PASS.containsKey(handlerMethod)) {
                    return true;
                }
                Method method = handlerMethod.getMethod();
                boolean annotationPresent = method.isAnnotationPresent(AuthIgnore.class);
                if (annotationPresent) {
                    PASS.put(handlerMethod, handlerMethod);
                    return true;
                }
                boolean annotationPresent11 = method.isAnnotationPresent(Ignore.class);
                if (annotationPresent11) {
                    PASS.put(handlerMethod, handlerMethod);
                    return true;
                }

                Class<?> beanType = handlerMethod.getBeanType();
                boolean annotationPresent1 = beanType.isAnnotationPresent(AuthIgnore.class);
                if (annotationPresent1) {
                    PASS.put(handlerMethod, handlerMethod);
                    return true;
                }

                boolean annotationPresent12 = beanType.isAnnotationPresent(Ignore.class);
                if (annotationPresent12) {
                    PASS.put(handlerMethod, handlerMethod);
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
                ObjectUtils.defaultIfStringNull(request.getAttribute(authProperties.getTokenName()), null)
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
     * 协议实例缓存
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Protocol> PROTOCOL_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>(8);
    private static final long DEFAULT_PROTOCOL_CACHE_TTL = 30000; // 30秒缓存
    /**
     * 默认协议缓存
     */
    private volatile String cachedDefaultProtocol;
    private volatile long defaultProtocolCacheTime;

    /**
     * 鉴权
     *
     * @return 鉴权信息
     */
    public AuthenticationInformation authentication() {
        return authentication(null);
    }

    /**
     * 鉴权（支持指定协议）- 高性能版本
     *
     * @param oauthProtocol OAuth协议类型，如果为null则使用默认协议
     * @return 鉴权信息
     */
    public AuthenticationInformation authentication(String oauthProtocol) {
        // 快速获取认证数据
        Cookie[] cookie = getCookie();
        String token = getToken();

        // 快速确定协议类型
        String protocolType = determineProtocolTypeFast(oauthProtocol);

        // 直接获取协议实现（使用高性能ServiceProvider）
        Protocol protocol = getProtocolFast(protocolType);

        return protocol.approve(cookie, token);
    }

    /**
     * 获取协议实现（高性能版本）
     *
     * @param protocolType 协议类型
     * @return 协议实现
     */
    private Protocol getProtocolFast(String protocolType) {
        // 尝试从缓存获取
        Protocol protocol = PROTOCOL_CACHE.get(protocolType);
        if (protocol != null) {
            return protocol;
        }

        // 缓存未命中，从ServiceProvider加载
        protocol = ServiceProvider.of(Protocol.class).getExtension(protocolType);
        if (protocol != null) {
            // 缓存协议实例
            PROTOCOL_CACHE.put(protocolType, protocol);
            return protocol;
        }

        // 如果不是默认协议，尝试获取默认协议
        String defaultProtocol = getDefaultProtocolCached();
        if (!defaultProtocol.equals(protocolType)) {
            return getProtocolFast(defaultProtocol);
        }

        // 最后的备选方案
        protocol = ServiceProvider.of(Protocol.class).getExtension("http");
        if (protocol != null) {
            PROTOCOL_CACHE.put("http", protocol);
            return protocol;
        }

        throw new IllegalStateException("无法找到任何可用的协议实现");
    }

    /**
     * 确定使用的协议类型（高性能版本）
     *
     * @param oauthProtocol 指定的OAuth协议
     * @return 最终使用的协议类型
     */
    private String determineProtocolTypeFast(String oauthProtocol) {
        // 1. 优先使用方法参数指定的协议（最快路径）
        if (oauthProtocol != null && !oauthProtocol.isEmpty()) {
            return oauthProtocol;
        }

        // 2. 快速检查请求对象是否存在
        if (request == null) {
            return getDefaultProtocolCached();
        }

        // 3. 检查请求头中的x-oauth-protocol参数（常用路径）
        String headerProtocol = request.getHeader("x-oauth-protocol");
        if (headerProtocol != null && !headerProtocol.isEmpty()) {
            return headerProtocol;
        }

        // 4. 检查请求参数中的x-oauth-protocol（备用路径）
        String paramProtocol = request.getParameter("x-oauth-protocol");
        if (paramProtocol != null && !paramProtocol.isEmpty()) {
            return paramProtocol;
        }

        // 5. 返回默认协议
        return getDefaultProtocolCached();
    }

    /**
     * 确定使用的协议类型（兼容性方法）
     */
    private String determineProtocolType(String oauthProtocol) {
        return determineProtocolTypeFast(oauthProtocol);
    }

    /**
     * 获取默认协议（带缓存优化）
     *
     * @return 默认协议类型
     */
    private String getDefaultProtocolCached() {
        long currentTime = System.currentTimeMillis();

        // 检查缓存是否有效
        if (cachedDefaultProtocol != null &&
                (currentTime - defaultProtocolCacheTime) < DEFAULT_PROTOCOL_CACHE_TTL) {
            return cachedDefaultProtocol;
        }

        // 更新缓存
        String defaultProtocol = getDefaultProtocol();
        cachedDefaultProtocol = defaultProtocol;
        defaultProtocolCacheTime = currentTime;

        return defaultProtocol;
    }

    /**
     * 获取默认协议
     *
     * @return 默认协议类型
     */
    private String getDefaultProtocol() {
        // 使用局部变量避免多次属性访问
        String defaultProtocol = authProperties != null ? authProperties.getProtocol() : null;
        return (defaultProtocol != null && !defaultProtocol.isEmpty()) ? defaultProtocol : "custom";
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
     *
     * @param upgradeType  升级类型
     * @param refreshToken 刷新令牌
     * @return 登录结果
     */
    public LoginResult upgrade(UpgradeType upgradeType, String refreshToken) {
        return upgrade(upgradeType, refreshToken, null);
    }

    /**
     * 刷新token（支持指定协议）- 高性能版本
     *
     * @param upgradeType   升级类型
     * @param refreshToken  刷新令牌
     * @param oauthProtocol OAuth协议类型，如果为null则使用默认协议
     * @return 登录结果
     */
    public LoginResult upgrade(UpgradeType upgradeType, String refreshToken, String oauthProtocol) {
        // 快速获取认证数据
        Cookie[] cookie = getCookie();
        String token = getToken();

        // 快速检查是否有可用的认证数据
        if ((cookie == null || cookie.length == 0) && (token == null || token.isEmpty())) {
            return null;
        }

        // 快速确定协议类型
        String protocolType = determineProtocolTypeFast(oauthProtocol);

        // 直接获取协议实现
        Protocol protocol = getProtocolFast(protocolType);

        return protocol.upgrade(cookie, token, upgradeType, refreshToken);
    }
}
