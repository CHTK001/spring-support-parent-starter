package com.chua.starter.oauth.client.support.web;

import com.chua.common.support.core.annotation.Ignore;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.core.utils.ObjectUtils;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
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
    private final String protocol;


    public WebRequest(AuthClientProperties authProperties, HttpServletRequest request, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.authProperties = authProperties;
        this.protocol = authProperties.getProtocol();
        this.request = request;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.contextPath = SpringBeanUtils.getEnvironment().resolvePlaceholders("${server.servlet.context-path:}");
        
        if (request != null) {
            log.debug("[WebRequest]创建WebRequest - URI: {}, 协议: {}, 上下文路径: {}", 
                     request.getRequestURI(), protocol, contextPath);
        }
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
        String uri = request.getRequestURI();
        log.debug("[WebRequest白名单]开始检查白名单 - URI: {}", uri);
        
        if (!authProperties.isEnable()) {
            log.debug("[WebRequest白名单]OAuth认证已禁用，直接放行 - URI: {}", uri);
            return true;
        }
        List<String> whitelist = authProperties.getWhitelist();
        if (null == whitelist) {
            log.debug("[WebRequest白名单]白名单为空，需要认证 - URI: {}", uri);
            return false;
        }

        uri = StringUtils.isNotBlank(contextPath) ? StringUtils.removeStart(uri, contextPath) : uri;
        if(isResource(uri)) {
            log.debug("[WebRequest白名单]静态资源放行 - URI: {}", uri);
            return true;
        }

        for (String s : whitelist) {
            if (PATH_MATCHER.match(s, uri)) {
                log.debug("[WebRequest白名单]匹配白名单规则放行 - URI: {}, 规则: {}", uri, s);
                return true;
            }
        }

        String authUrl = authProperties.getLoginPage();
        if (uri.equalsIgnoreCase(authUrl)) {
            log.debug("[WebRequest白名单]登录页面放行 - URI: {}", uri);
            return true;
        }

        String authUrl1 = authProperties.getNoPermissionPage();
        if (uri.equalsIgnoreCase(authUrl1)) {
            log.debug("[WebRequest白名单]无权限页面放行 - URI: {}", uri);
            return true;
        }

        if (null != requestMappingHandlerMapping) {
            HandlerMethod handlerMethod = null;
            try {
                handlerMethod = (HandlerMethod) requestMappingHandlerMapping.getHandler(request).getHandler();
            } catch (HttpRequestMethodNotSupportedException exception) {
                log.warn("[WebRequest白名单]请求方法不支持 - URI: {}, 方法: {}", uri, request.getMethod());
                try {
                    throw exception;
                } catch (HttpRequestMethodNotSupportedException e) {
                    throw new RuntimeException(e.getMessage());
                }
            } catch (Exception ignored) {
            }
            if (null != handlerMethod) {
                if (PASS.containsKey(handlerMethod)) {
                    log.debug("[WebRequest白名单]缓存命中放行 - URI: {}, 方法: {}", uri, handlerMethod.getMethod().getName());
                    return true;
                }
                Method method = handlerMethod.getMethod();
                boolean annotationPresent = method.isAnnotationPresent(TokenForIgnore.class);
                if (annotationPresent) {
                    PASS.put(handlerMethod, handlerMethod);
                    log.debug("[WebRequest白名单]@TokenForIgnore注解放行(方法) - URI: {}, 方法: {}", uri, method.getName());
                    return true;
                }
                boolean annotationPresent11 = method.isAnnotationPresent(Ignore.class);
                if (annotationPresent11) {
                    PASS.put(handlerMethod, handlerMethod);
                    log.debug("[WebRequest白名单]@Ignore注解放行(方法) - URI: {}, 方法: {}", uri, method.getName());
                    return true;
                }

                Class<?> beanType = handlerMethod.getBeanType();
                boolean annotationPresent1 = beanType.isAnnotationPresent(TokenForIgnore.class);
                if (annotationPresent1) {
                    PASS.put(handlerMethod, handlerMethod);
                    log.debug("[WebRequest白名单]@TokenForIgnore注解放行(类) - URI: {}, 类: {}", uri, beanType.getSimpleName());
                    return true;
                }

                boolean annotationPresent12 = beanType.isAnnotationPresent(Ignore.class);
                if (annotationPresent12) {
                    PASS.put(handlerMethod, handlerMethod);
                    log.debug("[WebRequest白名单]@Ignore注解放行(类) - URI: {}, 类: {}", uri, beanType.getSimpleName());
                    return true;
                }
            }
        }

        log.debug("[WebRequest白名单]未匹配任何白名单规则，需要认证 - URI: {}", uri);
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
        String uri = request != null ? request.getRequestURI() : "unknown";
        log.debug("[WebRequest Token检查]开始检查Token - URI: {}", uri);
        
        if (Strings.isNullOrEmpty(authProperties.getLoginAddress())) {
            log.error("[WebRequest Token检查]登录地址未配置 - URI: {}", uri);
            return true;
        }
        
        //判断cookie
        Cookie[] tokenCookie = getCookie();
        String token = getToken();
        
        boolean hasCookie = tokenCookie != null && tokenCookie.length > 0;
        boolean hasToken = !Strings.isNullOrEmpty(token);
        
        log.debug("[WebRequest Token检查]Token状态 - URI: {}, Cookie数量: {}, Token存在: {}", 
                 uri, 
                 tokenCookie != null ? tokenCookie.length : 0, 
                 hasToken);
        
        if (!hasCookie && !hasToken) {
            log.debug("[WebRequest Token检查]未找到有效Token - URI: {}", uri);
            return true;
        }
        
        log.debug("[WebRequest Token检查]找到有效Token - URI: {}, 来源: {}", 
                 uri, hasToken ? "Header/Parameter" : "Cookie");
        return false;
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
     * 鉴权（支持指定协议）- 高性能版本
     *
     * @return 鉴权信息
     */
    public AuthenticationInformation authentication() {
        String uri = request != null ? request.getRequestURI() : "unknown";
        long startTime = System.currentTimeMillis();
        
        log.debug("[WebRequest鉴权]========== 开始鉴权 ==========");
        log.debug("[WebRequest鉴权]请求URI: {}, 协议: {}", uri, protocol);
        
        // 快速获取认证数据
        Cookie[] cookie = getCookie();
        String token = getToken();
        
        log.debug("[WebRequest鉴权]Token信息 - Cookie数量: {}, Token长度: {}", 
                 cookie != null ? cookie.length : 0, 
                 token != null ? token.length() : 0);
        
        // 快速确定协议类型
        log.debug("[WebRequest鉴权]使用协议: {}", protocol);
        
        String oauthProtocol = request.getHeader("x-oauth-protocol");
        if (oauthProtocol != null) {
            log.debug("[WebRequest鉴权]请求头指定协议: {}", oauthProtocol);
        }
        
        // 从Header读取认证类型（x-oauth-type用于认证类型：oauth2、appkey、jwt等）
        String authType = request.getHeader("x-oauth-type");
        if (authType != null) {
            log.debug("[WebRequest鉴权]请求头指定认证类型: {}", authType);
        }

        if(isPass()) {
            return AuthenticationInformation.passAuthenticationInformation();
        }
        
        AuthenticationInformation result = ServiceProvider.of(Protocol.class)
                .getNewExtension(protocol, authProperties)
                .approve(cookie, token, authType);
        
        long costTime = System.currentTimeMillis() - startTime;
        
        if (result != null && result.getInformation() != null) {
            if (result.getInformation().getCode() == 200) {
                log.info("[WebRequest鉴权]鉴权成功 - URI: {}, 用户: {}, 耗时: {}ms", 
                        uri, 
                        result.getReturnResult() != null ? result.getReturnResult().getUsername() : "unknown",
                        costTime);
                log.debug("[WebRequest鉴权]用户详情 - 用户ID: {}, 是否管理员: {}", 
                         result.getReturnResult() != null ? result.getReturnResult().getUserId() : null,
                         result.getReturnResult() != null && result.getReturnResult().isAdmin());
            } else {
                log.warn("[WebRequest鉴权]鉴权失败 - URI: {}, 状态码: {}, 消息: {}, 耗时: {}ms", 
                        uri, 
                        result.getInformation().getCode(),
                        result.getInformation().getMessage(),
                        costTime);
            }
        } else {
            log.error("[WebRequest鉴权]鉴权结果为空 - URI: {}, 耗时: {}ms", uri, costTime);
        }
        
        log.debug("[WebRequest鉴权]========== 鉴权完成 ==========");
        return result;
    }

    /**
     * 获取用户信息
     *
     * @param appKeySecret 用户编码
     * @return 用户信息
     */
    public AuthenticationInformation authentication(AppKeySecret appKeySecret) {
        // 快速获取认证数据
        Cookie[] cookie = getCookie();
        String token = getToken();
        // 快速确定协议类型
        log.debug("获取默认协议: {}", protocol);
        return ServiceProvider.of(Protocol.class).getNewExtension(protocol, authProperties).authentication(appKeySecret);
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
     * 刷新token（支持指定协议）- 高性能版本
     *
     * @param upgradeType   升级类型
     * @param refreshToken  刷新令牌
     * @return 登录结果
     */
    public LoginResult upgrade(UpgradeType upgradeType, String refreshToken) {
        // 快速获取认证数据
        Cookie[] cookie = getCookie();
        String token = getToken();

        // 快速检查是否有可用的认证数据
        if ((cookie == null || cookie.length == 0) && (token == null || token.isEmpty())) {
            return null;
        }
        return ServiceProvider.of(Protocol.class).getNewExtension(protocol, authProperties).upgrade(cookie, token,
                upgradeType, refreshToken);
    }


}
