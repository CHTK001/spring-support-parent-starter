package com.chua.starter.sync.support.datasync;

import com.chua.starter.datasync.DataSyncServer;
import com.chua.starter.datasync.agent.DataSyncAgentServer;
import com.chua.common.support.network.sync.SyncClient;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import com.chua.starter.sync.support.properties.SyncProperties;
import com.chua.starter.sync.support.server.SyncServer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.function.Function;

/**
 * DataSync 数据同步桥接自动配置
 * <p>
 * 将 Spring Sync 的消息能力桥接到 DataSync 框架：
 * - Spring Sync 网络消息通过 {@link DataSyncMessageBridge} 转换为 DataSync 可识别的数据
 * - DataSync 处理后的结果通过 {@link DataSyncMessagePublisher} 回传到 Spring Sync 网络层
 * </p>
 *
 * @author CH
 * @since 2026/01/15
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.sync.datasync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SyncDataSyncConfiguration {

    private static final String PREFIX = "plugin.sync.datasync";
    private static final Logger log = LoggerFactory.getLogger(SyncDataSyncConfiguration.class);

    /**
     * 环境变量工具
     */
    private final Environment environment;

    /**
     * SyncClient 提供器
     */
    private final ObjectProvider<SyncClient> syncClientProvider;

    /**
     * SyncServer 提供器
     */
    private final ObjectProvider<SyncServer> syncServerProvider;

    /**
     * SyncProperties 提供器
     */
    private final ObjectProvider<SyncProperties> syncPropertiesProvider;

    /**
     * DataSyncServer 提供器
     */
    private final ObjectProvider<com.chua.starter.datasync.DataSyncServer> dataSyncServerProvider;

    /**
     * DataSyncAgentServer 提供器
     */
    private final ObjectProvider<com.chua.starter.datasync.agent.DataSyncAgentServer> dataSyncAgentServerProvider;

    public SyncDataSyncConfiguration(Environment environment,
                                     ObjectProvider<SyncClient> syncClientProvider,
                                     ObjectProvider<SyncServer> syncServerProvider,
                                     ObjectProvider<SyncProperties> syncPropertiesProvider,
                                     ObjectProvider<com.chua.starter.datasync.DataSyncServer> dataSyncServerProvider,
                                     ObjectProvider<com.chua.starter.datasync.agent.DataSyncAgentServer> dataSyncAgentServerProvider) {
        this.environment = environment;
        this.syncClientProvider = syncClientProvider;
        this.syncServerProvider = syncServerProvider;
        this.syncPropertiesProvider = syncPropertiesProvider;
        this.dataSyncServerProvider = dataSyncServerProvider;
        this.dataSyncAgentServerProvider = dataSyncAgentServerProvider;
    }

    @PostConstruct
    public void init() {
        Boolean enable = environment.getProperty(PREFIX + ".enabled", Boolean.class, false);
        ServiceInfoRegistry.getInstance().registerFlag(
                "sync-datasync-bridge", "数据同步", enable,
                "Spring Sync -> DataSync 桥接", null);
        if (!Boolean.TRUE.equals(enable)) {
            log.info("[SyncDataSync] 未启用 DataSync 桥接功能");
            return;
        }
        log.info("[SyncDataSync] DataSync 桥接功能已启用");
    }

    /**
     * 创建 Spring Sync -> DataSync 数据桥接
     *
     * @return 数据桥接函数
     */
    @Bean
    @ConditionalOnProperty(prefix = "plugin.sync.datasync", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataSyncMessageBridge dataSyncMessageBridge() {
        return new DataSyncMessageBridge();
    }

    /**
     * 创建 DataSync -> Spring Sync 消息发布器
     *
     * @return 消息发布器
     */
    @Bean
    @ConditionalOnProperty(prefix = "plugin.sync.datasync", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataSyncMessagePublisher dataSyncMessagePublisher() {
        return new DataSyncMessagePublisher(
                syncClientProvider,
                syncServerProvider,
                syncPropertiesProvider
        );
    }

    // ==================== 内部类 ====================

    /**
     * DataSync 消息桥接函数
     * <p>
     * 负责将 Spring Sync 网络消息转换为 DataSync 框架可处理的数据格式
     * </p>
     *
     * @author CH
     * @since 2026/01/15
     */
    public static class DataSyncMessageBridge implements Function<Object, Object> {

        /**
         * 将输入数据转换为 DataSync 可识别的格式
         *
         * @param input 输入数据（通常为 Map 结构）
         * @return 转换后的数据
         */
        @Override
        public Object apply(Object input) {
            if (input == null) {
                return null;
            }
            if (!(input instanceof java.util.Map)) {
                return input;
            }
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> source = (java.util.Map<String, Object>) input;
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.putAll(source);
            return result;
        }
    }

    /**
     * DataSync 消息发布器
     * <p>
     * 负责将 DataSync 处理后的结果通过 Spring Sync 网络层发布出去
     * </p>
     *
     * @author CH
     * @since 2026/01/15
     */
    public static class DataSyncMessagePublisher {

        /**
         * SyncClient 提供器
         */
        private final ObjectProvider<SyncClient> syncClientProvider;

        /**
         * SyncServer 提供器
         */
        private final ObjectProvider<SyncServer> syncServerProvider;

        /**
         * SyncProperties 提供器
         */
        private final ObjectProvider<SyncProperties> syncPropertiesProvider;

        /**
         * 构造 DataSync 消息发布器
         *
         * @param syncClientProvider    SyncClient 提供器
         * @param syncServerProvider    SyncServer 提供器
         * @param syncPropertiesProvider SyncProperties 提供器
         */
        public DataSyncMessagePublisher(ObjectProvider<SyncClient> syncClientProvider,
                                        ObjectProvider<SyncServer> syncServerProvider,
                                        ObjectProvider<SyncProperties> syncPropertiesProvider) {
            this.syncClientProvider = syncClientProvider;
            this.syncServerProvider = syncServerProvider;
            this.syncPropertiesProvider = syncPropertiesProvider;
        }

        /**
         * 发布消息到指定通道
         *
         * @param channel 通道名称
         * @param payload 消息负载
         */
    public void publish(String channel, Object payload) {
        SyncProperties syncProperties = syncPropertiesProvider.getIfAvailable();
        if (syncProperties == null) {
            return;
        }
        String resolvedChannel = resolveChannel(channel, syncProperties);
        String topic = syncProperties.getDataSync().getTopicPrefix() + "/" + resolvedChannel;

        SyncClient syncClient = syncClientProvider.getIfAvailable();
        if (syncClient != null) {
            syncClient.send(topic, payload);
        }
    }

    /**
     * 广播消息到所有客户端
     *
     * @param channel 通道名称
     * @param payload 消息负载
     */
    public void broadcast(String channel, Object payload) {
        SyncProperties syncProperties = syncPropertiesProvider.getIfAvailable();
        if (syncProperties == null) {
            return;
        }
        String resolvedChannel = resolveChannel(channel, syncProperties);
        String topic = syncProperties.getDataSync().getTopicPrefix() + "/" + resolvedChannel;

        SyncServer syncServer = syncServerProvider.getIfAvailable();
        if (syncServer != null) {
            syncServer.publish(topic, payload);
        }
    }

        /**
         * 解析通道名称
         *
         * @param channel        原始通道名称
         * @param syncProperties 同步配置
         * @return 解析后的通道名称
         */
        private String resolveChannel(String channel, SyncProperties syncProperties) {
            if (channel == null || channel.isBlank()) {
                return syncProperties.getDataSync().getDefaultChannel();
            }
            return channel.trim();
        }
    }
}
