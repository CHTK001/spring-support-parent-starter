package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.SignUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.RequestUtils;
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
import com.google.common.base.Strings;
import jakarta.servlet.http.Cookie;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.exception.AuthException;

import java.util.Map;

import static com.chua.common.support.http.HttpClientUtils.APPLICATION_JSON;

/**
 * http
 *
 * @author CH
 */
@SpiDefault
@Extension("http")
@Slf4j
public class HttpProtocol extends AbstractProtocol {

    /**
     * 弹性处理器（熔断+重试）
     */
    private final OAuthClientResilience resilience;

    /**
     * HTTP客户端实例（配置超时）
     */
    private static volatile UnirestInstance httpClient;
    private static final Object HTTP_CLIENT_LOCK = new Object();

    public HttpProtocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
        this.resilience = new OAuthClientResilience(authClientProperties);
        initHttpClient(authClientProperties);
    }

    /**
     * 初始化HTTP客户端（配置超时）
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
                            .automaticRetries(false)  // 使用自定义重试机制
                            .verifySsl(true);
                    log.info("【OAuth客户端】HTTP客户端初始化完成 - 连接超时: {}ms, 读取超时: {}ms",
                            config.getConnectTimeout(), config.getReadTimeout());
                }
            }
        }
    }

    @Override
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-cookie", null == cookie ? null : cookie.getValue());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-sub-protocol", StringUtils.defaultString(subProtocol, "DEFAULT").toUpperCase());
        jsonObject.put("x-oauth-param-address", RequestUtils.getIpAddress());
        jsonObject.put("x-oauth-param-app-name", SpringBeanUtils.getEnvironment().resolvePlaceholders("${spring.application.name:}"));
        return createAuthenticationInformation(jsonObject, null, authClientProperties.getOauthUrl());
    }

    @Override
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-user-code", Json.toJSONBytes(appKeySecret));
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-param-address", RequestUtils.getIpAddress());
        jsonObject.put("x-oauth-param-app-name", SpringBeanUtils.getEnvironment().resolvePlaceholders("${spring.application.name:}"));
        return createAuthenticationInformation(jsonObject, null, authClientProperties.getOauthUrl());
    }

    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-cookie", null == cookie ? null : cookie.getValue());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-refresh-token", refreshToken);
        jsonObject.put("x-oauth-upgrade-type", upgradeType.name().toUpperCase());
        AuthenticationInformation authenticationInformation = createAuthenticationInformation(jsonObject, upgradeType, "upgrade");
        if (authenticationInformation.getInformation() == Information.OK) {
            if (upgradeType == UpgradeType.VERSION) {
                RequestUtils.removeUserInfo();
                String cacheKey = getCacheKey(new Cookie[]{cookie}, token);
                if (hasCache(cacheKey)) {
                    clearAuthenticationInformation(cacheKey);
                }
            }
        }
        return authenticationInformation;
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-username", username);
        jsonObject.put("x-oauth-password", password);
        jsonObject.put("x-oauth-auth-type", authType);
        jsonObject.put("x-oauth-ext", ext);
        AuthenticationInformation information = createAuthenticationInformation(jsonObject, null, authClientProperties.getLoginPage());
        log.info("当前状态: {}", information.getInformation().getCode());
        log.info("当前信息: {}", information.getInformation().getMessage());
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        loginAuthResult.setCode(information.getInformation().getCode());
        loginAuthResult.setMessage(information.getInformation().getMessage());
        loginAuthResult.setUserResume(information.getReturnResult());
        loginAuthResult.setToken(information.getToken());
        loginAuthResult.setRefreshToken(information.getRefreshToken());
        return loginAuthResult;
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-uid", uid);
        jsonObject.put("x-oauth-logout-type", logoutType);
        AuthenticationInformation information = createAuthenticationInformation(jsonObject, null, authClientProperties.getLogoutPage());
        if(information.getInformation() == Information.OK) {
            return LoginAuthResult.OK;
        }
        throw new AuthException(information.getInformation().getMessage());
    }


    /**
     * 创建认证信息
     * 向远程认证服务器发送HTTP请求以验证用户凭据或升级令牌
     *
     * @param jsonObject  包含认证所需参数的JSON对象
     * @param upgradeType 升级类型，用于指定升级策略（如基于版本号或时间戳）
     * @return AuthenticationInformation 返回认证结果，包含认证状态和用户信息
     */
    protected AuthenticationInformation createAuthenticationInformation(JsonObject jsonObject, UpgradeType upgradeType, String path) {
        // 使用弹性处理器执行请求（含熔断和重试）
        return resilience.executeWithResilience(
                () -> doRequest(jsonObject, upgradeType, path),
                () -> OAuthClientResilience.createFallbackResponse(
                        authClientProperties.getCircuitBreaker().getFallbackMessage())
        );
    }

    /**
     * 执行实际的HTTP请求
     */
    private AuthenticationInformation doRequest(JsonObject jsonObject, UpgradeType upgradeType, String path) {
        // 获取认证服务器地址
        String selectedUrl = selectUrl();
        if (null == selectedUrl) {
            return AuthenticationInformation.authServerError();
        }

        // 生成随机密钥和请求头参数
        String key = IdUtils.simpleUuid();
        jsonObject.put("x-ext-timestamp", System.currentTimeMillis());

        String timestamp = System.nanoTime() + "";
        String key1 = IdUtils.simpleUuid();

        HttpResponse<String> httpResponse = null;
        try {
            // 使用配置好超时的HTTP客户端
            UnirestInstance client = httpClient != null ? httpClient : Unirest.primaryInstance();
            
            // 构建POST请求
            HttpRequestWithBody withBody = client.post(
                    StringUtils.endWithAppend(StringUtils.startWithAppend(selectedUrl, "http://"), "/") +
                            StringUtils.startWithMove(path, "/"));

            // 设置基础请求头
            HttpRequestWithBody requestWithBody = withBody
                    .header("x-oauth-timestamp", timestamp)
                    .header("x-oauth-uuid", key1)
                    .header("x-oauth-encode", String.valueOf(isEncode()))
                    .header("x-oauth-serial", createData(key, key1))
                    .header("x-oauth-sign", SignUtils.generateSignFromMap(jsonObject));

            // 如果有升级类型，则添加相应请求头
            if (null != upgradeType) {
                requestWithBody = requestWithBody.header("x-oauth-upgrade-type", upgradeType.name());
            }

            // 发送请求并获取响应
            httpResponse = requestWithBody.contentType(APPLICATION_JSON)
                    .body(JsonObject.create()
                            .fluentPut("data", createData(jsonObject, key)).toJSONString())
                    .asString();

        } catch (UnirestException e) {
            // 完整记录请求异常信息
            log.error("【OAuth客户端】认证请求异常 - URL: {}, 路径: {}, 异常类型: {}, 异常信息: {}", 
                    selectedUrl, path, e.getClass().getSimpleName(), e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("【OAuth客户端】认证请求异常堆栈", e);
            }
            return AuthenticationInformation.authServerError();
        } catch (Exception e) {
            // 捕获其他未预期异常
            log.error("【OAuth客户端】认证请求未知异常 - URL: {}, 路径: {}, 异常: {}", 
                    selectedUrl, path, e.getMessage(), e);
            return AuthenticationInformation.authServerError();
        }

        // 检查响应是否为空
        if (null == httpResponse) {
            log.warn("【OAuth客户端】认证服务器响应为空 - URL: {}, 路径: {}", selectedUrl, path);
            return AuthenticationInformation.authServerError();
        }

        // 获取响应状态码和内容
        int status = httpResponse.getStatus();
        String body = httpResponse.getBody();

        // 判断响应是否为服务器错误或空数据
        if (status >= 400 && status < 600) {
            log.warn("【OAuth客户端】认证服务器返回错误状态 - URL: {}, 路径: {}, 状态码: {}, 响应: {}", 
                    selectedUrl, path, status, body);
            return AuthenticationInformation.authServerNotFound();
        }

        if (Strings.isNullOrEmpty(body)) {
            log.warn("【OAuth客户端】认证服务器返回空响应 - URL: {}, 路径: {}, 状态码: {}", 
                    selectedUrl, path, status);
            return AuthenticationInformation.authServerNotFound();
        }

        // 成功响应时解析返回结果
        if (status == 200) {
            log.debug("【OAuth客户端】认证请求成功 - URL: {}, 路径: {}", selectedUrl, path);
            return createAuthenticationInformation(Json.fromJson(body, ReturnResult.class),
                    httpResponse.getHeaders().getFirst("x-oauth-response-serial"),
                    path);
        }

        // 其他状态码
        log.warn("【OAuth客户端】认证服务器返回未知状态 - URL: {}, 路径: {}, 状态码: {}", 
                selectedUrl, path, status);
        return AuthenticationInformation.authServerError();
    }

}
