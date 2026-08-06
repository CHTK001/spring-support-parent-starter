package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitorConfig;
import com.chua.starter.monitor.support.mapper.MsMonitorConfigMapper;
import com.chua.starter.monitor.support.service.MonitorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorConfigServiceImpl implements MonitorConfigService {

    private final MsMonitorConfigMapper configMapper;

    @Override
    @Cacheable(cacheNames = "monitorConfig", key = "'monitor.config:key:' + #key")
    public ReturnResult<MsMonitorConfig> findByKey(String key) {
        try {
            LambdaQueryWrapper<MsMonitorConfig> w = new LambdaQueryWrapper<>();
            w.eq(MsMonitorConfig::getMonitorConfigKey, key);
            return ReturnResult.ok(configMapper.selectOne(w));
        } catch (Exception e) { return ReturnResult.error(e.getMessage()); }
    }

    @Override public String getString(String key) { return getString(key, ""); }

    @Override
    public String getString(String key, String defaultValue) {
        Optional<MsMonitorConfig> opt = Optional.ofNullable(findByKey(key).getData());
        return opt.map(MsMonitorConfig::getMonitorConfigValue).orElse(defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        try { return Integer.parseInt(getString(key)); } catch (NumberFormatException e) { return defaultValue; }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        try { return Long.parseLong(getString(key)); } catch (NumberFormatException e) { return defaultValue; }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String val = getString(key);
        return val == null || val.isEmpty() ? defaultValue : Boolean.parseBoolean(val);
    }

    @Override
    @Cacheable(cacheNames = "monitorConfig", key = "'monitor.config:all'")
    public ReturnResult<Map<String, String>> getAll() {
        try {
            Map<String, String> map = new HashMap<>();
            for (MsMonitorConfig c : listAll()) map.put(c.getMonitorConfigKey(), c.getMonitorConfigValue());
            return ReturnResult.ok(map);
        } catch (Exception e) { return ReturnResult.error(e.getMessage()); }
    }

    @Override
    public List<MsMonitorConfig> listAll() { return configMapper.selectList(null); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorConfig")
    public ReturnResult<MsMonitorConfig> upsert(MsMonitorConfig entity) {
        try {
            ReturnResult<MsMonitorConfig> existing = findByKey(entity.getMonitorConfigKey());
            if (existing.getData() != null) {
                MsMonitorConfig old = existing.getData();
                old.setMonitorConfigValue(entity.getMonitorConfigValue());
                old.setMonitorConfigDesc(entity.getMonitorConfigDesc());
                configMapper.updateById(old);
                return ReturnResult.ok(old);
            }
            configMapper.insert(entity);
            return ReturnResult.ok(entity);
        } catch (Exception e) { return ReturnResult.error(e.getMessage()); }
    }
}