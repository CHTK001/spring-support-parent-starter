package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.tcp.JdkTcpClient;
import com.chua.common.support.network.tcp.TcpClient;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.RequestUtils;
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
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.exception.AuthException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * TCP 协议实现
 * <p>
 * 使用 JDK NIO Socket通道 发送长度帧（4 字节大端长度头 + 消息体）与 o认证 认证服务端通信。
 * 帧格式为 {@code topic:body}，topic 对应服务端路由路径（如 login、OAuth、upgrade、logout、temporary-令牌），
 * 主体 为加密后的 JSON 请求体；服务端响应同样走长度帧，客户端解帧后解析 JSON。
 * </p>
 *
 * @since 2026-09-05
 * @author CH
 */
@Spi({"tcp"})
@Slf4j
public class TcpProtocol extends AbstractProtocol {

    /**
     * TCP 短连接客户端（每次请求新建连接，用完关闭，由 jdktcp客户端 内部虚拟线程池管理）
    */
    private final TcpClient tcpClient = new JdkTcpClient();

    /**
     * 弹性处理器（熔断+重试）
    */
    private final OAuthClientResilience resilience;

    /**
     * 构造函数
     *
     * @param authClientProperties 认证客户端配置
     */
    public TcpProtocol(AuthClientProperties authClientProperties) {
        /**
         * 父。
         * @param authClientProperties 认证客户端属性
         */
        super(authClientProperties);
        this.resilience = new OAuthClientResilience(authClientProperties);
    }

