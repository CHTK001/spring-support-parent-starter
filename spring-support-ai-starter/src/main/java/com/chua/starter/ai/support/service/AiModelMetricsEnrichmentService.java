package com.chua.starter.ai.support.service;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.datasearch.pricing.spi.AbstractModelMetricsProvider;
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
import java.util.concurrent.ConcurrentHashMap;
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
    /**
     * 磁盘快照目录
    */
    private static final String CACHE_DIR =
            System.getProperty("user.home") + "/.nomifun/cache";

    /**
     * 磁盘快照文件名
    */
    private static final String SNAPSHOT_FILE = "ai-model-metrics.json";

    private static final long CACHE_TTL_MILLIS = 6L * 60 * 60 * 1000; // 缓存ttlmillis

    /**
     * 指标数据源名称
     */
    private static final String SOURCE_NAME = "artificialanalysis";

    /**
     * 硬编码兜底定价（USD/百万 令牌）—— 用于不在 Artificial Analysis 数据库中的模型。
     * 键为归一化模型名，值为 {输入, 输出}。
     */
    private static final Map<String, double[]> FALLBACK_PRICING = new java.util.HashMap<>();

    static {
 // Agnes AI 自有模型（基于 打开router 同类产品参考定价）
        FALLBACK_PRICING.put("agnes-2-5-flash", new double[]{0.10, 0.40});
        FALLBACK_PRICING.put("agnes-image-2-0-flash", new double[]{0.00, 0.20});
        FALLBACK_PRICING.put("agnes-image-2-1-flash", new double[]{0.00, 0.30});
        FALLBACK_PRICING.put("agnes-video-v2-0", new double[]{0.00, 5.00});
        // GLM 系列（硅基流动公开定价）
        FALLBACK_PRICING.put("glm-4-flash", new double[]{0.00, 0.00});
        FALLBACK_PRICING.put("glm-4.1v-thinking-flash", new double[]{0.00, 0.00});
        // Google Gemini
        FALLBACK_PRICING.put("gemini-2.0-flash", new double[]{0.10, 0.40});
 // sensenova
        FALLBACK_PRICING.put("sensenova-6.7-flash-lite", new double[]{0.02, 0.06});
 // 打开router 免费嵌入模型
        FALLBACK_PRICING.put("llama-nemotron-embed-vl-1b-v2-free", new double[]{0.00, 0.00});
        FALLBACK_PRICING.put("nemotron-3-embed-1b-free", new double[]{0.00, 0.00});
        FALLBACK_PRICING.put("bge-large-en-v1.5", new double[]{0.00, 0.00});
 // 通义千问 免费模型
        FALLBACK_PRICING.put("qwen3-coder-free", new double[]{0.00, 0.00});
        FALLBACK_PRICING.put("qwen3-next-80b-a3b-instruct-free", new double[]{0.30, 0.30});
        // Nemotron
        FALLBACK_PRICING.put("nemotron-nano-12b-v2-vl-free", new double[]{0.00, 0.00});
 // cog视频
        FALLBACK_PRICING.put("cogvideox-flash", new double[]{0.00, 1.00});
        FALLBACK_PRICING.put("cogview-3-flash", new double[]{0.00, 0.50});
 // deepseek（与 AA 数据对齐，确保一致）
        FALLBACK_PRICING.put("deepseek-v4-flash-free", new double[]{0.14, 0.28});
        FALLBACK_PRICING.put("deepseek-v4-flash", new double[]{0.14, 0.28});
        FALLBACK_PRICING.put("deepseek-ai-deepseek-v4-flash", new double[]{0.14, 0.28});
    }

    /**
     * 静态初始化：确保 brotli4j native 库可用。
     *
     * <p>HttpClient5 会自动请求 brotli 编码并解压响应，但 brotli4j 的 native 库
     * 需要显式调用 {@code Brotli4jLoader.ensureAvailability()} 才会加载；未加载时
     * 解压会抛 {@code UnsatisfiedLinkError}，导致在线指标同步失败。这里用反射调用
     * 以避免编译期硬依赖 brotli4j（运行时才在 类路径）。</p>
     */
    static {
        try {
            Class<?> loader = ReflectUtils.forName("com.aayushatharva.brotli4j.Brotli4jLoader");
            if (loader != null) {
                ReflectUtils.invoke(null, "ensureAvailability", void.class);
            }
        } catch (Throwable ignored) {
            // brotli4j 缺失时静默降级（在线同步失败，回退磁盘快照）
        }
    }

    /**
     * 指标索引：归一化模型名 → 记录
     */
    private final ConcurrentHashMap<String, MetricsRecord> index = new ConcurrentHashMap<>();

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
     * @param modelId 模型 标识（如 gpt-4o、deepseek-v4-flash）
     * @param row     模型行数据（原地追加）
     */
    public void enrich(String modelId, Map<String, Object> row) {
        MetricsRecord rec = find(modelId);
        if (rec != null) {
            // AA 数据完整，直接写入
            /**
             * 放入ifnot空。
             * @param row row
             * @param "price_input_per_musd" "price输入permusd"
             * @param rec.inputPerMillion rec.输入permillion
             */
            putIfNotNull(row, "price_input_per_musd", rec.inputPerMillion);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "price_output_per_musd" "price输出permusd"
             * @param rec.outputPerMillion rec.输出permillion
             */
            putIfNotNull(row, "price_output_per_musd", rec.outputPerMillion);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "intelligence_index" "智能_索引"
             * @param rec.intelligenceIndex rec.智能索引
             */
            putIfNotNull(row, "intelligence_index", rec.intelligenceIndex);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "output_speed_tps" "输出速度tps"
             * @param rec.outputSpeedTps rec.输出速度tps
             */
            putIfNotNull(row, "output_speed_tps", rec.outputSpeedTps);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "latency_first_token_seconds" "延迟第一个令牌seconds"
             * @param rec.latencyFirstTokenSeconds rec.延迟第一个令牌seconds
             */
            putIfNotNull(row, "latency_first_token_seconds", rec.latencyFirstTokenSeconds);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "icon_url" "icon_url"
             * @param rec.iconUrl rec.iconurl
             */
            putIfNotNull(row, "icon_url", rec.iconUrl);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "reasoning_model" "ReasonMLML_模型"
             * @param rec.reasoning rec.ReasonMLML
             */
            putIfNotNull(row, "reasoning_model", rec.reasoning);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "web_search" "web_搜索"
             * @param rec.webSearch rec.web搜索
             */
            putIfNotNull(row, "web_search", rec.webSearch);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "image_input" "镜像_输入"
             * @param rec.imageInput rec.镜像输入
             */
            putIfNotNull(row, "image_input", rec.imageInput);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "function_calling" "function_calling"
             * @param rec.functionCalling rec.functioncalling
             */
            putIfNotNull(row, "function_calling", rec.functionCalling);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "price_cache_hit_per_musd" "price缓存hitpermusd"
             * @param rec.cacheHitPrice rec.缓存hitprice
             */
            putIfNotNull(row, "price_cache_hit_per_musd", rec.cacheHitPrice);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "price_cache_write_per_musd" "price缓存写入permusd"
             * @param rec.cacheWritePrice rec.缓存写入price
             */
            putIfNotNull(row, "price_cache_write_per_musd", rec.cacheWritePrice);
            /**
             * 放入ifnot空。
             * @param row row
             * @param "context_window_tokens" "上下文窗口令牌"
             * @param rec.contextWindowTokens rec.上下文窗口令牌
             */
            putIfNotNull(row, "context_window_tokens", rec.contextWindowTokens);
        } else {
            // AA 无数据，尝试硬编码兜底定价
            /**
             * apply降级pricing。
             * @param modelId 模型标识
             * @param row row
             */
            applyFallbackPricing(modelId, row);
        }
    }

    /**
     * 应用硬编码兜底定价：只补充 price_输入/输出，其余指标保持 空。
     * @param modelId 模型标识
     * @param row row
     */
    private void applyFallbackPricing(String modelId, Map<String, Object> row) {
        String key = normalize(modelId);
        double[] prices = FALLBACK_PRICING.get(key);
        if (prices == null) { return; }
        BigDecimal inPrice = scale(BigDecimal.valueOf(prices[0]));
        BigDecimal outPrice = scale(BigDecimal.valueOf(prices[1]));
        if (inPrice != null) { row.put("price_input_per_musd", inPrice); }
        if (outPrice != null) { row.put("price_output_per_musd", outPrice); }
    }

    /**
     * 归一化匹配模型并返回指标记录。
     *
     * @param modelId 模型 标识
     * @return 命中的指标记录，未命中返回 空
     */
    /**
     * 按模型标识查找 AA 定价记录（精确匹配优先，模糊匹配兜底）。
     *
     * @param modelId 模型标识，如 deepseek-v4-pro
     * @return 定价记录，未找到时返回 空
     */
    public MetricsRecord getPricingRecord(String modelId) {
        return find(modelId);
    }

    /**
     * 返回指标源中的全部模型指标记录（全量，与本地是否配置该模型无关）。
     *
     * @return 指标记录列表
     */
    public List<MetricsRecord> all() {
        ensureLoaded();
        return new ArrayList<>(index.values());
    }

    /**
     * 暴露归一化键计算，供指标表按模型名匹配。
     *
     * @param modelId 模型标识
     * @return 归一化键
     */
    public String normalizeKey(String modelId) {
        return normalize(modelId);
    }

    /**
     * 查找。
     * @param modelId 模型标识
     * @return find的结果
     */
    private MetricsRecord find(String modelId) {
        /**
         * ensure加载。
         */
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
                index.clear();
                index.putAll(indexOf(fresh));
                lastLoadAt.set(now);
                /**
                 * 保存转为disk。
                 * @param fresh fresh
                 */
                saveToDisk(fresh);
                log.info("[AiModelMetrics] 在线指标已加载并持久化，共 {} 条", fresh.size());
                return;
            }

            // 在线失败 → 回退磁盘持久化快照（陈旧数据优于无数据）
            List<MetricsRecord> stale = loadFromDisk();
            if (stale != null && !stale.isEmpty()) {
                index.clear();
                index.putAll(indexOf(stale));
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
        List<ModelDefinition> list;
        if (provider instanceof AbstractModelMetricsProvider abstractProvider) {
 // SPI 实例未注入 配置保存or加载，获取指标()/同步从online() 无法持久化读取，
            // 直接调用在线抓取方法拿原始列表。
            list = abstractProvider.fetchOnlinePricing();
        } else {
            list = safeMetrics(provider);
            if (list.isEmpty()) {
                provider.syncFromOnline();
                list = safeMetrics(provider);
            }
        }
        if (list == null || list.isEmpty()) { return Map.of(); }
        Map<String, MetricsRecord> idx = new HashMap<>(256); // AA 模型数量较多，预留容量
        for (ModelDefinition md : list) {
            String key = normalize(md.getId());
            if (key.isEmpty()) { continue; }
            idx.putIfAbsent(key, new MetricsRecord(
                    key,
                    md.getProvider(),
                    /**
                     * scale。
                     * @param md.getInputUnitPrice( md.获取输入unitprice(
                     */
                    scale(md.getInputUnitPrice()),
                    /**
                     * scale。
                     * @param md.getOutputUnitPrice( md.获取输出unitprice(
                     */
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
     * @param id 原始模型 标识
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
     * @return 格式化值或 空
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
            StringBuilder sb = new StringBuilder(512); // 快照 JSON 长度不可预估
            for (int i = 0; i < records.size(); i++) {
                MetricsRecord r = records.get(i);
                if (i > 0) { sb.append(','); }
                sb.append("{\"key\":\"").append(esc(r.normalizedKey)).append('"')
                  .append(",\"prov\":")
                  .append(r.provider == null ? "null" : "\"" + esc(r.provider) + "\"")
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
     * @return 指标记录列表，文件缺失或损坏返回 空
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
                        o.get("prov") == null ? null : String.valueOf(o.get("prov")),
                        /**
                         * dec。
                         * @param o.get("in" o.获取("入"
                         */
                        dec(o.get("in")),
                        /**
                         * dec。
                         * @param o.get("out" o.获取("出"
                         */
                        dec(o.get("out")),
                        /**
                         * dec。
                         * @param o.get("intel" o.获取("intel"
                         */
                        dec(o.get("intel")),
                        /**
                         * dec。
                         * @param o.get("spd" o.获取("spd"
                         */
                        dec(o.get("spd")),
                        /**
                         * dec。
                         * @param o.get("ttft" o.获取("ttft"
                         */
                        dec(o.get("ttft")),
                        o.get("icon") == null ? null : String.valueOf(o.get("icon")),
                        /**
                         * bool。
                         * @param o.get("reason" o.获取("ReasonMLML"
                         */
                        bool(o.get("reason")),
                        /**
                         * bool。
                         * @param o.get("wsrch" o.获取("wsrch"
                         */
                        bool(o.get("wsrch")),
                        /**
                         * bool。
                         * @param o.get("imgin" o.获取("imgin"
                         */
                        bool(o.get("imgin")),
                        /**
                         * bool。
                         * @param o.get("fncall" o.获取("fncall"
                         */
                        bool(o.get("fncall")),
                        /**
                         * dec。
                         * @param o.get("cachehit" o.获取("cachehit"
                         */
                        dec(o.get("cachehit")),
                        /**
                         * dec。
                         * @param o.get("cachewr" o.获取("cachewr"
                         */
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
        Map<String, MetricsRecord> idx = new HashMap<>(256); // AA 模型数量较多，预留容量
        for (MetricsRecord r : records) {
            idx.putIfAbsent(r.normalizedKey(), r);
        }
        return idx;
    }

    /**
     * 空值安全的数值转换。
     *
     * @param v 原始值
     * @return 数值或 空
     */
    private BigDecimal dec(Object v) {
        if (v == null) { return null; }
        try { return new BigDecimal(String.valueOf(v)).stripTrailingZeros(); }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * 空值安全的布尔转换（磁盘旧快照缺字段时返回 空）。
     *
     * @param v 原始值
     * @return 布尔或 空
     */
    private Boolean bool(Object v) {
        return v == null ? null : Boolean.valueOf(String.valueOf(v));
    }

    /**
     * 空值安全的长整型转换。
     *
     * @param v 原始值
     * @return 数值或 空
     */
    private Long lng(Object v) {
        if (v == null) { return null; }
        try { return Long.parseLong(String.valueOf(v).split("\\.")[0]); }
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
    public record MetricsRecord(
            /**
             * 归一化键
            */
            String normalizedKey,
            /**
             * 提供厂商
            */
            String provider,
            /**
             * 输入单价（USD/百万 令牌）
            */
            BigDecimal inputPerMillion,
            /**
             * 输出单价（USD/百万 令牌）
            */
            BigDecimal outputPerMillion,
            /**
             * 智能指数
            */
            BigDecimal intelligenceIndex,
            /**
             * 输出速度（令牌/秒）
            */
            BigDecimal outputSpeedTps,
            /**
             * 首 令牌 延迟（秒）
            */
            BigDecimal latencyFirstTokenSeconds,
            /**
             * 图标地址
            */
            String iconUrl,
            /**
             * 是否深度思考（推理模型）
            */
            Boolean reasoning,
            /**
             * 是否联网查询
            */
            Boolean webSearch,
            /**
             * 是否图片识别（视觉输入）
            */
            Boolean imageInput,
            /**
             * 是否函数调用
            */
            Boolean functionCalling,
            /**
             * 缓存命中输入价（USD/百万 令牌）
            */
            BigDecimal cacheHitPrice,
            /**
             * 缓存写入价（USD/百万 令牌）
            */
            BigDecimal cacheWritePrice,
            /**
             * 上下文窗口（令牌 数）
            */
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
