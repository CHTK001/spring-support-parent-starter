package com.chua.starter.plugin.service;

import com.chua.starter.plugin.entity.PluginBlackWhiteList;
import com.chua.starter.plugin.entity.PluginRateLimitConfig;
import com.chua.starter.plugin.entity.PluginSecurityConfig;
import com.chua.starter.plugin.store.PersistenceStore;
import com.chua.starter.plugin.store.QueryCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 安全配置服务
 * 统一管理限流配置和黑白名单配置，支持实时更新
 *
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitConfigService implements ApplicationRunner {

    private final PersistenceStore<PluginSecurityConfig, Long> securityConfigStore;
    private final RateLimitCacheManager rateLimitCacheManager;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 安全配置缓存
     */
    private final ConcurrentMap<String, PluginSecurityConfig> securityConfigCache = new ConcurrentHashMap<>();

    /**
     * 配置版本缓存
     */
    private final ConcurrentMap<String, Long> configVersionCache = new ConcurrentHashMap<>();

    /**
     * 应用启动时加载所有配置到内存
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Initializing Rate Limit Config Service...");
        loadAllConfigurations();
        log.info("Rate Limit Config Service initialized successfully");
    }

    /**
     * 加载所有配置到缓存
     */
    public void loadAllConfigurations() {
        try {
            QueryCondition condition = QueryCondition.empty().eq("enabled", true);
            List<PluginSecurityConfig> configs = securityConfigStore.findByCondition(condition);

            // 清空缓存
            rateLimitCacheManager.clearAll();
            securityConfigCache.clear();

            int rateLimitCount = 0;
            int blackWhiteListCount = 0;

            for (PluginSecurityConfig config : configs) {
                // 添加到统一缓存
                securityConfigCache.put(config.getUniqueKey(), config);

                // 如果是限流配置，也添加到限流缓存管理器
                if (config.getConfigType() == PluginSecurityConfig.ConfigType.RATE_LIMIT) {
                    // 转换为旧的限流配置格式以兼容现有的缓存管理器
                    rateLimitCacheManager.putConfig(convertToRateLimitConfig(config));
                    rateLimitCount++;
                } else {
                    blackWhiteListCount++;
                }
            }

            log.info("Loaded {} rate limit configs and {} black/white list configs",
                    rateLimitCount, blackWhiteListCount);
        } catch (Exception e) {
            log.error("Failed to load security configs", e);
        }
    }

    /**
     * 转换为旧的限流配置格式（用于兼容现有缓存管理器）
     */
    private PluginRateLimitConfig convertToRateLimitConfig(PluginSecurityConfig securityConfig) {
        PluginRateLimitConfig rateLimitConfig = new PluginRateLimitConfig();
        rateLimitConfig.setId(securityConfig.getId());
        rateLimitConfig.setLimitType(PluginRateLimitConfig.LimitType.valueOf(securityConfig.getLimitType().name()));
        rateLimitConfig.setLimitKey(securityConfig.getConfigKey());
        rateLimitConfig.setQps(securityConfig.getQps());
        rateLimitConfig.setBurstCapacity(securityConfig.getBurstCapacity());
        rateLimitConfig.setAlgorithmType(PluginRateLimitConfig.AlgorithmType.valueOf(securityConfig.getAlgorithmType().name()));
        rateLimitConfig.setOverflowStrategy(PluginRateLimitConfig.OverflowStrategy.valueOf(securityConfig.getOverflowStrategy().name()));
        rateLimitConfig.setWindowSizeSeconds(securityConfig.getWindowSizeSeconds());
        rateLimitConfig.setEnabled(securityConfig.getEnabled());
        rateLimitConfig.setDescription(securityConfig.getDescription());
        rateLimitConfig.setCreatedTime(securityConfig.getCreatedTime());
        rateLimitConfig.setUpdatedTime(securityConfig.getUpdatedTime());
        rateLimitConfig.setCreatedBy(securityConfig.getCreatedBy());
        rateLimitConfig.setUpdatedBy(securityConfig.getUpdatedBy());
        return rateLimitConfig;
    }

    /**
     * 保存或更新安全配置
     *
     * @param config 安全配置
     * @return 保存后的配置
     */
    @Transactional
    public PluginSecurityConfig saveConfig(PluginSecurityConfig config) {
        if (config == null || !config.isValid()) {
            throw new IllegalArgumentException("Invalid security config");
        }

        try {
            // 保存到数据库
            PluginSecurityConfig savedConfig = securityConfigStore.save(config);

            // 更新内存缓存
            securityConfigCache.put(savedConfig.getUniqueKey(), savedConfig);

            // 如果是限流配置，也更新限流缓存管理器
            if (savedConfig.getConfigType() == PluginSecurityConfig.ConfigType.RATE_LIMIT) {
                rateLimitCacheManager.putConfig(convertToRateLimitConfig(savedConfig));
            }

            log.info("Saved security config: {} -> {}", savedConfig.getUniqueKey(),
                    savedConfig.getConfigType() == PluginSecurityConfig.ConfigType.RATE_LIMIT ?
                            savedConfig.getQps() + " QPS" : savedConfig.getMatchType());
            return savedConfig;
        } catch (Exception e) {
            log.error("Failed to save security config: {}", config.getUniqueKey(), e);
            throw e;
        }
    }

    /**
     * 保存限流配置（兼容旧接口）
     */
    @Transactional
    public PluginRateLimitConfig saveConfig(PluginRateLimitConfig config) {
        PluginSecurityConfig securityConfig = convertFromRateLimitConfig(config);
        PluginSecurityConfig savedConfig = saveConfig(securityConfig);
        return convertToRateLimitConfig(savedConfig);
    }

    /**
     * 从旧的限流配置转换为安全配置
     */
    private PluginSecurityConfig convertFromRateLimitConfig(PluginRateLimitConfig rateLimitConfig) {
        PluginSecurityConfig securityConfig = new PluginSecurityConfig();
        securityConfig.setId(rateLimitConfig.getId());
        securityConfig.setConfigType(PluginSecurityConfig.ConfigType.RATE_LIMIT);
        securityConfig.setLimitType(PluginSecurityConfig.LimitType.valueOf(rateLimitConfig.getLimitType().name()));
        securityConfig.setConfigKey(rateLimitConfig.getLimitKey());
        securityConfig.setQps(rateLimitConfig.getQps());
        securityConfig.setBurstCapacity(rateLimitConfig.getBurstCapacity());
        securityConfig.setAlgorithmType(PluginSecurityConfig.AlgorithmType.valueOf(rateLimitConfig.getAlgorithmType().name()));
        securityConfig.setOverflowStrategy(PluginSecurityConfig.OverflowStrategy.valueOf(rateLimitConfig.getOverflowStrategy().name()));
        securityConfig.setWindowSizeSeconds(rateLimitConfig.getWindowSizeSeconds());
        securityConfig.setEnabled(rateLimitConfig.getEnabled());
        securityConfig.setDescription(rateLimitConfig.getDescription());
        securityConfig.setCreatedTime(rateLimitConfig.getCreatedTime());
        securityConfig.setUpdatedTime(rateLimitConfig.getUpdatedTime());
        securityConfig.setCreatedBy(rateLimitConfig.getCreatedBy());
        securityConfig.setUpdatedBy(rateLimitConfig.getUpdatedBy());
        return securityConfig;
    }

    /**
     * 根据类型和键获取配置
     *
     * @param limitType 限流类型
     * @param limitKey  限流键
     * @return 限流配置
     */
    public Optional<PluginRateLimitConfig> getConfig(PluginRateLimitConfig.LimitType limitType, String limitKey) {
        // 先从缓存获取
        PluginRateLimitConfig cachedConfig = rateLimitCacheManager.getConfig(limitType, limitKey);
        if (cachedConfig != null) {
            return Optional.of(cachedConfig);
        }

        // 从统一配置中查找
        String uniqueKey = limitType + ":" + limitKey;
        PluginSecurityConfig securityConfig = securityConfigCache.get(uniqueKey);

        if (securityConfig != null && securityConfig.getConfigType() == PluginSecurityConfig.ConfigType.RATE_LIMIT) {
            PluginRateLimitConfig rateLimitConfig = convertToRateLimitConfig(securityConfig);
            rateLimitCacheManager.putConfig(rateLimitConfig);
            return Optional.of(rateLimitConfig);
        }

        // 如果缓存中没有，从数据库查找
        QueryCondition condition = QueryCondition.empty()
                .eq("configType", PluginSecurityConfig.ConfigType.RATE_LIMIT)
                .eq("limitType", PluginSecurityConfig.LimitType.valueOf(limitType.name()))
                .eq("configKey", limitKey)
                .eq("enabled", true);

        List<PluginSecurityConfig> configs = securityConfigStore.findByCondition(condition);
        if (!configs.isEmpty()) {
            PluginSecurityConfig config = configs.get(0);
            securityConfigCache.put(config.getUniqueKey(), config);
            PluginRateLimitConfig rateLimitConfig = convertToRateLimitConfig(config);
            rateLimitCacheManager.putConfig(rateLimitConfig);
            return Optional.of(rateLimitConfig);
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
    public boolean deleteConfig(PluginRateLimitConfig.LimitType limitType, String limitKey) {
        try {
            String uniqueKey = limitType + ":" + limitKey;

            // 从统一配置中查找并删除
            QueryCondition condition = QueryCondition.empty()
                    .eq("configType", PluginSecurityConfig.ConfigType.RATE_LIMIT)
                    .eq("limitType", PluginSecurityConfig.LimitType.valueOf(limitType.name()))
                    .eq("configKey", limitKey);

            List<PluginSecurityConfig> configs = securityConfigStore.findByCondition(condition);

            if (!configs.isEmpty()) {
                PluginSecurityConfig config = configs.get(0);
                // 从数据库删除
                securityConfigStore.delete(config);

                // 从缓存删除
                securityConfigCache.remove(uniqueKey);
                rateLimitCacheManager.removeConfig(limitType, limitKey);

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
    public boolean updateQps(PluginRateLimitConfig.LimitType limitType, String limitKey, Integer qps) {
        if (qps == null || qps <= 0) {
            throw new IllegalArgumentException("QPS must be positive");
        }

        try {
            String uniqueKey = limitType + ":" + limitKey;

            // 从统一配置中查找并更新
            QueryCondition condition = QueryCondition.empty()
                    .eq("configType", PluginSecurityConfig.ConfigType.RATE_LIMIT)
                    .eq("limitType", PluginSecurityConfig.LimitType.valueOf(limitType.name()))
                    .eq("configKey", limitKey);

            List<PluginSecurityConfig> configs = securityConfigStore.findByCondition(condition);

            if (!configs.isEmpty()) {
                PluginSecurityConfig config = configs.get(0);
                config.setQps(qps);
                config.setBurstCapacity(qps * 2); // 更新突发容量
                config.onUpdate();

                // 保存到数据库
                securityConfigStore.save(config);

                // 更新缓存
                securityConfigCache.put(uniqueKey, config);
                PluginRateLimitConfig rateLimitConfig = convertToRateLimitConfig(config);
                rateLimitCacheManager.putConfig(rateLimitConfig);

                log.info("Updated QPS for {}: {}", uniqueKey, qps);
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
    public boolean setEnabled(PluginRateLimitConfig.LimitType limitType, String limitKey, boolean enabled) {
        try {
            String uniqueKey = limitType + ":" + limitKey;

            // 从统一配置中查找并更新
            QueryCondition condition = QueryCondition.empty()
                    .eq("configType", PluginSecurityConfig.ConfigType.RATE_LIMIT)
                    .eq("limitType", PluginSecurityConfig.LimitType.valueOf(limitType.name()))
                    .eq("configKey", limitKey);

            List<PluginSecurityConfig> configs = securityConfigStore.findByCondition(condition);

            if (!configs.isEmpty()) {
                PluginSecurityConfig config = configs.get(0);
                config.setEnabled(enabled);
                config.onUpdate();

                // 保存到数据库
                securityConfigStore.save(config);

                // 更新缓存
                if (enabled) {
                    securityConfigCache.put(uniqueKey, config);
                    PluginRateLimitConfig rateLimitConfig = convertToRateLimitConfig(config);
                    rateLimitCacheManager.putConfig(rateLimitConfig);
                } else {
                    securityConfigCache.remove(uniqueKey);
                    rateLimitCacheManager.removeConfig(limitType, limitKey);
                }

                log.info("{} rate limit config: {}", enabled ? "Enabled" : "Disabled", uniqueKey);
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
    public List<PluginRateLimitConfig> getAllConfigs() {
        QueryCondition condition = QueryCondition.empty().eq("configType", PluginSecurityConfig.ConfigType.RATE_LIMIT);
        List<PluginSecurityConfig> securityConfigs = securityConfigStore.findByCondition(condition);
        return securityConfigs.stream()
                .map(this::convertToRateLimitConfig)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取所有启用的配置
     *
     * @return 启用的配置列表
     */
    public List<PluginRateLimitConfig> getEnabledConfigs() {
        QueryCondition condition = QueryCondition.empty()
                .eq("configType", PluginSecurityConfig.ConfigType.RATE_LIMIT)
                .eq("enabled", true);
        List<PluginSecurityConfig> securityConfigs = securityConfigStore.findByCondition(condition);
        return securityConfigs.stream()
                .map(this::convertToRateLimitConfig)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据类型获取配置
     *
     * @param limitType 限流类型
     * @return 配置列表
     */
    public List<PluginRateLimitConfig> getConfigsByType(PluginRateLimitConfig.LimitType limitType) {
        QueryCondition condition = QueryCondition.empty()
                .eq("configType", PluginSecurityConfig.ConfigType.RATE_LIMIT)
                .eq("limitType", PluginSecurityConfig.LimitType.valueOf(limitType.name()))
                .eq("enabled", true);
        List<PluginSecurityConfig> securityConfigs = securityConfigStore.findByCondition(condition);
        return securityConfigs.stream()
                .map(this::convertToRateLimitConfig)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 批量保存配置
     *
     * @param configs 配置列表
     * @return 保存后的配置列表
     */
    @Transactional
    public List<PluginRateLimitConfig> batchSaveConfigs(List<PluginRateLimitConfig> configs) {
        try {
            // 转换为安全配置并保存
            List<PluginSecurityConfig> securityConfigs = configs.stream()
                    .map(this::convertFromRateLimitConfig)
                    .collect(java.util.stream.Collectors.toList());

            List<PluginSecurityConfig> savedSecurityConfigs = securityConfigStore.saveAll(securityConfigs);

            // 更新缓存
            for (PluginSecurityConfig securityConfig : savedSecurityConfigs) {
                if (securityConfig.getEnabled()) {
                    securityConfigCache.put(securityConfig.getUniqueKey(), securityConfig);
                    PluginRateLimitConfig rateLimitConfig = convertToRateLimitConfig(securityConfig);
                    rateLimitCacheManager.putConfig(rateLimitConfig);
                }
            }

            // 转换回限流配置格式返回
            List<PluginRateLimitConfig> savedConfigs = savedSecurityConfigs.stream()
                    .map(this::convertToRateLimitConfig)
                    .collect(java.util.stream.Collectors.toList());

            log.info("Batch saved {} rate limit configs", savedConfigs.size());
            return savedConfigs;
        } catch (Exception e) {
            log.error("Failed to batch save rate limit configs", e);
            throw e;
        }
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息
     */
    public String getCacheStats() {
        return String.format("Config cache: %d, RateLimiter cache: %d", rateLimitCacheManager.getConfigCount(),
                rateLimitCacheManager.getRateLimiterCount());
    }

    // ==================== 黑白名单管理 ====================

    /**
     * 实时更新黑白名单配置
     */
    @Transactional
    public boolean updateBlackWhiteListConfig(PluginBlackWhiteList config) {
        try {
            // 转换为统一的安全配置
            PluginSecurityConfig securityConfig = convertFromBlackWhiteListConfig(config);
            PluginSecurityConfig savedConfig = securityConfigStore.save(securityConfig);

            // 更新缓存
            securityConfigCache.put(savedConfig.getUniqueKey(), savedConfig);

            updateConfigVersion("blackWhiteList:" + savedConfig.getUniqueKey());

            log.info("Updated black/white list config: {}", savedConfig.getUniqueKey());
            return true;
        } catch (Exception e) {
            log.error("Failed to update black/white list config", e);
            return false;
        }
    }

    /**
     * 从黑白名单配置转换为安全配置
     */
    private PluginSecurityConfig convertFromBlackWhiteListConfig(PluginBlackWhiteList blackWhiteListConfig) {
        PluginSecurityConfig securityConfig = new PluginSecurityConfig();
        securityConfig.setId(blackWhiteListConfig.getId());
        securityConfig.setConfigType(blackWhiteListConfig.getListType() == PluginBlackWhiteList.ListType.BLACKLIST ?
                PluginSecurityConfig.ConfigType.BLACKLIST : PluginSecurityConfig.ConfigType.WHITELIST);
        securityConfig.setConfigKey(blackWhiteListConfig.getListValue());
        securityConfig.setMatchType(PluginSecurityConfig.MatchType.valueOf(blackWhiteListConfig.getMatchType().name()));
        securityConfig.setPriority(blackWhiteListConfig.getPriority());
        securityConfig.setEnabled(blackWhiteListConfig.getEnabled());
        securityConfig.setDescription(blackWhiteListConfig.getDescription());
        securityConfig.setCreatedTime(blackWhiteListConfig.getCreatedTime());
        securityConfig.setUpdatedTime(blackWhiteListConfig.getUpdatedTime());
        securityConfig.setCreatedBy(blackWhiteListConfig.getCreatedBy());
        securityConfig.setUpdatedBy(blackWhiteListConfig.getUpdatedBy());
        securityConfig.setExpireTime(blackWhiteListConfig.getExpireTime());
        return securityConfig;
    }

    /**
     * 删除黑白名单配置
     */
    @Transactional
    public boolean deleteBlackWhiteListConfig(PluginBlackWhiteList.ListType listType, String listValue) {
        try {
            PluginSecurityConfig.ConfigType configType = listType == PluginBlackWhiteList.ListType.BLACKLIST ?
                    PluginSecurityConfig.ConfigType.BLACKLIST : PluginSecurityConfig.ConfigType.WHITELIST;

            QueryCondition condition = QueryCondition.empty()
                    .eq("configType", configType)
                    .eq("configKey", listValue);
            List<PluginSecurityConfig> configs = securityConfigStore.findByCondition(condition);

            if (!configs.isEmpty()) {
                PluginSecurityConfig config = configs.get(0);
                securityConfigStore.delete(config);
                securityConfigCache.remove(config.getUniqueKey());

                configVersionCache.remove("blackWhiteList:" + config.getUniqueKey());

                log.info("Deleted black/white list config: {}:{}", listType, listValue);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to delete black/white list config: {}:{}", listType, listValue, e);
            return false;
        }
    }

    /**
     * 检查是否在黑白名单中
     */
    public boolean isInBlackWhiteList(PluginBlackWhiteList.ListType listType, String value) {
        PluginSecurityConfig.ConfigType configType = listType == PluginBlackWhiteList.ListType.BLACKLIST ?
                PluginSecurityConfig.ConfigType.BLACKLIST : PluginSecurityConfig.ConfigType.WHITELIST;

        return securityConfigCache.values().stream()
                .filter(config -> config.getConfigType() == configType && config.getEnabled())
                .anyMatch(config -> config.matches(value));
    }

    /**
     * 获取所有黑白名单配置
     */
    public List<PluginBlackWhiteList> getAllBlackWhiteListConfigs() {
        QueryCondition condition = QueryCondition.empty()
                .in("configType", PluginSecurityConfig.ConfigType.BLACKLIST, PluginSecurityConfig.ConfigType.WHITELIST);
        List<PluginSecurityConfig> securityConfigs = securityConfigStore.findByCondition(condition);
        return securityConfigs.stream()
                .map(this::convertToBlackWhiteListConfig)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据类型获取黑白名单配置
     */
    public List<PluginBlackWhiteList> getBlackWhiteListConfigsByType(PluginBlackWhiteList.ListType listType) {
        PluginSecurityConfig.ConfigType configType = listType == PluginBlackWhiteList.ListType.BLACKLIST ?
                PluginSecurityConfig.ConfigType.BLACKLIST : PluginSecurityConfig.ConfigType.WHITELIST;

        QueryCondition condition = QueryCondition.empty()
                .eq("configType", configType)
                .eq("enabled", true);
        List<PluginSecurityConfig> securityConfigs = securityConfigStore.findByCondition(condition);
        return securityConfigs.stream()
                .map(this::convertToBlackWhiteListConfig)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 从安全配置转换为黑白名单配置
     */
    private PluginBlackWhiteList convertToBlackWhiteListConfig(PluginSecurityConfig securityConfig) {
        PluginBlackWhiteList blackWhiteListConfig = new PluginBlackWhiteList();
        blackWhiteListConfig.setId(securityConfig.getId());
        blackWhiteListConfig.setListType(securityConfig.getConfigType() == PluginSecurityConfig.ConfigType.BLACKLIST ?
                PluginBlackWhiteList.ListType.BLACKLIST : PluginBlackWhiteList.ListType.WHITELIST);
        blackWhiteListConfig.setListValue(securityConfig.getConfigKey());
        blackWhiteListConfig.setMatchType(PluginBlackWhiteList.MatchType.valueOf(securityConfig.getMatchType().name()));
        blackWhiteListConfig.setPriority(securityConfig.getPriority());
        blackWhiteListConfig.setEnabled(securityConfig.getEnabled());
        blackWhiteListConfig.setDescription(securityConfig.getDescription());
        blackWhiteListConfig.setCreatedTime(securityConfig.getCreatedTime());
        blackWhiteListConfig.setUpdatedTime(securityConfig.getUpdatedTime());
        blackWhiteListConfig.setCreatedBy(securityConfig.getCreatedBy());
        blackWhiteListConfig.setUpdatedBy(securityConfig.getUpdatedBy());
        blackWhiteListConfig.setExpireTime(securityConfig.getExpireTime());
        return blackWhiteListConfig;
    }

    // ==================== 统一管理方法 ====================

    /**
     * 获取所有安全配置统计
     */
    public Map<String, Object> getSecurityConfigStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        // 限流配置统计
        Map<String, Object> rateLimitStats = rateLimitCacheManager.getAllConfigs().entrySet().stream()
                .collect(ConcurrentHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().getQps()),
                        ConcurrentHashMap::putAll);
        stats.put("rateLimitConfigs", rateLimitStats);
        stats.put("rateLimitCount", rateLimitStats.size());

        // 黑白名单统计
        long blacklistCount = securityConfigCache.values().stream()
                .filter(config -> config.getConfigType() == PluginSecurityConfig.ConfigType.BLACKLIST)
                .count();
        long whitelistCount = securityConfigCache.values().stream()
                .filter(config -> config.getConfigType() == PluginSecurityConfig.ConfigType.WHITELIST)
                .count();

        stats.put("blacklistCount", blacklistCount);
        stats.put("whitelistCount", whitelistCount);
        stats.put("totalBlackWhiteListCount", blacklistCount + whitelistCount);

        return stats;
    }

    /**
     * 重新加载所有配置
     */
    public void reloadAllConfigurations() {
        log.info("Reloading all security configurations...");
        loadAllConfigurations();
        log.info("All security configurations reloaded successfully");
    }

    // ==================== 私有辅助方法 ====================

    private void updateConfigVersion(String configKey) {
        configVersionCache.put(configKey, System.currentTimeMillis());
    }
}
