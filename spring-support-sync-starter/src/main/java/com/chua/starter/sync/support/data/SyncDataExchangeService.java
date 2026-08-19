package com.chua.starter.sync.support.data;

import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.common.support.network.sync.SyncClient;
import com.chua.starter.sync.support.pojo.SyncResponse;
import com.chua.starter.sync.support.properties.SyncProperties;
import com.chua.starter.sync.support.server.SyncServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 数据同步交换服务。
 * <p>
 * 将底层 SyncClient/SyncServer 的 topic publish/request 能力提升为面向业务的数据同步交换接口。
 * </p>
 *
 * @since 2026/03/23
 * @author CH
 */
public class SyncDataExchangeService {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(SyncDataExchangeService.class);

    /** 同步客户端提供者 */
    private final ObjectProvider<SyncClient> syncClientProvider;
    /** 同步服务器提供者 */
    private final ObjectProvider<SyncServer> syncServerProvider;
    /** 同步属性 */
    private final SyncProperties syncProperties;

    public SyncDataExchangeService(ObjectProvider<SyncClient> syncClientProvider,
                                   ObjectProvider<SyncServer> syncServerProvider,
                                   SyncProperties syncProperties) {
        this.syncClientProvider = syncClientProvider;
        this.syncServerProvider = syncServerProvider;
        this.syncProperties = SyncProperties.copyOf(syncProperties);
    }

    public void publish(String channel, Object payload) {
        publish(channel, "SYNC_DATA", payload, Collections.emptyMap());
    }

    public void publish(String channel, String eventType, Object payload, Map<String, Object> metadata) {
        SyncClient syncClient = requireSyncClient();
        String topic = resolveTopic(channel);
        syncClient.send(topic, payload);
        log.debug("[SyncData] 客户端发送数据同步消息, topic={}, eventType={}", topic, eventType);
    }

    public SyncResponse request(String channel, Object payload) {
        return request(channel, "SYNC_REQUEST", payload, Collections.emptyMap());
    }

    public SyncResponse request(String channel, String eventType, Object payload, Map<String, Object> metadata) {
        SyncClient syncClient = requireSyncClient();
        String topic = resolveTopic(channel);
        syncClient.send(topic, payload);
        log.debug("[SyncData] 客户端发送同步请求, topic={}, eventType={}", topic, eventType);
        return SyncResponse.success(UUID.randomUUID().toString(), syncClient.getClientId(), payload);
    }

    public void broadcast(String channel, Object payload) {
        SyncServer syncServer = requireSyncServer();
        String topic = resolveTopic(channel);
        syncServer.broadcast(topic, payload);
    }

    public void sendToSession(String sessionId, String channel, Object payload) {
        SyncServer syncServer = requireSyncServer();
        String topic = resolveTopic(channel);
        syncServer.send(sessionId, topic, payload);
    }

    public int sendToApplication(String appName, String channel, Object payload) {
        SyncServer syncServer = requireSyncServer();
        String topic = resolveTopic(channel);
        return syncServer.publishByAppName(appName, topic, payload);
    }

    public int sendToMetadata(String metadataKey, Object metadataValue, String channel, Object payload) {
        return sendToMetadata(metadataKey, metadataValue, channel, "SYNC_METADATA", payload, Collections.emptyMap());
    }

    public int sendToMetadata(String metadataKey,
                              Object metadataValue,
                              String channel,
                              String eventType,
                              Object payload,
                              Map<String, Object> metadata) {
        SyncServer syncServer = requireSyncServer();
        String topic = resolveTopic(channel);
        return syncServer.publishByMetadata(metadataKey, metadataValue, topic, payload);
    }

    public int sendToClients(Predicate<ClientInfo> filter, String channel, Object payload) {
        return sendToClients(filter, channel, "SYNC_FILTER", payload, Collections.emptyMap());
    }

    public int sendToClients(Predicate<ClientInfo> filter,
                             String channel,
                             String eventType,
                             Object payload,
                             Map<String, Object> metadata) {
        SyncServer syncServer = requireSyncServer();
        String topic = resolveTopic(channel);
        return syncServer.publishToClients(filter, topic, payload);
    }

    public boolean hasClient() {
        return syncClientProvider.getIfAvailable() != null;
    }

    public boolean hasServer() {
        return syncServerProvider.getIfAvailable() != null;
    }

    private String resolveTopic(String channel) {
        return syncProperties.getDataSync().getTopicPrefix() + "/" + resolveChannel(channel);
    }

    private String resolveChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return syncProperties.getDataSync().getDefaultChannel();
        }
        return channel.trim();
    }

    private SyncClient requireSyncClient() {
        SyncClient syncClient = syncClientProvider.getIfAvailable();
        if (syncClient == null) {
            throw new IllegalStateException("当前未启用 SyncClient，无法执行客户端数据同步操作");
        }
        return syncClient;
    }

    private SyncServer requireSyncServer() {
        SyncServer syncServer = syncServerProvider.getIfAvailable();
        if (syncServer == null) {
            throw new IllegalStateException("当前未启用 SyncServer，无法执行服务端数据同步操作");
        }
        return syncServer;
    }
}
