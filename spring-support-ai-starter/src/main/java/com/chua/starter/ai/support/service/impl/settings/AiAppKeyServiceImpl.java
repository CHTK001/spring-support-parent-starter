package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;
import org.springframework.cache.annotation.CacheEvict;
import com.chua.starter.ai.support.entity.settings.SysAiGroup;
import com.chua.starter.ai.support.entity.settings.SysAiProvider;
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
 * appKey 服务实现类。
 *
 * <p>面板层 AppKey 复用 {@link SysAiAppKey} 实体与 {@link SysAiAppKeyMapper}，
 * 与协议层共用同一张 {@code sys_ai_app_key} 表，消除双实体对账写的风险。</p>
 *
 * @since 2026-08-03
 * @author CH
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
     * appKey 映射器（与协议层共用）
     */
    private final SysAiAppKeyMapper appKeyMapper;
    /**
     * 提供者映射器
    */
    private final AiProviderMapper providerMapper;
    /**
     * 分组映射器
    */
    private final AiGroupMapper groupMapper;

    /**
     * 查询所有 appKey。
     *
     * @return 操作结果
     */
    @Override
    public ReturnResult<List<AiAppKeyDTO>> list() {
        List<SysAiAppKey> keys = appKeyMapper.selectList(
                        new LambdaQueryWrapper<SysAiAppKey>()
                                .orderByDesc(SysAiAppKey::getId))
                .stream().toList();

        Map<Long, String> providerNames = providerMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysAiProvider::getProviderId, SysAiProvider::getProviderName, (a, b) -> a));
        Map<Integer, String> groupNames = resolveGroupNames();

        return ReturnResult.ok(keys.stream()
                .map(k -> toDto(k, providerNames.get(k.getProviderId()),
                        groupNames.get(k.getGroupId())))
                .collect(Collectors.toList()));
    }

    /**
     * 解析全部分组名称映射。
     *
     * @return groupId → 群体名称 映射
     */
    private Map<Integer, String> resolveGroupNames() {
        return groupMapper.selectList(null).stream()
                .collect(Collectors.toMap(g -> g.getGroupId().intValue(), SysAiGroup::getGroupName, (a, b) -> a));
    }

    /**
     * 新建 appKey。
     *
     * @param dto appKey DTO
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
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getDelete() == null) {
            entity.setDelete(0);
        }
        if (entity.getUseCount() == null) {
            entity.setUseCount(0L);
        }
        appKeyMapper.insert(entity);
        log.debug("[SysAiAppKey] 新建成功: id={}, name={}", entity.getId(), entity.getName());
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getProviderId()),
                resolveGroup(entity.getGroupId())));
    }

    /**
     * 更新 appKey。
     *
     * @param appKeyId appKey 主键
     * @param dto      appKey DTO
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
        entity.setId(appKeyId.intValue());
        entity.setDelete(0);
        appKeyMapper.updateById(entity);
        SysAiAppKey updated = appKeyMapper.selectById(appKeyId.intValue());
        return ReturnResult.ok(toDto(updated, resolveProvider(updated.getProviderId()),
                resolveGroup(updated.getGroupId())));
    }

    /**
     * 删除 appKey（逻辑删除）。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果
     */
    @Override
    @Transactional
    public ReturnResult<Void> delete(Long appKeyId) {
        if (appKeyId == null) {
            return ReturnResult.illegal("AppKey ID 不能为空");
        }
        appKeyMapper.deleteById(appKeyId.intValue());
        log.debug("[SysAiAppKey] 删除成功: id={}", appKeyId);
        return ReturnResult.ok();
    }

    /**
     * 切换 appKey 启用状态。
     *
     * @param appKeyId appKey 主键
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
                .eq(SysAiAppKey::getId, appKeyId.intValue())
                .set(SysAiAppKey::getStatus, Boolean.TRUE.equals(enabled) ? 1 : 0)
                .set(SysAiAppKey::getEnabled, enabled);
        appKeyMapper.update(null, wrapper);
        SysAiAppKey entity = appKeyMapper.selectById(appKeyId.intValue());
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getProviderId()),
                resolveGroup(entity.getGroupId())));
    }

    /**
     * 解析供应商名称。
     *
     * @param providerId 供应商 标识
     * @return 供应商名称
     */
    private String resolveProvider(Long providerId) {
        if (providerId == null) {
            return null;
        }
        return Optional.ofNullable(providerMapper.selectById(providerId))
                .map(SysAiProvider::getProviderName)
                .orElse(null);
    }

    /**
     * 解析分组名称。
     *
     * @param groupId 分组 标识
     * @return 分组名称
     */
    private String resolveGroup(Integer groupId) {
        if (groupId == null) {
            return null;
        }
        return Optional.ofNullable(groupMapper.selectById(groupId))
                .map(SysAiGroup::getGroupName)
                .orElse(null);
    }

    /**
     * 实体转 DTO。
     *
     * @param entity       appKey 实体
     * @param providerName 供应商名称
     * @param groupName    分组名称
     * @return AppKey DTO
     */
    private AiAppKeyDTO toDto(SysAiAppKey entity, String providerName, String groupName) {
        if (entity == null) {
            return null;
        }
        AiAppKeyDTO dto = new AiAppKeyDTO();
        dto.setId(entity.getId() != null ? entity.getId().longValue() : null);
        dto.setName(entity.getName());
        dto.setKey(entity.getAppKey());
        dto.setProvider(entity.getProviderId());
        dto.setProviderName(providerName);
        dto.setModel(entity.getModel());
        dto.setGroupId(entity.getGroupId() != null ? entity.getGroupId().longValue() : null);
        dto.setGroupName(groupName);
        dto.setUseCount(entity.getUseCount());
        dto.setMaxUse(entity.getMaxUse());
        dto.setExpireTime(entity.getExpireDatetime() != null
                ? entity.getExpireDatetime().format(FORMATTER) : null);
        dto.setEnabled(entity.getEnabled() != null
                ? entity.getEnabled()
                : entity.getStatus() != null && entity.getStatus() == 1);
        dto.setCreatedAt(entity.getCreateTime() != null ? entity.getCreateTime().format(FORMATTER) : null);
        return dto;
    }

    /**
     * DTO 转实体。
     *
     * @param dto appKey DTO
     * @return AppKey 实体
     */
    private SysAiAppKey toEntity(AiAppKeyDTO dto) {
        SysAiAppKey entity = new SysAiAppKey();
        entity.setName(dto.getName());
        if (dto.getKey() != null && !dto.getKey().isBlank()) {
            entity.setAppKey(dto.getKey());
            entity.setSecret(dto.getKey());
        }
        entity.setProviderId(dto.getProvider());
        entity.setModel(dto.getModel());
        if (dto.getGroupId() != null) {
            entity.setGroupId(dto.getGroupId().intValue());
        }
        entity.setEnabled(dto.getEnabled());
        entity.setStatus(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        entity.setUseCount(dto.getUseCount());
        entity.setMaxUse(dto.getMaxUse());
        if (dto.getExpireTime() != null && !dto.getExpireTime().isBlank()) {
            entity.setExpireDatetime(LocalDateTime.parse(dto.getExpireTime(), FORMATTER));
        } else if (dto.getMaxUse() == null) {
            entity.setExpireDatetime(null);
        }
        return entity;
    }
}
