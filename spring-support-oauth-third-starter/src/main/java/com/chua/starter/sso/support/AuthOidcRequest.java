package com.chua.starter.sso.support;

import com.alibaba.fastjson.JSONObject;
import com.chua.common.support.utils.StringUtils;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.UserInfoSuccessResponse;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.cache.AuthStateCache;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.enums.AuthResponseStatus;
import me.zhyd.oauth.enums.AuthUserGender;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OIDC 登录请求（Nimbus OAuth2/OIDC SDK 实现）
 *
 * <p>
 * 与 JustAuth 内置平台 {@code Auth*Request} 对齐，实现 {@link AuthRequest} 接口：
 * {@link #authorize(String)} 生成授权地址；{@link #login(AuthCallback)} 完成 code 换 token、
 * id_token 校验与用户信息拉取。
 * </p>
 *
 * <p>安全要点：id_token 强制校验签名 / iss / aud / exp / nonce，拒绝失败；state 与 nonce 与本次会话绑定后校验。</p>
 *
 * @author CH
 * @since 2026/10/06
 */
@Slf4j
public class AuthOidcRequest implements AuthRequest {

    /**
     * 平台标识
     */
    public static final String PLATFORM = "oidc";

    /**
     * 默认 scope
     */
    static final String DEFAULT_SCOPE = "openid profile email";

    /**
     * state 缓存后缀：nonce
     */
    static final String NONCE_CACHE_SUFFIX = "_nonce";

    /**
     * state 缓存后缀：issuer
     */
    public static final String ISSUER_CACHE_SUFFIX = "_issuer";

    /**
     * state 缓存后缀：scope
     */
    public static final String SCOPE_CACHE_SUFFIX = "_scope";

    /**
     * discovery 元数据缓存 TTL（毫秒）
     */
    private static final long METADATA_TTL_MILLIS = 10 * 60 * 1000L;

    /**
     * discovery 元数据缓存：issuerUri -> 元数据
     */
    private static final ConcurrentHashMap<String, CachedMetadata> METADATA_CACHE = new ConcurrentHashMap<>();

    private final String issuerUri;
    private final String scope;
    private final AuthConfig authConfig;
    private final AuthStateCache authStateCache;

    /**
     * 构造 OIDC 登录请求
     *
     * @param issuerUri      IdP issuer（如 https://accounts.google.com），不能为空
     * @param scope          请求 scope，为空时使用 {@code openid profile email}
     * @param authConfig     clientId / clientSecret / redirectUri
     * @param authStateCache state 缓存
     */
    public AuthOidcRequest(String issuerUri, String scope, AuthConfig authConfig, AuthStateCache authStateCache) {
        this.issuerUri = StringUtils.trimToEmpty(issuerUri);
        this.scope = StringUtils.isBlank(scope) ? DEFAULT_SCOPE : StringUtils.trimToEmpty(scope);
        this.authConfig = Objects.requireNonNull(authConfig, "authConfig");
        this.authStateCache = Objects.requireNonNull(authStateCache, "authStateCache");
    }

    @Override
    public String authorize(String state) {
        String realState = StringUtils.isBlank(state) ? UUID.randomUUID().toString().replace("-", "") : state;
        // 与 JustAuth getRealState 行为一致：state 本身入缓存，供回调时 checkState 校验
        authStateCache.cache(realState, realState);
        try {
            OIDCProviderMetadata metadata = resolveMetadata();
            Nonce nonce = new Nonce();
            authStateCache.cache(realState + NONCE_CACHE_SUFFIX, nonce.getValue());
            AuthenticationRequest request = new AuthenticationRequest(
                    metadata.getAuthorizationEndpointURI(),
                    new ResponseType(ResponseType.Value.CODE),
                    Scope.parse(this.scope),
                    new ClientID(authConfig.getClientId()),
                    new URI(authConfig.getRedirectUri()),
                    new State(realState),
                    nonce);
            return request.toURI().toString();
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("OIDC 授权地址生成失败, issuerUri={}", issuerUri, e);
            throw new AuthException(AuthResponseStatus.FAILURE.getMsg() + "：OIDC 授权地址生成失败", e);
        }
    }

    @Override
    public AuthToken getAccessToken(AuthCallback callback) {
        if (null == callback || StringUtils.isBlank(callback.getCode())) {
            throw new AuthException(AuthResponseStatus.ILLEGAL_CODE);
        }
        String state = callback.getState();
        if (StringUtils.isBlank(state) || !authStateCache.containsKey(state)) {
            throw new AuthException(AuthResponseStatus.ILLEGAL_STATUS);
        }
        try {
            OIDCProviderMetadata metadata = resolveMetadata();
            ClientAuthentication clientAuth = SecretAuthenticationFactory.create(authConfig);
            com.nimbusds.oauth2.sdk.TokenRequest tokenRequest = new com.nimbusds.oauth2.sdk.TokenRequest(
                    metadata.getTokenEndpointURI(),
                    clientAuth,
                    new AuthorizationCodeGrant(new AuthorizationCode(callback.getCode()), new URI(authConfig.getRedirectUri())));
            HTTPResponse httpResponse = tokenRequest.toHTTPRequest().send();
            TokenResponse tokenResponse = OIDCTokenResponseParser.parse(httpResponse);
            if (!tokenResponse.indicatesSuccess()) {
                TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
                if (null == errorResponse) {
                    log.warn("OIDC token 交换失败, issuerUri={}", issuerUri);
                } else {
                    log.warn("OIDC token 交换失败, issuerUri={}, error={}", issuerUri, errorResponse.getErrorObject());
                }
                throw new AuthException(AuthResponseStatus.ILLEGAL_TOKEN);
            }
            OIDCTokens oidcTokens = tokenResponse.toSuccessResponse() instanceof OIDCTokenResponse oidcTokenResponse
                    ? oidcTokenResponse.getOIDCTokens()
                    : null;
            if (null == oidcTokens) {
                log.warn("OIDC token 响应不是 OIDC 格式, issuerUri={}", issuerUri);
                throw new AuthException(AuthResponseStatus.ILLEGAL_TOKEN);
            }
            JWT idToken = oidcTokens.getIDToken();
            if (null == idToken) {
                log.warn("OIDC token 响应缺少 id_token, issuerUri={}", issuerUri);
                throw new AuthException(AuthResponseStatus.ILLEGAL_TOKEN);
            }
            // 强制校验 id_token：签名 / iss / aud / exp / nonce（nonce 缺失或错配直接失败）
            Nonce nonce = null;
            String nonceValue = authStateCache.get(state + NONCE_CACHE_SUFFIX);
            if (StringUtils.isNotBlank(nonceValue)) {
                nonce = new Nonce(nonceValue);
            }
            IDTokenClaimsSet claims = validateIdToken(metadata, idToken, nonce);
            if (null == claims.getNonce()) {
                log.warn("OIDC id_token 缺少 nonce, issuerUri={}", issuerUri);
                throw new AuthException(AuthResponseStatus.ILLEGAL_TOKEN);
            }
            Subject subject = claims.getSubject();
            if (null == subject || StringUtils.isBlank(subject.getValue())) {
                throw new AuthException(AuthResponseStatus.ILLEGAL_TOKEN);
            }
            AccessToken accessToken = oidcTokens.getAccessToken();
            RefreshToken refreshToken = oidcTokens.getRefreshToken();
            return AuthToken.builder()
                    .accessToken(null == accessToken ? null : accessToken.getValue())
                    .refreshToken(null == refreshToken ? null : refreshToken.getValue())
                    .expireIn(null == accessToken ? 0 : (int) accessToken.getLifetime())
                    .idToken(idToken.serialize())
                    .uid(subject.getValue())
                    .scope(null == accessToken || null == accessToken.getScope() ? this.scope : accessToken.getScope().toString())
                    .tokenType(null == accessToken ? null : accessToken.getType().getValue())
                    .build();
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("OIDC token 交换失败, issuerUri={}", issuerUri, e);
            throw new AuthException(AuthResponseStatus.FAILURE.getMsg() + "：OIDC token 交换失败", e);
        }
    }

    @Override
    public AuthUser getUserInfo(AuthToken authToken) {
        if (null == authToken || StringUtils.isBlank(authToken.getAccessToken())) {
            throw new AuthException(AuthResponseStatus.ILLEGAL_TOKEN);
        }
        try {
            OIDCProviderMetadata metadata = resolveMetadata();
            Map<String, Object> claims = requestUserInfo(metadata, authToken.getAccessToken());
            if (null == claims) {
                // userinfo 端点不可用时回退到已校验过的 id_token claims
                claims = parseIdTokenClaims(authToken.getIdToken());
            }
            if (null == claims || claims.isEmpty()) {
                throw new AuthException(AuthResponseStatus.ILLEGAL_TOKEN);
            }
            return buildAuthUser(authToken, claims);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("OIDC 用户信息获取失败, issuerUri={}", issuerUri, e);
            throw new AuthException(AuthResponseStatus.FAILURE.getMsg() + "：OIDC 用户信息获取失败", e);
        }
    }

    @Override
    public AuthResponse<AuthUser> login(AuthCallback callback) {
        try {
            AuthToken authToken = getAccessToken(callback);
            AuthUser authUser = getUserInfo(authToken);
            return AuthResponse.<AuthUser>builder()
                    .code(AuthResponseStatus.SUCCESS.getCode())
                    .msg(AuthResponseStatus.SUCCESS.getMsg())
                    .data(authUser)
                    .build();
        } catch (AuthException e) {
            log.error("OIDC 登录失败, issuerUri={}", issuerUri, e);
            return AuthResponse.<AuthUser>builder()
                    .code(e.getErrorCode())
                    .msg(e.getErrorMsg())
                    .build();
        } catch (Exception e) {
            log.error("OIDC 登录失败, issuerUri={}", issuerUri, e);
            return AuthResponse.<AuthUser>builder()
                    .code(AuthResponseStatus.FAILURE.getCode())
                    .msg(AuthResponseStatus.FAILURE.getMsg())
                    .build();
        }
    }

    /**
     * discovery 并缓存 IdP 元数据
     *
     * @return IdP 元数据
     */
    OIDCProviderMetadata resolveMetadata() {
        if (StringUtils.isBlank(issuerUri)) {
            throw new AuthException(AuthResponseStatus.PARAMETER_INCOMPLETE);
        }
        CachedMetadata cached = METADATA_CACHE.get(issuerUri);
        if (null != cached && !cached.expired()) {
            return cached.metadata;
        }
        try {
            OIDCProviderMetadata metadata = OIDCProviderMetadata.resolve(new Issuer(issuerUri));
            if (null == metadata.getAuthorizationEndpointURI() || null == metadata.getTokenEndpointURI()) {
                throw new IllegalStateException("IdP 元数据缺失 authorization/token 端点");
            }
            METADATA_CACHE.put(issuerUri, new CachedMetadata(metadata));
            return metadata;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("OIDC discovery 失败, issuerUri={}", issuerUri, e);
            throw new AuthException(AuthResponseStatus.FAILURE.getMsg() + "：OIDC 服务发现失败", e);
        }
    }

    /**
     * 校验 id_token
     *
     * @param metadata IdP 元数据
     * @param idToken  id_token
     * @param nonce    本次会话 nonce
     * @return 校验通过的 claims
     */
    private IDTokenClaimsSet validateIdToken(OIDCProviderMetadata metadata, JWT idToken, Nonce nonce) throws Exception {
        ClientID clientId = new ClientID(authConfig.getClientId());
        Issuer expectedIssuer = new Issuer(issuerUri);
        // 优先用元数据宣告的签名算法 + JWKS 端点校验
        if (null != metadata.getIDTokenJWSAlgs() && !metadata.getIDTokenJWSAlgs().isEmpty()
                && null != metadata.getJWKSetURI()) {
            IDTokenValidator validator = new IDTokenValidator(expectedIssuer, clientId,
                    metadata.getIDTokenJWSAlgs().get(0), metadata.getJWKSetURI().toURL());
            return validator.validate(idToken, nonce);
        }
        // 回退：HMAC client_secret 校验
        IDTokenValidator validator = new IDTokenValidator(expectedIssuer, clientId,
                com.nimbusds.jose.JWSAlgorithm.HS256, new Secret(authConfig.getClientSecret()));
        return validator.validate(idToken, nonce);
    }

    /**
     * 调用 userinfo 端点拉取用户信息
     *
     * @param metadata    IdP 元数据
     * @param accessToken 访问令牌
     * @return claims；端点不可用或失败时返回 null
     */
    private Map<String, Object> requestUserInfo(OIDCProviderMetadata metadata, String accessToken) {
        if (null == metadata.getUserInfoEndpointURI()) {
            return null;
        }
        try {
            UserInfoRequest userInfoRequest = new UserInfoRequest(metadata.getUserInfoEndpointURI(),
                    new BearerAccessToken(accessToken));
            HTTPResponse httpResponse = userInfoRequest.toHTTPRequest().send();
            UserInfoResponse userInfoResponse = UserInfoResponse.parse(httpResponse);
            if (!userInfoResponse.indicatesSuccess()
                    || !(userInfoResponse.toSuccessResponse() instanceof UserInfoSuccessResponse successResponse)) {
                log.warn("OIDC userinfo 请求失败, issuerUri={}, status={}", issuerUri, httpResponse.getStatusCode());
                return null;
            }
            UserInfo userInfo = successResponse.getUserInfo();
            return null == userInfo ? null : toMap(userInfo.toJSONObject());
        } catch (Exception e) {
            log.warn("OIDC userinfo 请求异常, issuerUri={}", issuerUri, e);
            return null;
        }
    }

    /**
     * 回退：解析已校验过的 id_token payload claims
     *
     * @param idToken id_token 字符串
     * @return claims；空白或解析失败返回 null
     */
    private Map<String, Object> parseIdTokenClaims(String idToken) {
        if (StringUtils.isBlank(idToken)) {
            return null;
        }
        try {
            com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(idToken);
            return toMap(new net.minidev.json.JSONObject(signedJWT.getPayload().toJSONObject()));
        } catch (Exception e) {
            log.warn("OIDC id_token 解析失败, issuerUri={}", issuerUri, e);
            return null;
        }
    }

    /**
     * claims 映射为 AuthUser
     *
     * @param authToken 认证令牌
     * @param claims    claims 集合
     * @return AuthUser
     */
    private AuthUser buildAuthUser(AuthToken authToken, Map<String, Object> claims) {
        String sub = stringOf(claims.get("sub"));
        if (StringUtils.isBlank(sub)) {
            throw new AuthException(AuthResponseStatus.ILLEGAL_TOKEN);
        }
        String username = firstNotBlank(stringOf(claims.get("preferred_username")), stringOf(claims.get("name")),
                stringOf(claims.get("email")), sub);
        String nickname = firstNotBlank(stringOf(claims.get("name")), stringOf(claims.get("nickname")), username);
        JSONObject rawUserInfo = new JSONObject();
        rawUserInfo.putAll(claims);
        return AuthUser.builder()
                .uuid(sub)
                .username(username)
                .nickname(nickname)
                .avatar(stringOf(claims.get("picture")))
                .email(stringOf(claims.get("email")))
                .gender(toGender(stringOf(claims.get("gender"))))
                .source(PLATFORM)
                .token(authToken)
                .rawUserInfo(rawUserInfo)
                .build();
    }

    /**
     * 转换性别字符串为枚举。
     *
     * @param gender 性别字符串
     * @return 性别枚举；无效返回 UNKNOWN
     */
    private static AuthUserGender toGender(String gender) {
        if (StringUtils.isBlank(gender)) {
            return AuthUserGender.UNKNOWN;
        }
        try {
            return AuthUserGender.getRealGender(gender.trim());
        } catch (Exception e) {
            return AuthUserGender.UNKNOWN;
        }
    }

    /**
     * 对象转字符串。
     *
     * @param value 待转换值
     * @return 字符串；null 原样返回
     */
    private static String stringOf(Object value) {
        return null == value ? null : String.valueOf(value);
    }

    /**
     * 取第一个非空白字符串。
     *
     * @param values 候选值
     * @return 第一个非空白值；全部空白返回 null
     */
    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * JSON 对象转 Map。
     *
     * @param jsonObject JSON 对象
     * @return Map；null 输入返回空 Map
     */
    private static Map<String, Object> toMap(net.minidev.json.JSONObject jsonObject) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (null != jsonObject) {
            for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return map;
    }

    /**
     * client_secret 认证工厂（IdP 普遍支持 client_secret_post；失败时可扩展 basic）
     */
    private static final class SecretAuthenticationFactory {
        /**
         * 私有构造器，禁止实例化。
         */
        private SecretAuthenticationFactory() {
        }

        /**
         * 创建 client_secret 认证。
         *
         * @param authConfig 认证配置
         * @return 客户端认证对象
         */
        static ClientAuthentication create(AuthConfig authConfig) {
            return new com.nimbusds.oauth2.sdk.auth.ClientSecretPost(
                    new ClientID(authConfig.getClientId()), new Secret(authConfig.getClientSecret()));
        }
    }

    /**
     * discovery 元数据缓存项
     */
    private static final class CachedMetadata {
        private final OIDCProviderMetadata metadata;
        private final long cachedAt = System.currentTimeMillis();

        /**
         * 构造缓存项。
         *
         * @param metadata discovery 元数据
         */
        private CachedMetadata(OIDCProviderMetadata metadata) {
            this.metadata = metadata;
        }

        /**
         * 是否已过期。
         *
         * @return true 表示超过 TTL
         */
        private boolean expired() {
            return System.currentTimeMillis() - cachedAt > METADATA_TTL_MILLIS;
        }
    }

    /**
     * 仅测试使用：清空 discovery 缓存
     */
    static void clearMetadataCache() {
        METADATA_CACHE.clear();
    }
}