    @Override
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
        if (StringUtils.isBlank(subProtocol)) {
            jakarta.servlet.http.HttpServletRequest request = RequestUtils.getRequest();
            if (request != null) {
                subProtocol = request.getHeader("x-oauth-type");
                if (StringUtils.isNotBlank(subProtocol)) {
                    log.debug("[TcpProtocol]从Header读取认证类型: {}", subProtocol);
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
        jsonObject.put("x-oauth-param-app-name", OauthClientRuntimeContext.getApplicationName());
        return sendRequest("/oauth", jsonObject, null);
    }

    @Override
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
        JsonObject jsonObject = createAppKeyAuthenticationData(appKeySecret);
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        var request = RequestUtils.getRequest();
        jsonObject.put("x-oauth-param-address", request != null ? RequestUtils.getIpAddress(request) : "unknown");
        jsonObject.put("x-oauth-param-app-name", OauthClientRuntimeContext.getApplicationName());
        return sendRequest("/oauth", jsonObject, null);
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

        AuthenticationInformation authenticationInformation = sendRequest("/upgrade", jsonObject, upgradeType);
        if (authenticationInformation.getInformation() == Information.OK) {
            if (upgradeType == UpgradeType.VERSION) {
                com.chua.starter.oauth.client.support.execute.AuthSessionUtils.removeUserInfo();
                String cacheKey = getCacheKey(new Cookie[]{cookie}, token);
                if (hasCache(cacheKey)) {
                    /**
                     * clear认证信息。
                     * @param cacheKey 缓存Key
                     */
                    clearAuthenticationInformation(cacheKey);
                }
            } else if (upgradeType == UpgradeType.REFRESH) {
                String cacheKey = getCacheKey(new Cookie[]{cookie}, token);
                if (hasCache(cacheKey)) {
                    /**
                     * clear认证信息。
                     * @param cacheKey 缓存Key
                     */
                    clearAuthenticationInformation(cacheKey);
                }
                /**
                 * invalidate缓存。
                 * @param token 令牌
                 */
                invalidateCache(token);
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
        String authTypeCode = (authType == null || authType == com.chua.starter.oauth.client.support.enums.AuthType.NONE)
                ? "WEB" : authType.getCode().toUpperCase();
        jsonObject.put("x-oauth-auth-type", authTypeCode);
        jsonObject.put("x-oauth-ext", ext);

        AuthenticationInformation information = sendRequest("/login", jsonObject, null);
        log.info("[Login] 状态={}, 用户={}, 类型={}",
                information.getInformation().getCode(), username, authTypeCode);
        log.debug("[Login] 信息={}", information.getInformation().getMessage());

        LoginAuthResult loginAuthResult = new LoginAuthResult();
        loginAuthResult.setCode(information.getInformation().getCode());
        String msg = information.getErrorMessage() != null
                ? information.getErrorMessage() : information.getInformation().getMessage();
        loginAuthResult.setMessage(msg);
        loginAuthResult.setUserResume(information.getReturnResult());
        if (information.getToken() != null) {
            loginAuthResult.setToken(information.getToken());
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

        AuthenticationInformation information = sendRequest("/logout", jsonObject, null);
        if (information.getInformation() == Information.OK) {
            return LoginAuthResult.OK;
        }
        /**
         * 认证异常。
         * @param information.getInformation( 信息.获取信息(
         * @return 认证异常的结果
         */
        throw new AuthException(information.getInformation().getMessage());
    }

    @Override
    public LoginAuthResult createTemporaryToken(String sourceToken, Map<String, Object> ext) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-source-token", sourceToken);
        jsonObject.put("x-oauth-ext", ext);

        AuthenticationInformation information = sendRequest("/temporary-token", jsonObject, null);

        LoginAuthResult result = new LoginAuthResult();
        result.setCode(information.getInformation().getCode());
        String msg = information.getErrorMessage() != null
                ? information.getErrorMessage() : information.getInformation().getMessage();
        result.setMessage(msg);
        if (information.getInformation() == Information.OK) {
            result.setToken(information.getToken());
            result.setUserResume(information.getReturnResult());
        }
        return result;
    }

    @Override
    public FingerprintVerifyResult verifyFingerprint(String token, String fingerprint) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-fingerprint", fingerprint);
        jsonObject.put("x-oauth-verify-fingerprint", true);

        try {
            AuthenticationInformation information = sendRequest("/oauth", jsonObject, null);

            int code = information.getInformation().getCode();
            if (code == 200) {
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
            AuthenticationInformation information = sendRequest("/online/list", jsonObject, null);
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
            AuthenticationInformation information = sendRequest("/online/status", jsonObject, null);
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

    // ==================== 内部方法 ====================

    /**
     * 发送 TCP 请求并解析响应
     * <p>帧格式：{@code topic:body}，body 为加密 JSON；响应为长度帧包裹的 JSON。</p>
     * @param route route
     * @param jsonObject json对象
     * @param upgradeType upgrade类型
     * @return 发送请求的结果
     */
    protected AuthenticationInformation sendRequest(String route, JsonObject jsonObject, UpgradeType upgradeType) {
        return resilience.executeWithResilience(
                () -> doRequest(route, jsonObject, upgradeType),
                () -> OAuthClientResilience.createFallbackResponse(
                        authClientProperties.getCircuitBreaker().getFallbackMessage())
        );
    }

    /**
     * 执行 TCP 短连接请求-响应
     * @param route route
     * @param jsonObject json对象
     * @param upgradeType upgrade类型
     * @return 执行请求的结果
     */
    private AuthenticationInformation doRequest(String route, JsonObject jsonObject, UpgradeType upgradeType) {
        String selectedUrl = selectUrl();
        if (selectedUrl == null) {
            return AuthenticationInformation.authServerError();
        }

        String host = extractHost(selectedUrl);
        int port = extractPort(selectedUrl, 19180);

        String key = IdUtils.simpleUuid();
        jsonObject.put("x-ext-timestamp", System.currentTimeMillis());

        String timestamp = System.nanoTime() + "";
        String key1 = IdUtils.simpleUuid();

        jakarta.servlet.http.HttpServletRequest currentRequest = RequestUtils.getRequest();
        String authType = null;
        if (currentRequest != null) {
            authType = currentRequest.getHeader("x-oauth-type");
        }

        // 构建请求 JSON（不含 topic 前缀，topic 由帧格式承载）
        JsonObject requestJson = new JsonObject();
        requestJson.putAll(jsonObject);
        if (StringUtils.isNotBlank(authType)) {
            requestJson.put("x-oauth-type", authType);
        }
        if (upgradeType != null) {
            requestJson.put("x-oauth-upgrade-type", upgradeType.name());
        }

        // 加密请求体
        String payload = requestJson.toJSONString();
        String encryptedPayload = createData(payload, key);

 // 构建 TCP 帧：topic:主体（与服务端 OAuth协议服务端管理器 解析逻辑对称）
        String frame = route + ":" + encryptedPayload;
        byte[] frameBytes = frame.getBytes(StandardCharsets.UTF_8);

        try {
            byte[] responseBytes = tcpClient.call(host, port, frameBytes);
            if (responseBytes == null || responseBytes.length == 0) {
                return AuthenticationInformation.authServerNotFound();
            }

            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            if (Strings.isNullOrEmpty(responseBody)) {
                return AuthenticationInformation.authServerNotFound();
            }

            ReturnResult<?> returnResult = Json.fromJson(responseBody, ReturnResult.class);
            if (returnResult == null) {
                return AuthenticationInformation.authServerError();
            }

            return createAuthenticationInformation(returnResult, null, route);
        } catch (Exception e) {
            log.error("[TcpProtocol]OAuth 请求异常: route={}, host:{}, 异常: {}", route, host + ":" + port, e.getMessage(), e);
            return AuthenticationInformation.authServerError();
        }
    }

    /**
     * 从 URL 提取主机名
     * @param url url
     * @return extract主机的结果
     */
    private String extractHost(String url) {
        if (url.startsWith("tcp://")) {
            url = url.substring(6);
        } else if (url.startsWith("http://")) {
            url = url.substring(7);
        } else if (url.startsWith("https://")) {
            url = url.substring(8);
        }
        int colonIndex = url.indexOf(':');
        if (colonIndex > 0) {
            return url.substring(0, colonIndex);
        }
        int slashIndex = url.indexOf('/');
        if (slashIndex > 0) {
            return url.substring(0, slashIndex);
        }
        return url;
    }

    /**
     * 从 URL 提取端口
     * @param url url
     * @param defaultPort 默认端口
     * @return extract端口的结果
     */
    private int extractPort(String url, int defaultPort) {
        if (url.startsWith("tcp://")) {
            url = url.substring(6);
        } else if (url.startsWith("http://")) {
            url = url.substring(7);
        } else if (url.startsWith("https://")) {
            url = url.substring(8);
        }
        int colonIndex = url.indexOf(':');
        if (colonIndex > 0) {
            int slashIndex = url.indexOf('/', colonIndex);
            String portStr = slashIndex > 0 ? url.substring(colonIndex + 1, slashIndex) : url.substring(colonIndex + 1);
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                log.warn("无法解析端口号: {}, 使用默认端口 {}", portStr, defaultPort);
            }
        }
        return defaultPort;
    }

    /**
     * 解析在线用户结果
     *
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

    /**
     * 获取int值。
     * @param map 映射
     * @param key Key
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
     * 获取字符串值。
     * @param map 映射
     * @param key Key
     * @param defaultValue 默认值
     * @return 获取字符串值的结果
     */
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    @Override
    public Map<String, Object> getFingerprintUser(String fingerprint) {
        if (StringUtils.isBlank(fingerprint)) {
            return null;
        }
        try {
            JsonObject requestJson = new JsonObject();
            requestJson.put("fingerprint", fingerprint);
            String payload = requestJson.toJSONString();
            String frame = "/fingerprint/by-fingerprint:" + payload;
            byte[] frameBytes = frame.getBytes(StandardCharsets.UTF_8);
            String selectedUrl = selectUrl();
            if (selectedUrl == null) {
                return null;
            }
            String host = extractHost(selectedUrl);
            int port = extractPort(selectedUrl, 19180);
            byte[] responseBytes = tcpClient.call(host, port, frameBytes);
            if (responseBytes == null || responseBytes.length == 0) {
                return null;
            }
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            ReturnResult<?> returnResult = Json.fromJson(responseBody, ReturnResult.class);
            if (returnResult != null && returnResult.isOk() && returnResult.getData() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) returnResult.getData();
                if (Boolean.TRUE.equals(data.get("found"))) {
                    data.remove("found");
                    return data;
                }
            }
        } catch (Exception e) {
            log.warn("[TcpProtocol] 指纹查询失败: fingerprint={}", fingerprint, e);
        }
        return null;
    }

    @Override
    public java.util.List<Map<String, Object>> getFingerprintUsersByIp(String ip) {
        if (StringUtils.isBlank(ip)) {
            return java.util.Collections.emptyList();
        }
        try {
            JsonObject requestJson = new JsonObject();
            requestJson.put("ip", ip);
            String payload = requestJson.toJSONString();
            String frame = "/fingerprint/by-ip:" + payload;
            byte[] frameBytes = frame.getBytes(StandardCharsets.UTF_8);
            String selectedUrl = selectUrl();
            if (selectedUrl == null) {
                return java.util.Collections.emptyList();
            }
            String host = extractHost(selectedUrl);
            int port = extractPort(selectedUrl, 19180);
            byte[] responseBytes = tcpClient.call(host, port, frameBytes);
            if (responseBytes == null || responseBytes.length == 0) {
                return java.util.Collections.emptyList();
            }
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            ReturnResult<?> returnResult = Json.fromJson(responseBody, ReturnResult.class);
            if (returnResult != null && returnResult.isOk() && returnResult.getData() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) returnResult.getData();
                Object users = data.get("users");
                if (users instanceof java.util.List) {
                    return (java.util.List<Map<String, Object>>) users;
                }
            }
        } catch (Exception e) {
            log.warn("[TcpProtocol] IP指纹查询失败: ip={}", ip, e);
        }
        return java.util.Collections.emptyList();
    }
}
