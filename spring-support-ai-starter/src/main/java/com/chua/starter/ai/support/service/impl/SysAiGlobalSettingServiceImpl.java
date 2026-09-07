package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.ocr.OcrSettingsChangedEvent;
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
     *
     * @param group 配置分组名
     * @param field 配置字段名
     * @return 配置值，不存在或为空时返回 Optional.empty()
     */
    @Override
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:global:setting:' + #group + ':' + #field", unless = "#result == null || #result.isEmpty()")
    public Optional<String> getValue(String group, String field) {
        LambdaQueryWrapper<SysAiGlobalSetting> wrapper = Wrappers.<SysAiGlobalSetting>lambdaQuery()
                .eq(SysAiGlobalSetting::getSettingGroup, group)
                .eq(SysAiGlobalSetting::getField, field)
                .eq(SysAiGlobalSetting::getStatus, 1);
        SysAiGlobalSetting setting = sysAiGlobalSettingMapper.selectOne(wrapper);
        if (setting == null) {
            return Optional.empty();
        }
        String value = setting.getValue();
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * 读取布尔类型配置，查询失败或转换失败时返回默认值。
     *
     * @param group          配置分组名
     * @param field          配置字段名
     * @param defaultValue   默认值（查询失败或字段不存在时返回）
     * @return 解析后的布尔值
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
     *
     * @param group 配置分组名
     * @return 配置项列表，每项包含 group、field、value、valueType、description、sort 字段
     */
    @Override
    public List<Map<String, Object>> list(String group) {
        LambdaQueryWrapper<SysAiGlobalSetting> wrapper = Wrappers.<SysAiGlobalSetting>lambdaQuery()
                .eq(SysAiGlobalSetting::getSettingGroup, group)
                .eq(SysAiGlobalSetting::getStatus, 1)
                .orderByAsc(SysAiGlobalSetting::getSort);
        List<SysAiGlobalSetting> settings = sysAiGlobalSettingMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiGlobalSetting s : settings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("group", s.getSettingGroup());
            item.put("field", s.getField());
            item.put("value", s.getValue());
            item.put("valueType", s.getValueType());
            item.put("description", s.getDescription());
            item.put("sort", s.getSort());
            result.add(item);
        }
        return result;
    }

    /**
     * 批量替换指定分组的配置：先删后插（事务内），并清除缓存。
     *
     * @param request Map 对象，包含 group（分组名）和 fields（配置项列表）
     */
    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            allEntries = true)
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
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
                .eq(SysAiGlobalSetting::getSettingGroup, group);
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
            setting.setSettingGroup(group);
            setting.setField((String) field.getOrDefault("field", ""));
            setting.setValue(Objects.toString(field.get("value"), ""));
            setting.setValueType((String) field.getOrDefault("valueType", "STRING"));
            setting.setDescription((String) field.getOrDefault("description", ""));
            setting.setSort(sort++);
            setting.setStatus(1);
            setting.setCreateTime(LocalDateTime.now());
            setting.setUpdateTime(LocalDateTime.now());
            sysAiGlobalSettingMapper.insert(setting);
        }

        log.info("[SysAiGlobalSetting] 分组 {} 配置已更新，共 {} 项", group, fields.size());

        // 发布配置变更事件，触发对应能力模块热重建（如人脸识别实时生效）
        try {
            eventPublisher.publishEvent(new FaceSettingsChangedEvent(group));
            if ("ocr".equals(group)) {
                eventPublisher.publishEvent(new OcrSettingsChangedEvent(group));
            }
            if ("ocr".equals(group)) {
                eventPublisher.publishEvent(new OcrSettingsChangedEvent(group));
            }
        } catch (Exception e) {
            log.warn("[SysAiGlobalSetting] 发布配置变更事件失败: {}", e.getMessage());
        }
    }

    /**
     * 增量写入/更新单字段配置，不影响分组内其它已有字段。
     *
     * @param group       配置分组名
     * @param field       配置字段名
     * @param value       配置值
     * @param description 配置描述（可为 null）
     */
    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            allEntries = true)
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void upsertValue(String group, String field, String value, String description) {
        if (group == null || group.isBlank() || field == null || field.isBlank()) {
            log.warn("[SysAiGlobalSetting] upsertValue 缺少 group/field 参数");
            return;
        }
        SysAiGlobalSetting existing = sysAiGlobalSettingMapper.selectOne(
                Wrappers.<SysAiGlobalSetting>lambdaQuery()
                        .eq(SysAiGlobalSetting::getSettingGroup, group)
                        .eq(SysAiGlobalSetting::getField, field));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setValue(value);
            if (description != null) {
                existing.setDescription(description);
            }
            existing.setUpdateTime(now);
            sysAiGlobalSettingMapper.updateById(existing);
        } else {
            SysAiGlobalSetting setting = new SysAiGlobalSetting();
            setting.setSettingGroup(group);
            setting.setField(field);
            setting.setValue(value);
            setting.setValueType("STRING");
            setting.setDescription(description != null ? description : "");
            setting.setSort(0);
            setting.setStatus(1);
            setting.setCreateTime(now);
            setting.setUpdateTime(now);
            sysAiGlobalSettingMapper.insert(setting);
        }
        log.debug("[SysAiGlobalSetting] upsertValue: group={}, field={}, value={}", group, field, value);
    }

    /**
     * 完整写入/更新单字段配置（含类型与排序）。
     *
     * @param group       配置分组名
     * @param field       配置字段名
     * @param value       配置值
     * @param valueType   值类型（如 STRING、BOOLEAN）
     * @param description 配置描述（可为 null）
     * @param sort        排序值
     */
    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            allEntries = true)
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void upsertField(String group, String field, String value, String valueType, String description, int sort) {
        if (group == null || group.isBlank() || field == null || field.isBlank()) {
            log.warn("[SysAiGlobalSetting] upsertField 缺少 group/field 参数");
            return;
        }
        SysAiGlobalSetting existing = sysAiGlobalSettingMapper.selectOne(
                Wrappers.<SysAiGlobalSetting>lambdaQuery()
                        .eq(SysAiGlobalSetting::getSettingGroup, group)
                        .eq(SysAiGlobalSetting::getField, field));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setValue(value);
            if (description != null) {
                existing.setDescription(description);
            }
            if (valueType != null) {
                existing.setValueType(valueType);
            }
            existing.setSort(sort);
            existing.setUpdateTime(now);
            sysAiGlobalSettingMapper.updateById(existing);
        } else {
            SysAiGlobalSetting setting = new SysAiGlobalSetting();
            setting.setSettingGroup(group);
            setting.setField(field);
            setting.setValue(value);
            setting.setValueType(valueType != null ? valueType : "STRING");
            setting.setDescription(description != null ? description : "");
            setting.setSort(sort);
            setting.setStatus(1);
            setting.setCreateTime(now);
            setting.setUpdateTime(now);
            sysAiGlobalSettingMapper.insert(setting);
        }
        log.debug("[SysAiGlobalSetting] upsertField: group={}, field={}, value={}, type={}, sort={}",
                group, field, value, valueType, sort);
    }
}

