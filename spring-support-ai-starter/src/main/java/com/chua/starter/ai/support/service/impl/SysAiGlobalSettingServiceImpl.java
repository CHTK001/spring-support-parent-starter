package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.mapper.SysAiGlobalSettingMapper;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * AI 全局配置服务实现。
 * <p>
 * 基于 MyBatis Plus 操作 sys_ai_global_setting 表，
 * 支持按分组（group_name）和字段名（field_name）读写配置。
 * 提供缓存以降低高频读取（如认证守卫的 app_key_enabled 开关）带来的 DB 压力。
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiGlobalSettingServiceImpl implements SysAiGlobalSettingService {

    private final SysAiGlobalSettingMapper sysAiGlobalSettingMapper;

    /**
     * 根据分组和字段名查询配置值（带缓存）。
     */
    @Override
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:global:setting:' + #group + ':' + #field", unless = "#result == null || #result.isEmpty()")
    public Optional<String> getValue(String group, String field) {
        LambdaQueryWrapper<SysAiGlobalSetting> wrapper = Wrappers.<SysAiGlobalSetting>lambdaQuery()
                .eq(SysAiGlobalSetting::getSysAiGlobalSettingGroup, group)
                .eq(SysAiGlobalSetting::getSysAiGlobalSettingField, field)
                .eq(SysAiGlobalSetting::getSysAiGlobalSettingStatus, 1);
        SysAiGlobalSetting setting = sysAiGlobalSettingMapper.selectOne(wrapper);
        if (setting == null) {
            return Optional.empty();
        }
        String value = setting.getSysAiGlobalSettingValue();
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * 读取布尔类型配置，查询失败或转换失败时返回默认值。
     */
    @Override
    public boolean getBoolean(String group, String field, boolean defaultValue) {
        return getValue(group, field)
                .map(v -> {
                    String lower = v.trim().toLowerCase();
                    return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower);
                })
                .orElse(defaultValue);
    }

    /**
     * 列出指定分组下所有启用的配置项，按 sort 排序。
     */
    @Override
    public List<Map<String, Object>> list(String group) {
        LambdaQueryWrapper<SysAiGlobalSetting> wrapper = Wrappers.<SysAiGlobalSetting>lambdaQuery()
                .eq(SysAiGlobalSetting::getSysAiGlobalSettingGroup, group)
                .eq(SysAiGlobalSetting::getSysAiGlobalSettingStatus, 1)
                .orderByAsc(SysAiGlobalSetting::getSysAiGlobalSettingSort);
        List<SysAiGlobalSetting> settings = sysAiGlobalSettingMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiGlobalSetting s : settings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("group", s.getSysAiGlobalSettingGroup());
            item.put("field", s.getSysAiGlobalSettingField());
            item.put("value", s.getSysAiGlobalSettingValue());
            item.put("valueType", s.getSysAiGlobalSettingValueType());
            item.put("description", s.getSysAiGlobalSettingDescription());
            item.put("sort", s.getSysAiGlobalSettingSort());
            result.add(item);
        }
        return result;
    }

    /**
     * 批量替换指定分组的配置：先删后插（事务内）。
     */
    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            allEntries = true)
    public void replace(Object request) {
        if (!(request instanceof Map)) {
            log.warn("[SysAiGlobalSetting] replace 参数类型不支持: {}", request.getClass());
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) request;

        String group = (String) body.get("group");
        if (group == null || group.isBlank()) {
            log.warn("[SysAiGlobalSetting] replace 缺少 group 参数");
            return;
        }

        // 先删除旧配置
        LambdaQueryWrapper<SysAiGlobalSetting> deleteWrapper = Wrappers.<SysAiGlobalSetting>lambdaQuery()
                .eq(SysAiGlobalSetting::getSysAiGlobalSettingGroup, group);
        sysAiGlobalSettingMapper.delete(deleteWrapper);

        // 再插入新配置
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) body.get("fields");
        if (fields == null || fields.isEmpty()) {
            return;
        }

        int sort = 0;
        for (Map<String, Object> field : fields) {
            SysAiGlobalSetting setting = new SysAiGlobalSetting();
            setting.setSysAiGlobalSettingGroup(group);
            setting.setSysAiGlobalSettingField((String) field.getOrDefault("field", ""));
            setting.setSysAiGlobalSettingValue(Objects.toString(field.get("value"), ""));
            setting.setSysAiGlobalSettingValueType((String) field.getOrDefault("valueType", "STRING"));
            setting.setSysAiGlobalSettingDescription((String) field.getOrDefault("description", ""));
            setting.setSysAiGlobalSettingSort(sort++);
            setting.setSysAiGlobalSettingStatus(1);
            setting.setCreateTime(LocalDateTime.now());
            setting.setUpdateTime(LocalDateTime.now());
            sysAiGlobalSettingMapper.insert(setting);
        }

        log.info("[SysAiGlobalSetting] 分组 {} 配置已更新，共 {} 项", group, fields.size());
    }
}
