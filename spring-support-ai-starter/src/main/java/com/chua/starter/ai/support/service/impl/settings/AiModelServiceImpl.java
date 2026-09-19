package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiManufacturer;
import com.chua.starter.ai.support.entity.SysAiManufacturerKey;
import com.chua.starter.ai.support.entity.settings.SysAiModel;
import com.chua.starter.ai.support.mapper.SysAiManufacturerKeyMapper;
import com.chua.starter.ai.support.mapper.SysAiManufacturerMapper;
import com.chua.starter.ai.support.mapper.settings.AiModelMapper;
import com.chua.starter.ai.support.model.AiModelDTO;
import com.chua.starter.ai.support.service.settings.AiModelService;
import com.chua.common.support.lang.json.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 模型定义服务实现类
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiModelMapper modelMapper;
    private final SysAiManufacturerMapper manufacturerMapper;
    private final SysAiManufacturerKeyMapper manufacturerKeyMapper;

    @Override
    public List<AiModelDTO> list() {
        List<SysAiModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiModel>()
                        .orderByAsc(SysAiModel::getModelSort)
                        .orderByAsc(SysAiModel::getModelCreateTime));

        Map<Long, String> providerNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(m -> Long.valueOf(m.getId()), m -> m.getName() == null ? "" : m.getName(),
                        (a, b) -> a));

        return models.stream()
                .map(m -> toDto(m, resolveProviderName(m.getModelProviderId(), providerNames)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
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
        log.debug("[SysAiModel] create success: id={}, code={}", entity.getModelId(), entity.getModelCode());
        return toDto(entity, resolveProviderName(entity.getModelProviderId(), null));
    }

    @Override
    @Transactional
    public AiModelDTO update(Long modelId, AiModelDTO model) {
        SysAiModel entity = toEntity(model);
        entity.setModelId(modelId);
        entity.setModelUpdateTime(LocalDateTime.now());
        modelMapper.updateById(entity);
        log.debug("[SysAiModel] update success: id={}", modelId);
        return toDto(modelMapper.selectById(modelId), resolveProviderName(entity.getModelProviderId(), null));
    }

    @Override
    @Transactional
    public void delete(Long modelId) {
        modelMapper.deleteById(modelId);
        log.debug("[SysAiModel] delete success: id={}", modelId);
    }

    @Override
    @Transactional
    public AiModelDTO testLatency(Long modelId) {
        SysAiModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("Model not found: " + modelId);
        }
        Long providerId = model.getModelProviderId();
        if (providerId == null) {
            throw new IllegalStateException("Model has no provider configured, cannot test latency");
        }
        SysAiManufacturer manufacturer = manufacturerMapper.selectById(providerId.intValue());
        if (manufacturer == null) {
            throw new IllegalStateException("Provider not found: " + providerId);
        }
        String baseUrl = manufacturer.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Provider has no baseUrl configured");
        }
        // Find first enabled API key
        SysAiManufacturerKey key = manufacturerKeyMapper.selectList(
                new LambdaQueryWrapper<SysAiManufacturerKey>()
                        .eq(SysAiManufacturerKey::getSysAiManufacturerCode, manufacturer.getCode())
                        .eq(SysAiManufacturerKey::getStatus, 1)
                        .last("LIMIT 1")
        ).stream().findFirst().orElse(null);
        String apiKey = key != null ? key.getValue() : manufacturer.getAppKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No API key available for provider: " + manufacturer.getCode());
        }
        // Build OpenAI-compatible chat completions request (max_tokens=1)
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "v1/chat/completions" : baseUrl + "/v1/chat/completions";
        String body = Json.toJson(Map.of(
                "model", model.getModelCode(),
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "max_tokens", 1,
                "stream", false
        ));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();
        long start = System.currentTimeMillis();
        long latency;
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            latency = System.currentTimeMillis() - start;
            if (response.statusCode() >= 400) {
                log.warn("[SysAiModel] latency test failed for model {}: status={}, body={}",
                        model.getModelCode(), response.statusCode(),
                        response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length())));
                // Still record latency even on error, but mark via exception
                throw new RuntimeException("API returned status " + response.statusCode());
            }
            log.info("[SysAiModel] latency test success for model {}: {}ms", model.getModelCode(), latency);
        } catch (Exception e) {
            latency = System.currentTimeMillis() - start;
            log.warn("[SysAiModel] latency test error for model {}: {}ms, error={}",
                    model.getModelCode(), latency, e.getMessage());
            // Record latency even on failure (negative would indicate error, but we keep positive with update)
            model.setModelLastLatency(latency);
            model.setModelLastTestTime(LocalDateTime.now());
            model.setModelUpdateTime(LocalDateTime.now());
            modelMapper.updateById(model);
            throw new RuntimeException("Latency test failed: " + e.getMessage(), e);
        }
        model.setModelLastLatency(latency);
        model.setModelLastTestTime(LocalDateTime.now());
        model.setModelUpdateTime(LocalDateTime.now());
        modelMapper.updateById(model);
        return toDto(model, resolveProviderName(model.getModelProviderId(), null));
    }

    /**
     * 解析提供者名称。
     *
     * @param providerId 提供者ID，不允许为 null
     * @param providerNames 提供者Names，不允许为 null
     * @return 结果字符串
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
            SysAiManufacturer manufacturer = manufacturerMapper.selectById(providerId.intValue());
            return manufacturer != null ? manufacturer.getName() : null;
        }
        SysAiManufacturer manufacturer = manufacturerMapper.selectById(providerId.intValue());
        return manufacturer != null ? manufacturer.getName() : null;
    }

    /**
     * 转为Dto。
     *
     * @param entity 实体，不允许为 null
     * @param providerName 提供者名称，不允许为 null
     * @return Ai模型DTO 对象
     */
    private AiModelDTO toDto(SysAiModel entity, String providerName) {
        if (entity == null) {
            return null;
        }
        AiModelDTO dto = new AiModelDTO();
        dto.setId(entity.getModelId());
        dto.setName(entity.getModelName());
        dto.setCode(entity.getModelCode());
        dto.setProviderId(entity.getModelProviderId());
        dto.setProviderName(providerName);
        dto.setCapabilities(parseList(entity.getModelCapabilities()));
        dto.setRequiredPipelines(parseList(entity.getModelRequiredPipelines()));
        dto.setOptionalPipelines(parseList(entity.getModelOptionalPipelines()));
        dto.setDescription(entity.getModelDescription());
        dto.setEnabled(entity.getModelEnabled());
        dto.setSort(entity.getModelSort());
        dto.setInputPrice(entity.getModelInputPrice());
        dto.setOutputPrice(entity.getModelOutputPrice());
        dto.setCachePrice(entity.getModelCachePrice());
        dto.setAliases(parseList(entity.getModelAliases()));
        dto.setLastLatency(entity.getModelLastLatency());
        dto.setLastTestTime(entity.getModelLastTestTime() != null ? entity.getModelLastTestTime().format(FORMATTER) : null);
        dto.setCreatedAt(entity.getModelCreateTime() != null ? entity.getModelCreateTime().format(FORMATTER) : null);
        return dto;
    }

    /**
     * 转为实体。
     *
     * @param dto 方法入参 dto
     * @return SysAi模型 对象
     */
    private SysAiModel toEntity(AiModelDTO dto) {
        SysAiModel entity = new SysAiModel();
        entity.setModelName(dto.getName());
        entity.setModelCode(dto.getCode());
        entity.setModelProviderId(dto.getProviderId());
        entity.setModelCapabilities(toJsonArray(dto.getCapabilities()));
        entity.setModelRequiredPipelines(toJsonArray(dto.getRequiredPipelines()));
        entity.setModelOptionalPipelines(toJsonArray(dto.getOptionalPipelines()));
        entity.setModelDescription(dto.getDescription());
        entity.setModelEnabled(dto.getEnabled());
        entity.setModelSort(dto.getSort());
        entity.setModelInputPrice(dto.getInputPrice());
        entity.setModelOutputPrice(dto.getOutputPrice());
        entity.setModelCachePrice(dto.getCachePrice());
        entity.setModelAliases(toJsonArray(dto.getAliases()));
        return entity;
    }

    /**
     * 解析列出。
     *
     * @param content 内容，不允许为 null
     * @return 结果列表，无数据时为空列表
     */
    private List<String> parseList(String content) {
        if (content == null || content.isBlank()) {
            return new java.util.ArrayList<>();
        }
        try {
            if (content.trim().startsWith("[")) {
                @SuppressWarnings("unchecked")
                List<String> list = Json.fromJson(content, List.class);
                return list != null ? list : new java.util.ArrayList<>();
            }
            return java.util.Arrays.stream(content.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (Exception e) {
            return java.util.Arrays.stream(content.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    /**
     * 转为Json数组。
     *
     * @param values 方法入参 values
     * @return 结果字符串
     */
    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return Json.toJson(values);
    }
}
