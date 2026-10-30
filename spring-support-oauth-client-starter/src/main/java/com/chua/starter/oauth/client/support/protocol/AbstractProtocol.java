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
import com.chua.starter.oauth.client.support.session.StaticSessionRegistry;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.user.UserRoleResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import com.chua.common.support.utils.BeanUtils;

import java.util.Objects;

import static com.chua.common.support.lang.code.ReturnCode.RESOURCE_OAUTH_ERROR;
import static com.chua.common.support.lang.code.ReturnCode.RESULT_ACCESS_UNAUTHORIZED;
import static com.chua.starter.oauth.client.support.infomation.Information.*;

/**
 * 协议
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public abstract class AbstractProtocol implements Protocol {

    /**
     * 认证信息缓存（线程安全的单例初始化）
     */
    protected static volatile CacheProvider CACHEABLE;
    /**
     * 缓存_锁
    */
    private static final Object CACHE_LOCK = new Object();
    
    /**
     * Enable加密
    */
    private final boolean enableEncryption;
    /**
     * 认证客户端属性
    */
    protected AuthClientProperties authClientProperties;
    /**
     * 加密
    */
    private final String encryption;

    /**
     * 创建 抽象协议 实例
     * @param authClientProperties 认证客户端属性
     */
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
     * @param token 用户令牌
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
     * 令牌吊销名单键前缀
    */
    private static final String REVOKED_PREFIX = "oauth:revoked:";
    /**
     * 令牌吊销名单
     * <p>与认证缓存分开存放：清除认证缓存(如全端登出)不得让已吊销令牌复活</p>
     */
    private static final CacheProvider REVOKED = CacheProvider.auto();
    /**
     * 秒级/毫秒级时间戳判定阈值
    */
    private static final long MILLIS_EPOCH_THRESHOLD = 100_000_000_000L;
    /**
     * 吊销名单兜底存活时间(秒)，与静态令牌最长有效期一致
    */
    private static final long REVOKE_FALLBACK_SECONDS = 7L * 24 * 60 * 60;

    /**
     * 吊销令牌：登记进吊销名单并清除该令牌的认证缓存
     * <p>静态令牌自身无状态，必须依赖吊销名单才能在服务端提前失效</p>
     *
     * @param token      待吊销令牌
     * @param expireTime 令牌过期时间(秒级或毫秒级时间戳)，可为空
     */
    public static void revokeToken(String token, Long expireTime) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        revokeTokenHash(DigestUtils.sha256(token), expireTime);
        invalidateCache(token);
    }

    /**
     * 按令牌摘要吊销（强制下线时只持有摘要）
     *
     * @param tokenHash  令牌 SHA-256 摘要
     * @param expireTime 令牌过期时间(秒级或毫秒级时间戳)，可为空
     */
    public static void revokeTokenHash(String tokenHash, Long expireTime) {
        if (null == tokenHash) {
            return;
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long expiresAt = null == expireTime ? 0L
                : (expireTime > MILLIS_EPOCH_THRESHOLD ? expireTime / 1000L : expireTime);
        long seconds = expiresAt > nowSeconds ? expiresAt - nowSeconds : REVOKE_FALLBACK_SECONDS;
        REVOKED.put(REVOKED_PREFIX + tokenHash, "1", seconds);
        StaticSessionRegistry.getInstance().remove(tokenHash);
        log.info("[OAuth客户端]令牌已吊销 - 摘要: {}", tokenHash.substring(0, Math.min(8, tokenHash.length())));
    }

    /**
     * 令牌是否已被吊销
     *
     * @param token 待检查令牌
     * @return 是否已吊销
     */
    public static boolean isTokenRevoked(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        return null != REVOKED.get(REVOKED_PREFIX + DigestUtils.sha256(token), String.class);
    }


    /**
     * 获取认证信息
     *
     * @param cookies     Cookie
     * @param token       令牌
     * @param subProtocol 认证协议
     * @return 认证信息
     */
    protected abstract AuthenticationInformation approve(Cookie cookies, String token, String subProtocol);
    /**
     * 根据用户编码获取认证信息
     *
     * @param appKeySecret 用户编码，用于唯一标识一个用户
     * 示例: "用户_001" - 系统中唯一的用户编码
     * @return AuthenticationInformation 认证信息对象，包含用户的认证状态和用户信息
     * 示例: 认证信息{信息=OK, 申报结果=用户resume{用户标识='1', 用户名='admin'}}
     *
     * @since 2023-01-01
     */
    protected abstract AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret);

    /**
     * 获取认证信息（升级用）
     *
     * @param cookie       Cookie
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
     * @param jsonObject json对象
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
     * 构建 appKey 鉴权请求体。
     * <p>
     * 当前服务端将 x-OAuth-用户-编码 视为 appKey 字符串，并要求 编码 字段参与用户信息回填。
     * appKeysecret.主体 兼容两种形式：
     * 1. 纯字符串：按 编码 处理
     * 2. JSON 对象：展开并合并到请求体
     * </p>
     *
     * @param appKeySecret appKey 参数
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
        /**
         * 合并appKey请求主体。
         * @param jsonObject json对象
         * @param appKeySecret.getBody( appKeysecret.getbody(
         */
        mergeAppKeyRequestBody(jsonObject, appKeySecret.getBody());
        return jsonObject;
    }

    /**
     * 合并 appKey 鉴权附加参数。
     *
     * @param target 目标请求体
     * @param body   原始 主体
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
 // 非法 JSON 时回退为 编码 字段，兼容老调用方只传订单/用户编码场景。
            }
        }

        if (StringUtils.isBlank(target.getObject("code", String.class))) {
            target.put("code", body);
        }
    }
    
    /**
     * 获取 accessKey（用于请求头）
     *
     * @return AccessKey，未启用返回 空
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
    /**
     * Approve
     * @param cookies 参数
     * @param subProtocol 字符串
     * @param token 字符串
     * @return 返回值
    */
    public AuthenticationInformation approve(Cookie[] cookies, String token, String subProtocol) {
        // 已吊销令牌的判定必须【先于】认证缓存读取：
        // 缓存里存的是签发那一刻的放行结果，若先读缓存，
        // 登出 / 被踢出 的令牌会在缓存有效期内继续放行，而且不再询问服务端
        // （表现为「登出后旧令牌仍可访问业务接口，且鉴权日志显示耗时 0ms」）。
        // StaticProtocol 已在自己的 approve 里做了这一步，但 tcp/http/rsocket 等协议
        // 直接继承本方法，此前没有这层保护，因此补到父类让各协议一致生效。
        if (isTokenRevoked(token)) {
            clearAuthenticationInformation(getCacheKey(cookies, token));
            return AuthenticationInformation.noAuth();
        }
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

        /**
         * 写入认证信息。
         * @param cacheKey 缓存Key
         * @param authenticationInformation 认证信息
         */
        writeAuthenticationInformation(cacheKey, authenticationInformation);
        return authenticationInformation;
    }

    /**
     * 获取缓存Key
     *
     * @param appKeySecret 用户编码
     * @return 缓存key
     */
    @Override
    public AuthenticationInformation authentication(AppKeySecret appKeySecret) {
        if (null == appKeySecret) {
            return AuthenticationInformation.noAuth();
        }
 // 优先使用用户编码作为缓存 Key，主体 可能为空导致无法命中缓存
        String cacheKey = StringUtils.isNotBlank(appKeySecret.getUserCode())
                ? appKeySecret.getUserCode() : appKeySecret.getBody();
        AuthenticationInformation cached = readAuthenticationInformation(cacheKey);
        if (null != cached) {
            return cached;
        }

        AuthenticationInformation authenticationInformation = authenticationUserCode(appKeySecret);

        /**
         * 写入认证信息。
         * @param cacheKey 缓存Key
         * @param authenticationInformation 认证信息
         */
        writeAuthenticationInformation(cacheKey, authenticationInformation);
        return authenticationInformation;
    }
    /**
     * 是否有效
     *
     * @param cookie Cookie
     * @param token  令牌
     * @return 是否有效
     */
    private boolean hasValid(Cookie cookie, String token) {
        return null != cookie || StringUtils.isNotBlank(token);
    }

    @Override
    /**
     * Upgrade
     * @param cookies 参数
     * @param refreshToken 字符串
     * @param token 字符串
     * @param upgradeType 参数
     * @return 返回值
    */
    public LoginResult upgrade(Cookie[] cookies, String token, UpgradeType upgradeType, String refreshToken) {
        Cookie cookie = CookieUtil.get(cookies, "x-oauth-cookie");
 // REFRESH 类型只需要 refresh令牌，不需要现有的 Cookie/access令牌
        boolean isRefresh = upgradeType == UpgradeType.REFRESH && refreshToken != null && !refreshToken.isEmpty();
        if (!isRefresh && !hasValid(cookie, token)) {
            /**
             * IllegalArgumentException。
             * @param "认证失败" "认证失败"
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("认证失败");
        }
        AuthenticationInformation authenticationInformation = upgradeInformation(cookie, token, upgradeType, refreshToken);
        if(authenticationInformation.getInformation() != OK) {
            /**
             * IllegalArgumentException。
             * @param "升级失败" "升级失败"
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("升级失败");
        }
        /**
         * login结果。
         * @param authenticationInformation.getToken( 认证信息.gettoken(
         * @return login结果的结果
         */
        return new LoginResult(authenticationInformation.getToken(), authenticationInformation.getRefreshToken(), null, authenticationInformation.getReturnResult());
    }

    /**
     * 是否有缓存
     *
     * @return 是否有缓存
     * @param cacheKey 缓存Key
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
     * @param key 缓存Key
     * @return 缓存信息
     * @param returnResult 申报结果
     */
    protected LoginResult createUpgradeResponse(ReturnResult returnResult, String key) {
        var code = returnResult.getCode();
        if (RESOURCE_OAUTH_ERROR.getCode().equals(code)) {
            var servletRequest = RequestUtils.getRequest();
            if (null != servletRequest) {
                CookieUtil.remove(servletRequest, ResponseUtils.getResponse(), "x-oauth-cookie");
            }

            /**
             * IllegalArgumentException。
             * @param "OSS服务器不存在" "对象存储服务器不存在"
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("OSS服务器不存在");
        }
        Object data = returnResult.getData();
        if (Objects.isNull(data)) {
            /**
             * IllegalArgumentException。
             * @param "OSS服务器不存在" "对象存储服务器不存在"
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("OSS服务器不存在");

        }

        String body;
        if (ReturnCode.OK.getCode().equals(code)) {
 // 根据加密配置决定是否解密（Key 为 空 时说明服务端未加密响应，直接用原始数据）
            if (authClientProperties.isEnableEncryption() && key != null) {
                body = Codec.build(encryption, key).decodeHex(data.toString());
            } else if (data instanceof String) {
                // 服务端把数据 包成 JSON 字符串 返回（HTTP/RSocket 协议的老形状），
                // 本身就是可解析文本，直接用
                body = (String) data;
            } else {
                // 服务端把数据 作为 嵌套 JSON 对象 返回（TCP 协议的形状，例如 登录 返回的
                // {"token":..,"refreshToken":..,"userResume":{..}}），反序列化后是 Map。
                // Map.toString() 产出的是 {key=value} 这种 Java toString 形式，
                // 键值之间用 = 而不是 :，不是合法 JSON，直接丢给 Json.fromJson 会抛
                // JsonParseException: Unexpected character ('=' ...): was expecting a colon
                // to separate field name and value，
                // 最终在调用方表现为「鉴权服务器异常」，登录/升級整体失败。
                // 因此这里必须先转回 JSON 文本。
                body = Json.toJson(data);
            }
            LoginResult loginResult = Json.fromJson(body, LoginResult.class);
            UserResume userResume = BeanUtils.copyProperties(loginResult.getUserResume(), UserResume.class);
            return loginResult;
        }

        /**
         * IllegalArgumentException。
         * @param "OSS服务器不存在" "对象存储服务器不存在"
         * @return IllegalArgumentException的结果
         */
        throw new IllegalArgumentException("OSS服务器不存在");

    }

    /**
     * 创建认证信息
     *
     * @param returnResult 返回结果
     * @param key          密钥
     * @return 认证信息
     * @param path 路径
     */
    protected AuthenticationInformation createAuthenticationInformation(ReturnResult returnResult, String key, String path) {
        String code = returnResult.getCode();
        HttpServletRequest servletRequest = RequestUtils.getRequest();
        if (RESOURCE_OAUTH_ERROR.getCode().equals(code) || RESULT_ACCESS_UNAUTHORIZED.getCode().equals(code)) {
            if (null != servletRequest) {
                /**
                 * 注销从请求。
                 * @param servletRequest servlet请求
                 */
                unregisterFromRequest(servletRequest);
            }
            log.warn("认证失败 ==> {}", returnResult.getMsg());
            /**
             * 认证信息。
             * @param AUTHENTICATION_FAILURE 认证失败
             * @param null 空
             * @return 认证信息的结果
             */
            return new AuthenticationInformation(AUTHENTICATION_FAILURE, null);
        }

        Object data = returnResult.getData();
        if (Objects.isNull(data)) {
            /**
             * 注销从请求。
             * @param servletRequest servlet请求
             */
            unregisterFromRequest(servletRequest);
            log.warn("认证失败 ==> {}", returnResult.getMsg());
            // 透传服务端的真实错误消息（如"账号/密码不正确"），而非固定的"鉴权服务器异常"
 // 鉴权路径（验证/oauth）返回 认证_失败(403)，符合 HTTP 语义
 // 登录/其他路径返回 认证_服务端_异常(500)，保持原有行为
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
 // 根据加密配置决定是否解密（Key 为 空 时说明服务端未加密响应，直接用原始数据）
            if (authClientProperties.isEnableEncryption() && key != null) {
                body = Codec.build(encryption, key).decodeHex(data.toString());
            } else if (data instanceof String) {
                // 服务端把数据 包成 JSON 字符串 返回（HTTP/RSocket 协议的老形状），
                // 本身就是可解析文本，直接用
                body = (String) data;
            } else {
                // 服务端把数据 作为 嵌套 JSON 对象 返回（TCP 协议的形状，例如 登录 返回的
                // {"token":..,"refreshToken":..,"userResume":{..}}），反序列化后是 Map。
                // Map.toString() 产出的是 {key=value} 这种 Java toString 形式，
                // 键值之间用 = 而不是 :，不是合法 JSON，直接丢给 Json.fromJson 会抛
                // JsonParseException: Unexpected character ('=' ...): was expecting a colon
                // to separate field name and value，
                // 最终在调用方表现为「鉴权服务器异常」，登录/升級整体失败。
                // 因此这里必须先转回 JSON 文本。
                body = Json.toJson(data);
            }

            if(path != null && path.endsWith("login")) {
                LoginResult loginResult = Json.fromJson(body, LoginResult.class);
                UserResume userResume = loginResult.getUserResume();
                /**
                 * 注册转为请求。
                 * @param userResume 用户resume
                 */
                registerToRequest(userResume);
                AuthenticationInformation authenticationInformation = new AuthenticationInformation(OK, userResume);
                authenticationInformation.setRefreshToken(loginResult.getRefreshToken());
                authenticationInformation.setToken(loginResult.getToken());
                return authenticationInformation;
            }

            if(path != null && path.endsWith("logout")) {
                /**
                 * 注销从请求。
                 * @param servletRequest servlet请求
                 */
                unregisterFromRequest(servletRequest);
                /**
                 * 认证信息。
                 * @param Information.OK 信息.OK
                 * @param null 空
                 * @return 认证信息的结果
                 */
                return new AuthenticationInformation(Information.OK, null);
            }

 // temporary-token 路径：服务端返回的是完整登录结果（token/refreshToken/userResume），
 // 与 login 同形状。早期这里直接把整个响应体当令牌，调用方拿到的
 // "令牌"其实是一段 JSON（`{"token":"AT..."}`），拿去鉴权必然失败——
 // 该缺陷此前未暴露是因为 tcp 协议的临时令牌一直没被真正使用。
            if(path != null && path.endsWith("temporary-token")) {
                LoginResult tempResult = null;
                try {
                    tempResult = Json.fromJson(body, LoginResult.class);
                } catch (Exception e) {
                    log.debug("临时令牌响应非 LoginResult 形状，按裸令牌处理: {}", e.getMessage());
                }
                if (tempResult == null || tempResult.getToken() == null) {
                    // 兼容服务端直接返回裸令牌字符串的实现
                    AuthenticationInformation bare = new AuthenticationInformation(OK, null);
                    bare.setToken(body);
                    return bare;
                }
                AuthenticationInformation authenticationInformation =
                        new AuthenticationInformation(OK, tempResult.getUserResume());
                authenticationInformation.setToken(tempResult.getToken());
                authenticationInformation.setRefreshToken(tempResult.getRefreshToken());
                return authenticationInformation;
            }

            UserResult userResume = Json.fromJson(body, UserResult.class);
 // /验证 等鉴权路径返回的 角色 可能为空，此处统一补充角色，保证菜单/接口权限判断正确
            /**
             * supplement角色。
             * @param userResume 用户resume
             */
            supplementRoles(userResume);
            AuthenticationInformation authenticationInformation = new AuthenticationInformation(OK, userResume);
            authenticationInformation.setToken(userResume.getToken());
            authenticationInformation.setRefreshToken(userResume.getRefreshToken());
            return authenticationInformation;
        }


        /**
         * 认证信息。
         * @param OTHER OTHER
         * @param null 空
         * @return 认证信息的结果
         */
        return new AuthenticationInformation(OTHER, null);
    }

    /**
     * 删除hook
     *
     * @param servletRequest servlet请求
     */
    private void unregisterFromRequest(HttpServletRequest servletRequest) {
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.removeUsername();
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.removeUserInfo();
        if (servletRequest != null) {
 // 失败路径不强制创建 会话，仅清理已存在的会话属性
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
     * @return void
     * @param userResume 用户resume
     */
    public static void registerToRequest(UserResume userResume) {
        /**
         * supplement角色。
         * @param userResume 用户resume
         */
        supplementRoles(userResume);
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
     * 补充用户角色
     * <p>
     * http / http-lite 协议下，OAuth 服务端 /验证 返回的 角色 可能为空
     * （如服务端角色查询未命中），此时通过 SPI（用户角色解析器）由业务侧补充。
     * </p>
     *
     * @param userResume 用户信息
     */
    private static void supplementRoles(UserResume userResume) {
        if (userResume == null) {
            return;
        }
        java.util.Set<String> roles = userResume.getRoles();
        if (roles != null && !roles.isEmpty()) {
            return;
        }
        try {
            UserRoleResolver resolver = resolveUserRoleResolver();
            log.info("[OAuth角色补充] 解析结果 resolver={}, userId={}, username={}, roles={}",
                    resolver != null ? resolver.getClass().getSimpleName() : "null",
                    userResume.getUserId(), userResume.getUsername(), roles);
            if (resolver == null) {
                return;
            }
            java.util.Set<String> supplement = resolver.resolve(userResume.getUserId(), userResume.getUsername());
            if (supplement != null && !supplement.isEmpty()) {
                userResume.setRoles(supplement);
                log.info("[OAuth角色补充] userId={}, roles={}", userResume.getUserId(), supplement);
            }
        } catch (Exception e) {
            log.info("[OAuth角色补充] 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 解析用户角色解析器
     * <p>
     * 优先从 Spring 容器获取（@服务 + @Spi 的跨包实现），
     * 其次回退到 SPI 同包扫描。
     * </p>
     *
     * @return 用户角色解析器，未找到时返回 空
     */
    private static UserRoleResolver resolveUserRoleResolver() {
        ApplicationContext context = null;
        try {
            context = OauthClientRuntimeContext.getApplicationContext();
        } catch (Exception ignore) {
            // 运行时上下文未初始化，忽略
        }
        if (context == null) {
            try {
                context = com.chua.spring.support.configuration.SpringBeanUtils.getApplicationContext();
            } catch (Exception ignore) {
 // thread本地 上下文未初始化，忽略
            }
        }
        if (context != null) {
            try {
                java.util.Collection<UserRoleResolver> beans = context.getBeansOfType(UserRoleResolver.class).values();
                log.info("[OAuth角色补充] Spring 容器候选数量: {}", beans.size());
                for (UserRoleResolver bean : beans) {
                    if (bean != null) {
                        return bean;
                    }
                }
            } catch (Exception e) {
                log.info("[OAuth角色补充] Spring 获取失败: {}", e.getMessage());
            }
        } else {
            log.info("[OAuth角色补充] Spring 上下文未初始化，回退 SPI");
        }
        return ServiceProvider.of(UserRoleResolver.class).getExtension("default");
    }

    /**
     * 清除缓存信息
     *
     * @param cacheKey 缓存Key
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
     * @param cacheKey 缓存Key
     * @return 认证信息，未命中或无效返回 空
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
 // 写入时使用 值 包装，读取时解包为实际认证信息
        if (value instanceof com.chua.common.support.value.Value<?> wrapper) {
            value = wrapper.getValue();
        }

        AuthenticationInformation authenticationInformation;
        try {
            authenticationInformation = (AuthenticationInformation) value;
        } catch (Exception e) {
            /**
             * clear认证信息。
             * @param cacheKey 缓存Key
             */
            clearAuthenticationInformation(cacheKey);
            return null;
        }
        if (null == authenticationInformation
                || authenticationInformation.getInformation() == null
                || authenticationInformation.getInformation().getCode() != 200) {
            /**
             * clear认证信息。
             * @param cacheKey 缓存Key
             */
            clearAuthenticationInformation(cacheKey);
            return null;
        }

        UserResume userResume = authenticationInformation.getReturnResult();
        if (null == userResume) {
            /**
             * clear认证信息。
             * @param cacheKey 缓存Key
             */
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
     * @param cacheKey                 缓存Key
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
     * 获取缓存Key
     * <p>对 token 进行 SHA-256 哈希，避免长字符串直接作为缓存 key 造成存储/带宽浪费</p>
     *
     * @param cookies Cookie
     * @param token   令牌
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
