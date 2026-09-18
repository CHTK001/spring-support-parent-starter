package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.manage.SysAiWsTopics;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.service.AiModelMetricsEnrichmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
* 模型价格/指标快照服务。
*
* <p>定时枚举全部已配置供应商与模型，用 {@link AiModelMetricsEnrichmentService}
* （Artificial Analysis 指标源）匹配「输入价格/输出价格/缓存价格/智能/输出速度/首块延迟/
* 深度思考/联网查询/图片识别/函数调用」，合并模型自身的 <code>指标</code> 实时字段
* 与能力健康探测延迟，构建一份快照写入 Redis（钥匙 {@code nomifun:model-metrics:latest}，
* TTL 12h），供查询接口按需读取。不再经 web套接字 定时推送，前端通过
* {@code GET /api/model-metrics} 从缓存获取。</p>
*
* <p>快照行同时输出厂商 Logo（{@code provider_logo}）与模型 Logo（{@code icon_url}，
* 以 Artificial Analysis 图标优先，缺省回退厂商 Logo），供前端渲染真实品牌图。</p>
*
* @since 4.0.0.42
* @author CH
     */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelMetricsSnapshotService {

    /**
    * 平台标识 → Logo 静态资源基名（对应 {@code /api/assets/logos/{base}}）。
    * 与前端 {@code modelPlatforms.ts} 的 模型_PLATFORMS 命名对齐；缺失平台忽略。
    */
    private static final Map<String, String> PLATFORM_LOGO = logoMap();

    /**
    * 由平台标识解析厂商 Logo URL（外部部署时由前端按运行时地址展开）。
    *
    * @param platform 平台标识（区分大小写匹配后统一小写）
    * @return Logo 相对 URL，平台未配置时返回 空
    */
    public static String providerLogoUrl(String platform) {
        if (platform == null) {
            return null;
        }
        String base = PLATFORM_LOGO.get(platform.toLowerCase());
        if (base == null) {
            return null;
        }
        return "/api/assets/logos/" + base;
    }

    /**
    * 构建平台 → Logo 基名映射。
    *
    * @return 不可变映射
    */
    private static Map<String, String> logoMap() {
        Map<String, String> m = new java.util.HashMap<>();
        m.put("openai", "openai");
        m.put("anthropic", "anthropic");
        m.put("google", "gemini");
        m.put("gemini", "gemini");
        m.put("gemini-vertex-ai", "gemini");
        m.put("azure", "microsoft");
        m.put("bedrock", "bedrock");
        m.put("siliconflow", "siliconflow");
        m.put("deepseek", "deepseek");
        m.put("qwen", "qwen");
        m.put("dashscope", "qwen");
        m.put("dashscope-coding", "qwen");
        m.put("moonshot-cn", "kimi");
        m.put("moonshot-global", "kimi");
        m.put("moonshot", "kimi");
        m.put("grok", "xai");
        m.put("xai", "xai");
        m.put("zhipu", "zhipu");
        m.put("glm-coding-plan", "zhipu");
        m.put("ark", "volcengine");
        m.put("ark-coding-plan", "volcengine");
        m.put("ark-agent-plan", "volcengine");
        m.put("qianfan", "baidu");
        m.put("qianfan-coding-plan", "baidu");
        m.put("hunyuan", "tencent");
        m.put("hunyuan-global", "tencent");
        m.put("lingyi", "lingyiwanwu");
        m.put("stepfun", "stepfun");
        m.put("stepfun-plan", "stepfun");
        m.put("minimax", "minimax");
        m.put("minimax-code", "minimax");
        m.put("minimax-coding-plan", "minimax");
        m.put("mimo", "mimo");
        m.put("mimo-token-plan-cn", "mimo");
        m.put("mimo-token-plan-sgp", "mimo");
        m.put("mimo-token-plan-ams", "mimo");
        m.put("novita", "novita");
        m.put("openrouter", "openrouter");
        m.put("poe", "poe");
        m.put("ppio", "ppio");
        m.put("modelscope", "modelscope");
        m.put("infiniai", "infiniai");
        m.put("ctyun", "ctyun");
        m.put("new-api", "newapi");
        m.put("onnx", "onnx");
        m.put("sensenova", "sensenova");
        m.put("agnes", "agnes");
        m.put("opencode", "opencode");
        return Map.copyOf(m);
    }

    /**
    * Redis 快照 钥匙
    */
    static final String REDIS_KEY = "nomifun:model-metrics:latest";

    /**
    * Redis 快照 TTL（12 小时，防止冗余堆积）
    */
    private static final long REDIS_TTL_SECONDS = 12L * 60 * 60;

    /**
    * 提供商 映射器
    */
    private final SysAiProviderMapper providerMapper;

    /**
    * 提供商模型 映射器
    */
    private final SysAiProviderModelMapper modelMapper;

    /**
    * 指标富集服务（Artificial Analysis 价格/速度/延迟匹配）
    */
    private final AiModelMetricsEnrichmentService metricsEnrichmentService;

    /**
    * Redis 模板
    */
    private final StringRedisTemplate stringRedisTemplate;

    /**
    * JSON 工具
    */
    private final ObjectMapper objectMapper;

    /**
    * WS 推送服务
    */
    private final ApiWebSocketPushService pushService;

    /**
    * 构建最新快照并写入 Redis（不推送 WS，查询接口按需读取缓存）。
    *
    * <p>指标缺失不回退失败：该模型行仍会进入快照，价格/速度等字段置 null，
    * 前端展示占位符。Redis 写入失败仅告警。</p>
    *
    * @return 本次快照
    */
    public Map<String, Object> buildAndPushSnapshot() {
        Map<String, Object> snapshot = buildSnapshotInternal();
        /**
        * persist转为Redis。
        * @param snapshot snapshot
        */
        persistToRedis(snapshot);
        return snapshot;
    }

    /**
    * 仅构建快照（不写 Redis、不推送），供查询接口首查/后台任务直接使用。
    *
    * @return 快照 映射
    */
    public Map<String, Object> buildSnapshot() {
        return buildSnapshotInternal();
    }

    /**
    * 立即将某模型的实测探测延迟写入 Redis 快照，替换该模型旧值。
    *
    * <p>由健康检测（监控检测）接口在探测成功后调用：读取现有 Redis 快照，
    * 定位 {@code provider_id + model} 对应的行并更新 {@code latency_ms}，
    * 写回 Redis（保持 TTL）。Redis 无快照或模型行不存在时静默跳过，
    * 不干扰调用方。</p>
    *
    * @param providerId 提供商 标识
    * @param model      模型名
    * @param latencyMs  实测延迟（毫秒）
    */
    public void updateProbeLatency(String providerId, String model, long latencyMs) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (cached == null || cached.isBlank()) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(cached, Map.class);
            Object modelsObj = parsed.get("models");
            if (!(modelsObj instanceof List<?> list)) {
                return;
            }
            boolean updated = false;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                if (providerId.equals(String.valueOf(row.get("provider_id")))
                        && model.equals(String.valueOf(row.get("model")))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rowMap = (Map<String, Object>) row;
                    rowMap.put("latency_ms", latencyMs);
                    rowMap.remove("probe_latency_ms");
                    updated = true;
                    break;
                }
            }
            if (updated) {
                /**
                * persist转为Redis。
                * @param parsed 解析
                */
                persistToRedis(parsed);
                log.debug("[ModelMetrics] 已更新模型探测延迟 provider={} model={} latency={}ms",
                        providerId, model, latencyMs);
            }
        } catch (Exception e) {
            log.warn("[ModelMetrics] 更新探测延迟到 Redis 失败: {}", e.getMessage());
        }
    }

    /**
    * 读取最近一次 Redis 快照；无快照时现场构建一份并返回。
    *
    * @return 快照 映射，构造失败时返回空结构
    */
    public Map<String, Object> latestSnapshot() {
        try {
            String cached = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (cached != null && !cached.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(cached, Map.class);
                if (parsed != null) {
                    return parsed;
                }
            }
        } catch (Exception e) {
            log.warn("[ModelMetrics] 读取 Redis 快照失败，转为现场构建: {}", e.getMessage());
        }
        return buildSnapshotInternal();
    }

    /**
    * 快照构建核心：枚举配置模型并匹配指标。
    *
    * @return 快照 映射
    */
    private Map<String, Object> buildSnapshotInternal() {
        List<Map<String, Object>> models = new ArrayList<>();
        List<SysAiProvider> providers = providerMapper.selectList(new LambdaQueryWrapper<>());
        for (SysAiProvider provider : providers) {
            List<SysAiProviderModel> rows = modelMapper.selectList(
                    new LambdaQueryWrapper<SysAiProviderModel>()
                            .eq(SysAiProviderModel::getProviderId, provider.getProviderId())
                            .orderByAsc(SysAiProviderModel::getSortOrder));
            for (SysAiProviderModel row : rows) {
                models.add(toRow(provider, row));
            }
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("updated_at", Instant.now().toEpochMilli());
        snapshot.put("count", models.size());
        snapshot.put("models", models);
        return snapshot;
    }

    /**
    * 单行聚合：提供者 基本信息 + enrich 静态指标 + 模型实时 指标 + 健康延迟。
    *
    * @param provider 供应商
    * @param row      模型实体
    * @return 聚合行
    */
    private Map<String, Object> toRow(SysAiProvider provider, SysAiProviderModel row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider_id", provider.getProviderId());
        out.put("provider_name", provider.getName());
        out.put("platform", provider.getPlatform());
        out.put("model", row.getModel());
        out.put("enabled", row.getEnabled() == null || row.getEnabled());
        out.put("description", row.getDescription() == null ? "" : row.getDescription());
        out.put("source", "configured");
 // 厂商 Logo（/api/assets/logos/{基础}），供前端渲染真实品牌图
        String providerLogo = providerLogoUrl(provider.getPlatform());
        if (providerLogo != null) {
            out.put("provider_logo", providerLogo);
        }

        // Artificial Analysis 静态指标（输入/输出价、智能、速度、首块延迟）
        metricsEnrichmentService.enrich(row.getModel(), out);

        // 模型 Logo：AA 命中则用其图标，否则回退厂商 Logo
        Object iconUrl = out.get("icon_url");
        if ((iconUrl == null || String.valueOf(iconUrl).isBlank()) && providerLogo != null) {
            out.put("icon_url", providerLogo);
        }

 // 合并模型自身实时 指标（用户维护/探测得到的延迟、智能、速度）
        Map<String, Object> metrics = row.getMetrics();
        if (metrics != null && !metrics.isEmpty()) {
            /**
            * 合并not空。
            * @param out 出
            * @param "latency_ms" "延迟_ms"
            * @param metrics.get("latency_ms" 指标.获取("延迟_ms"
            */
            mergeNotNull(out, "latency_ms", metrics.get("latency_ms"));
            /**
            * 合并not空。
            * @param out 出
            * @param "intelligence" "智能"
            * @param metrics.get("intelligence" 指标.获取("智能"
            */
            mergeNotNull(out, "intelligence", metrics.get("intelligence"));
            /**
            * 合并not空。
            * @param out 出
            * @param "output_speed" "输出_速度"
            * @param metrics.get("output_speed" 指标.获取("输出_速度"
            */
            mergeNotNull(out, "output_speed", metrics.get("output_speed"));
        }

 // 能力健康探测延迟（最近一次 健康.延迟，单位 ms）
 // 直接写入 延迟_ms，替代之前的缓存值，反映本机到端点的实际探测延迟
        Long probeLatencyMs = firstCapabilityLatency(row);
        if (probeLatencyMs != null && probeLatencyMs >= 0) {
            out.put("latency_ms", probeLatencyMs);
        }

 // 缓存价格：指标源无缓存价字段时沿用模型 指标 自定义值，缺失为 空
        Object cache = metrics == null ? null : metrics.get("price_cache_per_musd");
        if (cache == null) {
            cache = metrics == null ? null : metrics.get("cache_price_usd");
        }
        if (cache != null) {
            out.put("price_cache_per_musd", cache);
        }
        return out;
    }

    /**
    * 取第一条能力探测延迟（若存在）。
    *
    * @param row 模型实体
    * @return 延迟毫秒或 空
    */
    private Long firstCapabilityLatency(SysAiProviderModel row) {
        List<Map<String, Object>> capabilities = row.getCapabilities();
        if (capabilities == null) {
            return null;
        }
        for (Map<String, Object> capability : capabilities) {
            Object health = capability.get("health");
            if (health instanceof Map<?, ?> healthMap) {
                Object latency = healthMap.get("latency");
                if (latency instanceof Number number) {
                    return number.longValue();
                }
            }
        }
        return null;
    }

    /**
    * 非空值合并（避免覆盖 enrich 已输出的 钥匙）。
    *
    * @param out   目标行
    * @param key   键
    * @param value 值
    */
    private void mergeNotNull(Map<String, Object> out, String key, Object value) {
        if (value != null) {
            out.put(key, value);
        }
    }

    /**
    * 快照写入 Redis（失败仅告警，不影响 WS 推送）。
    *
    * @param snapshot 快照
    */
    private void persistToRedis(Map<String, Object> snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            stringRedisTemplate.opsForValue().set(REDIS_KEY, json, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[ModelMetrics] Redis 快照写入失败: {}", e.getMessage());
        }
    }
}
