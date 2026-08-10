package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiManufacturer;
import com.chua.starter.ai.support.entity.settings.AiModel;
import com.chua.starter.ai.support.mapper.SysAiManufacturerMapper;
import com.chua.starter.ai.support.mapper.settings.AiModelMapper;
import com.chua.starter.ai.support.service.AiManufacturerFacadeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * AI 模型厂商管理服务实现。
 * <p>
 * 基于 MyBatis Plus 操作 sys_ai_manufacturer 表，
 * 提供厂商的增删改查，并缓存列表以降低频繁读取。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiManufacturerFacadeServiceImpl implements AiManufacturerFacadeService {

    /**
     * HTTP 请求超时时间（秒）
     */
    private static final int HTTP_TIMEOUT_SECONDS = 15;

    /**
     * 厂商 Mapper
     */
    private final SysAiManufacturerMapper sysAiManufacturerMapper;

    /**
     * 模型 Mapper
     */
    private final AiModelMapper aiModelMapper;

    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .build();

    @Override
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:manufacturer:list'", unless = "#result == null || #result.isEmpty()")
    public List<SysAiManufacturer> list() {
        return sysAiManufacturerMapper.selectList(null);
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:manufacturer:list'")
    public SysAiManufacturer save(SysAiManufacturer manufacturer) {
        manufacturer.setSysAiManufacturerStatus(
                manufacturer.getSysAiManufacturerStatus() == null ? 1 : manufacturer.getSysAiManufacturerStatus());
        manufacturer.setSysAiManufacturerSort(
                manufacturer.getSysAiManufacturerSort() == null ? 0 : manufacturer.getSysAiManufacturerSort());
        manufacturer.setCreateTime(LocalDateTime.now());
        manufacturer.setUpdateTime(LocalDateTime.now());
        sysAiManufacturerMapper.insert(manufacturer);
        return manufacturer;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:manufacturer:list'")
    public boolean update(SysAiManufacturer manufacturer) {
        if (manufacturer == null || manufacturer.getSysAiManufacturerId() == null) {
            log.warn("[AiManufacturerFacade] update 失败: ID 为空");
            return false;
        }
        // 编辑/启停时脱敏占位值或空值不覆盖真实密钥（含 **** 的占位符直接置 null，避免覆盖损坏）
        if (manufacturer.getSysAiManufacturerAppKey() != null
                && (manufacturer.getSysAiManufacturerAppKey().isBlank()
                || manufacturer.getSysAiManufacturerAppKey().contains("****"))) {
            manufacturer.setSysAiManufacturerAppKey(null);
        }
        if (manufacturer.getSysAiManufacturerApiSecret() != null
                && (manufacturer.getSysAiManufacturerApiSecret().isBlank()
                || manufacturer.getSysAiManufacturerApiSecret().contains("****"))) {
            manufacturer.setSysAiManufacturerApiSecret(null);
        }
        manufacturer.setUpdateTime(LocalDateTime.now());
        return sysAiManufacturerMapper.updateById(manufacturer) > 0;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:manufacturer:list'")
    public boolean delete(Integer id) {
        return deleteById(id);
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:manufacturer:list'")
    public boolean deleteById(Integer id) {
        if (id == null) {
            return false;
        }
        boolean result = sysAiManufacturerMapper.deleteById(id) > 0;
        if (result) {
            log.info("[AiManufacturerFacade] 删除厂商: id={}", id);
        }
        return result;
    }

    /**
     * 从厂商 API 直拉模型列表，同步到模型表（先删后插）。
     *
     * <p>使用 OpenAI 兼容的 {@code GET {baseUrl}/models} 接口拉取模型，
     * 以厂商 {@code providerId} 维度先删后插，保证模型列表与厂商最新能力一致。</p>
     *
     * @param code 厂商编码
     * @return 同步的模型数量
     */
    @Override
    public int syncModels(String code) {
        if (code == null || code.isBlank()) {
            log.warn("[AiManufacturerFacade] syncModels 失败: 厂商编码为空");
            return 0;
        }
        SysAiManufacturer manufacturer = sysAiManufacturerMapper.selectOne(
                new LambdaQueryWrapper<SysAiManufacturer>()
                        .eq(SysAiManufacturer::getSysAiManufacturerCode, code));
        if (manufacturer == null) {
            log.warn("[AiManufacturerFacade] syncModels 失败: 厂商 {} 不存在", code);
            return 0;
        }
        String baseUrl = manufacturer.getSysAiManufacturerBaseUrl();
        String apiKey = manufacturer.getSysAiManufacturerAppKey() != null
                ? manufacturer.getSysAiManufacturerAppKey()
                : manufacturer.getSysAiManufacturerApiSecret();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("[AiManufacturerFacade] syncModels 失败: 厂商 {} 未配置 baseUrl", code);
            return 0;
        }
        String modelsUrl = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";
        List<String> modelNames = fetchModels(modelsUrl, apiKey);
        if (modelNames.isEmpty()) {
            log.warn("[AiManufacturerFacade] syncModels 失败: 厂商 {} 未返回模型列表（url={}）", code, modelsUrl);
            return 0;
        }

        // 先物理删除该厂商已有模型，再插入最新模型（避免逻辑删除残留导致的唯一索引冲突）
        aiModelMapper.physicalDeleteByProviderId(manufacturer.getSysAiManufacturerId().longValue());
        int inserted = 0;
        for (String modelName : modelNames) {
            AiModel model = new AiModel();
            model.setModelCode(modelName);
            model.setModelName(modelName);
            model.setModelProviderId(manufacturer.getSysAiManufacturerId().longValue());
            model.setModelEnabled(Boolean.TRUE);
            model.setModelSort(inserted);
            aiModelMapper.insert(model);
            inserted++;
        }
        log.info("[AiManufacturerFacade] 厂商 {} 模型同步完成，共 {} 个模型", code, inserted);
        return inserted;
    }

    /**
     * 调用 OpenAI 兼容的 {@code /models} 接口拉取模型 ID 列表。
     *
     * @param url    models 接口地址
     * @param apiKey 厂商 API Key（可能为 null）
     * @return 模型 ID 列表
     */
    private List<String> fetchModels(String url, String apiKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .GET();
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[AiManufacturerFacade] 拉取模型失败: url={}, status={}", url, response.statusCode());
                return List.of();
            }
            return parseModelIds(response.body());
        } catch (Exception e) {
            log.warn("[AiManufacturerFacade] 拉取模型异常: url={}, msg={}", url, e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 OpenAI 兼容的模型列表响应，提取 data[].id。
     *
     * @param body 响应体
     * @return 模型 ID 列表
     */
    private List<String> parseModelIds(String body) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(body);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return List.of();
            }
            List<String> ids = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode idNode = item.get("id");
                if (idNode != null && idNode.isTextual() && !idNode.asText().isBlank()) {
                    ids.add(idNode.asText());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("[AiManufacturerFacade] 解析模型列表失败: {}", e.getMessage());
            return List.of();
        }
    }
}
