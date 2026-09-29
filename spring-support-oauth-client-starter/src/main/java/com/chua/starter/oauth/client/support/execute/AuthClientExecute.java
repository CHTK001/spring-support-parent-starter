package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.exception.AuthenticationException;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.value.Value;
import com.chua.starter.common.support.watch.Watch;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.credential.CredentialIssuer;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.impersonation.TemporaryToken;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.Protocol;
import com.chua.starter.oauth.client.support.ratelimit.RateLimitService;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.web.WebRequest;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.common.support.utils.RequestUtils.*;

/**
 * 鉴权客户端操作
 *
 * @since 2023/10/09
 * @author CH
 */
public class AuthClientExecute {

    /**
     * 认证客户端属性（最近一次从运行时上下文取到的快照）
     * <p>{@code INSTANCE} 在类初始化期创建，此时 Spring 可能还没完成 {@code plugin.oauth} 绑定，
     * 因此不能一次性冻结；由 {@link #getAuthClientProperties()} 每次与上下文对齐。</p>
    */
    private AuthClientProperties authClientProperties;

    /**
     * 实例
    */
    public static final AuthClientExecute INSTANCE = new AuthClientExecute();

    /**
     * 默认_Key
    */
    public static final String DEFAULT_KEY = "1234567980123456";

    /**
     * 获取Instance
     *
     * @return 获取instance的结果
     */
    public static AuthClientExecute getInstance() {
        return INSTANCE;
    }

    private static final Cache<String, Value<UserResult>> CACHE = CacheBuilder
            .newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();

    /**
     * 创建 认证客户端执行 实例
    */
    public AuthClientExecute() {
    }

    /**
     * 认证客户端属性。
     * <p>上下文未就绪时会返回默认属性；一旦 Spring 完成绑定并写入上下文，这里自动切到真实配置。</p>
     *
     * @return 认证客户端属性
     */
    private AuthClientProperties getAuthClientProperties() {
        AuthClientProperties current = OauthClientRuntimeContext.getAuthClientProperties();
        if (current != null && current != authClientProperties) {
            authClientProperties = current;
        }
        return authClientProperties;
    }


    /**
     * 获取缓存的用户结果信息（仅从 会话 获取，不请求服务器）
     *
     * @return 用户结果信息，未登录返回 空
     */
    public UserResult getCacheUserResult() {
        return getSessionUserResult();
    }

    /**
     * 获取用户结果信息（优先从 会话 获取，否则请求服务器验证）
     *
     * @return 用户结果信息
     * @throws AuthenticationException 如果未登录或令牌无效
     */
    public UserResult getUserResult() {
        return getUserResultInternal(true);
    }

    /**
     * 安全获取用户结果信息，不抛出异常
     *
     * @return 用户结果信息，如果未登录则返回 空
     */
    public UserResult getSafeUserResult() {
        return getUserResultInternal(false);
    }

    /**
     * 从 会话 获取用户结果（不请求服务器）
     * @return 获取会话用户结果的结果
     */
    private UserResult getSessionUserResult() {
        HttpServletRequest request = AuthSessionUtils.getRequest();
        if (request == null) {
            return null;
        }

        Object attribute = request.getSession().getAttribute(AuthSessionUtils.SESSION_USER_INFO);
        if (attribute == null) {
            return null;
        }

        if (attribute instanceof UserResult userResult) {
            return userResult;
        }

        if (attribute instanceof UserResume userResume) {
            UserResult userResult = UserResult.fromResume(userResume);
            request.getSession().setAttribute(AuthSessionUtils.SESSION_USER_INFO, userResult);
            return userResult;
        }
        return null;
    }

    /**
     * 获取用户结果信息的内部实现
     *
     * @param throwOnFailure 失败时是否抛出异常
     * @return 用户结果信息
     */
    private UserResult getUserResultInternal(boolean throwOnFailure) {
        HttpServletRequest request = AuthSessionUtils.getRequest();
        if (request == null) {
            return null;
        }

 // 先从 会话 获取
        UserResult cached = getSessionUserResult();
        if (cached != null) {
            return cached;
        }

        // 请求服务器验证
        WebRequest webRequest = new WebRequest(getAuthClientProperties(), request, null);
        return webRequest.getUserResult(throwOnFailure);
    }

