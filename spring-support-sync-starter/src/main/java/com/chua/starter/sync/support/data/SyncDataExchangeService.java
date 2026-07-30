package com.chua.starter.sync.support.data;

import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.client.SyncClient;
import com.chua.starter.sync.support.pojo.SyncResponse;
import com.chua.starter.sync.support.properties.SyncProperties;
import com.chua.starter.sync.support.server.SyncServer;
import lombok.extern.slf4j.Slf4j;
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
 * @author CH
 * @since 2026/03/23
 */
@Slf4j
public class SyncDataExchangeService {

    private final ObjectProvider<SyncClient> syncClientProvider;
    private final ObjectProvider<SyncServer> syncServerProvider;
    private final SyncProperties syncProperties;

    public SyncDataExchangeService(ObjectProvider<SyncClient> syncClientProvider,
                                   ObjectProvider<SyncServer> syncServerProvider,
                                   SyncProperties syncProperties) {
        this.syncClientProvider = syncClientProvider;
        this.syncServerProvider = syncServerProvider;
        this.syncProperties = SyncProperties.copyOf(syncProperties);
    }

    /**
     * 客户端向服务端发送数据同步消息。
     *
     * @param channel 逻辑通道
     * @param payload 负载
     */
    public void publish(String channel, Object payload) {
        publish(channel, "SYNC_DATA", payload, Collections.emptyMap());
    }

    /**
     * 客户端向服务端发送数据同步消息。
     *
     * @param channel  逻辑通道
     * @param eventType 事件类型
     * @param payload  负载
     * @param metadata 元数据
     */
    public void publish(String channel, String eventType, Object payload, Map<String, Object> metadata) {
        SyncClient syncClient = requireSyncClient();
        SyncDataEnvelope envelope = buildEnvelope(channel, eventType, payload, metadata);
        syncClient.publish(resolveTopic(channel), envelope);
        log.debug("[SyncData] 客户端发送数据同步消息, topic={}, eventType={}", resolveTopic(channel), eventType);
    }

    /**
     * 客户端发起同步请求。
     *
     * @param channel 逻辑通道
     * @param payload 负载
     * @return 同步响应
     */
    public SyncResponse request(String channel, Object payload) {
        return request(channel, "SYNC_REQUEST", payload, Collections.emptyMap());
    }

    /**
     * 客户端发起同步请求。
     *
     * @param channel   逻辑通道
     * @param eventType 事件类型
     * @param payload   负载
     * @param metadata  元数据
     * @return 同步响应
     */
    public SyncResponse request(String channel, String eventType, Object payload, Map<String, Object> metadata) {
        SyncClient syncClient = requireSyncClient();
        SyncDataEnvelope envelope = buildEnvelope(channel, eventType, payload, metadata);
        return syncClient.request(resolveTopic(channel), envelope, syncProperties.getDataSync().getRequestTimeout());
    }

    /**
     * 服务端广播数据同步消息。
     *
     * @param channel 逻辑通道
     * @param payload 负载
     */
    public void broadcast(String channel, Object payload) {
        SyncServer syncServer = requireSyncServer();
        SyncDataEnvelope envelope = buildEnvelope(channel, "SYNC_BROADCAST", payload, Collections.emptyMap());
        syncServer.broadcast(resolveTopic(channel), envelope);
    }

    /**
     * 服务端向指定会话发送数据同步消息。
     *
     * @param sessionId 会话ID
     * @param channel   逻辑通道
     * @param payload   负载
     */
    public void sendToSession(String sessionId, String channel, Object payload) {
        SyncServer syncServer = requireSyncServer();
        SyncDataEnvelope envelope = buildEnvelope(channel, "SYNC_DIRECT", payload, Collections.emptyMap());
        syncServer.send(sessionId, resolveTopic(channel), envelope);
    }

