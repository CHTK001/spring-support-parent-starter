package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiManufacturer;
import com.chua.starter.ai.support.entity.settings.SysAiModel;
import com.chua.starter.ai.support.entity.settings.SysAiProvider;
import com.chua.starter.ai.support.mapper.SysAiManufacturerMapper;
import com.chua.starter.ai.support.mapper.settings.AiModelMapper;
import com.chua.starter.ai.support.mapper.settings.AiProviderMapper;
import com.chua.starter.ai.support.model.AiModelDTO;
import com.chua.starter.ai.support.service.settings.AiModelService;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.lang.json.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 模型定义服务实现类
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    /** Formatter */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 模型mapper */
    private final AiModelMapper modelMapper;
    /** 提供者mapper */
    private final AiProviderMapper providerMapper;
    /** Manufacturermapper */
    private final SysAiManufacturerMapper manufacturerMapper;

    @Override
    /** List */
    public List<AiModelDTO> list() {
        List<SysAiModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiModel>()
                        .orderByAsc(SysAiModel::getModelSort)
                        .orderByAsc(SysAiModel::getModelCreateTime));

        Map<Long, String> providerNames = providerMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysAiProvider::getProviderId, SysAiProvider::getProviderName,
                        (a, b) -> a));

        return models.stream()
                .map(m -> toDto(m, resolveProviderName(m.getModelProviderId(), providerNames)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    /** 创建 */
    public AiModelDTO create(AiModelDTO model) {
        SysAiModel entity = toEntity(model);
        LocalDateTime now = LocalDateTime.now();
        entity.setModelCreateTime(now);
        entity.setModelUpdateTime(now);
        if (entity.getModelEnabled() == null) {
            entity.setModelEnabled(true);
        }
        if (entity.getModelDeleted() == null) {
            entity.setModelDeleted(0);
        }
        modelMapper.insert(entity);
        log.debug("[SysAiModel] 新建成功: id={}, code={}", entity.getModelId(), entity.getModelCode());
        return toDto(entity, resolveProviderName(entity.getModelProviderId(), null));
    }

    @Override
    @Transactional
    /** 更新 */
    public AiModelDTO update(Long modelId, AiModelDTO model) {
        SysAiModel entity = toEntity(model);
        entity.setModelId(modelId);
        entity.setModelUpdateTime(LocalDateTime.now());
        modelMapper.updateById(entity);
        log.debug("[SysAiModel] 更新成功: id={}", modelId);
        return toDto(modelMapper.selectById(modelId), resolveProviderName(entity.getModelProviderId(), null));
    }

    @Override
    @Transactional
    /** 删除 */
    public void delete(Long modelId) {
        modelMapper.deleteById(modelId);
        log.debug("[SysAiModel] 删除成功: id={}", modelId);
    }

    /**
     * 解析供应商名称：优先模型供应商表，回退厂商表（兼容 syncModels 写入的 manufacturerId）。
     *
     * @param providerId   关联 ID（可能是 providerId 或 manufacturerId）
     * @param providerNames 预加载的供应商名称映射（可为 null）
     * @return 供应商/厂商名称，未找到返回 null
     */
    private String resolveProviderName(Long providerId, Map<Long, String> providerNames) {
        if (providerId == null) {
            return null;
        }
        if (providerNames != null) {
            String name = providerNames.get(providerId);
            if (name != null) {
                return name;
            }
        } else {
            SysAiProvider provider = providerMapper.selectById(providerId);
            if (provider != null) {
                return provider.getProviderName();
            }
        }
        // 回退厂商表
        SysAiManufacturer manufacturer = manufacturerMapper.selectById(providerId.intValue());
        return manufacturer != null ? manufacturer.getName() : null;
    }

    /** ToDto */
    private AiModelDTO toDto(SysAiModel entity, String providerName) {
        if (entity == null) return null;
        AiModelDTO dto = new AiModelDTO();
        dto.setId(entity.getModelId());
        dto.setName(entity.getModelName());
        dto.setCode(entity.getModelCode());
        dto.setProviderId(entity.getModelProviderId());
        dto.setProviderName(providerName);
        dto.setCapabilities(parseList(entity.getModelCapabilities()));
        dto.setDescription(entity.getModelDescription());
        dto.setEnabled(entity.getModelEnabled());
        dto.setSort(entity.getModelSort());
        dto.setInputPrice(entity.getModelInputPrice());
        dto.setOutputPrice(entity.getModelOutputPrice());
        dto.setCachePrice(entity.getModelCachePrice());
        dto.setCreatedAt(entity.getModelCreateTime() != null ? entity.getModelCreateTime().format(FORMATTER) : null);
        return dto;
    }

    /** ToEntity */
    private SysAiModel toEntity(AiModelDTO dto) {
        SysAiModel entity = new SysAiModel();
        entity.setModelName(dto.getName());
        entity.setModelCode(dto.getCode());
        entity.setModelProviderId(dto.getProviderId());
        entity.setModelCapabilities(toJsonArray(dto.getCapabilities()));
        entity.setModelDescription(dto.getDescription());
        entity.setModelEnabled(dto.getEnabled());
        entity.setModelSort(dto.getSort());
        entity.setModelInputPrice(dto.getInputPrice());
        entity.setModelOutputPrice(dto.getOutputPrice());
        entity.setModelCachePrice(dto.getCachePrice());
        return entity;
    }

    /** 解析List */
    private List<String> parseList(String content) {
        return Converter.convertIfListNecessary(content, String.class);
    }

    /** ToJsonArray */
    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        return Json.toJson(values);
    }
}