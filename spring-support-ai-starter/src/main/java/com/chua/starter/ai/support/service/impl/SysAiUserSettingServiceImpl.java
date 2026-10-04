package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.ocr.OcrSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.rag.RagSettingsChangedEvent;
import com.chua.starter.ai.support.entity.SysAiUserSetting;
import com.chua.starter.ai.support.mapper.SysAiUserSettingMapper;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
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
     * 配置变更事件发布器。
     *
     * <p>用户覆盖写入必须和全局写入一样触发热重建，否则用户在设置页改了自己的模型
     * 「保存成功却不生效」（已缓存的管线/客户端不重建）。见
     * {@code SysAiGlobalSettingServiceImpl#publishSettingsChanged}，两处语义保持一致。</p>
     */
    private final ApplicationEventPublisher eventPublisher;

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

    /**
     * 解析布尔配置值，用户未覆盖时使用全局配置或默认值。
     *
     * @param userId 用户标识，可为空
     * @param group 配置分组
     * @param field 配置字段
     * @param defaultValue 全局配置不存在时的默认值
     * @return 解析后的布尔值
     */
    @Override
    public boolean resolveBoolean(String userId, String group, String field, boolean defaultValue) {
        return resolve(userId, group, field)
                .map(v -> {
                    String lower = v.trim().toLowerCase();
                    return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower);
                })
                .orElse(defaultValue);
    }

    /**
     * 获取用户在指定分组中的配置覆盖值。
     *
     * @param userId 用户标识
     * @param group 配置分组
     * @param field 配置字段
     * @return 用户覆盖值，不存在时返回空值
     */
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

    /**
     * 合并全局配置和用户覆盖配置。
     *
     * @param userId 用户标识
     * @param group 配置分组
     * @return 合并后的配置列表
     */
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

    /**
     * 查询用户在指定分组中的全部配置覆盖。
     *
     * @param userId 用户标识
     * @param group 配置分组
     * @return 用户覆盖配置列表
     */
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

    /**
     * 新增或更新用户配置覆盖。
     *
     * @param userId 用户标识
     * @param group 配置分组
     * @param field 配置字段
     * @param value 配置值
     * @param valueType 值类型
     * @param description 配置说明
     */
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
        publishSettingsChanged(group);
    }

    /**
     * 删除用户配置覆盖。
     *
     * @param userId 用户标识
     * @param group 配置分组
     * @param field 配置字段
     * @return 删除记录数
     */
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
        if (rows > 0) {
            publishSettingsChanged(group);
        }
        return rows;
    }

    /**
     * 清空用户在指定分组中的配置覆盖。
     *
     * @param userId 用户标识
     * @param group 配置分组
     * @return 删除记录数
     */
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
        if (rows > 0) {
            publishSettingsChanged(group);
        }
        return rows;
    }

    /**
     * 发布某个分组的配置变更事件，触发对应能力模块热重建。
     *
     * <p>与 {@code SysAiGlobalSettingServiceImpl#publishSettingsChanged} 完全一致：
     * 各能力模块自己按 {@code group} 过滤（例如 {@code FaceClientManager} 只处理 face），
     * 因此这里对所有分组统一发一次即可。</p>
     *
     * <p><b>为什么用户写入也必须发</b>：默认模型已改为「每个用户管自己的」，用户覆盖
     * 才是实际生效的那一层。只 evict 解析缓存只能让**下次读取**拿到新值，对
     * 「构建期读一次、之后缓存住」的实例（人脸管线 / 以图搜图 store / OCR / 排版）
     * 无效 —— 表现为「设置里改了模型，功能还是旧的，重启才好」。</p>
     *
     * <p><b>但绝不能把共享单例也重建了</b>：{@code FaceClientManager} 的检测器 / 分类器
     * 等字段是**全局共享**的，按触发者的用户身份重建会把他的模型烘进去、串掉其他用户。
     * 因此这里把事件标记为 {@code userScoped=true}，由各监听方自行决定：
     * 共享单例跳过，**按账号**构建的一方（{@code FaceStores}）重建并绑定各账号自己的身份。</p>
     *
     * @param group 配置分组名
     */
    private void publishSettingsChanged(String group) {
        if (isBlank(group)) {
            return;
        }
        try {
            eventPublisher.publishEvent(new FaceSettingsChangedEvent(group, true));
            if ("ocr".equals(group)) {
                eventPublisher.publishEvent(new OcrSettingsChangedEvent(group, true));
            }
            if ("rag".equals(group)) {
                eventPublisher.publishEvent(new RagSettingsChangedEvent(group, true));
            }
        } catch (Exception e) {
            // 事件发布失败不得影响配置写入本身：上层已落库并清缓存，功能最多退化为「重启后生效」
            log.warn("[SysAiUserSetting] 发布配置变更事件失败(group={}): {}", group, e.getMessage());
        }
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
