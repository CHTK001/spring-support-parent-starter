package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.core.annotation.Extension;
import com.chua.common.support.text.json.Json;
import com.chua.common.support.text.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.core.utils.IdUtils;
import com.chua.common.support.core.utils.SignUtils;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.google.common.base.Strings;
import com.chua.starter.oauth.client.support.resilience.OAuthClientResilience;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.MediaType;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.exception.AuthException;

import java.time.Duration;
import java.util.Map;

/**
 * Armeria 协议实现
 * <p>
 * 使用 Armeria WebClient 进行 OAuth 认证，基于 Netty 的高性能异步框架。
 * 相比 HTTP 协议具有以下优势：
 * <ul>
 * <li>支持 HTTP/2 多路复用，减少连接开销</li>
 * <li>异步非阻塞 IO，更高的并发处理能力</li>
 * <li>内置连接池管理，自动重连</li>
 * <li>更低的延迟和更高的吞吐量</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Extension("armeria")
@Slf4j
public class ArmeriaProtocol extends AbstractProtocol {

    private volatile WebClient webClient;
    private static final Object CLIENT_LOCK = new Object();
    
    /**
     * 弹性处理器（熔断+重试）
     */
    private final OAuthClientResilience resilience;

    /**
     * 构造函数
     *
     * @param authClientProperties 认证客户端配置
     */
    public ArmeriaProtocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
        this.resilience = new OAuthClientResilience(authClientProperties);
    }

    /**
     * 初始化 Armeria WebClient（配置超时和连接池）
     */
    private void initializeClient() {
        if (webClient == null) {
            synchronized (CLIENT_LOCK) {
                if (webClient == null) {
                    String selectedUrl = selectUrl();
                    if (selectedUrl == null) {
                        throw new IllegalStateException("OAuth 服务器地址不存在");
                    }
                    
                    String baseUrl = StringUtils.startWithAppend(selectedUrl, "http://");
                    
                    // 创建带超时配置的 ClientFactory
                    ClientFactory clientFactory = ClientFactory.builder()
                            .connectTimeoutMillis(authClientProperties.getConnectTimeout())
                            .idleTimeoutMillis(authClientProperties.getReadTimeout())
                            .maxNumEventLoopsPerEndpoint(4)
                            .maxNumEventLoopsPerHttp1Endpoint(2)
                            .build();
                    
                    // 创建 WebClient
                    this.webClient = WebClient.builder(baseUrl)
                            .factory(clientFactory)
                            .responseTimeoutMillis(authClientProperties.getRequestTimeout())
                            .writeTimeoutMillis(authClientProperties.getConnectTimeout())
                            .maxResponseLength(10 * 1024 * 1024) // 10MB
                            .build();
                    
                    log.info("[Armeria]OAuth客户端已初始化 - URL: {}, 连接超时: {}ms, 读取超时: {}ms", 
                            baseUrl, authClientProperties.getConnectTimeout(), authClientProperties.getReadTimeout());
                }
            }
        }
    }

    @Override
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
        // 如果传入的subProtocol（认证类型）为空，尝试从Header读取 x-oauth-type
        if (StringUtils.isBlank(subProtocol)) {
            jakarta.servlet.http.HttpServletRequest request = RequestUtils.getRequest();
            if (request != null) {
                subProtocol = request.getHeader("x-oauth-type");
                if (StringUtils.isNotBlank(subProtocol)) {
                    log.debug("[ArmeriaProtocol]从Header读取认证类型: {}", subProtocol);
                }
            }
        }
        
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-cookie", null == cookie ? null : cookie.getValue());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        var request = RequestUtils.getRequest();
        jsonObject.put("x-oauth-param-address", request != null ? RequestUtils.getIpAddress(request) : "unknown");
        jsonObject.put("x-oauth-param-app-name", SpringBeanUtils.getEnvironment().resolvePlaceholders("${spring.application.name:}"));
        return createAuthenticationInformation(jsonObject, null, authClientProperties.getOauthUrl());
    }

    @Override
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
        // 注意：指纹校验应该在Filter层进行，因为需要比对用户信息里的指纹
        // 协议层此时还没有用户信息，无法进行真正的指纹比对
        // 指纹比对逻辑在 AuthFilter.verifyFingerprint() 中实现
        
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-user-code", Json.toJSONBytes(appKeySecret));
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        var request = RequestUtils.getRequest();
        jsonObject.put("x-oauth-param-address", request != null ? RequestUtils.getIpAddress(request) : "unknown");
        jsonObject.put("x-oauth-param-app-name", SpringBeanUtils.getEnvironment().resolvePlaceholders("${spring.application.name:}"));
        return createAuthenticationInformation(jsonObject, null, authClientProperties.getOauthUrl());
    }
    
    /**
     * 脱敏 AppKey
     */
    private String maskAppKey(String appKey) {
        if (appKey == null || appKey.length() < 8) {
            return "***";
        }
        return appKey.substring(0, 4) + "..." + appKey.substring(appKey.length() - 4);
    }
    
    /**
     * 脱敏指纹
     */
    private String maskFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 8) {
            return "***";
        }
        return fingerprint.substring(0, 4) + "..." + fingerprint.substring(fingerprint.length() - 4);
    }

    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-cookie", null == cookie ? null : cookie.getValue());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-refresh-token", refreshToken);
        jsonObject.put("x-oauth-upgrade-type", upgradeType.name().toUpperCase());
        
        AuthenticationInformation authenticationInformation = createAuthenticationInformation(jsonObject, upgradeType, "upgrade");
        if (authenticationInformation.getInformation() == Information.OK) {
            if (upgradeType == UpgradeType.VERSION) {
                com.chua.starter.oauth.client.support.execute.AuthSessionUtils.removeUserInfo();
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
        String token = information.getToken();
        if(null != token) {
            loginAuthResult.setToken(token);
        }
        loginAuthResult.setRefreshToken(information.getRefreshToken());
        return loginAuthResult;
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-uid", uid);
        jsonObject.put("x-oauth-logout-type", logoutType);
        
        AuthenticationInformation information = createAuthenticationInformation(jsonObject, null, authClientProperties.getLogoutPage());
        if (information.getInformation() == Information.OK) {
            return LoginAuthResult.OK;
        }
        throw new AuthException(information.getInformation().getMessage());
    }

    @Override
    public OnlineUserResult getOnlineUsers(OnlineUserQuery query) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        if (query != null) {
            jsonObject.put("x-oauth-query-username", query.getUsername());
            jsonObject.put("x-oauth-query-ip", query.getIp());
            jsonObject.put("x-oauth-query-page", query.getPage());
            jsonObject.put("x-oauth-query-size", query.getSize());
        }
        try {
            AuthenticationInformation information = createAuthenticationInformation(
                    jsonObject, null, "/online/list");
            if (information.getInformation().getCode() == 200) {
                Object data = information.getReturnResult();
                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> resultMap = (java.util.Map<String, Object>) data;
                    return parseOnlineUserResult(resultMap);
                }
            }
        } catch (Exception e) {
            log.warn("获取在线用户列表失败", e);
        }
        return OnlineUserResult.empty();
    }

    @Override
    public OnlineStatus getOnlineStatus(String uid) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-uid", uid);
        try {
            AuthenticationInformation information = createAuthenticationInformation(
                    jsonObject, null, "/online/status");
            if (information.getInformation().getCode() == 200) {
                Object data = information.getReturnResult();
                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> resultMap = (java.util.Map<String, Object>) data;
                    OnlineStatus status = new OnlineStatus();
                    status.setOnlineCount(getIntValue(resultMap, "onlineCount", 0));
                    status.setMaxOnlineCount(getIntValue(resultMap, "maxOnlineCount", -1));
                    status.setOnlineMode(getStringValue(resultMap, "onlineMode", "MULTIPLE"));
                    return status;
                }
            }
        } catch (Exception e) {
            log.warn("获取用户在线状态失败: uid={}", uid, e);
        }
        return OnlineStatus.defaultStatus();
    }

    private OnlineUserResult parseOnlineUserResult(java.util.Map<String, Object> resultMap) {
        OnlineUserResult result = new OnlineUserResult();
        result.setTotal(getIntValue(resultMap, "total", 0));
        result.setPage(getIntValue(resultMap, "page", 1));
        result.setSize(getIntValue(resultMap, "size", 20));
        Object usersObj = resultMap.get("users");
        if (usersObj instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> usersList = 
                    (java.util.List<java.util.Map<String, Object>>) usersObj;
            java.util.List<OnlineUserInfo> users = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> userMap : usersList) {
                OnlineUserInfo info = new OnlineUserInfo();
                info.setUserId(getStringValue(userMap, "userId", null));
                info.setUsername(getStringValue(userMap, "username", null));
                info.setNickname(getStringValue(userMap, "nickname", null));
                info.setLoginIp(getStringValue(userMap, "loginIp", null));
                info.setLoginAddress(getStringValue(userMap, "loginAddress", null));
                info.setBrowser(getStringValue(userMap, "browser", null));
                info.setOs(getStringValue(userMap, "os", null));
                info.setToken(getStringValue(userMap, "token", null));
                info.setLoginType(getStringValue(userMap, "loginType", null));
                Object loginTimeObj = userMap.get("loginTime");
                if (loginTimeObj instanceof Number) {
                    info.setLoginTime(((Number) loginTimeObj).longValue());
                }
                users.add(info);
            }
            result.setUsers(users);
        } else {
            result.setUsers(java.util.Collections.emptyList());
        }
        return result;
    }

    private int getIntValue(java.util.Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private String getStringValue(java.util.Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 创建认证信息
     * 使用 Armeria WebClient 向远程认证服务器发送请求
     *
     * @param jsonObject  包含认证所需参数的JSON对象
     * @param upgradeType 升级类型
     * @param path        请求路径
     * @return 认证结果
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
     * 执行实际的 Armeria 请求
     */
    private AuthenticationInformation doRequest(JsonObject jsonObject, UpgradeType upgradeType, String path) {
        initializeClient();
        
        String selectedUrl = selectUrl();
        if (null == selectedUrl) {
            return AuthenticationInformation.authServerError();
        }

        String key = IdUtils.simpleUuid();
        jsonObject.put("x-ext-timestamp", System.currentTimeMillis());

        String timestamp = System.nanoTime() + "";
        String key1 = IdUtils.simpleUuid();

        try {
            String requestPath = StringUtils.startWithMove(path, "/");
            
            // 构建请求体
            String requestBody = JsonObject.create()
                    .fluentPut("data", createData(jsonObject, key))
                    .toJSONString();
            
            // 添加认证类型请求头（x-oauth-type）
            jakarta.servlet.http.HttpServletRequest currentRequest = RequestUtils.getRequest();
            String authType = null;
            if (currentRequest != null) {
                authType = currentRequest.getHeader("x-oauth-type");
            }
            
            // 使用 Armeria WebClient 发送请求
            var preparation = webClient.prepare()
                    .post(requestPath)
                    .header(HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
                    .header("x-oauth-timestamp", timestamp)
                    .header("x-oauth-uuid", key1)
                    .header("x-oauth-encode", String.valueOf(isEncode()))
                    .header("x-oauth-serial", createData(key, key1))
                    .header("x-oauth-sign", SignUtils.generateSignFromMap(jsonObject));
            
            if (StringUtils.isNotBlank(authType)) {
                preparation = preparation.header("x-oauth-type", authType);
            }
            if (upgradeType != null) {
                preparation = preparation.header("x-oauth-upgrade-type", upgradeType.name());
            }
            
            AggregatedHttpResponse response = preparation
                    .content(MediaType.JSON_UTF_8, requestBody)
                    .execute()
                    .aggregate()
                    .join();

            int status = response.status().code();
            String body = response.contentUtf8();

            if (status >= 400 && status < 600) {
                log.warn("[Armeria]认证服务器返回错误状态 - URL: {}, 路径: {}, 状态码: {}", 
                        selectedUrl, path, status);
                return AuthenticationInformation.authServerNotFound();
            }

            if (Strings.isNullOrEmpty(body)) {
                log.warn("[Armeria]认证服务器返回空响应 - URL: {}, 路径: {}", selectedUrl, path);
                return AuthenticationInformation.authServerNotFound();
            }

            if (status == 200) {
                log.debug("[Armeria]认证请求成功 - URL: {}, 路径: {}", selectedUrl, path);
                String responseSerial = response.headers().get("x-oauth-response-serial");
                return createAuthenticationInformation(
                        Json.fromJson(body, ReturnResult.class),
                        responseSerial,
                        path);
            }

            return AuthenticationInformation.authServerError();
            
        } catch (Exception e) {
            log.error("[Armeria]OAuth请求异常 - 路径: {}, 异常: {}", path, e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("[Armeria]请求异常堆栈", e);
            }
            return AuthenticationInformation.authServerError();
        }
    }
}