    /**
     * 服务端向指定应用发送数据同步消息。
     *
     * @param appName  应用名
     * @param channel  逻辑通道
     * @param payload  负载
     * @return 推送客户端数量
     */
    public int sendToApplication(String appName, String channel, Object payload) {
        SyncServer syncServer = requireSyncServer();
        SyncDataEnvelope envelope = buildEnvelope(channel, "SYNC_APP", payload, Collections.emptyMap());
        envelope.setTargetAppName(appName);
        return syncServer.publishByAppName(appName, resolveTopic(channel), envelope);
    }

    /**
     * 服务端按客户端元数据定向发送数据同步消息。
     *
     * @param metadataKey   元数据键
     * @param metadataValue 元数据值
     * @param channel       逻辑通道
     * @param payload       负载
     * @return 推送客户端数量
     */
    public int sendToMetadata(String metadataKey, Object metadataValue, String channel, Object payload) {
        return sendToMetadata(metadataKey, metadataValue, channel, "SYNC_METADATA", payload, Collections.emptyMap());
    }

    /**
     * 服务端按客户端元数据定向发送数据同步消息。
     *
     * @param metadataKey   元数据键
     * @param metadataValue 元数据值
     * @param channel       逻辑通道
     * @param eventType     事件类型
     * @param payload       负载
     * @param metadata      元数据
     * @return 推送客户端数量
     */
    public int sendToMetadata(String metadataKey,
                              Object metadataValue,
                              String channel,
                              String eventType,
                              Object payload,
                              Map<String, Object> metadata) {
        SyncServer syncServer = requireSyncServer();
        SyncDataEnvelope envelope = buildEnvelope(channel, eventType, payload, metadata);
        return syncServer.publishByMetadata(metadataKey, metadataValue, resolveTopic(channel), envelope);
    }

    /**
     * 服务端按自定义过滤条件定向发送数据同步消息。
     *
     * @param filter   过滤条件
     * @param channel  逻辑通道
     * @param payload  负载
     * @return 推送客户端数量
     */
    public int sendToClients(Predicate<ClientInfo> filter, String channel, Object payload) {
        return sendToClients(filter, channel, "SYNC_FILTER", payload, Collections.emptyMap());
    }

    /**
     * 服务端按自定义过滤条件定向发送数据同步消息。
     *
     * @param filter    过滤条件
     * @param channel   逻辑通道
     * @param eventType 事件类型
     * @param payload   负载
     * @param metadata  元数据
     * @return 推送客户端数量
     */
    public int sendToClients(Predicate<ClientInfo> filter,
                             String channel,
                             String eventType,
                             Object payload,
                             Map<String, Object> metadata) {
        SyncServer syncServer = requireSyncServer();
        SyncDataEnvelope envelope = buildEnvelope(channel, eventType, payload, metadata);
        return syncServer.publishToClients(filter, resolveTopic(channel), envelope);
    }

    /**
     * 是否具备客户端能力。
     *
     * @return 是否存在客户端
     */
    public boolean hasClient() {
        return syncClientProvider.getIfAvailable() != null;
    }

    /**
     * 是否具备服务端能力。
     *
     * @return 是否存在服务端
     */
    public boolean hasServer() {
        return syncServerProvider.getIfAvailable() != null;
    }

    private SyncDataEnvelope buildEnvelope(String channel,
                                           String eventType,
                                           Object payload,
                                           Map<String, Object> metadata) {
        String resolvedChannel = resolveChannel(channel);
        return SyncDataEnvelope.builder()
                .requestId(UUID.randomUUID().toString())
                .channel(resolvedChannel)
                .eventType(eventType)
                .sourceClientId(resolveClientId())
                .payload(payload)
                .metadata(metadata == null ? new java.util.HashMap<>() : new java.util.HashMap<>(metadata))
                .timestamp(System.currentTimeMillis())
                .build();
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

    private String resolveClientId() {
        SyncClient syncClient = syncClientProvider.getIfAvailable();
        return syncClient != null ? syncClient.getClientId() : "server";
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
