package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.exception.AuthenticationException;
import com.chua.common.support.lang.robin.Node;
import com.chua.common.support.lang.robin.Robin;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.Md5Utils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.value.Value;
import com.chua.starter.common.support.application.Binder;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.watch.Watch;
import com.chua.starter.oauth.client.support.advice.def.DefSecret;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.entity.AuthRequest;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.Protocol;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.constant.NumberConstant.NUM_200;
import static com.chua.common.support.http.HttpClientUtils.APPLICATION_JSON;
import static com.chua.common.support.lang.code.ReturnCode.OK;
import static com.chua.starter.common.support.utils.RequestUtils.SESSION_USERNAME;
import static com.chua.starter.common.support.utils.RequestUtils.SESSION_USER_INFO;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.ACCESS_KEY;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.SECRET_KEY;
import static com.chua.starter.oauth.client.support.web.WebRequest.isEmbed;

/**
 * 鉴权客户端操作
 *
 * @author CH
 */
public class AuthClientExecute {

    private final AuthClientProperties authClientProperties;

    public static final AuthClientExecute INSTANCE = new AuthClientExecute();
    private final String encryption;
    private static final String DEFAULT_KEY = "1234567980123456";

    private final Codec AES = Codec.build("AES", DEFAULT_KEY);

    public static AuthClientExecute getInstance() {
        return INSTANCE;
    }

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
        if(null != attribute) {
            if(attribute instanceof UserResult) {
                return (UserResult) attribute;
            }

            if(attribute instanceof UserResume) {
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
     * @param logoutType 账号类型
     * @return token
     */
    public LoginAuthResult logout(String uid, LogoutType logoutType) {
        AuthType authType = null;
        String oauthUrl = authClientProperties.getAddress();
        if(StringUtils.isEmpty(oauthUrl)) {
            authType = AuthType.EMBED;
        }

        if(authType == AuthType.EMBED) {
            dehookUid(uid);
            return new LoginAuthResult(200, "");
        }

        if (Strings.isNullOrEmpty(uid) && logoutType == LogoutType.UN_REGISTER) {
            return new LoginAuthResult(400, "uid不能为空");
        }

        UserResult userResult = getUserResult();
        String resultUid = userResult.getUid();

        String accessKey = authClientProperties.getAccessKey();
        String secretKey = authClientProperties.getSecretKey();
        String serviceKey = authClientProperties.getServiceKey();
        String key = UUID.randomUUID().toString();

        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            accessKey = DefSecret.ACCESS_KEY;
            secretKey = DefSecret.SECRET_KEY;
        }

        Map<String, Object> jsonObject = new HashMap<>(2);
        jsonObject.put(ACCESS_KEY, accessKey);
        jsonObject.put("uid", Strings.isNullOrEmpty(uid) ? resultUid : uid);
        jsonObject.put(SECRET_KEY, secretKey);

        String asString = Json.toJson(jsonObject);
        String md5Hex = DigestUtils.md5Hex(key);
        String request = Codec.build(encryption, md5Hex).encodeHex(asString);

        String uidKey = UUID.randomUUID().toString();
        Map<String, Object> item2 = new HashMap<>(3);
        item2.put(AuthConstant.OAUTH_VALUE, request);
        item2.put(AuthConstant.OAUTH_KEY, key);
        item2.put("x-oauth-uid", uidKey);
        request = Codec.build(encryption, serviceKey).encodeHex(Json.toJson(item2));
        Robin robin = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin robin1 = robin.create();
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(authClientProperties.getAddress()).split(",");
        robin1.addNode(split);
        Node node = robin1.selectNode();
        HttpResponse<String> httpResponse = null;
        try {
            String url = node.getString();
            if (null == url) {
                return null;
            }

            AuthRequest request1 = new AuthRequest();
            request1.setData(request);
            request1.setType(logoutType.name());
            httpResponse = Unirest.post(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(url, "http://"), "/")
                                    + "logout")
                    .header("accept", "application/json")
                    .header("x-oauth-timestamp", System.nanoTime() + "")
                    .contentType(APPLICATION_JSON)
                    .body(Json.toJson(request1))
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            return null;
        }

        int status = httpResponse.getStatus();
        if (status == NUM_200) {
            LoginAuthResult loginAuthResult = new LoginAuthResult();
            loginAuthResult.setCode(status);
            dehook(null);
            return loginAuthResult;
        }
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        loginAuthResult.setCode(status);
        loginAuthResult.setMessage("认证服务器异常");
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
        String oauthUrl = authClientProperties.getAddress();
        if(StringUtils.isEmpty(oauthUrl)) {
            authType = AuthType.EMBED;
        }

        if(authType == AuthType.EMBED) {
            return hook(newLoginAuthResult(username, password));
        }

        String accessKey = authClientProperties.getAccessKey();
        String secretKey = authClientProperties.getSecretKey();
        String serviceKey = authClientProperties.getServiceKey();
        String key = UUID.randomUUID().toString();

        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            accessKey = DefSecret.ACCESS_KEY;
            secretKey = DefSecret.SECRET_KEY;
        }

