package com.chua.starter.strategy.service;

import com.chua.common.support.text.json.Json;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 策略配置同步服务
 * <p>
 * 使用Redis Pub/Sub实现多节点配置同步。
 * 当配置变更时，广播通知所有节点刷新本地缓存。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyConfigSyncService {

    private static final String SYNC_CHANNEL = "strategy:config:sync";

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private RedisMessageListenerContainer redisMessageListenerContainer;

    /**
     * 配置类型对应的刷新回调
     */
    private final Map<ConfigType, Consumer<SyncMessage>> refreshCallbacks = new ConcurrentHashMap<>();

    /**
     * 配置类型枚举
     */
    public enum ConfigType {
        /** 限流配置 */
        RATE_LIMIT,
        /** 熔断配置 */
        CIRCUIT_BREAKER,
        /** 防抖配置 */
        DEBOUNCE,
        /** 缓存配置 */
        CACHE,
        /** IP黑白名单 */
        IP_ACCESS,
        /** 调度任务 */
        SCHEDULER,
        /** 全部配置 */
        ALL
    }

    /**
     * 同步消息
     */
    @Data
    public static class SyncMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /** 配置类型 */
        private ConfigType configType;
        /** 操作类型：CREATE, UPDATE, DELETE, REFRESH */
        private String operation;
        /** 配置ID（可选） */
        private Long configId;
        /** 发送节点标识 */
        private String sourceNode;
        /** 时间戳 */
        private long timestamp;
        /** 额外数据 */
        private String extra;

        public SyncMessage() {
            this.timestamp = System.currentTimeMillis();
            this.sourceNode = getNodeId();
        }

        public SyncMessage(ConfigType configType, String operation) {
            this();
            this.configType = configType;
            this.operation = operation;
        }

        public SyncMessage(ConfigType configType, String operation, Long configId) {
            this(configType, operation);
            this.configId = configId;
        }

        private static String getNodeId() {
            return System.getProperty("spring.application.name", "unknown") + 
                   ":" + ProcessHandle.current().pid();
        }
    }

    @PostConstruct
    public void init() {
        if (redisMessageListenerContainer != null) {
            // 订阅配置同步频道
            redisMessageListenerContainer.addMessageListener(
                    (message, pattern) -> handleMessage(new String(message.getBody())),
                    new ChannelTopic(SYNC_CHANNEL)
            );
            log.info("[STRATEGY] 配置同步服务已启动，订阅频道: {}", SYNC_CHANNEL);
        } else {
            log.warn("[STRATEGY] Redis未配置，配置同步服务不可用");
        }
    }

    /**
     * 注册配置刷新回调
     *
     * @param configType 配置类型
     * @param callback   刷新回调
     */
    public void registerRefreshCallback(ConfigType configType, Consumer<SyncMessage> callback) {
        refreshCallbacks.put(configType, callback);
        log.debug("注册配置刷新回调: {}", configType);
    }

    /**
     * 发布配置变更通知
     *
     * @param message 同步消息
     */
    public void publish(SyncMessage message) {
        if (stringRedisTemplate == null) {
            log.warn("Redis未配置，无法发布配置变更通知");
            return;
        }

        try {
            String json = Json.toJson(message);
            stringRedisTemplate.convertAndSend(SYNC_CHANNEL, json);
            log.info("[STRATEGY] 发布配置变更通知: type={}, operation={}", 
                    message.getConfigType(), message.getOperation());
        } catch (Exception e) {
            log.error("[STRATEGY] 发布配置变更通知失败", e);
        }
    }

    /**
     * 发布限流配置变更
     *
     * @param operation 操作类型
     * @param configId  配置ID
     */
    public void publishRateLimitChange(String operation, Long configId) {
        publish(new SyncMessage(ConfigType.RATE_LIMIT, operation, configId));
    }

    /**
     * 发布熔断配置变更
     *
     * @param operation 操作类型
     * @param configId  配置ID
     */
    public void publishCircuitBreakerChange(String operation, Long configId) {
        publish(new SyncMessage(ConfigType.CIRCUIT_BREAKER, operation, configId));
    }

    /**
     * 发布防抖配置变更
     *
     * @param operation 操作类型
     * @param configId  配置ID
     */
    public void publishDebounceChange(String operation, Long configId) {
        publish(new SyncMessage(ConfigType.DEBOUNCE, operation, configId));
    }

    /**
     * 发布缓存配置变更
     *
     * @param operation 操作类型
     * @param configId  配置ID
     */
    public void publishCacheChange(String operation, Long configId) {
        publish(new SyncMessage(ConfigType.CACHE, operation, configId));
    }

    /**
     * 发布刷新全部配置
     */
    public void publishRefreshAll() {
        publish(new SyncMessage(ConfigType.ALL, "REFRESH"));
    }

    /**
     * 处理收到的同步消息
     *
     * @param messageJson 消息JSON
     */
    private void handleMessage(String messageJson) {
        try {
            SyncMessage message = Json.fromJson(messageJson, SyncMessage.class);
            
            // 忽略自己发送的消息（避免循环）
            String currentNode = SyncMessage.getNodeId();
            if (currentNode.equals(message.getSourceNode())) {
                log.debug("忽略本节点发送的配置同步消息");
                return;
            }

            log.info("[STRATEGY] 收到配置同步消息: type={}, operation={}, from={}", 
                    message.getConfigType(), message.getOperation(), message.getSourceNode());

            // 执行刷新回调
            if (message.getConfigType() == ConfigType.ALL) {
                // 刷新全部配置
                refreshCallbacks.values().forEach(callback -> {
                    try {
                        callback.accept(message);
                    } catch (Exception e) {
                        log.error("执行配置刷新回调失败", e);
                    }
                });
            } else {
                // 刷新指定类型配置
                Consumer<SyncMessage> callback = refreshCallbacks.get(message.getConfigType());
                if (callback != null) {
                    try {
                        callback.accept(message);
                    } catch (Exception e) {
                        log.error("执行配置刷新回调失败: {}", message.getConfigType(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[STRATEGY] 处理配置同步消息失败: {}", messageJson, e);
        }
    }

    /**
     * 检查同步服务是否可用
     *
     * @return true-可用，false-不可用
     */
    public boolean isAvailable() {
        return stringRedisTemplate != null && redisMessageListenerContainer != null;
    }
}