    /**
     * 用户登出操作
     *
     * @param uid        用户唯一标识，例如："123456"
     * @param loginType  登录类型，例如："mobile"、"email"
     * @param logoutType 登出类型，例如：logout类型.LOGOUT
     * @return 登录认证结果
     */
    public LoginAuthResult logout(String uid, String loginType, LogoutType logoutType) {
        String protocol = getAuthClientProperties().getProtocol();
        ProtocolExecutor protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension(protocol);
        if (protocolExecutor == null) {
            protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension("default");
        }
        return protocolExecutor.logout(uid, logoutType, getUserResult());
    }

    // ==================== 登出便捷方法 ====================

    /**
     * 清除本地缓存（会话 和内存缓存）
     * <p>登出时调用，清除 AuthFilter 存储的用户信息</p>
     */
    private void clearLocalCache() {
        HttpServletRequest request = AuthSessionUtils.getRequest();
        if (request == null) {
            return;
        }
        
        try {
 // 清除 会话 中的用户信息
            AuthSessionUtils.clearSession();
            
            // 清除内存缓存
            String token = getTokenFromRequest(request);
            if (StringUtils.isNotBlank(token)) {
                CACHE.invalidate(token);
            }
        } catch (Exception ignored) {
            // 忽略清除缓存时的异常
        }
    }

    /**
     * 从请求中获取 令牌
     * @param request 请求
     * @return 获取令牌从请求的结果
     */
    private String getTokenFromRequest(HttpServletRequest request) {
 // 从 头部 获取
        String token = request.getHeader(getAuthClientProperties().getTokenName());
        if (StringUtils.isNotBlank(token)) {
            return token;
        }
        
        // 从 Cookie 获取
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (getAuthClientProperties().getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        // 从参数获取
        return request.getParameter(getAuthClientProperties().getTokenName());
    }

    /**
     * 登出当前设备
     * <p>根据当前请求中的 Token 登出，删除 Token 和 RefreshToken，并清除本地缓存</p>
     *
     * @return 登出结果
     */
    public LoginAuthResult logoutCurrent() {
        UserResult userResult = getUserResult();
        if (userResult == null || StringUtils.isBlank(userResult.getToken())) {
            /**
             * login认证结果。
             * @param 400 400
             * @param "未登录" "未登录"
             * @return login认证结果的结果
             */
            return new LoginAuthResult(400, "未登录");
        }
        // 清除本地缓存
        /**
         * clear本地缓存。
         */
        clearLocalCache();
 // 使用 令牌 作为 uid 参数，logout类型.LOGOUT 表示单设备登出
        return logout(userResult.getToken(), userResult.getLoginType(), LogoutType.LOGOUT);
    }

    /**
     * 登出当前用户的所有设备
     * <p>删除该用户的所有 Token 和 RefreshToken，并清除本地缓存</p>
     *
     * @return 登出结果
     */
    public LoginAuthResult logoutAll() {
        UserResult userResult = getUserResult();
        if (userResult == null || StringUtils.isBlank(userResult.getUid())) {
            /**
             * login认证结果。
             * @param 400 400
             * @param "未登录" "未登录"
             * @return login认证结果的结果
             */
            return new LoginAuthResult(400, "未登录");
        }
        // 清除本地缓存
        /**
         * clear本地缓存。
         */
        clearLocalCache();
        return logout(userResult.getUid(), userResult.getLoginType(), LogoutType.LOGOUT_ALL);
    }

    /**
     * 登出指定用户的所有设备
     * <p>删除该用户的所有 Token 和 RefreshToken</p>
     *
     * @param uid 用户唯一标识
     * @return 登出结果
     */
    public LoginAuthResult logoutAll(String uid) {
        if (StringUtils.isBlank(uid)) {
            /**
             * login认证结果。
             * @param 400 400
             * @param "用户ID不能为空" "用户标识不能为空"
             * @return login认证结果的结果
             */
            return new LoginAuthResult(400, "用户ID不能为空");
        }
        return logout(uid, null, LogoutType.LOGOUT_ALL);
    }

    /**
     * 根据 令牌 登出指定设备
     *
     * @param token 访问令牌
     * @return 登出结果
     */
    public LoginAuthResult logoutByToken(String token) {
        if (StringUtils.isBlank(token)) {
            /**
             * login认证结果。
             * @param 400 400
             * @param "Token不能为空" "令牌不能为空"
             * @return login认证结果的结果
             */
            return new LoginAuthResult(400, "Token不能为空");
        }
        return logout(token, null, LogoutType.LOGOUT);
    }

    /**
     * 注销账号（永久）
     * <p>删除该用户的所有 Token 和 RefreshToken，通常用于账号注销场景</p>
     *
     * @param uid 用户唯一标识
     * @return 登出结果
     */
    public LoginAuthResult unregister(String uid) {
        if (StringUtils.isBlank(uid)) {
            /**
             * login认证结果。
             * @param 400 400
             * @param "用户ID不能为空" "用户标识不能为空"
             * @return login认证结果的结果
             */
            return new LoginAuthResult(400, "用户ID不能为空");
        }
        return logout(uid, null, LogoutType.UN_REGISTER);
    }

    /**
     * 获取访问令牌
     *
     * @param username 账号，例如："zhangsan"
     * @param password 密码，例如："123456"
     * @param authType 认证类型，例如：认证类型.密码
     * @param ext      额外参数，例如：映射.的("captcha", "abcd")
     * @return 登录认证结果
     */
    @Watch
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
 // 账号锁定检查（o认证 限流能力）
        RateLimitService rateLimitService = RateLimitService.getInstance();
        if (rateLimitService.isAccountLocked(username)) {
            LoginAuthResult locked = new LoginAuthResult();
            locked.setCode(com.chua.starter.oauth.client.support.infomation.Information.AUTHENTICATION_FAILURE.getCode());
            locked.setMessage("账号已锁定，请稍后再试");
            return locked;
        }

        String protocol = getAuthClientProperties().getProtocol();
        ProtocolExecutor protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension(protocol);
        if (protocolExecutor == null) {
            protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension("default");
        }
        LoginAuthResult result = protocolExecutor.getAccessToken(username, password, authType, ext);
        // 登录结果埋点：成功清除失败计数，失败累加（达到阈值触发账号锁定）
        if (result != null && result.getCode() != null
                && result.getCode() == com.chua.starter.oauth.client.support.infomation.Information.OK.getCode()) {
            rateLimitService.resetAccount(username);
        } else {
            rateLimitService.recordLoginFailure(username);
        }
        return result;
    }

