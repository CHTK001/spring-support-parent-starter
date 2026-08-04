package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.monitor.support.entity.MsMonitorConfig;
import com.chua.starter.monitor.support.mapper.MsMonitorConfigMapper;
import com.chua.starter.monitor.support.service.MonitorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 监控全局配置 Service 实现
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorConfigServiceImpl implements MonitorConfigService {

    /**
     * 配置缓存名
     */
    public static final String CACHE = "monitorConfig";

    /**
     * Mapper
     */
    private final MsMonitorConfigMapper configMapper;

    @Override
    @Cacheable(cacheNames = CACHE, key = "'monitor.config:key:' + #key")
    public Optional<MsMonitorConfig> findByKey(String key) {
        LambdaQueryWrapper<MsMonitorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsMonitorConfig::getMonitorConfigKey, key);
        return Optional.ofNullable(configMapper.selectOne(wrapper));
    }

    @Override
    public String getString(String key) {
        return getString(key, "");
    }

    @Override
    public String getString(String key, String defaultValue) {
        return findByKey(key).map(MsMonitorConfig::getMonitorConfigValue).orElse(defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String value = getString(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("[MonitorConfig] 解析 int 失败 key={} value={}", key, value);
            return defaultValue;
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        String value = getString(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("[MonitorConfig] 解析 long 失败 key={} value={}", key, value);
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    @Cacheable(cacheNames = CACHE, key = "'monitor.config:all'")
    public Map<String, String> getAll() {
        Map<String, String> out = new HashMap<>();
        for (MsMonitorConfig c : listAll()) {
            out.put(c.getMonitorConfigKey(), c.getMonitorConfigValue());
        }
        return out;
    }

    @Override
    public List<MsMonitorConfig> listAll() {
        return configMapper.selectList(null);
    }

    @Override
    @CacheEvict(allEntries = true, cacheNames = CACHE)
    public MsMonitorConfig upsert(MsMonitorConfig entity) {
        Optional<MsMonitorConfig> existing = findByKey(entity.getMonitorConfigKey());
        if (existing.isPresent()) {
            MsMonitorConfig old = existing.get();
            old.setMonitorConfigValue(entity.getMonitorConfigValue());
            old.setMonitorConfigDesc(entity.getMonitorConfigDesc());
            configMapper.updateById(old);
            return old;
        }
        configMapper.insert(entity);
        return entity;
    }
}