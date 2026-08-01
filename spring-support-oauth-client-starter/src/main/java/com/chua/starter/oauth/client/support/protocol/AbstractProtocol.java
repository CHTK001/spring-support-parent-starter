package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.lang.balance.LoadBalance;
import com.chua.common.support.lang.balance.Node;
import com.chua.common.support.cache.CacheProvider;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.value.Value;
import com.chua.starter.common.support.algorithm.crypto.Codec;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.utils.ResponseUtils;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.utils.BeanUtils;

import java.util.Objects;

import static com.chua.common.support.lang.code.ReturnCode.RESOURCE_OAUTH_ERROR;
import static com.chua.common.support.lang.code.ReturnCode.RESULT_ACCESS_UNAUTHORIZED;
import static com.chua.starter.oauth.client.support.infomation.Information.*;

/**
 * 协议
 *
 * @author CH
 */
@Slf4j
public abstract class AbstractProtocol implements Protocol {

    /**
     * 认证信息缓存（线程安全的单例初始化）
     */
    protected static volatile CacheProvider CACHEABLE;
    private static final Object CACHE_LOCK = new Object();
    
    private final boolean enableEncryption;
    protected AuthClientProperties authClientProperties;
    private final String encryption;

    public AbstractProtocol(AuthClientProperties authClientProperties) {
        this.authClientProperties = authClientProperties;
        this.encryption = authClientProperties.getEncryption();
        this.enableEncryption = authClientProperties.isEnableEncryption();
        // 双重检查锁定，确保线程安全初始化
        if (CACHEABLE == null) {
            synchronized (CACHE_LOCK) {
                if (CACHEABLE == null) {
                    CacheProvider provider = CacheProvider.auto();
                    CACHEABLE = provider;
                    log.info("[OAuth客户端]缓存初始化完成 - 超时时间: {}秒, 冷热备份: {}", 
                            authClientProperties.getCacheTimeout(), 
                            authClientProperties.isCacheHotColdBackup());
                }
            }
        }
    }
    
    /**
     * 清除指定用户的缓存（用于用户权限变更时调用）
     *
     * @param token 用户Token
     */
    public static void invalidateCache(String token) {
        if (CACHEABLE != null && token != null) {
            String cacheKey = DigestUtils.sha256(token);
            CACHEABLE.remove(cacheKey);
            log.info("[OAuth客户端]已清除用户缓存 - Token: {}***", 
                    token.length() > 8 ? token.substring(0, 8) : token);
        }
    }
    
    /**
     * 清除所有缓存
     */
    public static void invalidateAllCache() {
        if (CACHEABLE != null) {
            CACHEABLE.clear();
            log.info("[OAuth客户端]已清除所有缓存");
        }
    }


