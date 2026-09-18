package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.utils.SignUtils;
import com.chua.starter.oauth.client.support.trace.TraceContext;
import com.chua.starter.oauth.client.support.trace.TraceSpan;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.resilience.OAuthClientResilience;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.google.common.base.Strings;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.exception.AuthException;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
* http
*
* @author CH
* @since 4.0.0
 */
@Spi({"http", "http-lite"})
@Slf4j
public class HttpProtocol extends AbstractProtocol {

    /**
    * 弹性处理器（熔断+重试）
    */
    private final OAuthClientResilience resilience;

    /**
    * 创建 http协议 实例
    * @param authClientProperties 认证客户端属性
    */
    public HttpProtocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
        this.resilience = new OAuthClientResilience(authClientProperties);
    }

    @Override
    /** Approve */
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
 // 如果传入的sub协议（认证类型）为空，尝试从头部读取 x-OAuth-类型
        if (StringUtils.isBlank(subProtocol)) {
            HttpServletRequest request = RequestUtils.getRequest();
            if (request != null) {
                subProtocol = request.getHeader("x-oauth-type");
                if (StringUtils.isNotBlank(subProtocol)) {
                    log.debug("[HttpProtocol]从Header读取认证类型: {}", subProtocol);
                }
            }
        }
        
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-cookie", null == cookie ? null : cookie.getValue());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        var request = RequestUtils.getRequest();
        jsonObject.put("x-oauth-param-address", request != null ? RequestUtils.getIpAddress(request) : "unknown");
        jsonObject.put("x-oauth-param-app-name", OauthClientRuntimeContext.getApplicationName());
        return createAuthenticationInformation(jsonObject, null, authClientProperties.getOauthUrl());
    }

    @Override
    /** 认证用户编码 */
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
 // 注意：指纹校验应该在过滤器层进行，因为需要比对用户信息里的指纹
        // 协议层此时还没有用户信息，无法进行真正的指纹比对
        // 指纹比对逻辑在 AuthFilter.verifyFingerprint() 中实现
        
        JsonObject jsonObject = createAppKeyAuthenticationData(appKeySecret);
        // 构建认证数据
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        var request = RequestUtils.getRequest();
        jsonObject.put("x-oauth-param-address", request != null ? RequestUtils.getIpAddress(request) : "unknown");
        jsonObject.put("x-oauth-param-app-name", OauthClientRuntimeContext.getApplicationName());
        return createAuthenticationInformation(jsonObject, null, authClientProperties.getOauthUrl());
    }
    
    /**
    * 脱敏 app键
    * @param appKey app键
    * @return maskapp键的结果
    */
    private String maskAppKey(String appKey) {
        if (appKey == null || appKey.length() < 8) {
            return "***";
        }
        return appKey.substring(0, 4) + "..." + appKey.substring(appKey.length() - 4);
    }
    
    /**
    * 脱敏指纹
    * @param fingerprint fingerprint
    * @return maskFingerprint的结果
    */
    private String maskFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 8) {
            return "***";
        }
        return fingerprint.substring(0, 4) + "..." + fingerprint.substring(fingerprint.length() - 4);
    }

    @Override
    /** upgrade信息 */
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
                com.chua.starter.oauth.client.support.execute.AuthSessionUtils.removeUserInfo();
                String cacheKey = getCacheKey(new Cookie[]{cookie}, token);
                if (hasCache(cacheKey)) {
                    clearAuthenticationInformation(cacheKey);
                }
            } else if (upgradeType == UpgradeType.REFRESH) {
                String cacheKey = getCacheKey(new Cookie[]{cookie}, token);
                if (hasCache(cacheKey)) {
                    clearAuthenticationInformation(cacheKey);
                }
                invalidateCache(token);
            }
        }
        return authenticationInformation;
    }

    @Override
    /** 获取access令牌 */
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        JsonObject jsonObject = new JsonObject();
        // 构建认证数据
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-username", username);
        jsonObject.put("x-oauth-password", password);
        String authTypeCode = (authType == null || authType == com.chua.starter.oauth.client.support.enums.AuthType.NONE)
                ? "WEB" : authType.getCode().toUpperCase();
        jsonObject.put("x-oauth-auth-type", authTypeCode);
        // 将 ext 中携带的浏览器/系统信息同步到请求体，方便服务端记录登录日志
        if (ext != null) {
            jsonObject.put("x-oauth-ext", ext);
            if (!ext.containsKey("ua")) {
                jakarta.servlet.http.HttpServletRequest request = RequestUtils.getRequest();
                if (request != null) {
                    String ua = request.getHeader("User-Agent");
                    if (StringUtils.isNotBlank(ua)) {
                        ext.put("ua", ua);
                    }
                }
            }
        } else {
            jakarta.servlet.http.HttpServletRequest request = RequestUtils.getRequest();
            Map<String, Object> newExt = new java.util.HashMap<>();
            if (request != null) {
                String ua = request.getHeader("User-Agent");
                if (StringUtils.isNotBlank(ua)) {
                    newExt.put("ua", ua);
                }
            }
            jsonObject.put("x-oauth-ext", newExt);
        }
        AuthenticationInformation information = createAuthenticationInformation(jsonObject, null, authClientProperties.getLoginPage());
        log.info("[Login] 状态={}, 用户={}, 类型={}",
                information.getInformation().getCode(), username, authTypeCode);
        log.debug("[Login] 信息={}", information.getInformation().getMessage());
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        loginAuthResult.setCode(information.getInformation().getCode());
        String errorMessage = StringUtils.isNotBlank(information.getErrorMessage())
                ? information.getErrorMessage()
                : information.getInformation().getMessage();
        loginAuthResult.setMessage(errorMessage);
        loginAuthResult.setUserResume(information.getReturnResult());
        String token = information.getToken();
        if (StringUtils.isNotBlank(token)) {
            loginAuthResult.setToken(token);
        }
        String refreshToken = information.getRefreshToken();
        if (StringUtils.isNotBlank(refreshToken)) {
            loginAuthResult.setRefreshToken(refreshToken);
        }
        return loginAuthResult;
    }

    @Override
    /** Logout */
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

    @Override
    /** 创建temporary令牌 */
    public LoginAuthResult createTemporaryToken(String sourceToken, Map<String, Object> ext) {
        JsonObject jsonObject = new JsonObject();
        // 构建请求数据
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-source-token", sourceToken);
        jsonObject.put("x-oauth-ext", ext);

        AuthenticationInformation information = createAuthenticationInformation(
                jsonObject, null, authClientProperties.getTemporaryTokenPage());

        LoginAuthResult result = new LoginAuthResult();
        result.setCode(information.getInformation().getCode());
        result.setMessage(information.getInformation().getMessage());
        if (information.getInformation() == Information.OK) {
            result.setToken(information.getToken());
            result.setUserResume(information.getReturnResult());
        }
        return result;
    }

    @Override
    /** 获取online用户 */
    public OnlineUserResult getOnlineUsers(OnlineUserQuery query) {
        JsonObject jsonObject = new JsonObject();
        // 构建请求数据
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
                // 从返回结果中解析在线用户信息
                Object data = information.getReturnResult();
                if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) data;
                    return parseOnlineUserResult(resultMap);
                }
            }
        } catch (Exception e) {
            log.warn("获取在线用户列表失败", e);
        }
        return OnlineUserResult.empty();
    }

    @Override
    /** 验证Fingerprint */
    public FingerprintVerifyResult verifyFingerprint(String token, String fingerprint) {
        JsonObject jsonObject = new JsonObject();
        // 构建请求数据
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-fingerprint", fingerprint);
        jsonObject.put("x-oauth-verify-fingerprint", true);

        try {
            AuthenticationInformation information = createAuthenticationInformation(
                    jsonObject, null, authClientProperties.getOauthUrl());

            int code = information.getInformation().getCode();
            if (code == 200) {
                // 验证通过，返回存储的指纹
                String storedFingerprint = null;
                if (information.getReturnResult() != null) {
                    storedFingerprint = information.getReturnResult().getFingerprint();
                }
                return FingerprintVerifyResult.success(storedFingerprint);
            } else if (code == 40301) {
                return FingerprintVerifyResult.mismatch();
            } else if (code == 40302) {
                return FingerprintVerifyResult.missing();
            } else {
                return FingerprintVerifyResult.serverError(information.getInformation().getMessage());
            }
        } catch (Exception e) {
            log.warn("指纹验证失败: token={}", token, e);
            return FingerprintVerifyResult.serverError("指纹验证失败: " + e.getMessage());
        }
    }

    @Override
    /** 获取online状态 */
    public OnlineStatus getOnlineStatus(String uid) {
        JsonObject jsonObject = new JsonObject();
        // 构建请求数据
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-uid", uid);

        try {
            AuthenticationInformation information = createAuthenticationInformation(
                    jsonObject, null, "/online/status");
            
            if (information.getInformation().getCode() == 200) {
                Object data = information.getReturnResult();
                if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) data;
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

    /**
    * 解析在线用户结果
    * @param resultMap 结果映射
    * @return 解析online用户结果的结果
    */
    private OnlineUserResult parseOnlineUserResult(Map<String, Object> resultMap) {
        OnlineUserResult result = new OnlineUserResult();
        result.setTotal(getIntValue(resultMap, "total", 0));
        result.setPage(getIntValue(resultMap, "page", 1));
        result.setSize(getIntValue(resultMap, "size", 20));
        
        Object usersObj = resultMap.get("users");
        if (usersObj instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> usersList = (java.util.List<Map<String, Object>>) usersObj;
            java.util.List<OnlineUserInfo> users = new java.util.ArrayList<>();
            for (Map<String, Object> userMap : usersList) {
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

    /**
    * 获取int值
    *
    * @param map 映射
    * @param key 键
    * @param defaultValue 默认值
    * @return 获取int值的结果
    */
    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
    * 获取字符串值
    *
    * @param map 映射
    * @param key 键
    * @param defaultValue 默认值
    * @return 获取字符串值的结果
    */
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
    * 创建认证信息
    * 向远程认证服务器发送HTTP请求以验证用户凭据或升级令牌
    *
    * @param jsonObject  包含认证所需参数的JSON对象
    * @param upgradeType 升级类型，用于指定升级策略（如基于版本号或时间戳）
    * @return AuthenticationInformation 返回认证结果，包含认证状态和用户信息
    */
    /**
    * 大小写不敏感读取响应头
    * @param headers 头部
    * @param name 名称
    * @return 头部ignore大小写的结果
    */
    private String headerIgnoreCase(com.chua.common.support.network.http.HttpHeader headers, String name) {
        if (headers == null) {
            return null;
        }
        var value = headers.get(name);
        if (value != null) {
            return value;
        }
        return headers.toMap().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    /**
    * 创建认证信息
    *
    * @param jsonObject json对象
    * @param upgradeType upgrade类型
    * @param path 路径
    * @return 创建认证信息的结果
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
    * @param jsonObject json对象
    * @param upgradeType upgrade类型
    * @param path 路径
    * @return 执行请求的结果
    */
    private AuthenticationInformation doRequest(JsonObject jsonObject, UpgradeType upgradeType, String path) {
        // 创建或获取追踪上下文
        TraceContext traceContext = TraceContext.current();
        boolean isNewTrace = false;
        if (traceContext == null) {
            traceContext = TraceContext.create();
            var request = RequestUtils.getRequest();
            traceContext.clientInfo(
                    OauthClientRuntimeContext.getApplicationName(),
                    request != null ? RequestUtils.getIpAddress(request) : "unknown"
            );
            isNewTrace = true;
        }

        try {
            // Span: 构建请求
            TraceSpan buildSpan = traceContext.startSpan("client_build_request");
            
            // 获取认证服务器地址
            String selectedUrl = selectUrl();
            if (null == selectedUrl) {
                traceContext.endSpan();
                return AuthenticationInformation.authServerError();
            }

            // 生成随机密钥和请求头参数
            String key = IdUtils.simpleUuid();
            jsonObject.put("x-ext-timestamp", System.currentTimeMillis());

            String timestamp = System.nanoTime() + "";
            String key1 = IdUtils.simpleUuid();
            
            traceContext.endSpan(); // 结束构建请求 Span

            com.chua.common.support.network.client.ClientResponse httpResponse = null;
            // Span: HTTP调用
            TraceSpan httpSpan = traceContext.startSpan("client_http_call");
            try {
                // 使用 common-starter HttpClient(替代 unirest)
                String url = StringUtils.endWithAppend(StringUtils.startWithAppend(selectedUrl, "http://"), "/") +
                        StringUtils.startWithMove(path, "/");

                com.chua.common.support.network.client.HttpClientBuilder client =
                        com.chua.common.support.network.client.HttpClientFactory.of(url)
                                .json()
                                .header("x-oauth-timestamp", timestamp)
                                .header("x-oauth-uuid", key1)
                                .header("x-oauth-encode", String.valueOf(isEncode()))
                                .header("x-oauth-serial", createData(key, key1))
                                .header("x-oauth-sign", SignUtils.generateSignFromMap(jsonObject));

                // 添加 AK/SK 请求头（如果启用）
                String accessKey = getAccessKey();
                if (StringUtils.isNotBlank(accessKey)) {
                    client = client.header("x-oauth-ak", accessKey);
                }

 // 添加认证类型请求头（x-OAuth-类型）
                HttpServletRequest currentRequest = RequestUtils.getRequest();
                if (currentRequest != null) {
                    String authType = currentRequest.getHeader("x-oauth-type");
                    if (StringUtils.isNotBlank(authType)) {
                        client = client.header("x-oauth-type", authType);
                    }
                }

                // 透传 Authorization: Bearer 头到服务端，并设置 x-oauth-type=bearer
                if (currentRequest != null) {
                    String authorization = currentRequest.getHeader("Authorization");
                    if (StringUtils.isNotBlank(authorization) && authorization.startsWith("Bearer ")) {
                        client = client.header("Authorization", authorization);
 // 设置认证类型为 bearer，服务端根据此通过 SPI 选择 bearer授权处理器
                        client = client.header("x-oauth-type", "bearer");
                        log.debug("[HttpProtocol] 透传 Authorization: Bearer 头到服务端, x-oauth-type=bearer");
 // 同时在请求体中放入 令牌，兼容加密协议解析
                        jsonObject.put("x-oauth-token", authorization.substring(7).trim());
                    }
                }

                // 传递追踪头用于链路追踪
                client = client
                        .header("x-trace-id", traceContext.getTraceId())
                        .header("x-trace-timestamp", String.valueOf(traceContext.getStartTimestamp()))
                        .header("x-trace-app-name", traceContext.getClientAppName() != null ? traceContext.getClientAppName() : "")
                        .header("x-trace-client-ip", traceContext.getClientIp() != null ? traceContext.getClientIp() : "");

                // 如果有升级类型，则添加相应请求头
                if (null != upgradeType) {
                    client = client.header("x-oauth-upgrade-type", upgradeType.name());
                }

                // 发送请求并获取响应
                httpResponse = client.body(JsonObject.create()
                        .fluentPut("data", createData(jsonObject, key)).toJSONString())
                        .post();

            } catch (Exception e) {
                traceContext.endSpan(); // 结束HTTP调用 Span
                // 完整记录请求异常信息
                log.error("[OAuth客户端]认证请求异常 - traceId: {}, URL: {}, 路径: {}, 异常类型: {}, 异常信息: {}",
                        traceContext.getTraceId(), selectedUrl, path, e.getClass().getSimpleName(), e.getMessage());
                if (log.isDebugEnabled()) {
                    log.debug("[OAuth客户端]认证请求异常堆栈", e);
                }
                return AuthenticationInformation.authServerError();
            }
            traceContext.endSpan(); // 结束HTTP调用 Span

            // Span: 解析响应
            TraceSpan parseSpan = traceContext.startSpan("client_parse_response");
            try {
                // 检查响应是否为空
                if (null == httpResponse) {
                    log.warn("[OAuth客户端]认证服务器响应为空 - traceId: {}, URL: {}, 路径: {}", 
                            traceContext.getTraceId(), selectedUrl, path);
                    return AuthenticationInformation.authServerError();
                }

                // 获取响应状态码和内容
                int status = httpResponse.getStatusCode();
                String body = httpResponse.getBodyString();

                // 判断响应是否为服务器错误或空数据
                if (status >= 400 && status < 600) {
                    log.warn("[OAuth客户端]认证服务器返回错误状态 - traceId: {}, URL: {}, 路径: {}, 状态码: {}, 响应: {}", 
                            traceContext.getTraceId(), selectedUrl, path, status, body);
                    return AuthenticationInformation.authServerNotFound();
                }

                if (Strings.isNullOrEmpty(body)) {
                    log.warn("[OAuth客户端]认证服务器返回空响应 - traceId: {}, URL: {}, 路径: {}, 状态码: {}", 
                            traceContext.getTraceId(), selectedUrl, path, status);
                    return AuthenticationInformation.authServerNotFound();
                }

                // 成功响应时解析返回结果
                if (status == 200) {
                    log.debug("[OAuth客户端]认证请求成功 - traceId: {}, URL: {}, 路径: {}, 总耗时: {}ms", 
                            traceContext.getTraceId(), selectedUrl, path, traceContext.getTotalCostMs());
                    log.debug("[OAuth客户端]追踪详情: {}", traceContext.getSpansSummary());
                    return createAuthenticationInformation(Json.fromJson(body, ReturnResult.class),
                            headerIgnoreCase(httpResponse.getHeaders(), "x-oauth-response-serial"),
                            path);
                }

                // 其他状态码
                log.warn("[OAuth客户端]认证服务器返回未知状态 - traceId: {}, URL: {}, 路径: {}, 状态码: {}", 
                        traceContext.getTraceId(), selectedUrl, path, status);
                return AuthenticationInformation.authServerError();
            } finally {
                traceContext.endSpan(); // 结束解析响应 Span
            }
        } finally {
            // 如果是本方法创建的追踪上下文，则清理
            if (isNewTrace) {
                TraceContext.clear();
            }
        }
    }

    @Override
    public Map<String, Object> getFingerprintUser(String fingerprint) {
        if (StringUtils.isBlank(fingerprint)) {
            return null;
        }
        try {
            String selectedUrl = selectUrl();
            if (selectedUrl == null) {
                return null;
            }
            String url = StringUtils.endWithAppend(selectedUrl, "/")
                    + "oauth/admin/fingerprint/by-fingerprint?fingerprint="
                    + com.google.common.net.UrlEscapers.urlFormParameterEscaper().escape(fingerprint);
            ClientResponse response = HttpClientFactory.of(url).header("x-oauth-token", "").get();
            if (response.getStatusCode() == 200) {
                String body = response.getBodyString();
                if (StringUtils.isNotBlank(body)) {
                    ReturnResult<Map<String, Object>> result = Json.fromJson(body, ReturnResult.class);
                    if (result != null && result.isOk() && result.getData() != null) {
                        Map<String, Object> data = result.getData();
                        if (Boolean.TRUE.equals(data.get("found"))) {
                            data.remove("found");
                            return data;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[HttpProtocol] 指纹查询失败: fingerprint={}", fingerprint, e);
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> getFingerprintUsersByIp(String ip) {
        if (StringUtils.isBlank(ip)) {
            return Collections.emptyList();
        }
        try {
            String selectedUrl = selectUrl();
            if (selectedUrl == null) {
                return Collections.emptyList();
            }
            String url = StringUtils.endWithAppend(selectedUrl, "/")
                    + "oauth/admin/fingerprint/by-ip?ip="
                    + com.google.common.net.UrlEscapers.urlFormParameterEscaper().escape(ip);
            ClientResponse response = HttpClientFactory.of(url).header("x-oauth-token", "").get();
            if (response.getStatusCode() == 200) {
                String body = response.getBodyString();
                if (StringUtils.isNotBlank(body)) {
                    ReturnResult<Map<String, Object>> result = Json.fromJson(body, ReturnResult.class);
                    if (result != null && result.isOk() && result.getData() != null) {
                        Object users = result.getData().get("users");
                        if (users instanceof List) {
                            return (List<Map<String, Object>>) users;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[HttpProtocol] IP指纹查询失败: ip={}", ip, e);
        }
        return Collections.emptyList();
    }

}
