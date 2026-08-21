package com.chua.starter.ai.support.nomifun.api;

import com.chua.starter.ai.support.nomifun.common.ApiErrorCode;
import com.chua.starter.ai.support.nomifun.common.ApiException;
import com.chua.starter.ai.support.nomifun.common.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 前端 Provider / 模型 / 协议管理控制器（对接 nomicore 契约）。
 *
 * <p>替代原 Rust 后端的厂商管理能力，提供 providers、provider-models、provider 连接、
 * 托管免费模型服务以及模型协议清单四类纯 Spring 接口。所有存储采用内存
 * {@link ConcurrentHashMap} 维护（现有 {@code AiManufacturer*} 数据模型与 nomicore 契约
 * 不兼容，按任务约定以内存 Map 实现），响应始终为 {@link ApiResult} 信封且字段为 snake_case。</p>
 *
 * <p>凭证字段（credentials）写入后不回显，响应仅以 {@code has_credentials} 布尔标识是否存在。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiProviderController {

    /**
     * Provider 实体存储（key = provider_id）。
     */
    private final ConcurrentHashMap<String, ProviderEntity> providers = new ConcurrentHashMap<>();

    /**
     * 托管免费模型服务运行时状态。
     */
    private final FreeModelService freeModelService = new FreeModelService();

    // ==================== Providers ====================

    /**
     * 列举全部 Provider（按 sort_order 升序）。
     *
     * @return ProviderResponse 列表
     */
    @GetMapping("/providers")
    public ApiResult<List<Map<String, Object>>> listProviders() {
        List<Map<String, Object>> items = providers.values().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(a.sortOrder, b.sortOrder);
                    return c != 0 ? c : Long.compare(a.createdAt, b.createdAt);
                })
                .map(this::toProviderResponse)
                .collect(Collectors.toList());
        return ApiResult.ok(items);
    }

    /**
     * 创建 Provider（原子包含 initial_model 与可选 connections）。
     *
     * @param body 创建请求
     * @return 创建后的 ProviderResponse
     */
    @PostMapping("/providers")
    public ApiResult<Map<String, Object>> createProvider(@RequestBody Map<String, Object> body) {
        String providerId = str(body, "provider_id");
        if (providerId == null || providerId.isBlank()) {
            providerId = UUID.randomUUID().toString().replace("-", "");
        }
        if (providers.containsKey(providerId)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "provider_id 已存在: " + providerId);
        }
        long now = System.currentTimeMillis();
        ProviderEntity entity = new ProviderEntity();
        entity.providerId = providerId;
        entity.platform = requireStr(body, "platform");
        entity.name = requireStr(body, "name");
        entity.baseUrl = requireStr(body, "base_url");
        entity.authScheme = requireStr(body, "auth_scheme");
        entity.credentials = uncheckedCast(body.get("credentials"));
        entity.enabled = bool(body, "enabled", true);
        entity.bedrockConfig = uncheckedCast(body.get("bedrock_config"));
        entity.sortOrder = intv(body, "sort_order", providers.size());
        entity.createdAt = now;
        entity.updatedAt = now;

        // 初始模型（原子创建必须包含至少一条可用模型）
        Map<String, Object> initialModel = uncheckedCast(body.get("initial_model"));
        if (initialModel == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "initial_model 不能为空");
        }
        entity.models.put(requireStr(initialModel, "model"), toModelEntity(initialModel, now));

        // 初始连接（按 role 建表）
        List<Map<String, Object>> connections = listOfMap(body, "connections");
        for (Map<String, Object> conn : connections) {
            String role = requireStr(conn, "role");
            entity.connections.put(role, toConnectionEntity(entity.providerId, conn, now));
        }

        providers.put(providerId, entity);
        return ApiResult.ok(toProviderResponse(entity));
    }

    /**
     * 更新 Provider（凭证缺省时保留既有凭证）。
     *
     * @param providerId Provider 标识
     * @param body       更新请求
     * @return 更新后的 ProviderResponse
     */
    @PutMapping("/providers/{provider_id}")
    public ApiResult<Map<String, Object>> updateProvider(@PathVariable("provider_id") String providerId,
                                                         @RequestBody Map<String, Object> body) {
        ProviderEntity entity = requireProvider(providerId);
        if (body.containsKey("name")) {
            entity.name = str(body, "name");
        }
        if (body.containsKey("base_url")) {
            entity.baseUrl = str(body, "base_url");
        }
        if (body.containsKey("auth_scheme")) {
            entity.authScheme = str(body, "auth_scheme");
        }
        if (body.containsKey("enabled")) {
            entity.enabled = bool(body, "enabled", entity.enabled);
        }
        if (body.containsKey("sort_order")) {
            entity.sortOrder = intv(body, "sort_order", entity.sortOrder);
        }
        if (body.containsKey("bedrock_config")) {
            entity.bedrockConfig = uncheckedCast(body.get("bedrock_config"));
        }
        // credentials 缺省 → 保留已有凭证；显式传 null 视为清空
        if (body.containsKey("credentials")) {
            entity.credentials = uncheckedCast(body.get("credentials"));
        }
        entity.updatedAt = System.currentTimeMillis();
        return ApiResult.ok(toProviderResponse(entity));
    }

    /**
     * 删除 Provider。
     *
     * @param providerId Provider 标识
     * @return 成功
     */
    @DeleteMapping("/providers/{provider_id}")
    public ApiResult<Void> deleteProvider(@PathVariable("provider_id") String providerId) {
        if (providers.remove(providerId) == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider 不存在: " + providerId);
        }
        return ApiResult.ok();
    }

    /**
     * 克隆 Provider（连同其模型能力与连接一并复制）。
     *
     * @param providerId Provider 标识
     * @param body       可选 { name }
     * @return 克隆后的 ProviderResponse
     */
    @PostMapping("/providers/{provider_id}/clone")
    public ApiResult<Map<String, Object>> cloneProvider(@PathVariable("provider_id") String providerId,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        ProviderEntity source = requireProvider(providerId);
        long now = System.currentTimeMillis();
        String newId = UUID.randomUUID().toString().replace("-", "");
        ProviderEntity clone = new ProviderEntity();
        clone.providerId = newId;
        clone.platform = source.platform;
        clone.name = body != null && body.get("name") != null
                ? str(body, "name") : source.name + " 副本";
        clone.baseUrl = source.baseUrl;
        clone.authScheme = source.authScheme;
        clone.credentials = source.credentials != null ? new LinkedHashMap<>(source.credentials) : null;
        clone.enabled = source.enabled;
        clone.bedrockConfig = source.bedrockConfig != null ? new LinkedHashMap<>(source.bedrockConfig) : null;
        clone.sortOrder = providers.size();
        clone.createdAt = now;
        clone.updatedAt = now;
        // 深拷贝模型能力
        source.models.forEach((k, v) -> clone.models.put(k, copyModel(v, now)));
        // 深拷贝连接
        source.connections.forEach((k, v) -> clone.connections.put(k, copyConnection(v, newId, now)));

        providers.put(newId, clone);
        return ApiResult.ok(toProviderResponse(clone));
    }

    /**
     * 拉取 Provider 远端模型清单（需 provider 已存在）。
     *
     * @param providerId Provider 标识
     * @param body       可选 { try_fix }
     * @return FetchModelsResponse
     */
    @PostMapping("/providers/{provider_id}/models")
    public ApiResult<Map<String, Object>> fetchProviderModels(@PathVariable("provider_id") String providerId,
                                                              @RequestBody(required = false) Map<String, Object> body) {
        ProviderEntity entity = requireProvider(providerId);
        return ApiResult.ok(buildFetchModels(entity.platform, entity.baseUrl));
    }

    /**
     * 匿名预拉取模型清单（创建前预览，不需要 provider 行）。
     *
     * @param body FetchModelsAnonymousRequest
     * @return FetchModelsResponse
     */
    @PostMapping("/providers/fetch-models")
    public ApiResult<Map<String, Object>> fetchModelsAnonymous(@RequestBody Map<String, Object> body) {
        String platform = requireStr(body, "platform");
        String baseUrl = requireStr(body, "base_url");
        return ApiResult.ok(buildFetchModels(platform, baseUrl));
    }

    // ==================== Provider Connections ====================

    /**
     * 列举 Provider 的额外连接（按 role）。
     *
     * @param providerId Provider 标识
     * @return ProviderConnectionResponse 列表
     */
    @GetMapping("/providers/{provider_id}/connections")
    public ApiResult<List<Map<String, Object>>> listConnections(@PathVariable("provider_id") String providerId) {
        ProviderEntity entity = requireProvider(providerId);
        List<Map<String, Object>> items = entity.connections.values().stream()
                .sorted((a, b) -> Long.compare(a.createdAt, b.createdAt))
                .map(this::toConnectionResponse)
                .collect(Collectors.toList());
        return ApiResult.ok(items);
    }

    /**
     * 获取单条 Provider 连接。
     *
     * @param providerId Provider 标识
     * @param role       连接角色
     * @return ProviderConnectionResponse
     */
    @GetMapping("/providers/{provider_id}/connections/{role}")
    public ApiResult<Map<String, Object>> getConnection(@PathVariable("provider_id") String providerId,
                                                        @PathVariable("role") String role) {
        ProviderEntity entity = requireProvider(providerId);
        ProviderConnectionEntity conn = entity.connections.get(role);
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "connection 不存在: " + role);
        }
        return ApiResult.ok(toConnectionResponse(conn));
    }

    /**
     * 保存（新建或更新）Provider 连接。
     *
     * @param providerId Provider 标识
     * @param body       SaveProviderConnectionRequest
     * @return ProviderConnectionResponse
     */
    @PutMapping("/providers/{provider_id}/connections")
    public ApiResult<Map<String, Object>> saveConnection(@PathVariable("provider_id") String providerId,
                                                         @RequestBody Map<String, Object> body) {
        ProviderEntity entity = requireProvider(providerId);
        String role = requireStr(body, "role");
        ProviderConnectionEntity existing = entity.connections.get(role);
        if (existing == null) {
            // 新建：凭证为必填
            if (body.get("credentials") == null) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "新建连接时 credentials 必填");
            }
            entity.connections.put(role, toConnectionEntity(entity.providerId, body, System.currentTimeMillis()));
        } else {
            // 更新：仅覆盖显式出现的字段，缺省 credentials 保留既有
            if (body.containsKey("label")) {
                existing.label = str(body, "label");
            }
            if (body.containsKey("base_url")) {
                existing.baseUrl = str(body, "base_url");
            }
            if (body.containsKey("auth_scheme")) {
                existing.authScheme = str(body, "auth_scheme");
            }
            if (body.containsKey("extra")) {
                existing.extra = uncheckedCast(body.get("extra"));
            }
            if (body.containsKey("credentials")) {
                existing.credentials = uncheckedCast(body.get("credentials"));
            }
            existing.updatedAt = System.currentTimeMillis();
        }
        return ApiResult.ok(toConnectionResponse(entity.connections.get(role)));
    }

    /**
     * 删除 Provider 连接。
     *
     * @param providerId Provider 标识
     * @param role       连接角色
     * @return 成功
     */
    @DeleteMapping("/providers/{provider_id}/connections/{role}")
    public ApiResult<Void> deleteConnection(@PathVariable("provider_id") String providerId,
                                            @PathVariable("role") String role) {
        ProviderEntity entity = requireProvider(providerId);
        if (entity.connections.remove(role) == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "connection 不存在: " + role);
        }
        return ApiResult.ok();
    }

    // ==================== Provider Models ====================

    /**
     * 列举 provider_models 行（可按 provider_id 过滤）。
     *
     * @param providerId 可选过滤
     * @return ProviderModelResponse 列表
     */
    @GetMapping("/provider-models")
    public ApiResult<List<Map<String, Object>>> listProviderModels(
            @RequestParam(required = false) String provider_id) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ProviderEntity entity : providers.values()) {
            if (provider_id != null && !provider_id.equals(entity.providerId)) {
                continue;
            }
            for (ProviderModelEntity model : entity.models.values()) {
                items.add(toProviderModelResponse(entity, model));
            }
        }
        items.sort((a, b) -> Long.compare((Long) a.get("created_at"), (Long) b.get("created_at")));
        return ApiResult.ok(items);
    }

    /**
     * 全量保存（upsert）某 Provider 下某模型的完整能力集。
     *
     * @param body SaveProviderModelRequest
     * @return ProviderModelResponse
     */
    @PutMapping("/provider-models")
    public ApiResult<Map<String, Object>> saveProviderModel(@RequestBody Map<String, Object> body) {
        String providerId = requireStr(body, "provider_id");
        ProviderEntity entity = requireProvider(providerId);
        Map<String, Object> modelInput = uncheckedCast(body.get("model"));
        if (modelInput == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "model 不能为空");
        }
        String modelName = requireStr(modelInput, "model");
        ProviderModelEntity existing = entity.models.get(modelName);
        if (existing == null) {
            entity.models.put(modelName, toModelEntity(modelInput, System.currentTimeMillis()));
        } else {
            applyModelInput(existing, modelInput);
            existing.updatedAt = System.currentTimeMillis();
        }
        return ApiResult.ok(toProviderModelResponse(entity, entity.models.get(modelName)));
    }

    /**
     * 删除 provider_models 行（按复合主键 provider_id + model）。
     *
     * @param providerId Provider 标识
     * @param model      模型名
     * @return 成功
     */
    @DeleteMapping("/provider-models")
    public ApiResult<Void> deleteProviderModel(@RequestParam("provider_id") String providerId,
                                               @RequestParam("model") String model) {
        ProviderEntity entity = requireProvider(providerId);
        if (entity.models.remove(model) == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider model 不存在: " + model);
        }
        return ApiResult.ok();
    }

    // ==================== Managed Free Model Service ====================

    /**
     * 托管免费模型服务状态。
     *
     * @return ManagedModelServiceStatus
     */
    @GetMapping("/model-services/free/status")
    public ApiResult<Map<String, Object>> freeServiceStatus() {
        return ApiResult.ok(freeModelService.toStatus());
    }

    /**
     * 托管免费模型清单。
     *
     * @return ManagedModel 列表
     */
    @GetMapping("/model-services/free/models")
    public ApiResult<List<Map<String, Object>>> freeServiceModels() {
        return ApiResult.ok(freeModelService.toList());
    }

    /**
     * 刷新托管免费模型服务目录（同步上游能力，更新 lastRefresh）。
     *
     * @return 刷新后的 ManagedModelServiceStatus
     */
    @PostMapping("/model-services/free/refresh")
    public ApiResult<Map<String, Object>> freeServiceRefresh() {
        freeModelService.refresh();
        return ApiResult.ok(freeModelService.toStatus());
    }

    /**
     * 启用/停用托管免费模型服务。
     *
     * @param body SetManagedModelServiceEnabledRequest
     * @return 操作后的 ManagedModelServiceStatus
     */
    @PostMapping("/model-services/free/activate")
    public ApiResult<Map<String, Object>> freeServiceActivate(@RequestBody Map<String, Object> body) {
        freeModelService.enabled = bool(body, "enabled", true);
        freeModelService.ready = freeModelService.enabled;
        freeModelService.lastRefresh = System.currentTimeMillis();
        return ApiResult.ok(freeModelService.toStatus());
    }

    /**
     * 批量健康检查托管免费模型。
     *
     * @return ManagedModelHealthBatchResult
     */
    @PostMapping("/model-services/free/health")
    public ApiResult<Map<String, Object>> freeServiceHealth() {
        return ApiResult.ok(freeModelService.healthBatch());
    }

    /**
     * 快照式健康检查托管免费模型。
     *
     * @return ManagedModelHealthResult 列表
     */
    @GetMapping("/model-services/free/health")
    public ApiResult<List<Map<String, Object>>> freeServiceHealthSnapshot() {
        return ApiResult.ok(freeModelService.healthList());
    }

    /**
     * 启用/停用单个托管免费模型。
     *
     * @param modelId 模型标识
     * @param body    { enabled }
     * @return 操作后的 ManagedModelServiceStatus
     */
    @PatchMapping("/model-services/free/models/{model_id}")
    public ApiResult<Map<String, Object>> freeServiceSetModelEnabled(@PathVariable("model_id") String modelId,
                                                                     @RequestBody Map<String, Object> body) {
        freeModelService.setModelEnabled(modelId, bool(body, "enabled", true));
        return ApiResult.ok(freeModelService.toStatus());
    }

    /**
     * 单个托管免费模型健康检查。
     *
     * @param modelId 模型标识
     * @return ManagedModelHealthResult
     */
    @PostMapping("/model-services/free/models/{model_id}/health")
    public ApiResult<Map<String, Object>> freeServiceModelHealth(@PathVariable("model_id") String modelId) {
        return ApiResult.ok(freeModelService.healthOne(modelId));
    }

    // ==================== Model Protocols ====================

    /**
     * 模型协议能力清单（服务端运营默认）。
     *
     * @param preset  平台预设
     * @param task    请求任务
     * @param baseUrl 已存在 provider URL 提示
     * @return ModelProtocolManifestResponse
     */
    @GetMapping("/model-protocols")
    public ApiResult<Map<String, Object>> modelProtocols(@RequestParam String preset,
                                                         @RequestParam String task,
                                                         @RequestParam(required = false) String base_url) {
        return ApiResult.ok(buildManifest(preset, task, base_url));
    }

    // ==================== 内部实体 ====================

    /**
     * Provider 内存实体。
     */
    static class ProviderEntity {
        String providerId;
        String platform;
        String name;
        String baseUrl;
        String authScheme;
        Map<String, Object> credentials;
        boolean enabled;
        int sortOrder;
        long createdAt;
        long updatedAt;
        Map<String, Object> bedrockConfig;
        final LinkedHashMap<String, ProviderModelEntity> models = new LinkedHashMap<>();
        final LinkedHashMap<String, ProviderConnectionEntity> connections = new LinkedHashMap<>();
    }

    /**
     * Provider 模型能力实体。
     */
    static class ProviderModelEntity {
        String model;
        boolean enabled;
        int sortOrder;
        String description;
        final List<ProviderModelCapabilityEntity> capabilities = new ArrayList<>();
        long createdAt;
        long updatedAt;
    }

    /**
     * 单任务能力实体。
     */
    static class ProviderModelCapabilityEntity {
        String task;
        final List<String> traits = new ArrayList<>();
        String protocol;
        String connectionRole;
        String baseUrlOverride;
        String endpoint;
        String pollEndpoint;
        String contentEndpoint;
        String realtimeEndpoint;
        boolean allowCrossOriginCredentials;
        Object providerParams;
        Integer contextLimit;
        long createdAt;
        long updatedAt;
    }

    /**
     * Provider 附加连接实体（凭证不回显）。
     */
    static class ProviderConnectionEntity {
        String connectionId;
        String providerId;
        String role;
        String label;
        String baseUrl;
        String authScheme;
        Map<String, Object> credentials;
        Object extra;
        long createdAt;
        long updatedAt;
    }

    /**
     * 托管免费模型。
     */
    static class ManagedModel {
        String id;
        String name;
        boolean enabled;
        String source;
    }

    /**
     * 托管免费模型服务运行时状态。
     */
    static class FreeModelService {
        boolean enabled = false;
        boolean ready = false;
        Long lastRefresh = null;
        String lastError = null;
        final LinkedHashMap<String, ManagedModel> models = new LinkedHashMap<>();

        FreeModelService() {
            ManagedModel m1 = new ManagedModel();
            m1.id = "nomifun-free-gpt";
            m1.name = "NomiFun Free GPT";
            m1.enabled = true;
            m1.source = "openai";
            ManagedModel m2 = new ManagedModel();
            m2.id = "nomifun-free-claude";
            m2.name = "NomiFun Free Claude";
            m2.enabled = true;
            m2.source = "anthropic";
            models.put(m1.id, m1);
            models.put(m2.id, m2);
        }

        void refresh() {
            this.lastRefresh = System.currentTimeMillis();
            this.ready = true;
            this.lastError = null;
        }

        void setModelEnabled(String modelId, boolean enabled) {
            ManagedModel m = models.get(modelId);
            if (m == null) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "free model 不存在: " + modelId);
            }
            m.enabled = enabled;
        }

        List<Map<String, Object>> toList() {
            List<Map<String, Object>> out = new ArrayList<>();
            for (ManagedModel m : models.values()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", m.id);
                item.put("name", m.name);
                item.put("enabled", m.enabled);
                item.put("source", m.source);
                out.add(item);
            }
            return out;
        }

        Map<String, Object> toStatus() {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("protocolVersion", "openai-v1");
            status.put("providerId", null);
            status.put("enabled", enabled);
            status.put("ready", ready);
            status.put("upstream", "https://free.nomifun.com/v1");
            status.put("models", toList());
            status.put("lastRefresh", lastRefresh);
            status.put("lastError", lastError);
            status.put("automaticRefresh", true);
            status.put("refreshIntervalMs", 3_600_000L);
            status.put("nextRefresh", null);
            status.put("privacyNotice", "托管免费模型由 NomiFun 统一代理，请求内容不会用于训练；请遵守上游厂商使用条款。");
            status.put("availability", ready ? "ready" : "unverified");
            return status;
        }

        Map<String, Object> healthOne(String modelId) {
            ManagedModel m = models.get(modelId);
            if (m == null) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "free model 不存在: " + modelId);
            }
            return buildHealth(m);
        }

        Map<String, Object> buildHealth(ManagedModel m) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("modelId", m.id);
            r.put("status", m.enabled ? "healthy" : "unhealthy");
            r.put("latencyMs", null);
            r.put("checkedAt", System.currentTimeMillis());
            r.put("errorKind", m.enabled ? null : "model_disabled");
            r.put("message", m.enabled ? "模型可用" : "模型已停用");
            return r;
        }

        List<Map<String, Object>> healthList() {
            return models.values().stream().map(this::buildHealth).collect(Collectors.toList());
        }

        Map<String, Object> healthBatch() {
            List<Map<String, Object>> list = healthList();
            int total = list.size();
            int healthy = 0;
            int unhealthy = 0;
            int unknown = 0;
            for (Map<String, Object> r : list) {
                String s = String.valueOf(r.get("status"));
                if ("healthy".equals(s)) {
                    healthy++;
                } else if ("unhealthy".equals(s)) {
                    unhealthy++;
                } else {
                    unknown++;
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("results", list);
            out.put("total", total);
            out.put("healthy", healthy);
            out.put("unhealthy", unhealthy);
            out.put("unknown", unknown);
            return out;
        }
    }

    // ==================== 响应构建 ====================

    /**
     * 构造 ProviderResponse（不回显 credentials）。
     *
     * @param entity Provider 实体
     * @return 响应 Map
     */
    private Map<String, Object> toProviderResponse(ProviderEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("provider_id", entity.providerId);
        map.put("platform", entity.platform);
        map.put("name", entity.name);
        map.put("base_url", entity.baseUrl);
        map.put("auth_scheme", entity.authScheme);
        map.put("has_credentials", entity.credentials != null && !entity.credentials.isEmpty());
        List<Map<String, Object>> models = entity.models.values().stream()
                .map(m -> toProviderModelResponse(entity, m))
                .collect(Collectors.toList());
        map.put("models", models);
        map.put("enabled", entity.enabled);
        if (entity.bedrockConfig != null) {
            map.put("bedrock_config", entity.bedrockConfig);
        }
        map.put("sort_order", entity.sortOrder);
        map.put("created_at", entity.createdAt);
        map.put("updated_at", entity.updatedAt);
        return map;
    }

    /**
     * 构造 ProviderModelResponse。
     *
     * @param entity Provider 实体
     * @param model  模型实体
     * @return 响应 Map
     */
    private Map<String, Object> toProviderModelResponse(ProviderEntity entity, ProviderModelEntity model) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("provider_id", entity.providerId);
        map.put("model", model.model);
        map.put("enabled", model.enabled);
        map.put("sort_order", model.sortOrder);
        if (model.description != null) {
            map.put("description", model.description);
        }
        List<Map<String, Object>> caps = model.capabilities.stream()
                .map(this::toCapabilityResponse)
                .collect(Collectors.toList());
        map.put("capabilities", caps);
        map.put("created_at", model.createdAt);
        map.put("updated_at", model.updatedAt);
        return map;
    }

    /**
     * 构造单任务能力响应。
     *
     * @param cap 能力实体
     * @return 响应 Map
     */
    private Map<String, Object> toCapabilityResponse(ProviderModelCapabilityEntity cap) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("task", cap.task);
        map.put("traits", new ArrayList<>(cap.traits));
        map.put("protocol", cap.protocol);
        map.put("connection_role", cap.connectionRole);
        if (cap.baseUrlOverride != null) {
            map.put("base_url_override", cap.baseUrlOverride);
        }
        if (cap.endpoint != null) {
            map.put("endpoint", cap.endpoint);
        }
        if (cap.pollEndpoint != null) {
            map.put("poll_endpoint", cap.pollEndpoint);
        }
        if (cap.contentEndpoint != null) {
            map.put("content_endpoint", cap.contentEndpoint);
        }
        if (cap.realtimeEndpoint != null) {
            map.put("realtime_endpoint", cap.realtimeEndpoint);
        }
        map.put("allow_cross_origin_credentials", cap.allowCrossOriginCredentials);
        map.put("provider_params", cap.providerParams);
        if (cap.contextLimit != null) {
            map.put("context_limit", cap.contextLimit);
        }
        map.put("created_at", cap.createdAt);
        map.put("updated_at", cap.updatedAt);
        return map;
    }

    /**
     * 构造 ProviderConnectionResponse（不回显 credentials）。
     *
     * @param conn 连接实体
     * @return 响应 Map
     */
    private Map<String, Object> toConnectionResponse(ProviderConnectionEntity conn) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("connection_id", conn.connectionId);
        map.put("provider_id", conn.providerId);
        map.put("role", conn.role);
        if (conn.label != null) {
            map.put("label", conn.label);
        }
        map.put("base_url", conn.baseUrl);
        map.put("auth_scheme", conn.authScheme);
        map.put("has_credentials", conn.credentials != null && !conn.credentials.isEmpty());
        map.put("extra", conn.extra);
        map.put("created_at", conn.createdAt);
        map.put("updated_at", conn.updatedAt);
        return map;
    }

    // ==================== 实体构建 ====================

    /**
     * 由模型输入构造模型实体（新建）。
     *
     * @param input 模型输入
     * @param now   时间戳
     * @return 模型实体
     */
    private ProviderModelEntity toModelEntity(Map<String, Object> input, long now) {
        ProviderModelEntity e = new ProviderModelEntity();
        e.model = requireStr(input, "model");
        e.enabled = bool(input, "enabled", true);
        e.sortOrder = intv(input, "sort_order", 0);
        e.description = str(input, "description");
        applyCapabilities(e, input, now);
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /**
     * 把模型输入应用到已有实体（更新，替换完整能力集）。
     *
     * @param e     模型实体
     * @param input 模型输入
     */
    private void applyModelInput(ProviderModelEntity e, Map<String, Object> input) {
        if (input.containsKey("enabled")) {
            e.enabled = bool(input, "enabled", e.enabled);
        }
        if (input.containsKey("sort_order")) {
            e.sortOrder = intv(input, "sort_order", e.sortOrder);
        }
        if (input.containsKey("description")) {
            e.description = str(input, "description");
        }
        applyCapabilities(e, input, System.currentTimeMillis());
    }

    /**
     * 重建模型能力集。
     *
     * @param e     模型实体
     * @param input 模型输入
     * @param now   时间戳
     */
    private void applyCapabilities(ProviderModelEntity e, Map<String, Object> input, long now) {
        e.capabilities.clear();
        List<Map<String, Object>> caps = listOfMap(input, "capabilities");
        for (Map<String, Object> c : caps) {
            ProviderModelCapabilityEntity cap = new ProviderModelCapabilityEntity();
            cap.task = requireStr(c, "task");
            cap.traits.addAll(listOfStr(c, "traits"));
            cap.protocol = requireStr(c, "protocol");
            cap.connectionRole = requireStr(c, "connection_role");
            cap.baseUrlOverride = str(c, "base_url_override");
            cap.endpoint = str(c, "endpoint");
            cap.pollEndpoint = str(c, "poll_endpoint");
            cap.contentEndpoint = str(c, "content_endpoint");
            cap.realtimeEndpoint = str(c, "realtime_endpoint");
            cap.allowCrossOriginCredentials = bool(c, "allow_cross_origin_credentials", false);
            cap.providerParams = c.get("provider_params");
            if (c.containsKey("context_limit")) {
                cap.contextLimit = intv(c, "context_limit", 0);
            }
            cap.createdAt = now;
            cap.updatedAt = now;
            e.capabilities.add(cap);
        }
    }

    /**
     * 由连接输入构造连接实体（新建）。
     *
     * @param providerId Provider 标识
     * @param input      连接输入
     * @param now        时间戳
     * @return 连接实体
     */
    private ProviderConnectionEntity toConnectionEntity(String providerId, Map<String, Object> input, long now) {
        ProviderConnectionEntity e = new ProviderConnectionEntity();
        e.connectionId = UUID.randomUUID().toString().replace("-", "");
        e.providerId = providerId;
        e.role = requireStr(input, "role");
        e.label = str(input, "label");
        e.baseUrl = requireStr(input, "base_url");
        e.authScheme = requireStr(input, "auth_scheme");
        e.credentials = uncheckedCast(input.get("credentials"));
        e.extra = input.get("extra");
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /**
     * 深拷贝模型实体。
     *
     * @param src 源实体
     * @param now 时间戳
     * @return 副本
     */
    private ProviderModelEntity copyModel(ProviderModelEntity src, long now) {
        ProviderModelEntity e = new ProviderModelEntity();
        e.model = src.model;
        e.enabled = src.enabled;
        e.sortOrder = src.sortOrder;
        e.description = src.description;
        for (ProviderModelCapabilityEntity c : src.capabilities) {
            ProviderModelCapabilityEntity nc = new ProviderModelCapabilityEntity();
            nc.task = c.task;
            nc.traits.addAll(c.traits);
            nc.protocol = c.protocol;
            nc.connectionRole = c.connectionRole;
            nc.baseUrlOverride = c.baseUrlOverride;
            nc.endpoint = c.endpoint;
            nc.pollEndpoint = c.pollEndpoint;
            nc.contentEndpoint = c.contentEndpoint;
            nc.realtimeEndpoint = c.realtimeEndpoint;
            nc.allowCrossOriginCredentials = c.allowCrossOriginCredentials;
            nc.providerParams = c.providerParams;
            nc.contextLimit = c.contextLimit;
            nc.createdAt = now;
            nc.updatedAt = now;
            e.capabilities.add(nc);
        }
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /**
     * 深拷贝连接实体。
     *
     * @param src        源实体
     * @param providerId 新 Provider 标识
     * @param now        时间戳
     * @return 副本
     */
    private ProviderConnectionEntity copyConnection(ProviderConnectionEntity src, String providerId, long now) {
        ProviderConnectionEntity e = new ProviderConnectionEntity();
        e.connectionId = UUID.randomUUID().toString().replace("-", "");
        e.providerId = providerId;
        e.role = src.role;
        e.label = src.label;
        e.baseUrl = src.baseUrl;
        e.authScheme = src.authScheme;
        e.credentials = src.credentials != null ? new LinkedHashMap<>(src.credentials) : null;
        e.extra = src.extra;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    // ==================== 模型协议清单 ====================

    /**
     * 构造模型协议清单响应。
     *
     * @param preset  平台预设
     * @param task    请求任务
     * @param baseUrl 已存在 provider URL 提示
     * @return 清单 Map
     */
    private Map<String, Object> buildManifest(String preset, String task, String baseUrl) {
        ProtocolPreset pp = PROTOCOL_PRESETS.getOrDefault(preset,
                new ProtocolPreset(preset, "https://api.openai.com/v1"));
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("tasks", ALL_TASKS);
        manifest.put("preset", preset);
        manifest.put("platform", pp.platform);
        manifest.put("requested_task", task);
        manifest.put("platform_default_base_url", baseUrl != null ? baseUrl : pp.defaultBaseUrl);
        manifest.put("requires_user_input", false);
        manifest.put("default_auth_scheme", "api_key");
        manifest.put("auth_schemes", authSchemes());

        Map<String, Object> recommendation = new LinkedHashMap<>();
        recommendation.put("protocol_id", pp.platform + "-openai-compat");
        recommendation.put("connection_role", "default");
        recommendation.put("default_base_url", pp.defaultBaseUrl);
        recommendation.put("default_auth_scheme", "api_key");
        recommendation.put("base_url_override_required", false);
        manifest.put("recommendation", recommendation);

        manifest.put("protocols", List.of(buildProtocol(pp, task)));
        return manifest;
    }

    /**
     * 构造单协议描述符。
     *
     * @param pp  协议预设
     * @param task 请求任务
     * @return 协议 Map
     */
    private Map<String, Object> buildProtocol(ProtocolPreset pp, String task) {
        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("protocol_id", pp.platform + "-openai-compat");
        protocol.put("supported_tasks", ALL_TASKS);
        protocol.put("executor", "model_invoke");
        protocol.put("transport", "http");
        protocol.put("allowed_auth_schemes", List.of("api_key", "bearer", "oauth", "none"));
        protocol.put("scopes", List.of("official_compat", "custom"));
        protocol.put("platforms", List.of(pp.platform));

        Map<String, Object> dc = new LinkedHashMap<>();
        dc.put("preset", pp.platform);
        dc.put("platform", pp.platform);
        dc.put("connection_role", "default");
        dc.put("connection_label", null);
        dc.put("base_url", pp.defaultBaseUrl);
        dc.put("auth_scheme", "api_key");
        dc.put("requires_credentials", true);
        protocol.put("default_connections", List.of(dc));
        protocol.put("endpoints", List.of());
        return protocol;
    }

    /**
     * 构造鉴权方案清单。
     *
     * @return 鉴权方案列表
     */
    private List<Map<String, Object>> authSchemes() {
        return List.of(
                authScheme("api_key", true),
                authScheme("bearer", true),
                authScheme("oauth", true),
                authScheme("none", false)
        );
    }

    /**
     * 构造单鉴权方案。
     *
     * @param scheme        方案名
     * @param parameterized 是否参数化
     * @return 方案 Map
     */
    private Map<String, Object> authScheme(String scheme, boolean parameterized) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scheme", scheme);
        m.put("parameterized", parameterized);
        return m;
    }

    /**
     * 平台预设记录。
     *
     * @param platform      平台名
     * @param defaultBaseUrl 默认 base_url
     */
    private record ProtocolPreset(String platform, String defaultBaseUrl) {
    }

    /**
     * 全部任务枚举。
     */
    private static final List<String> ALL_TASKS = List.of(
            "chat", "realtime_conversation", "image_generation", "image_edit",
            "video_generation", "speech_synthesis", "speech_recognition",
            "embedding", "rerank"
    );

    /**
     * 平台 → 协议预设映射。
     */
    private static final Map<String, ProtocolPreset> PROTOCOL_PRESETS;

    static {
        Map<String, ProtocolPreset> m = new LinkedHashMap<>();
        m.put("openai", new ProtocolPreset("openai", "https://api.openai.com/v1"));
        m.put("anthropic", new ProtocolPreset("anthropic", "https://api.anthropic.com/v1"));
        m.put("google", new ProtocolPreset("google", "https://generativelanguage.googleapis.com/v1beta"));
        m.put("azure", new ProtocolPreset("azure", "https://api.openai.azure.com"));
        m.put("siliconflow", new ProtocolPreset("siliconflow", "https://api.siliconflow.cn/v1"));
        m.put("deepseek", new ProtocolPreset("deepseek", "https://api.deepseek.com/v1"));
        m.put("qwen", new ProtocolPreset("qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        m.put("moonshot", new ProtocolPreset("moonshot", "https://api.moonshot.cn/v1"));
        m.put("grok", new ProtocolPreset("grok", "https://api.x.ai/v1"));
        m.put("ollama", new ProtocolPreset("ollama", "http://localhost:11434/v1"));
        m.put("nomifun-free-model", new ProtocolPreset("nomifun-free-model", "https://free.nomifun.com/v1"));
        PROTOCOL_PRESETS = Map.copyOf(m);
    }

    // ==================== 模型拉取 ====================

    /**
     * 构造 FetchModelsResponse（按平台提供默认模型清单）。
     *
     * @param platform 平台
     * @param baseUrl  base_url
     * @return 响应 Map
     */
    private Map<String, Object> buildFetchModels(String platform, String baseUrl) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> models = new ArrayList<>();
        for (String id : DEFAULT_PLATFORM_MODELS.getOrDefault(platform, List.of())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", id);
            m.put("tasks", List.of("chat"));
            m.put("traits", List.of("streaming"));
            models.add(m);
        }
        out.put("models", models);
        if (!models.isEmpty()) {
            out.put("fixed_base_url", baseUrl);
        }
        return out;
    }

    /**
     * 平台 → 默认模型列表。
     */
    private static final Map<String, List<String>> DEFAULT_PLATFORM_MODELS = Map.of(
            "openai", List.of("gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo"),
            "anthropic", List.of("claude-opus-4", "claude-sonnet-4", "claude-haiku-4"),
            "google", List.of("gemini-1.5-pro", "gemini-1.5-flash"),
            "deepseek", List.of("deepseek-chat", "deepseek-reasoner"),
            "qwen", List.of("qwen-max", "qwen-plus", "qwen-turbo"),
            "moonshot", List.of("moonshot-v1-8k", "moonshot-v1-32k"),
            "siliconflow", List.of("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct"),
            "ollama", List.of("llama3", "qwen2.5", "mistral")
    );

    // ==================== 工具方法 ====================

    /**
     * 获取 Provider 实体，不存在则抛 NOT_FOUND。
     *
     * @param providerId Provider 标识
     * @return Provider 实体
     */
    private ProviderEntity requireProvider(String providerId) {
        ProviderEntity e = providers.get(providerId);
        if (e == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider 不存在: " + providerId);
        }
        return e;
    }

    /**
     * 读取字符串字段（可为空）。
     *
     * @param map 源 Map
     * @param key 键
     * @return 字符串值（空串返回 null）
     */
    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    /**
     * 读取必填字符串字段。
     *
     * @param map 源 Map
     * @param key 键
     * @return 字符串值
     */
    private String requireStr(Map<String, Object> map, String key) {
        String s = str(map, key);
        if (s == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "字段 " + key + " 不能为空");
        }
        return s;
    }

    /**
     * 读取布尔字段（带默认值）。
     *
     * @param map   源 Map
     * @param key   键
     * @param def 默认值
     * @return 布尔值
     */
    private boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    /**
     * 读取整数字段（带默认值）。
     *
     * @param map 源 Map
     * @param key 键
     * @param def 默认值
     * @return 整数值
     */
    private int intv(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    /**
     * 读取字符串列表字段。
     *
     * @param map 源 Map
     * @param key 键
     * @return 字符串列表（不可为 null）
     */
    @SuppressWarnings("unchecked")
    private List<String> listOfStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    /**
     * 读取 Map 列表字段。
     *
     * @param map 源 Map
     * @param key 键
     * @return Map 列表（不可为 null）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    /**
     * 强制转型为 Map。
     *
     * @param obj 对象
     * @return Map（null 或类型不符时返回 null）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> uncheckedCast(Object obj) {
        if (obj instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }
}
