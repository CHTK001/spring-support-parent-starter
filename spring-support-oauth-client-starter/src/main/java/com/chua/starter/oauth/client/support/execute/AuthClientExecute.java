package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.exception.AuthenticationException;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.Md5Utils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.value.Value;
import com.chua.starter.common.support.application.Binder;
import com.chua.starter.common.support.utils.RequestUtils;
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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.constant.NumberConstant.NUM_200;
import static com.chua.starter.common.support.utils.RequestUtils.*;
import static com.chua.starter.oauth.client.support.enums.AuthType.AUTO;
import static com.chua.starter.oauth.client.support.enums.AuthType.STATIC;

/**
 * 鉴权客户端操作
 *
 * @author CH
 */
public class AuthClientExecute {

    private final AuthClientProperties authClientProperties;

    public static final AuthClientExecute INSTANCE = new AuthClientExecute();
    private final String encryption;
    public static final String DEFAULT_KEY = "1234567980123456";

    public static AuthClientExecute getInstance() {
        return INSTANCE;
    }

    private static final Cache<String, Value<UserResult>> CACHE = CacheBuilder
            .newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();

    public AuthClientExecute() {
        this.authClientProperties = Binder.binder(AuthClientProperties.PRE, AuthClientProperties.class);
        this.encryption = authClientProperties.getEncryption();
    }

    /**
     * 获取UserResult
     *
     * @return token
     */
    public UserResult getUserResult() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        Object attribute = request.getSession().getAttribute(SESSION_USER_INFO);
        if (null != attribute) {
            if (attribute instanceof UserResult) {
                return (UserResult) attribute;
            }

            if (attribute instanceof UserResume) {
                UserResult userResult = new UserResult();
                com.chua.common.support.bean.BeanUtils.copyProperties(attribute, userResult);
                request.getSession().setAttribute(SESSION_USER_INFO, userResult);
                return userResult;
            }
        }

        WebRequest webRequest1 = new WebRequest(
                authClientProperties,
                request, null);

