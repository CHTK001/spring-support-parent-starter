package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
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
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.google.common.base.Strings;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.exception.AuthException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * RSocket 协议实现
 * 使用 RSocket 进行 OAuth 认证，支持响应式通信
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Extension("rsocket")
@Slf4j
public class RSocketProtocol extends AbstractProtocol {

    private volatile RSocket rSocket;

    /**
     * 构造函数
     *
     * @param authClientProperties 认证客户端配置
     */
    public RSocketProtocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
    }

    /**
     * 初始化 RSocket 连接
     */
    private void initializeRSocket() {
        if (rSocket == null || rSocket.isDisposed()) {
            synchronized (this) {
                if (rSocket == null || rSocket.isDisposed()) {
                    String selectedUrl = selectUrl();
                    if (selectedUrl == null) {
                        throw new IllegalStateException("OAuth 服务器地址不存在");
                    }

                    String host = extractHost(selectedUrl);
                    int port = extractPort(selectedUrl, 7000);

                    this.rSocket = RSocketConnector.create()
                            .keepAlive(Duration.ofSeconds(30), Duration.ofSeconds(90))
                            .connect(TcpClientTransport.create(host, port))
                            .block();

                    log.info("RSocket OAuth 客户端已连接: {}:{}", host, port);
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
                    log.debug("[RSocketProtocol]从Header读取认证类型: {}", subProtocol);
                }
            }
        }
        
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-cookie", null == cookie ? null : cookie.getValue());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-param-address", RequestUtils.getIpAddress());
        jsonObject.put("x-oauth-param-app-name",
                SpringBeanUtils.getEnvironment().resolvePlaceholders("${spring.application.name:}"));
        return sendRSocketRequest("oauth", jsonObject, null);
    }

    @Override
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-user-code", Json.toJSONBytes(appKeySecret));
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-param-address", RequestUtils.getIpAddress());
        jsonObject.put("x-oauth-param-app-name",
                SpringBeanUtils.getEnvironment().resolvePlaceholders("${spring.application.name:}"));
        return sendRSocketRequest("oauth", jsonObject, null);
    }

    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType,
            String refreshToken) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-cookie", null == cookie ? null : cookie.getValue());
        jsonObject.put("x-oauth-token", token);
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-refresh-token", refreshToken);
        jsonObject.put("x-oauth-upgrade-type", upgradeType.name().toUpperCase());

        AuthenticationInformation authenticationInformation = sendRSocketRequest("upgrade", jsonObject, upgradeType);
        if (authenticationInformation.getInformation() == Information.OK) {
            if (upgradeType == UpgradeType.VERSION) {
                RequestUtils.removeUserInfo();
                String cacheKey = getCacheKey(new Cookie[] { cookie }, token);
                if (hasCache(cacheKey)) {
                    clearAuthenticationInformation(cacheKey);
                }
            }
        }
        return authenticationInformation;
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType,
            Map<String, Object> ext) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-username", username);
        jsonObject.put("x-oauth-password", password);
        jsonObject.put("x-oauth-auth-type", authType);
        jsonObject.put("x-oauth-ext", ext);

        AuthenticationInformation information = sendRSocketRequest("login", jsonObject, null);

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
        jsonObject.put("x-oauth-access-key", authClientProperties.getKey().getAccessKey());
        jsonObject.put("x-oauth-secret-key", authClientProperties.getKey().getSecretKey());
        jsonObject.put("x-oauth-uid", uid);
        jsonObject.put("x-oauth-logout-type", logoutType);

        AuthenticationInformation information = sendRSocketRequest("logout", jsonObject, null);
        if (information.getInformation() == Information.OK) {
            return LoginAuthResult.OK;
        }
        throw new AuthException(information.getInformation().getMessage());
    }

    /**
     * 发送 RSocket 请求
     *
     * @param route       路由
     * @param jsonObject  请求数据
     * @param upgradeType 升级类型
     * @return 认证信息
     */
    private AuthenticationInformation sendRSocketRequest(String route, JsonObject jsonObject, UpgradeType upgradeType) {
        initializeRSocket();

        try {
            String key = IdUtils.simpleUuid();
            jsonObject.put("x-ext-timestamp", System.currentTimeMillis());

            String timestamp = System.nanoTime() + "";
            String key1 = IdUtils.simpleUuid();

            // 添加认证类型到元数据（x-oauth-type）
            jakarta.servlet.http.HttpServletRequest currentRequest = RequestUtils.getRequest();
            String authType = null;
            if (currentRequest != null) {
                authType = currentRequest.getHeader("x-oauth-type");
            }
            
            // 构建元数据
            JsonObject metadata = new JsonObject();
            metadata.put("route", route);
            metadata.put("x-oauth-timestamp", timestamp);
            metadata.put("x-oauth-uuid", key1);
            metadata.put("x-oauth-encode", String.valueOf(isEncode()));
            metadata.put("x-oauth-serial", createData(key, key1));
            metadata.put("x-oauth-sign", SignUtils.generateSignFromMap(jsonObject));
            if (StringUtils.isNotBlank(authType)) {
                metadata.put("x-oauth-type", authType);
            }
            if (upgradeType != null) {
                metadata.put("x-oauth-upgrade-type", upgradeType.name());
            }

            // 构建请求体
            String requestBody = JsonObject.create()
                    .fluentPut("data", createData(jsonObject, key))
                    .toJSONString();

            // 发送 RSocket 请求
            Payload payload = DefaultPayload.create(requestBody, metadata.toJSONString());
            Payload responsePayload = rSocket.requestResponse(payload)
                    .block(Duration.ofSeconds(10));

            if (responsePayload == null) {
                return AuthenticationInformation.authServerError();
            }

            String responseBody = responsePayload.getDataUtf8();
            String responseMetadata = responsePayload.getMetadataUtf8();

            responsePayload.release();

            if (Strings.isNullOrEmpty(responseBody)) {
                return AuthenticationInformation.authServerNotFound();
            }

            // 解析响应
            JsonObject responseMeta = Json.fromJson(responseMetadata, JsonObject.class);
            String responseSerial = responseMeta != null ? responseMeta.getString("x-oauth-response-serial") : null;

            return createAuthenticationInformation(
                    Json.fromJson(responseBody, ReturnResult.class),
                    responseSerial,
                    route);

        } catch (Exception e) {
            log.error("RSocket OAuth 请求异常: {}", route, e);
            return AuthenticationInformation.authServerError();
        }
    }

    /**
     * 提取主机名
     */
    private String extractHost(String url) {
        if (url.startsWith("rsocket://")) {
            url = url.substring(10);
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
     * 提取端口
     */
    private int extractPort(String url, int defaultPort) {
        if (url.startsWith("rsocket://")) {
            url = url.substring(10);
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
                log.warn("无法解析端口号: {}, 使用默认端口{}", portStr, defaultPort);
            }
        }

        return defaultPort;
    }

    /**
     * 关闭 RSocket 连接
     */
    public void shutdown() {
        if (rSocket != null && !rSocket.isDisposed()) {
            rSocket.dispose();
            log.info("RSocket OAuth 客户端已关闭");
        }
    }
}
