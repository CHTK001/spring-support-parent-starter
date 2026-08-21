package com.chua.starter.ai.support.nomifun.service;

import com.chua.starter.ai.support.entity.SysAiManufacturer;
import com.chua.starter.ai.support.service.AiManufacturerFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * nomifun 前端 Provider（制造商）契约适配服务。
 *
 * <p>nomifun 前端调用 {@code /api/providers/*}，期望返回 ProviderResponse 格式：
 * <pre>
 *   { code, name, model_list: [...], status, ... }
 * </pre>
 * 本服务将现有 {@link AiManufacturerFacadeService} 的 {@link SysAiManufacturer}
 * 实体转换为前端期望的 JSON 格式，并完成 CRUD 映射。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NomifunProviderService {

    private final AiManufacturerFacadeService manufacturerService;

    /**
     * 列出所有提供商（GET /api/providers）。
     */
    public List<Map<String, Object>> listProviders() {
        List<SysAiManufacturer> manufacturers = manufacturerService.list();
        if (manufacturers == null || manufacturers.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiManufacturer m : manufacturers) {
            result.add(toProviderResponse(m));
        }
        return result;
    }

    /**
     * 创建提供商（POST /api/providers）。
     */
    public Map<String, Object> createProvider(Map<String, Object> body) {
        SysAiManufacturer m = new SysAiManufacturer();
        m.setSysAiManufacturerCode(str(body, "code"));
        m.setSysAiManufacturerName(str(body, "name"));
        m.setSysAiManufacturerFactory(str(body, "factory"));
        m.setSysAiManufacturerBaseUrl(str(body, "base_url"));
        m.setSysAiManufacturerAppKey(str(body, "app_key"));
        m.setSysAiManufacturerApiSecret(str(body, "secret"));
        SysAiManufacturer saved = manufacturerService.save(m);
        log.info("[nomifun] created provider: id={} code={}", saved.getSysAiManufacturerId(), saved.getSysAiManufacturerCode());
        return toProviderResponse(saved);
    }

    /**
     * 获取单个提供商（GET /api/providers/{id}）。
     */
    public Map<String, Object> getProvider(String id) {
        try {
            Integer iid = Integer.parseInt(id);
            List<SysAiManufacturer> all = manufacturerService.list();
            return all.stream()
                    .filter(m -> m.getSysAiManufacturerId() != null && m.getSysAiManufacturerId().equals(iid))
                    .findFirst()
                    .map(this::toProviderResponse)
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 更新提供商（PATCH /api/providers/{id}）。
     */
    public Map<String, Object> updateProvider(String id, Map<String, Object> body) {
        try {
            Integer iid = Integer.parseInt(id);
            List<SysAiManufacturer> all = manufacturerService.list();
            SysAiManufacturer m = all.stream()
                    .filter(x -> x.getSysAiManufacturerId() != null && x.getSysAiManufacturerId().equals(iid))
                    .findFirst().orElse(null);
            if (m == null) return null;
            if (body.get("name") != null) m.setSysAiManufacturerName(str(body, "name"));
            if (body.get("factory") != null) m.setSysAiManufacturerFactory(str(body, "factory"));
            if (body.get("base_url") != null) m.setSysAiManufacturerBaseUrl(str(body, "base_url"));
            manufacturerService.update(m);
            return toProviderResponse(m);
        } catch (Exception e) {
            log.warn("[nomifun] update provider failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 删除提供商（DELETE /api/providers/{id}）。
     */
    public boolean deleteProvider(String id) {
        try {
            Integer iid = Integer.parseInt(id);
            return manufacturerService.delete(iid);
        } catch (Exception e) {
            log.warn("[nomifun] delete provider failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 克隆提供商（POST /api/providers/{id}/clone）。
     */
    public Map<String, Object> cloneProvider(String id) {
        try {
            Integer iid = Integer.parseInt(id);
            List<SysAiManufacturer> all = manufacturerService.list();
            SysAiManufacturer source = all.stream()
                    .filter(m -> m.getSysAiManufacturerId() != null && m.getSysAiManufacturerId().equals(iid))
                    .findFirst().orElse(null);
            if (source == null) return null;
            SysAiManufacturer clone = new SysAiManufacturer();
            clone.setSysAiManufacturerCode(source.getSysAiManufacturerCode() + "-copy");
            clone.setSysAiManufacturerName(source.getSysAiManufacturerName() + " (副本)");
            clone.setSysAiManufacturerFactory(source.getSysAiManufacturerFactory());
            clone.setSysAiManufacturerBaseUrl(source.getSysAiManufacturerBaseUrl());
            clone.setSysAiManufacturerAppKey(source.getSysAiManufacturerAppKey());
            clone.setSysAiManufacturerApiSecret(source.getSysAiManufacturerApiSecret());
            return toProviderResponse(manufacturerService.save(clone));
        } catch (Exception e) {
            log.warn("[nomifun] clone provider failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将实体转换为前端期望的 ProviderResponse。
     */
    private Map<String, Object> toProviderResponse(SysAiManufacturer m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", m.getSysAiManufacturerCode() == null ? "" : m.getSysAiManufacturerCode());
        r.put("name", m.getSysAiManufacturerName() == null ? "" : m.getSysAiManufacturerName());
        r.put("factory", m.getSysAiManufacturerFactory() == null ? "" : m.getSysAiManufacturerFactory());
        r.put("base_url", m.getSysAiManufacturerBaseUrl() == null ? "" : m.getSysAiManufacturerBaseUrl());
        r.put("status", 1); // 默认在线
        r.put("model_list", List.of()); // 模型列表由单独的接口提供
        r.put("provider_id", String.valueOf(m.getSysAiManufacturerId()));
        r.put("created_at", System.currentTimeMillis());
        r.put("updated_at", System.currentTimeMillis());
        return r;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
