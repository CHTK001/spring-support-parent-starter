package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;
import org.springframework.cache.annotation.CacheEvict;
import com.chua.starter.ai.support.entity.settings.AiGroup;
import com.chua.starter.ai.support.entity.settings.AiProvider;
import com.chua.starter.ai.support.mapper.SysAiAppKeyMapper;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AppKey 服务实现类。
 *
 * <p>面板层 AppKey 复用 {@link SysAiAppKey} 实体与 {@link SysAiAppKeyMapper}，
 * 与协议层共用同一张 {@code sys_ai_app_key} 表，消除双实体对账写的风险。</p>
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
     * AppKey Mapper（与协议层共用）
     */
    private final SysAiAppKeyMapper appKeyMapper;
    private final AiProviderMapper providerMapper;
    private final AiGroupMapper groupMapper;

    /**
     * 查询所有 AppKey。
     *
     * @return 操作结果
     */
    @Override
    public ReturnResult<List<AiAppKeyDTO>> list() {
        List<SysAiAppKey> keys = appKeyMapper.selectList(
                        new LambdaQueryWrapper<SysAiAppKey>()
                                .orderByDesc(SysAiAppKey::getSysAiAppKeyId))
                .stream().toList();

        Map<Long, String> providerNames = providerMapper.selectList(null).stream()
                .collect(Collectors.toMap(AiProvider::getProviderId, AiProvider::getProviderName, (a, b) -> a));
        Map<Integer, String> groupNames = resolveGroupNames();

        return ReturnResult.ok(keys.stream()
                .map(k -> toDto(k, providerNames.get(k.getSysAiAppKeyProviderId()),
                        groupNames.get(k.getSysAiAppKeyGroupId())))
                .collect(Collectors.toList()));
    }

    /**
     * 解析全部分组名称映射。
     *
     * @return groupId → groupName 映射
     */
    private Map<Integer, String> resolveGroupNames() {
        return groupMapper.selectList(null).stream()
                .collect(Collectors.toMap(g -> g.getGroupId().intValue(), AiGroup::getGroupName, (a, b) -> a));
    }

    /**
     * 新建 AppKey。
     *
     * @param dto AppKey DTO
     * @return 操作结果
     */
    @Override
    @Transactional
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    public ReturnResult<AiAppKeyDTO> create(AiAppKeyDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            return ReturnResult.illegal("AppKey 名称不能为空");
        }
        SysAiAppKey entity = toEntity(dto);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getSysAiAppKeyStatus() == null) {
            entity.setSysAiAppKeyStatus(1);
        }
        if (entity.getSysAiAppKeyDelete() == null) {
            entity.setSysAiAppKeyDelete(0);
        }
        if (entity.getSysAiAppKeyUseCount() == null) {
            entity.setSysAiAppKeyUseCount(0L);
        }
        appKeyMapper.insert(entity);
        log.debug("[AiAppKey] 新建成功: id={}, name={}", entity.getSysAiAppKeyId(), entity.getSysAiAppKeyName());
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getSysAiAppKeyProviderId()),
                resolveGroup(entity.getSysAiAppKeyGroupId())));
    }

    /**
     * 更新 AppKey。
     *
     * @param appKeyId AppKey 主键
     * @param dto      AppKey DTO
     * @return 操作结果
     */
    @Override
    @Transactional
    public ReturnResult<AiAppKeyDTO> update(Long appKeyId, AiAppKeyDTO dto) {
        if (appKeyId == null) {
            return ReturnResult.illegal("AppKey ID 不能为空");
        }
        SysAiAppKey existing = appKeyMapper.selectById(appKeyId.intValue());
        if (existing == null) {
            return ReturnResult.error("AppKey 不存在");
        }
        SysAiAppKey entity = toEntity(dto);
        entity.setSysAiAppKeyId(appKeyId.intValue());
        entity.setSysAiAppKeyDelete(0);
        appKeyMapper.updateById(entity);
        SysAiAppKey updated = appKeyMapper.selectById(appKeyId.intValue());
        return ReturnResult.ok(toDto(updated, resolveProvider(updated.getSysAiAppKeyProviderId()),
                resolveGroup(updated.getSysAiAppKeyGroupId())));
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
        appKeyMapper.deleteById(appKeyId.intValue());
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
        LambdaUpdateWrapper<SysAiAppKey> wrapper = new LambdaUpdateWrapper<SysAiAppKey>()
                .eq(SysAiAppKey::getSysAiAppKeyId, appKeyId.intValue())
                .set(SysAiAppKey::getSysAiAppKeyStatus, Boolean.TRUE.equals(enabled) ? 1 : 0)
                .set(SysAiAppKey::getSysAiAppKeyEnabled, enabled);
        appKeyMapper.update(null, wrapper);
        SysAiAppKey entity = appKeyMapper.selectById(appKeyId.intValue());
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getSysAiAppKeyProviderId()),
                resolveGroup(entity.getSysAiAppKeyGroupId())));
    }

    /**
     * 解析供应商名称。
     *
     * @param providerId 供应商 ID
     * @return 供应商名称
     */
    private String resolveProvider(Long providerId) {
        if (providerId == null) {
            return null;
        }
        return Optional.ofNullable(providerMapper.selectById(providerId))
                .map(AiProvider::getProviderName)
                .orElse(null);
    }

    /**
     * 解析分组名称。
     *
     * @param groupId 分组 ID
     * @return 分组名称
     */
    private String resolveGroup(Integer groupId) {
        if (groupId == null) {
            return null;
        }
        return Optional.ofNullable(groupMapper.selectById(groupId))
                .map(AiGroup::getGroupName)
                .orElse(null);
    }

    /**
     * 实体转 DTO。
     *
     * @param entity       AppKey 实体
     * @param providerName 供应商名称
     * @param groupName    分组名称
     * @return AppKey DTO
     */
    private AiAppKeyDTO toDto(SysAiAppKey entity, String providerName, String groupName) {
        if (entity == null) {
            return null;
        }
        AiAppKeyDTO dto = new AiAppKeyDTO();
        dto.setId(entity.getSysAiAppKeyId() != null ? entity.getSysAiAppKeyId().longValue() : null);
        dto.setName(entity.getSysAiAppKeyName());
        dto.setKey(entity.getSysAiAppKeyAppKey());
        dto.setProvider(entity.getSysAiAppKeyProviderId());
        dto.setProviderName(providerName);
        dto.setModel(entity.getSysAiAppKeyModel());
        dto.setGroupId(entity.getSysAiAppKeyGroupId() != null ? entity.getSysAiAppKeyGroupId().longValue() : null);
        dto.setGroupName(groupName);
        dto.setUseCount(entity.getSysAiAppKeyUseCount());
        dto.setMaxUse(entity.getSysAiAppKeyMaxUse());
        dto.setExpireTime(entity.getSysAiAppKeyExpireDatetime() != null
                ? entity.getSysAiAppKeyExpireDatetime().format(FORMATTER) : null);
        dto.setEnabled(entity.getSysAiAppKeyEnabled() != null
                ? entity.getSysAiAppKeyEnabled()
                : entity.getSysAiAppKeyStatus() != null && entity.getSysAiAppKeyStatus() == 1);
        dto.setCreatedAt(entity.getCreateTime() != null ? entity.getCreateTime().format(FORMATTER) : null);
        return dto;
    }

    /**
     * DTO 转实体。
     *
     * @param dto AppKey DTO
     * @return AppKey 实体
     */
    private SysAiAppKey toEntity(AiAppKeyDTO dto) {
        SysAiAppKey entity = new SysAiAppKey();
        entity.setSysAiAppKeyName(dto.getName());
        if (dto.getKey() != null && !dto.getKey().isBlank()) {
            entity.setSysAiAppKeyAppKey(dto.getKey());
            entity.setSysAiAppKeySecret(dto.getKey());
        }
        entity.setSysAiAppKeyProviderId(dto.getProvider());
        entity.setSysAiAppKeyModel(dto.getModel());
        if (dto.getGroupId() != null) {
            entity.setSysAiAppKeyGroupId(dto.getGroupId().intValue());
        }
        entity.setSysAiAppKeyEnabled(dto.getEnabled());
        entity.setSysAiAppKeyStatus(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        entity.setSysAiAppKeyUseCount(dto.getUseCount());
        entity.setSysAiAppKeyMaxUse(dto.getMaxUse());
        if (dto.getExpireTime() != null && !dto.getExpireTime().isBlank()) {
            entity.setSysAiAppKeyExpireDatetime(LocalDateTime.parse(dto.getExpireTime(), FORMATTER));
        } else if (dto.getMaxUse() == null) {
            entity.setSysAiAppKeyExpireDatetime(null);
        }
        return entity;
    }
}
