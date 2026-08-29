package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.datasearch.pricing.spi.ModelMetricsProvider;
import com.chua.common.support.spi.ServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 模型指标富集服务。
 *
 * <p>加载 Artificial Analysis 全量模型指标（分项价格、智能指数、输出速度、首块延迟、厂商图标），
 * 为模型列表接口的每一行补充这些扩展字段；数据按 6 小时周期自动刷新，
 * 加载失败时静默降级（不追加任何字段）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class AiModelMetricsEnrichmentService {

    /**
     * 缓存刷新间隔（毫秒）：6 小时
     */
    /** 磁盘快照目录 */
    private static final String CACHE_DIR =
            System.getProperty("user.home") + "/.nomifun/cache";

    /** 磁盘快照文件名 */
    private static final String SNAPSHOT_FILE = "ai-model-metrics.json";

    private static final long CACHE_TTL_MILLIS = 6L * 60 * 60 * 1000;

    /**
     * 指标数据源名称
     */
    private static final String SOURCE_NAME = "artificialanalysis";

    /**
     * 指标索引：归一化模型名 → 记录
     */
    private volatile Map<String, MetricsRecord> index = Map.of();

    /**
     * 上次加载时间戳
     */
    private final AtomicLong lastLoadAt = new AtomicLong(0);

    /**
     * 为单个模型追加指标扩展字段。
     *
     * <p>命中的字段以固定键名写入 row：</p>
     * <ul>
     *   <li>{@code price_input_per_musd} / {@code price_output_per_musd}（USD/百万 Token）</li>
     *   <li>{@code price_cache_hit_per_musd} / {@code price_cache_write_per_musd}（缓存价）</li>
     *   <li>{@code intelligence_index}</li>
     *   <li>{@code output_speed_tps}（Token/秒）</li>
     *   <li>{@code latency_first_token_seconds}</li>
     *   <li>{@code icon_url}</li>
     *   <li>{@code reasoning_model} / {@code web_search} / {@code image_input} / {@code function_calling}</li>
     *   <li>{@code context_window_tokens}</li>
     * </ul>
     *
     * @param modelId 模型 ID（如 gpt-4o、deepseek-v4-flash）
     * @param row     模型行数据（原地追加）
     */
    public void enrich(String modelId, Map<String, Object> row) {
        MetricsRecord rec = find(modelId);
        if (rec == null) {
            return;
        }
        putIfNotNull(row, "price_input_per_musd", rec.inputPerMillion);
        putIfNotNull(row, "price_output_per_musd", rec.outputPerMillion);
        putIfNotNull(row, "intelligence_index", rec.intelligenceIndex);
        putIfNotNull(row, "output_speed_tps", rec.outputSpeedTps);
        putIfNotNull(row, "latency_first_token_seconds", rec.latencyFirstTokenSeconds);
        putIfNotNull(row, "icon_url", rec.iconUrl);
        putIfNotNull(row, "reasoning_model", rec.reasoning);
        putIfNotNull(row, "web_search", rec.webSearch);
        putIfNotNull(row, "image_input", rec.imageInput);
        putIfNotNull(row, "function_calling", rec.functionCalling);
        putIfNotNull(row, "price_cache_hit_per_musd", rec.cacheHitPrice);
        putIfNotNull(row, "price_cache_write_per_musd", rec.cacheWritePrice);
        putIfNotNull(row, "context_window_tokens", rec.contextWindowTokens);
    }

    /**
     * 归一化匹配模型并返回指标记录。
     *
     * @param modelId 模型 ID
     * @return 命中的指标记录，未命中返回 null
     */
    private MetricsRecord find(String modelId) {
        ensureLoaded();
        if (index.isEmpty()) { return null; }
        String key = normalize(modelId);
        if (key.isEmpty()) { return null; }
        MetricsRecord exact = index.get(key);
        if (exact != null) { return exact; }
        // 次优：包含关系（AA 的 slug 通常更长，如 deepseek-v4-flash-vision-exp）
        for (MetricsRecord rec : index.values()) {
            String nk = rec.normalizedKey;
            if (nk.length() >= 6 && (nk.contains(key) || key.contains(nk))) { return rec; }
        }
        return null;
    }

    /**
     * 惰性加载并按 TTL 刷新指标索引。
     */
    private void ensureLoaded() {
        long now = System.currentTimeMillis();
        long last = lastLoadAt.get();
        if (now - last < CACHE_TTL_MILLIS && !index.isEmpty()) { return; }
        synchronized (this) {
            now = System.currentTimeMillis();
            last = lastLoadAt.get();
            if (now - last < CACHE_TTL_MILLIS && !index.isEmpty()) { return; }

            List<MetricsRecord> fresh = null;
            try {
                Map<String, MetricsRecord> loaded = loadIndex();
                if (!loaded.isEmpty()) { fresh = new ArrayList<>(loaded.values()); }
            } catch (Throwable t) {
                log.warn("[AiModelMetrics] 在线指标加载失败: {}", t.getMessage());
            }

            if (fresh != null && !fresh.isEmpty()) {
                index = indexOf(fresh);
                lastLoadAt.set(now);
                saveToDisk(fresh);
                log.info("[AiModelMetrics] 在线指标已加载并持久化，共 {} 条", fresh.size());
                return;
            }

            // 在线失败 → 回退磁盘持久化快照（陈旧数据优于无数据）
            List<MetricsRecord> stale = loadFromDisk();
            if (stale != null && !stale.isEmpty()) {
                index = indexOf(stale);
                log.warn("[AiModelMetrics] 使用磁盘持久化快照（陈旧数据），共 {} 条", stale.size());
            }
        }
    }

    /**
     * 从 Artificial Analysis 数据源构建指标索引。
     *
     * <p>优先读取本地缓存；缓存为空时触发一次在线同步再读取。</p>
     *
     * @return 归一化键 → 指标记录
     */
    private Map<String, MetricsRecord> loadIndex() {
        ModelMetricsProvider provider = ServiceProvider.of(ModelMetricsProvider.class)
                .getExtension(SOURCE_NAME);
        if (provider == null) { return Map.of(); }
        List<ModelDefinition> list = safeMetrics(provider);
        if (list.isEmpty()) {
            provider.syncFromOnline();
            list = safeMetrics(provider);
        }
        if (list == null || list.isEmpty()) { return Map.of(); }
        Map<String, MetricsRecord> idx = new HashMap<>();
        for (ModelDefinition md : list) {
            String key = normalize(md.getId());
            if (key.isEmpty()) { continue; }
            idx.putIfAbsent(key, new MetricsRecord(
                    key,
                    scale(md.getInputUnitPrice()),
                    scale(md.getOutputUnitPrice()),
                    md.getIntelligenceIndex(),
                    md.getOutputSpeedTokensPerSecond(),
                    md.getLatencyFirstTokenSeconds(),
                    md.getIconUrl(),
                    md.getReasoning(),
                    md.getWebSearch(),
                    md.getImageInput(),
                    md.getFunctionCalling(),
                    md.getCacheHitPrice(),
                    md.getCacheWritePrice(),
                    md.getContextWindowTokens()));
        }
        return idx;
    }

    /**
     * 归一化：小写、去除厂商前缀、非字母数字合并为连字符。
     *
     * @param id 原始模型 ID
     * @return 归一化键
     */
    private String normalize(String id) {
        if (id == null) { return ""; }
        String t = id.toLowerCase();
        int slash = t.lastIndexOf('/');
        if (slash >= 0) { t = t.substring(slash + 1); }
        return t.replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-");
    }

    /**
     * 值非空时写入。
     *
     * @param row 目标行
     * @param key 键
     * @param v   值
     */
    private void putIfNotNull(Map<String, Object> row, String key, Object v) {
        if (v != null) { row.put(key, v); }
    }

    /**
     * 数值格式化（4 位小数）。
     *
     * @param v 原始值
     * @return 格式化值或 null
     */
    private BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
    }


    /**
     * 将指标列表持久化到磁盘快照文件。
     *
     * @param records 指标记录
     */
    private void saveToDisk(List<MetricsRecord> records) {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of(CACHE_DIR);
            java.nio.file.Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < records.size(); i++) {
                MetricsRecord r = records.get(i);
                if (i > 0) { sb.append(','); }
                sb.append("{\"key\":\"").append(esc(r.normalizedKey)).append('"')
                  .append(",\"in\":").append(r.inputPerMillion)
                  .append(",\"out\":").append(r.outputPerMillion)
                  .append(",\"intel\":").append(r.intelligenceIndex)
                  .append(",\"spd\":").append(r.outputSpeedTps)
                  .append(",\"ttft\":").append(r.latencyFirstTokenSeconds)
                  .append(",\"icon\":")
                  .append(r.iconUrl == null ? "null" : "\"" + esc(r.iconUrl) + "\"")
                  .append(",\"reason\":").append(r.reasoning)
                  .append(",\"wsrch\":").append(r.webSearch)
                  .append(",\"imgin\":").append(r.imageInput)
                  .append(",\"fncall\":").append(r.functionCalling)
                  .append(",\"cachehit\":").append(r.cacheHitPrice)
                  .append(",\"cachewr\":").append(r.cacheWritePrice)
                  .append(",\"ctx\":").append(r.contextWindowTokens)
                  .append('}');
            }
            sb.append(']');
            java.nio.file.Files.write(
                    dir.resolve(SNAPSHOT_FILE),
                    sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable t) {
            log.debug("[AiModelMetrics] 快照写盘失败: {}", t.getMessage());
        }
    }

    /**
     * 从磁盘快照恢复指标列表。
     *
     * @return 指标记录列表，文件缺失或损坏返回 null
     */
    @SuppressWarnings("unchecked")
    private List<MetricsRecord> loadFromDisk() {
        try {
            java.nio.file.Path file = java.nio.file.Path.of(CACHE_DIR, SNAPSHOT_FILE);
            if (!java.nio.file.Files.exists(file)) { return null; }
            String json = java.nio.file.Files.readString(file);
            List<Map<String, Object>> arr = com.chua.common.support.lang.json.Json.fromJson(
                    json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() { });
            if (arr == null || arr.isEmpty()) { return null; }
            List<MetricsRecord> out = new ArrayList<>(arr.size());
            for (Map<String, Object> o : arr) {
                out.add(new MetricsRecord(
                        String.valueOf(o.get("key")),
                        dec(o.get("in")),
                        dec(o.get("out")),
                        dec(o.get("intel")),
                        dec(o.get("spd")),
                        dec(o.get("ttft")),
                        o.get("icon") == null ? null : String.valueOf(o.get("icon")),
                        bool(o.get("reason")),
                        bool(o.get("wsrch")),
                        bool(o.get("imgin")),
                        bool(o.get("fncall")),
                        dec(o.get("cachehit")),
                        dec(o.get("cachewr")),
                        lng(o.get("ctx"))));
            }
            return out;
        } catch (Throwable t) {
            log.debug("[AiModelMetrics] 快照读取失败: {}", t.getMessage());
            return null;
        }
    }

    /**
     * 按记录列表构建归一化索引。
     *
     * @param records 指标记录
     * @return 归一化键 → 记录
     */
    private Map<String, MetricsRecord> indexOf(List<MetricsRecord> records) {
        Map<String, MetricsRecord> idx = new HashMap<>();
        for (MetricsRecord r : records) {
            idx.putIfAbsent(r.normalizedKey(), r);
        }
        return idx;
    }

    /**
     * 空值安全的数值转换。
     *
     * @param v 原始值
     * @return 数值或 null
     */
    private BigDecimal dec(Object v) {
        if (v == null) { return null; }
        try { return new BigDecimal(String.valueOf(v)).stripTrailingZeros(); }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * 空值安全的布尔转换（磁盘旧快照缺字段时返回 null）。
     *
     * @param v 原始值
     * @return 布尔或 null
     */
    private Boolean bool(Object v) {
        return v == null ? null : Boolean.valueOf(String.valueOf(v));
    }

    /**
     * 空值安全的长整型转换。
     *
     * @param v 原始值
     * @return 数值或 null
     */
    private Long lng(Object v) {
        if (v == null) { return null; }
        try { return Long.valueOf(String.valueOf(v).split("\\.")[0]); }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * JSON 字符串转义。
     *
     * @param s 原始文本
     * @return 转义后的文本
     */
    private String esc(String s) {
        if (s == null) { return ""; }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    /**
     * 单条模型指标记录。
     */
    record MetricsRecord(
            /** 归一化键 */
            String normalizedKey,
            /** 输入单价（USD/百万 Token） */
            BigDecimal inputPerMillion,
            /** 输出单价（USD/百万 Token） */
            BigDecimal outputPerMillion,
            /** 智能指数 */
            BigDecimal intelligenceIndex,
            /** 输出速度（Token/秒） */
            BigDecimal outputSpeedTps,
            /** 首 Token 延迟（秒） */
            BigDecimal latencyFirstTokenSeconds,
            /** 图标地址 */
            String iconUrl,
            /** 是否深度思考（推理模型） */
            Boolean reasoning,
            /** 是否联网查询 */
            Boolean webSearch,
            /** 是否图片识别（视觉输入） */
            Boolean imageInput,
            /** 是否函数调用 */
            Boolean functionCalling,
            /** 缓存命中输入价（USD/百万 Token） */
            BigDecimal cacheHitPrice,
            /** 缓存写入价（USD/百万 Token） */
            BigDecimal cacheWritePrice,
            /** 上下文窗口（Token 数） */
            Long contextWindowTokens) {
    }

    /**
     * 安全读取指标列表。
     *
     * @param provider 数据源
     * @return 指标列表，异常时返回空列表
     */
    private List<ModelDefinition> safeMetrics(ModelMetricsProvider provider) {
        try {
            List<ModelDefinition> list = provider.getMetrics();
            return list == null ? List.of() : list;
        } catch (Throwable t) {
            log.warn("[AiModelMetrics] 读取指标失败: {}", t.getMessage());
            return List.of();
        }
    }
}
