package com.chua.starter.plugin.service;

import com.chua.starter.plugin.entity.PluginXssConfig;
import com.chua.starter.plugin.store.PersistenceStore;
import com.chua.starter.plugin.store.QueryCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * XSS配置管理服务
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XssConfigService {

    private final PersistenceStore<PluginXssConfig, Long> xssConfigStore;
    private final XssProtectionService xssProtectionService;

    /**
     * 保存XSS配置
     * 
     * @param config XSS配置
     * @return 保存后的配置
     */
    @Transactional
    public PluginXssConfig saveConfig(PluginXssConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("XSS config cannot be null");
        }

        if (!config.isValid()) {
            throw new IllegalArgumentException("Invalid XSS config");
        }

        // 设置创建/更新信息
        if (config.getPluginXssConfigId() == null) {
            config.setPluginXssConfigCreatedBy("SYSTEM");
            config.setPluginXssConfigUpdatedBy("SYSTEM");
            config.onCreate();
        } else {
            config.setPluginXssConfigUpdatedBy("SYSTEM");
            config.onUpdate();
        }

        try {
            PluginXssConfig savedConfig = xssConfigStore.save(config);
            
            // 重新加载配置缓存
            xssProtectionService.loadAllConfigsToCache();
            
            log.info("Saved XSS config: {}", savedConfig.getUniqueKey());
            return savedConfig;
        } catch (Exception e) {
            log.error("Failed to save XSS config: {}", config.getUniqueKey(), e);
            throw e;
        }
    }

    /**
     * 根据配置名称获取配置
     * 
     * @param configName 配置名称
     * @return 配置信息
     */
    public Optional<PluginXssConfig> getConfigByName(String configName) {
        if (configName == null || configName.trim().isEmpty()) {
            return Optional.empty();
        }

        QueryCondition condition = QueryCondition.empty().eq("pluginXssConfigName", configName);
        List<PluginXssConfig> configs = xssConfigStore.findByCondition(condition);
        
        return configs.isEmpty() ? Optional.empty() : Optional.of(configs.get(0));
    }

    /**
     * 根据ID获取配置
     * 
     * @param id 配置ID
     * @return 配置信息
     */
    public Optional<PluginXssConfig> getConfigById(Long id) {
        return xssConfigStore.findById(id);
    }

    /**
     * 删除配置
     * 
     * @param configName 配置名称
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteConfig(String configName) {
        try {
            QueryCondition condition = QueryCondition.empty().eq("pluginXssConfigName", configName);
            List<PluginXssConfig> configs = xssConfigStore.findByCondition(condition);
            
            if (!configs.isEmpty()) {
                PluginXssConfig config = configs.get(0);
                xssConfigStore.delete(config);
                
                // 重新加载配置缓存
                xssProtectionService.loadAllConfigsToCache();
                
                log.info("Deleted XSS config: {}", configName);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to delete XSS config: {}", configName, e);
            return false;
        }
    }

    /**
     * 启用或禁用配置
     * 
     * @param configName 配置名称
     * @param enabled 是否启用
     * @return 是否操作成功
     */
    @Transactional
    public boolean setEnabled(String configName, boolean enabled) {
        try {
            QueryCondition condition = QueryCondition.empty().eq("pluginXssConfigName", configName);
            List<PluginXssConfig> configs = xssConfigStore.findByCondition(condition);
            
            if (!configs.isEmpty()) {
                PluginXssConfig config = configs.get(0);
                config.setPluginXssConfigEnabled(enabled);
                config.setPluginXssConfigUpdatedBy("SYSTEM");
                config.onUpdate();
                
                xssConfigStore.save(config);
                
                // 重新加载配置缓存
                xssProtectionService.loadAllConfigsToCache();
                
                log.info("{} XSS config: {}", enabled ? "Enabled" : "Disabled", configName);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to set enabled status for XSS config: {}", configName, e);
            return false;
        }
    }

    /**
     * 更新防护模式
     * 
     * @param configName 配置名称
     * @param protectionMode 防护模式
     * @return 是否操作成功
     */
    @Transactional
    public boolean updateProtectionMode(String configName, PluginXssConfig.ProtectionMode protectionMode) {
        if (protectionMode == null) {
            throw new IllegalArgumentException("Protection mode cannot be null");
        }

        try {
            QueryCondition condition = QueryCondition.empty().eq("pluginXssConfigName", configName);
            List<PluginXssConfig> configs = xssConfigStore.findByCondition(condition);
            
            if (!configs.isEmpty()) {
                PluginXssConfig config = configs.get(0);
                config.setPluginXssConfigProtectionMode(protectionMode);
                config.setPluginXssConfigUpdatedBy("SYSTEM");
                config.onUpdate();
                
                xssConfigStore.save(config);
                
                // 重新加载配置缓存
                xssProtectionService.loadAllConfigsToCache();
                
                log.info("Updated protection mode for XSS config: {} -> {}", configName, protectionMode);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to update protection mode for XSS config: {}", configName, e);
            return false;
        }
    }

    /**
     * 获取所有配置
     * 
     * @return 所有配置列表
     */
    public List<PluginXssConfig> getAllConfigs() {
        return xssConfigStore.findAll();
    }

    /**
     * 获取所有启用的配置
     * 
     * @return 启用的配置列表
     */
    public List<PluginXssConfig> getEnabledConfigs() {
        QueryCondition condition = QueryCondition.empty().eq("pluginXssConfigEnabled", true);
        return xssConfigStore.findByCondition(condition);
    }

    /**
     * 批量保存配置
     * 
     * @param configs 配置列表
     * @return 保存后的配置列表
     */
    @Transactional
    public List<PluginXssConfig> batchSaveConfigs(List<PluginXssConfig> configs) {
        try {
            // 设置创建/更新信息
            LocalDateTime now = LocalDateTime.now();
            for (PluginXssConfig config : configs) {
                if (config.getPluginXssConfigId() == null) {
                    config.setPluginXssConfigCreatedBy("SYSTEM");
                    config.setPluginXssConfigCreatedTime(now);
                }
                config.setPluginXssConfigUpdatedBy("SYSTEM");
                config.setPluginXssConfigUpdatedTime(now);
            }
            
            List<PluginXssConfig> savedConfigs = xssConfigStore.saveAll(configs);
            
            // 重新加载配置缓存
            xssProtectionService.loadAllConfigsToCache();
            
            log.info("Batch saved {} XSS configs", savedConfigs.size());
            return savedConfigs;
        } catch (Exception e) {
            log.error("Failed to batch save XSS configs", e);
            throw e;
        }
    }

    /**
     * 创建默认配置
     * 
     * @return 默认配置
     */
    @Transactional
    public PluginXssConfig createDefaultConfig() {
        // 检查是否已存在默认配置
        Optional<PluginXssConfig> existingConfig = getConfigByName("default");
        if (existingConfig.isPresent()) {
            return existingConfig.get();
        }
        
        // 创建默认配置
        PluginXssConfig defaultConfig = PluginXssConfig.createDefault();
        return saveConfig(defaultConfig);
    }

    /**
     * 重新加载配置缓存
     */
    public void reloadConfigCache() {
        xssProtectionService.loadAllConfigsToCache();
        log.info("Reloaded XSS config cache");
    }

    /**
     * 检查配置名称是否已存在
     * 
     * @param configName 配置名称
     * @return 是否已存在
     */
    public boolean isConfigNameExists(String configName) {
        return getConfigByName(configName).isPresent();
    }

    /**
     * 获取配置统计信息
     * 
     * @return 统计信息
     */
    public ConfigStatistics getConfigStatistics() {
        List<PluginXssConfig> allConfigs = getAllConfigs();
        long enabledCount = allConfigs.stream()
            .filter(config -> config.getPluginXssConfigEnabled())
            .count();
        
        return new ConfigStatistics(allConfigs.size(), (int) enabledCount);
    }

    /**
     * 配置统计信息
     */
    public static class ConfigStatistics {
        private final int totalCount;
        private final int enabledCount;

        public ConfigStatistics(int totalCount, int enabledCount) {
            this.totalCount = totalCount;
            this.enabledCount = enabledCount;
        }

        public int getTotalCount() { return totalCount; }
        public int getEnabledCount() { return enabledCount; }
        public int getDisabledCount() { return totalCount - enabledCount; }
    }
}
