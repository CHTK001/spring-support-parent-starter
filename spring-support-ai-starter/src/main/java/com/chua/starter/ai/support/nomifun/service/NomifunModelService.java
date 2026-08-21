package com.chua.starter.ai.support.nomifun.service;

import com.chua.starter.ai.support.service.AiModelCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * nomifun 前端 Provider Model 契约适配服务。
 *
 * <p>前端期望 /api/provider-models 返回 {@code ProviderModelResponse[]}，
 * 格式：{ provider_id, model, enabled, sort_order, description, capabilities, created_at, updated_at }。</p>
 *
 * <p>当前实现：返回空列表（模型数据由独立的管理页面维护，暂不映射）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NomifunModelService {

    private final AiModelCatalogService modelCatalogService;

    /**
     * 列出提供商模型（GET /api/provider-models）。
     */
    public List<Map<String, Object>> listProviderModels(String providerId) {
        // 当前返回空列表，模型管理能力在独立的模型管理页面
        return List.of();
    }

    /**
     * 保存提供商模型（PUT /api/provider-models）。
     */
    public Map<String, Object> saveProviderModel(Map<String, Object> body) {
        log.info("[nomifun] save provider model (stub): {}", body);
        // 暂不实现，返回空
        return Map.of();
    }

    /**
     * 删除提供商模型（DELETE /api/provider-models?provider_id=&model=）。
     */
    public void deleteProviderModel(String providerId, String model) {
        log.info("[nomifun] delete provider model (stub): provider={}, model={}", providerId, model);
    }
}