    /**
     * 三方登录：三方身份确认后直接换发访问令牌。
     * <p>
     * 适用于微信小程序、公众号、Gitee、GitHub 等三方登录场景，三方身份（如
     * 微信 编码 换取的手机号/openid）已在业务层确认，此处仅负责按三方凭证签发令牌。
     * 内部复用 {@link #getAccessToken}，自动携带账号锁定限流埋点。
     * </p>
     *
     * @param account    三方确认后的登录账号，例如微信手机号
     * @param credential 三方凭证，例如微信 openid
     * @param authType   认证类型，例如 {@link AuthType#WECHAT} / {@link AuthType#THIRD_PARTY}
     * @param ext        额外参数，例如 {@code Map.of("openid", "xxx", "thirdPartyType", "wx_mini_app")}
     * @return 登录认证结果
     */
    public LoginAuthResult thirdPartyLogin(String account, String credential, AuthType authType, Map<String, Object> ext) {
        return getAccessToken(account, credential, authType, ext);
    }

    /**
     * 手机号登录：以手机号（或用户标识）换发访问令牌。
     * <p>
     * 适用于短信验证码登录等已验证手机号归属的场景，验证码校验在业务层完成，
     * 此处仅负责按账号签发令牌。内部复用 {@link #getAccessToken}，自动携带账号锁定限流埋点。
     * </p>
     *
     * @param account 登录账号，例如手机号或用户标识
     * @param ext     额外参数，例如 {@code Map.of("loginType", "sms", "phone", "13800000000")}
     * @return 登录认证结果
     */
    public LoginAuthResult smsLogin(String account, Map<String, Object> ext) {
        return getAccessToken(account, null, AuthType.SMS, ext);
    }