        UserResult userResult = new UserResult();
        AuthenticationInformation authentication = webRequest1.authentication();
        UserResume returnResult = authentication.getReturnResult();
        if (null == returnResult) {
            throw new AuthenticationException("请重新登录");
        }
        com.chua.common.support.bean.BeanUtils.copyProperties(returnResult, userResult);
        request.getSession().setAttribute(SESSION_USER_INFO, userResult);
        return userResult;
    }

    /**
     * 登出
     *
     * @param loginType
     * @param logoutType 账号类型
     * @return token
     */
    public LoginAuthResult logout(String uid, String loginType, LogoutType logoutType) {
        AuthType authType = null;
        if (STATIC.name().equalsIgnoreCase(authClientProperties.getProtocol())) {
            authType = STATIC;
        } else {
            authType = AUTO;
        }

        ProtocolExecutor protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension(authType);
        LoginAuthResult loginAuthResult = protocolExecutor.logout(uid, logoutType, getUserResult());
        if (null != loginAuthResult && loginAuthResult.getCode() == NUM_200) {
            return dehook(loginAuthResult);
        }
        return loginAuthResult;
    }

    /**
     * 获取token
     *
     * @param username 账号
     * @param password 密码
     * @param authType 账号类型
     * @param ext      额外参数
     * @return token
     */
    @Watch
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        if (STATIC.name().equalsIgnoreCase(authClientProperties.getProtocol())) {
            authType = STATIC;
        }

        ProtocolExecutor protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension(authType);
        LoginAuthResult loginAuthResult = protocolExecutor.getAccessToken(username, password, authType, ext);
        if (null == loginAuthResult || loginAuthResult.getCode() != NUM_200) {
            return dehook(loginAuthResult);
        }
        return hook(loginAuthResult);

    }

    /**
     * 删除hook
     *
     * @param loginAuthResult 登录身份验证结果
     * @return {@link LoginAuthResult}
     */
    private LoginAuthResult dehook(LoginAuthResult loginAuthResult) {
        RequestUtils.removeUsername();
        RequestUtils.removeUserInfo();
        return loginAuthResult;
    }

    /**
     * dehook
     * 删除hook
     *
     * @param uid) 登录身份验证结果
     * @return {@link LoginAuthResult}
     */
    private void dehookUid(String uid) {
        RequestUtils.removeUsername();
        RequestUtils.removeUserInfo();
    }

    /**
     * 注册
     *
     * @param loginAuthResult 登录身份验证结果
     * @return {@link LoginAuthResult}
     */
    public static LoginAuthResult hook(LoginAuthResult loginAuthResult) {
        UserResult userResult = loginAuthResult.getUserResult();
        if (null == userResult) {
            return loginAuthResult;
        }

        RequestUtils.setUsername(userResult.getUsername());
        RequestUtils.setUserId(userResult.getId());
        RequestUtils.setUserInfo(userResult);
        return loginAuthResult;
    }


    /**
     * 创建UID
     *
     * @param authType 登录方式
     * @param username 账号
     * @return UID
     */
    public static String createUid(String username, String authType) {
        UserResult userResult = new UserResult();
        userResult.setAuthType(authType);

        return Md5Utils.getInstance().getMd5String( username +
                userResult.getAuthType());
    }

    /**
     * 刷新token
     */
    public LoginResult upgrade(UpgradeType upgradeType) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return new LoginAuthResult(-1, ReturnCode.CLIENT_OTHER_ERROR.getMsg());
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;
        HttpServletRequest request = attributes.getRequest();
        WebRequest webRequest1 = new WebRequest(
                authClientProperties,
                request, null);

        return webRequest1.upgrade(upgradeType);
    }

    /**
     * 登陆码
     *
     * @param loginCodeType 登录类型
     * @param type
     * @param callback
     * @return 登陆码
     */
    public String getLoginCode(String loginCodeType, String type, String token, String callback) {
        ProtocolExecutor protocolExecutor = ServiceProvider.of(ProtocolExecutor.class).getExtension(authClientProperties.getProtocol());
        return protocolExecutor.getLoginCode(loginCodeType, type, token, callback);
    }

    /**
     * 获取用户结果
     *
     * @param token token
     * @return {@link UserResult}
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

        Protocol protocol = ServiceProvider.of(Protocol.class).getExtension(authClientProperties.getProtocol());
        AuthenticationInformation approve = protocol.approve(null, token);
        if(approve.getInformation().getCode() != 200) {
            return null;
        }
        UserResult userResult = BeanUtils.copyProperties(approve.getReturnResult(), UserResult.class);
        CACHE.put(token, Value.of(userResult));
        return userResult;
    }


    /**
     * 获取用户名
     *
     * @return {@link String}
     */
    public static String getUserId() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        Object attribute = request.getSession().getAttribute(SESSION_USERID);
        return null == attribute ? null : attribute.toString();
    }

    /**
     * 获取用户名
     *
     * @return {@link String}
     */
    public static String getUsername() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        Object attribute = request.getSession().getAttribute(SESSION_USERNAME);
        return null == attribute ? null : attribute.toString();
    }

    /**
     * 设置用户名
     *
     * @param username 用户名
     */
    public static void setUsername(String username) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        request.getSession().setAttribute(SESSION_USERNAME, username);
    }

    /**
     * 设置用户信息
     *
     * @param userInfo 用户名
     */
    public static void setUserInfo(Object userInfo) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        request.getSession().setAttribute(SESSION_USER_INFO, userInfo);
    }

    /**
     * 获取用户信息
     */
    @SuppressWarnings("ALL")
    public static <T> T getUserInfo(Class<T> target) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        Object attribute = request.getSession().getAttribute(SESSION_USER_INFO);
        if (null != attribute || target.isAssignableFrom(attribute.getClass())) {
            return (T) attribute;
        }

        return BeanUtils.copyProperties(attribute, target);
    }

    /**
     * 删除用户名
     */
    public static void removeUsername() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        request.getSession().removeAttribute(SESSION_USER_INFO);
    }

    /**
     * 删除用户信息
     */
    public static void removeUserInfo() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        request.getSession().removeAttribute(SESSION_USER_INFO);
    }

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
}
