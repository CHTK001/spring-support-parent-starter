package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.settings.AiAppKey;
import com.chua.starter.ai.support.entity.settings.AiGroup;
import com.chua.starter.ai.support.entity.settings.AiProvider;
import com.chua.starter.ai.support.mapper.settings.AiAppKeyMapper;
import com.chua.starter.ai.support.mapper.settings.AiGroupMapper;
import com.chua.starter.ai.support.mapper.settings.AiProviderMapper;
import com.chua.starter.ai.support.model.AiAppKeyDTO;
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
                .map(k -> toDto(k, providerNames.get(k.getAppKeyProviderId()), groupNames.get(k.getAppKeyGroupId())))
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
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getAppKeyProviderId()), resolveGroup(entity.getAppKeyGroupId())));
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
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getAppKeyProviderId()), resolveGroup(entity.getAppKeyGroupId())));
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
}