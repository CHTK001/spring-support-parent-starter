package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.lang.exception.AuthenticationException;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.Md5Utils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.value.Value;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.watch.Watch;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.Protocol;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.common.support.utils.RequestUtils.*;

/**
 * 鉴权客户端操作
 *
 * @author CH
 * @since 2023/10/09
 */
public class AuthClientExecute {

    private final AuthClientProperties authClientProperties;

    public static final AuthClientExecute INSTANCE = new AuthClientExecute();

    public static final String DEFAULT_KEY = "1234567980123456";

    public static AuthClientExecute getInstance() {
        return INSTANCE;
    }

    private static final Cache<String, Value<UserResult>> CACHE = CacheBuilder
            .newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();

    public AuthClientExecute() {
        this.authClientProperties = SpringBeanUtils.getBinderBean(AuthClientProperties.PRE, AuthClientProperties.class);
    }


    /**
     * 获取缓存的用户结果信息（仅从 Session 获取，不请求服务器）
     *
     * @return 用户结果信息，未登录返回 null
     */
    public UserResult getCacheUserResult() {
        return getSessionUserResult();
    }

    /**
     * 获取用户结果信息（优先从 Session 获取，否则请求服务器验证）
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
     * @return 用户结果信息，如果未登录则返回 null
     */
    public UserResult getSafeUserResult() {
        return getUserResultInternal(false);
    }

    /**
     * 从 Session 获取用户结果（不请求服务器）
     */
    private UserResult getSessionUserResult() {
        HttpServletRequest request = AuthSessionUtils.getRequest();
        if (request == null) {
            return null;
        }

        Object attribute = request.getSession().getAttribute(SESSION_USER_INFO);
        if (attribute == null) {
            return null;
        }

        if (attribute instanceof UserResult userResult) {
            return userResult;
        }

        if (attribute instanceof UserResume userResume) {
            UserResult userResult = new UserResult();
            BeanUtils.copyProperties(userResume, userResult);
            request.getSession().setAttribute(SESSION_USER_INFO, userResult);
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

        // 先从 Session 获取
        UserResult cached = getSessionUserResult();
        if (cached != null) {
            return cached;
        }

        // 请求服务器验证
        WebRequest webRequest = new WebRequest(authClientProperties, request, null);
        AuthenticationInformation authentication = webRequest.authentication();
        UserResume returnResult = authentication.getReturnResult();

        if (returnResult == null) {
            if (throwOnFailure) {
                throw new AuthenticationException("请重新登录");
            }
            return null;
        }

        UserResult userResult = new UserResult();
        BeanUtils.copyProperties(returnResult, userResult);
        request.getSession().setAttribute(SESSION_USER_INFO, userResult);
        return userResult;
    }

    /**
     * 用户登出操作
     *
     * @param uid        用户唯一标识，例如："123456"
     * @param loginType  登录类型，例如："mobile"、"email"
     * @param logoutType 登出类型，例如：LogoutType.LOGOUT
     * @return 登录认证结果
     */
    public LoginAuthResult logout(String uid, String loginType, LogoutType logoutType) {
        String protocol = authClientProperties.getProtocol();
        ProtocolExecutor protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension(protocol);
        if (protocolExecutor == null) {
            protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension("default");
        }
        return protocolExecutor.logout(uid, logoutType, getUserResult());
    }

    // ==================== 登出便捷方法 ====================

    /**
     * 清除本地缓存（Session 和内存缓存）
     * <p>登出时调用，清除 AuthFilter 存储的用户信息</p>
     */
    private void clearLocalCache() {
        HttpServletRequest request = AuthSessionUtils.getRequest();
        if (request == null) {
            return;
        }
        
        try {
            // 清除 Session 中的用户信息
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
     * 从请求中获取 Token
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        // 从 Header 获取
        String token = request.getHeader(authClientProperties.getTokenName());
        if (StringUtils.isNotBlank(token)) {
            return token;
        }
        
        // 从 Cookie 获取
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (authClientProperties.getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        // 从参数获取
        return request.getParameter(authClientProperties.getTokenName());
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
            return new LoginAuthResult(400, "未登录");
        }
        // 清除本地缓存
        clearLocalCache();
        // 使用 token 作为 uid 参数，LogoutType.LOGOUT 表示单设备登出
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
            return new LoginAuthResult(400, "未登录");
        }
        // 清除本地缓存
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
            return new LoginAuthResult(400, "用户ID不能为空");
        }
        return logout(uid, null, LogoutType.LOGOUT_ALL);
    }

