// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.web;

import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.chua.common.support.annotation.Ignore;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.execute.AuthSessionUtils;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.Protocol;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.impersonation.TemporaryToken;
import com.chua.starter.oauth.client.support.user.UserResume;
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
import java.util.Map;

/**
 * webrequest
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class WebRequest {

    @Getter
    /**
     * 认证属性
    */
    private final AuthClientProperties authProperties;
    /**
     * 上下文路径
    */
    private final String contextPath;
    /**
     * 请求
    */
    private final HttpServletRequest request;
    /**
     * 请求mapping处理器mapping
    */
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    /**
     * 路径_匹配
    */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    /**
     * 协议
    */
    private final String protocol;


    /**
     * 创建 web请求 实例
     * @param authProperties 认证属性
     * @param request httpservlet请求
     * @param requestMappingHandlerMapping 请求mapping处理器mapping
     * @param request 请求
     * @param requestMappingHandlerMapping 请求mapping处理器mapping
     */
    public WebRequest(AuthClientProperties authProperties, HttpServletRequest request, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.authProperties = authProperties;
        this.protocol = authProperties.getProtocol();
        this.request = request;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.contextPath = request != null && StringUtils.isNotBlank(request.getContextPath())
                ? request.getContextPath() : OauthClientRuntimeContext.getContextPath();
        
        if (request != null) {
            log.debug("[WebRequest]创建WebRequest - URI: {}, 协议: {}, 上下文路径: {}", 
                     request.getRequestURI(), protocol, contextPath);
        }
    }

    /**
     * 创建 web请求 实例
     * @param authProperties 认证属性
     */
    public WebRequest(AuthClientProperties authProperties) {
        /**
         * this。
         * @param authProperties 认证属性
         * @param null 空
         * @param null 空
         */
        this(authProperties, null, null);
    }

    private static final ConcurrentReferenceHashMap<HandlerMethod, HandlerMethod> PASS = new ConcurrentReferenceHashMap<>(1024); // 通过

    /**
     * 协议实例缓存（协议名+配置摘要 -> 协议实例），避免每次请求重复 SPI 查找与构造
     * <p>key 包含配置摘要，防止同协议名不同配置（如 address/oauthUrl/加密方式）复用同一实例导致串配置</p>
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Protocol> PROTOCOL_CACHE = new java.util.concurrent.ConcurrentHashMap<>(8);

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
        List<String> whitelist = authProperties.getEffectiveWhitelist();
        if (null == whitelist) {
            log.debug("[WebRequest白名单]白名单为空，需要认证 - URI: {}", uri);
            return false;
        }

        uri = StringUtils.isNotBlank(contextPath) && !"/".equals(contextPath) ? StringUtils.removeStart(uri, contextPath) : uri;
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
                // 路径有处理器但方法不支持：不是白名单命中，保持 handlerMethod 为空继续按需要认证处理，
                // 认证通过后由 SpringMVC 返回 405。在此抛运行时异常会让鉴权层把 405 变成 500
                log.warn("[WebRequest白名单]请求方法不支持 - URI: {}, 方法: {}", uri, request.getMethod());
            } catch (Exception e) {
                // 无处理器匹配（404 场景）：保持 handlerMethod 为空继续按需要认证处理
                log.debug("[WebRequest白名单]解析处理器失败，按非白名单处理 - URI: {}, 原因: {}", uri, e.getMessage());
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

    /**
     * 是否Resource
     *
     * @param uri uri
     * @return 是否resource的结果
     */
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
        
 // 判断Cookie
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
     * 鉴权令牌
     *
     * @return token
     */
    private String getToken() {
        String header = request.getHeader(authProperties.getTokenName());
        if (!Strings.isNullOrEmpty(header)) {
            return header;
        }

        String authorization = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }

        return StringUtils.defaultString(
                request.getParameter(authProperties.getTokenName()),
                ObjectUtils.defaultIfStringNull(request.getAttribute(authProperties.getTokenName()), null)
        );
    }

    /**
     * 鉴权令牌
     *
     * @return token
     */
    private Cookie[] getCookie() {
        return request.getCookies();
    }

    /**
     * 鉴权链路
     * @param chain 链路
     * @param response 响应
     * @return 返回值
     * @throws IOException 异常
     * @throws ServletException 异常
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
        
 // 从头部读取认证类型（x-OAuth-类型用于认证类型：OAuth2、appkey、JWT等）
        String authType = request.getHeader("x-oauth-type");
        if (authType != null) {
            log.debug("[WebRequest鉴权]请求头指定认证类型: {}", authType);
        }

        if(isPass()) {
            return AuthenticationInformation.passAuthenticationInformation();
        }

 // AI appKey 本地认证：Bearer 令牌 命中动态表则直接放行（单机/外部 o认证 不可达场景）
        AuthenticationInformation aiAppKeyResult = tryAuthenticateAiAppKey();
        if (aiAppKeyResult != null) {
            return aiAppKeyResult;
        }

        Protocol protocolExtension = resolveProtocol();
        if (protocolExtension == null) {
            log.error("[WebRequest鉴权]协议未注册 - 协议: {}, 可用协议: {}", protocol,
                    ServiceProvider.of(Protocol.class).names());
            return AuthenticationInformation.noAuth();
        }
        
        AuthenticationInformation result = protocolExtension.approve(cookie, token, authType);

        // 临时令牌（管理员代入）的有效期与路径校验。
        // 放在协议之外是必要的：static 在 approve 内部靠解密自包含令牌自查，
        // 而有状态协议（tcp）的令牌由服务端签发、其 ext 随 approve 结果返回；
        // 两条路必须汇到同一套规则，否则换协议就会静默丢掉限权。
        if (!isTemporaryTokenAllowed(result, uri)) {
            log.warn("[WebRequest鉴权]临时令牌无权访问该路径 - URI: {}", uri);
            return AuthenticationInformation.noAuth();
        }

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
     * 校验临时令牌（管理员代入）是否允许访问当前路径。
     *
     * <p>判定完全基于 approve 结果里的 {@code UserResult.ext}，不依赖令牌能否被本地解密，
     * 因此对「自包含令牌（static）」与「服务端存储令牌（tcp）」同样成立。</p>
     *
     * @param result approve 的结果
     * @param uri    本次请求路径
     * @return 允许通过返回 {@code true}；非临时令牌或校验失败情形下由各分支决定
     */
    private boolean isTemporaryTokenAllowed(AuthenticationInformation result, String uri) {
        if (result == null || result.getInformation() == null
                || result.getInformation().getCode() != 200) {
            // 鉴权本身未成功，交给既有失败链处理，这里不做二次判定
            return true;
        }
        UserResume granted = result.getReturnResult();
        if (granted == null || granted.getExt() == null
                || !Boolean.TRUE.equals(granted.getExt().get(TemporaryToken.EXT_TEMPORARY))) {
            // 非临时令牌不受路径与短期有效期限制
            return true;
        }
        // 经 JSON 往返转成 UserResult 再交给 TemporaryToken 判定：
        // UserResult 重复声明了 expireTime 等与父类同名的字段（字段遮蔽），
        // 若直接判断，取到的可能是父类的 null 而非实际值；
        // JSON 往返回按属性名序列化/反序列化，可绕开该错位。
        UserResult asResult = Json.fromJson(Json.toJson(granted), UserResult.class);
        if (asResult == null) {
            return true;
        }
        // 这里只校验路径，不校验有效期。
        // 原因：expireTime 的语义在两端并不统一——客户端 CredentialIssuer 存的是
        // 绝对时间戳，而服务端 RedisTokenResolver 存的是时长（如 2592000），
        // TemporaryToken#isUsable 按时间戳比较，会把服务端签发的令牌一律判为过期。
        // 有效期实际由更可靠的两层保证：static 令牌在 StaticProtocol 内已自查过期，
        // 服务端令牌则依赖 Redis TTL——过期后服务端查不到，approve 直接失败，
        // 根本走不到这里。
        return TemporaryToken.isPathAllowed(asResult, uri);
    }

    /**
     * 获取用户信息
     *
     * @param appKeySecret 用户编码
     * @return 用户信息
     */
    public AuthenticationInformation authentication(AppKeySecret appKeySecret) {
        // 快速确定协议类型
        log.debug("获取默认协议: {}", protocol);
        Protocol protocolExtension = resolveProtocol();
        if (protocolExtension == null) {
            log.error("[WebRequest鉴权]协议未注册 - 协议: {}, 可用协议: {}", protocol,
                    ServiceProvider.of(Protocol.class).names());
            return AuthenticationInformation.noAuth();
        }
        return protocolExtension.authentication(appKeySecret);
    }

    /**
     * 尝试使用 AI appKey 本地认证。
     * <p>
     * 启用 {@code plugin.oauth.ai-app-key.enabled} 时，对 Bearer 令牌 做本地动态表校验。
     * 命中则返回已认证信息；未命中或未启用返回 {@code null}，由调用方走原 o认证 协议。
     * </p>
     *
     * @return 认证信息，未命中返回 {@code null}
     */
    private AuthenticationInformation tryAuthenticateAiAppKey() {
        var support = com.chua.starter.oauth.client.support.appkey.AiAppKeySupport.get();
        if (support == null || !support.isEnabled()) {
            return null;
        }
        String token = getToken();
        if (StringUtils.isBlank(token)) {
            return null;
        }
        Map<String, Object> row = support.verifyAppKey(token);
        if (row == null) {
            log.debug("[WebRequest鉴权]AI AppKey 校验未命中: {}", protocol);
            return null;
        }
        String username = support.resolveUsername(row, token);
        UserResume userResume = new UserResume()
                .setUsername(username)
                .setNickName(username)
                .setLoginType("ai-appkey");
        log.debug("[WebRequest鉴权]AI AppKey 认证通过: user={}", username);
        /**
         * 认证信息。
         * @param Information.OK 信息.OK
         * @param userResume 用户resume
         * @return 认证信息的结果
         */
        return new AuthenticationInformation(Information.OK, userResume);
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
     * 解析认证协议扩展
     * <p>通过 SPI 加载配置的协议实现，协议未注册时返回 null，由调用方降级处理</p>
     *
     * @return 协议实例，未注册返回 空
     */
    private Protocol resolveProtocol() {
        return getProtocolExtension(authProperties);
    }

    /**
     * 获取认证协议扩展（带进程内实例缓存）
     * <p>协议配置全局唯一，实例无每请求状态，按协议名缓存单例避免每次请求重复 SPI 查找与构造</p>
     *
     * @param authProperties 认证配置
     * @return 协议实例，未注册返回 空
     */
    public static Protocol getProtocolExtension(AuthClientProperties authProperties) {
        if (null == authProperties) {
            return null;
        }
        String cacheKey = protocolCacheKey(authProperties);
        Protocol cached = PROTOCOL_CACHE.get(cacheKey);
        if (null != cached) {
            return cached;
        }
        String protocol = authProperties.getProtocol();
        Protocol extension = ServiceProvider.of(Protocol.class).getNewExtension(protocol, authProperties);
        if (null == extension) {
            log.error("[WebRequest协议解析]协议扩展加载失败 - 协议: {}, 可用协议: {}", protocol,
                    ServiceProvider.of(Protocol.class).names());
            return null;
        }
        Protocol existing = PROTOCOL_CACHE.putIfAbsent(cacheKey, extension);
        return null != existing ? existing : extension;
    }

    /**
     * 构建协议缓存Key
     * <p>以协议名 + 影响协议运行行为的关键配置摘要作为 key，
     * 保证不同配置的协议实例互不串用，同时避免敏感配置（如 secretKey）明文进 Key</p>
     *
     * @param authProperties 认证配置
     * @return 缓存key
     */
    private static String protocolCacheKey(AuthClientProperties authProperties) {
        AuthClientProperties.Aksk aksk = authProperties.getKey();
        String akskPart = null == aksk ? "" : aksk.isEnabled() + "|"
                + StringUtils.defaultString(aksk.getAccessKey(), "") + "|"
                + StringUtils.defaultString(aksk.getSecretKey(), "");
        String digest = DigestUtils.sha256(String.join("|",
                StringUtils.defaultString(authProperties.getAddress(), ""),
                StringUtils.defaultString(authProperties.getOauthUrl(), ""),
                StringUtils.defaultString(authProperties.getTokenName(), ""),
                StringUtils.defaultString(authProperties.getCookieName(), ""),
                StringUtils.defaultString(authProperties.getEncryption(), ""),
                String.valueOf(authProperties.isEnableEncryption()),
                StringUtils.defaultString(authProperties.getBalance(), ""),
                akskPart));
        return authProperties.getProtocol() + "|" + digest;
    }



    /**
     * 是嵌入
     *
     * @return boolean
     * @param authProperties 认证属性
     */
    public static boolean isEmbed(AuthClientProperties authProperties) {
        String oauthUrl = authProperties.getAddress();
        return StringUtils.isEmpty(oauthUrl);
    }

    /**
     * 成功
     * @param chain 链路
     * @param response 响应
     * @return 返回值
     * @throws IOException 异常
     * @throws ServletException 异常
    */
    public void doChain(FilterChain chain, HttpServletResponse response) throws IOException, ServletException {
        chain.doFilter(request, response);
    }

    /**
     * 刷新令牌（支持指定协议）- 高性能版本
     *
     * @param upgradeType   升级类型
     * @param refreshToken  刷新令牌
     * @return 登录结果
     */
    public LoginResult upgrade(UpgradeType upgradeType, String refreshToken) {
        // 快速获取认证数据
        Cookie[] cookie = getCookie();
        String token = getToken();

        Protocol protocolExtension = resolveProtocol();
        if (protocolExtension == null) {
            log.error("[WebRequest升级]协议未注册 - 协议: {}, 可用协议: {}", protocol,
                    ServiceProvider.of(Protocol.class).names());
            return null;
        }

 // REFRESH 类型只需要 refresh令牌，不需要现有的 access令牌
        if (upgradeType == UpgradeType.REFRESH && refreshToken != null && !refreshToken.isEmpty()) {
            return protocolExtension.upgrade(cookie, token,
                    upgradeType, refreshToken);
        }

        // 快速检查是否有可用的认证数据
        if ((cookie == null || cookie.length == 0) && (token == null || token.isEmpty())) {
            return null;
        }
        return protocolExtension.upgrade(cookie, token,
                upgradeType, refreshToken);
    }


    /**
     * 获取用户信息
     *
     * @return 用户信息
     * @param throwOnFailure 抛出on失败
     */
    public UserResult getUserResult(boolean throwOnFailure) {
        AuthenticationInformation authentication = authentication();
        UserResume returnResult = authentication.getReturnResult();

        if (returnResult == null) {
            if(isPass()) {
                return UserResult.emptyUserResult();
            }
            if (throwOnFailure) {
                /**
                 * runtime异常。
                 * @param "请重新登录" "请重新登录"
                 * @return runtime异常的结果
                 */
                throw new RuntimeException("请重新登录");
            }
            return null;
        }

        UserResult userResult = UserResult.fromResume(returnResult);
        request.getSession().setAttribute(AuthSessionUtils.SESSION_USER_INFO, userResult);
        return userResult;
    }
}
