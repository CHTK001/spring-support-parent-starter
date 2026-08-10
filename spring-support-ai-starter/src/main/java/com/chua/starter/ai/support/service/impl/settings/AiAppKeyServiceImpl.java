package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.entity.settings.AiAppKey;
import com.chua.starter.ai.support.entity.settings.AiGroup;
import com.chua.starter.ai.support.entity.settings.AiProvider;
import com.chua.starter.ai.support.mapper.settings.AiAppKeyMapper;
import com.chua.starter.ai.support.mapper.settings.AiGroupMapper;
import com.chua.starter.ai.support.mapper.settings.AiProviderMapper;
import com.chua.starter.ai.support.model.AiAppKeyDTO;
import com.chua.starter.ai.support.service.SysAiAppKeyService;
import com.chua.starter.ai.support.service.settings.AiAppKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AppKey 服务实现类
 *
 * @author CH
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAppKeyServiceImpl implements AiAppKeyService {

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * AppKey Mapper
     */
    private final AiAppKeyMapper appKeyMapper;
    private final AiProviderMapper providerMapper;
    private final AiGroupMapper groupMapper;

    /**
     * OpenAI 兼容鉴权密钥服务（同步写入 sys_ai_app_key 兼容列）
     */
    private final SysAiAppKeyService sysAiAppKeyService;

    /**
     * 查询所有 AppKey。
     *
     * @return 操作结果
     */
    @Override
    public ReturnResult<List<AiAppKeyDTO>> list() {
        List<AiAppKey> keys = appKeyMapper.selectList(
                        new LambdaQueryWrapper<AiAppKey>()
                                .orderByDesc(AiAppKey::getCreateTime))
                .stream().toList();

        Map<Long, String> providerNames = providerMapper.selectList(null).stream()
                .collect(Collectors.toMap(AiProvider::getProviderId, AiProvider::getProviderName, (a, b) -> a));
        Map<Long, String> groupNames = groupMapper.selectList(null).stream()
                .collect(Collectors.toMap(AiGroup::getGroupId, AiGroup::getGroupName, (a, b) -> a));

        return ReturnResult.ok(keys.stream()
                .map(k -> {
                    AiAppKeyDTO dto = toDto(k, providerNames.get(k.getAppKeyProviderId()), groupNames.get(k.getAppKeyGroupId()));
                    // 列表接口密钥脱敏，防止明文泄露
                    if (dto != null) {
                        dto.setKey(maskSecret(dto.getKey()));
                    }
                    return dto;
                })
                .collect(Collectors.toList()));
    }

    /**
     * 新建 AppKey。
     *
     * @param appKey AppKey DTO
     * @return 操作结果
     */
    @Override
    @Transactional
    public ReturnResult<AiAppKeyDTO> create(AiAppKeyDTO appKey) {
        if (appKey == null || appKey.getName() == null || appKey.getName().isBlank()) {
            return ReturnResult.illegal("AppKey 名称不能为空");
        }
        AiAppKey entity = toEntity(appKey);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getAppKeyEnabled() == null) {
            entity.setAppKeyEnabled(true);
        }
        if (entity.getAppKeyDeleted() == null) {
            entity.setAppKeyDeleted(0);
        }
        if (entity.getAppKeyUseCount() == null) {
            entity.setAppKeyUseCount(0L);
        }
        appKeyMapper.insert(entity);
        log.debug("[AiAppKey] 新建成功: id={}, name={}", entity.getAppKeyId(), entity.getAppKeyName());

        // 同步写入 OpenAI 兼容鉴权记录（sys_ai_app_key_app_key / sys_ai_app_key_secret）
        syncCompatibleAppKey(entity);
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getAppKeyProviderId()), resolveGroup(entity.getAppKeyGroupId())));
    }

    /**
     * 将面板创建的 AppKey 同步为 OpenAI 兼容鉴权记录。
     *
     * <p>面板资料行与 OpenAI 兼容鉴权共用 {@code sys_ai_app_key} 同一张表：
     * 面板使用 {@code sys_ai_app_key_value} 列，协议鉴权使用
     * {@code sys_ai_app_key_app_key} 与 {@code sys_ai_app_key_secret} 列。
     * 为避免重复行，此处在同一行上补写兼容列，而非新建一行。</p>
     *
     * @param entity 面板 AppKey 实体
     */
    private void syncCompatibleAppKey(AiAppKey entity) {
        try {
            String keyValue = entity.getAppKeyValue();
            if (keyValue == null || keyValue.isBlank()) {
                log.warn("[AiAppKey] AppKey 值为空，跳过 OpenAI 兼容同步: id={}", entity.getAppKeyId());
                return;
            }
            // 同一行补齐兼容鉴权列（不新增行，避免列表出现重复 AppKey）
            appKeyMapper.update(
                    null,
                    new LambdaUpdateWrapper<AiAppKey>()
                            .eq(AiAppKey::getAppKeyId, entity.getAppKeyId())
                            .set(AiAppKey::getUpdateTime, LocalDateTime.now()));
            sysAiAppKeyService.updateCompatibleColumns(entity.getAppKeyId(), keyValue,
                    Boolean.TRUE.equals(entity.getAppKeyEnabled()) ? 1 : 0,
                    entity.getAppKeyGroupId() != null ? entity.getAppKeyGroupId().intValue() : null);
            // 同步过期时间与次数上限，使协议鉴权立即生效
            sysAiAppKeyService.updateExpireCompatible(
                    entity.getAppKeyId(),
                    toEpochSecond(entity.getAppKeyExpireTime()),
                    entity.getAppKeyMaxUse());
            log.debug("[AiAppKey] 已同步 OpenAI 兼容鉴权列: appKey={}", keyValue);
        } catch (Exception e) {
            log.warn("[AiAppKey] 同步 OpenAI 兼容鉴权列失败: {}", e.getMessage());
        }
    }

    /**
     * 更新 AppKey。
     *
     * @param appKeyId AppKey 主键
     * @param appKey   AppKey DTO
     * @return 操作结果
     */
    @Override
    @Transactional
    public ReturnResult<AiAppKeyDTO> update(Long appKeyId, AiAppKeyDTO appKey) {
        if (appKeyId == null) {
            return ReturnResult.illegal("AppKey ID 不能为空");
        }
        AiAppKey existing = appKeyMapper.selectById(appKeyId);
        if (existing == null) {
            return ReturnResult.error("AppKey 不存在");
        }
        AiAppKey entity = toEntity(appKey);
        entity.setAppKeyId(appKeyId);
        entity.setCreateTime(existing.getCreateTime());
        entity.setUpdateTime(LocalDateTime.now());
        appKeyMapper.updateById(entity);
        // 编辑后回写兼容鉴权列，确保协议端 key/status/expire/maxUse 与面板一致
        syncCompatibleAppKey(entity);
        return ReturnResult.ok(toDto(appKeyMapper.selectById(appKeyId), resolveProvider(entity.getAppKeyProviderId()), resolveGroup(entity.getAppKeyGroupId())));
    }

    /**
     * 删除 AppKey（逻辑删除）。
     *
     * @param appKeyId AppKey 主键
     * @return 操作结果
     */
    @Override
    @Transactional
    public ReturnResult<Void> delete(Long appKeyId) {
        if (appKeyId == null) {
            return ReturnResult.illegal("AppKey ID 不能为空");
        }
        AiAppKey entity = appKeyMapper.selectById(appKeyId);
        // 先同步禁用 OpenAI 兼容鉴权，再执行物理/逻辑删除，避免已删除 AppKey 仍可调用协议端点
        if (entity != null && entity.getAppKeyValue() != null && !entity.getAppKeyValue().isBlank()) {
            sysAiAppKeyService.updateCompatibleColumns(
                    entity.getAppKeyId(),
                    entity.getAppKeyValue(),
                    0,
                    entity.getAppKeyGroupId() != null ? entity.getAppKeyGroupId().intValue() : null);
        }
        appKeyMapper.deleteById(appKeyId);
        log.debug("[AiAppKey] 删除成功: id={}", appKeyId);
        return ReturnResult.ok();
    }

    /**
     * 切换 AppKey 启用状态。
     *
     * @param appKeyId AppKey 主键
     * @param enabled  是否启用
     * @return 操作结果
     */
    @Override
    @Transactional
    public ReturnResult<AiAppKeyDTO> toggle(Long appKeyId, Boolean enabled) {
        if (appKeyId == null) {
            return ReturnResult.illegal("AppKey ID 不能为空");
        }
        LambdaUpdateWrapper<AiAppKey> wrapper = new LambdaUpdateWrapper<AiAppKey>()
                .eq(AiAppKey::getAppKeyId, appKeyId)
                .set(AiAppKey::getAppKeyEnabled, enabled)
                .set(AiAppKey::getUpdateTime, LocalDateTime.now());
        appKeyMapper.update(null, wrapper);
        AiAppKey entity = appKeyMapper.selectById(appKeyId);
        // 同步 OpenAI 兼容鉴权状态（status 列），使协议端点立即生效
        syncCompatibleStatus(entity);
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getAppKeyProviderId()), resolveGroup(entity.getAppKeyGroupId())));
    }

    /**
     * 将面板 AppKey 的启停状态同步到 OpenAI 兼容鉴权列（status）。
     *
     * @param entity 面板 AppKey 实体
     */
    private void syncCompatibleStatus(AiAppKey entity) {
        try {
            if (entity == null || entity.getAppKeyId() == null) {
                return;
            }
            String keyValue = entity.getAppKeyValue();
            if (keyValue == null || keyValue.isBlank()) {
                return;
            }
            sysAiAppKeyService.updateCompatibleColumns(
                    entity.getAppKeyId(),
                    keyValue,
                    Boolean.TRUE.equals(entity.getAppKeyEnabled()) ? 1 : 0,
                    entity.getAppKeyGroupId() != null ? entity.getAppKeyGroupId().intValue() : null);
            // 同步过期时间与次数上限，与面板保持一致
            sysAiAppKeyService.updateExpireCompatible(
                    entity.getAppKeyId(),
                    toEpochSecond(entity.getAppKeyExpireTime()),
                    entity.getAppKeyMaxUse());
            log.debug("[AiAppKey] 已同步 OpenAI 兼容鉴权状态: id={}, enabled={}", entity.getAppKeyId(), entity.getAppKeyEnabled());
        } catch (Exception e) {
            log.warn("[AiAppKey] 同步 OpenAI 兼容鉴权状态失败: {}", e.getMessage());
        }
    }

    private String resolveProvider(Long providerId) {
        if (providerId == null) return null;
        AiProvider provider = providerMapper.selectById(providerId);
        return provider != null ? provider.getProviderName() : null;
    }

    private String resolveGroup(Long groupId) {
        if (groupId == null) return null;
        AiGroup group = groupMapper.selectById(groupId);
        return group != null ? group.getGroupName() : null;
    }

    /**
     * 密钥脱敏：保留前 2 位与后 2 位，中间以 **** 代替。
     *
     * @param value 明文密钥
     * @return 脱敏值
     */
    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    /**
     * 实体转 DTO。
     */
    private AiAppKeyDTO toDto(AiAppKey entity, String providerName, String groupName) {
        if (entity == null) {
            return null;
        }
        AiAppKeyDTO dto = new AiAppKeyDTO();
        dto.setId(entity.getAppKeyId());
        dto.setName(entity.getAppKeyName());
        dto.setKey(entity.getAppKeyValue());
        dto.setProvider(entity.getAppKeyProviderId());
        dto.setProviderName(providerName);
        dto.setModel(entity.getAppKeyModel());
        dto.setGroupId(entity.getAppKeyGroupId());
        dto.setGroupName(groupName);
        dto.setUseCount(entity.getAppKeyUseCount());
        dto.setMaxUse(entity.getAppKeyMaxUse());
        dto.setExpireTime(entity.getAppKeyExpireTime() != null ? entity.getAppKeyExpireTime().format(FORMATTER) : null);
        dto.setEnabled(entity.getAppKeyEnabled());
        dto.setCreatedAt(entity.getCreateTime() != null ? entity.getCreateTime().format(FORMATTER) : null);
        return dto;
    }

    /**
     * DTO 转实体。
     */
    private AiAppKey toEntity(AiAppKeyDTO dto) {
        AiAppKey entity = new AiAppKey();
        entity.setAppKeyName(dto.getName());
        entity.setAppKeyValue(dto.getKey());
        entity.setAppKeyProviderId(dto.getProvider());
        entity.setAppKeyModel(dto.getModel());
        entity.setAppKeyGroupId(dto.getGroupId());
        entity.setAppKeyEnabled(dto.getEnabled());
        entity.setAppKeyUseCount(dto.getUseCount());
        entity.setAppKeyMaxUse(dto.getMaxUse());
        if (dto.getExpireTime() != null && !dto.getExpireTime().isBlank()) {
            entity.setAppKeyExpireTime(LocalDateTime.parse(dto.getExpireTime(), FORMATTER));
        }
        return entity;
    }

    /**
     * LocalDateTime 转 epoch 秒（null 返回 null）。
     *
     * @param dateTime 面板过期时间
     * @return epoch 秒
     */
    private Long toEpochSecond(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }
}