        Map<String, Object> jsonObject = new HashMap<>(2);
        jsonObject.put(ACCESS_KEY, accessKey);
        jsonObject.put(SECRET_KEY, secretKey);

        String asString = Json.toJson(jsonObject);
        String request = Codec.build(encryption, DigestUtils.md5Hex(key)).encodeHex(asString);

        String uid = DigestUtils.md5Hex(UUID.randomUUID().toString());
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("ext", ext);
        item2.put(AuthConstant.OAUTH_VALUE, request);
        item2.put(AuthConstant.OAUTH_KEY, key);
        item2.put("x-oauth-uid", uid);
        item2.put("password", password);
        request = Codec.build(encryption, serviceKey).encodeHex(Json.toJson(item2));
        Robin robin1 = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin balance = robin1.create();
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(authClientProperties.getAddress()).split(",");
        balance.addNode(split);
        Node robin = balance.selectNode();
        HttpResponse<String> httpResponse = null;
        try {
            String url = robin.getString();
            if (null == url) {
                return dehook(null);
            }

            AuthRequest request1 = new AuthRequest();
            request1.setData(request);
            request1.setUsername(username);
            request1.setType(authType.name());
            httpResponse = Unirest.post(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(url, "http://"), "/")
                                    + "doLogin")
                    .contentType(APPLICATION_JSON)
                    .header("accept", "application/json")
                    .header("x-oauth-timestamp", System.nanoTime() + "")
                    .body(Json.toJson(request1))
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            return dehook(null);
        }

        int status = httpResponse.getStatus();
        String body = httpResponse.getBody();
        if (status == NUM_200) {
            ReturnResult returnResult = Json.fromJson(body, ReturnResult.class);
            String code = returnResult.getCode();
            Object data = returnResult.getData();
            if (code.equals(OK.getCode())) {
                LoginAuthResult loginAuthResult = null;
                try {
                    loginAuthResult = Json.fromJson(Codec.build(encryption, uid).decodeHex(data.toString()), LoginAuthResult.class);
                } catch (Exception ignore) {
                }

                if (null == loginAuthResult) {
                    loginAuthResult = Json.fromJson(Json.toJson(data), LoginAuthResult.class);
                }

                if (null == loginAuthResult) {
                    return dehook(null);
                }

                loginAuthResult.setCode(status);
                return hook(loginAuthResult);
            }
            LoginAuthResult loginAuthResult = new LoginAuthResult();
            loginAuthResult.setCode(403);
            loginAuthResult.setMessage(returnResult.getMsg());

            return dehook(loginAuthResult);
        }
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        loginAuthResult.setCode(status);
        loginAuthResult.setMessage("认证服务器异常");
        return dehook(loginAuthResult);

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
    private LoginAuthResult hook(LoginAuthResult loginAuthResult) {
        UserResult userResult = loginAuthResult.getUserResult();
        if(null == userResult) {
            return loginAuthResult;
        }

        RequestUtils.setUsername(userResult.getUsername());
        RequestUtils.setUserInfo(userResult);
        return loginAuthResult;
    }

    /**
     * 新登录身份验证结果
     *
     * @param username 用户名
     * @param password 暗语
     * @return {@link LoginAuthResult}
     */
    private LoginAuthResult newLoginAuthResult(String username, String password) {
        AuthClientProperties.TempUser temp = authClientProperties.getTemp();
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        String user = temp.getUser();
        if(StringUtils.isNotEmpty(user)) {
            Set<String> strings = Splitter.on(";").omitEmptyStrings().trimResults().splitToSet(user);
            for (String string : strings) {
                List<String> userAndPassword = Splitter.on(":").omitEmptyStrings().limit(2).trimResults().splitToList(string);
                if(isMatch(userAndPassword, username, password)) {
                    loginAuthResult.setCode(200);
                    UserResult userResult = new UserResult();
                    userResult.setId("0");
                    userResult.setAuthType(AuthType.EMBED.name());
                    userResult.setUsername(username);
                    userResult.setExpire(System.nanoTime());
                    loginAuthResult.setUserResult(userResult);
                    try {
                        loginAuthResult.setToken(Codec.build(encryption, DEFAULT_KEY).encodeHex(Json.toJson(userResult)));
                    } catch (Exception ignored) {
                    }
                    userResult.setUid(loginAuthResult.getToken());
                    return loginAuthResult;
                }
            }
        }

        loginAuthResult.setCode(403);
        loginAuthResult.setMessage("账号或密码错误");
        return loginAuthResult;

    }

    /**
     * 匹配
     *
     * @param userAndPassword 用户和密码
     * @param username        用户名
     * @param password        暗语
     * @return boolean
     */
    private boolean isMatch(List<String> userAndPassword, String username, String password) {
        if(userAndPassword.isEmpty()) {
            return false;
        }

        String user = CollectionUtils.find(userAndPassword, 0);
        if(userAndPassword.size() == 1) {
            return user.equals(username) && user.equals(password);
        }

        String passwd = CollectionUtils.find(userAndPassword, 1);
        return user.equals(username) && DigestUtils.md5Hex(passwd).equals(password);
    }

    /**
     * 创建UID
     *
     * @param authType 登录方式
     * @param beanType 认证方式
     * @param password 密码
     * @param username 账号
     * @return UID
     */
    public String createUid(String beanType, String username, String password, String authType) {
        UserResult userResult = new UserResult();
        userResult.setPassword(password).setAuthType(authType).setBeanType(beanType);

        return Md5Utils.getInstance().getMd5String(userResult.getBeanType() +
                username +
                userResult.getPassword() +
                userResult.getAuthType());
    }

    /**
     * 刷新token
     */
    public void refreshToken() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;
        HttpServletRequest request = attributes.getRequest();
        WebRequest webRequest1 = new WebRequest(
                authClientProperties,
                request, null);

        webRequest1.refreshToken();
    }

    /**
     * 登陆码
     *
     * @param loginCodeType 登录类型
     * @return 登陆码
     */
    public String getLoginCode(String loginCodeType) {
        Robin robin1 = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin balance = robin1.create();
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(authClientProperties.getAddress()).split(",");
        balance.addNode(split);
        Node robin = balance.selectNode();
        HttpResponse<String> httpResponse = null;
        try {
            String url = robin.getString();
            if (null == url) {
                return null;
            }


            httpResponse = Unirest.get(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(url, "http://"), "/")
                                    + loginCodeType + "/loginCodeType?redirect_url=")
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            return null;
        }

        if (httpResponse.getStatus() != 200) {
            return null;
        }

        return httpResponse.getBody();
    }
    private static final Cache<String, Value<UserResult>> CACHE = CacheBuilder
            .newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .build();
    /**
     * 获取用户结果
     *
     * @param token token
     * @return {@link UserResult}
     */
    public UserResult getUserResult(String token) {
        Value<UserResult> ifPresent = CACHE.getIfPresent(token);
        if(null != ifPresent) {
            return ifPresent.getValue();
        }

        if(StringUtils.isEmpty(token) || CommonConstant.NULL.equals(token)) {
            return null;
        }
        if(isEmbed(authClientProperties)) {
            try {
                UserResult userResult = Json.fromJson(AES.decodeHex(token), UserResult.class);
                CACHE.put(token, Value.of(userResult));
                return userResult;
            } catch (Exception ignored) {
            }
        }

        Protocol protocol = ServiceProvider.of(Protocol.class).getExtension(authClientProperties.getProtocol());
        AuthenticationInformation approve = protocol.approve(null, token);
        UserResult userResult = BeanUtils.copyProperties(approve.getReturnResult(), UserResult.class);
        CACHE.put(token, Value.of(userResult));
        return userResult;
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
     *
     */
    @SuppressWarnings("ALL")
    public static <T>T getUserInfo(Class<T> target) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;

        HttpServletRequest request = attributes.getRequest();
        Object attribute = request.getSession().getAttribute(SESSION_USER_INFO);
        if(null != attribute || target.isAssignableFrom(attribute.getClass())) {
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
}
