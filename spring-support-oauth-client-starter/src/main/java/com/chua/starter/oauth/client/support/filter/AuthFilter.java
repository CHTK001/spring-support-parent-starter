package com.chua.starter.oauth.client.support.filter;


import com.chua.common.support.core.utils.MapUtils;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.oauth.client.support.annotation.VerifyFingerprint;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.Objects;

/**
 * *
 * 鉴权拦截器
 *
 * @author CH
 */
@Slf4j
public class AuthFilter implements Filter {

    private final WebRequest webRequest;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    
    /**
     * 缓存HandlerMethod是否需要指纹验证的结果
     * 使用ConcurrentReferenceHashMap，线程安全且支持弱引用
     */
    private final ConcurrentReferenceHashMap<HandlerMethod, Boolean> fingerprintVerificationCache = 
            new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

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
        String requestURI = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        String remoteAddr = httpRequest.getRemoteAddr();
        long startTime = System.currentTimeMillis();

        log.debug("[AuthFilter]========== 开始处理请求 ==========");
        log.debug("[AuthFilter]请求信息 - URI: {}, 方法: {}, 远程IP: {}", requestURI, method, remoteAddr);

        WebRequest webRequest = new WebRequest(this.webRequest.getAuthProperties(), httpRequest, requestMappingHandlerMapping);

        httpRequest.getSession().setAttribute("codec", true);

        // 白名单检查
        if (webRequest.isPass()) {
            httpRequest.getSession().setAttribute("codec", false);
            log.info("[AuthFilter白名单]请求通过白名单 - URI: {}, 方法: {}", requestURI, method);
            log.debug("[AuthFilter白名单]跳过认证，直接放行");
            chain.doFilter(request, response);
            log.debug("[AuthFilter]请求处理完成(白名单) - URI: {}, 耗时: {}ms", requestURI, System.currentTimeMillis() - startTime);
            return;
        }

        log.info("[AuthFilter拦截]拦截到需要认证的请求 - URI: {}, 方法: {}, IP: {}", requestURI, method, remoteAddr);

        // Token检查
        if (webRequest.isFailure()) {
            log.warn("[AuthFilter认证失败]未找到有效Token - URI: {}, 方法: {}", requestURI, method);
            log.debug("[AuthFilter认证失败]Token检查失败，执行失败链");
            webRequest.doFailureChain(chain, (HttpServletResponse) response);
            log.debug("[AuthFilter]请求处理完成(Token缺失) - URI: {}, 耗时: {}ms", requestURI, System.currentTimeMillis() - startTime);
            return;
        }

        // 执行鉴权
        log.debug("[AuthFilter鉴权]开始执行鉴权 - URI: {}", requestURI);
        AuthenticationInformation authentication = webRequest.authentication();

        if (!Objects.equals(authentication.getInformation().getCode(), 200)) {
            log.warn("[AuthFilter鉴权失败]鉴权未通过 - URI: {}, 状态码: {}, 消息: {}", 
                    requestURI, 
                    authentication.getInformation().getCode(),
                    authentication.getInformation().getMessage());
            webRequest.doFailureChain(chain, (HttpServletResponse) response, authentication.getInformation());
            log.debug("[AuthFilter]请求处理完成(鉴权失败) - URI: {}, 耗时: {}ms", requestURI, System.currentTimeMillis() - startTime);
            return;
        }

        log.info("[AuthFilter鉴权成功]鉴权通过 - URI: {}", requestURI);

        // 验证浏览器指纹（仅对标记了@VerifyFingerprint注解的接口）
        UserResume userResume = authentication.getReturnResult();
        Information fingerprintResult = verifyFingerprint(httpRequest, userResume);
        if (fingerprintResult != null && fingerprintResult != Information.OK) {
            log.warn("[AuthFilter指纹验证失败]指纹验证未通过 - URI: {}, 状态码: {}, 消息: {}", 
                    requestURI, fingerprintResult.getCode(), fingerprintResult.getMessage());
            webRequest.doFailureChain(chain, (HttpServletResponse) response, fingerprintResult);
            log.debug("[AuthFilter]请求处理完成(指纹验证失败) - URI: {}, 耗时: {}ms", requestURI, System.currentTimeMillis() - startTime);
            return;
        }

