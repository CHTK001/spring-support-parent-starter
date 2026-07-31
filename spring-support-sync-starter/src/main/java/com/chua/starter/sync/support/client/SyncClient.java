package com.chua.starter.sync.support.client;

import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.sync.SyncFlowListener;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.sync.support.properties.SyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.UUID;

/**
 * 同步客户端包装器。
 *
 * @author CH
 * @since 2026/07/31
 */
@Slf4j
public class SyncClient implements com.chua.common.support.network.sync.SyncClient {

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
            for (com.chua.starter.sync.support.spi.SyncClientInfoCustomizer customizer : infoCustomizers) {
                customizer.customize(this);
            }
            for (com.chua.starter.sync.support.spi.SyncClientCustomizer customizer : customizers) {
                customizer.customize(this);
            }
        } catch (Exception e) {
            throw new IllegalStateException("创建 SyncClient 失败", e);
        }
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
