package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.resilience.OAuthClientResilience;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.Cookie;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.exception.AuthException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * OAuth 2.0 标准协议客户端实现
 * <p>
 * 提供符合 RFC 6749 规范的 OAuth 2.0 客户端功能：
 * - 支持 client_credentials 授权模式
 * - 支持 password 授权模式
 * - 支持 refresh_token 刷新令牌
 * - 支持令牌撤销 (RFC 7009)
 * - 支持令牌内省 (RFC 7662)
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-15
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749">RFC 6749 - OAuth 2.0</a>
 */
@Extension("oauth2")
@Slf4j
public class Oauth2Protocol extends AbstractProtocol {

    /**
     * 弹性处理器（熔断+重试）
     */
    private final OAuthClientResilience resilience;

    /**
     * HTTP客户端实例
     */
    private static volatile UnirestInstance httpClient;
    private static final Object HTTP_CLIENT_LOCK = new Object();

    public Oauth2Protocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
        this.resilience = new OAuthClientResilience(authClientProperties);
        initHttpClient(authClientProperties);
    }

    /**
     * 初始化HTTP客户端
     */
    private void initHttpClient(AuthClientProperties config) {
        if (httpClient == null) {
            synchronized (HTTP_CLIENT_LOCK) {
                if (httpClient == null) {
                    httpClient = Unirest.spawnInstance();
                    httpClient.config()
                            .connectTimeout((int) config.getConnectTimeout())
                            .socketTimeout((int) config.getReadTimeout())
                            .concurrency(200, 20)
                            .automaticRetries(false)
                            .verifySsl(true);
                    log.info("[OAuth2客户端]HTTP客户端初始化完成 - 连接超时: {}ms, 读取超时: {}ms",
                            config.getConnectTimeout(), config.getReadTimeout());
                }
            }
        }
    }

    @Override
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
        // 使用令牌内省端点验证Token
        return introspectToken(token);
    }

    @Override
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
        // OAuth 2.0 使用 client_credentials 模式，凭据来自客户端配置
        String clientId = authClientProperties.getKey().getAccessKey();
        String clientSecret = authClientProperties.getKey().getSecretKey();
        return clientCredentialsGrant(clientId, clientSecret, null);
    }

    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token,
                                                           UpgradeType upgradeType, String refreshToken) {
        if (upgradeType == UpgradeType.REFRESH && StringUtils.isNotBlank(refreshToken)) {
            return refreshTokenGrant(refreshToken);
        }
        // 其他升级类型使用内省验证
        return introspectToken(token);
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        log.debug("OAuth2 获取访问令牌 - username: {}, authType: {}", username, authType);

        String selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return new LoginAuthResult(500, "认证服务器不可用");
        }

        try {
            String tokenUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/token";
            
            HttpResponse<String> response = getHttpClient()
                    .post(tokenUrl)
                    .header("Authorization", buildBasicAuth())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .field("grant_type", "password")
                    .field("username", username)
                    .field("password", password)
                    .field("scope", ext != null ? (String) ext.get("scope") : null)
                    .asString();

            return parseTokenResponse(response);
        } catch (UnirestException e) {
            log.error("[OAuth2客户端]获取令牌失败", e);
            return new LoginAuthResult(500, "获取令牌失败: " + e.getMessage());
        }
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        log.debug("OAuth2 登出 - uid: {}, logoutType: {}", uid, logoutType);

        String selectedUrl = selectUrl();
        if (selectedUrl == null) {
            throw new AuthException("认证服务器不可用");
        }

        try {
            String revokeUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/revoke";
            String token = userResult != null ? userResult.getToken() : uid;

            HttpResponse<String> response = getHttpClient()
                    .post(revokeUrl)
                    .header("Authorization", buildBasicAuth())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .field("token", token)
                    .asString();

            // RFC 7009: 无论成功与否服务端都返回200
            if (response.getStatus() == 200 || response.getStatus() == 204) {
                return LoginAuthResult.OK;
            }
            
            throw new AuthException("登出失败: HTTP " + response.getStatus());
        } catch (UnirestException e) {
            log.error("[OAuth2客户端]登出失败", e);
            throw new AuthException("登出失败: " + e.getMessage());
        }
    }

    @Override
    public LoginAuthResult createTemporaryToken(String sourceToken, Map<String, Object> ext) {
        // OAuth 2.0 标准不直接支持临时令牌，返回不支持
        return new LoginAuthResult(501, "OAuth 2.0协议不支持临时令牌功能");
    }

    @Override
    public OnlineStatus getOnlineStatus(String uid) {
        // OAuth 2.0 标准不包含在线状态查询
        return OnlineStatus.defaultStatus();
    }

    @Override
    public OnlineUserResult getOnlineUsers(OnlineUserQuery query) {
        // OAuth 2.0 标准不包含在线用户列表
        return OnlineUserResult.empty();
    }

    // ==================== OAuth 2.0 特有方法 ====================

    /**
     * 使用客户端凭据模式获取令牌
     *
     * @param clientId     客户端ID
     * @param clientSecret 客户端密钥
     * @param scope        权限范围
     * @return 认证信息
     */
    public AuthenticationInformation clientCredentialsGrant(String clientId, String clientSecret, String scope) {
        log.debug("OAuth2 client_credentials授权 - clientId: {}", clientId);

        String selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return AuthenticationInformation.authServerError();
        }

        try {
            String tokenUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/token";

            HttpResponse<String> response = getHttpClient()
                    .post(tokenUrl)
                    .header("Authorization", buildBasicAuth(clientId, clientSecret))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .field("grant_type", "client_credentials")
                    .field("scope", scope)
                    .asString();

            return parseAuthResponse(response);
        } catch (UnirestException e) {
            log.error("[OAuth2客户端]client_credentials授权失败", e);
            return AuthenticationInformation.authServerError();
        }
    }

    /**
     * 使用刷新令牌获取新的访问令牌
     *
     * @param refreshToken 刷新令牌
     * @return 认证信息
     */
    public AuthenticationInformation refreshTokenGrant(String refreshToken) {
        log.debug("OAuth2 refresh_token授权");

        String selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return AuthenticationInformation.authServerError();
        }

        try {
            String tokenUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/token";

            HttpResponse<String> response = getHttpClient()
                    .post(tokenUrl)
                    .header("Authorization", buildBasicAuth())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .field("grant_type", "refresh_token")
                    .field("refresh_token", refreshToken)
                    .asString();

            return parseAuthResponse(response);
        } catch (UnirestException e) {
            log.error("[OAuth2客户端]refresh_token授权失败", e);
            return AuthenticationInformation.authServerError();
        }
    }

    /**
     * 令牌内省 (RFC 7662)
     *
     * @param token 要验证的令牌
     * @return 认证信息
     */
    public AuthenticationInformation introspectToken(String token) {
        log.debug("OAuth2 令牌内省");

        if (StringUtils.isBlank(token)) {
            return AuthenticationInformation.noAuth();
        }

        String selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return AuthenticationInformation.authServerError();
        }

        try {
            String introspectUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/introspect";

            HttpResponse<String> response = getHttpClient()
                    .post(introspectUrl)
                    .header("Authorization", buildBasicAuth())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .field("token", token)
                    .asString();

            if (response.getStatus() != 200) {
                return AuthenticationInformation.authServerError();
            }

            Map<String, Object> result = Json.fromJson(response.getBody(), Map.class);
            if (result == null || !Boolean.TRUE.equals(result.get("active"))) {
                return AuthenticationInformation.noAuth();
            }

            // 构建用户信息
            UserResult userResult = new UserResult();
            userResult.setUserId(getStringValue(result, "sub"));
            userResult.setUsername(getStringValue(result, "username"));
            userResult.setToken(token);

            AuthenticationInformation authInfo = new AuthenticationInformation(Information.OK, userResult);
            authInfo.setToken(token);
            return authInfo;
        } catch (UnirestException e) {
            log.error("[OAuth2客户端]令牌内省失败", e);
            return AuthenticationInformation.authServerError();
        }
    }

    /**
     * 获取用户信息
     *
     * @param accessToken 访问令牌
     * @return 用户信息
     */
    public UserResume getUserInfo(String accessToken) {
        log.debug("OAuth2 获取用户信息");

        String selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return null;
        }

        try {
            String userinfoUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/userinfo";

            HttpResponse<String> response = getHttpClient()
                    .get(userinfoUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .asString();

            if (response.getStatus() != 200) {
                return null;
            }

            Map<String, Object> result = Json.fromJson(response.getBody(), Map.class);
            if (result == null) {
                return null;
            }

            UserResult userResult = new UserResult();
            userResult.setUserId(getStringValue(result, "sub"));
            userResult.setUsername(getStringValue(result, "preferred_username"));
            userResult.setNickName(getStringValue(result, "name"));
            userResult.setToken(accessToken);

            return userResult;
        } catch (UnirestException e) {
            log.error("[OAuth2客户端]获取用户信息失败", e);
            return null;
        }
    }

    // ==================== 辅助方法 ====================

    private UnirestInstance getHttpClient() {
        return httpClient != null ? httpClient : Unirest.primaryInstance();
    }

    /**
     * 构建Basic认证头（使用配置的client_id/client_secret）
     */
    private String buildBasicAuth() {
        return buildBasicAuth(
                authClientProperties.getKey().getAccessKey(),
                authClientProperties.getKey().getSecretKey()
        );
    }

    /**
     * 构建Basic认证头
     */
    private String buildBasicAuth(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * 解析Token响应
     */
    private LoginAuthResult parseTokenResponse(HttpResponse<String> response) {
        if (response.getStatus() != 200) {
            Map<String, Object> error = Json.fromJson(response.getBody(), Map.class);
            String errorDesc = error != null ? getStringValue(error, "error_description") : "未知错误";
            return new LoginAuthResult(response.getStatus(), errorDesc);
        }

        Map<String, Object> result = Json.fromJson(response.getBody(), Map.class);
        if (result == null) {
            return new LoginAuthResult(500, "解析响应失败");
        }

        LoginAuthResult authResult = new LoginAuthResult();
        authResult.setCode(200);
        authResult.setToken(getStringValue(result, "access_token"));
        authResult.setRefreshToken(getStringValue(result, "refresh_token"));

        // 构建用户信息
        UserResult userResult = new UserResult();
        userResult.setToken(authResult.getToken());
        userResult.setRefreshToken(authResult.getRefreshToken());
        Object expiresIn = result.get("expires_in");
        if (expiresIn instanceof Number) {
            userResult.setExpireTime(((Number) expiresIn).longValue());
        }
        authResult.setUserResume(userResult);

        return authResult;
    }

    /**
     * 解析认证响应
     */
    private AuthenticationInformation parseAuthResponse(HttpResponse<String> response) {
        if (response.getStatus() != 200) {
            return new AuthenticationInformation(Information.AUTHENTICATION_FAILURE, null);
        }

        Map<String, Object> result = Json.fromJson(response.getBody(), Map.class);
        if (result == null) {
            return AuthenticationInformation.authServerError();
        }

        UserResult userResult = new UserResult();
        userResult.setToken(getStringValue(result, "access_token"));
        userResult.setRefreshToken(getStringValue(result, "refresh_token"));
        Object expiresIn = result.get("expires_in");
        if (expiresIn instanceof Number) {
            userResult.setExpireTime(((Number) expiresIn).longValue());
        }

        AuthenticationInformation authInfo = new AuthenticationInformation(Information.OK, userResult);
        authInfo.setToken(userResult.getToken());
        authInfo.setRefreshToken(userResult.getRefreshToken());
        return authInfo;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
