package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiUserSetting;
import com.chua.starter.ai.support.mapper.SysAiUserSettingMapper;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * AI 设置两层解析服务实现。
 *
 * <p>全局层复用 {@link SysAiGlobalSettingService}（其自身带缓存），用户层查
 * {@code sys_ai_user_setting}。两层合成后的解析结果单独缓存，缓存键含用户段，
 * 因此不同用户之间不会互相污染；管理员改全局默认时，全局服务的全量清缓存会连带
 * 解析缓存一起失效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiUserSettingServiceImpl implements SysAiUserSettingService {

    /**
     * 解析结果缓存键前缀
     */
    private static final String RESOLVE_CACHE_PREFIX = "sys:ai:setting:resolved:";

    /**
     * 用户覆盖行 映射器
     */
    private final SysAiUserSettingMapper userSettingMapper;

    /**
     * 全局默认层服务
     */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 解析字段值：用户覆盖优先，缺失回落全局默认。
     *
     * @param userId 用户标识，可为空
     * @param group  分组
     * @param field  字段名
     * @return 配置值
     */
    @Override
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "T(com.chua.starter.ai.support.service.impl.SysAiUserSettingServiceImpl).resolveKey(#userId, #group, #field)",
            unless = "#result == null || #result.isEmpty()")
    public Optional<String> resolve(String userId, String group, String field) {
        Optional<String> override = getUserValue(userId, group, field);
        if (override.isPresent()) {
            return override;
        }
        return globalSettingService.getValue(group, field);
    }

    @Override
    public boolean resolveBoolean(String userId, String group, String field, boolean defaultValue) {
        return resolve(userId, group, field)
                .map(v -> {
                    String lower = v.trim().toLowerCase();
                    return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower);
                })
                .orElse(defaultValue);
    }

    @Override
    public Optional<String> getUserValue(String userId, String group, String field) {
        if (isBlank(userId) || isBlank(group) || isBlank(field)) {
            return Optional.empty();
        }
        SysAiUserSetting setting = userSettingMapper.selectOne(Wrappers.<SysAiUserSetting>lambdaQuery()
                .eq(SysAiUserSetting::getUserId, userId)
                .eq(SysAiUserSetting::getSettingGroup, group)
                .eq(SysAiUserSetting::getField, field)
                .eq(SysAiUserSetting::getStatus, 1));
        if (setting == null || setting.getValue() == null) {
            return Optional.empty();
        }
        return Optional.of(setting.getValue());
    }

    @Override
    public List<Map<String, Object>> listMerged(String userId, String group) {
        List<Map<String, Object>> merged = new ArrayList<>();
        Map<String, SysAiUserSetting> overrides = new LinkedHashMap<>();
        for (SysAiUserSetting row : selectOverrides(userId, group)) {
            overrides.put(row.getField(), row);
        }
        for (Map<String, Object> item : globalSettingService.list(group)) {
            String field = String.valueOf(item.get("field"));
            SysAiUserSetting override = overrides.remove(field);
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("overridden", override != null);
            row.put("defaultValue", item.get("value"));
            if (override != null) {
                row.put("value", override.getValue());
            }
            merged.add(row);
        }
        // 用户覆盖了全局层尚未定义的字段，仍然展示出来，避免"改了看不见"
        for (SysAiUserSetting override : overrides.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("group", group);
            row.put("field", override.getField());
            row.put("value", override.getValue());
            row.put("valueType", override.getValueType());
            row.put("description", override.getDescription());
            row.put("sort", override.getSort());
            row.put("overridden", true);
            row.put("defaultValue", null);
            merged.add(row);
        }
        return merged;
    }

    @Override
    public List<Map<String, Object>> listOverrides(String userId, String group) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiUserSetting row : selectOverrides(userId, group)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("group", row.getSettingGroup());
            item.put("field", row.getField());
            item.put("value", row.getValue());
            item.put("valueType", row.getValueType());
            item.put("description", row.getDescription());
            item.put("sort", row.getSort());
            result.add(item);
        }
        return result;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "T(com.chua.starter.ai.support.service.impl.SysAiUserSettingServiceImpl).resolveKey(#userId, #group, #field)")
    @Transactional(rollbackFor = Exception.class)
    public void upsertValue(String userId, String group, String field, String value,
                            String valueType, String description) {
        if (isBlank(userId) || isBlank(group) || isBlank(field)) {
            log.warn("[SysAiUserSetting] upsertValue 缺少 userId/group/field 参数");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        SysAiUserSetting existing = userSettingMapper.selectOne(Wrappers.<SysAiUserSetting>lambdaQuery()
                .eq(SysAiUserSetting::getUserId, userId)
                .eq(SysAiUserSetting::getSettingGroup, group)
                .eq(SysAiUserSetting::getField, field));
        if (existing != null) {
            existing.setValue(value);
            if (valueType != null) {
                existing.setValueType(valueType);
            }
            if (description != null) {
                existing.setDescription(description);
            }
            existing.setStatus(1);
            existing.setUpdateTime(now);
            userSettingMapper.updateById(existing);
        } else {
            SysAiUserSetting row = new SysAiUserSetting();
            row.setUserId(userId);
            row.setSettingGroup(group);
            row.setField(field);
            row.setValue(value);
            row.setValueType(valueType != null ? valueType : "STRING");
            row.setDescription(description != null ? description : "");
            row.setSort(0);
            row.setStatus(1);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            userSettingMapper.insert(row);
        }
        log.info("[SysAiUserSetting] 用户 {} 覆盖 {}.{} 已写入", userId, group, field);
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "T(com.chua.starter.ai.support.service.impl.SysAiUserSettingServiceImpl).resolveKey(#userId, #group, #field)")
    @Transactional(rollbackFor = Exception.class)
    public int deleteValue(String userId, String group, String field) {
        if (isBlank(userId)) {
            return 0;
        }
        int rows = userSettingMapper.delete(Wrappers.<SysAiUserSetting>lambdaQuery()
                .eq(SysAiUserSetting::getUserId, userId)
                .eq(SysAiUserSetting::getSettingGroup, group)
                .eq(SysAiUserSetting::getField, field));
        log.info("[SysAiUserSetting] 用户 {} 撤销覆盖 {}.{}，影响 {} 行", userId, group, field, rows);
        return rows;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public int clearGroup(String userId, String group) {
        if (isBlank(userId)) {
            return 0;
        }
        int rows = userSettingMapper.delete(Wrappers.<SysAiUserSetting>lambdaQuery()
                .eq(SysAiUserSetting::getUserId, userId)
                .eq(SysAiUserSetting::getSettingGroup, group));
        log.info("[SysAiUserSetting] 用户 {} 清空分组 {} 覆盖，影响 {} 行", userId, group, rows);
        return rows;
    }

    /**
     * 解析结果缓存键，与 {@link #resolve} 的 {@code @Cacheable} 表达式保持一致。
     *
     * @param userId 用户标识
     * @param group  分组
     * @param field  字段名
     * @return 缓存键
     */
    public static String resolveKey(String userId, String group, String field) {
        return RESOLVE_CACHE_PREFIX + (isBlank(userId) ? "-" : userId) + ":" + group + ":" + field;
    }

    /**
     * 查询用户在指定分组的覆盖行；用户标识为空时返回空列表。
     *
     * @param userId 用户标识
     * @param group  分组
     * @return 覆盖行列表
     */
    private List<SysAiUserSetting> selectOverrides(String userId, String group) {
        if (isBlank(userId)) {
            return List.of();
        }
        return userSettingMapper.selectList(Wrappers.<SysAiUserSetting>lambdaQuery()
                .eq(SysAiUserSetting::getUserId, userId)
                .eq(SysAiUserSetting::getStatus, 1)
                .eq(!isBlank(group), SysAiUserSetting::getSettingGroup, group)
                .orderByAsc(SysAiUserSetting::getSort));
    }

    /**
     * 空串判断（含仅空白）。
     *
     * @param value 待判断值
     * @return 是否为空
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
