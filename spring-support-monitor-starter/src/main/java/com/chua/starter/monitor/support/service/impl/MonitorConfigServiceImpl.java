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
/**
 * 监控配置服务实现。
 *
 * <p>按 key 读写监控配置项，读取带缓存、写入后按需失效，避免频繁打库。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorConfigServiceImpl implements MonitorConfigService {

    /**
     * 配置映射器
    */
    private final MsMonitorConfigMapper configMapper;

    @Override
    @Cacheable(cacheNames = "monitorConfig", key = "'monitor.config:key:' + #key")
    /**
     * 查找by键
     *
     * @param key 键
     * @return findby键的结果
     */
    public ReturnResult<MsMonitorConfig> findByKey(String key) {
        try {
            LambdaQueryWrapper<MsMonitorConfig> w = new LambdaQueryWrapper<>();
            w.eq(MsMonitorConfig::getMonitorConfigKey, key);
            return ReturnResult.ok(configMapper.selectOne(w));
        } catch (Exception e) { return ReturnResult.error(e.getMessage()); }
    }

    @Override public String getString(String key) { return getString(key, ""); }

    @Override
    /**
     * 获取字符串
     * @param defaultValue 字符串
     * @param key 字符串
     * @return 返回值
    */
    public String getString(String key, String defaultValue) {
        Optional<MsMonitorConfig> opt = Optional.ofNullable(findByKey(key).getData());
        return opt.map(MsMonitorConfig::getMonitorConfigValue).orElse(defaultValue);
    }

    @Override
    /**
     * 获取Int
     * @param defaultValue 整数
     * @param key 字符串
     * @return 返回值
    */
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getString(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    /**
     * 获取Long
     * @param defaultValue 长整数
     * @param key 字符串
     * @return 返回值
    */
    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(getString(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    /**
     * 获取布尔值
     * @param defaultValue 布尔值
     * @param key 字符串
     * @return 返回值
    */
    public boolean getBoolean(String key, boolean defaultValue) {
        String val = getString(key);
        return val == null || val.isEmpty() ? defaultValue : Boolean.parseBoolean(val);
    }

    @Override
    @Cacheable(cacheNames = "monitorConfig", key = "'monitor.config:all'")
    /**
     * 获取全部
     *
     * @return 获取全部的结果
     */
    public ReturnResult<Map<String, String>> getAll() {
        try {
            Map<String, String> map = new HashMap<>();
            for (MsMonitorConfig c : listAll()) {
                map.put(c.getMonitorConfigKey(), c.getMonitorConfigValue());
            }
            return ReturnResult.ok(map);
        } catch (Exception e) { return ReturnResult.error(e.getMessage()); }
    }

    @Override
    /**
     * 列表全部
     * @return 返回值
    */
    public List<MsMonitorConfig> listAll() { return configMapper.selectList(null); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorConfig")
    /**
     * Upsert
     *
     * @param entity 实体
     * @return upsert的结果
     */
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitorConfig")
    /**
     * upsert全部
     *
     * @param entities 实体
     * @return upsert全部的结果
     */
    public ReturnResult<Integer> upsertAll(List<MsMonitorConfig> entities) {
        if (entities == null || entities.isEmpty()) {
            return ReturnResult.ok(0);
        }
        int count = 0;
        try {
            for (MsMonitorConfig entity : entities) {
                if (entity.getMonitorConfigKey() == null || entity.getMonitorConfigKey().isBlank()) {
                    continue;
                }
                ReturnResult<MsMonitorConfig> result = findByKey(entity.getMonitorConfigKey());
                if (result.getData() != null) {
                    MsMonitorConfig old = result.getData();
                    old.setMonitorConfigValue(entity.getMonitorConfigValue());
                    old.setMonitorConfigDesc(entity.getMonitorConfigDesc());
                    old.setMonitorConfigKey(entity.getMonitorConfigKey());
                    configMapper.updateById(old);
                } else {
                    configMapper.insert(entity);
                }
                count++;
            }
            return ReturnResult.ok(count);
        } catch (Exception e) {
            log.error("[monitor] 批量保存配置失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }
}