    /**
     * 邮箱登录：以邮箱（或用户标识）换发访问令牌。
     * <p>
     * 适用于邮箱验证码登录等已验证邮箱归属的场景，验证码校验在业务层完成，
     * 此处仅负责按账号签发令牌。内部复用 {@link #getAccessToken}，自动携带账号锁定限流埋点。
     * </p>
     *
     * @param account 登录账号（用户名，与静态账号配置一致）
     * @param ext     额外参数，例如 {@code Map.of("loginType", "email", "email", "a@b.com")}
     * @return 登录认证结果
     */
    public LoginAuthResult emailLogin(String account, Map<String, Object> ext) {
        return getAccessToken(account, null, AuthType.EMAIL, ext);
    }


    /**
     * 创建用户唯一标识
     *
     * @param username 账号，例如："zhangsan"
     * @param authType 登录方式，例如："mobile"
     * @return UID
     */
    public static String createUid(String username, String authType) {
        UserResult userResult = new UserResult();
        userResult.setLoginType(authType);

        return DigestUtils.md5(username + userResult.getLoginType());
    }

    // ==================== 在线状态查询（调用服务器） ====================

    /**
     * 查询当前用户的在线状态
     * <p>通过调用服务器端接口获取在线状态信息</p>
     *
     * @return 在线状态信息，未登录返回 空
     */
    public Protocol.OnlineStatus getOnlineStatus() {
        UserResult userResult = getUserResult();
        if (userResult == null || StringUtils.isBlank(userResult.getUid())) {
            return null;
        }
        return getOnlineStatus(userResult.getUid());
    }

