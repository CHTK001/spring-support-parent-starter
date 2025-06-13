package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.robin.Node;
import com.chua.common.support.lang.robin.Robin;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.cache.Cacheable;
import com.chua.common.support.task.cache.GuavaCacheable;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.Md5Utils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.utils.ResponseUtils;
import com.chua.starter.oauth.client.support.advice.def.DefSecret;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.google.common.base.Strings;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

import static com.chua.common.support.lang.code.ReturnCode.RESOURCE_OAUTH_ERROR;
import static com.chua.common.support.lang.code.ReturnCode.RESULT_ACCESS_UNAUTHORIZED;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.OAUTH_UPGRADE_KEY;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.OAUTH_UPGRADE_KEY_TOKEN;
import static com.chua.starter.oauth.client.support.infomation.Information.*;

/**
 * WebSocket协议实现
 * <p>
 * 基于WebSocket实现的认证协议，提供实时、持久的认证连接。
 * 相比HTTP协议，WebSocket协议具有以下优势：
 * </p>
 * <ul>
 *   <li>保持长连接，减少连接建立开销</li>
 *   <li>支持服务器推送，可实现实时会话状态更新</li>
 *   <li>减少请求头开销，提高传输效率</li>
 *   <li>适合需要实时认证状态更新的场景</li>
 * </ul>
 *
 * @author CH
 */
@Extension("websocket")
@Slf4j
public class WebSocketProtocol extends AbstractProtocol implements InitializingBean {

    /**
     * WebSocket会话连接超时时间(毫秒)
     */
    private static final long CONNECTION_TIMEOUT = 5000;
    /**
     * WebSocket响应超时时间(毫秒)
     */
    private static final long RESPONSE_TIMEOUT = 10000;
    /**
     * WebSocket会话心跳间隔(毫秒)
     */
    private static final long HEARTBEAT_INTERVAL = 30000;
    /**
     * 认证结果缓存
     */
    protected static Cacheable CACHEABLE;
    /**
     * WebSocket会话管理器
     */
    private final Map<String, WebSocketClient> sessionMap = new ConcurrentHashMap<>();
    /**
     * 认证客户端配置
     */
    @AutoInject
    private AuthClientProperties authClientProperties;
    /**
     * 加密算法
     */
    private String encryption;

    /**
     * 验证用户身份并返回认证信息
     *
     * @param cookie 用于认证的Cookie数组
     * @param token  用于认证的令牌字符串
     * @return 认证信息对象
     */
    @Override
    public AuthenticationInformation approve(Cookie[] cookie, String token) {
        checkCache();
        String key = DigestUtils.md5Hex(UUID.randomUUID().toString());
        Map<String, Object> jsonObject = new HashMap<>(2);
        Cookie[] cookies = Optional.ofNullable(cookie).orElse(new Cookie[0]);
        String cacheKey = getCacheKey(cookies, token);

        // 检查缓存中是否存在有效的认证信息
        if (null != cacheKey) {
            check();
            Object value = CACHEABLE.get(cacheKey);
            if (null != value) {
                AuthenticationInformation authenticationInformation = (AuthenticationInformation) value;
                if (null != authenticationInformation && authenticationInformation.getInformation().getCode() == 200) {
                    UserResume userResume = authenticationInformation.getReturnResult();
                    RequestUtils.setUsername(userResume.getUsername());
                    RequestUtils.setUserInfo(userResume);
                    RequestUtils.setUserId(userResume.getUserId());
                    RequestUtils.setTenantId(userResume.getTenantId());
                    return authenticationInformation;
                } else {
                    CACHEABLE.remove(cacheKey);
                }
            }
        }

        // 构建认证请求数据
        jsonObject.put("x-oauth-cookie", cookies);
        jsonObject.put("x-oauth-token", token);
        String accessKey = authClientProperties.getAccessKey();
        String secretKey = authClientProperties.getSecretKey();
        String serviceKey = authClientProperties.getServiceKey();
        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            accessKey = DefSecret.ACCESS_KEY;
            secretKey = DefSecret.SECRET_KEY;
        }

