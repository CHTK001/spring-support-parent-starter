package com.chua.starter.ai.support.nomifun.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.ai.support.nomifun.common.ApiErrorCode;
import com.chua.starter.ai.support.nomifun.common.ApiException;
import com.chua.starter.ai.support.nomifun.common.ApiResult;
import com.chua.starter.ai.support.nomifun.mapper.NomifunProviderConnectionMapper;
import com.chua.starter.ai.support.nomifun.mapper.NomifunProviderMapper;
import com.chua.starter.ai.support.nomifun.mapper.NomifunProviderModelMapper;
import com.chua.starter.ai.support.nomifun.store.NomifunProvider;
import com.chua.starter.ai.support.nomifun.store.NomifunProviderConnection;
import com.chua.starter.ai.support.nomifun.store.NomifunProviderModel;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
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

import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 前端 Provider / 模型 / 协议管理控制器（对接 nomicore 契约）。
 *
 * <p>替代原 Rust 后端的厂商管理能力，提供 providers、provider-models、provider 连接、
 * 托管免费模型服务以及模型协议清单五类纯 Spring 接口。数据通过 MyBatis-Plus 持久化到
 * {@code sys_nomifun_provider} / {@code sys_nomifun_provider_model} /
 * {@code sys_nomifun_provider_connection} 三张表。</p>
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
     * 提供商 Mapper
     */
    private final NomifunProviderMapper providerMapper;

    /**
     * 提供商模型 Mapper
     */
    private final NomifunProviderModelMapper modelMapper;

    /**
     * 提供商连接 Mapper
     */
    private final NomifunProviderConnectionMapper connectionMapper;

    /**
     * 托管免费模型服务运行时状态
     */
    private final FreeModelService freeModelService = new FreeModelService();

    /**
     * 上游模型列表拉取 HTTP 客户端（跟随重定向，连接池复用）
     */
    private final HttpClient upstreamHttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 上游模型列表接口超时时间（秒）
     */
    private static final Duration UPSTREAM_TIMEOUT = Duration.ofSeconds(15);

    /**
     * UUIDv7 格式正则：xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx
     */
    private static final java.util.regex.Pattern UUID_V7_PATTERN = 
        java.util.regex.Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    // ==================== Providers ====================

    /**
     * 列举全部 Provider（按 sort_order 升序）。
     *
     * @return ProviderResponse 列表
     */
    @GetMapping("/providers")
    public ApiResult<List<Map<String, Object>>> listProviders() {
        List<NomifunProvider> providers = providerMapper.selectList(
                new LambdaQueryWrapper<NomifunProvider>()
                        .orderByAsc(NomifunProvider::getSortOrder)
                        .orderByAsc(NomifunProvider::getCreatedAt));

        List<Map<String, Object>> items = new ArrayList<>();
        for (NomifunProvider p : providers) {
            List<NomifunProviderModel> models = modelMapper.selectList(
                    new LambdaQueryWrapper<NomifunProviderModel>()
                            .eq(NomifunProviderModel::getProviderId, p.getProviderId()));
            List<NomifunProviderConnection> connections = connectionMapper.selectList(
                    new LambdaQueryWrapper<NomifunProviderConnection>()
                            .eq(NomifunProviderConnection::getProviderId, p.getProviderId()));
            items.add(toProviderResponse(p, models, connections));
        }
        return ApiResult.ok(items);
    }

    /**
     * 获取单个 Provider 详情。
     *
     * @param providerId Provider 标识
     * @return ProviderResponse
     */
    @GetMapping("/providers/{provider_id}")
    public ApiResult<Map<String, Object>> getProvider(@PathVariable("provider_id") String providerId) {
        NomifunProvider entity = requireProvider(providerId);
        List<NomifunProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderModel>()
                        .eq(NomifunProviderModel::getProviderId, providerId));
        List<NomifunProviderConnection> connections = connectionMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderConnection>()
                        .eq(NomifunProviderConnection::getProviderId, providerId));
        return ApiResult.ok(toProviderResponse(entity, models, connections));
    }

    /**
     * 创建 Provider（原子包含 initial_model 与可选 connections）。
     *
     * @param body 创建请求
     * @return 创建后的 ProviderResponse
     */
    @PostMapping("/providers")
    @Transactional
    public ApiResult<Map<String, Object>> createProvider(@RequestBody Map<String, Object> body) {
        String providerId = str(body, "provider_id");
        if (providerId == null || providerId.isBlank()) {
            providerId = IdUtils.uuidv7();
        }
        if (providerMapper.selectById(providerId) != null) {
            throw new ApiException(ApiErrorCode.CONFLICT, "provider_id 已存在: " + providerId);
        }
        long now = System.currentTimeMillis();

        // 创建 Provider 实体
        NomifunProvider entity = new NomifunProvider();
        entity.setProviderId(providerId);
        entity.setPlatform(requireStr(body, "platform"));
        entity.setName(requireStr(body, "name"));
        entity.setBaseUrl(requireStr(body, "base_url"));
        entity.setAuthScheme(requireStr(body, "auth_scheme"));
        entity.setCredentials(uncheckedCast(body.get("credentials")));
        entity.setEnabled(bool(body, "enabled", true));
        entity.setBedrockConfig(uncheckedCast(body.get("bedrock_config")));
        entity.setSortOrder(intv(body, "sort_order", providerMapper.selectCount(null).intValue()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        providerMapper.insert(entity);

        // 初始模型（原子创建必须包含至少一条可用模型）
        Map<String, Object> initialModel = uncheckedCast(body.get("initial_model"));
        if (initialModel == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "initial_model 不能为空");
        }
        NomifunProviderModel modelEntity = toModelEntity(providerId, initialModel, now);
        modelMapper.insert(modelEntity);

        // 初始连接（按 role 建表）
        List<Map<String, Object>> connections = listOfMap(body, "connections");
        for (Map<String, Object> conn : connections) {
            NomifunProviderConnection connEntity = toConnectionEntity(providerId, conn, now);
            connectionMapper.insert(connEntity);
        }

        // 查询完整数据返回
        List<NomifunProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderModel>()
                        .eq(NomifunProviderModel::getProviderId, providerId));
        List<NomifunProviderConnection> conns = connectionMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderConnection>()
                        .eq(NomifunProviderConnection::getProviderId, providerId));
        return ApiResult.ok(toProviderResponse(entity, models, conns));
    }

    /**
     * 更新 Provider（凭证缺省时保留既有凭证）。
     *
     * @param providerId Provider 标识
     * @param body       更新请求
     * @return 更新后的 ProviderResponse
     */
    @PutMapping("/providers/{provider_id}")
    @Transactional
    public ApiResult<Map<String, Object>> updateProvider(@PathVariable("provider_id") String providerId,
                                                         @RequestBody Map<String, Object> body) {
        NomifunProvider entity = requireProvider(providerId);
        if (body.containsKey("name")) {
            entity.setName(str(body, "name"));
        }
        if (body.containsKey("base_url")) {
            entity.setBaseUrl(str(body, "base_url"));
        }
        if (body.containsKey("auth_scheme")) {
            entity.setAuthScheme(str(body, "auth_scheme"));
        }
        if (body.containsKey("enabled")) {
            entity.setEnabled(bool(body, "enabled", entity.getEnabled()));
        }
        if (body.containsKey("sort_order")) {
            entity.setSortOrder(intv(body, "sort_order", entity.getSortOrder()));
        }
        if (body.containsKey("bedrock_config")) {
            entity.setBedrockConfig(uncheckedCast(body.get("bedrock_config")));
        }
        // credentials 缺省 → 保留已有凭证；显式传 null 视为清空
        if (body.containsKey("credentials")) {
            entity.setCredentials(uncheckedCast(body.get("credentials")));
        }
        entity.setUpdatedAt(System.currentTimeMillis());
        providerMapper.updateById(entity);

        List<NomifunProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderModel>()
                        .eq(NomifunProviderModel::getProviderId, providerId));
        List<NomifunProviderConnection> conns = connectionMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderConnection>()
                        .eq(NomifunProviderConnection::getProviderId, providerId));
        return ApiResult.ok(toProviderResponse(entity, models, conns));
    }

    /**
     * 删除 Provider（级联删除模型和连接）。
     *
     * @param providerId Provider 标识
     * @return 成功
     */
    @DeleteMapping("/providers/{provider_id}")
    @Transactional
    public ApiResult<Void> deleteProvider(@PathVariable("provider_id") String providerId) {
        requireProvider(providerId);
        providerMapper.deleteById(providerId);
        modelMapper.delete(new LambdaQueryWrapper<NomifunProviderModel>()
                .eq(NomifunProviderModel::getProviderId, providerId));
        connectionMapper.delete(new LambdaQueryWrapper<NomifunProviderConnection>()
                .eq(NomifunProviderConnection::getProviderId, providerId));
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
    @Transactional
    public ApiResult<Map<String, Object>> cloneProvider(@PathVariable("provider_id") String providerId,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        NomifunProvider source = requireProvider(providerId);
        long now = System.currentTimeMillis();
        String newId = IdUtils.uuidv7();

        // 克隆 Provider
        NomifunProvider clone = new NomifunProvider();
        clone.setProviderId(newId);
        clone.setPlatform(source.getPlatform());
        clone.setName(body != null && body.get("name") != null
                ? str(body, "name") : source.getName() + " 副本");
        clone.setBaseUrl(source.getBaseUrl());
        clone.setAuthScheme(source.getAuthScheme());
        clone.setCredentials(source.getCredentials() != null
                ? new LinkedHashMap<>(source.getCredentials()) : null);
        clone.setEnabled(source.getEnabled());
        clone.setBedrockConfig(source.getBedrockConfig() != null
                ? new LinkedHashMap<>(source.getBedrockConfig()) : null);
        clone.setSortOrder(providerMapper.selectCount(null).intValue());
        clone.setCreatedAt(now);
        clone.setUpdatedAt(now);
        providerMapper.insert(clone);

        // 深拷贝模型能力
        List<NomifunProviderModel> sourceModels = modelMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderModel>()
                        .eq(NomifunProviderModel::getProviderId, providerId));
        for (NomifunProviderModel srcModel : sourceModels) {
            NomifunProviderModel m = new NomifunProviderModel();
            m.setProviderId(newId);
            m.setModel(srcModel.getModel());
            m.setEnabled(srcModel.getEnabled());
            m.setSortOrder(srcModel.getSortOrder());
            m.setDescription(srcModel.getDescription());
            m.setCapabilities(srcModel.getCapabilities() != null
                    ? new ArrayList<>(srcModel.getCapabilities()) : null);
            m.setCreatedAt(now);
            m.setUpdatedAt(now);
            modelMapper.insert(m);
        }

        // 深拷贝连接
        List<NomifunProviderConnection> sourceConns = connectionMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderConnection>()
                        .eq(NomifunProviderConnection::getProviderId, providerId));
        for (NomifunProviderConnection srcConn : sourceConns) {
            NomifunProviderConnection c = new NomifunProviderConnection();
            c.setConnectionId(IdUtils.uuidv7());
            c.setProviderId(newId);
            c.setRole(srcConn.getRole());
            c.setLabel(srcConn.getLabel());
            c.setBaseUrl(srcConn.getBaseUrl());
            c.setAuthScheme(srcConn.getAuthScheme());
            c.setCredentials(srcConn.getCredentials() != null
                    ? new LinkedHashMap<>(srcConn.getCredentials()) : null);
            c.setExtra(srcConn.getExtra());
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            connectionMapper.insert(c);
        }

        List<NomifunProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderModel>()
                        .eq(NomifunProviderModel::getProviderId, newId));
        List<NomifunProviderConnection> conns = connectionMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderConnection>()
                        .eq(NomifunProviderConnection::getProviderId, newId));
        return ApiResult.ok(toProviderResponse(clone, models, conns));
    }

    /**
     * 拉取 Provider 远端模型清单（需 provider 已存在）。
     *
     * <p>使用 Provider 持久化的 base_url 与凭证真实调用上游 {@code GET {base_url}/models}
     * 接口，返回实时模型目录；不使用任何写死的兜底数据。</p>
     *
     * @param providerId Provider 标识
     * @param body       可选 { try_fix }
     * @return FetchModelsResponse
     */
    @PostMapping("/providers/{provider_id}/models")
    public ApiResult<Map<String, Object>> fetchProviderModels(@PathVariable("provider_id") String providerId,
                                                              @RequestBody(required = false) Map<String, Object> body) {
        NomifunProvider entity = requireProvider(providerId);
        boolean tryFix = body != null && Boolean.TRUE.equals(body.get("try_fix"));
        return ApiResult.ok(fetchModelsFromUpstream(entity.getPlatform(), entity.getBaseUrl(),
                entity.getCredentials(), entity.getAuthScheme(), tryFix));
    }

    /**
     * 匿名预拉取模型清单（创建前预览，不需要 provider 行）。
     *
     * <p>直接使用请求体中的 base_url 与凭证真实调用上游 {@code GET {base_url}/models}
     * 接口，用于「添加模型」弹窗在下拉框中实时预览供应商可用模型。</p>
     *
     * @param body FetchModelsAnonymousRequest
     * @return FetchModelsResponse
     */
    @PostMapping("/providers/fetch-models")
    public ApiResult<Map<String, Object>> fetchModelsAnonymous(@RequestBody Map<String, Object> body) {
        String platform = requireStr(body, "platform");
        String baseUrl = requireStr(body, "base_url");
        @SuppressWarnings("unchecked")
        Map<String, Object> credentials = body.get("credentials") instanceof Map
                ? (Map<String, Object>) body.get("credentials") : null;
        String authScheme = requireStr(body, "auth_scheme");
        return ApiResult.ok(fetchModelsFromUpstream(platform, baseUrl, credentials, authScheme, false));
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
        requireProvider(providerId);
        List<NomifunProviderConnection> conns = connectionMapper.selectList(
                new LambdaQueryWrapper<NomifunProviderConnection>()
                        .eq(NomifunProviderConnection::getProviderId, providerId)
                        .orderByAsc(NomifunProviderConnection::getCreatedAt));
        List<Map<String, Object>> items = conns.stream()
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
        requireProvider(providerId);
        NomifunProviderConnection conn = connectionMapper.selectOne(
                new LambdaQueryWrapper<NomifunProviderConnection>()
                        .eq(NomifunProviderConnection::getProviderId, providerId)
                        .eq(NomifunProviderConnection::getRole, role));
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
    @Transactional
    public ApiResult<Map<String, Object>> saveConnection(@PathVariable("provider_id") String providerId,
                                                         @RequestBody Map<String, Object> body) {
        NomifunProvider entity = requireProvider(providerId);
        String role = requireStr(body, "role");

        NomifunProviderConnection existing = connectionMapper.selectOne(
                new LambdaQueryWrapper<NomifunProviderConnection>()
                        .eq(NomifunProviderConnection::getProviderId, providerId)
                        .eq(NomifunProviderConnection::getRole, role));

        if (existing == null) {
            // 新建：凭证为必填
            if (body.get("credentials") == null) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "新建连接时 credentials 必填");
            }
            NomifunProviderConnection conn = toConnectionEntity(providerId, body, System.currentTimeMillis());
            connectionMapper.insert(conn);
            return ApiResult.ok(toConnectionResponse(conn));
        } else {
            // 更新：仅覆盖显式出现的字段，缺省 credentials 保留既有
            if (body.containsKey("label")) {
                existing.setLabel(str(body, "label"));
            }
            if (body.containsKey("base_url")) {
                existing.setBaseUrl(str(body, "base_url"));
            }
            if (body.containsKey("auth_scheme")) {
                existing.setAuthScheme(str(body, "auth_scheme"));
            }
            if (body.containsKey("extra")) {
                existing.setExtra(body.get("extra"));
            }
            if (body.containsKey("credentials")) {
                existing.setCredentials(uncheckedCast(body.get("credentials")));
            }
            existing.setUpdatedAt(System.currentTimeMillis());
            connectionMapper.updateById(existing);
            return ApiResult.ok(toConnectionResponse(existing));
        }
    }

    /**
     * 删除 Provider 连接。
     *
     * @param providerId Provider 标识
     * @param role       连接角色
     * @return 成功
     */
    @DeleteMapping("/providers/{provider_id}/connections/{role}")
    @Transactional
    public ApiResult<Void> deleteConnection(@PathVariable("provider_id") String providerId,
                                            @PathVariable("role") String role) {
        requireProvider(providerId);
        int deleted = connectionMapper.delete(
                new LambdaQueryWrapper<NomifunProviderConnection>()
                        .eq(NomifunProviderConnection::getProviderId, providerId)
                        .eq(NomifunProviderConnection::getRole, role));
        if (deleted == 0) {
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
        LambdaQueryWrapper<NomifunProviderModel> qw = new LambdaQueryWrapper<>();
        if (provider_id != null) {
            qw.eq(NomifunProviderModel::getProviderId, provider_id);
        }
        qw.orderByAsc(NomifunProviderModel::getCreatedAt);

        List<NomifunProviderModel> models = modelMapper.selectList(qw);
        List<Map<String, Object>> items = new ArrayList<>();
        for (NomifunProviderModel model : models) {
            NomifunProvider provider = providerMapper.selectById(model.getProviderId());
            if (provider != null) {
                items.add(toProviderModelResponse(provider, model));
            }
        }
        return ApiResult.ok(items);
    }

    /**
     * 全量保存（upsert）某 Provider 下某模型的完整能力集。
     *
     * @param body SaveProviderModelRequest
     * @return ProviderModelResponse
     */
    @PutMapping("/provider-models")
    @Transactional
    public ApiResult<Map<String, Object>> saveProviderModel(@RequestBody Map<String, Object> body) {
        String providerId = requireStr(body, "provider_id");
        NomifunProvider provider = requireProvider(providerId);
        Map<String, Object> modelInput = uncheckedCast(body.get("model"));
        if (modelInput == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "model 不能为空");
        }
        String modelName = requireStr(modelInput, "model");

        NomifunProviderModel existing = modelMapper.selectOne(
                new LambdaQueryWrapper<NomifunProviderModel>()
                        .eq(NomifunProviderModel::getProviderId, providerId)
                        .eq(NomifunProviderModel::getModel, modelName));

        if (existing == null) {
            NomifunProviderModel newModel = toModelEntity(providerId, modelInput, System.currentTimeMillis());
            modelMapper.insert(newModel);
            return ApiResult.ok(toProviderModelResponse(provider, newModel));
        } else {
            applyModelInput(existing, modelInput);
            existing.setUpdatedAt(System.currentTimeMillis());
            modelMapper.updateById(existing);
            return ApiResult.ok(toProviderModelResponse(provider, existing));
        }
    }

    /**
     * 删除 provider_models 行（按复合主键 provider_id + model）。
     *
     * @param providerId Provider 标识
     * @param model      模型名
     * @return 成功
     */
    @DeleteMapping("/provider-models")
    @Transactional
    public ApiResult<Void> deleteProviderModel(@RequestParam("provider_id") String providerId,
                                               @RequestParam("model") String model) {
        requireProvider(providerId);
        int deleted = modelMapper.delete(
                new LambdaQueryWrapper<NomifunProviderModel>()
                        .eq(NomifunProviderModel::getProviderId, providerId)
                        .eq(NomifunProviderModel::getModel, model));
        if (deleted == 0) {
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
     */
    private Map<String, Object> toProviderResponse(NomifunProvider provider,
                                                    List<NomifunProviderModel> models,
                                                    List<NomifunProviderConnection> connections) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("provider_id", provider.getProviderId());
        map.put("platform", provider.getPlatform());
        map.put("name", provider.getName());
        map.put("base_url", provider.getBaseUrl());
        map.put("auth_scheme", provider.getAuthScheme());
        map.put("has_credentials", provider.getCredentials() != null && !provider.getCredentials().isEmpty());
        List<Map<String, Object>> modelList = models.stream()
                .map(m -> toProviderModelResponse(provider, m))
                .collect(Collectors.toList());
        map.put("models", modelList);
        map.put("enabled", provider.getEnabled());
        if (provider.getBedrockConfig() != null) {
            map.put("bedrock_config", provider.getBedrockConfig());
        }
        map.put("sort_order", provider.getSortOrder());
        map.put("created_at", provider.getCreatedAt());
        map.put("updated_at", provider.getUpdatedAt());
        return map;
    }

    /**
     * 构造 ProviderModelResponse。
     */
    private Map<String, Object> toProviderModelResponse(NomifunProvider provider, NomifunProviderModel model) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("provider_id", provider.getProviderId());
        map.put("model", model.getModel());
        map.put("enabled", model.getEnabled());
        map.put("sort_order", model.getSortOrder());
        if (model.getDescription() != null) {
            map.put("description", model.getDescription());
        }
        List<Map<String, Object>> caps = model.getCapabilities() != null
                ? model.getCapabilities().stream().map(this::toCapabilityResponse).collect(Collectors.toList())
                : new ArrayList<>();
        map.put("capabilities", caps);
        map.put("created_at", model.getCreatedAt());
        map.put("updated_at", model.getUpdatedAt());
        return map;
    }

    /**
     * 构造单任务能力响应。
     */
    private Map<String, Object> toCapabilityResponse(Map<String, Object> cap) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("task", cap.get("task"));
        map.put("traits", cap.get("traits") != null ? cap.get("traits") : List.of());
        map.put("protocol", cap.get("protocol"));
        map.put("connection_role", cap.get("connection_role"));
        if (cap.get("base_url_override") != null) {
            map.put("base_url_override", cap.get("base_url_override"));
        }
        if (cap.get("endpoint") != null) {
            map.put("endpoint", cap.get("endpoint"));
        }
        if (cap.get("poll_endpoint") != null) {
            map.put("poll_endpoint", cap.get("poll_endpoint"));
        }
        if (cap.get("content_endpoint") != null) {
            map.put("content_endpoint", cap.get("content_endpoint"));
        }
        if (cap.get("realtime_endpoint") != null) {
            map.put("realtime_endpoint", cap.get("realtime_endpoint"));
        }
        map.put("allow_cross_origin_credentials", cap.get("allow_cross_origin_credentials") != null
                && (boolean) cap.get("allow_cross_origin_credentials"));
        map.put("provider_params", cap.get("provider_params"));
        if (cap.get("context_limit") != null) {
            map.put("context_limit", cap.get("context_limit"));
        }
        map.put("created_at", cap.get("created_at"));
        map.put("updated_at", cap.get("updated_at"));
        return map;
    }

    /**
     * 构造 ProviderConnectionResponse（不回显 credentials）。
     */
    private Map<String, Object> toConnectionResponse(NomifunProviderConnection conn) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("connection_id", conn.getConnectionId());
        map.put("provider_id", conn.getProviderId());
        map.put("role", conn.getRole());
        if (conn.getLabel() != null) {
            map.put("label", conn.getLabel());
        }
        map.put("base_url", conn.getBaseUrl());
        map.put("auth_scheme", conn.getAuthScheme());
        map.put("has_credentials", conn.getCredentials() != null && !conn.getCredentials().isEmpty());
        map.put("extra", conn.getExtra());
        map.put("created_at", conn.getCreatedAt());
        map.put("updated_at", conn.getUpdatedAt());
        return map;
    }

    // ==================== 实体构建 ====================

    /**
     * 由模型输入构造模型实体（新建）。
     */
    @SuppressWarnings("unchecked")
    private NomifunProviderModel toModelEntity(String providerId, Map<String, Object> input, long now) {
        NomifunProviderModel e = new NomifunProviderModel();
        e.setProviderId(providerId);
        e.setModel(requireStr(input, "model"));
        e.setEnabled(bool(input, "enabled", true));
        e.setSortOrder(intv(input, "sort_order", 0));
        e.setDescription(str(input, "description"));
        // capabilities → 直接存原始 List<Map>
        Object capsObj = input.get("capabilities");
        if (capsObj instanceof List<?> capsList) {
            List<Map<String, Object>> caps = new ArrayList<>();
            for (Object c : capsList) {
                if (c instanceof Map<?, ?> m) {
                    caps.add((Map<String, Object>) m);
                }
            }
            e.setCapabilities(caps);
        } else {
            e.setCapabilities(new ArrayList<>());
        }
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    /**
     * 把模型输入应用到已有实体（更新，替换完整能力集）。
     */
    @SuppressWarnings("unchecked")
    private void applyModelInput(NomifunProviderModel e, Map<String, Object> input) {
        if (input.containsKey("enabled")) {
            e.setEnabled(bool(input, "enabled", e.getEnabled()));
        }
        if (input.containsKey("sort_order")) {
            e.setSortOrder(intv(input, "sort_order", e.getSortOrder()));
        }
        if (input.containsKey("description")) {
            e.setDescription(str(input, "description"));
        }
        if (input.containsKey("capabilities")) {
            Object capsObj = input.get("capabilities");
            if (capsObj instanceof List<?> capsList) {
                List<Map<String, Object>> caps = new ArrayList<>();
                for (Object c : capsList) {
                    if (c instanceof Map<?, ?> m) {
                        caps.add((Map<String, Object>) m);
                    }
                }
                e.setCapabilities(caps);
            }
        }
    }

    /**
     * 由连接输入构造连接实体（新建）。
     */
    private NomifunProviderConnection toConnectionEntity(String providerId, Map<String, Object> input, long now) {
        NomifunProviderConnection e = new NomifunProviderConnection();
        e.setConnectionId(IdUtils.uuidv7());
        e.setProviderId(providerId);
        e.setRole(requireStr(input, "role"));
        e.setLabel(str(input, "label"));
        e.setBaseUrl(requireStr(input, "base_url"));
        e.setAuthScheme(requireStr(input, "auth_scheme"));
        e.setCredentials(uncheckedCast(input.get("credentials")));
        e.setExtra(input.get("extra"));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    // ==================== 模型协议清单 ====================

    private Map<String, Object> buildManifest(String preset, String task, String baseUrl) {
        // 前端 preset 可能传大写形式（如 Zhipu），统一转小写后匹配
        String presetKey = preset == null ? null : preset.toLowerCase();
        ProtocolPreset pp = PROTOCOL_PRESETS.getOrDefault(presetKey,
                new ProtocolPreset(presetKey != null ? presetKey : "custom", "https://api.openai.com/v1"));
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

    private List<Map<String, Object>> authSchemes() {
        return List.of(
                authScheme("api_key", true),
                authScheme("bearer", true),
                authScheme("oauth", true),
                authScheme("none", false)
        );
    }

    private Map<String, Object> authScheme(String scheme, boolean parameterized) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scheme", scheme);
        m.put("parameterized", parameterized);
        return m;
    }

    private record ProtocolPreset(String platform, String defaultBaseUrl) {
    }

    private static final List<String> ALL_TASKS = List.of(
            "chat", "realtime_conversation", "image_generation", "image_edit",
            "video_generation", "speech_synthesis", "speech_recognition",
            "embedding", "rerank"
    );

    private static final Map<String, ProtocolPreset> PROTOCOL_PRESETS;

    static {
        Map<String, ProtocolPreset> m = new LinkedHashMap<>();
        m.put("openai", new ProtocolPreset("openai", "https://api.openai.com/v1"));
        m.put("anthropic", new ProtocolPreset("anthropic", "https://api.anthropic.com/v1"));
        m.put("google", new ProtocolPreset("google", "https://generativelanguage.googleapis.com/v1beta"));
        m.put("gemini", new ProtocolPreset("gemini", "https://generativelanguage.googleapis.com/v1beta"));
        m.put("gemini-vertex-ai", new ProtocolPreset("gemini-vertex-ai", "https://aiplatform.googleapis.com/v1"));
        m.put("azure", new ProtocolPreset("azure", "https://api.openai.azure.com"));
        m.put("bedrock", new ProtocolPreset("bedrock", "https://bedrock-runtime.us-east-1.amazonaws.com"));
        m.put("siliconflow", new ProtocolPreset("siliconflow", "https://api.siliconflow.cn/v1"));
        m.put("deepseek", new ProtocolPreset("deepseek", "https://api.deepseek.com/v1"));
        m.put("qwen", new ProtocolPreset("qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        m.put("dashscope", new ProtocolPreset("dashscope", "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        m.put("dashscope-coding", new ProtocolPreset("dashscope-coding", "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        m.put("moonshot", new ProtocolPreset("moonshot", "https://api.moonshot.cn/v1"));
        m.put("moonshot-cn", new ProtocolPreset("moonshot-cn", "https://api.moonshot.cn/v1"));
        m.put("moonshot-global", new ProtocolPreset("moonshot-global", "https://api.moonshot.ai/v1"));
        m.put("grok", new ProtocolPreset("grok", "https://api.x.ai/v1"));
        m.put("xai", new ProtocolPreset("xai", "https://api.x.ai/v1"));
        m.put("ollama", new ProtocolPreset("ollama", "http://localhost:11434/v1"));
        m.put("nomifun-free-model", new ProtocolPreset("nomifun-free-model", "https://free.nomifun.com/v1"));
        // 中国平台官方 OpenAI 兼容地址
        m.put("zhipu", new ProtocolPreset("zhipu", "https://open.bigmodel.cn/api/paas/v4"));
        m.put("glm-coding-plan", new ProtocolPreset("glm-coding-plan", "https://open.bigmodel.cn/api/coding/paas/v4"));
        m.put("ark", new ProtocolPreset("ark", "https://ark.cn-beijing.volces.com/api/v3"));
        m.put("ark-coding-plan", new ProtocolPreset("ark-coding-plan", "https://ark.cn-beijing.volces.com/api/v3"));
        m.put("ark-agent-plan", new ProtocolPreset("ark-agent-plan", "https://ark.cn-beijing.volces.com/api/v3"));
        m.put("qianfan", new ProtocolPreset("qianfan", "https://qianfan.baidubce.com/v2"));
        m.put("qianfan-coding-plan", new ProtocolPreset("qianfan-coding-plan", "https://qianfan.baidubce.com/v2"));
        m.put("hunyuan", new ProtocolPreset("hunyuan", "https://api.hunyuan.cloud.tencent.com/v1"));
        m.put("hunyuan-global", new ProtocolPreset("hunyuan-global", "https://api.hunyuan.cloud.tencent.com/v1"));
        m.put("lingyi", new ProtocolPreset("lingyi", "https://api.lingyiwanwu.com/v1"));
        m.put("stepfun", new ProtocolPreset("stepfun", "https://api.stepfun.com/v1"));
        m.put("stepfun-plan", new ProtocolPreset("stepfun-plan", "https://api.stepfun.com/v1"));
        m.put("minimax", new ProtocolPreset("minimax", "https://api.minimaxi.com/v1"));
        m.put("minimax-code", new ProtocolPreset("minimax-code", "https://api.minimaxi.com/v1"));
        m.put("minimax-coding-plan", new ProtocolPreset("minimax-coding-plan", "https://api.minimaxi.com/v1"));
        m.put("mimo", new ProtocolPreset("mimo", "https://api.mimo.xiaomi.com/v1"));
        m.put("mimo-token-plan-cn", new ProtocolPreset("mimo-token-plan-cn", "https://api.mimo.xiaomi.com/v1"));
        m.put("mimo-token-plan-sgp", new ProtocolPreset("mimo-token-plan-sgp", "https://api.mimo.xiaomi.com/v1"));
        m.put("mimo-token-plan-ams", new ProtocolPreset("mimo-token-plan-ams", "https://api.mimo.xiaomi.com/v1"));
        m.put("deepgram", new ProtocolPreset("deepgram", "https://api.deepgram.com/v1"));
        m.put("novita", new ProtocolPreset("novita", "https://api.novita.ai/openai/v1"));
        m.put("openrouter", new ProtocolPreset("openrouter", "https://openrouter.ai/api/v1"));
        m.put("poe", new ProtocolPreset("poe", "https://api.poe.com/v1"));
        m.put("ppio", new ProtocolPreset("ppio", "https://api.ppinfra.com/v3/openai"));
        m.put("modelscope", new ProtocolPreset("modelscope", "https://api-inference.modelscope.cn/v1"));
        m.put("infiniai", new ProtocolPreset("infiniai", "https://cloud.infini-ai.com/maas/v1"));
        m.put("ctyun", new ProtocolPreset("ctyun", "https://modelverse.cn/v1"));
        m.put("new-api", new ProtocolPreset("new-api", "http://localhost:3000/v1"));
        m.put("custom", new ProtocolPreset("custom", "https://api.openai.com/v1"));
        PROTOCOL_PRESETS = Map.copyOf(m);
    }

    // ==================== 模型拉取 ====================

    /**
     * 真实调用上游 {@code GET {base_url}/models} 拉取模型目录。
     *
     * <p>OpenAI 兼容协议：{@code Authorization: Bearer <key>}，响应 {@code data[].id}；
     * Google Gemini 协议：{@code ?key=<key>} 查询参数，响应 {@code models[].name}（形如 models/gemini-xx）。
     * 拉取失败时抛出与前端契约对应的错误码（UNAUTHORIZED / FORBIDDEN / RATE_LIMITED / TIMEOUT / BAD_GATEWAY）。</p>
     *
     * @param platform    平台标识（zhipu/openai/google/...）
     * @param baseUrl     供应商 API 基础地址
     * @param credentials 凭证（兼容 api_key 字符串与 api_keys 数组两种形态）
     * @param authScheme  鉴权方式（api_key 等）
     * @param tryFix      是否在 404 时尝试补全 /v1 后重试
     * @return FetchModelsResponse（models + 可选 fixed_base_url）
     */
    private Map<String, Object> fetchModelsFromUpstream(String platform, String baseUrl,
                                                        Map<String, Object> credentials,
                                                        String authScheme, boolean tryFix) {
        // 基础地址缺失直接判参数错误
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "base_url 未配置，无法拉取模型列表");
        }
        String apiKey = extractApiKey(credentials);
        // 平台标识统一小写后再判断协议族（前端可能传大写形式）
        String platformLower = platform == null ? "" : platform.toLowerCase();
        boolean googleProtocol = platformLower.contains("gemini") || platformLower.contains("google");

        // 首次按原始地址拉取
        List<Map<String, Object>> models = requestUpstreamModels(baseUrl, apiKey, googleProtocol);
        String fixedBaseUrl = null;
        // 404 且允许修正时，尝试补全 /v1 再拉一次（用户常见漏填 /v1 的场景）
        if (models == null && tryFix && !googleProtocol) {
            String fixed = baseUrl.endsWith("/") ? baseUrl + "v1" : baseUrl + "/v1";
            models = requestUpstreamModels(fixed, apiKey, false);
            if (models != null) {
                fixedBaseUrl = fixed;
            }
        }
        // 仍然失败则按无法连接处理
        if (models == null) {
            throw new ApiException(ApiErrorCode.BAD_GATEWAY, "无法从供应商拉取模型列表，请检查 API 地址和 Key");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("models", models);
        if (fixedBaseUrl != null) {
            out.put("fixed_base_url", fixedBaseUrl);
        }
        return out;
    }

    /**
     * 请求上游模型列表接口。
     *
     * @param baseUrl        基础地址
     * @param apiKey         API Key（可能为 null，如 Ollama 本地实例）
     * @param googleProtocol 是否使用 Gemini 协议
     * @return 模型条目列表；请求失败返回 null（由调用方决定是否重试/报错）
     */
    private List<Map<String, Object>> requestUpstreamModels(String baseUrl, String apiKey, boolean googleProtocol) {
        // 拼接模型列表 URL：OpenAI 兼容为 {base}/models，Gemini 为 {base}/models?key=xxx
        StringBuilder url = new StringBuilder(baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models");
        if (googleProtocol && apiKey != null && !apiKey.isBlank()) {
            url.append(url.indexOf("?") >= 0 ? "&" : "?").append("key=").append(apiKey);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(UPSTREAM_TIMEOUT)
                .GET();
        // OpenAI 兼容协议使用 Bearer 头鉴权
        if (!googleProtocol && apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<String> response = upstreamHttpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            // 按前端契约映射错误码
            if (status == 401) {
                throw new ApiException(ApiErrorCode.UNAUTHORIZED, "API Key 无效或已过期");
            }
            if (status == 403) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "该 API Key 没有读取模型列表的权限");
            }
            if (status == 429) {
                throw new ApiException(ApiErrorCode.RATE_LIMITED, "供应商请求过于频繁，请稍后重试");
            }
            if (status == 404) {
                // 返回 null 交给调用方决定是否 try_fix 重试
                return null;
            }
            if (status < 200 || status >= 300) {
                throw new ApiException(ApiErrorCode.BAD_GATEWAY, "供应商模型接口返回 " + status);
            }
            return googleProtocol ? parseGoogleModels(response.body()) : parseOpenAiModels(response.body());
        } catch (ApiException e) {
            // 业务异常直接向上抛出
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ApiException(ApiErrorCode.TIMEOUT, "供应商模型接口请求超时");
        } catch (Exception e) {
            // 网络层异常统一按网关错误处理
            throw new ApiException(ApiErrorCode.BAD_GATEWAY, "无法连接供应商模型接口: " + e.getMessage());
        }
    }

    /**
     * 解析 OpenAI 兼容的模型列表响应（data[].id）。
     *
     * @param body 响应体 JSON
     * @return 模型条目列表
     */
    private List<Map<String, Object>> parseOpenAiModels(String body) {
        List<Map<String, Object>> models = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode root = readJson(body);
        // 响应格式：{"data": [{"id": "glm-4-flash", ...}, ...]}
        com.fasterxml.jackson.databind.JsonNode data = root == null ? null : root.get("data");
        if (data != null && data.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : data) {
                String id = item.path("id").asText(null);
                // 跳过无 id 的条目
                if (id == null || id.isBlank()) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", id);
                m.put("name", item.path("name").asText(id));
                m.put("tasks", inferTasks(id));
                m.put("traits", List.of("streaming"));
                models.add(m);
            }
        }
        return models;
    }

    /**
     * 解析 Google Gemini 的模型列表响应（models[].name，形如 models/gemini-xx）。
     *
     * @param body 响应体 JSON
     * @return 模型条目列表
     */
    private List<Map<String, Object>> parseGoogleModels(String body) {
        List<Map<String, Object>> models = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode root = readJson(body);
        com.fasterxml.jackson.databind.JsonNode data = root == null ? null : root.get("models");
        if (data != null && data.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : data) {
                String name = item.path("name").asText(null);
                // 去掉 "models/" 前缀作为模型 ID
                String id = name != null && name.startsWith("models/") ? name.substring("models/".length()) : name;
                if (id == null || id.isBlank()) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", id);
                m.put("name", item.path("displayName").asText(id));
                m.put("tasks", inferTasks(id));
                m.put("traits", List.of("streaming"));
                models.add(m);
            }
        }
        return models;
    }

    /**
     * 解析 JSON 字符串，失败返回 null。
     *
     * @param body JSON 字符串
     * @return JsonNode 或 null
     */
    private com.fasterxml.jackson.databind.JsonNode readJson(String body) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 依据模型 ID 关键字推断任务类型。
     *
     * @param modelId 模型 ID
     * @return 任务类型列表（默认 chat）
     */
    private List<String> inferTasks(String modelId) {
        String lower = modelId.toLowerCase();
        // 按关键字匹配任务类型
        if (lower.contains("embedding") || lower.contains("embed") || lower.contains("-bge-")) {
            return List.of("embedding");
        }
        if (lower.contains("rerank")) {
            return List.of("rerank");
        }
        if (lower.contains("tts") || lower.contains("speech-synthesis") || lower.contains("speech_synthesis")) {
            return List.of("speech_synthesis");
        }
        if (lower.contains("whisper") || lower.contains("asr") || lower.contains("speech-recognition")) {
            return List.of("speech_recognition");
        }
        if (lower.contains("dall-e") || lower.contains("image-gen") || lower.contains("cogview") || lower.contains("flux")) {
            return List.of("image_generation");
        }
        if (lower.contains("cogvideo") || lower.contains("video-gen")) {
            return List.of("video_generation");
        }
        if (lower.contains("realtime")) {
            return List.of("realtime_conversation");
        }
        return List.of("chat");
    }

    /**
     * 从凭证 Map 中提取 API Key，兼容 api_key（字符串）与 api_keys（数组）两种形态。
     *
     * @param credentials 凭证 Map（可能为 null）
     * @return API Key 或 null
     */
    private String extractApiKey(Map<String, Object> credentials) {
        // 无凭证直接返回 null
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        Object single = credentials.get("api_key");
        if (single instanceof String && !((String) single).isBlank()) {
            return (String) single;
        }
        Object multi = credentials.get("api_keys");
        // 多 Key 数组取第一个作为拉取凭证
        if (multi instanceof List && !((List<?>) multi).isEmpty()) {
            Object first = ((List<?>) multi).get(0);
            if (first instanceof String && !((String) first).isBlank()) {
                return (String) first;
            }
        }
        return null;
    }

    // ==================== 工具方法 ====================

    /**
     * 获取 Provider 实体，不存在则抛 NOT_FOUND。
     */
    private NomifunProvider requireProvider(String providerId) {
        NomifunProvider e = providerMapper.selectById(providerId);
        if (e == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider 不存在: " + providerId);
        }
        return e;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private String requireStr(Map<String, Object> map, String key) {
        String s = str(map, key);
        if (s == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "字段 " + key + " 不能为空");
        }
        return s;
    }

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

    @SuppressWarnings("unchecked")
    private <T> T uncheckedCast(Object obj) {
        return (T) obj;
    }
}
