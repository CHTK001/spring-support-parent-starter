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
 * o认证 2.0 标准协议客户端实现
 * <p>
 * 提供符合 RFC 6749 规范的 o认证 2.0 客户端功能：
 * - 支持 客户端_凭证 授权模式
 * - 支持 密码 授权模式
 * - 支持 refresh_令牌 刷新令牌
 * - 支持令牌撤销 (RFC 7009)
 * - 支持令牌内省 (RFC 7662)
 * </p>
 *
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

    /**
     * 创建 OAuth2协议 实例
     * @param authClientProperties 认证客户端属性
     */
    public Oauth2Protocol(AuthClientProperties authClientProperties) {
        /**
         * 父。
         * @param authClientProperties 认证客户端属性
         */
        super(authClientProperties);
        this.resilience = new OAuthClientResilience(authClientProperties);
    }

    @Override
    /**
     * Approve
     * @param cookie 参数
     * @param subProtocol 字符串
     * @param token 字符串
     * @return 返回值
    */
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
 // 使用令牌内省端点验证令牌
        return introspectToken(token);
    }

    @Override
    /**
     * 认证用户编码
     * @param appKeySecret 参数
     * @return 返回值
    */
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
 // o认证 2.0 使用 客户端_凭证 模式，凭据来自客户端配置
        return clientCredentialsGrant(authClientProperties.getKey().getAccessKey(),
                authClientProperties.getKey().getSecretKey(), null);
    }

    @Override
    /**
     * upgrade信息
     * @param cookie Cookie
     * @param token 令牌
     * @param upgradeType upgrade类型
     * @param refreshToken refresh令牌
     */
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token,
                                                           UpgradeType upgradeType, String refreshToken) {
        if (upgradeType == UpgradeType.REFRESH && StringUtils.isNotBlank(refreshToken)) {
            return refreshTokenGrant(refreshToken);
        }
        // 其他升级类型使用内省验证
        return introspectToken(token);
    }

    @Override
    /**
     * 获取access令牌
     * @param Map<String 参数
     * @param authType 参数
     * @param ext 参数
     * @param password 设置认证密码
     * @param username 设置用户名
     * @return 返回值
    */
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        log.debug("OAuth2 获取访问令牌 - username: {}, authType: {}", username, authType);
        var selectedUrl = selectUrl();
        if (selectedUrl == null) {
            /**
             * login认证结果。
             * @param 500 500
             * @param "认证服务器不可用" "认证服务器不可用"
             * @return login认证结果的结果
             */
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
            /**
             * login认证结果。
             * @param 500 500
             * @param e.getMessage( e.获取消息(
             * @return login认证结果的结果
             */
            return new LoginAuthResult(500, "获取令牌失败: " + e.getMessage());
        }
    }

    @Override
    /**
     * Logout
     * @param logoutType 参数
     * @param uid 字符串
     * @param userResult 参数
     * @return 返回值
    */
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        log.debug("OAuth2 登出 - uid: {}, logoutType: {}", uid, logoutType);
        var selectedUrl = selectUrl();
        if (selectedUrl == null) {
            /**
             * 认证异常。
             * @param "认证服务器不可用" "认证服务器不可用"
             * @return 认证异常的结果
             */
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
            /**
             * 认证异常。
             * @param status 状态
             * @return 认证异常的结果
             */
            throw new AuthException("登出失败: HTTP " + status);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OAuth2客户端]登出失败", e);
            /**
             * 认证异常。
             * @param e.getMessage( e.获取消息(
             * @return 认证异常的结果
             */
            throw new AuthException("登出失败: " + e.getMessage());
        }
    }

    @Override
    /**
     * 创建temporary令牌
     * @param Map<String 参数
     * @param ext 参数
     * @param sourceToken 字符串
     * @return 返回值
    */
    public LoginAuthResult createTemporaryToken(String sourceToken, Map<String, Object> ext) {
 // o认证 2.0 标准不直接支持临时令牌，返回不支持
        /**
         * login认证结果。
         * @param 501 501
         * @param 2.0协议不支持临时令牌功能" 2.0协议不支持临时令牌功能"
         * @return login认证结果的结果
         */
        return new LoginAuthResult(501, "OAuth 2.0协议不支持临时令牌功能");
    }

    @Override
    /**
     * 获取online状态
     * @param uid 字符串
     * @return 返回值
    */
    public OnlineStatus getOnlineStatus(String uid) {
        return OnlineStatus.defaultStatus();
    }

    @Override
    /**
     * 获取online用户
     * @param query 查询
     * @return 返回值
    */
    public OnlineUserResult getOnlineUsers(OnlineUserQuery query) {
        return OnlineUserResult.empty();
    }

 // ==================== o认证 2.0 特有方法 ====================

    /**
     * 使用客户端凭据模式获取令牌
     * @param clientId 客户端标识
     * @param clientSecret 客户端secret
     * @param scope scope
     * @return 客户端凭证grant的结果
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
     * @param refreshToken refresh令牌
     * @return refresh令牌grant的结果
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
     * @param token 令牌
     * @return introspect令牌的结果
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
     * @param accessToken access令牌
     * @return 获取用户信息的结果
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
     * 构建 o认证2 表单 POST 客户端(application/x-www-form-urlencoded)
     * @param url url
     * @return formPost的结果
     */
    private com.chua.common.support.network.client.HttpClientBuilder formPost(String url) {
        return com.chua.common.support.network.client.HttpClientFactory.of(url)
                .form()
                .header("Content-Type", "application/x-www-form-urlencoded");
    }

    /**
     * 构建基础认证头（使用配置的客户端_标识/客户端_secret）
     * @return 构建basic认证的结果
     */
    private String buildBasicAuth() {
        return buildBasicAuth(
                authClientProperties.getKey().getAccessKey(),
                authClientProperties.getKey().getSecretKey()
        );
    }

    /**
     * 构建基础认证头
     * @param clientId 客户端标识
     * @param clientSecret 客户端secret
     * @return 构建basic认证的结果
     */
    private String buildBasicAuth(String clientId, String clientSecret) {
        var credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析令牌响应
     * @param response 响应
     * @return 解析令牌响应的结果
     */
    private LoginAuthResult parseTokenResponse(com.chua.common.support.network.client.ClientResponse response) {
        if (response.getStatusCode() != 200) {
            var error = Json.fromJson(response.getBodyString(), Map.class);
            var errorDesc = error != null ? getStringValue(error, "error_description") : "未知错误";
            /**
             * login认证结果。
             * @param response.getStatusCode( 响应.获取状态编码(
             * @return login认证结果的结果
             */
            return new LoginAuthResult(response.getStatusCode(), errorDesc);
        }
        var result = Json.fromJson(response.getBodyString(), Map.class);
        if (result == null) {
            /**
             * login认证结果。
             * @param 500 500
             * @param "解析响应失败" "解析响应失败"
             * @return login认证结果的结果
             */
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
     * @param response 响应
     * @return 解析认证响应的结果
     */
    private AuthenticationInformation parseAuthResponse(com.chua.common.support.network.client.ClientResponse response) {
        if (response.getStatusCode() != 200) {
            /**
             * 认证信息。
             * @param Information.AUTHENTICATION_FAILURE 信息.认证失败
             * @param null 空
             * @return 认证信息的结果
             */
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

    /**
     * 获取字符串值
     *
     * @param map 映射
     * @param key Key
     * @return 获取字符串值的结果
     */
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        var value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
