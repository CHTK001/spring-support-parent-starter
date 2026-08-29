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
 * 深度思考/联网查询/图片识别/函数调用」，合并模型自身的 <code>metrics</code> 实时字段
 * 与能力健康探测延迟，构建一份快照写入 Redis（key {@code nomifun:model-metrics:latest}，
 * TTL 12h），供查询接口按需读取。不再经 WebSocket 定时推送，前端通过
 * {@code GET /api/model-metrics} 从缓存获取。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelMetricsSnapshotService {

    /**
     * Redis 快照 key
     */
    static final String REDIS_KEY = "nomifun:model-metrics:latest";

    /**
     * Redis 快照 TTL（12 小时，防止冗余堆积）
     */
    private static final long REDIS_TTL_SECONDS = 12L * 60 * 60;

    /**
     * 提供商 Mapper
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 提供商模型 Mapper
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
        persistToRedis(snapshot);
        return snapshot;
    }

    /**
     * 仅构建快照（不写 Redis、不推送），供查询接口首查/后台任务直接使用。
     *
     * @return 快照 Map
     */
    public Map<String, Object> buildSnapshot() {
        return buildSnapshotInternal();
    }

    /**
     * 读取最近一次 Redis 快照；无快照时现场构建一份并返回。
     *
     * @return 快照 Map，构造失败时返回空结构
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
     * @return 快照 Map
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
     * 单行聚合：provider 基本信息 + enrich 静态指标 + 模型实时 metrics + 健康延迟。
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

        // Artificial Analysis 静态指标（输入/输出价、智能、速度、首块延迟）
        metricsEnrichmentService.enrich(row.getModel(), out);

        // 合并模型自身实时 metrics（用户维护/探测得到的延迟、智能、速度）
        Map<String, Object> metrics = row.getMetrics();
        if (metrics != null && !metrics.isEmpty()) {
            mergeNotNull(out, "latency_ms", metrics.get("latency_ms"));
            mergeNotNull(out, "intelligence", metrics.get("intelligence"));
            mergeNotNull(out, "output_speed", metrics.get("output_speed"));
        }

        // 能力健康探测延迟（最近一次 health.latency，单位 ms）
        Long probeLatencyMs = firstCapabilityLatency(row);
        if (probeLatencyMs != null) {
            out.put("probe_latency_ms", probeLatencyMs);
        }

        // 缓存价格：指标源无缓存价字段时沿用模型 metrics 自定义值，缺失为 null
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
     * @return 延迟毫秒或 null
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
     * 非空值合并（避免覆盖 enrich 已输出的 key）。
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
