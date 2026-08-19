package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.spi.annotations.Spi;
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
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.Cookie;
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
 * @version 1.0.0
 * @since 2025-12-15
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749">RFC 6749 - OAuth 2.0</a>
 * @author CH
 */
@Spi({"oauth2"})
@Slf4j
public class Oauth2Protocol extends AbstractProtocol {

    /**
     * 弹性处理器（熔断+重试）
     */
    private final OAuthClientResilience resilience;

    public Oauth2Protocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
        this.resilience = new OAuthClientResilience(authClientProperties);
    }

    @Override
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
        // 使用令牌内省端点验证Token
        return introspectToken(token);
    }

    @Override
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
        // OAuth 2.0 使用 client_credentials 模式，凭据来自客户端配置
        return clientCredentialsGrant(authClientProperties.getKey().getAccessKey(),
                authClientProperties.getKey().getSecretKey(), null);
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
        var selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return new LoginAuthResult(500, "认证服务器不可用");
        }
        try {
            var tokenUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/token";
            var response = formPost(tokenUrl)
                    .header("Authorization", buildBasicAuth())
                    .formData("grant_type", "password")
                    .formData("username", username)
                    .formData("password", password)
                    .formData("scope", ext != null ? (String) ext.get("scope") : null)
                    .post();
            return parseTokenResponse(response);
        } catch (Exception e) {
            log.error("[OAuth2客户端]获取令牌失败", e);
            return new LoginAuthResult(500, "获取令牌失败: " + e.getMessage());
        }
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        log.debug("OAuth2 登出 - uid: {}, logoutType: {}", uid, logoutType);
        var selectedUrl = selectUrl();
        if (selectedUrl == null) {
            throw new AuthException("认证服务器不可用");
        }
        try {
            var revokeUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/revoke";
            var token = userResult != null ? userResult.getToken() : uid;
            var response = formPost(revokeUrl)
                    .header("Authorization", buildBasicAuth())
                    .formData("token", token)
                    .post();
            // RFC 7009: 无论成功与否服务端都返回200
            var status = response.getStatusCode();
            if (status == 200 || status == 204) {
                return LoginAuthResult.OK;
            }
            throw new AuthException("登出失败: HTTP " + status);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
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
        return OnlineStatus.defaultStatus();
    }

    @Override
    public OnlineUserResult getOnlineUsers(OnlineUserQuery query) {
        return OnlineUserResult.empty();
    }

    // ==================== OAuth 2.0 特有方法 ====================

    /**
     * 使用客户端凭据模式获取令牌
     */
    public AuthenticationInformation clientCredentialsGrant(String clientId, String clientSecret, String scope) {
        log.debug("OAuth2 client_credentials授权 - clientId: {}", clientId);
        var selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return AuthenticationInformation.authServerError();
        }
        try {
            var tokenUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/token";
            var response = formPost(tokenUrl)
                    .header("Authorization", buildBasicAuth(clientId, clientSecret))
                    .formData("grant_type", "client_credentials")
                    .formData("scope", scope)
                    .post();
            return parseAuthResponse(response);
        } catch (Exception e) {
            log.error("[OAuth2客户端]client_credentials授权失败", e);
            return AuthenticationInformation.authServerError();
        }
    }

    /**
     * 使用刷新令牌获取新的访问令牌
     */
    public AuthenticationInformation refreshTokenGrant(String refreshToken) {
        log.debug("OAuth2 refresh_token授权");
        var selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return AuthenticationInformation.authServerError();
        }
        try {
            var tokenUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/token";
            var response = formPost(tokenUrl)
                    .header("Authorization", buildBasicAuth())
                    .formData("grant_type", "refresh_token")
                    .formData("refresh_token", refreshToken)
                    .post();
            return parseAuthResponse(response);
        } catch (Exception e) {
            log.error("[OAuth2客户端]refresh_token授权失败", e);
            return AuthenticationInformation.authServerError();
        }
    }

    /**
     * 令牌内省 (RFC 7662)
     */
    public AuthenticationInformation introspectToken(String token) {
        log.debug("OAuth2 令牌内省");
        if (StringUtils.isBlank(token)) {
            return AuthenticationInformation.noAuth();
        }
        var selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return AuthenticationInformation.authServerError();
        }
        try {
            var introspectUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/introspect";
            var response = formPost(introspectUrl)
                    .header("Authorization", buildBasicAuth())
                    .formData("token", token)
                    .post();
            if (response.getStatusCode() != 200) {
                return AuthenticationInformation.authServerError();
            }
            var result = Json.fromJson(response.getBodyString(), Map.class);
            if (result == null || !Boolean.TRUE.equals(result.get("active"))) {
                return AuthenticationInformation.noAuth();
            }
            var userResult = new UserResult();
            userResult.setUserId(getStringValue(result, "sub"));
            userResult.setUsername(getStringValue(result, "username"));
            userResult.setToken(token);
            var authInfo = new AuthenticationInformation(Information.OK, userResult);
            authInfo.setToken(token);
            return authInfo;
        } catch (Exception e) {
            log.error("[OAuth2客户端]令牌内省失败", e);
            return AuthenticationInformation.authServerError();
        }
    }

    /**
     * 获取用户信息
     */
    public UserResume getUserInfo(String accessToken) {
        log.debug("OAuth2 获取用户信息");
        var selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return null;
        }
        try {
            var userinfoUrl = StringUtils.endWithAppend(selectedUrl, "/") + "oauth2/userinfo";
            var response = com.chua.common.support.network.client.HttpClientFactory.of(userinfoUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .get();
            if (response.getStatusCode() != 200) {
                return null;
            }
            var result = Json.fromJson(response.getBodyString(), Map.class);
            if (result == null) {
                return null;
            }
            var userResult = new UserResult();
            userResult.setUserId(getStringValue(result, "sub"));
            userResult.setUsername(getStringValue(result, "preferred_username"));
            userResult.setNickName(getStringValue(result, "name"));
            userResult.setToken(accessToken);
            return userResult;
        } catch (Exception e) {
            log.error("[OAuth2客户端]获取用户信息失败", e);
            return null;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建 OAuth2 表单 POST 客户端(application/x-www-form-urlencoded)
     */
    private com.chua.common.support.network.client.HttpClientBuilder formPost(String url) {
        return com.chua.common.support.network.client.HttpClientFactory.of(url)
                .form()
                .header("Content-Type", "application/x-www-form-urlencoded");
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
        var credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析Token响应
     */
    private LoginAuthResult parseTokenResponse(com.chua.common.support.network.client.ClientResponse response) {
        if (response.getStatusCode() != 200) {
            var error = Json.fromJson(response.getBodyString(), Map.class);
            var errorDesc = error != null ? getStringValue(error, "error_description") : "未知错误";
            return new LoginAuthResult(response.getStatusCode(), errorDesc);
        }
        var result = Json.fromJson(response.getBodyString(), Map.class);
        if (result == null) {
            return new LoginAuthResult(500, "解析响应失败");
        }
        var authResult = new LoginAuthResult();
        authResult.setCode(200);
        authResult.setToken(getStringValue(result, "access_token"));
        authResult.setRefreshToken(getStringValue(result, "refresh_token"));
        var userResult = new UserResult();
        userResult.setToken(authResult.getToken());
        userResult.setRefreshToken(authResult.getRefreshToken());
        var expiresIn = result.get("expires_in");
        if (expiresIn instanceof Number number) {
            userResult.setExpireTime(number.longValue());
        }
        authResult.setUserResume(userResult);
        return authResult;
    }

    /**
     * 解析认证响应
     */
    private AuthenticationInformation parseAuthResponse(com.chua.common.support.network.client.ClientResponse response) {
        if (response.getStatusCode() != 200) {
            return new AuthenticationInformation(Information.AUTHENTICATION_FAILURE, null);
        }
        var result = Json.fromJson(response.getBodyString(), Map.class);
        if (result == null) {
            return AuthenticationInformation.authServerError();
        }
        var userResult = new UserResult();
        userResult.setToken(getStringValue(result, "access_token"));
        userResult.setRefreshToken(getStringValue(result, "refresh_token"));
        var expiresIn = result.get("expires_in");
        if (expiresIn instanceof Number number) {
            userResult.setExpireTime(number.longValue());
        }
        var authInfo = new AuthenticationInformation(Information.OK, userResult);
        authInfo.setToken(userResult.getToken());
        authInfo.setRefreshToken(userResult.getRefreshToken());
        return authInfo;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        var value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
