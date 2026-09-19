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
 * 将底层 同步客户端/同步服务端 的 topic 发布/请求 能力提升为面向业务的数据同步交换接口。
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

    /**
     * 创建 同步数据exchange服务 实例
     * @param syncClientProvider 同步客户端提供者
     * @param syncServerProvider 同步服务端提供者
     * @param syncProperties 同步属性
     */
    public SyncDataExchangeService(ObjectProvider<SyncClient> syncClientProvider,
                                   ObjectProvider<SyncServer> syncServerProvider,
                                   SyncProperties syncProperties) {
        this.syncClientProvider = syncClientProvider;
        this.syncServerProvider = syncServerProvider;
        this.syncProperties = SyncProperties.copyOf(syncProperties);
    }

    /**
     * 发布
     *
     * @param channel 通道
     * @param payload payload
     */
    public void publish(String channel, Object payload) {
        /**
         * 发布。
         * @param channel 通道
         * @param "SYNC_DATA" "同步_数据"
         * @param payload payload
         * @param Collections.emptyMap( 集合.空映射(
         */
        publish(channel, "SYNC_DATA", payload, Collections.emptyMap());
    }

    /**
     * 发布
     *
     * @param channel 通道
     * @param eventType 事件类型
     * @param payload payload
     * @param metadata metadata
     */
    public void publish(String channel, String eventType, Object payload, Map<String, Object> metadata) {
        SyncClient syncClient = requireSyncClient();
        String topic = resolveTopic(channel);
        syncClient.send(topic, payload);
        log.debug("[SyncData] 客户端发送数据同步消息, topic={}, eventType={}", topic, eventType);
    }

    /**
     * 请求
     *
     * @param channel 通道
     * @param payload payload
     * @return 请求的结果
     */
    public SyncResponse request(String channel, Object payload) {
        return request(channel, "SYNC_REQUEST", payload, Collections.emptyMap());
    }

    /**
     * 请求
     *
     * @param channel 通道
     * @param eventType 事件类型
     * @param payload payload
     * @param metadata metadata
     * @return 请求的结果
     */
    public SyncResponse request(String channel, String eventType, Object payload, Map<String, Object> metadata) {
        SyncClient syncClient = requireSyncClient();
        String topic = resolveTopic(channel);
        syncClient.send(topic, payload);
        log.debug("[SyncData] 客户端发送同步请求, topic={}, eventType={}", topic, eventType);
        return SyncResponse.success(UUID.randomUUID().toString(), syncClient.getClientId(), payload);
    }

    /**
     * Broadcast
     *
     * @param channel 通道
     * @param payload payload
     */
    public void broadcast(String channel, Object payload) {
        SyncServer syncServer = requireSyncServer();
        String topic = resolveTopic(channel);
        syncServer.broadcast(topic, payload);
    }

    /**
     * 发送转为会话
     *
     * @param sessionId 会话标识
     * @param channel 通道
     * @param payload payload
     */
    public void sendToSession(String sessionId, String channel, Object payload) {
        SyncServer syncServer = requireSyncServer();
        String topic = resolveTopic(channel);
        syncServer.send(sessionId, topic, payload);
    }

    /**
     * 发送转为application
     *
     * @param appName app名称
     * @param channel 通道
     * @param payload payload
     * @return 发送转为application的结果
     */
    public int sendToApplication(String appName, String channel, Object payload) {
        SyncServer syncServer = requireSyncServer();
        String topic = resolveTopic(channel);
        return syncServer.publishByAppName(appName, topic, payload);
    }

    /**
     * 发送转为metadata
     *
     * @param metadataKey metadataKey
     * @param metadataValue metadata值
     * @param channel 通道
     * @param payload payload
     * @return 发送转为metadata的结果
     */
    public int sendToMetadata(String metadataKey, Object metadataValue, String channel, Object payload) {
        return sendToMetadata(metadataKey, metadataValue, channel, "SYNC_METADATA", payload, Collections.emptyMap());
    }

    /**
     * 发送转为metadata
     * @param metadataKey metadataKey
     * @param metadataValue metadata值
     * @param channel 通道
     * @param eventType 事件类型
     * @param payload payload
     * @param metadata metadata
     */
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

    /**
     * 发送转为客户端
     *
     * @param filter 过滤器
     * @param channel 通道
     * @param payload payload
     * @return 发送转为客户端的结果
     */
    public int sendToClients(Predicate<ClientInfo> filter, String channel, Object payload) {
        return sendToClients(filter, channel, "SYNC_FILTER", payload, Collections.emptyMap());
    }

    /**
     * 发送转为客户端
     * @param filter 过滤器
     * @param channel 通道
     * @param eventType 事件类型
     * @param payload payload
     * @param metadata metadata
     */
    public int sendToClients(Predicate<ClientInfo> filter,
                             String channel,
                             String eventType,
                             Object payload,
                             Map<String, Object> metadata) {
        SyncServer syncServer = requireSyncServer();
        String topic = resolveTopic(channel);
        return syncServer.publishToClients(filter, topic, payload);
    }

    /**
     * 是否拥有客户端
     *
     * @return 是否包含客户端的结果
     */
    public boolean hasClient() {
        return syncClientProvider.getIfAvailable() != null;
    }

    /**
     * 是否拥有服务端
     *
     * @return 是否包含服务端的结果
     */
    public boolean hasServer() {
        return syncServerProvider.getIfAvailable() != null;
    }

    /**
     * 解析Topic
     *
     * @param channel 通道
     * @return resolveTopic的结果
     */
    private String resolveTopic(String channel) {
        return syncProperties.getDataSync().getTopicPrefix() + "/" + resolveChannel(channel);
    }

    /**
     * 解析通道
     *
     * @param channel 通道
     * @return resolve通道的结果
     */
    private String resolveChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return syncProperties.getDataSync().getDefaultChannel();
        }
        return channel.trim();
    }

    /**
     * require同步客户端
     *
     * @return require同步客户端的结果
     */
    private SyncClient requireSyncClient() {
        SyncClient syncClient = syncClientProvider.getIfAvailable();
        if (syncClient == null) {
            /**
             * illegal状态异常。
             * @param SyncClient，无法执行客户端数据同步操作" 同步客户端，无法执行客户端数据同步操作"
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("当前未启用 SyncClient，无法执行客户端数据同步操作");
        }
        return syncClient;
    }

    /**
     * require同步服务端
     *
     * @return require同步服务端的结果
     */
    private SyncServer requireSyncServer() {
        SyncServer syncServer = syncServerProvider.getIfAvailable();
        if (syncServer == null) {
            /**
             * illegal状态异常。
             * @param SyncServer，无法执行服务端数据同步操作" 同步服务端，无法执行服务端数据同步操作"
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("当前未启用 SyncServer，无法执行服务端数据同步操作");
        }
        return syncServer;
    }
}