    /**
     * 查询指定用户的在线状态
     * <p>通过调用服务器端接口获取在线状态信息</p>
     *
     * @param uid 用户唯一标识
     * @return 在线状态信息
     */
    public Protocol.OnlineStatus getOnlineStatus(String uid) {
        if (StringUtils.isBlank(uid)) {
            return null;
        }
        try {
            Protocol protocol = WebRequest.getProtocolExtension(getAuthClientProperties());
            if (protocol == null) {
                return null;
            }
            return protocol.getOnlineStatus(uid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查当前用户是否可以登录（未达到在线上限）
     * <p>通过调用服务器端接口判断</p>
     *
     * @return true-可以登录，false-已达上限
     */
    public boolean canLogin() {
        Protocol.OnlineStatus status = getOnlineStatus();
        return status == null || !status.isReachedLimit();
    }

    /**
     * 检查指定用户是否可以登录（未达到在线上限）
     * <p>通过调用服务器端接口判断</p>
     *
     * @param uid 用户唯一标识
     * @return true-可以登录，false-已达上限
     */
    public boolean canLogin(String uid) {
        Protocol.OnlineStatus status = getOnlineStatus(uid);
        return status == null || !status.isReachedLimit();
    }

    /**
     * 获取当前用户的在线数量
     *
     * @return 在线数量，未登录返回 0
     */
    public int getOnlineCount() {
        Protocol.OnlineStatus status = getOnlineStatus();
        return status != null ? status.getOnlineCount() : 0;
    }

    /**
     * 获取指定用户的在线数量
     *
     * @param uid 用户唯一标识
     * @return 在线数量
     */
    public int getOnlineCount(String uid) {
        Protocol.OnlineStatus status = getOnlineStatus(uid);
        return status != null ? status.getOnlineCount() : 0;
    }

    /**
     * 获取在线用户列表
     * <p>通过调用服务器端接口获取在线用户信息</p>
     *
     * @param username 用户名（模糊匹配，可为空）
     * @param ip       IP地址（模糊匹配，可为空）
     * @param page     页码（从1开始）
     * @param size     每页大小
     * @return 在线用户列表结果
     */
    public Protocol.OnlineUserResult getOnlineUsers(String username, String ip, int page, int size) {
        Protocol.OnlineUserQuery query = new Protocol.OnlineUserQuery(username, ip, page, size);
        return getOnlineUsers(query);
    }

    /**
     * 获取在线用户列表
     * <p>通过调用服务器端接口获取在线用户信息</p>
     *
     * @param query 查询参数
     * @return 在线用户列表结果
     */
    public Protocol.OnlineUserResult getOnlineUsers(Protocol.OnlineUserQuery query) {
        try {
            Protocol protocol = WebRequest.getProtocolExtension(getAuthClientProperties());
            if (protocol == null) {
                return Protocol.OnlineUserResult.empty();
            }
            return protocol.getOnlineUsers(query);
        } catch (Exception e) {
            return Protocol.OnlineUserResult.empty();
        }
    }

    /**
     * 获取所有在线用户列表（默认分页）
     * <p>通过调用服务器端接口获取在线用户信息</p>
     *
     * @return 在线用户列表结果
     */
    public Protocol.OnlineUserResult getOnlineUsers() {
        return getOnlineUsers(null, null, 1, 100);
    }

    // ==================== 临时令牌功能 ====================

    /**
     * 默认临时令牌过期时间（秒）
     */
    private static final int DEFAULT_TEMPORARY_TOKEN_EXPIRE = 10;

    /**
     * 最大临时令牌过期时间（秒）
     */
    private static final int MAX_TEMPORARY_TOKEN_EXPIRE = 3600;

    /**
     * 创建临时令牌（使用当前登录用户，默认10秒过期）
     * <p>基于当前登录用户的令牌创建一个短期有效的临时令牌</p>
     *
     * @return 临时令牌结果，包含临时token和过期时间
     */
    public LoginAuthResult createTemporaryToken() {
        return createTemporaryToken(DEFAULT_TEMPORARY_TOKEN_EXPIRE, null);
    }

    /**
     * 创建临时令牌（使用当前登录用户，指定过期时间）
     *
     * @param expireSeconds 过期时间（秒），范围：1-3600
     * @return 临时令牌结果
     */
    public LoginAuthResult createTemporaryToken(int expireSeconds) {
        return createTemporaryToken(expireSeconds, null);
    }

    /**
     * 创建临时令牌（使用当前登录用户，指定过期时间和允许的URL）
     *
     * @param expireSeconds 过期时间（秒），范围：1-3600
     * @param allowedUrls   允许访问的URL列表，空或空表示允许所有URL
     * @return 临时令牌结果
     */
    public LoginAuthResult createTemporaryToken(int expireSeconds, List<String> allowedUrls) {
        UserResult userResult = getSafeUserResult();
        if (userResult == null || StringUtils.isBlank(userResult.getToken())) {
            /**
             * login认证结果。
             * @param 401 401
             * @param "未登录或令牌无效" "未登录或令牌无效"
             * @return login认证结果的结果
             */
            return new LoginAuthResult(401, "未登录或令牌无效");
        }
        return createTemporaryTokenByToken(userResult.getToken(), expireSeconds, allowedUrls);
    }

    /**
     * 根据指定令牌创建临时令牌
     *
     * @param sourceToken   源令牌
     * @param expireSeconds 过期时间（秒），范围：1-3600
     * @param allowedUrls   允许访问的URL列表，空或空表示允许所有URL
     * @return 临时令牌结果
     */
    public LoginAuthResult createTemporaryTokenByToken(String sourceToken, int expireSeconds, List<String> allowedUrls) {
        if (StringUtils.isBlank(sourceToken)) {
            /**
             * login认证结果。
             * @param 400 400
             * @param "源令牌不能为空" "源令牌不能为空"
             * @return login认证结果的结果
             */
            return new LoginAuthResult(400, "源令牌不能为空");
        }

        // 限制过期时间范围
        int actualExpire = Math.max(1, Math.min(expireSeconds, MAX_TEMPORARY_TOKEN_EXPIRE));

        try {
            Protocol protocol = WebRequest.getProtocolExtension(getAuthClientProperties());
            if (protocol == null) {
                /**
                 * login认证结果。
                 * @param 500 500
                 * @param "协议服务不可用" "协议服务不可用"
                 * @return login认证结果的结果
                 */
                return new LoginAuthResult(500, "协议服务不可用");
            }

            // 构建扩展参数
            Map<String, Object> ext = new HashMap<>();
            ext.put("expireTime", actualExpire);
            if (allowedUrls != null && !allowedUrls.isEmpty()) {
                ext.put("allowedUrls", String.join(",", allowedUrls));
            }

            return protocol.createTemporaryToken(sourceToken, ext);
        } catch (Exception e) {
            /**
             * login认证结果。
             * @param 500 500
             * @param e.getMessage( e.获取消息(
             * @return login认证结果的结果
             */
            return new LoginAuthResult(500, "创建临时令牌失败: " + e.getMessage());
        }
    }

    /**
     * 为指定身份签发一个临时令牌（管理员代入他人身份）。
     *
     * <p>与 {@link #createTemporaryTokenByToken(String, int, List)} 的区别：后者换的是
     * 「当前登录者自己」的短期令牌，这里换的是<b>另一个身份</b>的令牌，
     * 用于「管理员打开某个学生的页面看他看到什么」这类排查场景。</p>
     *
     * <p>三道限制同时生效：</p>
     * <ol>
     *     <li>只有管理员可调用，非管理员一律 403；</li>
     *     <li>有效期封顶 {@value #MAX_TEMPORARY_TOKEN_EXPIRE} 秒；</li>
     *     <li>令牌自带 {@code readonly} 与可访问路径白名单，
     *     由 {@code StaticProtocol} 在鉴权时强制校验。</li>
     * </ol>
     *
     * <p>「目标身份是什么」由调用方决定 —— 本方法只管签发，不认识任何业务表。</p>
     *
     * @param target        目标身份；{@code userId} 为空直接拒绝
     * @param expireSeconds 有效期（秒），实际取值被夹到 1 到 {@value #MAX_TEMPORARY_TOKEN_EXPIRE}
     * @param allowedUrls   允许访问的路径前缀；为空表示不限路径
     * @return 含令牌的登录结果；非管理员或入参非法时返回对应错误码
     */
    public LoginAuthResult createTemporaryTokenFor(UserResult target, int expireSeconds, List<String> allowedUrls) {
        UserResume operator = getSafeUserResult();
        if (operator == null || StringUtils.isBlank(operator.getUserId())) {
            return new LoginAuthResult(401, "未登录或令牌无效");
        }
        if (!AuthConstant.isAdmin(operator.getRoles())) {
            Logger logger = LoggerFactory.getLogger(AuthClientExecute.class);
            logger.warn("非管理员尝试签发临时令牌 - 操作人: {}", operator.getUsername());
            return new LoginAuthResult(403, "只有管理员可以签发临时令牌");
        }
        if (target == null || StringUtils.isBlank(target.getUserId())) {
            return new LoginAuthResult(400, "目标身份不能为空");
        }
        int actualExpire = Math.max(1, Math.min(expireSeconds, MAX_TEMPORARY_TOKEN_EXPIRE));
        TemporaryToken.mark(target, operator.getUsername(), actualExpire, allowedUrls);
        return CredentialIssuer.issue(target, actualExpire);
    }

    /**
     * 根据刷新令牌创建临时令牌。
     * <p>使用 refresh token 作为源凭证换取一个短期有效的临时令牌，
     * 适用于 access 令牌 已过期但希望进行临时授权访问的场景。
     * 协议层是否接受 refresh 令牌 作为源凭证取决于各协议实现与服务端支持。</p>
     *
     * @param refreshToken  刷新令牌
     * @param expireSeconds 过期时间（秒），范围：1-3600
     * @param allowedUrls   允许访问的URL列表，空或空表示允许所有URL
     * @return 临时令牌结果
     */
    public LoginAuthResult createTemporaryTokenByRefreshToken(String refreshToken, int expireSeconds, List<String> allowedUrls) {
        if (StringUtils.isBlank(refreshToken)) {
            /**
             * login认证结果。
             * @param 400 400
             * @param "刷新令牌不能为空" "刷新令牌不能为空"
             * @return login认证结果的结果
             */
            return new LoginAuthResult(400, "刷新令牌不能为空");
        }
        return createTemporaryTokenByToken(refreshToken, expireSeconds, allowedUrls);
    }

    /**
     * 刷新令牌
     *
     * @param refreshToken  刷新令牌，例如："refresh_令牌_abc123"
     * @param upgradeType   更新类型，例如：upgrade类型.REFRESH
     * @return 登录结果
     */
    public LoginResult upgrade(String refreshToken, UpgradeType upgradeType) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;
        HttpServletRequest request = attributes.getRequest();
        WebRequest webRequest1 = new WebRequest(
                getAuthClientProperties(),
                request, null);

        return webRequest1.upgrade(upgradeType, refreshToken);
    }


    /**
     * 根据令牌获取用户结果
     *
     * @param token 令牌令牌，例如："access_令牌_xyz789"
     * @return {@link 用户结果} 用户结果对象
     */
    public UserResult getUserResult(String token) {
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        Value<UserResult> ifPresent = CACHE.getIfPresent(token);
        if (null != ifPresent) {
            return ifPresent.getValue();
        }

        if (StringUtils.isEmpty(token) || "null".equals(token)) {
            return null;
        }
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        String protocolName = null;
        if (requestAttributes != null) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;
            HttpServletRequest request = attributes.getRequest();
            protocolName = request.getHeader("x-oauth-protocol");
        }

        Protocol protocol = WebRequest.getProtocolExtension(getAuthClientProperties());
        if (protocol == null) {
            return null;
        }
        AuthenticationInformation approve = protocol.approve(null, token, protocolName);

        if (approve.getInformation().getCode() != 200) {
            return null;
        }
        UserResume returnResult = approve.getReturnResult();
        UserResult userResult = UserResult.fromResume(returnResult);
        CACHE.put(token, Value.of(userResult));
        return userResult;
    }


    /**
     * 获取租户标识
     *
     * @return 租户ID，例如："tenant_001"
     * @see AuthSessionUtils#getTenantId()
     */
    public static String getTenantId() {
        return AuthSessionUtils.getTenantId();
    }

    /**
     * 获取用户标识
     *
     * @return 用户ID，例如："user_001"
     * @see AuthSessionUtils#getUserId()
     */
    public static String getUserId() {
        return AuthSessionUtils.getUserId();
    }

    /**
     * 获取用户名
     *
     * @return 用户名，例如："zhangsan"
     * @see AuthSessionUtils#getUsername()
     */
    public static String getUsername() {
        return AuthSessionUtils.getUsername();
    }

    /**
     * 设置用户名
     *
     * @param username 用户名，例如："zhangsan"
     * @see AuthSessionUtils#setUsername(String)
     */
    public static void setUsername(String username) {
        AuthSessionUtils.setUsername(username);
    }

    /**
     * 设置用户信息
     *
     * @param userInfo 用户信息对象
     * @see AuthSessionUtils#setUserInfo(Object)
     */
    public static void setUserInfo(Object userInfo) {
        AuthSessionUtils.setUserInfo(userInfo);
    }

    /**
     * 获取用户信息
     *
     * @param target 目标类型，例如：用户结果.类
     * @param <T>    泛型类型
     * @return 用户信息对象
     * @see AuthSessionUtils#getUserInfo(Class)
     */
    public static <T> T getUserInfo(Class<T> target) {
        return AuthSessionUtils.getUserInfo(target);
    }

    /**
     * 删除用户名
     *
     * @see AuthSessionUtils#removeUsername()
     */
    public static void removeUsername() {
        AuthSessionUtils.removeUsername();
    }

    /**
     * 删除用户信息
     *
     * @see AuthSessionUtils#removeUserInfo()
     */
    public static void removeUserInfo() {
        AuthSessionUtils.removeUserInfo();
    }

    /**
     * 获取请求中的令牌
     *
     * @return token字符串，例如："access_token_xyz789"
     */
    public String getToken() {
        HttpServletRequest request = getRequest();
        if (null == request) {
            return null;
        }
        String header = request.getHeader(getAuthClientProperties().getTokenName());
        String token = Strings.isNullOrEmpty(header) ? StringUtils.defaultString(
                request.getParameter(getAuthClientProperties().getTokenName()),
                (String) request.getAttribute(getAuthClientProperties().getTokenName())
        ) : header;

        if (StringUtils.isNotBlank(token)) {
            return token;
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> StringUtils.equals("x-oauth-cookie", cookie.getName()))
                .findFirst().orElse(new Cookie("x-oauth-cookie", null))
                .getValue();
    }

    /**
     * 是否明白
     * @return 是否明白
     */
    public boolean whiteList() {
        HttpServletRequest request = getRequest();
        if (null == request) {
            return true;
        }
        WebRequest webRequest1 = new WebRequest(
                getAuthClientProperties(),
                request, null);
        return webRequest1.isPass();
    }
}
