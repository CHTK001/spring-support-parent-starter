package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.entity.settings.AiAppKey;
import com.chua.starter.ai.support.mapper.settings.AiAppKeyMapper;
import com.chua.starter.ai.support.model.AiAppKeyDTO;
import com.chua.starter.ai.support.service.settings.AiAppKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    /**
     * 查询所有 AppKey。
     *
     * @return AppKey DTO 列表
     */
    @Override
    public List<AiAppKeyDTO> list() {
        return appKeyMapper.selectList(
                        new LambdaQueryWrapper<AiAppKey>()
                                .orderByAsc(AiAppKey::getAppKeyCreateTime))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 新建 AppKey。
     *
     * @param appKey AppKey DTO
     * @return 创建后的 AppKey DTO
     */
    @Override
    @Transactional
    public AiAppKeyDTO create(AiAppKeyDTO appKey) {
        AiAppKey entity = toEntity(appKey);
        LocalDateTime now = LocalDateTime.now();
        entity.setAppKeyCreateTime(now);
        entity.setAppKeyUpdateTime(now);
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
        return toDto(entity);
    }

    /**
     * 更新 AppKey。
     *
     * @param appKeyId AppKey 主键
     * @param appKey   AppKey DTO
     * @return 更新后的 AppKey DTO
     */
    @Override
    @Transactional
    public AiAppKeyDTO update(Long appKeyId, AiAppKeyDTO appKey) {
        AiAppKey entity = toEntity(appKey);
        entity.setAppKeyId(appKeyId);
        entity.setAppKeyUpdateTime(LocalDateTime.now());
        appKeyMapper.updateById(entity);
        return toDto(appKeyMapper.selectById(appKeyId));
    }

    /**
     * 删除 AppKey（逻辑删除）。
     *
     * @param appKeyId AppKey 主键
     */
    @Override
    @Transactional
    public void delete(Long appKeyId) {
        appKeyMapper.deleteById(appKeyId);
        log.debug("[AiAppKey] 删除成功: id={}", appKeyId);
    }

    /**
     * 切换 AppKey 启用状态。
     *
     * @param appKeyId AppKey 主键
     * @param enabled  是否启用
     * @return 更新后的 AppKey DTO
     */
    @Override
    @Transactional
    public AiAppKeyDTO toggle(Long appKeyId, Boolean enabled) {
        LambdaUpdateWrapper<AiAppKey> wrapper = new LambdaUpdateWrapper<AiAppKey>()
                .eq(AiAppKey::getAppKeyId, appKeyId)
                .set(AiAppKey::getAppKeyEnabled, enabled)
                .set(AiAppKey::getAppKeyUpdateTime, LocalDateTime.now());
        appKeyMapper.update(null, wrapper);
        return toDto(appKeyMapper.selectById(appKeyId));
    }

    /**
     * 实体转 DTO。
     *
     * @param entity AppKey 实体
     * @return AppKey DTO
     */
    private AiAppKeyDTO toDto(AiAppKey entity) {
        if (entity == null) {
            return null;
        }
        AiAppKeyDTO dto = new AiAppKeyDTO();
        dto.setId(entity.getAppKeyId());
        dto.setName(entity.getAppKeyName());
        dto.setKey(entity.getAppKeyValue());
        dto.setProvider(entity.getAppKeyProviderId());
        dto.setModel(entity.getAppKeyModel());
        dto.setEnabled(entity.getAppKeyEnabled());
        dto.setCreatedAt(entity.getAppKeyCreateTime() != null ? entity.getAppKeyCreateTime().format(FORMATTER) : null);
        return dto;
    }

    /**
     * DTO 转实体。
     *
     * @param dto AppKey DTO
     * @return AppKey 实体
     */
    private AiAppKey toEntity(AiAppKeyDTO dto) {
        AiAppKey entity = new AiAppKey();
        entity.setAppKeyName(dto.getName());
        entity.setAppKeyValue(dto.getKey());
        entity.setAppKeyProviderId(dto.getProvider());
        entity.setAppKeyModel(dto.getModel());
        entity.setAppKeyEnabled(dto.getEnabled());
        return entity;
    }
}