        // 加密认证数据
        String asString = Json.toJson(jsonObject);
        String request = Codec.build(encryption, Md5Utils.getInstance()
                .getMd5String(accessKey + DigestUtils.md5Hex(secretKey + key))).encodeHex(asString);
        Map<String, Object> item2 = new HashMap<>(4);
        item2.put(AuthConstant.ACCESS_KEY, accessKey);
        item2.put(AuthConstant.SECRET_KEY, secretKey);
        item2.put(AuthConstant.OAUTH_VALUE, request);
        item2.put(AuthConstant.OAUTH_KEY, key);
        item2.put("type", "approve");
        request = Codec.build(encryption, serviceKey).encodeHex(Json.toJson(item2));

        // 选择认证服务器节点
        String serverUrl = selectServerUrl();
        if (serverUrl == null) {
            log.warn("认证服务器不可用");
            return inCache(cacheKey, AuthenticationInformation.authServerError());
        }

        try {
            // 发送WebSocket认证请求并等待响应
            String response = sendWebSocketRequest(serverUrl, request);
            if (response == null) {
                return inCache(cacheKey, AuthenticationInformation.authServerError());
            }

            // 处理认证响应
            ReturnResult returnResult = Json.fromJson(response, ReturnResult.class);
            String code = returnResult.getCode();

            // 处理认证失败
            if (RESOURCE_OAUTH_ERROR.getCode().equals(code) || RESULT_ACCESS_UNAUTHORIZED.getCode().equals(code)) {
                HttpServletRequest servletRequest = RequestUtils.getRequest();
                if (null != servletRequest) {
                    CookieUtil.remove(servletRequest, ResponseUtils.getResponse(), "x-oauth-cookie");
                }
                return inCache(cacheKey, new AuthenticationInformation(AUTHENTICATION_FAILURE, null));
            }

            // 检查响应数据
            Object data = returnResult.getData();
            if (Objects.isNull(data)) {
                return inCache(cacheKey, new AuthenticationInformation(AUTHENTICATION_SERVER_EXCEPTION, null));
            }

            // 处理认证成功
            if (ReturnCode.OK.getCode().equals(code)) {
                String body = Codec.build(encryption, key).decodeHex(data.toString());
                UserResume userResume = Json.fromJson(body, UserResume.class);
                RequestUtils.setUsername(userResume.getUsername());
                RequestUtils.setUserInfo(userResume);
                return inCache(cacheKey, new AuthenticationInformation(Information.OK, userResume));
            }

            return inCache(cacheKey, new AuthenticationInformation(OTHER, null));
        } catch (Exception e) {
            log.error("WebSocket认证异常", e);
            return inCache(cacheKey, AuthenticationInformation.authServerError());
        }
    }

    /**
     * 处理会话升级请求
     *
     * @param cookie       用于升级的Cookie数组
     * @param token        用于升级的令牌字符串
     * @param upgradeType  升级类型
     * @param refreshToken 刷新令牌
     * @return 升级结果
     */
    @Override
    public LoginResult upgrade(Cookie[] cookie, String token, UpgradeType upgradeType, String refreshToken) {
        checkEncryption();
        String key = DigestUtils.md5Hex(UUID.randomUUID().toString());
        Map<String, Object> jsonObject = new HashMap<>(4);
        Cookie[] cookies = Optional.ofNullable(cookie).orElse(new Cookie[0]);
        String cacheKey = getCacheKey(cookies, token);

        // 构建升级请求数据
        jsonObject.put("x-oauth-cookie", cookies);
        jsonObject.put("x-oauth-token", token);
        jsonObject.put(OAUTH_UPGRADE_KEY, upgradeType);
        jsonObject.put(OAUTH_UPGRADE_KEY_TOKEN, refreshToken);
        String accessKey = authClientProperties.getAccessKey();
        String secretKey = authClientProperties.getSecretKey();
        String serviceKey = authClientProperties.getServiceKey();
        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            accessKey = DefSecret.ACCESS_KEY;
            secretKey = DefSecret.SECRET_KEY;
        }

        // 加密升级数据
        String asString = Json.toJson(jsonObject);
        String request = Codec.build(encryption, Md5Utils.getInstance()
                .getMd5String(accessKey + DigestUtils.md5Hex(secretKey + key))).encodeHex(asString);
        Map<String, Object> item2 = new HashMap<>(5);
        item2.put(AuthConstant.ACCESS_KEY, accessKey);
        item2.put(AuthConstant.SECRET_KEY, secretKey);
        item2.put(AuthConstant.OAUTH_VALUE, request);
        item2.put(AuthConstant.OAUTH_KEY, key);
        item2.put("type", "upgrade");
        request = Codec.build(encryption, serviceKey).encodeHex(Json.toJson(item2));

        // 选择认证服务器节点
        String serverUrl = selectServerUrl();
        if (serverUrl == null) {
            throw new IllegalArgumentException("认证服务器不可用");
        }

        try {
            // 发送WebSocket升级请求并等待响应
            String response = sendWebSocketRequest(serverUrl, request);
            if (response == null) {
                throw new IllegalArgumentException("认证服务器未响应");
            }

            // 处理升级响应
            ReturnResult returnResult = Json.fromJson(response, ReturnResult.class);
            String code = returnResult.getCode();

            // 处理升级失败
            if (RESOURCE_OAUTH_ERROR.getCode().equals(code)) {
                HttpServletRequest servletRequest = RequestUtils.getRequest();
                if (null != servletRequest) {
                    CookieUtil.remove(servletRequest, ResponseUtils.getResponse(), "x-oauth-cookie");
                }
                inCache(cacheKey, new AuthenticationInformation(AUTHENTICATION_FAILURE, null));
                throw new IllegalArgumentException("认证失败");
            }

            // 检查响应数据
            Object data = returnResult.getData();
            if (Objects.isNull(data)) {
                inCache(cacheKey, new AuthenticationInformation(AUTHENTICATION_SERVER_EXCEPTION, null));
                throw new IllegalArgumentException("认证服务器异常");
            }

            // 处理升级成功
            if (ReturnCode.OK.getCode().equals(code)) {
                String body = Codec.build(encryption, key).decodeHex(data.toString());
                LoginResult loginResult = Json.fromJson(body, LoginResult.class);
                UserResume userResume = new UserResume();
                // 复制用户信息
                if (loginResult.getUserResult() != null) {
                    userResume.setUsername(loginResult.getUserResult().getUsername());
                    userResume.setUserId(loginResult.getUserResult().getUserId());
                    userResume.setTenantId(loginResult.getUserResult().getTenantId());
                    userResume.setRoles(loginResult.getUserResult().getRoles());
                    userResume.setPermission(loginResult.getUserResult().getPermission());
                }
                inCache(cacheKey, new AuthenticationInformation(Information.OK, userResume));
                return loginResult;
            }

            inCache(cacheKey, new AuthenticationInformation(OTHER, null));
            throw new IllegalArgumentException("升级失败");
        } catch (Exception e) {
            log.error("WebSocket升级异常", e);
            inCache(cacheKey, AuthenticationInformation.authServerError());
            throw new IllegalArgumentException("升级异常: " + e.getMessage());
        }
    }

    /**
     * 选择认证服务器URL
     *
     * @return 认证服务器WebSocket URL
     */
    private String selectServerUrl() {
        Robin balance = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin stringRobin = balance.create();
        String address = authClientProperties.getAddress();
        if (null == address) {
            log.warn("认证服务器地址未配置");
            return null;
        }
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(address).split(",");
        stringRobin.addNode(split);
        Node robin = stringRobin.selectNode();
        String url = robin.getString();
        if (null == url) {
            return null;
        }

        // 转换HTTP URL为WebSocket URL
        url = StringUtils.startWithAppend(url, "http://");
        url = url.replace("http://", "ws://").replace("https://", "wss://");
        return StringUtils.endWithAppend(url, "/") + "ws/oauth";
    }

    /**
     * 发送WebSocket请求并等待响应
     *
     * @param serverUrl 服务器URL
     * @param message   请求消息
     * @return 响应消息
     * @throws Exception 连接或通信异常
     */
    private String sendWebSocketRequest(String serverUrl, String message) throws Exception {
        // 检查是否有可用的会话
        WebSocketClient client = getOrCreateClient(serverUrl);
        if (client == null || !client.isConnected()) {
            log.warn("无法建立WebSocket连接: {}", serverUrl);
            return null;
        }

        // 发送请求并等待响应
        CompletableFuture<String> responseFuture = client.sendMessage(message);
        try {
            return responseFuture.get(RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("WebSocket请求超时: {}", serverUrl);
            return null;
        }
    }

    /**
     * 获取或创建WebSocket客户端
     *
     * @param serverUrl 服务器URL
     * @return WebSocket客户端
     */
    private WebSocketClient getOrCreateClient(String serverUrl) {
        // 检查现有客户端
        WebSocketClient existingClient = sessionMap.get(serverUrl);
        if (existingClient != null && existingClient.isConnected()) {
            return existingClient;
        }

        // 创建新客户端
        try {
            WebSocketClient client = new WebSocketClient(serverUrl);
            boolean connected = client.connect();

            if (connected) {
                // 缓存客户端
                sessionMap.put(serverUrl, client);
                return client;
            } else {
                log.error("WebSocket连接失败: {}", serverUrl);
                return null;
            }
        } catch (Exception e) {
            log.error("WebSocket连接失败: {}", serverUrl, e);
            return null;
        }
    }

    /**
     * 初始化缓存
     */
    void checkCache() {
        check();
    }

    /**
     * 检查并初始化
     */
    void check() {
        checkEncryption();
        if (null != CACHEABLE) {
            return;
        }
        try {
            afterPropertiesSet();
        } catch (Exception ignored) {
        }
    }

    /**
     * 检查并设置加密算法
     */
    private void checkEncryption() {
        if (null != encryption) {
            return;
        }
        this.encryption = authClientProperties.getEncryption();
    }

    /**
     * 缓存认证信息
     *
     * @param cacheKey                  缓存键
     * @param authenticationInformation 认证信息
     * @return 认证信息
     */
    AuthenticationInformation inCache(String cacheKey, AuthenticationInformation authenticationInformation) {
        if (null == cacheKey) {
            return authenticationInformation;
        }

        UserResume userResume = authenticationInformation.getReturnResult();
        if (null != userResume) {
            RequestUtils.setUsername(userResume.getUsername());
            RequestUtils.setUserInfo(userResume);
            RequestUtils.setUserId(userResume.getUserId());
            RequestUtils.setTenantId(userResume.getTenantId());
        }
        return CACHEABLE.put(cacheKey, authenticationInformation);
    }

    /**
     * 获取缓存键
     *
     * @param cookies Cookie数组
     * @param token   令牌
     * @return 缓存键
     */
    String getCacheKey(Cookie[] cookies, String token) {
        if (StringUtils.isNotEmpty(StringUtils.ifValid(token, ""))) {
            return token;
        }

        for (Cookie cookie : cookies) {
            if ("x-oauth-cookie".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.encryption = authClientProperties.getEncryption();
        CACHEABLE = new GuavaCacheable((int) authClientProperties.getCacheTimeout() / 3600);
        CACHEABLE.afterPropertiesSet();
        CACHEABLE = CACHEABLE.cacheHotColdBackup(authClientProperties.isCacheHotColdBackup());
    }

    /**
     * WebSocket客户端
     */
    private static class WebSocketClient extends Endpoint {
        private final URI serverUri;
        private final Map<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
        private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
        private Session session;
        private String lastMessageId;
        private Timer heartbeatTimer;
        private boolean connected = false;

        public WebSocketClient(String serverUrl) throws Exception {
            this.serverUri = new URI(serverUrl);
        }

        /**
         * 连接到WebSocket服务器
         *
         * @return 是否连接成功
         */
        public boolean connect() {
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

                session = container.connectToServer(this, config, serverUri);
                connected = true;

                // 启动心跳
                startHeartbeat();

                return true;
            } catch (Exception e) {
                log.error("WebSocket连接失败", e);
                return false;
            }
        }

        /**
         * 检查是否已连接
         *
         * @return 是否已连接
         */
        public boolean isConnected() {
            return connected && session != null && session.isOpen();
        }

        /**
         * 发送消息并等待响应
         *
         * @param message 消息内容
         * @return 响应内容的Future
         */
        public CompletableFuture<String> sendMessage(String message) {
            if (!isConnected()) {
                CompletableFuture<String> future = new CompletableFuture<>();
                future.completeExceptionally(new IOException("WebSocket未连接"));
                return future;
            }

            try {
                // 生成消息ID
                String messageId = UUID.randomUUID().toString();
                CompletableFuture<String> responseFuture = new CompletableFuture<>();

                // 注册待处理请求
                pendingRequests.put(messageId, responseFuture);
                lastMessageId = messageId;

                // 发送消息
                session.getBasicRemote().sendText(message);
                return responseFuture;
            } catch (IOException e) {
                CompletableFuture<String> future = new CompletableFuture<>();
                future.completeExceptionally(e);
                return future;
            }
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            this.session = session;
            this.connected = true;

            // 添加消息处理器
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }
            });

            log.debug("WebSocket连接已建立: {}", session.getId());
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            this.connected = false;
            log.debug("WebSocket连接已关闭: {}, 原因: {}", session.getId(), closeReason);

            // 完成所有待处理请求
            for (CompletableFuture<String> future : pendingRequests.values()) {
                future.completeExceptionally(new IOException("连接已关闭: " + closeReason));
            }
            pendingRequests.clear();

            // 停止心跳
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel();
                heartbeatTimer = null;
            }
        }

        @Override
        public void onError(Session session, Throwable throwable) {
            log.error("WebSocket错误: {}", session.getId(), throwable);

            // 完成所有待处理请求
            for (CompletableFuture<String> future : pendingRequests.values()) {
                future.completeExceptionally(throwable);
            }
            pendingRequests.clear();
        }

        /**
         * 处理接收到的消息
         *
         * @param message 消息内容
         */
        private void handleMessage(String message) {
            log.debug("收到WebSocket消息: {}", message);

            // 处理心跳响应
            if ("PONG".equals(message)) {
                return;
            }

            // 完成对应的请求
            if (lastMessageId != null && pendingRequests.containsKey(lastMessageId)) {
                CompletableFuture<String> future = pendingRequests.remove(lastMessageId);
                if (future != null) {
                    future.complete(message);
                }
            } else {
                // 放入消息队列
                messageQueue.offer(message);
            }
        }

        /**
         * 启动心跳定时器
         */
        private void startHeartbeat() {
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel();
            }

            heartbeatTimer = new Timer(true);
            heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (isConnected()) {
                        try {
                            session.getBasicRemote().sendText("PING");
                        } catch (IOException e) {
                            log.warn("发送心跳失败", e);
                        }
                    } else {
                        heartbeatTimer.cancel();
                    }
                }
            }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL);
        }

        /**
         * 关闭连接
         */
        public void close() {
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel();
                heartbeatTimer = null;
            }

            if (isConnected()) {
                try {
                    session.close();
                } catch (IOException e) {
                    log.warn("关闭WebSocket连接失败", e);
                }
            }

            connected = false;
        }
    }
} 