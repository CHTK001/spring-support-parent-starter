package com.chua.starter.sync.support.client;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.network.sync.SyncFlowListener;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.properties.SyncProperties;
import com.chua.starter.sync.support.security.SyncCryptoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;

/**
 * 同步客户端包装器。
 *
 * @since 2026/07/31
 * @author CH
 */
public class SyncClientWrapper implements com.chua.starter.sync.support.client.SyncClient {

    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(SyncClientWrapper.class);

    /**
     * 同步属性
    */
    private final SyncProperties syncProperties;
    /**
     * 环境
    */
    private final Environment environment;
    /**
     * Delegate
    */
    private com.chua.common.support.network.sync.SyncClient delegate;

    /**
     * 客户端信息（连接后自动注册到服务端）
     */
    private ClientInfo clientInfo;

    /**
     * 创建 同步客户端包装器 实例
     * @param syncProperties 同步属性
     * @param environment 环境
     * @param infoCustomizers 信息customizers
     * @param customizers customizers
     */
    public SyncClientWrapper(SyncProperties syncProperties,
                             Environment environment,
                             List<com.chua.starter.sync.support.spi.SyncClientInfoCustomizer> infoCustomizers,
                             List<com.chua.starter.sync.support.spi.SyncClientCustomizer> customizers) {
        this.syncProperties = syncProperties;
        this.environment = environment;
        try {
            String protocol = syncProperties.getClient().getProtocol();
            String serverAddress = syncProperties.getClient().getEffectiveServerAddress();
            this.delegate = ServiceProvider.of(com.chua.common.support.network.sync.SyncClient.class)
                    .getNewExtension(protocol, serverAddress);
            if (this.delegate == null) {
                /**
                 * IllegalArgumentException。
                 * @param protocol 协议
                 * @return IllegalArgumentException的结果
                 */
                throw new IllegalArgumentException("Unsupported sync client protocol: " + protocol);
            }
            if (this.delegate.getClientId() == null) {
                this.delegate = new com.chua.common.support.network.sync.impl.WebSocketSyncClient("ws://" + serverAddress);
            }
            this.clientInfo = buildClientInfo();
            for (com.chua.starter.sync.support.spi.SyncClientInfoCustomizer customizer : infoCustomizers) {
                customizer.customize(this.clientInfo);
            }
            for (com.chua.starter.sync.support.spi.SyncClientCustomizer customizer : customizers) {
                customizer.customize(this);
            }
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param 失败" 失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("创建 SyncClient 失败", e);
        }
    }

    /**
     * 构建客户端信息
     *
     * @return 构建客户端信息的结果
     */
    private ClientInfo buildClientInfo() {
        ClientInfo info = new ClientInfo();
        info.setClientId(delegate.getClientId());
        Map<String, Object> metadata = delegate.getMetadata();
        Map<String, Object> clientMetadata = new java.util.HashMap<>();
        if (metadata != null) {
            if (metadata.get("clientIpAddress") != null) {
                info.setClientIpAddress(String.valueOf(metadata.get("clientIpAddress")));
            }
            if (metadata.get("clientPort") != null) {
                info.setClientPort(Integer.parseInt(String.valueOf(metadata.get("clientPort"))));
            }
            if (metadata.get("clientApplicationName") != null) {
                info.setClientApplicationName(String.valueOf(metadata.get("clientApplicationName")));
            }
        }
        info.setClientMetadata(clientMetadata);
        return info;
    }

    @Override
    /**
     * 连接
    */
    public void connect() {
        delegate.connect();
        if (syncProperties.getClient().isAutoRegister() && clientInfo != null) {
            /**
             * 发送注册。
             */
            sendRegister();
        }
    }

    /**
     * 向服务端发送客户端注册消息。
     * <p>
     * 服务端通过注册消息登记客户端信息（含租户等元数据），
     * 以支持按租户定向推送。
     * </p>
     */
    private void sendRegister() {
        try {
            Map<String, Object> register = new java.util.HashMap<>();
            register.put("clientId", clientInfo.getClientId());
            register.put("clientApplicationName", clientInfo.getClientApplicationName());
            register.put("clientIpAddress", clientInfo.getClientIpAddress());
            register.put("clientPort", clientInfo.getClientPort());
            register.put("clientMetadata", clientInfo.getClientMetadata());
            /**
             * 发送。
             * @param syncProperties.getClient( 同步属性.getclient(
             */
            send(syncProperties.getClient().getRegisterTopic(), register);
            log.info("[SyncClient] 已发送客户端注册: clientId={}", clientInfo.getClientId());
        } catch (Exception e) {
            log.warn("[SyncClient] 客户端注册消息发送失败 cause={}", e.getMessage());
        }
    }

    @Override
    /**
     * 断开
    */
    public void disconnect() {
        delegate.disconnect();
    }

    @Override
    /**
     * 是否连接
    */
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    /**
     * 获取客户端标识
    */
    public String getClientId() {
        return delegate.getClientId();
    }

    @Override
    /**
     * 发送
    */
    public void send(String topic, Object message) {
        delegate.send(topic, SyncCryptoUtils.encrypt(message, syncProperties));
    }

    @Override
    /**
     * 订阅
    */
    public void subscribe(String topic, SyncMessageHandler handler) {
        delegate.subscribe(topic, handler);
    }

    @Override
    /**
     * 订阅
    */
    public void subscribe(String... topics) {
        for (String topic : topics) {
            delegate.subscribe(topic, null);
        }
    }

    @Override
    /**
     * 注册处理器
    */
    public void registerHandler(String topic, com.chua.starter.sync.support.spi.SyncMessageHandler handler) {
        delegate.subscribe(topic, (sessionId, message) -> {
            Object decrypted = SyncCryptoUtils.decrypt(parseIfNeeded(message), syncProperties);
            if (decrypted instanceof java.util.Map<?, ?> map) {
                handler.handle(topic, sessionId, (java.util.Map<String, Object>) map);
            }
        });
    }

    /**
     * 将传输层收到的字符串消息解析为结构化对象（JSON -> 映射）。
     *
     * @param message 收到的消息
     * @return 解析后的对象，无法解析时原样返回
     */
    private Object parseIfNeeded(Object message) {
        if (!(message instanceof String text) || text.isBlank()) {
            return message;
        }
        try {
            return Json.fromJson(text, java.util.Map.class);
        } catch (Exception e) {
            return message;
        }
    }

    @Override
    /**
     * 发布
    */
    public void publish(String topic, Map<String, Object> data) {
        delegate.send(topic, SyncCryptoUtils.encrypt(data, syncProperties));
    }

    @Override
    /**
     * 取消订阅
    */
    public void unsubscribe(String topic) {
        delegate.unsubscribe(topic);
    }

    @Override
    /**
     * 添加监听器
    */
    public void addListener(SyncFlowListener listener) {
        delegate.addListener(listener);
    }

    @Override
    /**
     * 移除监听器
    */
    public void removeListener(SyncFlowListener listener) {
        delegate.removeListener(listener);
    }

    @Override
    /**
     * 获取Metadata
    */
    public java.util.Map<String, Object> getMetadata() {
        return delegate.getMetadata();
    }

    @Override
    /**
     * 关闭
    */
    public void close() {
        delegate.close();
    }
}
