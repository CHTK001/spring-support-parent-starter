package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.ocr.OcrSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.rag.RagSettingsChangedEvent;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.mapper.SysAiGlobalSettingMapper;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 * 基于 MyBatis Plus 操作 sys_AI_全局_setting 表，
 * 支持按分组（群体_名称）和字段名（字段_名称）读写配置。
 * 提供缓存以降低高频读取（如认证守卫的 app_Key_已启用 开关）带来的 DB 压力。
 *
 * @since 2026-08-03
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiGlobalSettingServiceImpl implements SysAiGlobalSettingService {

    /**
     * 全局设置 映射器
     */
    private final SysAiGlobalSettingMapper sysAiGlobalSettingMapper;

    /**
     * Spring 应用事件发布器（配置变更后通知各能力模块热重建）
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 永久 Redis 缓存管理器（与 {@code @Cacheable(cacheManager = REDIS_CACHE_ALWAYS)} 同一实例）
     */
    @Resource(name = REDIS_CACHE_ALWAYS)
    private CacheManager alwaysCacheManager;

    /**
     * 全局配置缓存键前缀，注解 SpEL 与手工失效共用，避免两处拼串漂移
     */
    private static final String CACHE_KEY_PREFIX = "sys:ai:global:setting:";

    /**
     * 拼装 {@link #getValue(String, String)} 的缓存键。
     *
     * @param group 配置分组名
     * @param field 配置字段名
     * @return 缓存键
     */
    private static String cacheKey(String group, String field) {
        return CACHE_KEY_PREFIX + group + ":" + field;
    }

    /**
     * 手工失效指定配置项的缓存。
     *
     * <p>写库后按 {@link #cacheKey(String, String)} 精确清除，与 {@link #getValue(String, String)}
     * 的 {@code @Cacheable} 键同源，避免两处拼串漂移。</p>
     *
     * @param group 配置分组名
     * @param field 配置字段名
     */
    private void evictCache(String group, String field) {
        if (group == null || field == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 提交前删键会被并发读把旧值重新回填，而该缓存项永不过期，因此推迟到提交后失效
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doEvict(group, field);
                }
            });
            return;
        }
        doEvict(group, field);
    }

    /**
     * 按缓存键删除缓存项。
     *
     * @param group 配置分组名
     * @param field 配置字段名
     */
    private void doEvict(String group, String field) {
        Cache cache = alwaysCacheManager.getCache(REDIS_CACHE_ALWAYS);
        if (cache != null) {
            cache.evict(cacheKey(group, field));
        }
    }

    /**
     * 根据分组和字段名查询配置值（带缓存）。
     *
     * @param group 配置分组名
     * @param field 配置字段名
     * @return 配置值，不存在或为空时返回 期权.空()
     */
    @Override
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'" + CACHE_KEY_PREFIX + "' + #group + ':' + #field",
            unless = "#result == null || #result.isEmpty()")
    /**
     * 获取值（带缓存）。
     * @param group 群体
     * @param field 字段
     * @return 获取值的结果
     */
    public Optional<String> getValue(String group, String field) {
        return getValueDirect(group, field);
    }

    /**
     * 不经缓存、直接查询数据库（Redis 不可达时由读取器回落调用）。
     *
     * @param group 配置分组名
     * @param field 配置字段名
     * @return 配置值，不存在或为空时返回 {@link Optional#empty()}
     */
    @Override
    public Optional<String> getValueDirect(String group, String field) {
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
     * 列出指定分组下所有启用的配置项，按 排序 排序。
     *
     * @param group 配置分组名
     * @return 配置项列表，每项包含 群体、字段、值、值类型、description、排序 字段
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
     * 批量替换指定分组的配置：先删后插（事务内），并按字段精确失效缓存。
     *
     * @param request 映射 对象，包含 群体（分组名）和 字段（配置项列表）
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    /**
     * 替换。
     * @param request 请求
     */
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

        // 先记下被删掉的字段：只有旧清单+新清单一起失效，才不会留下已删字段的陈旧缓存
        List<String> touched = new ArrayList<>();
        for (SysAiGlobalSetting old : sysAiGlobalSettingMapper.selectList(
                Wrappers.<SysAiGlobalSetting>lambdaQuery().eq(SysAiGlobalSetting::getSettingGroup, group))) {
            touched.add(old.getField());
        }

        // 先删除旧配置
        LambdaQueryWrapper<SysAiGlobalSetting> deleteWrapper = Wrappers.<SysAiGlobalSetting>lambdaQuery()
                .eq(SysAiGlobalSetting::getSettingGroup, group);
        sysAiGlobalSettingMapper.delete(deleteWrapper);

        // 再插入新配置
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) body.get("fields");
        if (fields != null) {
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
                touched.add(setting.getField());
            }
        }
        for (String field : touched) {
            evictCache(group, field);
        }

        log.info("[SysAiGlobalSetting] 分组 {} 配置已更新，共 {} 项", group, fields == null ? 0 : fields.size());

        // 发布配置变更事件，触发对应能力模块热重建（如人脸识别实时生效）
        publishSettingsChanged(group);
    }

    /**
     * 增量写入/更新单字段配置，不影响分组内其它已有字段。
     *
     * @param group       配置分组名
     * @param field       配置字段名
     * @param value       配置值
     * @param description 配置描述（可为 空）
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    /**
     * upsert值。
     * @param group 群体
     * @param field 字段
     * @param value 值
     * @param description description
     */
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
        evictCache(group, field);
        log.debug("[SysAiGlobalSetting] upsertValue: group={}, field={}, value={}", group, field, value);
        // 必须发变更事件：管理员在页面上改配置走的就是这个方法（逐字段 upsert），
        // 而热重建（人脸 / OCR / RAG）只监听事件。此前这里**只 evict 缓存、不发事件**，
        // 于是「在界面上改了人脸检测模型，必须重启才生效」—— 与「改配置即刻生效」的设计相悖。
        // 实测：PUT /v2/settings/face 后 /v2/face/models 仍报「人脸能力未启用」，重启才恢复。
        publishSettingsChanged(group);
    }

    /**
     * 发布某个分组的配置变更事件，触发对应能力模块热重建。
     *
     * <p>各能力模块自己按 {@code group} 过滤（例如 {@code FaceClientManager} 只处理 face），
     * 因此这里对所有分组统一发一次即可。</p>
     *
     * @param group 配置分组名
     */
    private void publishSettingsChanged(String group) {
        try {
            eventPublisher.publishEvent(new FaceSettingsChangedEvent(group));
            if ("ocr".equals(group)) {
                eventPublisher.publishEvent(new OcrSettingsChangedEvent(group));
            }
            if ("rag".equals(group)) {
                eventPublisher.publishEvent(new RagSettingsChangedEvent(group));
            }
        } catch (Exception e) {
            log.warn("[SysAiGlobalSetting] 发布配置变更事件失败: {}", e.getMessage());
        }
    }

    /**
     * 完整写入/更新单字段配置（含类型与排序）。
     *
     * @param group       配置分组名
     * @param field       配置字段名
     * @param value       配置值
     * @param valueType   值类型（如 字符串、布尔值）
     * @param description 配置描述（可为 空）
     * @param sort        排序值
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    /**
     * upsert字段。
     * @param group 群体
     * @param field 字段
     * @param value 值
     * @param valueType 值类型
     * @param description description
     * @param sort 排序
     */
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
        evictCache(group, field);
        log.debug("[SysAiGlobalSetting] upsertField: group={}, field={}, value={}, type={}, sort={}",
                group, field, value, valueType, sort);
    }

    @Override
    public int syncDescriptions(String group, Map<String, String> fieldToDescription) {
        if (group == null || group.isBlank() || fieldToDescription == null || fieldToDescription.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (Map.Entry<String, String> entry : fieldToDescription.entrySet()) {
            String field = entry.getKey();
            String description = entry.getValue();
            if (field == null || field.isBlank() || description == null) {
                continue;
            }
            SysAiGlobalSetting existing = sysAiGlobalSettingMapper.selectOne(
                    Wrappers.<SysAiGlobalSetting>lambdaQuery()
                            .eq(SysAiGlobalSetting::getSettingGroup, group)
                            .eq(SysAiGlobalSetting::getField, field));
            // 描述一致则跳过：不写库、不刷缓存更新时间
            if (existing == null || description.equals(existing.getDescription())) {
                continue;
            }
            existing.setDescription(description);
            existing.setUpdateTime(LocalDateTime.now());
            sysAiGlobalSettingMapper.updateById(existing);
            evictCache(group, field);
            updated++;
        }
        if (updated > 0) {
            log.info("[SysAiGlobalSetting] 分组 '{}' 同步 {} 项配置描述", group, updated);
        }
        return updated;
    }
}

