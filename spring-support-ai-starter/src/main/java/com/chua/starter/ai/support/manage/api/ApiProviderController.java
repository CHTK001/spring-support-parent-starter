// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.common.support.ai.chat.AuthHeaders;
import com.chua.common.support.ai.chat.ChatClientIndicator;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.indicator.ChatClientIndicators;
import com.chua.common.support.ai.chat.indicator.HttpChatClientIndicator;
import com.chua.common.support.ai.chat.route.RouteTarget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.UUID;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.service.ModelMetricsSnapshotService;
import com.chua.starter.ai.support.manage.service.OnnxModelTaxonomy;
import com.chua.starter.ai.support.manage.service.SysAiModelMetricsService;
import com.chua.starter.ai.support.manage.store.SysAiModelMetrics;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.chat.MagpieRouteSource;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import com.chua.starter.common.support.annotations.Permission;
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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
     * Magpie 网关路由源：免费渠道的模型清单要并入网关当前动态模型
     */
    private final MagpieRouteSource magpieRouteSource;

    /**
     * 模型价格/指标快照服务（读取 Redis 缓存，含价格/延迟/速度/智能等指标）
     */
    private final ModelMetricsSnapshotService metricsSnapshotService;

    /**
     * Agent 模型注册中心（动态注入 Agent 虚拟 提供者）
     */
    private final com.chua.starter.ai.support.registry.ChatClientRegistry chatClientRegistry;

    /**
     * 全局设置映射器（持久化 ONNX 外部服务器目录，重启后恢复）
     */
    private final com.chua.starter.ai.support.mapper.SysAiGlobalSettingMapper globalSettingMapper;

    /**
     * JSON 序列化（读写 ONNX 外部目录清单）
     */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 本地模型门面（ONNX 提供商的模型目录来源）
     */
    private final com.chua.starter.ai.support.service.AiModelCatalogService aiModelCatalogService;

    /**
     * 模型指标扩展服务：保存 / 改名模型后，用采集到的指标补全上下文长度等参数
     * （仅在对应字段尚未配置时回填，不覆盖用户已填的值）
     */
    private final com.chua.starter.ai.support.service.AiModelMetricsEnrichmentService modelMetricsEnrichmentService;

    /**
     * 模型指标库服务（sync-pricing 同步入库）
     */
    private final com.chua.starter.ai.support.manage.service.SysAiModelMetricsService sysAiModelMetricsService;

    /**
     * ONNX 供应商物化服务：ONNX 供应商与模型镜像同步
     */
    private final com.chua.starter.ai.support.manage.service.OnnxProviderMaterializer onnxProviderMaterializer;

    /**
     * ONNX 外部模型目录服务：目录选取（对接后台文件系统接口）与登记、注销、启动恢复。
     */
    private final com.chua.starter.ai.support.manage.service.OnnxExternalDirectoryService onnxExternalDirectoryService;

    /**
     * 路由组业务：成员表（来自已添加的模型库）读写、顺序调整、成员删除级联
     */
    private final com.chua.starter.ai.support.chat.RouterGroupService routerGroupService;

    /**
     * 模型 → 客户端解析器（路由组配置变更后让缓存立即失效）
     */
    private final com.chua.starter.ai.support.chat.ModelClientResolver modelClientResolver;

    /**
     * ONNX 供应商标识：与其余提供商一样是 {@code sys_ai_provider} 表里的真实一行，
     * 由 {@link com.chua.starter.ai.support.manage.service.OnnxProviderMaterializer} 物化维护。
     */
    private static final String ONNX_PROVIDER_ID =
            com.chua.starter.ai.support.manage.service.OnnxProviderMaterializer.ONNX_PROVIDER_ID;

    /**
     * 判断供应商标识是否为内置的 ONNX 供应商。
     *
     * @param providerId 供应商标识
     * @return true=ONNX 供应商
     */
    private static boolean isOnnxProvider(String providerId) {
        return providerId != null && ONNX_PROVIDER_ID.equalsIgnoreCase(providerId.trim());
    }

    /**
     * 判断供应商行是否为内置 ONNX 供应商（按标识或平台判定）。
     *
     * @param provider 供应商行
     * @return true=ONNX 供应商
     */
    private static boolean isOnnxRow(SysAiProvider provider) {
        if (provider == null) {
            return false;
        }
        return isOnnxProvider(provider.getProviderId())
                || (provider.getPlatform() != null
                        && provider.getPlatform().toLowerCase(Locale.ROOT).contains("onnx"));
    }

    /**
     * 渠道管理页 OAuth 行（auth_scheme='oauth'）：由渠道管理为每个已注册渠道镀铬创建，
     * 没有模型 / 凭证，模型管理的供应商列表不暴露这些行。
     *
     * @param provider 供应商
     * @return true=渠道 OAuth provider
     */
    private static boolean isOAuthChannelRow(SysAiProvider provider) {
        if (provider == null) {
            return false;
        }
        return "oauth".equalsIgnoreCase(provider.getAuthScheme());
    }

    /**
     * 免费渠道行（platform='free-channel'/'free'）：渠道管理在模型层的供应层，保留在模型管理列表。
     *
     * @param provider 供应商
     * @return true=免费渠道 provider
     */
    private static boolean isFreeChannelRow(SysAiProvider provider) {
        if (provider == null) {
            return false;
        }
        return ProviderRouteTargets.isFreeChannel(provider.getPlatform());
    }

    /**
     * 新增 / 克隆供应商前的唯一性校验。
     *
     * <p>两条口径：</p>
     * <ul>
     *   <li>ONNX 是内置单行，任何新增或克隆请求都不允许再落一条 ONNX 平台供应商 ——
     *       要改它的地址或凭证只能编辑那一行；</li>
     *   <li>其余供应商按「同平台 + 同名称（去空格、忽略大小写）」判重，
     *       避免列表里出现两个都叫 DeepSeek 的条目导致默认模型选择歧义。</li>
     * </ul>
     *
     * @param platform         平台标识
     * @param name             供应商名称
     * @param excludeProviderId 需要跳过的供应商标识（克隆时为源供应商，可为 null）
     */
    private void requireNotDuplicateProvider(String platform, String name, String excludeProviderId) {
        String platformKey = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        // 排除自身：编辑 ONNX 那一行是合法操作（改地址、改凭证、改名称），
        // 只有「把别的供应商改成 ONNX」或「新建一个 ONNX 同名货」才需要拦。
        boolean onnxSelfRow = isOnnxProvider(excludeProviderId);
        if (!onnxSelfRow && (platformKey.contains("onnx") || isOnnxProvider(name))) {
            throw new ApiException(ApiErrorCode.CONFLICT,
                    "ONNX 供应商为内置单行，不允许重复新增；如需调整请直接编辑该供应商");
        }
        String nameKey = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (nameKey.isEmpty()) {
            return;
        }
        List<SysAiProvider> samePlatform = providerMapper.selectList(
                new LambdaQueryWrapper<SysAiProvider>()
                        .eq(SysAiProvider::getPlatform, platform));
        for (SysAiProvider candidate : samePlatform) {
            if (excludeProviderId != null && excludeProviderId.equals(candidate.getProviderId())) {
                continue;
            }
            String candidateName = candidate.getName() == null ? "" : candidate.getName().trim().toLowerCase(Locale.ROOT);
            if (nameKey.equals(candidateName)) {
                throw new ApiException(ApiErrorCode.CONFLICT, "供应商已存在，不允许重复新增: " + name);
            }
        }
    }

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
     * 价格指标键（每百万 token 美元 + 单任务成本）。
     *
     * <p>上游 {@code /models} 的 pricing 由
     * {@code HttpChatClientIndicator#parseModelMetrics} 归一成这些键；
     * 采集时同时写指标库与模型行 metrics，模型卡片才会显示价格与「免费」。</p>
     */
    private static final String[] PRICE_METRIC_KEYS = {
            "price_input_per_musd", "price_output_per_musd",
            "price_cache_hit_per_musd", "price_cache_write_per_musd",
            "cost_per_task",
    };

    /**
     * 需要合并进模型行 指标 的快照指标键。
     */
    private static final String[] METRIC_KEYS = {
            "price_input_per_musd", "price_output_per_musd",
            "price_cache_hit_per_musd", "price_cache_write_per_musd",
            "intelligence_index", "output_speed_tps", "latency_first_token_seconds",
            "probe_latency_ms", "icon_url", "reasoning_model", "web_search",
            "image_input", "function_calling", "context_window_tokens",
            "hle_score", "omniscience_index",
            "gpqa_score",
            "aime25_score",
            "livecodebench_score",
            "scicode_score",
            "ifbench_score",
            "critpt_score",
            "mmmu_pro_score",
            "tau2_score",
            "tau_banking_score",
            "lcr_score",
            "terminalbench_hard_score",
            "terminal_bench21_score",
            "terminal_bench40_score",
            "automation_bench_score",
            "gdpval_normalized_score",
            "briefcase_score",
            "harvey_lab_score",
            "apex_agents_score",
            "itbench_sre_score",
            "omniscience_accuracy",
            "omniscience_non_hallucination",
            "elo",
            "cost_per_task",
            "json_mode",
            "openai_compatible",
            "intelligence_index_estimated"
    };

    // ==================== 提供者 ====================

    /**
     * 获取 提供者 凭证明文（仅限本地可信客户端调用）。
     *
     * @param providerId 提供者 标识
     * @return 凭证映射，包含密钥明文
     */
    @Permission("admin")
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
     * <p>ONNX 供应商与其余供应商同构（同样是 {@code sys_ai_provider} 表里的一行），
     * 差别只有一条：仅管理员可见，非管理员的返回列表里不出现该条目。</p>
     *
     * @return 提供者响应列表
     */
    @GetMapping("/providers")
    public ApiResult<List<Map<String, Object>>> listProviders() {
        boolean onnxVisible = com.chua.starter.ai.support.util.AiUserContext.isAdmin();
        List<SysAiProvider> providers = providerMapper.selectList(
                new LambdaQueryWrapper<SysAiProvider>()
                        .orderByAsc(SysAiProvider::getSortOrder)
                        .orderByAsc(SysAiProvider::getCreatedAt));

        List<Map<String, Object>> items = new ArrayList<>(8); // 提供者数量不可预估
        for (SysAiProvider p : providers) {
            if (isOnnxRow(p) && !onnxVisible) {
                continue;
            }
            // 渠道管理页为每个已注册渠道镀铬的 OAuth provider 行（auth_scheme='oauth'，无模型 / 无凭证），
            // 模型管理（供应商列表 / 模型面板）与渠道解耦，不暴露这些行。
            // 免费渠道行（platform='free-channel'/'free'）由渠道管理提供模型供应，保留在列表。
            if (isOAuthChannelRow(p) && !isFreeChannelRow(p)) {
                continue;
            }
            List<SysAiProviderModel> models = modelMapper.selectList(
                    new LambdaQueryWrapper<SysAiProviderModel>()
                            .eq(SysAiProviderModel::getProviderId, p.getProviderId()));
            // 连接按账号隔离：provider 行是全局的，挂在下面的账号凭证不是。
            // 注：toProviderResponse 目前并未把 connections 回显到响应里（页面上的
            // 账号列表走 /api/channels），这里的过滤是防御性的 —— 一旦有人把连接
            // 加进响应，隔离条件已经在这里，不会裸奔。
            LambdaQueryWrapper<SysAiProviderConnection> connectionWrapper =
                    new LambdaQueryWrapper<SysAiProviderConnection>()
                            .eq(SysAiProviderConnection::getProviderId, p.getProviderId());
            applyConnectionOwnerFilter(connectionWrapper);
            List<SysAiProviderConnection> connections = connectionMapper.selectList(connectionWrapper);
            Map<String, Object> item = toProviderResponse(p, models, connections);
            if (routerGroupService.isRouterGroup(p)) {
                // 路由组没有自己的模型行：成员全在模型库里。但会话的模型面板读的就是
                // /api/providers 里每行的 models，这里补一条虚拟模型，让「选中这个组」
                // 在会话里可选；它解析到的客户端是 RouterGroupChatClient（按组内策略选成员）。
                item.put("models", List.of(routerGroupVirtualModel(p)));
            }
            items.add(item);
        }

        // 注入已注册的智能体模型作为虚拟提供者
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
     * <p>ONNX 供应商仅管理员可见：非管理员按「不存在」处理，避免通过详情接口探到本地推理配置。</p>
     *
     * @param providerId 提供者 标识
     * @return 提供者详情
     */
    @GetMapping("/providers/{provider_id}")
    public ApiResult<Map<String, Object>> getProvider(@PathVariable("provider_id") String providerId) {
        SysAiProvider entity = requireVisibleProvider(providerId);
        List<SysAiProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId));
        // 连接按账号隔离：详情页同样只能看到自己的账号（管理员另见 NULL 系统池）
        LambdaQueryWrapper<SysAiProviderConnection> connectionWrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId);
        applyConnectionOwnerFilter(connectionWrapper);
        List<SysAiProviderConnection> connections = connectionMapper.selectList(connectionWrapper);
        return ApiResult.ok(toProviderResponse(entity, models, connections));
    }

    /**
     * 创建 提供者（原子包含 initial_模型 与可选 connections）。
     *
     * @param body 创建请求
     * @return 创建后的 提供者响应
     */
    @Permission("admin")
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
        String platform = requireStr(body, "platform");
        String name = requireStr(body, "name");
        // 路由组供应商：模型来自「已添加的模型库」，没有上游地址可填，
        // 因此不校验同平台重名（同一平台可以建多个不同策略的路由组）。
        if (!ProviderRouteTargets.isRouterGroup(platform)) {
            // ONNX 为内置单行，不允许再新增一条同平台的供应商；其余供应商按「平台+名称」判重
            requireNotDuplicateProvider(platform, name, null);
        }
        long now = System.currentTimeMillis();

        // 创建提供者实体
        SysAiProvider entity = new SysAiProvider();
        entity.setProviderId(providerId);
        entity.setPlatform(platform);
        entity.setName(name);
        // 路由组没有真实上游：地址与鉴权方式留空占位，不做必填校验
        // （成员各自带着自己的地址与凭证，由 ModelClientResolver 按成员构造）。
        if (ProviderRouteTargets.isRouterGroup(platform)) {
            // 库列 NOT NULL：空串占位（不做「必填」校验，也不校验地址格式）
            String routerBaseUrl = str(body, "base_url");
            entity.setBaseUrl(routerBaseUrl == null ? "" : routerBaseUrl);
            entity.setAuthScheme(str(body, "auth_scheme") != null && !str(body, "auth_scheme").isBlank()
                ? str(body, "auth_scheme") : "none");
        } else {
            entity.setBaseUrl(requireStr(body, "base_url"));
            entity.setAuthScheme(requireStr(body, "auth_scheme"));
        }
        entity.setCredentials(uncheckedCast(body.get("credentials")));
        entity.setEnabled(bool(body, "enabled", true));
        entity.setBedrockConfig(uncheckedCast(body.get("bedrock_config")));
        entity.setProxy(str(body, "proxy"));
        entity.setExtra(uncheckedCast(body.get("extra")));
        entity.setSortOrder(intv(body, "sort_order", providerMapper.selectCount(null).intValue()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        providerMapper.insert(entity);

        // 初始模型（可选，用户可以后续手动添加）
        Map<String, Object> initialModel = uncheckedCast(body.get("initial_model"));
        if (initialModel != null) {
            SysAiProviderModel modelEntity = toModelEntity(providerId, initialModel, now);
            modelMapper.insert(modelEntity);
        }

        // 初始连接按连接角色保存
        List<Map<String, Object>> connections = listOfMap(body, "connections");
        for (Map<String, Object> conn : connections) {
            SysAiProviderConnection connEntity = toConnectionEntity(providerId, conn, now);
            connectionMapper.insert(connEntity);
        }

        // 查询完整数据返回
        List<SysAiProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId));
        LambdaQueryWrapper<SysAiProviderConnection> connWrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId);
        applyConnectionOwnerFilter(connWrapper);
        List<SysAiProviderConnection> conns = connectionMapper.selectList(connWrapper);
        return ApiResult.ok(toProviderResponse(entity, models, conns));
    }

    /**
     * 更新 提供者（凭证缺省时保留既有凭证）。
     *
     * @param providerId 提供者 标识
     * @param body       更新请求
     * @return 更新后的 提供者响应
     */
    @Permission("admin")
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
        // 管理员可改接入协议：协议落在每个模型 capability 上，需同步到该供应商全部模型
        if (body.containsKey("protocol")) {
            String protocol = str(body, "protocol");
            List<SysAiProviderModel> protocolModels = modelMapper.selectList(
                    new LambdaQueryWrapper<SysAiProviderModel>()
                            .eq(SysAiProviderModel::getProviderId, providerId));
            for (SysAiProviderModel pm : protocolModels) {
                List<Map<String, Object>> caps = pm.getCapabilities();
                if (caps != null) {
                    for (Map<String, Object> cap : caps) {
                        cap.put("protocol", protocol);
                    }
                    modelMapper.updateByKey(pm);
                }
            }
        }
        if (body.containsKey("sort_order")) {
            entity.setSortOrder(intv(body, "sort_order", entity.getSortOrder()));
        }
        // 编辑同样要守唯一性：否则可以把 A 改名为 B 的名字，绕过新增/克隆的判重造出重复项。
        // 排除自身（excludeProviderId = 当前被编辑的供应商），只拦与「别人」撞名的情况。
        if (body.containsKey("name") || body.containsKey("platform")) {
            requireNotDuplicateProvider(entity.getPlatform(), entity.getName(), providerId);
        }
        entity.setUpdatedAt(System.currentTimeMillis());
        providerMapper.updateById(entity);

        // ONNX 供应商的推理设备取自 provider.extra.device，更新后立即桥接到运行时，
        // 覆盖原模型参数接口的 device 读取，避免需重启才生效。
        refreshOnnxDevice(entity);

        List<SysAiProviderModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId));
        LambdaQueryWrapper<SysAiProviderConnection> connWrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId);
        applyConnectionOwnerFilter(connWrapper);
        List<SysAiProviderConnection> conns = connectionMapper.selectList(connWrapper);
        return ApiResult.ok(toProviderResponse(entity, models, conns));
    }

    /**
     * 删除 提供者（级联删除模型和连接）。
     *
     * <p>单条入口，复用 {@link #doBatchDeleteProviders(List)} 的整批拦截与清理逻辑。</p>
     *
     * @param providerId 提供者 标识
     * @return 成功
     */
    @Permission("admin")
    @DeleteMapping("/providers/{provider_id}")
    @Transactional
    public ApiResult<Void> deleteProvider(@PathVariable("provider_id") String providerId) {
        doBatchDeleteProviders(List.of(providerId));
        return ApiResult.ok();
    }

    /**
     * 解析批量删除的供应商 id 列表，并在删除前拦截非法请求。
     *
     * <p>整批定位先于任何写操作：空 id、不存在的 id、批量里混入 ONNX 供应商，都会
     * 直接拦截整批，不允许部分删除。</p>
     *
     * @param body 请求体，须含 {@code ids} 数组（provider_id 字符串）
     * @return 待删除的供应商 id 列表
     */
    private List<String> parseBatchProviderIds(Map<String, ?> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = body == null ? null : (List<String>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ids 不能为空");
        }
        // 先整批定位，再逐批删除：任何定位失败都直接拦截整批，不出现部分删除
        List<String> targets = new ArrayList<>(ids.size());
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "id 不能为空");
            }
            SysAiProvider target = requireProvider(id);
            if (isOnnxRow(target)) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "ONNX 供应商为内置单行，不允许删除");
            }
            targets.add(id);
        }
        return targets;
    }

    /**
     * 批量删除 提供者 行（级联删除其模型与连接）。
     *
     * <p>供应商行与模型行不同，id 就是 {@code provider_id}（不带 {@code :} 模型名的复合标识）。
     * 整批鉴权与清理逻辑与 {@link #deleteProvider} 公用。</p>
     *
     * @param body 请求体，须含 {@code ids} 数组（provider_id 字符串）
     * @return 包含 success 与 deletedCount 的结果
     */
    @Permission("admin")
    @DeleteMapping("/providers/batch")
    @Transactional
    public ApiResult<Map<String, Object>> batchDeleteProviders(@RequestBody Map<String, ?> body) {
        List<String> ids = parseBatchProviderIds(body);
        int deleted = doBatchDeleteProviders(ids);
        Map<String, Object> result = new HashMap<>(2);
        result.put("success", true);
        result.put("deletedCount", deleted);
        return ApiResult.ok(result);
    }

    /**
     * 执行供应商行（及级联）的删除。
     *
     * @param providerId 已定位的供应商 id
     */
    private void deleteProviderRows(String providerId) {
        // 先取出该提供商的模型标识：全局设置里的「默认模型」按 "providerId:model" 存值，
        // 必须在删除前收集，否则模型行删掉后就无法判断哪些配置项指向了本提供商
        List<String> ownedModels = modelMapper.selectList(new LambdaQueryWrapper<SysAiProviderModel>()
                        .select(SysAiProviderModel::getModel)
                        .eq(SysAiProviderModel::getProviderId, providerId))
                .stream()
                .map(SysAiProviderModel::getModel)
                .filter(s -> s != null && !s.trim().isEmpty())
                .map(String::trim)
                .toList();

        providerMapper.deleteById(providerId);
        modelMapper.delete(new LambdaQueryWrapper<SysAiProviderModel>()
                .eq(SysAiProviderModel::getProviderId, providerId));
        // 供应商行是全局的，删除它等于全局下线这家供应商，其下连接（无论归属谁）
        // 都已不可用 —— 一并清理，避免留下 providerId 已不存在的孤儿凭证行
        // （日后按同一 id 重建供应商会把这些账号“复活”，那才是真正的越权）。
        // 账号隔离体现在「读」：各用户只能看到自己的连接，删除别人的账号
        // 只能由删掉这家供应商这件事本身连带发生，且是管理员动作。
        connectionMapper.delete(new LambdaQueryWrapper<SysAiProviderConnection>()
                .eq(SysAiProviderConnection::getProviderId, providerId));
        clearGlobalSettingsReferencing(providerId, ownedModels);
        log.info("删除供应商 {}，级联删除其模型与全部连接", providerId);
    }

    /**
     * 执行已定位供应商行的整批删除。
     *
     * @param ids 已定位的供应商 id 列表
     * @return 删除的供应商数
     */
    private int doBatchDeleteProviders(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (String providerId : ids) {
            SysAiProvider entity = providerMapper.selectById(providerId);
            if (entity == null) {
                continue;
            }
            deleteProviderRows(providerId);
            deleted++;
        }
        return deleted;
    }

    /**
     * 清理全局设置中指向指定提供商的模型取值。
     *
     * <p>「默认模型」类字段的取值形如 {@code providerId:model}（前端模型选择器写入）。
     * 提供商被删除后若不同步清理，配置项会残留悬空引用：界面仍显示已不存在的模型，
     * 运行期解析失败且无提示。这里按前缀精确匹配该提供商的两个来源：</p>
     * <ul>
     *   <li>{@code providerId:model} —— 前端写入的规范形式</li>
     *   <li>裸 {@code model} —— 历史数据 / ONNX 等虚拟提供商曾直接写模型名</li>
     * </ul>
     *
     * @param providerId  被删除的提供商标识
     * @param ownedModels 该提供商下的模型标识
     * @return 清理条数
     */
    private int clearGlobalSettingsReferencing(String providerId, List<String> ownedModels) {
        if (providerId == null || providerId.trim().isEmpty()) {
            return 0;
        }
        List<SysAiGlobalSetting> candidates = globalSettingMapper.selectList(
                new LambdaQueryWrapper<SysAiGlobalSetting>()
                        .likeRight(SysAiGlobalSetting::getValue, providerId + ":"));
        int cleared = 0;
        String prefix = providerId + ":";
        for (SysAiGlobalSetting setting : candidates) {
            if (setting.getValue() != null && setting.getValue().startsWith(prefix)) {
                cleared += globalSettingMapper.update(null, new LambdaUpdateWrapper<SysAiGlobalSetting>()
                        .eq(SysAiGlobalSetting::getId, setting.getId())
                        .set(SysAiGlobalSetting::getValue, ""));
            }
        }
        // 裸模型名：仅当该模型名未被其它提供商占用时才可安全清空，避免误伤同名模型
        for (String model : ownedModels) {
            if (model == null || model.trim().isEmpty() || model.contains(":")) {
                continue;
            }
            long stillUsed = modelMapper.selectCount(new LambdaQueryWrapper<SysAiProviderModel>()
                    .eq(SysAiProviderModel::getModel, model));
            if (stillUsed > 0) {
                continue;
            }
            cleared += globalSettingMapper.update(null, new LambdaUpdateWrapper<SysAiGlobalSetting>()
                    .eq(SysAiGlobalSetting::getValue, model)
                    .set(SysAiGlobalSetting::getValue, ""));
        }
        return cleared;
    }

    /**
     * 克隆 提供者（连同其模型能力与连接一并复制）。
     *
     * @param providerId 提供者 标识
     * @param body       可选 { 名称 }
     * @return 克隆后的 提供者响应
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/clone")
    @Transactional
    public ApiResult<Map<String, Object>> cloneProvider(@PathVariable("provider_id") String providerId,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        SysAiProvider source = requireProvider(providerId);
        String cloneName = body != null && body.get("name") != null
                ? str(body, "name") : source.getName() + " 副本";
        // 克隆同样受唯一性约束：ONNX 不可克隆出第二份，其余供应商不能与既有条目重名
        requireNotDuplicateProvider(source.getPlatform(), cloneName, source.getProviderId());
        long now = System.currentTimeMillis();
        String newId = String.valueOf(System.currentTimeMillis() + (int)(Math.random() * 1000));

        // 克隆提供者
        SysAiProvider clone = new SysAiProvider();
        clone.setProviderId(newId);
        clone.setPlatform(source.getPlatform());
        clone.setName(cloneName);
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

        // 深拷贝连接（只拷贝当前账号可见的那些，见 applyConnectionOwnerFilter）
        LambdaQueryWrapper<SysAiProviderConnection> sourceWrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId);
        applyConnectionOwnerFilter(sourceWrapper);
        List<SysAiProviderConnection> sourceConns = connectionMapper.selectList(sourceWrapper);
        for (SysAiProviderConnection srcConn : sourceConns) {
            SysAiProviderConnection c = new SysAiProviderConnection();
            c.setConnectionId(String.valueOf(System.currentTimeMillis() + (int)(Math.random() * 1000)));
            c.setProviderId(newId);
            c.setRole(srcConn.getRole());
            c.setLabel(srcConn.getLabel());
            // 归属沿用源行（不改成当前操作者）：克隆是「复制一份同配置」，
            // 若原行是 user_id IS NULL 的系统池，副本仍应留在系统池里 ——
            // 免费渠道（_free 作用域）只认 NULL 行，改成当前用户会让副本失效。
            c.setUserId(srcConn.getUserId());
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
        LambdaQueryWrapper<SysAiProviderConnection> connWrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, newId);
        applyConnectionOwnerFilter(connWrapper);
        List<SysAiProviderConnection> conns = connectionMapper.selectList(connWrapper);
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
     * @return 上游模型列表响应
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/models")
    public ApiResult<Map<String, Object>> fetchProviderModels(@PathVariable("provider_id") String providerId,
                                                              @RequestBody(required = false) Map<String, Object> body) {
        SysAiProvider entity = requireProvider(providerId);
        boolean tryFix = body != null && Boolean.TRUE.equals(body.get("try_fix"));
        return ApiResult.ok(fetchModelsFromUpstream(entity.getPlatform(), entity.getBaseUrl(),
                entity.getCredentials(), UpstreamAuth.of(entity.getAuthScheme(), entity.getExtra()),
                entity.getProxy(), tryFix, entity.getExtra(), entity.getProviderId()));
    }

    /**
     * 一键拉取并**批量录入** 提供者 的全部远端模型。
     *
     * <p>「添加模型」要的是一次到位：用 提供者 已保存的地址与凭证真实调用上游
     * {@code GET {base_url}/models}，把返回的模型目录整批写进
     * {@code sys_ai_provider_model}，用户不必再在弹窗里逐条点「选择」。</p>
     *
     * <p><b>幂等</b>：模型名已存在的直接跳过，**不覆盖**用户改过的别名/参数，
     * 所以重复点击只会补录新出现的模型。缺模型名的上游条目计入 skipped 而非报错
     * ——一个坏条目不能让整批失败。</p>
     *
     * <p><b>元数据同步</b>：上游 {@code /models} 真实声明的上下文窗口、输出上限、
     * family、能力开关、模态会随录入一起落库；已存在的模型行也会补空列（不改别名、
     * 启用状态与排序），因此重复点击同时起到「刷新元数据」的作用，
     * 计数见响应里的 {@code enriched}。</p>
     *
     * @param providerId 提供者 标识
     * @param body       可选 { try_fix, models }（try_fix: 404 时补全 /v1 重试；models: 只录入勾选的模型名，空 = 全部）
     * @return { total, imported, skipped, enriched, models }
     */
    @SuppressWarnings("unchecked")
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/models/import-all")
    @Transactional
    public ApiResult<Map<String, Object>> importAllProviderModels(@PathVariable("provider_id") String providerId,
                                                                  @RequestBody(required = false) Map<String, Object> body) {
        SysAiProvider entity = requireProvider(providerId);
        boolean tryFix = body != null && Boolean.TRUE.equals(body.get("try_fix"));
        Map<String, Object> upstream = fetchModelsFromUpstream(entity.getPlatform(), entity.getBaseUrl(),
                entity.getCredentials(), UpstreamAuth.of(entity.getAuthScheme(), entity.getExtra()),
                entity.getProxy(), tryFix, entity.getExtra(), entity.getProviderId());
        Object rawModels = upstream.get("models");
        List<Map<String, Object>> upstreamModels = rawModels instanceof List<?> list ? (List<Map<String, Object>>) list
                : new ArrayList<>(0);
        // 只录入用户勾选的模型：body.models 为空表示「全部录入」（保持原语义）。
        // 放在落库前过滤，仍复用同一套元数据回填 / 去重逻辑。
        if (body != null && body.get("models") instanceof List<?> only) {
            Set<String> wanted = new HashSet<>(16);
            for (Object item : only) {
                if (item != null) {
                    wanted.add(String.valueOf(item));
                }
            }
            if (!wanted.isEmpty()) {
                List<Map<String, Object>> filtered = new ArrayList<>(wanted.size());
                for (Map<String, Object> m : upstreamModels) {
                    if (wanted.contains(batchModelName(m))) {
                        filtered.add(m);
                    }
                }
                upstreamModels = filtered;
            }
        }

        // 已存在的模型名一次取齐：既是幂等判据，也省掉每条一次 select；
        // 顺带算出最大排序号，新模型排在既有行之后
        Set<String> seen = new HashSet<>(16);
        Map<String, SysAiProviderModel> existing = new HashMap<>(16);
        int maxSort = -1;
        for (SysAiProviderModel row : modelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId))) {
            if (row.getModel() != null) {
                existing.put(row.getModel(), row);
            }
            if (row.getSortOrder() != null && row.getSortOrder() > maxSort) {
                maxSort = row.getSortOrder();
            }
        }
        // 按上游下标直接落库会和既有行撞出并列序号（并列时显示顺序不稳定），
        // 统一平移到既有行之后
        int sortBase = maxSort + 1;

        long now = System.currentTimeMillis();
        int imported = 0;
        int skipped = 0;
        int enriched = 0;
        List<Map<String, Object>> importedRows = new ArrayList<>(8);
        for (int i = 0; i < upstreamModels.size(); i++) {
            Map<String, Object> m = upstreamModels.get(i);
            String name = batchModelName(m);
            // 上游缺模型名的坏条目计入 skipped
            if (name == null) {
                skipped++;
                continue;
            }
            SysAiProviderModel exist = existing.get(name);
            if (exist != null) {
                // 已存在：不覆盖用户配置，只补上游真实元数据（仅空列 + metrics 合并）
                if (mergeUpstreamMetadata(entity, exist, m, now)) {
                    enriched++;
                }
                skipped++;
                continue;
            }
            // Set#add：上游内部重名也跳过，避免同批插入两条
            if (!seen.add(name)) {
                skipped++;
                continue;
            }
            SysAiProviderModel row = toModelEntity(providerId, batchModelInput(m, name, sortBase + i), now);
            enrichFromMetrics(entity, row);
            modelMapper.insert(row);
            imported++;
            importedRows.add(toProviderModelResponse(entity, row));
        }

        Map<String, Object> out = new LinkedHashMap<>(8);
        out.put("total", upstreamModels.size());
        out.put("imported", imported);
        out.put("skipped", skipped);
        out.put("enriched", enriched);
        out.put("models", importedRows);
        return ApiResult.ok(out);
    }

    /**
     * 用上游 {@code /models} 条目回填已存在模型行的元数据。
     *
     * <p>只走「空列回填」语义（{@link SysAiModelMetricsService#enrichRowFromMetrics}）：
     * 别名、启用状态、排序、手填的上下文窗口 / 输出上限与能力开关一律保留；
     * 上游未声明的键也不会写成空。无实际变化时不产生一次「全表写」。</p>
     *
     * @param provider 服务商行（用于记录来源地址）
     * @param row      已存在的模型行（就地修改并在有变化时落库）
     * @param upstream 上游目录条目
     * @param now      本次采集时间
     * @return 是否写入了新元数据
     */
    @SuppressWarnings("unchecked")
    private boolean mergeUpstreamMetadata(SysAiProvider provider, SysAiProviderModel row,
                                          Map<String, Object> upstream, long now) {
        Object rawMetrics = upstream.get("metrics");
        if (!(rawMetrics instanceof Map<?, ?>)) {
            return false;
        }
        Map<String, Object> metrics = (Map<String, Object>) rawMetrics;
        if (metrics.isEmpty()) {
            return false;
        }
        Map<String, Object> beforeMetrics = row.getMetrics() == null
                ? new LinkedHashMap<>(8)
                : new LinkedHashMap<>(row.getMetrics());
        beforeMetrics.remove("metrics_captured_at");
        Map<String, Object> beforeColumns = entityColumns(row);
        Map<String, Object> current = row.getMetrics() == null
                ? new LinkedHashMap<>(24)
                : new LinkedHashMap<>(row.getMetrics());
        sysAiModelMetricsService.enrichRowFromMetrics(row, current, metrics,
                "upstream:/models", provider.getBaseUrl(), now);
        // 上游 /models 自带 pricing 时也要落到行 metrics：模型卡片的「价格 / 免费」
        // 读的是行 metrics，只写指标库的话卡片上仍然看不到价格。
        // 只补行里还没有的价，已有价格（官方指标同步写入的）优先。
        for (String priceKey : PRICE_METRIC_KEYS) {
            Object price = metrics.get(priceKey);
            if (price != null && current.get(priceKey) == null) {
                current.put(priceKey, price);
            }
        }
        Map<String, Object> afterMetrics = new LinkedHashMap<>(current);
        afterMetrics.remove("metrics_captured_at");
        // 采集时间每次都会变，不参与「是否有变化」判断，否则每次点击都全表写
        if (afterMetrics.equals(beforeMetrics) && entityColumns(row).equals(beforeColumns)) {
            return false;
        }
        row.setUpdatedAt(now);
        modelMapper.update(row, new LambdaUpdateWrapper<SysAiProviderModel>()
                .eq(SysAiProviderModel::getProviderId, row.getProviderId())
                .eq(SysAiProviderModel::getModel, row.getModel()));
        return true;
    }

    /**
     * 快照 {@link SysAiModelMetricsService#enrichRowFromMetrics} 会回填的实体列。
     *
     * <p>用于判断一次合并是否真的带来新值，避免「元数据没变也写库」。</p>
     *
     * @param row 模型行
     * @return 列名 → 值
     */
    private static Map<String, Object> entityColumns(SysAiProviderModel row) {
        Map<String, Object> columns = new LinkedHashMap<>(16);
        columns.put("context_window", row.getContextWindow());
        columns.put("max_tokens", row.getMaxTokens());
        columns.put("family", row.getFamily());
        columns.put("knowledge", row.getKnowledge());
        columns.put("release_date", row.getReleaseDate());
        columns.put("last_updated", row.getLastUpdated());
        columns.put("open_weights", row.getOpenWeights());
        columns.put("attachment", row.getAttachment());
        columns.put("reasoning", row.getReasoning());
        columns.put("tool_call", row.getToolCall());
        columns.put("temperature_supported", row.getTemperatureSupported());
        columns.put("modalities", row.getModalities());
        return columns;
    }

    /**
     * 按模型名从服务商指标端点拉取指标快照，供模型新增/编辑页「自动获取」按钮回填表单。
     *
     * <p>返回可直接回填的字段集（Models.dev 等 indicator 的标准键，口径与价格同步一致）：</p>
     * <ul>
     *   <li>实体列键：family / knowledge / release_date / last_updated / modalities /
     *       open_weights / attachment / reasoning / tool_call / temperature_supported</li>
     *   <li>思考档位：reasoning_efforts（Models.dev reasoning_options 解析，如
     *       claude-opus-5 → [low,medium,high,xhigh,max]）</li>
     *   <li>常规：context_window_tokens / max_output_tokens / 价格</li>
     * </ul>
     *
     * <p>未收录该模型时 {@code found=false}，前端提示「该模型未被指标源收录」并保持表单不动。</p>
     *
     * @param providerId 提供者标识
     * @param model      模型名（query 参数）
     * @return 指标快照
     */
    @Permission("admin")
    @GetMapping("/providers/{provider_id}/models/metrics")
    public ApiResult<Map<String, Object>> recommendModelMetrics(
            @PathVariable("provider_id") String providerId,
            @RequestParam(value = "model", required = false) String model) {
        SysAiProvider entity = requireProvider(providerId);
        Map<String, Object> out = new LinkedHashMap<>(8);
        if (model == null || model.isBlank()) {
            out.put("found", false);
            out.put("message", "模型名不能为空");
            return ApiResult.ok(out);
        }
        Map<String, Object> hit = findIndicatorMetrics(entity, model.trim());
        if (hit == null || hit.isEmpty()) {
            out.put("found", false);
            out.put("message", "该模型未被指标源收录，可手动填写配置");
            return ApiResult.ok(out);
        }
        out.put("found", true);
        out.put("model", model.trim());
        // 只回传前端表单能消费的字段，避免整包指标泄漏
        for (String key : new String[]{
                "family", "knowledge", "release_date", "last_updated",
                "modalities", "open_weights", "attachment", "reasoning",
                "tool_call", "temperature_supported",
        }) {
            String indicatorKey = switch (key) {
                case "knowledge" -> "knowledge_cutoff";
                case "family" -> "model_family";
                case "tool_call" -> "function_calling";
                case "temperature_supported" -> "temperature";
                default -> key;
            };
            Object value = hit.get(indicatorKey);
            if (value != null) {
                out.put(key, value);
            }
        }
        out.put("reasoning_efforts", hit.get("reasoning_efforts"));
        out.put("context_window_tokens", hit.get("context_window_tokens"));
        out.put("max_output_tokens", hit.get("max_output_tokens"));
        out.put("price_input_per_musd", hit.get("price_input_per_musd"));
        out.put("price_output_per_musd", hit.get("price_output_per_musd"));
        return ApiResult.ok(out);
    }

    /**
     * 从服务商官方指标源同步价格 / 能力指标。
     *
     * <p>来源解析统一委托给
     * {@link com.chua.common.support.ai.chat.indicator.ChatClientIndicators}：
     * 优先使用服务商 {@code extra.metrics_url} 配置的端点；否则按平台路由到
     * OpenRouter / Pollinations / Vercel AI Gateway 官方目录或 Models.dev 聚合
     * （见各厂商的 {@code ChatClientIndicator} 实现）。同步结果同时写入
     * {@code sys_ai_model_metrics}（计费取价）与该服务商的
     * {@code sys_ai_provider_model.metrics}（前端模型卡片立即刷新）。</p>
     *
     * @param providerId 提供者标识
     * @return 同步统计；无可用来源时 {@code supported=false}
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/models/sync-pricing")
    @Transactional
    public ApiResult<Map<String, Object>> syncPricing(@PathVariable("provider_id") String providerId) {
        SysAiProvider entity = requireProvider(providerId);
        ChatClientIndicator indicator = resolveIndicator(entity);
        if (indicator == null) {
            log.info("[SyncPricing] provider={} 无可用指标端点，跳过", providerId);
            Map<String, Object> empty = new LinkedHashMap<>(8);
            empty.put("supported", false);
            empty.put("updated", 0);
            empty.put("inserted", 0);
            empty.put("provider_models", 0);
            empty.put("source", null);
            empty.put("source_url", null);
            empty.put("pricing_url", ChatClientIndicators.officialPricingUrl(entity.getPlatform()));
            empty.put("models", 0);
            return ApiResult.ok(empty);
        }
        int modelsCount = indicator.modelMetrics() == null ? 0 : indicator.modelMetrics().size();
        SysAiModelMetricsService.SyncResult result =
                sysAiModelMetricsService.syncFromIndicator(indicator, providerId);
        Map<String, Object> out = new LinkedHashMap<>(12);
        out.put("supported", true);
        out.put("updated", result.getUpdated());
        out.put("inserted", result.getInserted());
        out.put("provider_models", result.getProviderModels());
        out.put("source", indicator.source());
        out.put("source_url", indicator.sourceUrl());
        out.put("pricing_url", ChatClientIndicators.officialPricingUrl(entity.getPlatform()));
        out.put("models", modelsCount);
        out.put("captured_at", indicator.capturedAt());
        return ApiResult.ok(out);
    }

    /**
     * 按服务商平台解析对应的 {@link ChatClientIndicator}。
     *
     * <p>使用统一的
     * {@link com.chua.common.support.ai.chat.indicator.ChatClientIndicators} 解析，
     * 与 {@code ChatClient.indicator()} 走同一套映射，避免前后端登记不一致。</p>
     *
     * @param entity 供应商行
     * @return indicator 实现或 空
     */
    private ChatClientIndicator resolveIndicator(SysAiProvider entity) {
        ChatClientSetting setting = buildSettingForIndicator(entity);
        ChatClientIndicator indicator = ChatClientIndicators.of(setting);
        if (indicator != null) {
            return indicator;
        }
        String metricsUrl = extraString(entity.getExtra(), "metrics_url");
        if (com.chua.common.support.utils.StringUtils.isNotBlank(metricsUrl)) {
            return new HttpChatClientIndicator(setting);
        }
        return null;
    }

    /**
     * 组装供 indicator 使用的最小 {@link ChatClientSetting}。
     * @param entity 参数
     * @return 返回值
    */
    private ChatClientSetting buildSettingForIndicator(SysAiProvider entity) {
        Map<String, String> extraHeaders = extractExtraHeaders(entity.getExtra());
        String platform = entity.getPlatform();
        if (platform == null || platform.isBlank()) {
            platform = entity.getProviderId();
        }
        return ChatClientSetting.builder()
                .provider(platform)
                .appKey(firstApiKey(entity))
                .baseUrl(entity.getBaseUrl())
                .extraHeaders(extraHeaders)
                .authScheme(entity.getAuthScheme())
                .authHeader(extraString(entity.getExtra(), "auth_header"))
                .authPrefix(extraString(entity.getExtra(), "auth_prefix"))
                .metricsUrl(extraString(entity.getExtra(), "metrics_url"))
                .proxy(entity.getProxy())
                .build();
    }

    /**
     * 取服务商自身凭证中的第一个 API Key（无则返回 {@code null}）。 <p>复用 {@link #extractApiKey(Map)} 的既有逻辑。</p>
     * @param entity 参数
     * @return 返回值
    */
    private String firstApiKey(SysAiProvider entity) {
        return extractApiKey(entity.getCredentials());
    }

    /**
     * 从 extra 中读取字符串值（extra 为 {@code null} 或键不存在时返回 {@code null}）。
     * @param Map<String 参数
     * @param extra 参数
     * @param key 字符串
     * @return 返回值
    */
    private static String extraString(Map<String, Object> extra, String key) {
        if (extra == null) {
            return null;
        }
        Object value = extra.get(key);
        return value == null ? null : value.toString();
    }

    /**
    /**
     * 从 extra 的 {@code custom_headers} 字段提取附加请求头 Map；无则返回 {@code null}。
     * @param Map<String 参数
     * @param extra 参数
     * @return 返回值
    */
    private static Map<String, String> extractExtraHeaders(Map<String, Object> extra) {
        if (extra == null) {
            return null;
        }
        Object raw = extra.get("custom_headers");
        if (raw instanceof Map<?, ?> m) {
            Map<String, String> result = new LinkedHashMap<>((Map<String, String>) m);
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    /**
     * 取上游模型条目的模型名（{@code name} 优先，缺 name 回退 {@code id}）。
     *
     * @param m 上游条目
     * @return 模型名；条目缺失或两个字段都为空返回 null
     */
    private String batchModelName(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        if (m.get("name") instanceof String s && !s.isBlank()) {
            return s;
        }
        if (m.get("id") instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    /**
     * 上游条目 → 「保存提供者模型」输入（{@link #toModelEntity} 认识的形态）。
     *
     * <p>上游 OpenAI 兼容接口只给 {@code {id,name,tasks,traits}}。这里把它映射成
     * {@code capabilities[].task / traits} 结构，前端的模型类型标签与能力图标才能与
     * 单条保存路径一致 —— 不落 capabilities 的话批量录入出来的卡片会全退化成
     * 「文本」类型，图像/视频模型在模型选择器里也会被分错组。</p>
     *
     * <p>上游目录携带的元数据（上下文窗口、输出上限、family、能力开关、模态）也原样
     * 带过去，{@link #toModelEntity} 直接落实体列；<b>上游没给的键不写入</b>，
     * 保持「未指定」，由指标库快照或用户手填补上。</p>
     *
     * @param m     上游条目
     * @param name  模型名（已判空）
     * @param sortOrder 排序号（既有行之后的序号，保证不与已有行并列）
     * @return 模型输入
     */
    private Map<String, Object> batchModelInput(Map<String, Object> m, String name, int sortOrder) {
        Map<String, Object> input = new LinkedHashMap<>(24);
        input.put("model", name);
        input.put("enabled", true);
        input.put("sort_order", sortOrder);
        if (m.get("description") instanceof String s && !s.isBlank()) {
            input.put("description", s);
        }
        if (m.get("metrics") instanceof Map<?, ?>) {
            input.put("metrics", m.get("metrics"));
        }
        // 上游目录直接给出的实体列元数据：与前端「保存模型」同一套字段名
        for (String key : new String[]{"context_window", "max_tokens", "family", "knowledge",
                "release_date", "last_updated", "modalities", "open_weights", "attachment",
                "reasoning", "tool_call", "temperature_supported"}) {
            Object value = m.get(key);
            if (value != null) {
                input.put(key, value);
            }
        }

        List<String> traits = new ArrayList<>(4);
        if (m.get("traits") instanceof List<?> list) {
            for (Object t : list) {
                if (t instanceof String s && !s.isBlank()) {
                    traits.add(s);
                }
            }
        }
        List<Map<String, Object>> caps = new ArrayList<>(2);
        if (m.get("tasks") instanceof List<?> tasks) {
            for (Object t : tasks) {
                if (t instanceof String s && !s.isBlank()) {
                    Map<String, Object> cap = new LinkedHashMap<>(4);
                    cap.put("task", s);
                    cap.put("traits", traits);
                    // 上游条目如果有细分类型（capabilities[].subtype），批量录入时原样保留
                    String subType = str(m, "subtype");
                    if (subType == null && m.get("capabilities") instanceof List<?> rawCaps) {
                        for (Object rc : rawCaps) {
                            if (rc instanceof Map<?, ?> rm && rm.get("subtype") instanceof String sub && !sub.isBlank()) {
                                subType = sub;
                                break;
                            }
                        }
                    }
                    if (subType != null) {
                        cap.put("subtype", subType);
                    }
                    caps.add(cap);
                }
            }
        }
        if (!caps.isEmpty()) {
            input.put("capabilities", caps);
        }
        return input;
    }

    /**
     * 从提供商远程获取模型列表（GET接口，兼容前端调用）。
     *
     * <p>前端guada版本调用的是 GET /providers/{id}/remote_models，
     * 这里提供兼容的GET接口，内部调用同样的逻辑，
     * 并将返回格式转换成前端期望的 items[].modelName 格式。</p>
     *
     * @param providerId 提供者 标识
     * @return 上游模型列表响应
     */
    @Permission("admin")
    @GetMapping("/providers/{provider_id}/remote_models")
    public ApiResult<?> fetchRemoteModels(@PathVariable("provider_id") String providerId) {
        try {
            // ONNX 是后端注入的虚拟提供商，不落在 provider 表里，
            // 直接走本地模型门面，避免 requireProvider() 报「provider 不存在」
            if (isVirtualLocalProvider(providerId)) {
                Map<String, Object> local = fetchLocalModelsFromFacade();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> models = (List<Map<String, Object>>) local.get("models");
                List<Map<String, Object>> items = new ArrayList<>();
                if (models != null) {
                    for (Map<String, Object> model : models) {
                        Map<String, Object> item = new LinkedHashMap<>(model);
                        item.put("modelName", model.get("name") != null ? model.get("name") : model.get("id"));
                        items.add(item);
                    }
                }
                // 与云端分支保持一致：同时给出 models 与 items[].modelName
                local.put("items", items);
                return ApiResult.ok(local);
            }
            SysAiProvider entity = requireProvider(providerId);
            Map<String, Object> upstreamResult = fetchModelsFromUpstream(entity.getPlatform(), entity.getBaseUrl(),
                    entity.getCredentials(), UpstreamAuth.of(entity.getAuthScheme(), entity.getExtra()),
                    entity.getProxy(), false, entity.getExtra(), entity.getProviderId());

            // 转换格式：data.models[].name → data.items[].modelName
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> models = (List<Map<String, Object>>) upstreamResult.get("models");
            List<Map<String, Object>> items = new ArrayList<>();
            if (models != null) {
                for (Map<String, Object> m : models) {
                    Map<String, Object> item = new LinkedHashMap<>(m);
                    item.put("modelName", m.get("name") != null ? m.get("name") : m.get("id"));
                    // 价格 / 能力已随 metrics 解析出来：这里补挂到行顶层，
                    // 让「从提供商获取」列表能直接标注价格与「免费」，不必再钻 metrics
                    applyUpstreamMetrics(item, uncheckedCast(m.get("metrics")));
                    items.add(item);
                }
            }
            upstreamResult.put("items", items);
            return ApiResult.ok(upstreamResult);
        } catch (ApiException e) {
            // 上游供应商的 Key/网络错误属于业务错误：以 200 + 错误信封返回，
            // 避免被前端误判为「本应用会话过期」而强制登出。
            ApiErrorCode code = e.getErrorCode();
            // 认证/限流是用户可操作、需要明确告知的业务状态，保留错误信封；
            // 「连不上 / 地址没配 / 超时」这类拿不到目录的情况按用户要求直接回空列表，
            // 不再把 null 或网络异常堆到界面上。
            if (code == ApiErrorCode.UNAUTHORIZED || code == ApiErrorCode.FORBIDDEN
                    || code == ApiErrorCode.RATE_LIMITED) {
                return ApiResult.error(code.getCode(), e.getMessage());
            }
            Map<String, Object> empty = new LinkedHashMap<>(4);
            empty.put("models", new ArrayList<>());
            empty.put("items", new ArrayList<>());
            empty.put("hint", "未能从供应商获取模型列表，请检查 API 地址与网络连接");
            return ApiResult.ok(empty);
        }
    }

    /**
     * 匿名预拉取模型清单（创建前预览，不需要 提供者 行）。
     *
     * <p>直接使用请求体中的 base_url 与凭证真实调用上游 {@code GET {base_url}/models}
     * 接口，用于「添加模型」弹窗在下拉框中实时预览供应商可用模型。</p>
     *
     * @param body 获取模型anonymous请求
     * @return 上游模型列表响应
     */
    @Permission("admin")
    @PostMapping("/providers/fetch-models")
    public ApiResult<Map<String, Object>> fetchModelsAnonymous(@RequestBody Map<String, Object> body) {
        String platform = requireStr(body, "platform");
        // 在线神经网络推理模型的基础地址保存模型文件路径，创建前预览阶段允许为空
        String baseUrl = str(body, "base_url");
        if ((baseUrl == null || baseUrl.isBlank()) && !(platform != null && platform.toLowerCase().contains("onnx"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "base_url 不能为空");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> credentials = body.get("credentials") instanceof Map
                ? (Map<String, Object>) body.get("credentials") : null;
        String proxy = str(body, "proxy");
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyExtra = body.get("extra") instanceof Map
                ? (Map<String, Object>) body.get("extra") : null;
        return ApiResult.ok(fetchModelsFromUpstream(platform, baseUrl, credentials,
                UpstreamAuth.ofBody(body), proxy, false, bodyExtra, null));
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
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/test-keys")
    public ApiResult<Map<String, Object>> testProviderKeys(@PathVariable("provider_id") String providerId) {
        SysAiProvider entity = requireProvider(providerId);
        Map<String, Object> credentials = entity.getCredentials();
        List<String> keys = extractAllKeys(credentials);
        List<Map<String, Object>> results = new ArrayList<>(8); // 密钥数量不可预估
        int okCount = 0;
        for (int i = 0; i < keys.size(); i++) {
            Map<String, Object> r = testSingleKey(entity.getPlatform(), entity.getBaseUrl(),
                    keys.get(i), UpstreamAuth.of(entity.getAuthScheme(), entity.getExtra()), entity.getProxy(), i);
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
     *
     * <p>入参字段名是 {@code apiUrl / apiKey / protocol}（可选 {@code proxy}、
     * {@code auth_scheme / auth_header / auth_prefix}）。同时兼容旧前端的
     * {@code url}：历史版本发的是 url，而后端只读 apiUrl，会被判成「地址为空」
     * 直接返回失败 —— 测试请求根本不出网，看起来却像是「测了但没报错」。</p>
     *
     * @param body 请求体，不允许为 null
     * @return Api结果 对象
     */
    @Permission("admin")
    @PostMapping("/providers/test-connection")
    public ApiResult<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        String apiUrl = str(body, "apiUrl");
        if (apiUrl == null) {
            // 兼容旧字段名 url（前端迁移期两种都可能出现）
            apiUrl = str(body, "url");
        }
        if (apiUrl != null) {
            apiUrl = apiUrl.trim();
        }
        String apiKey = str(body, "apiKey");
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }
        String protocol = str(body, "protocol");
        if (protocol == null) {
            protocol = "openai";
        }
        String proxy = str(body, "proxy");
        Map<String, Object> out = new LinkedHashMap<>(4);
        if (apiUrl == null) {
            out.put("success", false);
            out.put("message", "API地址不能为空");
            return ApiResult.ok(out);
        }
        if (apiKey == null) {
            out.put("success", false);
            out.put("message", "API Key不能为空");
            return ApiResult.ok(out);
        }
        Map<String, Object> result = testSingleKey(protocol, apiUrl, apiKey, UpstreamAuth.ofBody(body), proxy, 0);
        out.put("success", result.get("ok"));
        out.put("message", result.getOrDefault("error", result.getOrDefault("message", "测试完成")));
        out.put("latency", result.get("latency_ms"));
        return ApiResult.ok(out);
    }

    /**
     * 测试指定提供者模型的连通性延迟。
     *
     * <p>模型管理卡片的模型来自 {@code /api/providers} 的
     * {@code provider_id + model} 复合主键，不是旧 {@code sys_ai_model}
     * 的数字主键，因此不能复用 {@code /v2/models/{id}/test-latency}。
     * 这里用第一个可用 Key 请求上游模型目录并测量耗时，供云端模型卡片展示。
     * 成功结果写入该模型 metrics 的 {@code probe_latency_ms} 和 {@code latency_ms}，
     * 列表接口会优先回读模型自身指标，刷新后仍能显示最近一次实测延迟。</p>
     *
     * @param providerId 提供者标识
     * @param modelName 模型名
     * @return 延迟测试结果
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/models/{model_name}/test-latency")
    public ApiResult<Map<String, Object>> testProviderModelLatency(
            @PathVariable("provider_id") String providerId,
            @PathVariable("model_name") String modelName) {
        SysAiProvider provider = requireProvider(providerId);
        SysAiProviderModel model = modelMapper.selectOne(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId)
                        .eq(SysAiProviderModel::getModel, modelName));
        if (model == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "模型不存在: " + modelName);
        }
        List<String> keys = extractAllKeys(provider.getCredentials());
        if (keys.isEmpty()) {
            Map<String, Object> noKey = new LinkedHashMap<>(3);
            noKey.put("success", false);
            noKey.put("message", "提供者未配置 API Key");
            noKey.put("latency", null);
            return ApiResult.ok(noKey);
        }
        Map<String, Object> result = testSingleKey(provider.getPlatform(), provider.getBaseUrl(),
                keys.get(0), UpstreamAuth.of(provider.getAuthScheme(), provider.getExtra()),
                provider.getProxy(), 0);
        Object latency = result.get("latency_ms");
        if (Boolean.TRUE.equals(result.get("ok"))) {
            Map<String, Object> metrics = model.getMetrics() != null
                    ? new LinkedHashMap<>(model.getMetrics())
                    : new LinkedHashMap<>(4);
            metrics.put("probe_latency_ms", latency);
            metrics.put("latency_ms", latency);
            model.setMetrics(metrics);
            model.setUpdatedAt(System.currentTimeMillis());
            modelMapper.updateByKey(model);
        }
        Map<String, Object> out = new LinkedHashMap<>(5);
        out.put("provider_id", providerId);
        out.put("model", modelName);
        out.put("success", result.get("ok"));
        out.put("message", result.getOrDefault("error", result.getOrDefault("message", "测试完成")));
        out.put("latency", latency);
        return ApiResult.ok(out);
    }

    /**
     * 用指定 Key 调上游 /模型，判定该 Key 是否有效（只读、不改库）。
    */
    private Map<String, Object> testSingleKey(String platform, String baseUrl, String apiKey,
                                              UpstreamAuth auth, String proxy, int index) {
        Map<String, Object> r = new LinkedHashMap<>(6);
        r.put("index", index);
        r.put("key_mask", maskKey(apiKey));
        long latencyStart = System.currentTimeMillis();
        try {
            if (baseUrl == null || baseUrl.isBlank()) {
                r.put("ok", false);
                r.put("error", "base_url 未配置");
                r.put("latency_ms", System.currentTimeMillis() - latencyStart);
                return r;
            }
            Map<String, Object> fakeCreds = new LinkedHashMap<>(2);
            fakeCreds.put("api_key", apiKey);
            // 协议判定必须与 fetchModelsFromUpstream 完全一致：平台为 google 时走
            // ?key= 查询参数，此前只认 "gemini"，导致 Google 的有效 Key 被按
            // Authorization: Bearer 发出去，上游回 401，界面误报「API Key 无效」。
            String platformLower = platform == null ? "" : platform.toLowerCase();
            boolean googleProtocol = platformLower.contains("gemini") || platformLower.contains("google");
            List<Map<String, Object>> models = requestUpstreamModels(baseUrl, apiKey, googleProtocol, proxy, auth);
            r.put("latency_ms", System.currentTimeMillis() - latencyStart);
            if (models == null) {
                // 404 / 网络失败：只说明「读不到模型列表」，不能断言 Key 无效
                r.put("ok", false);
                r.put("error", "无法从上游读取模型列表（地址或网络异常），未能校验 Key");
            } else {
                // 200 即使返回空列表也说明鉴权已通过，Key 有效；
                // 原实现把空列表判失败，导致有效 Key 被误报。
                r.put("ok", true);
                r.put("model_count", models.size());
                if (models.isEmpty()) {
                    r.put("message", "Key 校验通过，但上游未返回模型列表");
                }
            }
        } catch (Exception e) {
            r.put("latency_ms", System.currentTimeMillis() - latencyStart);
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
     * @return 提供者连接响应列表
     */
    @GetMapping("/providers/{provider_id}/connections")
    public ApiResult<List<Map<String, Object>>> listConnections(@PathVariable("provider_id") String providerId) {
        requireVisibleProvider(providerId);
        LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .orderByAsc(SysAiProviderConnection::getCreatedAt);
        // 账号隔离：连接属于绑定它的用户，不是供应商的公共子项
        applyConnectionOwnerFilter(wrapper);
        List<SysAiProviderConnection> conns = connectionMapper.selectList(wrapper);
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
     * @return 提供者连接详情
     */
    @GetMapping("/providers/{provider_id}/connections/{role}")
    public ApiResult<Map<String, Object>> getConnection(@PathVariable("provider_id") String providerId,
                                                        @PathVariable("role") String role) {
        requireVisibleProvider(providerId);
        LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .eq(SysAiProviderConnection::getRole, role);
        // 账号隔离：别人的连接按「不存在」处理，不区分「有但没权限」与「没有」
        applyConnectionOwnerFilter(wrapper);
        // 同 role 可能同时存在「自己名下」与「NULL 系统池」两行（管理员两者都可见），
        // selectOne 会抛 TooManyResultsException；取自己名下优先。
        List<SysAiProviderConnection> matches = connectionMapper.selectList(
                wrapper.orderByDesc(SysAiProviderConnection::getUserId));
        SysAiProviderConnection conn = matches.isEmpty() ? null : matches.get(0);
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
     * @return 提供者连接详情
     */
    @Permission("admin")
    @PutMapping("/providers/{provider_id}/connections")
    @Transactional
    public ApiResult<Map<String, Object>> saveConnection(@PathVariable("provider_id") String providerId,
                                                         @RequestBody Map<String, Object> body) {
        SysAiProvider entity = requireProvider(providerId);
        String role = requireStr(body, "role");

        LambdaQueryWrapper<SysAiProviderConnection> lookup =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .eq(SysAiProviderConnection::getRole, role);
        // 只认自己的连接（管理员另见 NULL 系统池）：别人 role=default 的账号不能被
        // 「保存」顺手改掉凭证 —— 同一个 provider 下同 role 可能有多行，按账号归属分家。
        applyConnectionOwnerFilter(lookup);
        // 管理员同时看得到「自己名下」与「NULL 系统池」两组行，同 role 可能各有一条；
        // selectOne 遇到两行会抛 TooManyResultsException。取自己名下优先，其次系统池。
        List<SysAiProviderConnection> matches = connectionMapper.selectList(
                lookup.orderByDesc(SysAiProviderConnection::getUserId));
        SysAiProviderConnection existing = matches.isEmpty() ? null : matches.get(0);

        if (existing == null) {
            // 新建：凭证为必填
            if (body.get("credentials") == null) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "新建连接时 credentials 必填");
            }
            SysAiProviderConnection conn = toConnectionEntity(providerId, body, System.currentTimeMillis());
            connectionMapper.insert(conn);
            return ApiResult.ok(toConnectionResponse(conn));
        } else {
        // 更新时仅覆盖显式传入的字段，未传凭证时保留原值
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
     */
    @Permission("admin")
    @DeleteMapping("/providers/{provider_id}/connections/{role}")
    @Transactional
    public ApiResult<Void> deleteConnection(@PathVariable("provider_id") String providerId,
                                            @PathVariable("role") String role) {
        requireProvider(providerId);
        LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId)
                        .eq(SysAiProviderConnection::getRole, role);
        // 账号隔离：只删自己名下的连接。别人的同 role 行不会被这条请求带走。
        applyConnectionOwnerFilter(wrapper);
        int deleted = connectionMapper.delete(wrapper);
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
     * @return 提供者模型响应列表
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
        // ONNX 是仅管理员可见的内置单行，其模型同样不外泄
        boolean admin = com.chua.starter.ai.support.util.AiUserContext.isAdmin();
        List<Map<String, Object>> items = new ArrayList<>(8); // 模型数量不可预估
        for (SysAiProviderModel model : models) {
            SysAiProvider provider = providerMapper.selectById(model.getProviderId());
            if (provider != null) {
                if (!admin && isOnnxRow(provider)) {
                    continue;
                }
                items.add(toProviderModelResponse(provider, model));
            }
        }
        return ApiResult.ok(items);
    }

    /**
     * 全量保存（upsert）某 提供者 下某模型的完整能力集。
     *
     * @param body 保存提供者模型请求
     * @return 提供者模型详情
     */
    @Permission("admin")
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
            // 新增：模型 ID 确定后，用已采集的指标补全上下文长度等参数（仅未配置时）
            enrichFromMetrics(provider, newModel);
            modelMapper.insert(newModel);
            refreshOnnxDevice(provider);
            return ApiResult.ok(toProviderModelResponse(provider, newModel));
        } else {
            applyModelInput(existing, modelInput);
            existing.setUpdatedAt(System.currentTimeMillis());
            // 复合主键由提供者和模型共同确定，避免同名模型跨提供者误更新。
            // 可空字段显式更新，确保传入 null 时能够清空已有配置。
            modelMapper.update(existing, new LambdaUpdateWrapper<SysAiProviderModel>()
                    .eq(SysAiProviderModel::getProviderId, providerId)
                    .eq(SysAiProviderModel::getModel, modelName)
                    .set(SysAiProviderModel::getAlias, existing.getAlias())
                    .set(SysAiProviderModel::getContextWindow, existing.getContextWindow())
                    .set(SysAiProviderModel::getMaxTokens, existing.getMaxTokens())
                    .set(SysAiProviderModel::getTemperature, existing.getTemperature())
                    .set(SysAiProviderModel::getTopP, existing.getTopP())
                    .set(SysAiProviderModel::getFrequencyPenalty, existing.getFrequencyPenalty()));
            refreshOnnxDevice(provider);
            return ApiResult.ok(toProviderModelResponse(provider, existing));
        }
    }

    /**
     * 重命名模型 标识（复合主键变更：复制行到新 标识 并删除旧行，原子完成）。
     *
     * @param body { 提供者_标识, 模型, 新_模型 }
     * @return 重命名后的 提供者模型响应
     */
    @Permission("admin")
    @PostMapping("/provider-models/rename")
    @Transactional
    public ApiResult<Map<String, Object>> renameProviderModel(@RequestBody Map<String, Object> body) {
        String providerId = requireStr(body, "provider_id");
        SysAiProvider provider = requireProvider(providerId);
        String oldName = requireStr(body, "model");
        String newName = requireStr(body, "new_model");
        // 新模型标识与旧标识相同时直接拒绝
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
        // 目标标识已被占用时返回冲突
        SysAiProviderModel dup = modelMapper.selectOne(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId)
                        .eq(SysAiProviderModel::getModel, newName));
        if (dup != null) {
            throw new ApiException(ApiErrorCode.CONFLICT, "模型 ID 已存在: " + newName);
        }
        // 在同一事务中复制到新标识并删除旧记录
        existing.setModel(newName);
        existing.setUpdatedAt(System.currentTimeMillis());
        // 改名后：新 ID 可能对应另一份已采集指标，用它补全上下文长度等（仅未配置时）
        enrichFromMetrics(provider, existing);
        modelMapper.insert(existing);
        modelMapper.delete(new LambdaQueryWrapper<SysAiProviderModel>()
                .eq(SysAiProviderModel::getProviderId, providerId)
                .eq(SysAiProviderModel::getModel, oldName));
        refreshOnnxDevice(provider);
        return ApiResult.ok(toProviderModelResponse(provider, existing));
    }

    /**
     * 删除 提供者_模型 行（按复合主键 提供者_标识 + 模型）。
     *
     * @param providerId 提供者 标识
     * @param model      模型名
     * @return 成功
     */
    @Permission("admin")
    @DeleteMapping("/provider-models")
    @Transactional
    public ApiResult<Void> deleteProviderModel(@RequestParam("provider_id") String providerId,
                                               @RequestParam("model") String model) {
        SysAiProvider provider = requireProvider(providerId);
        int deleted = modelMapper.delete(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId)
                        .eq(SysAiProviderModel::getModel, model));
        if (deleted == 0) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider model 不存在: " + model);
        }
        pruneRouterGroupMembers(providerId, model);
        refreshOnnxDevice(provider);
        return ApiResult.ok();
    }

    /**
     * 模型行删除后，把它从所有路由组成员表里摘掉。
     *
     * <p>路由组的成员就是模型库里的行；成员行被删后如果仍留在组里，那个成员会在
     * 每次路由时尝试构造客户端并失败。这里在同一个事务里一并清理，让「删模型」与
     * 「清成员」同进同退。</p>
     *
     * @param providerId 被删模型所属供应商
     * @param model      被删模型
     */
    private void pruneRouterGroupMembers(String providerId, String model) {
        try {
            List<SysAiProvider> all = providerMapper.selectList(new LambdaQueryWrapper<SysAiProvider>()
                    .eq(SysAiProvider::getPlatform, ProviderRouteTargets.ROUTER_GROUP_PLATFORM));
            for (com.chua.starter.ai.support.chat.RouterGroupService.PrunedGroup pruned
                    : routerGroupService.pruneMemberDeletions(all, providerId, model)) {
                SysAiProvider update = new SysAiProvider();
                update.setProviderId(pruned.providerId());
                update.setExtra(pruned.extra());
                update.setUpdatedAt(System.currentTimeMillis());
                // updateById 才会带上 JacksonTypeHandler（见 saveRouterGroup 的说明）
                providerMapper.updateById(update);
                modelClientResolver.invalidateRouterGroup(pruned.providerId());
            }
        } catch (Exception e) {
            log.warn("清理路由组成员失败 {}:{}: {}", providerId, model, e.getMessage());
        }
    }

    /**
     * 批量删除 提供者_模型 行（按复合主键 提供者_标识 + 模型）。
     *
     * <p>复合 id 用途与 {@link #deleteProviderModel(String, String)} 一致
     * （如 {@code openai:gpt-4o}），用 {@code ApiErrorCode.NOT_FOUND} 拦住不存在的 provider 行。
     * 整批解析完成后才删，避免部分删除。每删一个 provider 组刷新一次 ONNX 推理设备覆盖。</p>
     *
     * @param body 请求体，须含 {@code ids} 数组（providerId:modelName）
     * @return 包含 success 与 deletedCount 的结果
     */
    @Permission("admin")
    @DeleteMapping("/provider-models/batch")
    @Transactional
    public ApiResult<Map<String, Object>> batchDeleteProviderModels(@RequestBody Map<String, ?> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = body == null ? null : (List<String>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ids 不能为空");
        }

        // 先整批定位标识，再批量删除：定位失败直接拦截整批
        List<String[]> pairs = new ArrayList<>(ids.size());
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            int separator = id.indexOf(':');
            String providerId = separator > 0 ? id.substring(0, separator) : null;
            String model = separator > 0 ? id.substring(separator + 1) : null;
            if (providerId == null || providerId.isBlank() || model == null || model.isBlank()) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM,
                        "模型ID格式非法，应为 providerId:modelName");
            }
            requireProvider(providerId);
            pairs.add(new String[] { providerId, model });
        }
        if (pairs.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "无有效的模型ID");
        }

        int deleted = 0;
        String lastProviderId = null;
        for (String[] pair : pairs) {
            String providerId = pair[0];
            SysAiProvider provider = requireProvider(providerId);
            int removed = modelMapper.delete(
                    new LambdaQueryWrapper<SysAiProviderModel>()
                            .eq(SysAiProviderModel::getProviderId, providerId)
                            .eq(SysAiProviderModel::getModel, pair[1]));
            if (removed > 0) {
                deleted++;
            }
            // 同一 provider 连续删除时只刷新一次
            if (!providerId.equals(lastProviderId)) {
                refreshOnnxDevice(provider);
                lastProviderId = providerId;
            }
            if (removed > 0) {
                pruneRouterGroupMembers(providerId, pair[1]);
            }
        }

        Map<String, Object> result = new HashMap<>(2);
        result.put("success", true);
        result.put("deletedCount", deleted);
        return ApiResult.ok(result);
    }

    // ==================== 路由组 ====================

    /**
     * 路由组成员候选：已加入模型库的模型（供组页面搜索添加）。
     *
     * @param keyword 模型名关键字
     * @return 候选列表
     */
    @GetMapping("/router-groups/candidates")
    public ApiResult<List<Map<String, Object>>> listRouterGroupCandidates(
            @RequestParam(value = "keyword", required = false) String keyword) {
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SysAiProviderModel row : routerGroupService.searchMemberCandidates(keyword)) {
            SysAiProvider owner = providerMapper.selectById(row.getProviderId());
            if (owner == null || ProviderRouteTargets.isRouterGroup(owner.getPlatform())) {
                continue;
            }
            String key = row.getProviderId() + ":" + row.getModel();
            if (!seen.add(key)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("provider_id", row.getProviderId());
            item.put("provider_name", owner.getName());
            item.put("platform", owner.getPlatform());
            item.put("model", row.getModel());
            item.put("alias", row.getAlias());
            item.put("enabled", row.getEnabled());
            item.put("context_window", row.getContextWindow());
            item.put("effort", routerGroupService.readEffort(row));
            items.add(item);
        }
        return ApiResult.ok(items);
    }

    /**
     * 读取路由组实时决策快照（供管理页的实时连线视图使用）。
     *
     * <p>只读内存快照：进程重启后为空，视图显示「等待请求」即可。不落库、不改配置。</p>
     *
     * @param providerId 路由组供应商 ID
     * @return 最近决策（最新在前）
     */
    @GetMapping("/providers/{provider_id}/router-group/live")
    public ApiResult<Map<String, Object>> getRouterGroupLive(@PathVariable("provider_id") String providerId) {
        SysAiProvider provider = requireProvider(providerId);
        if (!routerGroupService.isRouterGroup(provider)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该供应商不是路由组: " + providerId);
        }
        List<Map<String, Object>> items = new ArrayList<>(8);
        for (com.chua.common.support.ai.chat.aggregate.RouterGroupActivity.Decision d
                : com.chua.common.support.ai.chat.aggregate.RouterGroupActivity.recent(providerId)) {
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("member_label", d.memberLabel());
            item.put("member_model", d.memberModel());
            item.put("at", d.at());
            item.put("success", d.success());
            item.put("cost_ms", d.costMs());
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("decisions", items);
        result.put("server_time", System.currentTimeMillis());
        return ApiResult.ok(result);
    }

    /**
     * 读取路由组配置（成员顺序 / 策略 / 每模型 effort）。
     *
     * @param providerId 路由组供应商 ID
     * @return 配置
     */
    @GetMapping("/providers/{provider_id}/router-group")
    public ApiResult<Map<String, Object>> getRouterGroup(@PathVariable("provider_id") String providerId) {
        SysAiProvider provider = requireProvider(providerId);
        if (!routerGroupService.isRouterGroup(provider)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该供应商不是路由组: " + providerId);
        }
        com.chua.common.support.ai.chat.aggregate.RouterGroupSetting setting =
                routerGroupService.readSetting(provider);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("setting", routerGroupService.toResponse(setting));
        result.put("members", routerGroupService.describeMembers(setting));
        return ApiResult.ok(result);
    }

    /**
     * 保存路由组配置：成员（新增 / 移除 / 顺序）、策略与每模型 effort。
     *
     * <p>成员一律来自「已添加的模型库」：{@code members} 里出现的每一行都必须能在
     * {@code sys_ai_provider_model} 里查到，否则整批拒绝 —— 避免配置上引用一个不存在的模型，
     * 直到真正调用时才失败。写入后让该路由组的客户端缓存立即失效。</p>
     *
     * @param providerId 路由组供应商 ID
     * @param body       配置（members / strategy / on_rate_limit / on_slow / on_busy / on_context …）
     * @return 保存后的配置
     */
    @Permission("admin")
    @PutMapping("/providers/{provider_id}/router-group")
    @Transactional
    public ApiResult<Map<String, Object>> saveRouterGroup(@PathVariable("provider_id") String providerId,
                                                          @RequestBody Map<String, Object> body) {
        SysAiProvider provider = requireProvider(providerId);
        if (!routerGroupService.isRouterGroup(provider)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该供应商不是路由组: " + providerId);
        }
        List<com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member> members =
                parseRouterGroupMembers(providerId, body);
        com.chua.common.support.ai.chat.aggregate.RouterGroupSetting setting =
                routerGroupService.applyRequest(provider, body, members);
        SysAiProvider update = new SysAiProvider();
        update.setProviderId(providerId);
        update.setExtra(routerGroupService.writeSetting(provider, setting));
        update.setUpdatedAt(System.currentTimeMillis());
        // 必须用 updateById：LambdaUpdateWrapper.set(...) 不会带上实体的 TypeHandler，
        // MyBatis-Plus 会退化成默认的 Java 序列化写 BLOB 字节，MySQL 直接报
        // 「Incorrect string value '\xAC\xED\x00\x05sr...'」。
        providerMapper.updateById(update);
        modelClientResolver.invalidateRouterGroup(providerId);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("setting", routerGroupService.toResponse(setting));
        result.put("members", routerGroupService.describeMembers(setting));
        log.info("保存路由组配置 provider={} 成员={} 策略={}", providerId,
                members.size(), setting.getStrategy());
        return ApiResult.ok(result);
    }

    /**
     * 调整路由组成员顺序（前端拖拽后调用）。
     *
     * @param providerId 路由组供应商 ID
     * @param body       含 {@code labels} 数组（{@code providerId:model}，新顺序）
     * @return 保存后的配置
     */
    @Permission("admin")
    @PutMapping("/providers/{provider_id}/router-group/order")
    @Transactional
    public ApiResult<Map<String, Object>> reorderRouterGroup(@PathVariable("provider_id") String providerId,
                                                             @RequestBody Map<String, Object> body) {
        SysAiProvider provider = requireProvider(providerId);
        if (!routerGroupService.isRouterGroup(provider)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该供应商不是路由组: " + providerId);
        }
        @SuppressWarnings("unchecked")
        List<String> labels = body == null ? null : (List<String>) body.get("labels");
        if (labels == null || labels.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "labels 不能为空");
        }
        com.chua.common.support.ai.chat.aggregate.RouterGroupSetting setting =
                routerGroupService.readSetting(provider);
        Map<String, com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member> byLabel =
                new LinkedHashMap<>();
        for (com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member m : setting.getMembers()) {
            byLabel.put(m.label(), m);
        }
        List<com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member> ordered = new ArrayList<>();
        for (String label : labels) {
            com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member m = byLabel.remove(label);
            if (m != null) {
                ordered.add(m);
            }
        }
        // 未出现在新顺序里的成员按原相对顺序补齐，不允许因排序丢成员
        ordered.addAll(byLabel.values());
        setting.setMembers(ordered);
        SysAiProvider update = new SysAiProvider();
        update.setProviderId(providerId);
        update.setExtra(routerGroupService.writeSetting(provider, setting));
        update.setUpdatedAt(System.currentTimeMillis());
        // updateById 才会带上 JacksonTypeHandler（见 saveRouterGroup 的说明）
        providerMapper.updateById(update);
        modelClientResolver.invalidateRouterGroup(providerId);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("setting", routerGroupService.toResponse(setting));
        result.put("members", routerGroupService.describeMembers(setting));
        return ApiResult.ok(result);
    }

    /**
     * 解析请求体里的成员数组，逐个校验「确实已加入模型库」。
     *
     * @param groupId 路由组 ID（用于拒绝自引用）
     * @param body    请求体
     * @return 成员列表（保持请求顺序）
     */
    private List<com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member> parseRouterGroupMembers(
            String groupId, Map<String, Object> body) {
        Object raw = body == null ? null : body.get("members");
        List<com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member> members = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return members;
        }
        Set<String> seen = new HashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String memberProviderId = m.get("provider_id") == null ? null : m.get("provider_id").toString().trim();
            String model = m.get("model") == null ? null : m.get("model").toString().trim();
            if (memberProviderId == null || memberProviderId.isEmpty() || model == null || model.isEmpty()) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "成员必须同时给出 provider_id 与 model");
            }
            if (groupId.equals(memberProviderId)) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "路由组不能把自己加入成员: " + model);
            }
            SysAiProvider owner = providerMapper.selectById(memberProviderId);
            if (owner == null || ProviderRouteTargets.isRouterGroup(owner.getPlatform())) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM,
                        "成员必须是已添加的模型: " + memberProviderId + ":" + model);
            }
            if (!routerGroupService.memberExists(memberProviderId, model)) {
                throw new ApiException(ApiErrorCode.NOT_FOUND,
                        "成员模型不在模型库中或已停用: " + memberProviderId + ":" + model);
            }
            String label = memberProviderId + ":" + model;
            if (!seen.add(label)) {
                continue;
            }
            String effort = m.get("effort") == null ? null : m.get("effort").toString().trim();
            int weight = parseRouterWeight(m.get("weight"));
            Boolean free = m.get("free") == null ? null : Boolean.valueOf(m.get("free").toString());
            Long expireAt = parseRouterLong(m.get("expire_at"));
            members.add(new com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member(
                    memberProviderId, model, true, weight,
                    effort == null || effort.isEmpty() ? null : effort, free, expireAt));
        }
        return members;
    }

    /**
     * 解析成员权重，非法值回落到 1。
     *
     * @param value 原始值
     * @return 权重（至少 1）
     */
    private int parseRouterWeight(Object value) {
        if (value == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(value.toString().trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * 解析可空长整数，非法值按无值处理。
     *
     * @param value 原始值
     * @return 长整数或 null
     */
    private Long parseRouterLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
     * 路由组在「供应商 → 模型」视图里对外暴露的唯一虚拟模型。
     *
     * <p>路由组没有自己的 {@code sys_ai_provider_model} 行（成员是模型库里已有的行），
     * 但会话的模型面板只认 {@code provider.models}，因此这里合成一条模型项，
     * 让「选中这个路由组」在会话里可选。它不落库，也不出现在模型管理页的模型列表里。</p>
     *
     * @param provider 路由组供应商
     * @return 虚拟模型项
     */
    private Map<String, Object> routerGroupVirtualModel(SysAiProvider provider) {
        String providerId = provider.getProviderId();
        String modelName = provider.getName() == null || provider.getName().isBlank()
                ? providerId : provider.getName();
        Map<String, Object> model = new LinkedHashMap<>(16);
        model.put("provider_id", providerId);
        model.put("model", modelName);
        model.put("id", providerId + ":" + modelName);
        model.put("modelType", "text");
        model.put("enabled", Boolean.TRUE.equals(provider.getEnabled()));
        model.put("sort_order", 0);
        model.put("alias", modelName);
        model.put("router_group", true);
        Map<String, Object> capability = new LinkedHashMap<>(4);
        capability.put("task", "chat");
        capability.put("traits", List.of("input:text", "output:text"));
        model.put("capabilities", List.of(capability));
        return model;
    }

    /**
     * 构造 提供者响应（含模型行与连接档案）。
     */
    private Map<String, Object> toProviderResponse(SysAiProvider provider,
                                                   List<SysAiProviderModel> models,
                                                   List<SysAiProviderConnection> connections) {        Map<String, Object> map = new LinkedHashMap<>(12);
        map.put("provider_id", provider.getProviderId());
        map.put("platform", provider.getPlatform());
        map.put("name", provider.getName());
        map.put("base_url", provider.getBaseUrl());
        // ONNX 供应商补 model_root：本地模型的真实落盘位置（绝对路径），供管理面显示与核对。
        // 用 displayModelRoot() 而非 readModelRoot()：后者未绑定过目录时返回空串，
        // 页面就不知道「模型该放到哪」；前者回落注册表默认根（models/onnx）并按
        // user.dir 绝对化，永远给出可直接复制的完整路径。
        // 只放这一个字段：/providers 是非管理员也可调的接口（写操作才拦），
        // 而 ONNX 行对非管理员已整体不返回（listProviders / listProviderModels 都过滤），
        // 因此该路径只可能到达管理员；不放 credentials.api_key（那是凭证语义）。
        if (isOnnxRow(provider)) {
            map.put("model_root", onnxExternalDirectoryService.displayModelRoot());
        }
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

            // 完整 Key 列表只回给管理员：
            // 本接口（GET /api/providers）是非管理员也能调的读接口，写操作才拦 admin。
            // 明文 Key 属于凭证语义 —— 回给只读账号等于把可用的上游密钥交给它。
            // 前端取 api_keys 的地方只有两处，都在 admin-only 的写流程里
            // （ProviderFormDialog 编辑回显、ModelFetchDialog 拉模型前置判断），
            // 非管理员拿不到这一段不影响任何只读展示。
            if (com.chua.starter.ai.support.util.AiUserContext.isAdmin()) {
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
        Map<String, Object> map = new LinkedHashMap<>(20);
        map.put("provider_id", provider.getProviderId());
        map.put("model", model.getModel());
        map.put("id", provider.getProviderId() + ":" + model.getModel());
        map.put("modelType", "text");
        map.put("enabled", model.getEnabled());
        map.put("sort_order", model.getSortOrder());
        if (model.getAlias() != null) {
            map.put("alias", model.getAlias());
        }
        if (model.getDescription() != null) {
            map.put("description", model.getDescription());
        }
        // 窗口与采样参数为空表示未指定，前端卡片据此回退到指标表中的官方值
        map.put("context_window", model.getContextWindow());
        map.put("max_tokens", model.getMaxTokens());
        map.put("temperature", model.getTemperature());
        map.put("top_p", model.getTopP());
        map.put("frequency_penalty", model.getFrequencyPenalty());
        // 指标回填字段：实体列 + metrics JSON 双存（同步价格时自动回填，编辑可改）
        if (model.getFamily() != null) {
            map.put("family", model.getFamily());
        }
        if (model.getKnowledge() != null) {
            map.put("knowledge", model.getKnowledge());
        }
        if (model.getReleaseDate() != null) {
            map.put("release_date", model.getReleaseDate());
        }
        if (model.getLastUpdated() != null) {
            map.put("last_updated", model.getLastUpdated());
        }
        if (model.getModalities() != null && !model.getModalities().isEmpty()) {
            map.put("modalities", model.getModalities());
        }
        map.put("open_weights", model.getOpenWeights());
        map.put("attachment", model.getAttachment());
        map.put("reasoning", model.getReasoning());
        map.put("tool_call", model.getToolCall());
        map.put("temperature_supported", model.getTemperatureSupported());
        // ONNX 模型补 external / available：设置页的模型选择器在 onnx 分支里
        // 按 `m.external === true` 过滤（见 DefaultModelSettings.loadModels），
        // 而本方法此前从不返回这两个键 —— 于是「图片识别/人脸/OCR」等分组的
        // 模型弹框里 ONNX 模型一个都不显示，提示「没有匹配类型的模型」，
        // 用户无从配置，只能靠接口写库绕过去。
        // external=true 表示该模型由「绑定外部目录」登记（模板模型名 + 独立权重目录），
        // 这类模型的权重一定在本地；内置模型则用 ModelRegistry 是否已注册判定可用性，
        // 避免把上百个未下载的候选也列出来。
        if (isOnnxRow(provider)) {
            boolean external = isExternalBoundModel(model.getModel());
            map.put("external", external);
            map.put("available", external || com.chua.deeplearning.support.engine.ModelRegistry
                    .isRegistered(model.getModel()));
        }
        List<Map<String, Object>> caps = model.getCapabilities() != null
                ? model.getCapabilities().stream().map(this::toCapabilityResponse).collect(Collectors.toList())
                : new ArrayList<>(1);
        map.put("capabilities", caps);
        // 生成参数（参考图 / 张数上限 / 分辨率列表 / 时长列表 / 步数区间）：
        // 原样回传 capabilities[].generate 节点，前端编辑时原样带回即可
        if (caps.stream().anyMatch(c -> c.get("generate") != null)) {
            map.put("generate", caps.stream()
                    .map(c -> c.get("generate"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null));
        }
        // 思考强度档位：与 generate 同款——原样回传 capabilities[].thinking_efforts，
        // 缺省不返回该键，前端按默认集合（low/medium/high/max）处理
        if (caps.stream().anyMatch(c -> c.get("thinking_efforts") != null)) {
            map.put("thinking_efforts", caps.stream()
                    .map(c -> c.get("thinking_efforts"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null));
        }
        // 图片输入输出细化（张数 / 参考图）：与 generate 同款——原样回传 capabilities[].image，
        // 缺省不返回该键，前端编辑弹窗按 null / false 兜底
        if (caps.stream().anyMatch(c -> c.get("image") != null)) {
            map.put("image", caps.stream()
                    .map(c -> c.get("image"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null));
        }
        // 模型指标优先使用自身配置，再从缓存快照补充价格、延迟、智能和速度数据
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
        // 本地平均输出速度：用量聚合任务回填至 sys_ai_model_metrics（非指标快照、
        // 不进 METRIC_KEYS：指标库中的 @code avg_output_speed_tps 才是本地均值），
        // 模型卡片的速度列优先展示本地平均值。
        try {
            fillAvgOutputSpeed(provider, model, metrics);
        } catch (Throwable e) {
            log.warn("[ApiProvider] 读取本地平均输出速度失败: {}", e.getMessage());
        }
        if (!metrics.isEmpty()) {
            map.put("metrics", metrics);
        }
        map.put("created_at", model.getCreatedAt());
        map.put("updated_at", model.getUpdatedAt());
        return map;
    }

    /**
     * 计算 provider/model 所在指标库行的本地平均输出速度，并入模型指标（空值不写）。
     *
     * <p><b>数据源</b>：sys_ai_model_metrics（由用量聚合任务按 provider+model 回填），
     * 与指标快照无关（不是 METRIC_KEYS 成员、不进 Redis 快照）。前端「速度」列优先
     * 取本地均值 {@code avg_output_speed_tps}，未回填时回退到 {@code output_speed_tps}。</p>
     *
     * @param provider  供应商行
     * @param model     模型行
     * @param metrics   待补充的指标映射（就地写入）
     */
    /**
     * 将键截断为固定长度（可排序、可索引）
     *
     * @param value  原值
     * @param maxLen 最大长度
     * @return 截断后的值；入参为空时返回空串
     */
    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private void fillAvgOutputSpeed(SysAiProvider provider, SysAiProviderModel model, Map<String, Object> metrics) {
        String key = truncate(
                modelMetricsEnrichmentService.normalizeKey(provider.getProviderId() + "/" + model.getModel()), 160);
        if (key.isEmpty()) {
            return;
        }
        SysAiModelMetrics row = sysAiModelMetricsService.getByKey(key);
        if (row == null || row.getAvgOutputSpeedTps() == null) {
            return;
        }
        metrics.put("avg_output_speed_tps", row.getAvgOutputSpeedTps());
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
     * @param cap 模型能力配置映射
     * @return 转为capability响应的结果
     */
    private Map<String, Object> toCapabilityResponse(Map<String, Object> cap) {
        Map<String, Object> map = new LinkedHashMap<>(14);
        map.put("task", cap.get("task"));
        map.put("traits", cap.get("traits") != null ? cap.get("traits") : List.of());
        map.put("protocol", cap.get("protocol"));
        map.put("connection_role", cap.get("connection_role"));
        // OCR/人脸组合模型细分类型：与 generate 同理，必须显式透传，
        // 否则数据库虽已保存，前端刷新后只能看到粗类型。
        if (cap.get("subtype") != null) {
            map.put("subtype", cap.get("subtype"));
        }
        // 生成参数节点：白名单式构造会丢字段，这里显式透传，供模型弹框回显与编辑
        if (cap.get("generate") != null) {
            map.put("generate", cap.get("generate"));
        }
        // 思考强度档位（low/medium/high/max）：与 generate 同理，显式透传，
        // 供模型管理弹框回显与编辑；未配置时前端回退到默认集合
        if (cap.get("thinking_efforts") != null) {
            map.put("thinking_efforts", cap.get("thinking_efforts"));
        }
        // 图片输入输出细化（张数 / 参考图）：与 generate 同理，显式透传，
        // 供模型弹框回显与编辑；未配置时缺省不返回该键
        if (cap.get("image") != null) {
            map.put("image", cap.get("image"));
        }
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
        // 最近一次健康探测结果，包括状态、延迟和错误信息
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
     * @param conn 提供者连接配置
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
        e.setAlias(str(input, "alias"));
        e.setEnabled(bool(input, "enabled", true));
        e.setSortOrder(intv(input, "sort_order", 0));
        e.setDescription(str(input, "description"));
    // 窗口与采样参数：模型弹框填了就落库；留空表示「未指定」，
    // 前端模型卡片会回退到指标表（sys_ai_model_metrics）里的官方值
        e.setContextWindow(intOrNull(input, "context_window"));
        e.setMaxTokens(intOrNull(input, "max_tokens"));
        e.setTemperature(dblOrNull(input, "temperature"));
        e.setTopP(dblOrNull(input, "top_p"));
        e.setFrequencyPenalty(dblOrNull(input, "frequency_penalty"));
        // 模型能力列表直接保存原始数据
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
        // 生成参数（参考图 / 张数上限 / 分辨率列表 / 时长列表 / 步数区间）：
        // 复用 capabilities[].generate 节点，不新增数据库字段
        applyGenerateConfig(e, input.get("generate"));
        // 思考强度档位（none/minimal/low/medium/high/max）：
        // 同样复用 capabilities[].thinking_efforts
        applyThinkingEfforts(e, input.get("thinking_efforts"));
        // 指标回填字段（实体列，同步价格回填 + 编辑可改）
        e.setFamily(str(input, "family"));
        e.setKnowledge(str(input, "knowledge"));
        e.setReleaseDate(str(input, "release_date"));
        e.setLastUpdated(str(input, "last_updated"));
        e.setModalities(toModalitiesMap(input.get("modalities")));
        e.setOpenWeights(boolOrNull(input, "open_weights"));
        e.setAttachment(boolOrNull(input, "attachment"));
        e.setReasoning(boolOrNull(input, "reasoning"));
        e.setToolCall(boolOrNull(input, "tool_call"));
        e.setTemperatureSupported(boolOrNull(input, "temperature_supported"));
        // 指标数据直接保存延迟、智能和输出速度等原始数据
        Object metricsObj = input.get("metrics");
        if (metricsObj instanceof Map<?, ?> metricsMap) {
            e.setMetrics((Map<String, Object>) metricsMap);
        }
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    /**
     * 用采集到的模型指标补全模型参数。
     *
     * <p>在模型<strong>新增、批量导入或改名后</strong>调用，两级补齐：</p>
     * <ol>
     *   <li>AA 指标（上下文长度、输出上限、价格、各类评分）→ 实体列与 metrics JSON；</li>
     *   <li>服务商指标端点（Models.dev 等 {@link ChatClientIndicator}）→ 按模型名匹配，
     *       回填 family / knowledge / release_date / last_updated / modalities / 能力开关
     *       实体列与 metrics JSON。</li>
     * </ol>
     *
     * <p><b>只在未配置时回填</b>：{@code contextWindow} / {@code maxTokens} 及新增的
     * 指标实体列为 {@code null}（用户未填）才写入；用户已显式配置的值一律保留，不被采集数据覆盖。
     * 指标本身以 {@code putIfNotNull} 写入，采集侧无值时不会产生空字段。</p>
     *
     * @param provider 服务商行（用于解析指标端点）
     * @param model    模型实体（就地修改）
     * @return 是否有字段被回填
     */
    private boolean enrichFromMetrics(SysAiProvider provider, SysAiProviderModel model) {
        if (model == null || model.getModel() == null || model.getModel().isBlank()) {
            return false;
        }
        Map<String, Object> row = new LinkedHashMap<>(32);
        try {
            modelMetricsEnrichmentService.enrich(model.getModel(), row);
        } catch (Exception e) {
            log.debug("[provider-model] 指标补全跳过 model={} 原因={}", model.getModel(), e.getMessage());
            row.clear();
        }
        boolean changed = false;
        if (model.getContextWindow() == null) {
            Integer ctx = intOrNull(row, "context_window_tokens");
            if (ctx != null && ctx > 0) {
                model.setContextWindow(ctx);
                changed = true;
            }
        }
        if (model.getMaxTokens() == null) {
            Integer max = intOrNull(row, "max_output_tokens");
            if (max == null) {
                max = intOrNull(row, "max_tokens");
            }
            if (max != null && max > 0) {
                model.setMaxTokens(max);
                changed = true;
            }
        }
        // 其余指标（价格、评分、能力标记）合并进 metrics 原始列，不覆盖已有键
        Map<String, Object> current = model.getMetrics();
        Map<String, Object> metrics = current == null
                ? new LinkedHashMap<>(32)
                : new LinkedHashMap<>(current);
        for (Map.Entry<String, Object> item : row.entrySet()) {
            String key = item.getKey();
            if (metrics.containsKey(key)) {
                continue;
            }
            if ("context_window_tokens".equals(key) || "max_output_tokens".equals(key)
                    || "max_tokens".equals(key)) {
                // 已映射到实体列，不再重复写入 metrics
                continue;
            }
            metrics.put(key, item.getValue());
        }
        if (!metrics.equals(current)) {
            model.setMetrics(metrics);
            changed = true;
        }
        // 服务商指标端点（Models.dev 等）：按模型名匹配快照，回填指标实体列
        changed |= enrichFromIndicator(provider, model);
        if (changed) {
            log.info("[provider-model] 已用采集指标补全参数 model={} contextWindow={} maxTokens={}",
                    model.getModel(), model.getContextWindow(), model.getMaxTokens());
        }
        return changed;
    }

    /**
     * 按模型名从服务商指标端点查指标快照（新增/编辑模型页「自动获取」用）。
     *
     * <p>精确匹配 → 归一化键匹配 → 归一化前缀包含匹配三级；未收录返回 null。</p>
     *
     * @param provider  服务商行
     * @param modelName 模型名
     * @return 指标快照（Models.dev 等 indicator 的标准键），未找到返回 null
     */
    private Map<String, Object> findIndicatorMetrics(SysAiProvider provider, String modelName) {
        try {
            ChatClientIndicator indicator = resolveIndicator(provider);
            if (indicator == null) {
                return null;
            }
            Map<String, Map<String, Object>> all = indicator.modelMetrics();
            if (all == null || all.isEmpty()) {
                return null;
            }
            if (modelName == null || modelName.isBlank()) {
                return null;
            }
            Map<String, Object> hit = all.get(modelName);
            if (hit == null) {
                String key = modelMetricsEnrichmentService.normalizeKey(modelName);
                if (key != null && !key.isBlank()) {
                    hit = all.get(key);
                    if (hit == null) {
                        for (Map.Entry<String, Map<String, Object>> e : all.entrySet()) {
                            if (key.equals(modelMetricsEnrichmentService.normalizeKey(e.getKey()))) {
                                hit = e.getValue();
                                break;
                            }
                        }
                    }
                }
            }
            return hit;
        } catch (Exception e) {
            log.debug("[provider-model] 指标快照查询跳过 model={} 原因={}", modelName, e.getMessage());
            return null;
        }
    }

    /**
     * 从服务商指标端点（Models.dev 等）按模型名回填指标实体列与 metrics JSON。
     *
     * <p>复用 {@link SysAiModelMetricsService#enrichRowFromMetrics}：与价格同步
     * 走同一套口径（family / knowledge / release_date / last_updated / modalities /
     * open_weights / attachment / reasoning / tool_call / temperature_supported），
     * 仅在实体列为空时回填。指标端点不可用或未收录该模型时返回 false。</p>
     *
     * @param provider 服务商行
     * @param model    模型实体（就地修改）
     * @return 是否回填了指标实体列
     */
    private boolean enrichFromIndicator(SysAiProvider provider, SysAiProviderModel model) {
        try {
            Map<String, Object> hit = findIndicatorMetrics(provider, model.getModel());
            if (hit == null || hit.isEmpty()) {
                return false;
            }
            Map<String, Object> current = model.getMetrics() == null
                    ? new LinkedHashMap<>(32)
                    : new LinkedHashMap<>(model.getMetrics());
            ChatClientIndicator indicator = resolveIndicator(provider);
            Long capturedAt = indicator == null ? null : indicator.capturedAt();
            sysAiModelMetricsService.enrichRowFromMetrics(model, current, hit,
                    indicator == null ? null : indicator.source(),
                    indicator == null ? null : indicator.sourceUrl(),
                    capturedAt == null ? System.currentTimeMillis() : capturedAt);
            return true;
        } catch (Exception e) {
            log.debug("[provider-model] 指标端点回填跳过 model={} 原因={}", model.getModel(), e.getMessage());
            return false;
        }
    }

    /**
     * 把模型输入应用到已有实体（更新，替换完整能力集）。
     * @param e e
     * @param input 输入
     */
    @SuppressWarnings("unchecked")
    private void applyModelInput(SysAiProviderModel e, Map<String, Object> input) {
        if (input.containsKey("alias")) {
            e.setAlias(str(input, "alias"));
        }
        if (input.containsKey("enabled")) {
            e.setEnabled(bool(input, "enabled", e.getEnabled()));
        }
        if (input.containsKey("sort_order")) {
            e.setSortOrder(intv(input, "sort_order", e.getSortOrder()));
        }
        if (input.containsKey("description")) {
            e.setDescription(str(input, "description"));
        }
    // 窗口与采样参数：按字段存在与否覆盖，允许显式传 null 清空
        if (input.containsKey("context_window")) {
            e.setContextWindow(intOrNull(input, "context_window"));
        }
        if (input.containsKey("max_tokens")) {
            e.setMaxTokens(intOrNull(input, "max_tokens"));
        }
        if (input.containsKey("temperature")) {
            e.setTemperature(dblOrNull(input, "temperature"));
        }
        if (input.containsKey("top_p")) {
            e.setTopP(dblOrNull(input, "top_p"));
        }
        if (input.containsKey("frequency_penalty")) {
            e.setFrequencyPenalty(dblOrNull(input, "frequency_penalty"));
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
        // 生成参数：按字段存在与否覆盖（复用 capabilities[].generate 节点）
        if (input.containsKey("generate")) {
            applyGenerateConfig(e, input.get("generate"));
        }
        // 思考强度档位：按字段存在与否覆盖（复用 capabilities[].thinking_efforts 节点）
        if (input.containsKey("thinking_efforts")) {
            applyThinkingEfforts(e, input.get("thinking_efforts"));
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
     * 把请求体中的生成参数写入实体的 {@code capabilities[].generate} 节点。
     *
     * <p>capabilities 为空时会先建一条空记录，保证 generate 有处安放；整段生成参数
     * 复用已有 JSON 列，不新增数据库字段。</p>
     *
     * @param entity 模型实体（原地修改）
     * @param raw    请求体中的 generate 对象，可为 null（表示不处理）
     */
    @SuppressWarnings("unchecked")
    private void applyGenerateConfig(SysAiProviderModel entity, Object raw) {
        if (raw == null) {
            return;
        }
        Map<String, Object> gen;
        if (raw instanceof Map<?, ?> map) {
            gen = (Map<String, Object>) map;
        } else {
            return;
        }
        List<Map<String, Object>> caps = entity.getCapabilities();
        if (caps == null || caps.isEmpty()) {
            caps = new ArrayList<>(1);
            caps.add(new LinkedHashMap<>(4));
            entity.setCapabilities(caps);
        }
        Map<String, Object> node = caps.getFirst();
        if (node == null) {
            node = new LinkedHashMap<>(4);
            caps.set(0, node);
        }
        node.put("generate", gen);
    }

    /**
     * 把请求体中的思考强度档位写入实体的 {@code capabilities[].thinking_efforts} 节点。
     *
     * <p>与 {@link #applyGenerateConfig} 同款：整段档位清单复用已有 JSON 列，不新增
     * 数据库字段；空列表表示「未配置」——前端按默认集合（low/medium/high/max）兜底。</p>
     *
     * @param entity 模型实体
     * @param raw    档位清单（List&lt;String&gt;；null 表示本次不改动）
     */
    @SuppressWarnings("unchecked")
    private void applyThinkingEfforts(SysAiProviderModel entity, Object raw) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        List<String> efforts = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                efforts.add(String.valueOf(item).trim());
            }
        }
        List<Map<String, Object>> caps = entity.getCapabilities();
        if (caps == null || caps.isEmpty()) {
            caps = new ArrayList<>(1);
            caps.add(new LinkedHashMap<>(4));
            entity.setCapabilities(caps);
        }
        Map<String, Object> node = caps.getFirst();
        if (node == null) {
            node = new LinkedHashMap<>(4);
            caps.set(0, node);
        }
        node.put("thinking_efforts", efforts);
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
        // 归属**故意留空**：本链路的写入入口全部是 admin-only（createProvider / saveConnection /
        // cloneProvider），落成 user_id IS NULL 即 SysAiProviderConnection 里定义的
        // 「管理员/系统账号池」—— 管理员读得到（applyConnectionOwnerFilter 额外放行 NULL），
        // 普通用户读不到，免费渠道（_free 作用域）也靠这批行取账号。
        // 不要在这里写 currentUserId()：那样免费渠道路由会一条都取不到。
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
        // 中国平台的官方兼容接口地址
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
        // Moss 开放平台：自有一组端点（/audio/speech、/audio/transcriptions、/responses、/images/generations）
        m.put("moss", new ProtocolPreset("moss", "https://api.mosi.cn/v1"));
        m.put("mosi", new ProtocolPreset("mosi", "https://api.mosi.cn/v1"));
        m.put("deepgram", new ProtocolPreset("deepgram", "https://api.deepgram.com/v1"));
        m.put("novita", new ProtocolPreset("novita", "https://api.novita.ai/openai/v1"));
        m.put("openrouter", new ProtocolPreset("openrouter", "https://openrouter.ai/api/v1"));
        m.put("poe", new ProtocolPreset("poe", "https://api.poe.com/v1"));
        m.put("ppio", new ProtocolPreset("ppio", "https://api.ppinfra.com/v3/openai"));
        m.put("modelscope", new ProtocolPreset("modelscope", "https://api-inference.modelscope.cn/v1"));
        m.put("infiniai", new ProtocolPreset("infiniai", "https://cloud.infini-ai.com/maas/v1"));
        m.put("ctyun", new ProtocolPreset("ctyun", "https://modelverse.cn/v1"));
        m.put("new-api", new ProtocolPreset("new-api", "http://localhost:3000/v1"));
        // 在线神经网络推理模型的基础地址保存模型文件路径，没有远程端点
        m.put("onnx", new ProtocolPreset("onnx", ""));
        m.put("custom", new ProtocolPreset("custom", "https://api.openai.com/v1"));
        PROTOCOL_PRESETS = Map.copyOf(m);
    }

    // ==================== 模型拉取 ====================

    /**
     * 上游鉴权参数。
     *
     * <p>把「认证方案 + 头名称 + 值前缀」三件套打包传递，避免在多层调用里逐个加参数。
     * 头名称与前缀来自供应商 {@code extra.auth_header} / {@code extra.auth_prefix}，
     * 用于 Dots Studio 这类只认 {@code api-key} 头、不认 {@code Authorization: Bearer} 的服务商。</p>
     *
     * @param scheme 认证方案（api_key / bearer / oauth / none）
     * @param header 认证头名称覆盖，可为 null
     * @param prefix 认证值前缀覆盖，可为 null
     */
    private record UpstreamAuth(String scheme, String header, String prefix) {

        /**
         * 从供应商实体构建。
         *
         * @param authScheme 认证方案
         * @param extra      扩展配置（读取 auth_header / auth_prefix）
         * @return 鉴权参数
         */
        static UpstreamAuth of(String authScheme, Map<String, Object> extra) {
            return new UpstreamAuth(authScheme, extraStr(extra, "auth_header"), extraStr(extra, "auth_prefix"));
        }

        /**
         * 从请求体构建（匿名预拉取 / 测试连接场景）。
         *
         * @param body 请求体
         * @return 鉴权参数
         */
        static UpstreamAuth ofBody(Map<String, Object> body) {
            if (body == null) {
                return new UpstreamAuth(null, null, null);
            }
            return new UpstreamAuth(extraStr(body, "auth_scheme"),
                    extraStr(body, "auth_header"), extraStr(body, "auth_prefix"));
        }

        /**
         * 读取扩展配置中的字符串值。
         *
         * @param extra 扩展配置
         * @param key   键名
         * @return 字符串值；缺失返回 null
         */
        private static String extraStr(Map<String, Object> extra, String key) {
            if (extra == null) {
                return null;
            }
            Object value = extra.get(key);
            return value == null ? null : value.toString();
        }
    }

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
     * @param auth        鉴权参数（方案 + 头名 + 前缀）
     * @param proxy       HTTP 代理地址（可为空）
     * @param tryFix      是否在 404 时尝试补全 /v1 后重试
     * @return 上游模型列表响应（models + 可选 fixed_基础_url）
     */
    private Map<String, Object> fetchModelsFromUpstream(String platform, String baseUrl,
                                                        Map<String, Object> credentials,
                                                        UpstreamAuth auth, String proxy, boolean tryFix,
                                                        Map<String, Object> extra, String providerId) {
        // 平台标识统一小写后再判断协议族（前端可能传大写形式）
        String platformLower = platform == null ? "" : platform.toLowerCase();
        // 本地推理（ONNX）没有远端模型目录可拉，改为分发给本地模型门面（ModelRegistry），
        // 由门面返回已注册且已就绪的模型 id 清单 —— 返回形态与其它供应商的「从提供商获取」一致。
        // 注意：ONNX 的基础地址与凭证与其余供应商同字段同语义，地址指向本机，
        // 不再把凭证当作模型文件路径使用。
        if (platformLower.contains("onnx")) {
            return fetchLocalModelsFromFacade();
        }
        // 免费渠道是纯路由聚合：模型清单 = 运行时同一份合并路由表（静态 extra.routes +
        // 动态渠道管理里的 OAuth 渠道模型 + Magpie 网关），**绝不真实请求占位 baseUrl 的 /models**。
        // 之前只读 extra.routes，导致「渠道管理里明明有模型，FreeChannel 拉取却看不到」。
        if (ProviderRouteTargets.isFreeChannel(platform)) {
            return fetchFreeChannelRoutes(providerId, extra);
        }
        // 基础地址缺失直接判参数错误
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "base_url 未配置，无法拉取模型列表");
        }
        String apiKey = extractApiKey(credentials);
        boolean googleProtocol = platformLower.contains("gemini") || platformLower.contains("google");
        boolean ollamaProtocol = platformLower.contains("ollama");

        // 首次按原始地址拉取
        List<Map<String, Object>> models = requestUpstreamModels(baseUrl, apiKey, googleProtocol, proxy, auth);
        String fixedBaseUrl = null;
        // 404 且允许修正时，尝试补全 /v1 再拉一次（用户常见漏填 /v1 的场景）
        if (models == null && tryFix && !googleProtocol) {
            String fixed = baseUrl.endsWith("/") ? baseUrl + "v1" : baseUrl + "/v1";
            models = requestUpstreamModels(fixed, apiKey, false, proxy, auth);
            if (models != null) {
                fixedBaseUrl = fixed;
            }
        }
        // 兼容回退使用原生标签接口，响应中包含模型名称列表
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
     * 免费渠道（free-channel）拉模型：返回路由表 {@code extra.routes} 的模型/前缀键清单。
     *
     * <p>免费渠道是纯路由聚合，没有真实上游目录可拉；模型清单即路由键（如
     * {@code glm-4.7}、{@code kilo}），用户选中哪个键，请求就路由到对应的
     * baseUrl —— 对应 {@code FreeChannelChatClient.route(model)} 的命中键。</p>
     *
     * <p>{@code default} 键是兜底路由，不是可选择的模型，返回时剔除。
     * 未配置 {@code routes} 时返回空清单 + 提示，不抛错。</p>
     *
     * <p>每行补充路由表节点提供的上游信息（provider / baseUrl / authScheme）；
     * 语义交给前端的 {@code describeFetchRow} 渲染成行内小字，让用户看到这一行
     * 实际走哪个上游通道，而不是只看到裸路由键。</p>
     *
     * @param extra 供应商扩展配置（{@code extra.routes}）
     * @return { models: [...], hint?: 字符串 }；形态与其它供应商拉取一致
     */
    private Map<String, Object> fetchFreeChannelRoutes(String providerId, Map<String, Object> extra) {
        Map<String, RouteTarget> routes = ProviderRouteTargets.fromExtra(extra);
        // 与运行时同源：FreeChannelChatClient 用的就是 ModelClientResolver#mergedRoutes
        // （静态 extra.routes + 从渠道管理动态收集的 OAuth 渠道路由 + Magpie）。
        // 拉取列表若只读 extra.routes，就会出现「渠道管理里 82 个模型、这里只看到 2 个」。
        if (providerId != null && !providerId.isBlank()) {
            try {
                SysAiProvider channelProvider = requireProvider(providerId);
                routes = modelClientResolver.mergedRoutes(
                        channelProvider, com.chua.starter.ai.support.chat.ModelClientResolver.ownerScopeOf(channelProvider));
            } catch (Exception e) {
                // 必须 WARN：退回 extra.routes 只会给出静态那几条路由（实测 2 条），
                // 用户在界面上看到的就是「渠道里明明有 80+ 个模型，这里只拉到 2 个」。
                // 之前打 DEBUG，日志被 INFO 级别吞掉，排查时只能看到结果不对劲，
                // 看不到「合并路由失败」这个真正原因。
                log.warn("[Provider] 合并渠道路由失败，已退回 extra.routes（模型清单会明显变少）: {}",
                        e.toString());
            }
        }
        List<Map<String, Object>> models = new ArrayList<>(8);
        if (routes != null) {
            // 渠道前缀键（平台名，如 OPENCODE / KILO）也是路由键，但它只是「该渠道任意模型」
            // 的前缀兜底，不是可选模型；混在模型列表里会让人以为有个叫 OPENCODE 的模型。
            java.util.Set<String> channelPlatformKeys = new java.util.HashSet<>();
            try {
                for (SysAiProvider p : providerMapper.selectList(
                        new LambdaQueryWrapper<SysAiProvider>().eq(SysAiProvider::getAuthScheme, "oauth"))) {
                    if (p.getPlatform() != null && !p.getPlatform().isBlank()) {
                        channelPlatformKeys.add(p.getPlatform().trim().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            } catch (Exception e) {
                log.debug("[Provider] 读取渠道平台键失败（不影响模型列表）: {}", e.getMessage());
            }
            // 静态 extra.routes 里显式写下的键一律保留：它们可能正好与渠道平台同名
            // （例如 kilo 渠道 + extra.routes 里的 kilo 路由），那是管理员明确要暴露的入口。
            // 注意必须按**原样大小写**判断：平台键是 KILO，静态键是 kilo，两者不同名。
            Map<String, RouteTarget> staticRoutes = ProviderRouteTargets.fromExtra(extra);
            for (Map.Entry<String, RouteTarget> entry : routes.entrySet()) {
                String key = entry.getKey();
                // default 是兜底路由，不作为可选模型暴露
                if ("default".equalsIgnoreCase(key)) {
                    continue;
                }
                // 渠道前缀键同样跳过，但静态路由显式声明的不动（见 staticRoutes 注释）
                String lowerKey = key.trim().toLowerCase(java.util.Locale.ROOT);
                if (channelPlatformKeys.contains(lowerKey) && (staticRoutes == null || !staticRoutes.containsKey(key))) {
                    continue;
                }
                RouteTarget target = entry.getValue();
                Map<String, Object> m = new LinkedHashMap<>(7);
                // 路由表节点信息：这一行实际路由到的上游提供商（后端状态色、
                // 前端通道徽标都靠它，不是裸路由键）
                m.put("id", key);
                m.put("name", key);
                m.put("provider", target.getProvider());
                m.put("baseUrl", target.getBaseUrl());
                m.put("authScheme", target.getAuthScheme());
                m.put("tasks", inferTasks(key));
                m.put("traits", List.of("streaming"));
                m.put("description", describeRouteTarget(key, target));
                models.add(m);
            }
        }
        // Magpie 网关的模型目录由网关自己维护、不落库：拉取时并入当前可用模型。
        // 与路由合并口径一致——已由 extra.routes 显式登记的 id 不重复添加。
        java.util.Set<String> seenIds = new HashSet<>();
        for (Map<String, Object> m : models) {
            seenIds.add(String.valueOf(m.get("id")));
        }
        try {
            for (Map<String, Object> row : magpieRouteSource.modelRows("_free")) {
                String id = String.valueOf(row.get("id"));
                if (!seenIds.add(id)) {
                    continue;
                }
                Object upstream = row.get("provider");
                Map<String, Object> m = new LinkedHashMap<>(7);
                m.put("id", id);
                m.put("name", row.get("name"));
                m.put("provider", MagpieRouteSource.CHANNEL);
                m.put("baseUrl", row.get("baseUrl"));
                m.put("authScheme", "api_key");
                m.put("tasks", inferTasks(id));
                m.put("traits", List.of("streaming"));
                m.put("description", "Magpie 网关"
                        + (upstream == null ? "" : " · 上游 " + upstream)
                        + (row.get("baseUrl") == null ? "" : " · " + row.get("baseUrl")));
                models.add(m);
            }
        } catch (Exception e) {
            log.debug("[Provider] 并入 Magpie 网关模型失败: {}", e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("models", models);
        out.put("route_keys", true);
        if (models.isEmpty()) {
            out.put("hint", "未配置模型路由（extra.routes），请在供应商配置中补充后重试");
        }
        return out;
    }

    /**
     * 免费渠道行内描述：这条路由通往的上游 provider 与地址。
     *
     * <p>文本直接落到模型行的 {@code description} 字段，前端 {@code describeFetchRow}
     * 取到它后在模型名下方渲染一行小字；没有描述信息时返回空串，前端不渲染。</p>
     *
     * @param key    路由键（线程缺失 provider 时的兜底标识）
     * @param target 路由目标
     * @return 描述文本（provider · baseUrl，鉴权方案存在时一并给出）
     */
    private String describeRouteTarget(String key, RouteTarget target) {
        if (target == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(target.getProvider() != null ? target.getProvider() : key);
        String baseUrl = target.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            sb.append(" · ").append(baseUrl);
        }
        String scheme = target.getAuthScheme();
        if (scheme != null && !scheme.isBlank() && !"none".equalsIgnoreCase(scheme)) {
            sb.append(" · ").append(scheme);
        }
        return sb.toString();
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
     * @param auth           鉴权参数（方案 + 头名 + 前缀）
     * @return 模型条目列表；请求失败返回 空（由调用方决定是否重试/报错）
     */
    private List<Map<String, Object>> requestUpstreamModels(String baseUrl, String apiKey, boolean googleProtocol,
                                                           String proxy, UpstreamAuth auth) {
        // 地址为空时必须在拼 URL 之前拦住：原实现的 baseUrl.endsWith(...) 会直接 NPE，
        // 异常 message 为 null，最终在界面上显示成「无法连接供应商模型接口: null」。
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "base_url 未配置，无法拉取模型列表");
        }
        baseUrl = baseUrl.trim();
        // 凭证里的 Key 可能带首尾空白（复制粘贴 / 多行输入），先归一化再发请求
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }
        // 拼接模型列表地址，兼容接口使用标准路径，生成式接口附带密钥参数
        StringBuilder url = new StringBuilder(baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models");
        if (googleProtocol && apiKey != null && !apiKey.isBlank()) {
            url.append(url.indexOf("?") >= 0 ? "&" : "?").append("key=").append(apiKey);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(UPSTREAM_TIMEOUT)
                .GET();
    // 按供应商配置的认证方案发送认证头。
        // 历史实现恒发 Authorization: Bearer，导致 Dots Studio 这类只认 api-key 头的
        // 服务商拉取模型必然 401；auth_scheme / extra.auth_header 此前被完全忽略。
        if (!googleProtocol) {
            Map.Entry<String, String> resolved = auth == null ? null
                    : AuthHeaders.resolve(auth.scheme(), auth.header(), auth.prefix(), apiKey);
            if (resolved != null) {
                builder.header(resolved.getKey(), resolved.getValue());
            }
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
                // 返回空结果，由调用方决定是否尝试修复后重试
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
            // 网络层异常统一按网关错误处理。
            // e.getMessage() 可能为 null（如 NPE），不能拼进字符串，否则界面出现
            // 「无法连接供应商模型接口: null」这种无信息量的报错。
            String reason = e.getMessage();
            throw new ApiException(ApiErrorCode.BAD_GATEWAY,
                    "无法连接供应商模型接口" + (reason == null || reason.isBlank() ? "（网络异常）" : ": " + reason));
        }
    }

    /**
     * 解析 打开AI 兼容的模型列表响应（数据[].标识）。
     *
     * <p>除了 {@code id/name}，上游条目里的上下文窗口、输出上限、价格、能力开关、
     * 输入输出模态等元数据也一并解析（见 {@link #upstreamModelRow}）——
     * 这正是「模型元数据必须来自真实 {@code /models} 响应」的入口，
     * 不能用模型名猜。</p>
     *
     * @param body 响应体 JSON
     * @return 模型条目列表
     */
    private List<Map<String, Object>> parseOpenAiModels(String body) {
        List<Map<String, Object>> models = new ArrayList<>(8); // 模型数量不可预估
        com.fasterxml.jackson.databind.JsonNode root = readJson(body);
        // 响应格式为数据数组，每个元素包含模型标识等字段。
        // 少数网关直接回裸数组或把列表放在 models 键下，这里一并兼容，
        // 避免「Key 有效但列表为空」被误判。
        com.fasterxml.jackson.databind.JsonNode data = root == null ? null
                : (root.isArray() ? root : root.get("data"));
        if (data == null && root != null) {
            data = root.get("models");
        }
        if (data != null && data.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : data) {
                String id = item.path("id").asText(null);
            // 跳过缺少模型标识的条目
                if (id == null || id.isBlank()) {
                    continue;
                }
                models.add(upstreamModelRow(id, item.path("name").asText(id), item));
            }
        }
        return models;
    }

    /**
     * 解析 Google Gemini 的模型列表响应（模型[].名称，形如 模型/gemini-xx）。
     *
     * <p>Gemini 的条目用 {@code inputTokenLimit} / {@code outputTokenLimit} 表达窗口与
     * 输出上限（已由统一解析口径识别），这里只补 Gemini 特有的
     * {@code temperature} 能力与 {@code description / version}。</p>
     *
     * @param body 响应体 JSON
     * @return 模型条目列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseGoogleModels(String body) {
        List<Map<String, Object>> models = new ArrayList<>(8); // 模型数量不可预估
        com.fasterxml.jackson.databind.JsonNode root = readJson(body);
        com.fasterxml.jackson.databind.JsonNode data = root == null ? null : root.get("models");
        if (data != null && data.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : data) {
                String name = item.path("name").asText(null);
            // 去掉模型路径前缀作为模型标识
                String id = name != null && name.startsWith("models/") ? name.substring("models/".length()) : name;
                if (id == null || id.isBlank()) {
                    continue;
                }
                String displayName = item.path("displayName").asText(id);
                Map<String, Object> m = upstreamModelRow(id, displayName, item);
                String description = item.path("description").asText(null);
                if (description != null && !description.isBlank()) {
                    m.put("description", description);
                }
                Map<String, Object> extra = new LinkedHashMap<>(4);
                // temperature 字段存在即表示支持采样温度（默认 1.0），不是「取值」
                if (item.has("temperature") && item.get("temperature").isNumber()) {
                    extra.put("temperature", true);
                }
                Long version = longOrNull(item, "version");
                if (version != null && version > 0) {
                    extra.put("created", version);
                }
                if (!extra.isEmpty()) {
                    Map<String, Object> merged = m.get("metrics") instanceof Map<?, ?> raw
                            ? new LinkedHashMap<>((Map<String, Object>) raw)
                            : new LinkedHashMap<>(4);
                    merged.putAll(extra);
                    m.put("metrics", merged);
                    applyUpstreamMetrics(m, merged);
                }
                models.add(m);
            }
        }
        return models;
    }

    /**
     * 上游模型条目 → 「从提供商获取」列表行（含可落库的指标元数据）。
     *
     * <p>上游 {@code /models} 的元数据字段各家差异很大（OpenRouter 的
     * {@code pricing} / {@code limit}、intern-ai schema 2.4 的模态数组、
     * Pollinations 的 {@code supported_parameters} 等），统一交给
     * {@link HttpChatClientIndicator#parseModelMetrics} 归一成标准指标键，
     * 再推导出模型弹框与批量录入认识的实体列键。</p>
     *
     * <p><b>只放上游真实给过的值</b>：解析不到的键不写入，前端按「未知」处理。
     * 后续指标库快照只在字段仍为空时兜底，不会覆盖上游真实元数据。</p>
     *
     * @param id   模型标识
     * @param name 模型展示名
     * @param item 上游条目 JSON
     * @return 列表行
     */
    private Map<String, Object> upstreamModelRow(String id, String name,
                                                 com.fasterxml.jackson.databind.JsonNode item) {
        Map<String, Object> m = new LinkedHashMap<>(12);
        m.put("id", id);
        m.put("name", name);
        m.put("tasks", inferTasks(id));
        m.put("traits", List.of("streaming"));
        Map<String, Object> metrics = HttpChatClientIndicator.parseModelMetrics(item);
        if (!metrics.isEmpty()) {
            m.put("metrics", metrics);
            applyUpstreamMetrics(m, metrics);
        }
        return m;
    }

    /**
     * 标准指标键 → 模型实体列键（字段名与「保存提供者模型」接口完全一致）。
     *
     * <p>映射口径与 {@link SysAiModelMetricsService#enrichRowFromMetrics} 相同，
     * 提前做一次是为了让「从提供商获取」列表与批量录入拿到同一份元数据，
     * 避免列表里看得到、落库后却丢了。</p>
     *
     * @param row     列表行（就地写入）
     * @param metrics 标准指标
     */
    private void applyUpstreamMetrics(Map<String, Object> row, Map<String, Object> metrics) {
        // 免费渠道 / 路由聚合行的「模型」是路由键，没有上游指标；调用方会传 null，
        // 这里直接跳过，否则 NPE 会把整个 remote_models 打成 500（实测）。
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        putIfPresent(row, "context_window", metrics.get("context_window_tokens"));
        putIfPresent(row, "max_tokens", metrics.get("max_output_tokens"));
        putIfPresent(row, "family", metrics.get("model_family"));
        putIfPresent(row, "knowledge", metrics.get("knowledge_cutoff"));
        putIfPresent(row, "release_date", metrics.get("release_date"));
        putIfPresent(row, "last_updated", metrics.get("last_updated"));
        putIfPresent(row, "open_weights", metrics.get("open_weights"));
        putIfPresent(row, "attachment", metrics.get("attachment"));
        putIfPresent(row, "reasoning", metrics.get("reasoning"));
        putIfPresent(row, "tool_call", metrics.get("function_calling"));
        putIfPresent(row, "temperature_supported", metrics.get("temperature"));
        // 价格：上游 /models 的 pricing 已由 parseModelMetrics 归一成「每百万 token 美元」。
        // 提到行顶层是为了让「从提供商获取」列表能直接标注价格/免费，
        // 落库时仍走 metrics 同一份数据（见 mergeUpstreamMetadata）。
        putIfPresent(row, "price_input_per_musd", metrics.get("price_input_per_musd"));
        putIfPresent(row, "price_output_per_musd", metrics.get("price_output_per_musd"));
        putIfPresent(row, "price_cache_hit_per_musd", metrics.get("price_cache_hit_per_musd"));
        putIfPresent(row, "price_cache_write_per_musd", metrics.get("price_cache_write_per_musd"));
        putIfPresent(row, "cost_per_task", metrics.get("cost_per_task"));
        Map<String, Object> modalities = modalityMapOf(metrics);
        if (!modalities.isEmpty()) {
            row.put("modalities", modalities);
        }
    }

    /**
     * 由标准指标组装 {@code {input:[...], output:[...]}} 模态结构。
     *
     * <p>优先使用上游显式声明的 {@code input_modalities} / {@code output_modalities}；
     * 输入侧缺失时按 image / audio / video / file 能力标记补齐。元素一定是字符串，
     * 与前端 {@code modalities} 契约一致。</p>
     *
     * @param metrics 标准指标
     * @return 模态 Map；两侧都无信息时返回空 Map
     */
    private static Map<String, Object> modalityMapOf(Map<String, Object> metrics) {
        Map<String, Object> result = new LinkedHashMap<>(4);
        List<String> inputs = stringValues(metrics.get("input_modalities"));
        if (inputs.isEmpty()) {
            inputs = new ArrayList<>(4);
            inputs.add("text");
            for (String[] pair : new String[][]{{"image_input", "image"}, {"audio_input", "audio"},
                    {"video_input", "video"}, {"file_input", "file"}}) {
                if (Boolean.TRUE.equals(metrics.get(pair[0]))) {
                    inputs.add(pair[1]);
                }
            }
        }
        if (!inputs.isEmpty()) {
            result.put("input", inputs);
        }
        List<String> outputs = stringValues(metrics.get("output_modalities"));
        if (!outputs.isEmpty()) {
            result.put("output", outputs);
        }
        return result;
    }

    /**
     * 宽松转字符串列表（字符串元素 / {@code {name}} 对象都接受）。
     *
     * @param value 原始值
     * @return 字符串列表；无法识别时返回空列表
     */
    private static List<String> stringValues(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof String text) {
                if (!text.isBlank()) {
                    values.add(text);
                }
            } else if (item instanceof Map<?, ?> map) {
                Object name = map.get("name") != null ? map.get("name") : map.get("type");
                if (name != null) {
                    values.add(String.valueOf(name));
                }
            }
        }
        return values;
    }

    /**
     * 值非空时写入行。
     *
     * @param row   目标行
     * @param key   键
     * @param value 值
     */
    private static void putIfPresent(Map<String, Object> row, String key, Object value) {
        if (value != null) {
            row.put(key, value);
        }
    }

    /**
     * 读取 Long 字段（缺失或非数值返回 null）。
     *
     * @param node 节点
     * @param key  字段名
     * @return 数值
     */
    private static Long longOrNull(com.fasterxml.jackson.databind.JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
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
        if (lower.contains("whisper") || lower.contains("asr") || lower.contains("speech-recognition")
                || lower.contains("transcribe") || lower.contains("transcription")) {
            return List.of("speech_recognition");
        }
        // 图像/视频：除 dall-e/cogview 这类专名，还要认裸 token。
        // 「厂商-类型-版本」命名（agnes-image-2.1-flash / agnes-video-2.5）原先只匹配
        // image-gen/video-gen，全部回落成 chat —— 人工建模时是手选类型，批量录入没
        // 这个机会，模型选择器就会把图像/视频模型分进「文本」组。
        // 代价：视频理解类对话模型（video-llava 之类）会被误判成生成类，罕见且只影响分组。
        if (lower.contains("image") || lower.contains("dall-e") || lower.contains("cogview")
                || lower.contains("flux") || lower.contains("sdxl")) {
            return List.of("image_generation");
        }
        if (lower.contains("video")) {
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
        // 没有凭证时直接返回空结果
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        Object single = credentials.get("api_key");
        if (single instanceof String && !((String) single).isBlank()) {
            return (String) single;
        }
        Object multi = credentials.get("api_keys");
        // 多密钥数组取第一个值作为拉取凭证
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
     * ONNX 供应商或其模型行变更后，刷新 provider 级推理设备覆盖。
     *
     * @param provider 供应商行
     */
    private void refreshOnnxDevice(SysAiProvider provider) {
        if (isOnnxRow(provider)) {
            onnxProviderMaterializer.syncProviderDevice();
        }
    }

    /**
     * 获取 提供者 实体，并对非管理员隐藏 ONNX 行（不存在语义，不泄露该行存在）。
     *
     * <p>供所有「读」路径使用：详情、连接列表、连接详情。写路径本身已是 admin-only，
     * 无需再判断；此处只负责让非管理员读不到 ONNX 那一行。</p>
     *
     * @param providerId 提供者 标识
     * @return 提供者实体
     */
    private SysAiProvider requireVisibleProvider(String providerId) {
        SysAiProvider e = requireProvider(providerId);
        if (isOnnxRow(e) && !com.chua.starter.ai.support.util.AiUserContext.isAdmin()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider 不存在: " + providerId);
        }
        return e;
    }

    /**
     * 给 connection 查询施加账号隔离条件。
     *
     * <p><b>为什么必须有这一层：</b>{@code sys_ai_provider} 是全局表（没有 user_id 列，
     * 供应商模板与模型目录对所有账号同构），但挂在它下面的 {@code sys_ai_provider_connection}
     * <b>有</b> {@code sys_ai_provider_connection_user_id} —— 账号凭证属于绑定它的那个人。
     * 本控制器此前所有 connection 查询都只按 provider_id 过滤，等于把「别人绑的账号」
     * 一并回显（connection_id / label / status / has_credentials）。渠道管理侧
     * {@code ApiChannelAuthController.applyOwnerFilter} 早已这么做，这里漏了。</p>
     *
     * <p>口径与渠道管理保持一致（{@code ApiChannelAuthController.applyOwnerFilter}）：</p>
     * <ul>
     *   <li>普通用户：只看得到 {@code user_id = 自己} 的连接；</li>
     *   <li>管理员：额外可见 {@code user_id IS NULL} 的系统池行（渠道管理改造前的绑定，
     *       免费渠道路由 {@code _free} 也取这一批）；</li>
     *   <li>无身份（AppKey 机器流量 / 后台线程）：只放系统池。这里不能直接抛 401 ——
     *       {@code GET /api/providers} 同时服务机器调用，改造前对它们可用；且「无主」
     *       只能被当作最窄的作用域，绝不能当成「全见」。</li>
     * </ul>
     *
     * @param wrapper 连接查询包装器
     */
    private void applyConnectionOwnerFilter(LambdaQueryWrapper<SysAiProviderConnection> wrapper) {
        String owner = com.chua.starter.ai.support.util.AiUserContext.currentUserId();
        if (owner == null) {
            wrapper.isNull(SysAiProviderConnection::getUserId);
            return;
        }
        boolean admin = com.chua.starter.ai.support.util.AiUserContext.isAdmin();
        wrapper.and(w -> {
            w.eq(SysAiProviderConnection::getUserId, owner);
            if (admin) {
                w.or().isNull(SysAiProviderConnection::getUserId);
            }
        });
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
     * boolOrNull：可空的布尔（前端未传时保持 null，不覆盖指标回填值）。
     * @param map 映射
     * @param key Key
     * @return Boolean 或 null
     */
    private Boolean boolOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v);
        if (s.isBlank() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return Boolean.parseBoolean(s);
    }

    /**
     * toModalitiesMap：归一化 inputs。 input / output 模态为字符串列表的 Map。
     *
     * <p>接受 {@code {input:[...], output:[...]}} 结构或 null；字符串值视为
     * 逗号分隔列表。返回值可变，便于后续写入实体。</p>
     *
     * @param value 原始值
     * @return 归一化的 Map；无有效内容时返回空 Map
     */
    private Map<String, Object> toModalitiesMap(Object value) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(2);
        List<String> reduced = new java.util.ArrayList<>(6);
        if (value instanceof Map<?, ?> raw) {
            Object in = raw.get("input");
            reduced.clear();
            collectModalityItems(in, reduced);
            if (!reduced.isEmpty()) {
                result.put("input", reduced);
            }
            Object out = raw.get("output");
            reduced = new java.util.ArrayList<>(6);
            collectModalityItems(out, reduced);
            if (!reduced.isEmpty()) {
                result.put("output", reduced);
            }
        }
        return result;
    }

    /**
     * collectModalityItems：把模态字段值规整成字符串列表（字符串按逗号拆分）。
     * @param value 原始值
     * @param target 收集目标
     */
    private void collectModalityItems(Object value, List<String> target) {
        if (value instanceof java.util.Collection<?> coll) {
            for (Object item : coll) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    target.add(String.valueOf(item).trim());
                }
            }
        } else if (value != null && !String.valueOf(value).isBlank()) {
            for (String part : String.valueOf(value).split(",")) {
                if (!part.isBlank()) {
                    target.add(part.trim());
                }
            }
        }
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

    /**
     * 取可空整数值（缺省 / 空串 / 非数字一律返回 null）。
     *
     * <p>用于「未指定即回退指标表」的窗口类字段：null 与 0 语义不同，
     * 不能用 {@link #intv(Map, String, int)} 的默认值表达。</p>
     *
     * @param map 映射
     * @param key Key
     * @return 解析结果，无法解析时为 null
     */
    private Integer intOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 取可空小数值（缺省 / 空串 / 非数字一律返回 null）。
     *
     * @param map 映射
     * @param key Key
     * @return 解析结果，无法解析时为 null
     */
    private Double dblOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
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
     * canonical Dir
     * @param raw 字符串
     * @return 返回值
    */
    private static String canonicalDir(String raw) {
        return com.chua.starter.ai.support.manage.service.OnnxExternalDirectoryService.canonicalDir(raw);
    }

        /**
     * 通过本地模型门面拉取 ONNX 模型清单。
     *
     * <p>ONNX 提供商的"凭证"是本地模型路径而非远程 Key，没有可拉取的远程目录，
     * 因此不能走 HTTP 拉取；改为按平台类型分发给本地模型门面
     * （{@code AiModelCatalogService}，底层为 {@code ModelRegistry} SPI 注册表），
     * 与其它提供商的「从提供商获取」体验保持一致。</p>
     *
     * <p>只返回用户可见范围内的模型，避免把整个注册表（上百个未下载模型）全列出来：</p>
     * <ul>
     *   <li>外部目录模型：仅返回「绑定外部目录」中登记过的路径</li>
     *   <li>内置模型：仅在模型文件已就绪时返回</li>
     * </ul>
     *
     * @return 与上游一致的 models 结构（{@code name}/{@code modelName}）并附带 onnx 专有字段
     */
    private Map<String, Object> fetchLocalModelsFromFacade() {
        List<Map<String, Object>> models = listVisibleLocalModels();
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("models", models);
        return out;
    }

    /**
     * 列出用户可见的本地模型（ONNX 提供商的模型目录）。
     *
     * <p>数据来源统一走本地模型门面 {@code AiModelCatalogService}（底层 {@code ModelRegistry} SPI），
     * 不再自己遍历注册表。门面的 {@code available} 字段由只读探测得出，不会触发模型下载。</p>
     *
     * <p>过滤规则（避免把注册表里上百个未下载模型全部列出）：</p>
     * <ul>
     *   <li>外部目录模型：仅返回「绑定外部目录」中登记过的路径</li>
     *   <li>内置模型：仅在模型文件已就绪（{@code available=true}）时返回</li>
     * </ul>
     *
     * @return 模型条目列表（含 id/name/modelName/capability/external/available）
     */
    private List<Map<String, Object>> listVisibleLocalModels() {
        List<Map<String, Object>> details;
        try {
            details = aiModelCatalogService.listLocalModelDetails();
        } catch (Exception e) {
            log.warn("[ONNX] 本地模型门面返回失败: {}", e.getMessage());
            return List.of();
        }
        if (details == null) {
            return List.of();
        }

        // 已绑定的外部目录：目录 → 任务（按绑定时登记的模板模型归类）
        Set<String> boundDirs = new HashSet<>();
        Map<String, String> externalTaskByDir = new java.util.HashMap<>(8);
        for (Map<String, String> en : onnxExternalDirectoryService.readEntries()) {
            String d = canonicalDir(en.get("dir"));
            if (d != null && !d.isBlank()) {
                boundDirs.add(d);
                String tpl = en.get("template");
                externalTaskByDir.put(d, classifyOnnxTask(tpl == null ? d : tpl));
            }
        }

        List<Map<String, Object>> models = new ArrayList<>();
        // 外部目录模型以「用户通过 ReFileSystem 选中的目录清单」为准：
        // 外部目录在 ModelRegistry 里是独立结构，不会出现在 getAll()，
        // 只靠目录门面过滤会一个都取不到（ONNX 刷新模型返回空）。
        for (Map<String, String> en : onnxExternalDirectoryService.readEntries()) {
            String d = canonicalDir(en.get("dir"));
            if (d == null || d.isBlank()) {
                continue;
            }
            String tpl = en.get("template");
            // id/name/modelName 用注册表模型标识（template），与其它提供商的模型名同构；
            // 目录路径单独放 path，不混进模型名
            String name = tpl == null || tpl.isBlank() ? dirNameOf(d) : tpl.trim();
            String task = classifyOnnxTask(name);
            Map<String, Object> item = new LinkedHashMap<>(9);
            item.put("id", name);
            item.put("name", name);
            item.put("modelName", name);
            item.put("path", d);
            item.put("capability", null);
            item.put("external", true);
            item.put("available", true);
            item.put("task", task);
            item.put("description", "外部目录 · " + d + " · " + onnxTaskLabel(task));
            models.add(item);
        }
        // 再补上本地已就绪的内置模型（available=true），避免把上百个未下载候选全列出来
        for (Map<String, Object> detail : details) {
            Object modelId = detail.get("modelId");
            if (modelId == null || String.valueOf(modelId).isBlank()) {
                continue;
            }
            String id = String.valueOf(modelId);
            if (models.stream().anyMatch(m -> id.equals(m.get("id")))) {
                continue;
            }
            // 外部目录在注册表里是以「绝对路径」为 key 注册的独立条目，会被模型门面
            // 当作内置模型一并返回；这些模型上面已按模板模型标识登记过，若不去重，
            // 同一个模型会以目录路径的形态重复出现（「从提供商获取」列表里混进路径）。
            if (boundDirs.contains(canonicalDir(id)) || isAbsolutePathLike(id)) {
                continue;
            }
            if (!Boolean.TRUE.equals(detail.get("available"))) {
                continue;
            }
            String task = classifyOnnxTask(id);
            Map<String, Object> item = new LinkedHashMap<>(12);
            item.put("id", id);
            item.put("name", id);
            item.put("modelName", id);
            item.put("path", null);
            item.put("capability", detail.get("capability"));
            item.put("external", false);
            item.put("available", true);
            item.put("task", task);
            // 批量录入（import-all）读的是 tasks/subtype：不带上这两个字段，
            // ONNX 模型入库后会没有 capabilities，前端只能按名字兜底成「文本」。
            item.put("tasks", List.of(task));
            String subType = OnnxModelTaxonomy.subtype(id, task);
            if (!subType.isBlank()) {
                item.put("subtype", subType);
            }
            item.put("description", "ONNX 本地模型 · " + onnxTaskLabel(task));
            models.add(item);
        }
        return models;
    }

    /**
     * id Contains
     * @param id 字符串
     * @param tokens 参数
     * @return 返回值
    */
    private static boolean idContains(String id, String... tokens) {
        for (String t : tokens) {
            if (id.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断模型标识是否形态上是一个绝对文件系统路径。
     *
     * <p>正常的模型标识是「模板模型名」（如 {@code bge-large-zh-embedding}），
     * 不应以路径出现；注册表里的外部目录条目则以绝对路径为 key，需要据此识别并过滤。</p>
     *
     * @param id 模型标识候选值
     * @return true 表示形如 {@code /xxx}（Unix）或 {@code X:/xxx} / {@code X:\xxx}（Windows）
     */
    /**
     * 判断某个 ONNX 模型名是否由「绑定外部目录」登记而来。
     *
     * <p>外部目录绑定时会把「模板模型名」写进条目（{@code template} 字段），
     * 所以拿模型名去比对 template 即可 —— 目录路径本身不是模型名，不参与比对。</p>
     *
     * <p>为什么需要：设置页模型选择器在 onnx 分支按 {@code external === true} 过滤，
     * 而 {@code /api/providers} 原先不返回该键，导致所有 ONNX 模型都选不到。</p>
     *
     * @param modelName 模型名（如 {@code image-colorize}）
     * @return true 表示由外部目录登记
     */
    private boolean isExternalBoundModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        try {
            for (Map<String, String> en : onnxExternalDirectoryService.readEntries()) {
                String tpl = en.get("template");
                if (tpl != null && !tpl.isBlank() && tpl.trim().equals(modelName.trim())) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 外部目录读取失败不能让整个 providers 列表挂掉，按「内置模型」处理
            log.debug("[ONNX] 读取外部目录失败，按内置处理: {}", e.getMessage());
        }
        return false;
    }

    /**
     * is Absolute Path Like
     * @param id 字符串
     * @return 返回值
    */
    private static boolean isAbsolutePathLike(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String v = id.trim();
        if (v.startsWith("/") || v.startsWith("\\\\")) {
            return true;
        }
        return v.length() >= 3
                && Character.isLetter(v.charAt(0))
                && v.charAt(1) == ':'
                && (v.charAt(2) == '/' || v.charAt(2) == '\\');
    }

    /**
     * 判断是否为本地推理供应商（ONNX）。
     *
     * <p>ONNX 与其它供应商同构，供应商行就落在 {@code sys_ai_provider} 表里；
     * 但「从提供商获取」这一步不同：其它供应商是向远端地址发 HTTP 拉取模型目录，
     * ONNX 没有远端目录，只能由本地模型门面（{@code ModelRegistry}）列出已注册的模型。
     * 因此模型拉取分支要绕开上游 HTTP，直接走门面 —— 返回的仍然是模型 id，与其它供应商一致。</p>
     *
     * @param providerId 提供商标识
     * @return true=本地推理供应商
     */
    private static boolean isVirtualLocalProvider(String providerId) {
        return isOnnxProvider(providerId);
    }

    /**
     * 按模型名约定把 ONNX 注册表模型归类到唯一规范 capability.task。 顺序敏感：布局 → OCR → 人脸 → 语音 → 嵌入 → 重排序 → 生成类 → 其余归图片识别。
     * @param modelId 字符串
     * @return 返回值
    */
    private static String classifyOnnxTask(String modelId) {
        // 统一走 OnnxModelTaxonomy：修复了旧实现里 "age" 命中 "image" 把图像编码器
        // 误判成人脸的问题，并与物化器 / 前端使用同一套词表。
        return OnnxModelTaxonomy.task(modelId);
    }

    /**
     * onnx Traits For Task
     * @param task 字符串
     * @return 返回值
    */
    private static List<String> onnxTraitsForTask(String task) {
        switch (task) {
            case "embedding": return List.of("input:text", "output:vector");
            case "speech_recognition": return List.of("input:audio", "output:text");
            case "speech_synthesis": return List.of("input:text", "output:audio");
            case "image_generation": return List.of("input:text", "output:image");
            case "video_generation": return List.of("input:text", "output:video");
            case "face_recognition":
            case "ocr":
            case "layout":
            case "image_cv":
            default: return List.of("input:image", "output:text");
        }
    }

    /**
     * 取目录名（未登记模板时作为模型名的回退）。
     *
     * @param dir 模型目录
     * @return 目录名
     */
    private static String dirNameOf(String dir) {
        int slash = dir.lastIndexOf('/');
        return slash < 0 || slash == dir.length() - 1 ? dir : dir.substring(slash + 1);
    }

    /**
     * onnx Task Label
     * @param task 字符串
     * @return 返回值
    */
    private static String onnxTaskLabel(String task) {
        switch (task) {
            case "layout": return "布局识别";
            case "ocr": return "OCR 识别";
            case "face_recognition": return "人脸识别";
            case "speech_recognition": return "语音识别";
            case "speech_synthesis": return "语音合成";
            case "embedding": return "特征嵌入";
            case "rerank": return "重排序";
            case "image_generation": return "文生图";
            case "video_generation": return "文生视频";
            case "image_cv":
            default: return "图片识别";
        }
    }

    // ==================== ONNX 外部服务器目录 ====================

    /**
     * 应用就绪后恢复已持久化的 ONNX 外部目录（重启后自动重新注册）。
     *
     * <p>排序必须最晚：ONNX 供应商物化与模型参数回填在启动阶段也会调用
     * {@code ModelRegistry#discoverAll}，而该调用会把模型根重置为库默认值。
     * 早于它们执行会让刚恢复的模型根被覆盖，目录选择器随即指向不存在的默认目录。</p>
     */
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void restoreOnnxExternalDirectories() {
        onnxExternalDirectoryService.restoreAll();
    }

    /**
     * 浏览服务器目录，供 ONNX 模型目录选择器使用。
     *
     * <p>本接口与后台文件系统接口 {@code GET /api/fs/browse} 同契约、同解析规则，
     * 前端 ReFileSystem 目录选择器直接消费：选择器里能打开的目录，才可能被
     * {@code POST /onnx/external} 登记进来。</p>
     *
     * @param path      目录绝对路径，空值时以当前用户主目录为起点
     * @param showFiles 是否返回文件
     * @return 目录条目与定位信息
     */
    @Permission("admin")
    @GetMapping("/onnx/external/browse")
    public ApiResult<Map<String, Object>> browseOnnxExternal(
            @RequestParam(defaultValue = "") String path,
            @RequestParam(defaultValue = "false") boolean showFiles) {
        try {
            return ApiResult.ok(onnxExternalDirectoryService.browse(path, showFiles));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, ex.getMessage());
        } catch (IOException ex) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "浏览目录失败: " + ex.getMessage());
        }
    }

    /**
     * 添加 ONNX 外部服务器目录模型。
     *
     * @param body {dir: 服务器模型目录绝对路径, template: 模板模型标识（可空）}
     * @return 新增模型信息
     */
    @Permission("admin")
    @PostMapping("/onnx/external")
    public ApiResult<Map<String, Object>> addOnnxExternal(@RequestBody Map<String, Object> body) {
        String dir = body == null ? null : String.valueOf(body.getOrDefault("dir", "")).trim();
        String template = body == null ? null : String.valueOf(body.getOrDefault("template", "")).trim();
        try {
            return ApiResult.ok(onnxExternalDirectoryService.add(dir, template));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, ex.getMessage());
        }
    }

    /**
     * 删除 ONNX 外部服务器目录模型。
     *
     * @param dirParam 服务器模型目录绝对路径（query 参数，DELETE 推荐）
     * @param body     兼容 body 形式 {dir: 服务器模型目录绝对路径}
     * @return 是否移除
     */
    @Permission("admin")
    @DeleteMapping("/onnx/external")
    public ApiResult<Map<String, Object>> removeOnnxExternal(
            @RequestParam(value = "dir", required = false) String dirParam,
            @RequestBody(required = false) Map<String, Object> body) {
        String dir = (dirParam != null && !dirParam.isBlank()) ? dirParam.trim()
                : (body == null ? null : String.valueOf(body.getOrDefault("dir", "")).trim());
        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("removed", onnxExternalDirectoryService.remove(dir));
        return ApiResult.ok(result);
    }

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