        // 渲染用户信息到Session
        render(authentication, httpRequest);

        // 创建增强的HttpServletRequestWrapper，集成Principal支持
        String authType = determineAuthType(webRequest);

        log.debug("[AuthFilter包装]创建OAuth请求包装器 - 用户: {}, 认证类型: {}", 
                 userResume != null ? userResume.getUsername() : "anonymous", authType);

        OAuthHttpServletRequestWrapper wrappedRequest = OAuthHttpServletRequestWrapper.authenticated(
            httpRequest, userResume, authType);

        // 设置 userId 到 request 属性，供拦截器和业务代码使用
        if (userResume != null && userResume.getUserId() != null) {
            wrappedRequest.setAttribute("userId", userResume.getUserId());
            log.debug("[AuthFilter属性]设置userId到request属性 - userId: {}", userResume.getUserId());
        }

        log.info("[AuthFilter完成]认证完成，继续处理请求 - 用户: {}, 用户ID: {}, URI: {}",
                 userResume != null ? userResume.getUsername() : "anonymous",
                 userResume != null ? userResume.getUserId() : null,
                 requestURI);

        // 使用包装后的请求继续过滤链
        chain.doFilter(wrappedRequest, response);

        log.debug("[AuthFilter]========== 请求处理完成 ==========");
        log.debug("[AuthFilter]请求处理完成 - URI: {}, 用户: {}, 总耗时: {}ms", 
                 requestURI, 
                 userResume != null ? userResume.getUsername() : "anonymous",
                 System.currentTimeMillis() - startTime);
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
            log.debug("[AuthFilter渲染]认证信息状态非OK，跳过渲染 - 状态: {}", authentication.getInformation());
            return;
        }
        
        log.debug("[AuthFilter渲染]开始渲染用户信息到Session");
        
        HttpSession session = request.getSession();
        UserResume userResume = authentication.getReturnResult();

        // 存储用户信息到Session
        session.setAttribute("username", userResume.getUsername());
        session.setAttribute("userId", MapUtils.getString(userResume.getExt(), "userId"));
        session.setAttribute("userResume", userResume);
        
        // 存储浏览器指纹到Session
        if (userResume.getFingerprint() != null) {
            session.setAttribute("x-oauth-fingerprint", userResume.getFingerprint());
            log.debug("[AuthFilter渲染]浏览器指纹已存储到Session");
        }
        
        log.debug("[AuthFilter渲染]Session属性设置 - username: {}, userId: {}", 
                 userResume.getUsername(), userResume.getUserId());

        // 创建并存储Principal
        String authType = determineAuthType(new WebRequest(this.webRequest.getAuthProperties(), request, requestMappingHandlerMapping));
        OAuthPrincipal principal = OAuthPrincipal.authenticated(userResume, authType);
        session.setAttribute("principal", principal);

        log.info("[AuthFilter渲染]用户信息已存储到Session - 用户: {}, ID: {}, 认证类型: {}, 角色数: {}, 权限数: {}",
                 userResume.getUsername(), 
                 userResume.getUserId(), 
                 authType,
                 userResume.getRoles() != null ? userResume.getRoles().size() : 0,
                 userResume.getPermission() != null ? userResume.getPermission().size() : 0);
        
        log.debug("[AuthFilter渲染]用户详细信息 - 昵称: {}, 租户ID: {}, 部门ID: {}, 是否管理员: {}",
                 userResume.getNickName(),
                 userResume.getTenantId(),
                 userResume.getDeptId(),
                 userResume.isAdmin());
    }

    /**
     * 验证浏览器指纹
     * <p>仅对标记了@VerifyFingerprint注解的接口进行指纹验证</p>
     *
     * @param request    HTTP请求
     * @param userResume 用户信息
     * @return 验证结果，null表示不需要验证或验证通过，否则返回错误信息
     */
    private Information verifyFingerprint(HttpServletRequest request, UserResume userResume) {
        // 检查当前请求对应的处理方法是否需要指纹验证
        if (!requiresFingerprintVerification(request)) {
            log.debug("[AuthFilter指纹验证]该接口未标记@VerifyFingerprint注解，跳过验证");
            return null;
        }
        
        // 获取请求中的指纹
        String requestFingerprint = request.getHeader("x-oauth-fingerprint");
        
        // 如果请求中没有携带指纹，返回缺少指纹错误
        if (StringUtils.isBlank(requestFingerprint)) {
            log.warn("[AuthFilter指纹验证]该接口需要指纹验证，但请求中未携带指纹");
            return Information.FINGERPRINT_MISSING;
        }
        
        // 如果用户信息为空或用户信息中没有存储指纹，跳过验证（向后兼容）
        if (userResume == null) {
            log.debug("[AuthFilter指纹验证]用户信息为空，跳过验证");
            return null;
        }
        
        String storedFingerprint = userResume.getFingerprint();
        if (StringUtils.isBlank(storedFingerprint)) {
            log.debug("[AuthFilter指纹验证]存储的指纹为空，跳过验证（向后兼容）");
            return null;
        }
        
        // 比对指纹
        if (!storedFingerprint.equals(requestFingerprint)) {
            log.warn("[AuthFilter指纹验证]指纹不匹配 - 存储: {}, 请求: {}", 
                    maskFingerprint(storedFingerprint), maskFingerprint(requestFingerprint));
            return Information.FINGERPRINT_MISMATCH;
        }
        
        log.debug("[AuthFilter指纹验证]指纹验证通过");
        return Information.OK;
    }
    
    /**
     * 检查当前请求是否需要指纹验证
     * <p>
     * 判断逻辑：
     * 1. 如果开启了全局指纹验证，则所有接口都需要验证
     * 2. 否则，只检查处理方法或其所在类是否标记了@VerifyFingerprint注解
     * </p>
     *
     * @param request HTTP请求
     * @return 是否需要指纹验证
     */
    private boolean requiresFingerprintVerification(HttpServletRequest request) {
        // 检查是否开启了全局指纹验证
        if (webRequest.getAuthProperties().getFingerprint() != null 
                && webRequest.getAuthProperties().getFingerprint().isGlobalVerification()) {
            log.debug("[AuthFilter指纹验证]已开启全局指纹验证");
            return true;
        }
        
        try {
            HandlerExecutionChain handlerChain = requestMappingHandlerMapping.getHandler(request);
            if (handlerChain == null) {
                return false;
            }
            
            Object handler = handlerChain.getHandler();
            if (!(handler instanceof HandlerMethod)) {
                return false;
            }
            
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            
            // 从缓存中获取结果
            Boolean cached = fingerprintVerificationCache.get(handlerMethod);
            if (cached != null) {
                return cached;
            }
            
            // 检查方法上是否有@VerifyFingerprint注解
            boolean requiresVerification = handlerMethod.hasMethodAnnotation(VerifyFingerprint.class);
            
            // 检查类上是否有@VerifyFingerprint注解
            if (!requiresVerification) {
                requiresVerification = handlerMethod.getBeanType().isAnnotationPresent(VerifyFingerprint.class);
            }
            
            // 缓存结果
            fingerprintVerificationCache.put(handlerMethod, requiresVerification);
            return requiresVerification;
        } catch (Exception e) {
            log.debug("[AuthFilter指纹验证]获取Handler失败，跳过指纹验证: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 脱敏指纹（用于日志输出）
     */
    private String maskFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 8) {
            return "***";
        }
        return fingerprint.substring(0, 4) + "..." + fingerprint.substring(fingerprint.length() - 4);
    }
}
