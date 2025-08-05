package com.chua.starter.plugin.store;

import com.chua.starter.plugin.entity.PluginSecurityConfig;
import com.chua.starter.plugin.entity.PluginXssConfig;
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

    @Value("${plugin.store.type:SQLITE}")
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
     * 创建安全配置存储（统一的限流和黑白名单存储）
     *
     * @return 安全配置存储
     */
    public PersistenceStore<PluginSecurityConfig, Long> createSecurityConfigStore() {
        return createStore(PluginSecurityConfig.class, "security_config");
    }

    /**
     * 创建XSS配置存储
     *
     * @return XSS配置存储
     */
    public PersistenceStore<PluginXssConfig, Long> createXssConfigStore() {
        return createStore(PluginXssConfig.class, "plugin_xss_config");
    }


}
