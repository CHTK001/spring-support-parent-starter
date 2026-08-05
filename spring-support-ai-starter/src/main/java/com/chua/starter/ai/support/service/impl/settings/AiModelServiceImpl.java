package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.settings.AiModel;
import com.chua.starter.ai.support.entity.settings.AiProvider;
import com.chua.starter.ai.support.mapper.settings.AiModelMapper;
import com.chua.starter.ai.support.mapper.settings.AiProviderMapper;
import com.chua.starter.ai.support.model.AiModelDTO;
import com.chua.starter.ai.support.service.settings.AiModelService;
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

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiModelMapper modelMapper;
    private final AiProviderMapper providerMapper;

    @Override
    public List<AiModelDTO> list() {
        List<AiModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<AiModel>()
                        .orderByAsc(AiModel::getModelSort)
                        .orderByAsc(AiModel::getModelCreateTime));

        Map<Long, String> providerNames = providerMapper.selectList(null).stream()
                .collect(Collectors.toMap(AiProvider::getProviderId, AiProvider::getProviderName,
                        (a, b) -> a));

        return models.stream().map(m -> toDto(m, providerNames.get(m.getModelProviderId()))).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AiModelDTO create(AiModelDTO model) {
        AiModel entity = toEntity(model);
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
        log.debug("[AiModel] 新建成功: id={}, code={}", entity.getModelId(), entity.getModelCode());
        return toDto(entity, resolveProviderName(entity.getModelProviderId()));
    }

    @Override
    @Transactional
    public AiModelDTO update(Long modelId, AiModelDTO model) {
        AiModel entity = toEntity(model);
        entity.setModelId(modelId);
        entity.setModelUpdateTime(LocalDateTime.now());
        modelMapper.updateById(entity);
        log.debug("[AiModel] 更新成功: id={}", modelId);
        return toDto(modelMapper.selectById(modelId), resolveProviderName(entity.getModelProviderId()));
    }

    @Override
    @Transactional
    public void delete(Long modelId) {
        modelMapper.deleteById(modelId);
        log.debug("[AiModel] 删除成功: id={}", modelId);
    }

    private String resolveProviderName(Long providerId) {
        if (providerId == null) return null;
        AiProvider provider = providerMapper.selectById(providerId);
        return provider != null ? provider.getProviderName() : null;
    }

    private AiModelDTO toDto(AiModel entity, String providerName) {
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
        dto.setCreatedAt(entity.getModelCreateTime() != null ? entity.getModelCreateTime().format(FORMATTER) : null);
        return dto;
    }

    private AiModel toEntity(AiModelDTO dto) {
        AiModel entity = new AiModel();
        entity.setModelName(dto.getName());
        entity.setModelCode(dto.getCode());
        entity.setModelProviderId(dto.getProviderId());
        entity.setModelCapabilities(toJsonArray(dto.getCapabilities()));
        entity.setModelDescription(dto.getDescription());
        entity.setModelEnabled(dto.getEnabled());
        entity.setModelSort(dto.getSort());
        return entity;
    }

    private List<String> parseList(String content) {
        if (content == null || content.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(content, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("[AiModel] 能力列表解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        return Json.toJson(values);
    }
}