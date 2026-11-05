// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.oauth.client.support.annotation.VerifyFingerprint;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.execute.AuthSessionUtils;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.principal.OAuthPrincipal;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
import com.chua.starter.oauth.client.support.wrapper.OAuthHttpServletRequestWrapper;
import com.google.common.collect.Sets;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * *
 * 鉴权拦截器
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class AuthFilter implements Filter {

    /**
     * Web请求
     */
    private final WebRequest webRequest;
    /**
     * 请求mapping处理器mapping
     */
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 本地管理员兜底只告警一次，避免每请求刷屏
     */
    private final AtomicBoolean localAdminFallbackWarned = new AtomicBoolean(false);

    
    /**
     * 缓存处理器方法是否需要指纹验证的结果
     * 使用并发引用哈希映射，线程安全且支持弱引用
     */
    private final ConcurrentReferenceHashMap<HandlerMethod, Boolean> fingerprintVerificationCache = 
            new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /**
     * 创建 认证过滤器 实例
     * @param webRequest web请求
     * @param requestMappingHandlerMapping 请求mapping处理器mapping
     * @param requestMappingHandlerMapping 请求mapping处理器mapping
     */
    public AuthFilter(WebRequest webRequest, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.webRequest = webRequest;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    /**
     * 动态添加白名单规则
     * <p>
     * 支持 Ant 风格的 URL 模式，例如 /v2/face/**，添加后立即生效，无需重启。
     * </p>
     *
     * @param pattern 白名单规则
     */
    public void addWhitelist(String pattern) {
        webRequest.getAuthProperties().addWhitelist(pattern);
        log.debug("[AuthFilter]动态添加白名单规则 - {}", pattern);
    }

    /**
     * 动态删除白名单规则
     * <p>
     * 删除后立即生效，无需重启。仅删除通过 {@link #addWhitelist(String)} 动态添加的规则。
     * </p>
     *
     * @param pattern 白名单规则
     */
    public void removeWhitelist(String pattern) {
        webRequest.getAuthProperties().removeWhitelist(pattern);
        log.debug("[AuthFilter]动态删除白名单规则 - {}", pattern);
    }

    /**
     * 清空所有动态添加的白名单规则
     */
    public void clearWhitelist() {
        webRequest.getAuthProperties().clearDynamicWhitelist();
        log.debug("[AuthFilter]清空动态白名单规则");
    }

    @Override
    /**
     * 初始化
     * @param filterConfig 参数
     * @return 返回值
     * @throws ServletException 异常
    */
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    /**
     * 执行过滤
     * @param chain 参数
     * @param request 参数
     * @param response 参数
     * @return 返回值
     * @throws IOException 异常
     * @throws ServletException 异常
    */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        String remoteAddr = httpRequest.getRemoteAddr();
        long startTime = System.currentTimeMillis();

        log.debug("[AuthFilter]========== 开始处理请求 ==========");
        log.debug("[AuthFilter]请求信息 - URI: {}, 方法: {}, 远程IP: {}", requestURI, method, remoteAddr);

        WebRequest webRequest = new WebRequest(this.webRequest.getAuthProperties(), httpRequest, requestMappingHandlerMapping);

        // 白名单检查
        if (webRequest.isPass()) {
            log.debug("[AuthFilter白名单]请求通过白名单 - URI: {}, 方法: {}", requestURI, method);
            log.debug("[AuthFilter白名单]跳过认证，直接放行");
            chain.doFilter(request, response);
            log.debug("[AuthFilter]请求处理完成(白名单) - URI: {}, 耗时: {}ms", requestURI, System.currentTimeMillis() - startTime);
            return;
        }

        log.debug("[AuthFilter拦截]拦截到需要认证的请求 - URI: {}, 方法: {}, IP: {}", requestURI, method, remoteAddr);

        // 本地管理员兜底：显式开启 plugin.oauth.local-admin-fallback 且本次请求没有有效令牌时，
        // 注入内置 admin 身份后继续。必须放在这里（令牌检查之前）：
        // 本过滤器是 Servlet Filter，早于所有 HandlerInterceptor，
        // 而 @Permission 的判定（ApiControlInterceptor / PermissionControlInterceptor）
        // 是在拦截器阶段读会话身份，兜底若晚于它们执行就永远不会被看到。
        if (isLocalAdminFallback() && webRequest.isFailure()) {
            applyLocalAdminFallback(httpRequest);
            chain.doFilter(request, response);
            log.debug("[AuthFilter]请求处理完成(本地管理员兜底) - URI: {}, 耗时: {}ms", requestURI, System.currentTimeMillis() - startTime);
            return;
        }

  // 令牌检查
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
            // 本地管理员兜底：鉴权失败同样放行。
            // 不能只判 isFailure()——WebRequest#getCookie 返回全部 Cookie（含 JSESSIONID），
            // 只要浏览器带着会话 Cookie，isFailure() 就误判为「已有 Token」而走到这里；
            // 典型的就是登录接口自身：先取验证码拿到 JSESSIONID，再调 /v1/users/login 换令牌。
            if (isLocalAdminFallback()) {
                applyLocalAdminFallback(httpRequest);
                chain.doFilter(request, response);
                log.debug("[AuthFilter]请求处理完成(本地管理员兜底-鉴权失败) - URI: {}, 耗时: {}ms", requestURI, System.currentTimeMillis() - startTime);
                return;
            }
            webRequest.doFailureChain(chain, (HttpServletResponse) response, authentication.getInformation());
            log.debug("[AuthFilter]请求处理完成(鉴权失败) - URI: {}, 耗时: {}ms", requestURI, System.currentTimeMillis() - startTime);
            return;
        }

        log.info("[AuthFilter鉴权成功]鉴权通过 - URI: {}", requestURI); // 验证浏览器指纹（仅对标记了@验证fingerprint注解的接口）
        UserResume userResume = authentication.getReturnResult();
        Information fingerprintResult = verifyFingerprint(httpRequest, userResume);
        if (fingerprintResult != null && fingerprintResult != Information.OK) {
            log.warn("[AuthFilter指纹验证失败]指纹验证未通过 - URI: {}, 状态码: {}, 消息: {}", 
                    requestURI, fingerprintResult.getCode(), fingerprintResult.getMessage());
            webRequest.doFailureChain(chain, (HttpServletResponse) response, fingerprintResult);
            log.debug("[AuthFilter]请求处理完成(指纹验证失败) - URI: {}, 耗时: {}ms", requestURI, System.currentTimeMillis() - startTime);
            return;
        }

 // 只读身份（管理员代入签发的临时令牌，或账号级 readonly）只允许读操作。
        // 为什么必须在过滤器里兜这一层：@Permission 的只读校验只对「标注了该注解」的
        // 类/方法生效，未标注的路径会完全绕过 PermissionInterceptor —— eduai 学生端
        // 就是这种情形，表现为「界面写着只读、POST 却能落库」。
        // 这里按 HTTP 方法做统一兜底，与 PermissionInterceptor 的判定口径一致。
        if (userResume != null && userResume.isReadonly() && !isSafeMethod(method)) {
            log.warn("[AuthFilter只读拦截]只读身份禁止写操作 - URI: {}, 方法: {}, 用户: {}",
                    requestURI, method, userResume.getUsername());
            writeReadOnlyDenied((HttpServletResponse) response);
            log.debug("[AuthFilter]请求处理完成(只读拦截) - URI: {}, 耗时: {}ms",
                    requestURI, System.currentTimeMillis() - startTime);
            return;
        }

 // 渲染用户信息到会话
        /**
         * render。
         * @param authentication 认证
         * @param httpRequest http请求
         */
        render(authentication, httpRequest);

 // 创建增强的httpservlet请求包装器，集成Principal支持
        String authType = determineAuthType(webRequest);

        log.debug("[AuthFilter包装]创建OAuth请求包装器 - 用户: {}, 认证类型: {}", 
                 userResume != null ? userResume.getUsername() : "anonymous", authType);

        OAuthHttpServletRequestWrapper wrappedRequest = OAuthHttpServletRequestWrapper.authenticated(
            httpRequest, userResume, authType);

 // 设置 用户标识 到 请求 属性，供拦截器和业务代码使用
        if (userResume != null) {
            if (userResume.getUserId() != null) {
                wrappedRequest.setAttribute("userId", userResume.getUserId());
                log.debug("[AuthFilter属性]设置userId到request属性 - userId: {}", userResume.getUserId());
            }
            if (userResume.getUsername() != null) {
                wrappedRequest.setAttribute("username", userResume.getUsername());
                log.debug("[AuthFilter属性]设置username到request属性 - username: {}", userResume.getUsername());
            }
        }

        log.debug("[AuthFilter完成]认证完成，继续处理请求 - 用户: {}, 用户ID: {}, URI: {}",
                 userResume != null ? userResume.getUsername() : "anonymous",
                 userResume != null ? userResume.getUserId() : null,
                 requestURI);

        // 将包装请求更新进 RequestContextHolder，使 RequestUtils / 业务层能取到认证 Principal 与用户属性
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(wrappedRequest, (HttpServletResponse) response));

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
 // 根据o认证客户端配置确定认证类型
        String protocol = webRequest.getAuthProperties().getProtocol();
        if (protocol != null) {
            return switch (protocol.toLowerCase()) {
                case "http", "http-lite", "lite" -> "OAUTH_HTTP";
                case "static" -> "OAUTH_STATIC";
                case "websocket" -> "OAUTH_WEBSOCKET";
                case "rsocket" -> "OAUTH_RSOCKET";
                default -> "OAUTH_" + protocol.toUpperCase();
            };
        }
        return "OAUTH";
    }

    /**
     * 是否安全方法（只读方法）。
     *
     * <p>与 {@code PermissionInterceptor} 的口径保持一致：GET/HEAD/OPTIONS 放行，
     * 其余（POST/PUT/DELETE/PATCH）视为写操作。</p>
     *
     * @param method HTTP 方法名
     * @return 安全方法返回 {@code true}
     */
    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    /**
     * 返回「只读身份禁止写操作」的 403 响应。
     *
     * <p>码值刻意用 {@link ReturnCode#ACCESS_DENIED}，而不是 403 的通用业务码：
     * 通用码属于「请重新登录」码段，前端收到会清空令牌；只读拦截是会话仍然有效期间的
     * 授权结果，用重新登录码会把「越权提示」变成「强制登出」。
     * 这与 {@code OauthExceptionAdvice} 对只读异常的处理保持同一约定。</p>
     *
     * @param response HTTP 响应
     * @throws IOException 写入响应体失败
     */
    private void writeReadOnlyDenied(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.toJson(
                ReturnResult.error(ReturnCode.ACCESS_DENIED.getCode(), "只读身份不允许执行写操作")));
    }

    /**
     * 是否开启本地管理员兜底（{@code plugin.oauth.local-admin-fallback}，默认关闭）。
     *
     * @return 开启返回 {@code true}
     */
    private boolean isLocalAdminFallback() {
        AuthClientProperties properties = OauthClientRuntimeContext.getAuthClientProperties();
        return properties != null && properties.isLocalAdminFallback();
    }

    /**
     * 注入内置 admin 兜底身份到当前会话。
     *
     * <p>角色与权限必须同时授予：{@code @Permission} 的 {@code role()} 与
     * {@code value()/permission()} 是两套独立判定依据，
     * {@code PermissionRules} 用「角色 ∪ 权限」，{@code PermissionInterceptor} 分开校验，
     * 只给角色会被权限判定拦下。</p>
     *
     * <p>注意：该开关只要开启，<b>任何</b>无法完成鉴权的请求（含携带无效令牌的请求）
     * 都会被提升为内置 admin。这是「本地开发 / 联调兜底」的既定语义，
     * 因此默认关闭，且严禁在对外环境开启。</p>
     *
     * @param request 当前请求，用于取得会话
     */
    private void applyLocalAdminFallback(HttpServletRequest request) {
        if (localAdminFallbackWarned.compareAndSet(false, true)) {
            log.warn("[AuthFilter本地管理员兜底]已开启 plugin.oauth.local-admin-fallback，"
                    + "无令牌请求将被放行为内置 admin，仅限本地开发与联调环境使用");
        }
        UserResume fallback = new UserResume();
        fallback.setUserId("1");
        fallback.setUid("1");
        fallback.setUsername(AuthConstant.BUILTIN_ADMIN_USERNAME);
        fallback.setRoles(Sets.newHashSet(AuthConstant.SUPER_ADMIN, Constant.ADMIN));
        fallback.setPermission(Sets.newHashSet(Constant.ADMIN));
        AuthSessionUtils.setUserInfo(fallback);
    }

    /**
     * Render
     *
     * @param authentication 认证
     * @param request 请求
     */
    private void render(AuthenticationInformation authentication, HttpServletRequest request) {
        if(authentication.getInformation() != Information.OK) {
            log.debug("[AuthFilter渲染]认证信息状态非OK，跳过渲染 - 状态: {}", authentication.getInformation());
            return;
        }
        
        log.debug("[AuthFilter渲染]开始渲染用户信息到Session");
        
        HttpSession session = request.getSession();
        UserResume userResume = authentication.getReturnResult();

 // 存储用户信息到会话
        session.setAttribute("username", userResume.getUsername());
        session.setAttribute("userId", MapUtils.getString(userResume.getExt(), "userId"));
        session.setAttribute("userResume", userResume);
        session.setAttribute(AuthSessionUtils.SESSION_USER_INFO, userResume);

 // 存储租户标识到会话
 // 必须在这里单独写一份：按租户切库的数据源过滤器读的是会话属性 tenantId，
 // 只写 userInfo 时它能取到，但一旦 AuthService 实现被替换 / 取不到用户上下文，
 // 就只能拿到空的 tenantId 而回退主库——那是把租户请求放进平台共享库。
        if (userResume.getTenantId() != null) {
            session.setAttribute(AuthSessionUtils.SESSION_TENANT_ID, userResume.getTenantId());
        }
        
 // 存储浏览器指纹到会话
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

        log.debug("[AuthFilter渲染]用户信息已存储到Session - 用户: {}, ID: {}, 认证类型: {}, 角色数: {}, 权限数: {}",
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
     * 2. 否则，只检查处理方法或其所在类是否标记了@验证fingerprint注解
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
            
 // 检查方法上是否有@验证fingerprint注解
            boolean requiresVerification = handlerMethod.hasMethodAnnotation(VerifyFingerprint.class);
            
 // 检查类上是否有@验证fingerprint注解
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
     * @param fingerprint fingerprint
     * @return maskFingerprint的结果
     */
    private String maskFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 8) {
            return "***";
        }
        return fingerprint.substring(0, 4) + "..." + fingerprint.substring(fingerprint.length() - 4);
    }
}