    /**
     * 根据 Token 登出指定设备
     *
     * @param token 访问令牌
     * @return 登出结果
     */
    public LoginAuthResult logoutByToken(String token) {
        if (StringUtils.isBlank(token)) {
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
            return new LoginAuthResult(400, "用户ID不能为空");
        }
        return logout(uid, null, LogoutType.UN_REGISTER);
    }

    /**
     * 获取访问令牌
     *
     * @param username 账号，例如："zhangsan"
     * @param password 密码，例如："123456"
     * @param authType 认证类型，例如：AuthType.PASSWORD
     * @param ext      额外参数，例如：Map.of("captcha", "abcd")
     * @return 登录认证结果
     */
    @Watch
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        String protocol = authClientProperties.getProtocol();
        ProtocolExecutor protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension(protocol);
        if (protocolExecutor == null) {
            protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension("default");
        }
        return protocolExecutor.getAccessToken(username, password, authType, ext);
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

        return DigestUtils.md5Hex(username + userResult.getLoginType());
    }

    // ==================== 在线状态查询（调用服务器） ====================

    /**
     * 查询当前用户的在线状态
     * <p>通过调用服务器端接口获取在线状态信息</p>
     *
     * @return 在线状态信息，未登录返回 null
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
            Protocol protocol = ServiceProvider.of(Protocol.class)
                    .getNewExtension(authClientProperties.getProtocol(), authClientProperties);
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
     * 刷新token
     *
     * @param refreshToken  刷新令牌，例如："refresh_token_abc123"
     * @param upgradeType   更新类型，例如：UpgradeType.REFRESH
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
                authClientProperties,
                request, null);

        return webRequest1.upgrade(upgradeType, refreshToken);
    }


    /**
     * 根据token获取用户结果
     *
     * @param token token令牌，例如："access_token_xyz789"
     * @return {@link UserResult} 用户结果对象
     */
    public UserResult getUserResult(String token) {
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        Value<UserResult> ifPresent = CACHE.getIfPresent(token);
        if (null != ifPresent) {
            return ifPresent.getValue();
        }

        if (StringUtils.isEmpty(token) || CommonConstant.NULL.equals(token)) {
            return null;
        }
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        String protocolName = null;
        if (requestAttributes != null) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;
            HttpServletRequest request = attributes.getRequest();
            protocolName = request.getHeader("x-oauth-protocol");
        }

        Protocol protocol = ServiceProvider.of(Protocol.class).getNewExtension(authClientProperties.getProtocol(), authClientProperties);
        AuthenticationInformation approve = protocol.approve(null, token, protocolName);

        if (approve.getInformation().getCode() != 200) {
            return null;
        }
        UserResult userResult = BeanUtils.copyProperties(approve.getReturnResult(), UserResult.class);
        CACHE.put(token, Value.of(userResult));
        return userResult;
    }


    /**
     * 获取租户ID
     *
     * @return 租户ID，例如："tenant_001"
     * @see AuthSessionUtils#getTenantId()
     */
    public static String getTenantId() {
        return AuthSessionUtils.getTenantId();
    }

    /**
     * 获取用户ID
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
     * @param target 目标类型，例如：UserResult.class
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
     * 获取请求中的token
     *
     * @return token字符串，例如："access_token_xyz789"
     */
    public String getToken() {
        HttpServletRequest request = getRequest();
        if (null == request) {
            return null;
        }
        String header = request.getHeader(authClientProperties.getTokenName());
        String token = Strings.isNullOrEmpty(header) ? StringUtils.defaultString(
                request.getParameter(authClientProperties.getTokenName()),
                (String) request.getAttribute(authClientProperties.getTokenName())
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
                authClientProperties,
                request, null);
        return webRequest1.isPass();
    }
}
