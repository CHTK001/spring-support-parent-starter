package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.entity.settings.AiProvider;
import com.chua.starter.ai.support.mapper.settings.AiProviderMapper;
import com.chua.starter.ai.support.model.AiProviderDTO;
import com.chua.starter.ai.support.service.settings.AiProviderService;
import com.chua.starter.common.support.text.json.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型供应商服务实现类
 *
 * @author CH
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderServiceImpl implements AiProviderService {

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * JSON 序列化器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 供应商 Mapper
     */
    private final AiProviderMapper providerMapper;

    /**
     * 查询所有启用状态的供应商。
     *
     * @return 供应商 DTO 列表
     */
    @Override
    public List<AiProviderDTO> list() {
        return providerMapper.selectList(
                        new LambdaQueryWrapper<AiProvider>()
                                .orderByAsc(AiProvider::getCreateTime))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 新建供应商。
     *
     * @param provider 供应商 DTO
     * @return 创建后的供应商 DTO
     */
    @Override
    @Transactional
    public AiProviderDTO create(AiProviderDTO provider) {
        AiProvider entity = toEntity(provider);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getProviderEnabled() == null) {
            entity.setProviderEnabled(true);
        }
        if (entity.getProviderDeleted() == null) {
            entity.setProviderDeleted(0);
        }
        providerMapper.insert(entity);
        log.debug("[AiProvider] 新建成功: id={}, name={}", entity.getProviderId(), entity.getProviderName());
        return toDto(entity);
    }

    /**
     * 更新供应商。
     *
     * @param providerId 供应商主键
     * @param provider   供应商 DTO
     * @return 更新后的供应商 DTO
     */
    @Override
    @Transactional
    public AiProviderDTO update(Long providerId, AiProviderDTO provider) {
        AiProvider entity = toEntity(provider);
        entity.setProviderId(providerId);
        entity.setUpdateTime(LocalDateTime.now());
        providerMapper.updateById(entity);
        log.debug("[AiProvider] 更新成功: id={}", providerId);
        return toDto(providerMapper.selectById(providerId));
    }

    /**
     * 删除供应商（逻辑删除）。
     *
     * @param providerId 供应商主键
     */
    @Override
    @Transactional
    public void delete(Long providerId) {
        providerMapper.deleteById(providerId);
        log.debug("[AiProvider] 删除成功: id={}", providerId);
    }

    /**
     * 切换供应商启用状态。
     *
     * @param providerId 供应商主键
     * @param enabled    是否启用
     * @return 更新后的供应商 DTO
     */
    @Override
    @Transactional
    public AiProviderDTO toggle(Long providerId, Boolean enabled) {
        LambdaUpdateWrapper<AiProvider> wrapper = new LambdaUpdateWrapper<AiProvider>()
                .eq(AiProvider::getProviderId, providerId)
                .set(AiProvider::getProviderEnabled, enabled)
                .set(AiProvider::getUpdateTime, LocalDateTime.now());
        providerMapper.update(null, wrapper);
        return toDto(providerMapper.selectById(providerId));
    }

    /**
     * 实体转 DTO。
     *
     * @param entity 供应商实体
     * @return 供应商 DTO
     */
    private AiProviderDTO toDto(AiProvider entity) {
        if (entity == null) {
            return null;
        }
        AiProviderDTO dto = new AiProviderDTO();
        dto.setId(entity.getProviderId());
        dto.setName(entity.getProviderName());
        dto.setType(entity.getProviderType());
        dto.setLogo(entity.getProviderLogo());
        dto.setBaseUrl(entity.getProviderBaseUrl());
        dto.setApiKey(entity.getProviderApiKey());
        dto.setModels(parseList(entity.getProviderModels()));
        dto.setEnabled(entity.getProviderEnabled());
        dto.setCreatedAt(entity.getCreateTime() != null ? entity.getCreateTime().format(FORMATTER) : null);
        return dto;
    }

    /**
     * DTO 转实体。
     *
     * @param dto 供应商 DTO
     * @return 供应商实体
     */
    private AiProvider toEntity(AiProviderDTO dto) {
        AiProvider entity = new AiProvider();
        entity.setProviderName(dto.getName());
        entity.setProviderType(dto.getType());
        entity.setProviderLogo(dto.getLogo());
        entity.setProviderBaseUrl(dto.getBaseUrl());
        entity.setProviderApiKey(dto.getApiKey());
        entity.setProviderModels(toJsonArray(dto.getModels()));
        entity.setProviderEnabled(dto.getEnabled());
        return entity;
    }

    /**
     * 解析 JSON 数组字符串为列表。
     *
     * @param content JSON 数组字符串
     * @return 字符串列表
     */
    private List<String> parseList(String content) {
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(content, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("[AiProvider] 模型列表解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 列表转 JSON 数组字符串。
     *
     * @param values 字符串列表
     * @return JSON 数组字符串
     */
    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return Json.toJson(values);
    }
}
