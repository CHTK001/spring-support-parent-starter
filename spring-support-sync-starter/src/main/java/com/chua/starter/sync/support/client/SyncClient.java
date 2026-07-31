package com.chua.starter.sync.support.client;

import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.sync.SyncFlowListener;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.properties.SyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;

/**
 * 同步客户端包装器。
 *
 * @author CH
 * @since 2026/07/31
 */
public class SyncClient implements com.chua.common.support.network.sync.SyncClient {

    private static final Logger log = LoggerFactory.getLogger(SyncClient.class);

    private final SyncProperties syncProperties;
    private final Environment environment;
    private SyncClient delegate;

    public SyncClient(SyncProperties syncProperties,
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
                throw new IllegalArgumentException("Unsupported sync client protocol: " + protocol);
            }
            if (this.delegate.getClientId() == null) {
                this.delegate = new com.chua.common.support.network.sync.impl.WebSocketSyncClient("ws://" + serverAddress);
            }
            ClientInfo clientInfo = buildClientInfo();
            for (com.chua.starter.sync.support.spi.SyncClientInfoCustomizer customizer : infoCustomizers) {
                customizer.customize(clientInfo);
            }
            for (com.chua.starter.sync.support.spi.SyncClientCustomizer customizer : customizers) {
                customizer.customize(this);
            }
        } catch (Exception e) {
            throw new IllegalStateException("创建 SyncClient 失败", e);
        }
    }

    private ClientInfo buildClientInfo() {
        ClientInfo info = new ClientInfo();
        info.setClientId(delegate.getClientId());
        Map<String, Object> metadata = delegate.getMetadata();
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
        return info;
    }

    @Override
    public void connect() {
        delegate.connect();
    }

    @Override
    public void disconnect() {
        delegate.disconnect();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public String getClientId() {
        return delegate.getClientId();
    }

    @Override
    public void send(String topic, Object message) {
        delegate.send(topic, message);
    }

    @Override
    public void subscribe(String topic, SyncMessageHandler handler) {
        delegate.subscribe(topic, handler);
    }

    @Override
    public void unsubscribe(String topic) {
        delegate.unsubscribe(topic);
    }

    @Override
    public void addListener(SyncFlowListener listener) {
        delegate.addListener(listener);
    }

    @Override
    public void removeListener(SyncFlowListener listener) {
        delegate.removeListener(listener);
    }

    @Override
    public java.util.Map<String, Object> getMetadata() {
        return delegate.getMetadata();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
