package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.mapper.SysAiGlobalSettingMapper;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * AI 全局配置服务实现。
 * <p>
 * 基于 MyBatis Plus 操作 sys_ai_global_setting 表，
 * 支持按分组（group_name）和字段名（field_name）读写配置。
 * 提供缓存以降低高频读取（如认证守卫的 app_key_enabled 开关）带来的 DB 压力。
 *
 * @since 2026-08-03
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiGlobalSettingServiceImpl implements SysAiGlobalSettingService {

    /**
     * 全局设置 Mapper
     */
    private final SysAiGlobalSettingMapper sysAiGlobalSettingMapper;

    /**
     * Spring 应用事件发布器（配置变更后通知各能力模块热重建）
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 根据分组和字段名查询配置值（带缓存）。
     */
    @Override
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:global:setting:' + #group + ':' + #field", unless = "#result == null || #result.isEmpty()")
    /** 获取Value */
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    /** Replace */
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

        // 发布配置变更事件，触发对应能力模块热重建（如人脸识别实时生效）
        try {
            eventPublisher.publishEvent(new FaceSettingsChangedEvent(group));
        } catch (Exception e) {
            log.warn("[SysAiGlobalSetting] 发布配置变更事件失败: {}", e.getMessage());
        }
    }

    /**
     * 增量写入/更新单字段配置，不影响分组内其它已有字段。
     */
    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            allEntries = true)
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    /** UpsertValue */
    public void upsertValue(String group, String field, String value, String description) {
        if (group == null || group.isBlank() || field == null || field.isBlank()) {
            log.warn("[SysAiGlobalSetting] upsertValue 缺少 group/field 参数");
            return;
        }
        SysAiGlobalSetting existing = sysAiGlobalSettingMapper.selectOne(
                Wrappers.<SysAiGlobalSetting>lambdaQuery()
                        .eq(SysAiGlobalSetting::getSysAiGlobalSettingGroup, group)
                        .eq(SysAiGlobalSetting::getSysAiGlobalSettingField, field));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setSysAiGlobalSettingValue(value);
            if (description != null) {
                existing.setSysAiGlobalSettingDescription(description);
            }
            existing.setUpdateTime(now);
            sysAiGlobalSettingMapper.updateById(existing);
        } else {
            SysAiGlobalSetting setting = new SysAiGlobalSetting();
            setting.setSysAiGlobalSettingGroup(group);
            setting.setSysAiGlobalSettingField(field);
            setting.setSysAiGlobalSettingValue(value);
            setting.setSysAiGlobalSettingValueType("STRING");
            setting.setSysAiGlobalSettingDescription(description != null ? description : "");
            setting.setSysAiGlobalSettingSort(0);
            setting.setSysAiGlobalSettingStatus(1);
            setting.setCreateTime(now);
            setting.setUpdateTime(now);
            sysAiGlobalSettingMapper.insert(setting);
        }
        log.debug("[SysAiGlobalSetting] upsertValue: group={}, field={}, value={}", group, field, value);
    }

    /**
     * 完整写入/更新单字段配置（含类型与排序）。
     */
    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            allEntries = true)
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    /** UpsertField */
    public void upsertField(String group, String field, String value, String valueType, String description, int sort) {
        if (group == null || group.isBlank() || field == null || field.isBlank()) {
            log.warn("[SysAiGlobalSetting] upsertField 缺少 group/field 参数");
            return;
        }
        SysAiGlobalSetting existing = sysAiGlobalSettingMapper.selectOne(
                Wrappers.<SysAiGlobalSetting>lambdaQuery()
                        .eq(SysAiGlobalSetting::getSysAiGlobalSettingGroup, group)
                        .eq(SysAiGlobalSetting::getSysAiGlobalSettingField, field));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            if (description != null) {
                existing.setSysAiGlobalSettingDescription(description);
            }
            if (valueType != null) {
                existing.setSysAiGlobalSettingValueType(valueType);
            }
            existing.setSysAiGlobalSettingSort(sort);
            existing.setUpdateTime(now);
            sysAiGlobalSettingMapper.updateById(existing);
        } else {
            SysAiGlobalSetting setting = new SysAiGlobalSetting();
            setting.setSysAiGlobalSettingGroup(group);
            setting.setSysAiGlobalSettingField(field);
            setting.setSysAiGlobalSettingValue(value);
            setting.setSysAiGlobalSettingValueType(valueType != null ? valueType : "STRING");
            setting.setSysAiGlobalSettingDescription(description != null ? description : "");
            setting.setSysAiGlobalSettingSort(sort);
            setting.setSysAiGlobalSettingStatus(1);
            setting.setCreateTime(now);
            setting.setUpdateTime(now);
            sysAiGlobalSettingMapper.insert(setting);
        }
        log.debug("[SysAiGlobalSetting] upsertField: group={}, field={}, value={}, type={}, sort={}",
                group, field, value, valueType, sort);
    }
}
