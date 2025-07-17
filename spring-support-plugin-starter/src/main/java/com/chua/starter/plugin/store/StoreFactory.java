package com.chua.starter.plugin.store;

import com.chua.starter.plugin.store.impl.MemoryPersistenceStore;
import com.chua.starter.plugin.store.impl.SqlitePersistenceStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 存储工厂
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Component
public class StoreFactory {

    @Value("${plugin.store.type:MEMORY}")
    private String storeType;

    @Value("${plugin.sqlite.database-path:plugin.db}")
    private String databasePath;

    /**
     * 创建持久化存储
     * 
     * @param entityClass 实体类
     * @param tableName   表名
     * @return 持久化存储
     */
    public <T, ID> PersistenceStore<T, ID> createStore(Class<T> entityClass, String tableName) {
        PersistenceStore.StoreType type;
        try {
            type = PersistenceStore.StoreType.valueOf(storeType.toUpperCase());
        } catch (Exception e) {
            log.warn("Invalid store type: {}, using MEMORY as default", storeType);
            type = PersistenceStore.StoreType.MEMORY;
        }

        PersistenceStore<T, ID> store;
        switch (type) {
        case SQLITE:
            store = new SqlitePersistenceStore<>(databasePath, entityClass, tableName);
            break;
        case MEMORY:
        default:
            store = new MemoryPersistenceStore<>(entityClass);
            break;
        }

        // 初始化存储
        store.initialize();

        log.info("Created {} store for entity: {}", type, entityClass.getSimpleName());
        return store;
    }

    /**
     * 创建限流配置存储
     * 
     * @return 限流配置存储
     */
    public PersistenceStore<com.chua.starter.plugin.entity.RateLimitConfig, Long> createRateLimitConfigStore() {
        return createStore(com.chua.starter.plugin.entity.RateLimitConfig.class, "rate_limit_config");
    }

    /**
     * 创建黑白名单存储
     *
     * @return 黑白名单存储
     */
    public PersistenceStore<com.chua.starter.plugin.entity.BlackWhiteList, Long> createBlackWhiteListStore() {
        return createStore(com.chua.starter.plugin.entity.BlackWhiteList.class, "black_white_list");
    }

    /**
     * 创建XSS配置存储
     *
     * @return XSS配置存储
     */
    public PersistenceStore<com.chua.starter.plugin.entity.PluginXssConfig, Long> createXssConfigStore() {
        return createStore(com.chua.starter.plugin.entity.PluginXssConfig.class, "plugin_xss_config");
    }

    /**
     * 创建XSS攻击日志存储
     *
     * @return XSS攻击日志存储
     */
    public PersistenceStore<com.chua.starter.plugin.entity.PluginXssAttackLog, Long> createXssAttackLogStore() {
        return createStore(com.chua.starter.plugin.entity.PluginXssAttackLog.class, "plugin_xss_attack_log");
    }

    /**
     * 创建节点日志配置存储
     *
     * @return 节点日志配置存储
     */
    public PersistenceStore<com.chua.starter.plugin.entity.PluginNodeLoggerConfig, Long> createNodeLoggerConfigStore() {
        return createStore(com.chua.starter.plugin.entity.PluginNodeLoggerConfig.class, "plugin_node_logger_config");
    }
}
