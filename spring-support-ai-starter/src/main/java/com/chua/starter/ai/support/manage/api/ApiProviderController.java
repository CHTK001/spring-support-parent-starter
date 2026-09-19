package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.UUID;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.service.ModelMetricsSnapshotService;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 前端 提供者 / 模型 / 协议管理控制器（对接 nomicore 契约）。
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
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiProviderController {

    /**
     * 提供商 映射器
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 提供商模型 映射器
     */
    private final SysAiProviderModelMapper modelMapper;

    /**
     * 提供商连接 映射器
     */
    private final SysAiProviderConnectionMapper connectionMapper;

    /**
     * 模型价格/指标快照服务（读取 Redis 缓存，含价格/延迟/速度/智能等指标）
     */
    private final ModelMetricsSnapshotService metricsSnapshotService;

    /**
     * Agent 模型注册中心（动态注入 Agent 虚拟 提供者）
     */
    private final com.chua.starter.ai.support.registry.ChatClientRegistry chatClientRegistry;

    /**
     * 拉取模型列表使用的 HTTP 客户端（连接池复用）
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
     * uuidv7 格式正则：xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx
     */
    private static final java.util.regex.Pattern UUID_V7_PATTERN = 
        java.util.regex.Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    /**
     * 指标索引构建时间戳（毫秒）
     */
    private volatile long metricsIndexLoadedAt = 0;

    /**
     * 指标索引（提供者标识::模型 → 快照行），供查询接口从 Redis 缓存组装指标
     */
    private volatile Map<String, Map<String, Object>> metricsIndex = Map.of();

    /**
     * 指标索引 TTL（毫秒）：30 秒
     */
    private static final long METRICS_INDEX_TTL_MILLIS = 30_000;

    /**
     * 需要合并进模型行 指标 的快照指标键。
     */
    private static final String[] METRIC_KEYS = {
            "price_input_per_musd", "price_output_per_musd",
            "price_cache_hit_per_musd", "price_cache_write_per_musd",
            "intelligence_index", "output_speed_tps", "latency_first_token_seconds",
            "probe_latency_ms", "icon_url", "reasoning_model", "web_search",
            "image_input", "function_calling", "context_window_tokens"
    };

 // ==================== 提供者 ====================

    /**
     * 获取 提供者 凭证明文（仅限本地可信客户端调用）。
     *
     * @param providerId 提供者 标识
     * @return 凭证 映射（含 api_Key 明文）
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     * @param @PathVariable("provider_id" @PathVariable("provider_id"
     */
    @GetMapping("/providers/{provider_id}/credentials")
    public ApiResult<Map<String, Object>> getProviderCredentials(@PathVariable("provider_id") String providerId) {
        SysAiProvider entity = requireProvider(providerId);
        Map<String, Object> credentials = entity.getCredentials();
        if (credentials == null) {
            credentials = new java.util.LinkedHashMap<>();
        }
        return ApiResult.ok(credentials);
    }

    /**
     * 列举全部 提供者（按 排序_订单 升序）。
     *
     * @return ProviderResponse 列表
     */
    @GetMapping("/providers")
    public ApiResult<List<Map<String, Object>>> listProviders() {
        List<SysAiProvider> providers = providerMapper.selectList(
                new LambdaQueryWrapper<SysAiProvider>()
                        .orderByAsc(SysAiProvider::getSortOrder)
                        .orderByAsc(SysAiProvider::getCreatedAt));

        List<Map<String, Object>> items = new ArrayList<>(8); // 提供者数量不可预估
        for (SysAiProvider p : providers) {
            List<SysAiProviderModel> models = modelMapper.selectList(
                    new LambdaQueryWrapper<SysAiProviderModel>()
                            .eq(SysAiProviderModel::getProviderId, p.getProviderId()));
            List<SysAiProviderConnection> connections = connectionMapper.selectList(
                    new LambdaQueryWrapper<SysAiProviderConnection>()
                            .eq(SysAiProviderConnection::getProviderId, p.getProviderId()));
            items.add(toProviderResponse(p, models, connections));
        }

 // 注入已注册的 Agent 模型作为虚拟 提供者
        List<String> registeredAgentModels = new ArrayList<>(chatClientRegistry.getRegisteredAgentModels());
        if (!registeredAgentModels.isEmpty()) {
            Map<String, Object> agentProvider = buildAgentProvider(registeredAgentModels);
            items.add(0, agentProvider);
        }

        return ApiResult.ok(items);
    }

    /**
     * 获取单个 提供者 详情。
     *
     * @param providerId 提供者 标识
     * @return ProviderResponse
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     * @param @PathVariable("provider_id" @PathVariable("provider_id"
     */
    @GetMapping("/providers/{provider_id}")
    public ApiResult<Map<String, Object>> getProvider(@PathVariable("provider_id") String providerId) {
        SysAiProvider entity = requireProvider(providerId);
        List<SysAiProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId));
        List<SysAiProviderConnection> connections = connectionMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId));
        return ApiResult.ok(toProviderResponse(entity, models, connections));
    }

    /**
     * 创建 提供者（原子包含 initial_模型 与可选 connections）。
     *
     * @param body 创建请求
     * @return 创建后的 提供者响应
     */
    @PostMapping("/providers")
    @Transactional
    public ApiResult<Map<String, Object>> createProvider(@RequestBody Map<String, Object> body) {
        String providerId = str(body, "provider_id");
        if (providerId == null || providerId.isBlank()) {
            providerId = String.valueOf(System.currentTimeMillis() + (int)(Math.random() * 1000));
        }
        if (providerMapper.selectById(providerId) != null) {
            throw new ApiException(ApiErrorCode.CONFLICT, "provider_id 已存在: " + providerId);
        }
        long now = System.currentTimeMillis();

 // 创建 提供者 实体
        SysAiProvider entity = new SysAiProvider();
        entity.setProviderId(providerId);
        entity.setPlatform(requireStr(body, "platform"));
        entity.setName(requireStr(body, "name"));
        entity.setBaseUrl(requireStr(body, "base_url"));
        entity.setAuthScheme(requireStr(body, "auth_scheme"));
        entity.setCredentials(uncheckedCast(body.get("credentials")));
        entity.setEnabled(bool(body, "enabled", true));
        entity.setBedrockConfig(uncheckedCast(body.get("bedrock_config")));
        entity.setProxy(str(body, "proxy"));
        entity.setExtra(uncheckedCast(body.get("extra")));
        entity.setSortOrder(intv(body, "sort_order", providerMapper.selectCount(null).intValue()));
        entity.setCreatedAt(null);
        entity.setUpdatedAt(null);
        providerMapper.insert(entity);

        // 初始模型（可选，用户可以后续手动添加）
        Map<String, Object> initialModel = uncheckedCast(body.get("initial_model"));
        if (initialModel != null) {
            SysAiProviderModel modelEntity = toModelEntity(providerId, initialModel, now);
            modelMapper.insert(modelEntity);
        }

 // 初始连接（按 角色 建表）
        List<Map<String, Object>> connections = listOfMap(body, "connections");
        for (Map<String, Object> conn : connections) {
            SysAiProviderConnection connEntity = toConnectionEntity(providerId, conn, now);
            connectionMapper.insert(connEntity);
        }

        // 查询完整数据返回
        List<SysAiProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId));
        List<SysAiProviderConnection> conns = connectionMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId));
        return ApiResult.ok(toProviderResponse(entity, models, conns));
    }

    /**
     * 更新 提供者（凭证缺省时保留既有凭证）。
     *
     * @param providerId 提供者 标识
     * @param body       更新请求
     * @return 更新后的 提供者响应
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     */
    @PutMapping("/providers/{provider_id}")
    @Transactional
    public ApiResult<Map<String, Object>> updateProvider(@PathVariable("provider_id") String providerId,
                                                         @RequestBody Map<String, Object> body) {
        SysAiProvider entity = requireProvider(providerId);
        if (body.containsKey("name")) {
            entity.setName(str(body, "name"));
        }
        if (body.containsKey("base_url")) {
            entity.setBaseUrl(str(body, "base_url"));
        }
        if (body.containsKey("auth_scheme")) {
            entity.setAuthScheme(str(body, "auth_scheme"));
        }
        if (body.containsKey("credentials")) {
            entity.setCredentials(uncheckedCast(body.get("credentials")));
        }
        if (body.containsKey("enabled")) {
            entity.setEnabled(bool(body, "enabled", true));
        }
        if (body.containsKey("bedrock_config")) {
            entity.setBedrockConfig(uncheckedCast(body.get("bedrock_config")));
        }
        if (body.containsKey("proxy")) {
            entity.setProxy(str(body, "proxy"));
        }
        if (body.containsKey("extra")) {
            entity.setExtra(uncheckedCast(body.get("extra")));
        }
        if (body.containsKey("platform")) {
            entity.setPlatform(str(body, "platform"));
        }
        if (body.containsKey("sort_order")) {
            entity.setSortOrder(intv(body, "sort_order", entity.getSortOrder()));
        }
        entity.setUpdatedAt(System.currentTimeMillis());
        providerMapper.updateById(entity);

        List<SysAiProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId));
        List<SysAiProviderConnection> conns = connectionMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId));
        return ApiResult.ok(toProviderResponse(entity, models, conns));
    }

    /**
     * 删除 提供者（级联删除模型和连接）。
     *
     * @param providerId 提供者 标识
     * @return 成功
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     * @param @PathVariable("provider_id" @PathVariable("provider_id"
     */
    @DeleteMapping("/providers/{provider_id}")
    @Transactional
    public ApiResult<Void> deleteProvider(@PathVariable("provider_id") String providerId) {
        requireProvider(providerId);
        providerMapper.deleteById(providerId);
        modelMapper.delete(new LambdaQueryWrapper<SysAiProviderModel>()
                .eq(SysAiProviderModel::getProviderId, providerId));
        connectionMapper.delete(new LambdaQueryWrapper<SysAiProviderConnection>()
                .eq(SysAiProviderConnection::getProviderId, providerId));
        return ApiResult.ok();
    }

    /**
     * 克隆 提供者（连同其模型能力与连接一并复制）。
     *
     * @param providerId 提供者 标识
     * @param body       可选 { 名称 }
     * @return 克隆后的 提供者响应
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     */
    @PostMapping("/providers/{provider_id}/clone")
    @Transactional
    public ApiResult<Map<String, Object>> cloneProvider(@PathVariable("provider_id") String providerId,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        SysAiProvider source = requireProvider(providerId);
        long now = System.currentTimeMillis();
        String newId = String.valueOf(System.currentTimeMillis() + (int)(Math.random() * 1000));

 // 克隆 提供者
        SysAiProvider clone = new SysAiProvider();
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
        clone.setProxy(source.getProxy());
        clone.setSortOrder(providerMapper.selectCount(null).intValue());
        clone.setCreatedAt(now);
        clone.setUpdatedAt(now);
        providerMapper.insert(clone);

        // 深拷贝模型能力
        List<SysAiProviderModel> sourceModels = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId));
        for (SysAiProviderModel srcModel : sourceModels) {
            SysAiProviderModel m = new SysAiProviderModel();
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
        List<SysAiProviderConnection> sourceConns = connectionMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId));
        for (SysAiProviderConnection srcConn : sourceConns) {
            SysAiProviderConnection c = new SysAiProviderConnection();
            c.setConnectionId(String.valueOf(System.currentTimeMillis() + (int)(Math.random() * 1000)));
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

        List<SysAiProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, newId));
        List<SysAiProviderConnection> conns = connectionMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, newId));
        return ApiResult.ok(toProviderResponse(clone, models, conns));
    }

    /**
     * 拉取 提供者 远端模型清单（需 提供者 已存在）。
     *
     * <p>使用 Provider 持久化的 base_url 与凭证真实调用上游 {@code GET {base_url}/models}
     * 接口，返回实时模型目录；不使用任何写死的兜底数据。</p>
     *
     * @param providerId 提供者 标识
     * @param body       可选 { 尝试_fix }
     * @return FetchModelsResponse
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     */
    @PostMapping("/providers/{provider_id}/models")
    public ApiResult<Map<String, Object>> fetchProviderModels(@PathVariable("provider_id") String providerId,
                                                              @RequestBody(required = false) Map<String, Object> body) {
        SysAiProvider entity = requireProvider(providerId);
        boolean tryFix = body != null && Boolean.TRUE.equals(body.get("try_fix"));
        return ApiResult.ok(fetchModelsFromUpstream(entity.getPlatform(), entity.getBaseUrl(),
                entity.getCredentials(), entity.getAuthScheme(), entity.getProxy(), tryFix));
    }

    /**
     * 从提供商远程获取模型列表（GET接口，兼容前端调用）。
     *
     * <p>前端guada版本调用的是 GET /providers/{id}/remote_models，
     * 这里提供兼容的GET接口，内部调用同样的逻辑，
     * 并将返回格式转换成前端期望的 items[].modelName 格式。</p>
     *
     * @param providerId 提供者 标识
     * @return FetchModelsResponse
     */
    @GetMapping("/providers/{provider_id}/remote_models")
    public ApiResult<Map<String, Object>> fetchRemoteModels(@PathVariable("provider_id") String providerId) {
        SysAiProvider entity = requireProvider(providerId);
        Map<String, Object> upstreamResult = fetchModelsFromUpstream(entity.getPlatform(), entity.getBaseUrl(),
                entity.getCredentials(), entity.getAuthScheme(), entity.getProxy(), false);

        // 转换格式：data.models[].name → data.items[].modelName
        List<Map<String, Object>> models = (List<Map<String, Object>>) upstreamResult.get("models");
        List<Map<String, Object>> items = new ArrayList<>();
        if (models != null) {
            for (Map<String, Object> m : models) {
                Map<String, Object> item = new LinkedHashMap<>(m);
                item.put("modelName", m.get("name") != null ? m.get("name") : m.get("id"));
                items.add(item);
            }
        }
        upstreamResult.put("items", items);
        return ApiResult.ok(upstreamResult);
    }

    /**
     * 匿名预拉取模型清单（创建前预览，不需要 提供者 行）。
     *
     * <p>直接使用请求体中的 base_url 与凭证真实调用上游 {@code GET {base_url}/models}
     * 接口，用于「添加模型」弹窗在下拉框中实时预览供应商可用模型。</p>
     *
     * @param body 获取模型anonymous请求
     * @return FetchModelsResponse
     */
    @PostMapping("/providers/fetch-models")
    public ApiResult<Map<String, Object>> fetchModelsAnonymous(@RequestBody Map<String, Object> body) {
        String platform = requireStr(body, "platform");
 // ONNX 本地离线模型的 基础_url 存的是模型文件路径，创建前预览阶段允许为空
        String baseUrl = str(body, "base_url");
        if ((baseUrl == null || baseUrl.isBlank()) && !(platform != null && platform.toLowerCase().contains("onnx"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "base_url 不能为空");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> credentials = body.get("credentials") instanceof Map
                ? (Map<String, Object>) body.get("credentials") : null;
        String authScheme = str(body, "auth_scheme");
        String proxy = str(body, "proxy");
        return ApiResult.ok(fetchModelsFromUpstream(platform, baseUrl, credentials, authScheme, proxy, false));
    }

    /**
     * 逐个测试 提供者 凭证中的每个 API Key（多 Key 有效标记）。
     *
     * <p>对 {@code credentials.api_keys} 中的每个 key 单独调用上游
     * {@code GET {base_url}/models}，返回每个 key 的连通性结果
     * （{@code ok} / {@code error}），供前端标记「哪个 Key 有效、哪个无效」。
     * 只读，不修改数据库。</p>
     *
     * @param providerId 提供者 标识
     * @return 每个 Key 的测试结果列表 + 汇总
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     * @param @PathVariable("provider_id" @PathVariable("provider_id"
     */
    @PostMapping("/providers/{provider_id}/test-keys")
    public ApiResult<Map<String, Object>> testProviderKeys(@PathVariable("provider_id") String providerId) {
        SysAiProvider entity = requireProvider(providerId);
        Map<String, Object> credentials = entity.getCredentials();
        List<String> keys = extractAllKeys(credentials);
        List<Map<String, Object>> results = new ArrayList<>(8); // 密钥数量不可预估
        int okCount = 0;
        for (int i = 0; i < keys.size(); i++) {
            Map<String, Object> r = testSingleKey(entity.getPlatform(), entity.getBaseUrl(),
                    keys.get(i), entity.getAuthScheme(), entity.getProxy(), i);
            if (Boolean.TRUE.equals(r.get("ok"))) {
                okCount++;
            }
            results.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("provider_id", providerId);
        out.put("total", keys.size());
        out.put("ok_count", okCount);
        out.put("results", results);
        return ApiResult.ok(out);
    }

    /**
     * 用指定 Key 调上游 /模型，判定该 Key 是否有效（只读、不改库）。
     * @param body 请求体，不允许为 null
     * @return Api结果 对象
     */
    @PostMapping("/providers/test-connection")
    public ApiResult<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        String apiUrl = (String) body.get("apiUrl");
        String apiKey = (String) body.get("apiKey");
        String protocol = (String) body.getOrDefault("protocol", "openai");
        String proxy = (String) body.get("proxy");
        Map<String, Object> out = new LinkedHashMap<>(4);
        if (apiUrl == null || apiUrl.isEmpty()) {
            out.put("success", false);
            out.put("message", "API地址不能为空");
            return ApiResult.ok(out);
        }
        if (apiKey == null || apiKey.isEmpty()) {
            out.put("success", false);
            out.put("message", "API Key不能为空");
            return ApiResult.ok(out);
        }
        Map<String, Object> result = testSingleKey(protocol, apiUrl, apiKey, "api_key", proxy, 0);
        out.put("success", result.get("ok"));
        out.put("message", result.getOrDefault("error", result.getOrDefault("message", "测试完成")));
        out.put("latency", result.get("latency_ms"));
        return ApiResult.ok(out);
    }

    /** 用指定 Key 调上游 /模型，判定该 Key 是否有效（只读、不改库）。 */
    private Map<String, Object> testSingleKey(String platform, String baseUrl, String apiKey,
                                              String authScheme, String proxy, int index) {
        Map<String, Object> r = new LinkedHashMap<>(6);
        r.put("index", index);
        r.put("key_mask", maskKey(apiKey));
        try {
            if (baseUrl == null || baseUrl.isBlank()) {
                r.put("ok", false);
                r.put("error", "base_url 未配置");
                return r;
            }
            Map<String, Object> fakeCreds = new LinkedHashMap<>(2);
            fakeCreds.put("api_key", apiKey);
            List<Map<String, Object>> models = requestUpstreamModels(baseUrl, apiKey,
                    (platform != null && platform.toLowerCase().contains("gemini")), proxy);
            if (models == null) {
                r.put("ok", false);
                r.put("error", "无法从上游拉取模型（401/404/网络），key 可能无效");
            } else {
                r.put("ok", true);
                r.put("model_count", models.size());
            }
        } catch (Exception e) {
            r.put("ok", false);
            r.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
        }
        return r;
    }

    /**
     * 脱敏 Key：仅保留前 6 位 + 后 4 位，避免明文回显。
     *
     * @param key Key
     * @return maskKey的结果
     */
    private static String maskKey(String key) {
        if (key == null || key.length() <= 10) {
            return "****";
        }
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    /**
     * 提取凭证中全部 Key（api_Key 数组 + api_Key 标量，去重保序）。
     *
     * @param credentials 凭证
     * @return extract全部Key的结果
     */
    private List<String> extractAllKeys(Map<String, Object> credentials) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (credentials != null) {
            Object multi = credentials.get("api_keys");
            if (multi instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null && !o.toString().isBlank()) {
                        set.add(o.toString());
                    }
                }
            }
            Object single = credentials.get("api_key");
            if (single instanceof String s && !s.isBlank()) {
                set.add(s);
            }
        }
        return new ArrayList<>(set);
    }

 // ==================== 提供者 Connections ====================

    /**
     * 列举 提供者 的额外连接（按 角色）。
     *
     * @param providerId 提供者 标识
     * @return ProviderConnectionResponse 列表
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     * @param @PathVariable("provider_id" @PathVariable("provider_id"
     */
    @GetMapping("/providers/{provider_id}/connections")
    public ApiResult<List<Map<String, Object>>> listConnections(@PathVariable("provider_id") String providerId) {
        requireProvider(providerId);
        List<SysAiProviderConnection> conns = connectionMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .orderByAsc(SysAiProviderConnection::getCreatedAt));
        List<Map<String, Object>> items = conns.stream()
                .map(this::toConnectionResponse)
                .collect(Collectors.toList());
        return ApiResult.ok(items);
    }

    /**
     * 获取单条 提供者 连接。
     *
     * @param providerId 提供者 标识
     * @param role       连接角色
     * @return ProviderConnectionResponse
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     */
    @GetMapping("/providers/{provider_id}/connections/{role}")
    public ApiResult<Map<String, Object>> getConnection(@PathVariable("provider_id") String providerId,
                                                        @PathVariable("role") String role) {
        requireProvider(providerId);
        SysAiProviderConnection conn = connectionMapper.selectOne(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .eq(SysAiProviderConnection::getRole, role));
        if (conn == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "connection 不存在: " + role);
        }
        return ApiResult.ok(toConnectionResponse(conn));
    }

    /**
     * 保存（新建或更新）提供者 连接。
     *
     * @param providerId 提供者 标识
     * @param body       保存提供者connection请求
     * @return ProviderConnectionResponse
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     */
    @PutMapping("/providers/{provider_id}/connections")
    @Transactional
    public ApiResult<Map<String, Object>> saveConnection(@PathVariable("provider_id") String providerId,
                                                         @RequestBody Map<String, Object> body) {
        SysAiProvider entity = requireProvider(providerId);
        String role = requireStr(body, "role");

        SysAiProviderConnection existing = connectionMapper.selectOne(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .eq(SysAiProviderConnection::getRole, role));

        if (existing == null) {
            // 新建：凭证为必填
            if (body.get("credentials") == null) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "新建连接时 credentials 必填");
            }
            SysAiProviderConnection conn = toConnectionEntity(providerId, body, System.currentTimeMillis());
            connectionMapper.insert(conn);
            return ApiResult.ok(toConnectionResponse(conn));
        } else {
 // 更新：仅覆盖显式出现的字段，缺省 凭证 保留既有
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
                existing.setExtra(uncheckedCast(body.get("extra")));
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
     * 删除 提供者 连接。
     *
     * @param providerId 提供者 标识
     * @param role       连接角色
     * @return 成功
     * @param @PathVariable("provider_id" @路径变量("提供者_标识"
     */
    @DeleteMapping("/providers/{provider_id}/connections/{role}")
    @Transactional
    public ApiResult<Void> deleteConnection(@PathVariable("provider_id") String providerId,
                                            @PathVariable("role") String role) {
        requireProvider(providerId);
        int deleted = connectionMapper.delete(
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .eq(SysAiProviderConnection::getRole, role));
        if (deleted == 0) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "connection 不存在: " + role);
        }
        return ApiResult.ok();
    }

 // ==================== 提供者 模型 ====================

    /**
     * 列举 提供者_模型 行（可按 提供者_标识 过滤）。
     *
     * @param providerId 可选过滤
     * @return ProviderModelResponse 列表
     */
    @GetMapping("/provider-models")
    public ApiResult<List<Map<String, Object>>> listProviderModels(
            @RequestParam(required = false) String provider_id) {
        LambdaQueryWrapper<SysAiProviderModel> qw = new LambdaQueryWrapper<>();
        if (provider_id != null) {
            qw.eq(SysAiProviderModel::getProviderId, provider_id);
        }
        qw.orderByAsc(SysAiProviderModel::getCreatedAt);

        List<SysAiProviderModel> models = modelMapper.selectList(qw);
        List<Map<String, Object>> items = new ArrayList<>(8); // 模型数量不可预估
        for (SysAiProviderModel model : models) {
            SysAiProvider provider = providerMapper.selectById(model.getProviderId());
            if (provider != null) {
                items.add(toProviderModelResponse(provider, model));
            }
        }
        return ApiResult.ok(items);
    }

    /**
     * 全量保存（upsert）某 提供者 下某模型的完整能力集。
     *
     * @param body 保存提供者模型请求
     * @return ProviderModelResponse
     */
    @PutMapping("/provider-models")
    @Transactional
    public ApiResult<Map<String, Object>> saveProviderModel(@RequestBody Map<String, Object> body) {
        String providerId = requireStr(body, "provider_id");
        SysAiProvider provider = requireProvider(providerId);
        Map<String, Object> modelInput = uncheckedCast(body.get("model"));
        if (modelInput == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "model 不能为空");
        }
        String modelName = requireStr(modelInput, "model");

        SysAiProviderModel existing = modelMapper.selectOne(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId)
                        .eq(SysAiProviderModel::getModel, modelName));

        if (existing == null) {
            SysAiProviderModel newModel = toModelEntity(providerId, modelInput, System.currentTimeMillis());
            modelMapper.insert(newModel);
            return ApiResult.ok(toProviderModelResponse(provider, newModel));
        } else {
            /**
             * apply模型输入。
             * @param existing existing
             * @param modelInput 模型输入
             */
            applyModelInput(existing, modelInput);
            existing.setUpdatedAt(System.currentTimeMillis());
 // 复合主键 (提供者_id, 模型)：显式按双键更新，避免同名模型跨供应商误伤
            modelMapper.update(existing, new LambdaUpdateWrapper<SysAiProviderModel>()
                    .eq(SysAiProviderModel::getProviderId, providerId)
                    .eq(SysAiProviderModel::getModel, modelName));
            return ApiResult.ok(toProviderModelResponse(provider, existing));
        }
    }

    /**
     * 重命名模型 标识（复合主键变更：复制行到新 标识 并删除旧行，原子完成）。
     *
     * @param body { 提供者_标识, 模型, 新_模型 }
     * @return 重命名后的 提供者模型响应
     */
    @PostMapping("/provider-models/rename")
    @Transactional
    public ApiResult<Map<String, Object>> renameProviderModel(@RequestBody Map<String, Object> body) {
        String providerId = requireStr(body, "provider_id");
        SysAiProvider provider = requireProvider(providerId);
        String oldName = requireStr(body, "model");
        String newName = requireStr(body, "new_model");
 // 新 标识 非法或与旧 标识 相同直接拒绝
        if (newName.equals(oldName)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "新模型 ID 不能与原 ID 相同");
        }
        SysAiProviderModel existing = modelMapper.selectOne(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId)
                        .eq(SysAiProviderModel::getModel, oldName));
        if (existing == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider model 不存在: " + oldName);
        }
 // 目标 标识 已被占用则冲突
        SysAiProviderModel dup = modelMapper.selectOne(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId)
                        .eq(SysAiProviderModel::getModel, newName));
        if (dup != null) {
            throw new ApiException(ApiErrorCode.CONFLICT, "模型 ID 已存在: " + newName);
        }
 // 复制到新 标识 并删除旧行（同事务原子生效）
        existing.setModel(newName);
        existing.setUpdatedAt(System.currentTimeMillis());
        modelMapper.insert(existing);
        modelMapper.delete(new LambdaQueryWrapper<SysAiProviderModel>()
                .eq(SysAiProviderModel::getProviderId, providerId)
                .eq(SysAiProviderModel::getModel, oldName));
        return ApiResult.ok(toProviderModelResponse(provider, existing));
    }

    /**
     * 删除 提供者_模型 行（按复合主键 提供者_标识 + 模型）。
     *
     * @param providerId 提供者 标识
     * @param model      模型名
     * @return 成功
     * @param @RequestParam("provider_id" @请求参数("提供者_标识"
     */
    @DeleteMapping("/provider-models")
    @Transactional
    public ApiResult<Void> deleteProviderModel(@RequestParam("provider_id") String providerId,
                                               @RequestParam("model") String model) {
        requireProvider(providerId);
        int deleted = modelMapper.delete(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId)
                        .eq(SysAiProviderModel::getModel, model));
        if (deleted == 0) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider model 不存在: " + model);
        }
        return ApiResult.ok();
    }

 // ==================== 模型 协议 ====================

    /**
     * 模型协议能力清单（服务端运营默认）。
     *
     * @param preset  平台预设
     * @param task    请求任务
     * @param baseUrl 已存在 提供者 URL 提示
     * @return ModelProtocolManifestResponse
     */
    @GetMapping("/model-protocols")
    public ApiResult<Map<String, Object>> modelProtocols(@RequestParam String preset,
                                                         @RequestParam String task,
                                                         @RequestParam(required = false) String base_url) {
        return ApiResult.ok(buildManifest(preset, task, base_url));
    }

    /**
     * 构造 提供者响应（含模型行与连接档案）。
     */
    private Map<String, Object> toProviderResponse(SysAiProvider provider,
                                                   List<SysAiProviderModel> models,
                                                   List<SysAiProviderConnection> connections) {
        Map<String, Object> map = new LinkedHashMap<>(12);
        map.put("provider_id", provider.getProviderId());
        map.put("platform", provider.getPlatform());
        map.put("name", provider.getName());
        map.put("base_url", provider.getBaseUrl());
        map.put("auth_scheme", provider.getAuthScheme());
        boolean hasCreds = provider.getCredentials() != null && !provider.getCredentials().isEmpty();
        map.put("has_credentials", hasCreds);
        
        // 返回key数量和掩码，方便前端显示"已配置N个key"
        if (hasCreds) {
            Map<String, Object> creds = provider.getCredentials();
            int keyCount = 0;
            String firstKeyMask = "";
            
            // 单个key
            Object singleKey = creds.get("api_key");
            if (singleKey != null && !singleKey.toString().isEmpty()) {
                String[] keys = singleKey.toString().split(",");
                keyCount = keys.length;
                firstKeyMask = maskKey(keys[0].trim());
            }
            
            // 多个key（list形式）
            Object multiKeys = creds.get("api_keys");
            if (multiKeys instanceof List) {
                List<?> keys = (List<?>) multiKeys;
                keyCount = keys.size();
                if (!keys.isEmpty() && keys.getFirst() != null) {
                    firstKeyMask = maskKey(keys.getFirst().toString().trim());
                }
            }
            
            map.put("api_key_count", keyCount);
            map.put("api_key_mask", firstKeyMask);

            // 返回完整的key列表，方便前端编辑时显示
            List<String> allKeys = new ArrayList<>();
            if (singleKey != null && !singleKey.toString().isEmpty()) {
                for (String k : singleKey.toString().split(",")) {
                    String trimmed = k.trim();
                    if (!trimmed.isEmpty()) {
                        allKeys.add(trimmed);
                    }
                }
            }
            if (multiKeys instanceof List) {
                for (Object k : (List<?>) multiKeys) {
                    if (k != null && !k.toString().isEmpty()) {
                        allKeys.add(k.toString().trim());
                    }
                }
            }
            map.put("api_keys", allKeys);
        }
        List<Map<String, Object>> modelItems = new ArrayList<>(8); // 模型数量不可预估
        for (SysAiProviderModel model : models) {
            modelItems.add(toProviderModelResponse(provider, model));
        }
        map.put("models", modelItems);
        map.put("enabled", provider.getEnabled());
        if (provider.getBedrockConfig() != null) {
            map.put("bedrock_config", provider.getBedrockConfig());
        }
        map.put("proxy", provider.getProxy());
        map.put("extra", provider.getExtra());
        map.put("sort_order", provider.getSortOrder());
        map.put("created_at", provider.getCreatedAt());
        map.put("updated_at", provider.getUpdatedAt());
        return map;
    }

    /**
     * 构造 提供者模型响应（含任务能力数组），并从缓存快照合并价格/延迟/速度等指标。
     * @param provider 提供者
     * @param model 模型
     * @return 转为提供者模型响应的结果
     */
    private Map<String, Object> toProviderModelResponse(SysAiProvider provider, SysAiProviderModel model) {
        Map<String, Object> map = new LinkedHashMap<>(12);
        map.put("provider_id", provider.getProviderId());
        map.put("model", model.getModel());
        map.put("id", provider.getProviderId() + ":" + model.getModel());
        map.put("modelType", "text");
        map.put("enabled", model.getEnabled());
        map.put("sort_order", model.getSortOrder());
        if (model.getDescription() != null) {
            map.put("description", model.getDescription());
        }
        List<Map<String, Object>> caps = model.getCapabilities() != null
                ? model.getCapabilities().stream().map(this::toCapabilityResponse).collect(Collectors.toList())
                : new ArrayList<>(1);
        map.put("capabilities", caps);
 // 模型指标：自身 指标 优先，再从缓存快照合并价格/延迟/智能/速度等
        Map<String, Object> metrics = model.getMetrics() != null
                ? new LinkedHashMap<>(model.getMetrics())
                : new LinkedHashMap<>(4);
        Map<String, Object> cached = metricIndex().get(provider.getProviderId() + "::" + model.getModel());
        if (cached != null) {
            for (String key : METRIC_KEYS) {
                Object value = cached.get(key);
                if (value != null) {
                    metrics.putIfAbsent(key, value);
                }
            }
        }
        if (!metrics.isEmpty()) {
            map.put("metrics", metrics);
        }
        map.put("created_at", model.getCreatedAt());
        map.put("updated_at", model.getUpdatedAt());
        return map;
    }

    /**
     * 构建（带 TTL 缓存）提供者标识::模型 → 缓存快照行的索引。
     *
     * <p>指标由 {@link ModelMetricsPushTask} 定时写入 Redis，本方法优先读取
     * Redis 快照并本地缓存 30 秒，避免每个模型行重复解析快照。</p>
     *
     * @return 指标索引，快照为空时返回空 映射
     */
    private Map<String, Map<String, Object>> metricIndex() {
        long now = System.currentTimeMillis();
        if (now - metricsIndexLoadedAt <= METRICS_INDEX_TTL_MILLIS && !metricsIndex.isEmpty()) {
            return metricsIndex;
        }
        synchronized (this) {
            if (now - metricsIndexLoadedAt <= METRICS_INDEX_TTL_MILLIS && !metricsIndex.isEmpty()) {
                return metricsIndex;
            }
            Map<String, Map<String, Object>> index = new HashMap<>(64); // 提供者模型索引不可预估
            try {
                Map<String, Object> snapshot = metricsSnapshotService.latestSnapshot();
                Object modelsObj = snapshot.get("models");
                if (modelsObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> row)) {
                            continue;
                        }
                        Object providerId = row.get("provider_id");
                        Object modelName = row.get("model");
                        if (providerId != null && modelName != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> rowMap = (Map<String, Object>) row;
                            index.put(providerId + "::" + modelName, rowMap);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[ApiProvider] 缓存指标索引构建失败: {}", e.getMessage());
            }
            metricsIndex = index;
            metricsIndexLoadedAt = now;
        }
        return metricsIndex;
    }

    /**
     * 构造单任务能力响应（含最近健康探测结果回显）。
     * @param cap cap
     * @return 转为capability响应的结果
     */
    private Map<String, Object> toCapabilityResponse(Map<String, Object> cap) {
        Map<String, Object> map = new LinkedHashMap<>(14);
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
 // 最近一次健康探测结果（含 状态/延迟/错误）
        if (cap.get("health") != null) {
            map.put("health", cap.get("health"));
        }
        if (cap.get("health_checked_at") != null) {
            map.put("health_checked_at", cap.get("health_checked_at"));
        }
        map.put("created_at", cap.get("created_at"));
        map.put("updated_at", cap.get("updated_at"));
        return map;
    }

    /**
     * 构造 提供者connection响应（不回显 凭证）。
     * @param conn conn
     * @return 转为connection响应的结果
     */
    private Map<String, Object> toConnectionResponse(SysAiProviderConnection conn) {
        Map<String, Object> map = new LinkedHashMap<>(10);
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
     * @param providerId 提供者标识
     * @param input 输入
     * @param now now
     * @return 转为模型实体的结果
     */
    @SuppressWarnings("unchecked")
    private SysAiProviderModel toModelEntity(String providerId, Map<String, Object> input, long now) {
        SysAiProviderModel e = new SysAiProviderModel();
        e.setProviderId(providerId);
        e.setModel(requireStr(input, "model"));
        e.setEnabled(bool(input, "enabled", true));
        e.setSortOrder(intv(input, "sort_order", 0));
        e.setDescription(str(input, "description"));
 // capabilities → 直接存原始 列表<Map>
        Object capsObj = input.get("capabilities");
        if (capsObj instanceof List<?> capsList) {
            List<Map<String, Object>> caps = new ArrayList<>(8); // 能力数量不可预估
            for (Object c : capsList) {
                if (c instanceof Map<?, ?> m) {
                    caps.add((Map<String, Object>) m);
                }
            }
            e.setCapabilities(caps);
        } else {
            e.setCapabilities(new ArrayList<>(1));
        }
 // 指标 → 直接存原始 映射（延迟/智能/输出速度等）
        Object metricsObj = input.get("metrics");
        if (metricsObj instanceof Map<?, ?> metricsMap) {
            e.setMetrics((Map<String, Object>) metricsMap);
        }
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    /**
     * 把模型输入应用到已有实体（更新，替换完整能力集）。
     * @param e e
     * @param input 输入
     */
    @SuppressWarnings("unchecked")
    private void applyModelInput(SysAiProviderModel e, Map<String, Object> input) {
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
List<Map<String, Object>> caps = new ArrayList<>(8); // 能力数量不可预估
                for (Object c : capsList) {
                    if (c instanceof Map<?, ?> m) {
                        caps.add((Map<String, Object>) m);
                    }
                }
                e.setCapabilities(caps);
            }
        }
        if (input.containsKey("metrics")) {
            Object metricsObj = input.get("metrics");
            if (metricsObj instanceof Map<?, ?> metricsMap) {
                e.setMetrics((Map<String, Object>) metricsMap);
            } else {
                e.setMetrics(null);
            }
        }
    }

    /**
     * 由连接输入构造连接实体（新建）。
     * @param providerId 提供者标识
     * @param input 输入
     * @param now now
     * @return 转为connection实体的结果
     */
    private SysAiProviderConnection toConnectionEntity(String providerId, Map<String, Object> input, long now) {
        SysAiProviderConnection e = new SysAiProviderConnection();
        e.setConnectionId(String.valueOf(System.currentTimeMillis() + (int)(Math.random() * 1000)));
        e.setProviderId(providerId);
        e.setRole(requireStr(input, "role"));
        e.setLabel(str(input, "label"));
        e.setBaseUrl(requireStr(input, "base_url"));
        e.setAuthScheme(requireStr(input, "auth_scheme"));
        e.setCredentials(uncheckedCast(input.get("credentials")));
        e.setExtra(uncheckedCast(input.get("extra")));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    // ==================== 模型协议清单 ====================

    /**
     * 构建manifest。
     * @param preset preset
     * @param task 任务
     * @param baseUrl baseurl
     * @return 构建manifest的结果
     */
    private Map<String, Object> buildManifest(String preset, String task, String baseUrl) {
        // 前端 preset 可能传大写形式（如 Zhipu），统一转小写后匹配
        String presetKey = preset == null ? null : preset.toLowerCase();
        ProtocolPreset pp = PROTOCOL_PRESETS.getOrDefault(presetKey,
                new ProtocolPreset(presetKey != null ? presetKey : "custom", "https://api.openai.com/v1"));
        // 本地模型（Ollama/ONNX）无需鉴权
        boolean localModel = presetKey != null && (presetKey.contains("ollama") || presetKey.contains("onnx"));
        String defaultAuthScheme = localModel ? "none" : "api_key";
        Map<String, Object> manifest = new LinkedHashMap<>(8);
        manifest.put("tasks", ALL_TASKS);
        manifest.put("preset", preset);
        manifest.put("platform", pp.platform);
        manifest.put("requested_task", task);
        manifest.put("platform_default_base_url", baseUrl != null ? baseUrl : pp.defaultBaseUrl);
        manifest.put("requires_user_input", false);
        manifest.put("default_auth_scheme", defaultAuthScheme);
        manifest.put("auth_schemes", authSchemes());

        Map<String, Object> recommendation = new LinkedHashMap<>(6);
        recommendation.put("protocol_id", pp.platform + "-openai-compat");
        recommendation.put("connection_role", "default");
        recommendation.put("default_base_url", pp.defaultBaseUrl);
        recommendation.put("default_auth_scheme", defaultAuthScheme);
        recommendation.put("base_url_override_required", false);
        manifest.put("recommendation", recommendation);

        manifest.put("protocols", List.of(buildProtocol(pp, task, defaultAuthScheme)));
        return manifest;
    }

    /**
     * 构建协议。
     * @param pp pp
     * @param task 任务
     * @param defaultAuthScheme 默认认证scheme
     * @return 构建协议的结果
     */
    private Map<String, Object> buildProtocol(ProtocolPreset pp, String task, String defaultAuthScheme) {
        Map<String, Object> protocol = new LinkedHashMap<>(8);
        protocol.put("protocol_id", pp.platform + "-openai-compat");
        protocol.put("supported_tasks", ALL_TASKS);
        protocol.put("executor", "model_invoke");
        protocol.put("transport", "http");
        protocol.put("allowed_auth_schemes", List.of("api_key", "bearer", "oauth", "none"));
        protocol.put("scopes", List.of("official_compat", "custom"));
        protocol.put("platforms", List.of(pp.platform));

        Map<String, Object> dc = new LinkedHashMap<>(8);
        dc.put("preset", pp.platform);
        dc.put("platform", pp.platform);
        dc.put("connection_role", "default");
        dc.put("connection_label", null);
        dc.put("base_url", pp.defaultBaseUrl);
        dc.put("auth_scheme", defaultAuthScheme);
        // 本地模型（Ollama/ONNX）无需凭证
        dc.put("requires_credentials", !"none".equals(defaultAuthScheme));
        protocol.put("default_connections", List.of(dc));
        protocol.put("endpoints", List.of());
        return protocol;
    }

    /**
     * 认证schemes。
     * @return 认证schemes的结果
     */
    private List<Map<String, Object>> authSchemes() {
        return List.of(
                /**
                 * 认证scheme。
                 * @param "api_key" "api_键"
                 * @param true true
                 */
                authScheme("api_key", true),
                /**
                 * 认证scheme。
                 * @param "bearer" "bearer"
                 * @param true true
                 */
                authScheme("bearer", true),
                /**
                 * 认证scheme。
                 * @param "oauth" "OAuth"
                 * @param true true
                 */
                authScheme("oauth", true),
                /**
                 * 认证scheme。
                 * @param "none" "无"
                 * @param false false
                 */
                authScheme("none", false)
        );
    }

    /**
     * 认证scheme。
     * @param scheme scheme
     * @param parameterized parameterized
     * @return 认证scheme的结果
     */
    private Map<String, Object> authScheme(String scheme, boolean parameterized) {
        Map<String, Object> m = new LinkedHashMap<>(4);
        m.put("parameterized", parameterized);
        return m;
    }

    /**
     * 协议preset。
     * @param platform platform
     * @param defaultBaseUrl 默认baseurl
     * @return 协议preset的结果
     */
    private record ProtocolPreset(String platform, String defaultBaseUrl) {
    }

    private static final List<String> ALL_TASKS = List.of(
            "chat", "realtime_conversation", "image_generation", "image_edit",
            "video_generation", "speech_synthesis", "speech_recognition",
            "embedding", "rerank"
    );

    private static final Map<String, ProtocolPreset> PROTOCOL_PRESETS; // 协议presets

    static {
        Map<String, ProtocolPreset> m = new LinkedHashMap<>(48);
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
 // 中国平台官方 打开AI 兼容地址
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
 // ONNX 本地离线模型：基础_url 存模型文件路径，无远程端点
        m.put("onnx", new ProtocolPreset("onnx", ""));
        m.put("custom", new ProtocolPreset("custom", "https://api.openai.com/v1"));
        PROTOCOL_PRESETS = Map.copyOf(m);
    }

    // ==================== 模型拉取 ====================

    /**
     * 真实调用上游 {@code GET {base_url}/模型} 拉取模型目录。
     *
     * <p>OpenAI 兼容协议：{@code Authorization: Bearer <key>}，响应 {@code data[].id}；
     * Google Gemini 协议：{@code ?key=<key>} 查询参数，响应 {@code models[].name}（形如 模型/gemini-xx）。
     * 拉取失败时抛出与前端契约对应的错误码（UNAUTHORIZED / FORBIDDEN / RATE_限制 / 超时 / BAD_GATEWAY）。</p>
     *
     * @param platform    平台标识（zhipu/openai/google/...）
     * @param baseUrl     供应商 API 基础地址
     * @param credentials 凭证（兼容 api_Key 字符串与 api_Key 数组两种形态）
     * @param authScheme  鉴权方式（api_Key 等）
     * @param proxy       HTTP 代理地址（可为空）
     * @param tryFix      是否在 404 时尝试补全 /v1 后重试
     * @return FetchModelsResponse（models + 可选 fixed_基础_url）
     */
    private Map<String, Object> fetchModelsFromUpstream(String platform, String baseUrl,
                                                        Map<String, Object> credentials,
                                                        String authScheme, String proxy, boolean tryFix) {
        // 平台标识统一小写后再判断协议族（前端可能传大写形式）
        String platformLower = platform == null ? "" : platform.toLowerCase();
 // ONNX 本地离线模型：基础_url 存的是模型文件路径，没有远程目录可拉，
        // 直接返回空列表（前端提示手输模型文件路径）
        if (platformLower.contains("onnx")) {
        Map<String, Object> out = new LinkedHashMap<>(4);
            out.put("models", new ArrayList<Map<String, Object>>(1));
            return out;
        }
        // 基础地址缺失直接判参数错误
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "base_url 未配置，无法拉取模型列表");
        }
        String apiKey = extractApiKey(credentials);
        boolean googleProtocol = platformLower.contains("gemini") || platformLower.contains("google");
        boolean ollamaProtocol = platformLower.contains("ollama");

        // 首次按原始地址拉取
        List<Map<String, Object>> models = requestUpstreamModels(baseUrl, apiKey, googleProtocol, proxy);
        String fixedBaseUrl = null;
        // 404 且允许修正时，尝试补全 /v1 再拉一次（用户常见漏填 /v1 的场景）
        if (models == null && tryFix && !googleProtocol) {
            String fixed = baseUrl.endsWith("/") ? baseUrl + "v1" : baseUrl + "/v1";
            models = requestUpstreamModels(fixed, apiKey, false, proxy);
            if (models != null) {
                fixedBaseUrl = fixed;
            }
        }
 // Ollama 兼容回退：原生接口为 获取 {基础}/api/标签（响应 模型[].名称）
        if (models == null && ollamaProtocol) {
            String tagsUrl = baseUrl.replaceAll("/v1/?$", "");
            models = requestOllamaTags(tagsUrl, apiKey, proxy);
        }
        // 仍然失败则按无法连接处理
        if (models == null) {
            throw new ApiException(ApiErrorCode.BAD_GATEWAY, "无法从供应商拉取模型列表，请检查 API 地址和 Key");
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("models", models);
        if (fixedBaseUrl != null) {
            out.put("fixed_base_url", fixedBaseUrl);
        }
        return out;
    }

    /**
     * 请求 Ollama 原生 {@code GET {base}/api/标签} 模型列表。
     *
     * @param baseUrl 基础地址（不含 /v1）
     * @param apiKey  API Key（Ollama 通常无需鉴权，可为 空）
     * @param proxy   HTTP 代理地址（可为空）
     * @return 模型条目列表；请求失败返回 空
     */
    private List<Map<String, Object>> requestOllamaTags(String baseUrl, String apiKey, String proxy) {
        String url = baseUrl.endsWith("/") ? baseUrl + "api/tags" : baseUrl + "/api/tags";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(UPSTREAM_TIMEOUT)
                .GET();
        try {
            HttpResponse<String> response = httpClient(proxy).send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            List<Map<String, Object>> models = new ArrayList<>(8); // 模型数量不可预估
            com.fasterxml.jackson.databind.JsonNode root = readJson(response.body());
            com.fasterxml.jackson.databind.JsonNode data = root == null ? null : root.get("models");
            if (data != null && data.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : data) {
                    String name = item.path("name").asText(null);
                    // 跳过无名条目
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    Map<String, Object> m = new LinkedHashMap<>(4);
                    m.put("id", name);
                    m.put("name", name);
                    m.put("tasks", inferTasks(name));
                    m.put("traits", List.of("streaming"));
                    models.add(m);
                }
            }
            return models;
        } catch (Exception e) {
            // 网络层异常按不可达处理
            return null;
        }
    }

    /**
     * 请求上游模型列表接口。
     *
     * @param baseUrl        基础地址
     * @param apiKey         API Key（可能为 空，如 Ollama 本地实例）
     * @param googleProtocol 是否使用 Gemini 协议
     * @param proxy          HTTP 代理地址（可为空）
     * @return 模型条目列表；请求失败返回 空（由调用方决定是否重试/报错）
     */
    private List<Map<String, Object>> requestUpstreamModels(String baseUrl, String apiKey, boolean googleProtocol, String proxy) {
 // 拼接模型列表 URL：打开AI 兼容为 {base}/模型，Gemini 为 {base}/模型?Key=xxx
        StringBuilder url = new StringBuilder(baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models");
        if (googleProtocol && apiKey != null && !apiKey.isBlank()) {
            url.append(url.indexOf("?") >= 0 ? "&" : "?").append("key=").append(apiKey);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(UPSTREAM_TIMEOUT)
                .GET();
 // 打开AI 兼容协议使用 Bearer 头鉴权
        if (!googleProtocol && apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<String> response = httpClient(proxy).send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
 // 返回 空 交给调用方决定是否 尝试_fix 重试
                return null;
            }
            if (status < 200 || status >= 300) {
                throw new ApiException(ApiErrorCode.BAD_GATEWAY, "供应商模型接口返回 " + status);
            }
            /**
             * 解析google模型。
             * @param response.body( 响应.主体(
             * @return 解析google模型的结果
             */
            return googleProtocol ? parseGoogleModels(response.body()) : parseOpenAiModels(response.body());
        } catch (ApiException e) {
            // 业务异常直接向上抛出
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ApiException(ApiErrorCode.TIMEOUT, "供应商模型接口请求超时");
        } catch (Exception e) {
            // 网络层异常统一按网关错误处理
            /**
             * api异常。
             * @param ApiErrorCode.BAD_GATEWAY api错误编码.badgateway
             * @param e.getMessage( e.获取消息(
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.BAD_GATEWAY, "无法连接供应商模型接口: " + e.getMessage());
        }
    }

    /**
     * 解析 打开AI 兼容的模型列表响应（数据[].标识）。
     *
     * @param body 响应体 JSON
     * @return 模型条目列表
     */
    private List<Map<String, Object>> parseOpenAiModels(String body) {
        List<Map<String, Object>> models = new ArrayList<>(8); // 模型数量不可预估
        com.fasterxml.jackson.databind.JsonNode root = readJson(body);
 // 响应格式：{"数据": [{"id": "glm-4-flash", ...}, ...]}
        com.fasterxml.jackson.databind.JsonNode data = root == null ? null : root.get("data");
        if (data != null && data.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : data) {
                String id = item.path("id").asText(null);
 // 跳过无 标识 的条目
                if (id == null || id.isBlank()) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>(4);
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
     * 解析 Google Gemini 的模型列表响应（模型[].名称，形如 模型/gemini-xx）。
     *
     * @param body 响应体 JSON
     * @return 模型条目列表
     */
    private List<Map<String, Object>> parseGoogleModels(String body) {
        List<Map<String, Object>> models = new ArrayList<>(8); // 模型数量不可预估
        com.fasterxml.jackson.databind.JsonNode root = readJson(body);
        com.fasterxml.jackson.databind.JsonNode data = root == null ? null : root.get("models");
        if (data != null && data.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : data) {
                String name = item.path("name").asText(null);
 // 去掉 "模型/" 前缀作为模型 ID
                String id = name != null && name.startsWith("models/") ? name.substring("models/".length()) : name;
                if (id == null || id.isBlank()) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>(4);
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
     * 解析 JSON 字符串，失败返回 空。
     *
     * @param body JSON 字符串
     * @return JsonNode 或 空
     */
    private com.fasterxml.jackson.databind.JsonNode readJson(String body) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 依据模型 标识 关键字推断任务类型。
     *
     * @param modelId 模型 标识
     * @return 任务类型列表（默认 对话）
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
     * 从凭证 映射 中提取 API Key，兼容 api_Key（字符串）与 api_Key（数组）两种形态。
     *
     * @param credentials 凭证 映射（可能为 空）
     * @return API Key 或 空
     */
    private String extractApiKey(Map<String, Object> credentials) {
 // 无凭证直接返回 空
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        Object single = credentials.get("api_key");
        if (single instanceof String && !((String) single).isBlank()) {
            return (String) single;
        }
        Object multi = credentials.get("api_keys");
 // 多 Key 数组取第一个作为拉取凭证
        if (multi instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    // ==================== 工具方法 ====================

    /**
     * 获取 提供者 实体，不存在则抛 NOT_FOUND。
     * @param providerId 提供者标识
     * @return require提供者的结果
     */
    private SysAiProvider requireProvider(String providerId) {
        SysAiProvider e = providerMapper.selectById(providerId);
        if (e == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider 不存在: " + providerId);
        }
        return e;
    }

    /**
     * str。
     * @param map 映射
     * @param key Key
     * @return str的结果
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
     * requirestr。
     * @param map 映射
     * @param key Key
     * @return requireStr的结果
     */
    private String requireStr(Map<String, Object> map, String key) {
        String s = str(map, key);
        if (s == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "字段 " + key + " 不能为空");
        }
        return s;
    }

    /**
     * bool。
     * @param map 映射
     * @param key Key
     * @param def def
     * @return bool的结果
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
     * intv。
     * @param map 映射
     * @param key Key
     * @param def def
     * @return intv的结果
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

    @SuppressWarnings("unchecked")
    /**
     * 列表的str。
     * @param map 映射
     * @param key Key
     * @return 列表的str的结果
     */
    private List<String> listOfStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>(4); // 元素数量不可预估
        for (Object o : list) {
            if (o != null) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    /**
     * 列表的映射。
     * @param map 映射
     * @param key Key
     * @return 列表的映射的结果
     */
    private List<Map<String, Object>> listOfMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(4); // 元素数量不可预估
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    /**
     * uncheckedcast。
     * @param obj obj
     * @return uncheckedCast的结果
     */
    private <T> T uncheckedCast(Object obj) {
        return (T) obj;
    }

    /**
     * 按代理地址获取（缓存）的 HTTP 客户端。
     *
     * <p>未配置代理时返回共享 {@link #upstreamHttpClient}；配置了代理（如
     * {@code http://127.0.0.1:7890}、{@code socks5://127.0.0.1:1080}）时按代理地址
     * 复用缓存的带 {@link ProxySelector} 客户端，避免每个请求重复创建。</p>
     *
     * @param proxy HTTP 代理地址，可为空
     * @return HttpClient
     */
    private HttpClient httpClient(String proxy) {
        if (proxy == null || proxy.isBlank()) {
            return upstreamHttpClient;
        }
        return proxyHttpClients.computeIfAbsent(proxy.trim(), addr -> HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(ProxySelector.of(new java.net.InetSocketAddress(
                        proxyHost(addr), proxyPort(addr))))
                .build());
    }

    /**
     * 解析代理地址的主机名（支持 {@code http://host:port} 与 {@code socks5://host:port}）。
     * @param proxy 代理
     * @return 代理主机的结果
     */
    private static String proxyHost(String proxy) {
        String trimmed = proxy.trim();
        int scheme = trimmed.indexOf("://");
        String rest = scheme >= 0 ? trimmed.substring(scheme + 3) : trimmed;
        int colon = rest.lastIndexOf(':');
        return colon > 0 ? rest.substring(0, colon) : rest;
    }

    /**
     * 解析代理地址的端口（默认 8080）。
     * @param proxy 代理
     * @return 代理端口的结果
     */
    private static int proxyPort(String proxy) {
        String trimmed = proxy.trim();
        int scheme = trimmed.indexOf("://");
        String rest = scheme >= 0 ? trimmed.substring(scheme + 3) : trimmed;
        int colon = rest.lastIndexOf(':');
        if (colon <= 0 || colon == rest.length() - 1) {
            return 8080;
        }
        try {
            return Integer.parseInt(rest.substring(colon + 1));
        } catch (NumberFormatException ex) {
            return 8080;
        }
    }

    /**
     * 所有已注册的 对话客户端 列表
     */
    private final Map<String, HttpClient> proxyHttpClients = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 构建 Agent 虚拟 提供者（含已注册的 Agent 模型列表）。
     * @param modelNames 模型名称
     * @return 构建Agent提供者的结果
     */
    private Map<String, Object> buildAgentProvider(List<String> modelNames) {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> models = new ArrayList<>(8); // 模型数量不可预估
        for (String modelName : modelNames) {
            Map<String, Object> model = new LinkedHashMap<>(10);
            model.put("provider_id", "agent");
            model.put("model", modelName);
            model.put("enabled", true);
            model.put("sort_order", 0);
            model.put("description", "Agent: " + modelName.replace("agent-", ""));
            Map<String, Object> capability = new LinkedHashMap<>(8);
            capability.put("task", "chat");
            capability.put("traits", List.of());
            capability.put("protocol", "agent");
            capability.put("connection_role", "user");
            capability.put("allow_cross_origin_credentials", false);
            capability.put("provider_params", null);
            capability.put("created_at", now);
            capability.put("updated_at", now);
            model.put("capabilities", List.of(capability));
            model.put("created_at", now);
            model.put("updated_at", now);
            models.add(model);
        }
        Map<String, Object> provider = new LinkedHashMap<>(10);
        provider.put("provider_id", "agent");
        provider.put("platform", "agent");
        provider.put("name", "Agent");
        provider.put("base_url", "");
        provider.put("auth_scheme", "none");
        provider.put("has_credentials", false);
        provider.put("models", models);
        provider.put("enabled", true);
        provider.put("sort_order", -1);
        provider.put("created_at", now);
        provider.put("updated_at", now);
        return provider;
    }
}

