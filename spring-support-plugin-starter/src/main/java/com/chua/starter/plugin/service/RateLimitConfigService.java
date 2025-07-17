package com.chua.starter.plugin.service;

import com.chua.starter.plugin.entity.RateLimitConfig;
import com.chua.starter.plugin.store.PersistenceStore;
import com.chua.starter.plugin.store.QueryCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 限流配置服务 负责限流配置的CRUD操作和缓存同步
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitConfigService implements ApplicationRunner {

    private final PersistenceStore<RateLimitConfig, Long> store;
    private final RateLimitCacheManager cacheManager;

    /**
     * 应用启动时加载所有配置到内存
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        loadAllConfigsToCache();
    }

    /**
     * 从数据库加载所有配置到内存缓存
     */
    public void loadAllConfigsToCache() {
        try {
            QueryCondition condition = QueryCondition.empty().eq("enabled", true);
            List<RateLimitConfig> configs = store.findByCondition(condition);
            log.info("Loading {} rate limit configs to cache", configs.size());

            // 清空现有缓存
            cacheManager.clearAll();

            // 加载配置到缓存
            for (RateLimitConfig config : configs) {
                cacheManager.putConfig(config);
            }

            log.info("Successfully loaded {} rate limit configs to cache", configs.size());
        } catch (Exception e) {
            log.error("Failed to load rate limit configs to cache", e);
        }
    }

    /**
     * 保存或更新配置
     * 
     * @param config 限流配置
     * @return 保存后的配置
     */
    @Transactional
    public RateLimitConfig saveConfig(RateLimitConfig config) {
        if (config == null || !config.isValid()) {
            throw new IllegalArgumentException("Invalid rate limit config");
        }

        try {
            // 保存到数据库
            RateLimitConfig savedConfig = store.save(config);

            // 更新内存缓存
            cacheManager.putConfig(savedConfig);

            log.info("Saved rate limit config: {} -> {}", savedConfig.getUniqueKey(), savedConfig.getQps());
            return savedConfig;
        } catch (Exception e) {
            log.error("Failed to save rate limit config: {}", config.getUniqueKey(), e);
            throw e;
        }
    }

    /**
     * 根据类型和键获取配置
     * 
     * @param limitType 限流类型
     * @param limitKey  限流键
     * @return 限流配置
     */
    public Optional<RateLimitConfig> getConfig(RateLimitConfig.LimitType limitType, String limitKey) {
        // 先从缓存获取
        RateLimitConfig cachedConfig = cacheManager.getConfig(limitType, limitKey);
        if (cachedConfig != null) {
            return Optional.of(cachedConfig);
        }

        // 缓存中没有，从数据库获取
        QueryCondition condition = QueryCondition.empty().eq("limitType", limitType).eq("limitKey", limitKey);
        List<RateLimitConfig> configs = store.findByCondition(condition);

        if (!configs.isEmpty()) {
            RateLimitConfig config = configs.get(0);
            if (config.getEnabled()) {
                // 加载到缓存
                cacheManager.putConfig(config);
                return Optional.of(config);
            }
        }

        return Optional.empty();
    }

    /**
     * 删除配置
     * 
     * @param limitType 限流类型
     * @param limitKey  限流键
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteConfig(RateLimitConfig.LimitType limitType, String limitKey) {
        try {
            QueryCondition condition = QueryCondition.empty().eq("limitType", limitType).eq("limitKey", limitKey);
            List<RateLimitConfig> configs = store.findByCondition(condition);

            if (!configs.isEmpty()) {
                RateLimitConfig config = configs.get(0);
                // 从数据库删除
                store.delete(config);

                // 从缓存删除
                cacheManager.removeConfig(limitType, limitKey);

                log.info("Deleted rate limit config: {}:{}", limitType, limitKey);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to delete rate limit config: {}:{}", limitType, limitKey, e);
            return false;
        }
    }

    /**
     * 更新配置的QPS
     * 
     * @param limitType 限流类型
     * @param limitKey  限流键
     * @param qps       新的QPS值
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateQps(RateLimitConfig.LimitType limitType, String limitKey, Integer qps) {
        if (qps == null || qps <= 0) {
            throw new IllegalArgumentException("QPS must be positive");
        }

        try {
            QueryCondition condition = QueryCondition.empty().eq("limitType", limitType).eq("limitKey", limitKey);
            List<RateLimitConfig> configs = store.findByCondition(condition);

            if (!configs.isEmpty()) {
                RateLimitConfig config = configs.get(0);
                config.setQps(qps);
                config.setBurstCapacity(qps * 2); // 更新突发容量

                // 保存到数据库
                store.save(config);

                // 更新缓存
                cacheManager.putConfig(config);

                log.info("Updated QPS for {}: {} -> {}", config.getUniqueKey(), config.getQps(), qps);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to update QPS for {}:{}", limitType, limitKey, e);
            return false;
        }
    }

    /**
     * 启用或禁用配置
     * 
     * @param limitType 限流类型
     * @param limitKey  限流键
     * @param enabled   是否启用
     * @return 是否操作成功
     */
    @Transactional
    public boolean setEnabled(RateLimitConfig.LimitType limitType, String limitKey, boolean enabled) {
        try {
            QueryCondition condition = QueryCondition.empty().eq("limitType", limitType).eq("limitKey", limitKey);
            List<RateLimitConfig> configs = store.findByCondition(condition);

            if (!configs.isEmpty()) {
                RateLimitConfig config = configs.get(0);
                config.setEnabled(enabled);

                // 保存到数据库
                store.save(config);

                // 更新缓存
                if (enabled) {
                    cacheManager.putConfig(config);
                } else {
                    cacheManager.removeConfig(limitType, limitKey);
                }

                log.info("{} rate limit config: {}", enabled ? "Enabled" : "Disabled", config.getUniqueKey());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to set enabled status for {}:{}", limitType, limitKey, e);
            return false;
        }
    }

    /**
     * 获取所有配置
     *
     * @return 所有配置列表
     */
    public List<RateLimitConfig> getAllConfigs() {
        return store.findAll();
    }

    /**
     * 获取所有启用的配置
     *
     * @return 启用的配置列表
     */
    public List<RateLimitConfig> getEnabledConfigs() {
        QueryCondition condition = QueryCondition.empty().eq("enabled", true);
        return store.findByCondition(condition);
    }

    /**
     * 根据类型获取配置
     *
     * @param limitType 限流类型
     * @return 配置列表
     */
    public List<RateLimitConfig> getConfigsByType(RateLimitConfig.LimitType limitType) {
        QueryCondition condition = QueryCondition.empty().eq("limitType", limitType).eq("enabled", true);
        return store.findByCondition(condition);
    }

    /**
     * 批量保存配置
     *
     * @param configs 配置列表
     * @return 保存后的配置列表
     */
    @Transactional
    public List<RateLimitConfig> batchSaveConfigs(List<RateLimitConfig> configs) {
        try {
            // 保存到数据库
            List<RateLimitConfig> savedConfigs = store.saveAll(configs);

            // 更新缓存
            for (RateLimitConfig config : savedConfigs) {
                if (config.getEnabled()) {
                    cacheManager.putConfig(config);
                }
            }

            log.info("Batch saved {} rate limit configs", savedConfigs.size());
            return savedConfigs;
        } catch (Exception e) {
            log.error("Failed to batch save rate limit configs", e);
            throw e;
        }
    }

    /**
     * 重新加载配置缓存
     */
    public void reloadCache() {
        log.info("Reloading rate limit config cache");
        loadAllConfigsToCache();
    }

    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息
     */
    public String getCacheStats() {
        return String.format("Config cache: %d, RateLimiter cache: %d", cacheManager.getConfigCount(),
                cacheManager.getRateLimiterCount());
    }
}
