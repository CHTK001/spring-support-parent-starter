package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.client.GlobalChatClientManager;
import com.chua.starter.ai.support.entity.settings.AiProvider;
import com.chua.starter.ai.support.mapper.settings.AiProviderMapper;
import com.chua.starter.ai.support.model.AiProviderDTO;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
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
import java.util.Map;
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
     * runtime 全局配置分组
     */
    private static final String RUNTIME_GROUP = "runtime";

    /**
     * 供应商 Mapper
     */
    private final AiProviderMapper providerMapper;

    /**
     * 键值全局设置服务
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * 全局 ChatClient 管理器（激活后热重建）
     */
    private final GlobalChatClientManager globalChatClientManager;

    /**
     * Jackson JSON 序列化器（注入框架实例）
     */
    private final ObjectMapper mapper;

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
                .map(p -> {
                    AiProviderDTO dto = toDto(p);
                    // 列表接口密钥脱敏，防止明文泄露
                    if (dto != null) {
                        dto.setApiKey(maskSecret(dto.getApiKey()));
                    }
                    return dto;
                })
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
        AiProvider existing = providerMapper.selectById(providerId);
        if (existing == null) {
            return null;
        }
        AiProvider entity = toEntity(provider);
        // 编辑时 apiKey 为空或为脱敏占位值则不覆盖，保留真实密钥
        if (entity.getProviderApiKey() == null || entity.getProviderApiKey().isBlank()
                || entity.getProviderApiKey().contains("****")) {
            entity.setProviderApiKey(existing.getProviderApiKey());
        }
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
     * 启用供应商为 OpenAI 兼容服务：将供应商连接信息写入 runtime 分组键值配置，
     * 开启 OpenAI REST 端口与 AppKey 鉴权开关，并热重建全局 ChatClient。
     *
     * @param providerId 供应商主键
     * @return 更新后的供应商 DTO
     */
    @Override
    @Transactional
    public AiProviderDTO activate(Long providerId) {
        AiProvider provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new IllegalStateException("供应商不存在: id=" + providerId);
        }
        if (StringUtils.isBlank(provider.getProviderBaseUrl())) {
            throw new IllegalStateException("供应商缺少 Base URL，无法启用服务");
        }
        if (StringUtils.isBlank(provider.getProviderApiKey())) {
            throw new IllegalStateException("供应商缺少 API Key，无法启用服务");
        }
        List<String> models = parseList(provider.getProviderModels());
        if (models.isEmpty()) {
            throw new IllegalStateException("供应商未配置可用模型，无法启用服务");
        }

        // 组装 runtime 分组键值配置（先删后插，全量写入）
        String baseUrl = provider.getProviderBaseUrl();
        // OpenAI 兼容标准要求 baseUrl 指向 /v1 根路径；未带 /v1 时自动补齐
        if (baseUrl != null && !baseUrl.endsWith("/")) {
            if (!baseUrl.endsWith("/v1") && !baseUrl.contains("/v1/")) {
                baseUrl = baseUrl + "/v1";
            }
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        addField(fields, "provider", provider.getProviderType() != null ? provider.getProviderType() : "openai");
        addField(fields, "factory", provider.getProviderType() != null ? provider.getProviderType() : "openai");
        addField(fields, "provider_base_url", baseUrl);
        addField(fields, "provider_api_key", provider.getProviderApiKey());
        addField(fields, "default_model", models.get(0));
        addField(fields, "openaiRestEnabled", "true");
        addField(fields, "app_key_enabled", "true");
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("group", RUNTIME_GROUP);
        request.put("fields", fields);
        sysAiGlobalSettingService.replace(request);
        log.info("[AiProvider] 供应商 {} 已激活为 OpenAI 兼容服务: baseUrl={}, model={}",
                provider.getProviderName(), baseUrl, models.get(0));

        // 确保供应商启用
        if (!Boolean.TRUE.equals(provider.getProviderEnabled())) {
            LambdaUpdateWrapper<AiProvider> enableWrapper = new LambdaUpdateWrapper<AiProvider>()
                    .eq(AiProvider::getProviderId, providerId)
                    .set(AiProvider::getProviderEnabled, true)
                    .set(AiProvider::getUpdateTime, LocalDateTime.now());
            providerMapper.update(null, enableWrapper);
        }

        // 热重建全局 ChatClient，使新配置立即生效
        globalChatClientManager.rebuild();
        return toDto(providerMapper.selectById(providerId));
    }

    /**
     * 组装单条 runtime 键值配置项。
     *
     * @param fields 配置项列表
     * @param field  字段名
     * @param value  字段值
     */
    private void addField(List<Map<String, Object>> fields, String field, String value) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("field", field);
        item.put("value", value);
        item.put("valueType", "STRING");
        item.put("description", field);
        fields.add(item);
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
            return mapper.readValue(content, new TypeReference<List<String>>() {
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