    /**
     * 获取认证信息
     *
     * @param cookies     cookies
     * @param token       令牌
     * @param subProtocol 认证协议
     * @return 认证信息
     */
    protected abstract AuthenticationInformation approve(Cookie cookies, String token, String subProtocol);
    /**
     * 根据用户编码获取认证信息
     *
     * @param appKeySecret 用户编码，用于唯一标识一个用户
     *                 示例: "USER_001" - 系统中唯一的用户编码
     * @return AuthenticationInformation 认证信息对象，包含用户的认证状态和用户信息
     *         示例: AuthenticationInformation{information=OK, returnResult=UserResume{userId='1', username='admin'}}
     *
     * @author CH
     * @since 2023-01-01
     */
    protected abstract AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret);

    /**
     * 获取认证信息（升级用）
     *
     * @param cookie       cookies
     * @param token        令牌
     * @param upgradeType  升级类型
     * @param refreshToken 刷新令牌
     * @return 认证信息
     */
    protected abstract AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken);


    /**
     * 是否加密
     *
     * @return 是否加密
     */
    protected boolean isEncode() {
        return enableEncryption;
    }
    /**
     * 创建数据
     *
     * @param value 值
     * @param key   密钥
     * @return 数据
     */
    protected String createData(String value, String key) {
        if (!enableEncryption) {
            return value;
        }
        String data = value;
        // AK/SK 二次加密
        AuthClientProperties.Aksk aksk = authClientProperties.getKey();
        if (aksk != null && aksk.isEnabled() && StringUtils.isNotBlank(aksk.getSecretKey())) {
            data = Codec.build(encryption, aksk.getSecretKey()).encodeHex(data);
        }
        return Codec.build(encryption, key).encodeHex(data);
    }

    /**
     * 创建数据
     *
     * @param jsonObject jsonObject
     * @param key        密钥
     * @return 数据
     */
    protected String createData(JsonObject jsonObject, String key) {
        String jsonObjectJSONString = jsonObject.toJSONString();
        if (!enableEncryption) {
            return jsonObjectJSONString;
        }
        String data = jsonObjectJSONString;
        // AK/SK 二次加密
        AuthClientProperties.Aksk aksk = authClientProperties.getKey();
        if (aksk != null && aksk.isEnabled() && StringUtils.isNotBlank(aksk.getSecretKey())) {
            data = Codec.build(encryption, aksk.getSecretKey()).encodeHex(data);
        }
        return Codec.build(encryption, key).encodeHex(data);
    }

    /**
     * 构建 AppKey 鉴权请求体。
     * <p>
     * 当前服务端将 x-oauth-user-code 视为 appKey 字符串，并要求 code 字段参与用户信息回填。
     * AppKeySecret.body 兼容两种形式：
     * 1. 纯字符串：按 code 处理
     * 2. JSON 对象：展开并合并到请求体
     * </p>
     *
     * @param appKeySecret AppKey 参数
     * @return 请求体
     */
    protected JsonObject createAppKeyAuthenticationData(AppKeySecret appKeySecret) {
        JsonObject jsonObject = new JsonObject();
        if (appKeySecret == null) {
            return jsonObject;
        }

        if (StringUtils.isNotBlank(appKeySecret.getUserCode())) {
            jsonObject.put("x-oauth-user-code", appKeySecret.getUserCode());
        }
        if (StringUtils.isNotBlank(appKeySecret.getAppId())) {
            jsonObject.put("x-oauth-app-id", appKeySecret.getAppId());
        }
        if (StringUtils.isNotBlank(appKeySecret.getXTime())) {
            jsonObject.put("x-time", appKeySecret.getXTime());
        }
        if (StringUtils.isNotBlank(appKeySecret.getXRandom())) {
            jsonObject.put("x-random", appKeySecret.getXRandom());
        }
        if (StringUtils.isNotBlank(appKeySecret.getXSign())) {
            jsonObject.put("x-sign", appKeySecret.getXSign());
        }
        mergeAppKeyRequestBody(jsonObject, appKeySecret.getBody());
        return jsonObject;
    }

    /**
     * 合并 AppKey 鉴权附加参数。
     *
     * @param target 目标请求体
     * @param body   原始 body
     */
    protected void mergeAppKeyRequestBody(JsonObject target, String body) {
        if (target == null || StringUtils.isBlank(body)) {
            return;
        }

        String trimmed = body.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JsonObject bodyObject = Json.fromJson(trimmed, JsonObject.class);
                if (bodyObject != null) {
                    target.putAll(bodyObject);
                    return;
                }
            } catch (Exception ignored) {
                // 非法 JSON 时回退为 code 字段，兼容老调用方只传订单/用户编码场景。
            }
        }

        if (StringUtils.isBlank(target.getObject("code", String.class))) {
            target.put("code", body);
        }
    }
    
    /**
     * 获取 AccessKey（用于请求头）
     *
     * @return AccessKey，未启用返回 null
     */
    protected String getAccessKey() {
        AuthClientProperties.Aksk aksk = authClientProperties.getKey();
        if (aksk != null && aksk.isEnabled()) {
            return aksk.getAccessKey();
        }
        return null;
    }

    /**
     * 选择地址
     *
     * @return 地址
     */
    protected String selectUrl() {
        LoadBalance balance = ServiceProvider.of(LoadBalance.class).getExtension(authClientProperties.getBalance());
        LoadBalance stringRobin = balance.create();
        String address = authClientProperties.getAddress();
        if (null == address) {
            log.warn("鉴权地址不存在");
            return null;
        }
        String[] split = OauthClientRuntimeContext.resolvePlaceholders(address).split(",");
        stringRobin.addNode((Object[]) split);
        Node robin = stringRobin.selectNode();
        return robin.getContent().toString();
    }


    @Override
    public AuthenticationInformation approve(Cookie[] cookies, String token, String subProtocol) {
        Cookie cookie = CookieUtil.get(cookies, "x-oauth-cookie");
        String cacheKey = getCacheKey(cookies, token);
        AuthenticationInformation cached = readAuthenticationInformation(cacheKey);
        if (null != cached) {
            return cached;
        }

        if (!hasValid(cookie, token)) {
            return AuthenticationInformation.noAuth();
        }
        AuthenticationInformation authenticationInformation = approve(cookie, token, subProtocol);

        writeAuthenticationInformation(cacheKey, authenticationInformation);
        return authenticationInformation;
    }

    /**
     * 获取缓存key
     *
     * @param appKeySecret userCode
     * @return 缓存key
     */
    @Override
    public AuthenticationInformation authentication(AppKeySecret appKeySecret) {
        if (null == appKeySecret) {
            return AuthenticationInformation.noAuth();
        }
        // 优先使用用户编码作为缓存 key，body 可能为空导致无法命中缓存
        String cacheKey = StringUtils.isNotBlank(appKeySecret.getUserCode())
                ? appKeySecret.getUserCode() : appKeySecret.getBody();
        AuthenticationInformation cached = readAuthenticationInformation(cacheKey);
        if (null != cached) {
            return cached;
        }

        AuthenticationInformation authenticationInformation = authenticationUserCode(appKeySecret);

        writeAuthenticationInformation(cacheKey, authenticationInformation);
        return authenticationInformation;
    }
    /**
     * 是否有效
     *
     * @param cookie cookie
     * @param token  令牌
     * @return 是否有效
     */
    private boolean hasValid(Cookie cookie, String token) {
        return null != cookie || StringUtils.isNotBlank(token);
    }

    @Override
    public LoginResult upgrade(Cookie[] cookies, String token, UpgradeType upgradeType, String refreshToken) {
        Cookie cookie = CookieUtil.get(cookies, "x-oauth-cookie");
        // REFRESH 类型只需要 refreshToken，不需要现有的 cookie/accessToken
        boolean isRefresh = upgradeType == UpgradeType.REFRESH && refreshToken != null && !refreshToken.isEmpty();
        if (!isRefresh && !hasValid(cookie, token)) {
            throw new IllegalArgumentException("认证失败");
        }
        AuthenticationInformation authenticationInformation = upgradeInformation(cookie, token, upgradeType, refreshToken);
        if(authenticationInformation.getInformation() != OK) {
            throw new IllegalArgumentException("升级失败");
        }
        return new LoginResult(authenticationInformation.getToken(), authenticationInformation.getRefreshToken(), null, authenticationInformation.getReturnResult());
    }

    /**
     * 是否有缓存
     *
     * @return 是否有缓存
     */
    protected boolean hasCache(String cacheKey) {
        if (null == cacheKey) {
            return false;
        }
        try {
            Object value = CACHEABLE.get(cacheKey, Object.class);
            return null != value;
        } catch (Exception e) {
            log.warn("[OAuth客户端]缓存读取失败 - key: {}", cacheKey, e);
            return false;
        }
    }


    /**
     * 缓存认证信息
     *
     * @param key 缓存key
     * @return 缓存信息
     */
    protected LoginResult createUpgradeResponse(ReturnResult returnResult, String key) {
        String code = returnResult.getCode();
        if (RESOURCE_OAUTH_ERROR.getCode().equals(code)) {
            HttpServletRequest servletRequest = RequestUtils.getRequest();
            if (null != servletRequest) {
                CookieUtil.remove(servletRequest, ResponseUtils.getResponse(), "x-oauth-cookie");
            }

            throw new IllegalArgumentException("OSS服务器不存在");
        }
        Object data = returnResult.getData();
        if (Objects.isNull(data)) {
            throw new IllegalArgumentException("OSS服务器不存在");

        }

        String body;
        if (ReturnCode.OK.getCode().equals(code)) {
            // 根据加密配置决定是否解密（key 为 null 时说明服务端未加密响应，直接用原始数据）
            if (authClientProperties.isEnableEncryption() && key != null) {
                body = Codec.build(encryption, key).decodeHex(data.toString());
            } else {
                body = data.toString();
            }
            LoginResult loginResult = Json.fromJson(body, LoginResult.class);
            UserResume userResume = BeanUtils.copyProperties(loginResult.getUserResume(), UserResume.class);
            return loginResult;
        }

        throw new IllegalArgumentException("OSS服务器不存在");

    }

    /**
     * 创建认证信息
     *
     * @param returnResult 返回结果
     * @param key          密钥
     * @return 认证信息
     */
    protected AuthenticationInformation createAuthenticationInformation(ReturnResult returnResult, String key, String path) {
        String code = returnResult.getCode();
        HttpServletRequest servletRequest = RequestUtils.getRequest();
        if (RESOURCE_OAUTH_ERROR.getCode().equals(code) || RESULT_ACCESS_UNAUTHORIZED.getCode().equals(code)) {
            if (null != servletRequest) {
                unregisterFromRequest(servletRequest);
            }
            log.warn("认证失败 ==> {}", returnResult.getMsg());
            return new AuthenticationInformation(AUTHENTICATION_FAILURE, null);
        }

        Object data = returnResult.getData();
        if (Objects.isNull(data)) {
            unregisterFromRequest(servletRequest);
            log.warn("认证失败 ==> {}", returnResult.getMsg());
            // 透传服务端的真实错误消息（如"账号/密码不正确"），而非固定的"鉴权服务器异常"
            // 鉴权路径（verify/oauth）返回 AUTHENTICATION_FAILURE(403)，符合 HTTP 语义
            // 登录/其他路径返回 AUTHENTICATION_SERVER_EXCEPTION(500)，保持原有行为
            String oauthUrl = authClientProperties.getOauthUrl();
            boolean isVerifyPath = path != null && (path.equals(oauthUrl)
                    || path.endsWith("/verify") || path.endsWith("/oauth") || path.equals("oauth"));
            Information failInformation = isVerifyPath ? AUTHENTICATION_FAILURE : AUTHENTICATION_SERVER_EXCEPTION;
            AuthenticationInformation failInfo = new AuthenticationInformation(failInformation, null);
            if (returnResult.getMsg() != null && !returnResult.getMsg().isEmpty()) {
                failInfo.setErrorMessage(returnResult.getMsg());
            }
            return failInfo;
        }

        String body;
        if (ReturnCode.OK.getCode().equals(code)) {
            // 根据加密配置决定是否解密（key 为 null 时说明服务端未加密响应，直接用原始数据）
            if (authClientProperties.isEnableEncryption() && key != null) {
                body = Codec.build(encryption, key).decodeHex(data.toString());
            } else {
                body = data.toString();
            }

            if(path != null && path.endsWith("login")) {
                LoginResult loginResult = Json.fromJson(body, LoginResult.class);
                UserResume userResume = loginResult.getUserResume();
                registerToRequest(userResume);
                AuthenticationInformation authenticationInformation = new AuthenticationInformation(OK, userResume);
                authenticationInformation.setRefreshToken(loginResult.getRefreshToken());
                authenticationInformation.setToken(loginResult.getToken());
                return authenticationInformation;
            }

            if(path != null && path.endsWith("logout")) {
                unregisterFromRequest(servletRequest);
                return new AuthenticationInformation(Information.OK, null);
            }

            // temporary-token 路径：服务端返回的 data 是 token 字符串
            if(path != null && path.endsWith("temporary-token")) {
                AuthenticationInformation authenticationInformation = new AuthenticationInformation(OK, null);
                authenticationInformation.setToken(body);
                return authenticationInformation;
            }

            UserResult userResume = Json.fromJson(body, UserResult.class);
            AuthenticationInformation authenticationInformation = new AuthenticationInformation(OK, userResume);
            authenticationInformation.setToken(userResume.getToken());
            authenticationInformation.setRefreshToken(userResume.getRefreshToken());
            return authenticationInformation;
        }


        return new AuthenticationInformation(OTHER, null);
    }

    /**
     * 删除hook
     *
     */
    private void unregisterFromRequest(HttpServletRequest servletRequest) {
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.removeUsername();
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.removeUserInfo();
        if (servletRequest != null) {
            // 失败路径不强制创建 session，仅清理已存在的会话属性
            jakarta.servlet.http.HttpSession session = servletRequest.getSession(false);
            if (session != null) {
                session.removeAttribute(com.chua.starter.oauth.client.support.execute.AuthSessionUtils.SESSION_USERID);
                session.removeAttribute(com.chua.starter.oauth.client.support.execute.AuthSessionUtils.SESSION_TENANT_ID);
            }
        }
        if(null != servletRequest) {
            CookieUtil.remove(servletRequest, ResponseUtils.getResponse(), "x-oauth-cookie");
        }
    }
    /**
     * 注册
     *
     * @return {@link LoginAuthResult}
     */
    public static void registerToRequest(UserResume userResume) {
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.setUsername(userResume.getUsername());
        var request = com.chua.starter.oauth.client.support.execute.AuthSessionUtils.getRequest();
        if (request != null) {
            if (userResume.getUserId() != null) {
                request.getSession().setAttribute(com.chua.starter.oauth.client.support.execute.AuthSessionUtils.SESSION_USERID, userResume.getUserId());
            }
            if (userResume.getTenantId() != null) {
                request.getSession().setAttribute(com.chua.starter.oauth.client.support.execute.AuthSessionUtils.SESSION_TENANT_ID, userResume.getTenantId());
            }
        }
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.setUserInfo(userResume);
    }

    /**
     * 清除缓存信息
     *
     * @param cacheKey 缓存key
     */
    protected void clearAuthenticationInformation(String cacheKey) {
        if (null == cacheKey) {
            return;
        }
        try {
            CACHEABLE.remove(cacheKey);
        } catch (Exception e) {
            log.warn("[OAuth客户端]缓存清除失败 - key: {}", cacheKey, e);
        }
    }

    /**
     * 读取缓存认证信息
     * <p>单次读取，命中且有效时回填请求上下文，无效缓存自动清除；
     * 缓存读取异常时降级为未命中，不阻断鉴权主流程</p>
     *
     * @param cacheKey 缓存key
     * @return 认证信息，未命中或无效返回 null
     */
    protected AuthenticationInformation readAuthenticationInformation(String cacheKey) {
        if (null == cacheKey) {
            return null;
        }
        Object value;
        try {
            value = CACHEABLE.get(cacheKey, Object.class);
        } catch (Exception e) {
            log.warn("[OAuth客户端]缓存读取异常，降级为未命中 - key: {}", cacheKey, e);
            return null;
        }
        if (null == value) {
            return null;
        }

        AuthenticationInformation authenticationInformation;
        try {
            authenticationInformation = (AuthenticationInformation) value;
        } catch (Exception e) {
            clearAuthenticationInformation(cacheKey);
            return null;
        }
        if (null == authenticationInformation
                || authenticationInformation.getInformation() == null
                || authenticationInformation.getInformation().getCode() != 200) {
            clearAuthenticationInformation(cacheKey);
            return null;
        }

        UserResume userResume = authenticationInformation.getReturnResult();
        if (null == userResume) {
            clearAuthenticationInformation(cacheKey);
            return null;
        }
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.setUsername(userResume.getUsername());
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.setUserInfo(userResume);
        var request = com.chua.starter.oauth.client.support.execute.AuthSessionUtils.getRequest();
        if (request != null) {
            jakarta.servlet.http.HttpSession session = request.getSession(false);
            if (session != null) {
                if (userResume.getUserId() != null) {
                    session.setAttribute(com.chua.starter.oauth.client.support.execute.AuthSessionUtils.SESSION_USERID, userResume.getUserId());
                }
                if (userResume.getTenantId() != null) {
                    session.setAttribute(com.chua.starter.oauth.client.support.execute.AuthSessionUtils.SESSION_TENANT_ID, userResume.getTenantId());
                }
            }
        }
        return authenticationInformation;
    }

    /**
     * 写入缓存认证信息
     * <p>缓存写入失败不影响鉴权主流程</p>
     *
     * @param cacheKey                 缓存key
     * @param authenticationInformation 认证信息
     */
    protected void writeAuthenticationInformation(String cacheKey, AuthenticationInformation authenticationInformation) {
        if (null == cacheKey || null == authenticationInformation) {
            return;
        }
        try {
            // 使用配置的缓存超时时间，确保认证缓存按 TTL 过期，权限变更后能及时刷新
            CACHEABLE.put(cacheKey, Value.of(authenticationInformation), authClientProperties.getCacheTimeout());
        } catch (Exception e) {
            log.warn("[OAuth客户端]缓存写入失败，降级为不缓存 - key: {}", cacheKey, e);
        }
    }

    /**
     * 获取缓存key
     * <p>对 token 进行 SHA-256 哈希，避免长字符串直接作为缓存 key 造成存储/带宽浪费</p>
     *
     * @param cookies cookies
     * @param token   token
     * @return 缓存key
     */
    protected String getCacheKey(Cookie[] cookies, String token) {
        if (StringUtils.isNotBlank(token)) {
            return DigestUtils.sha256(token);
        }

        if (null == cookies) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookie == null) {
                continue;
            }
            if ("x-oauth-cookie".equals(cookie.getName())) {
                return DigestUtils.sha256(cookie.getValue());
            }
        }

        return null;
    }

}