// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.datasearch.pricing.spi.AbstractModelMetricsProvider;
import com.chua.common.support.datasearch.pricing.spi.ModelMetricsProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.util.ModelMetricKeyMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * 是否已有线程在加载指标源（单飞标记）。
     */
    private final java.util.concurrent.atomic.AtomicBoolean loading =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 本轮加载的起始时间（毫秒）：给 loading 标记做看门狗，防止一次永久阻塞把
     * 「再也不会加载」变成永久状态。
     */
    private final AtomicLong loadStartedAt = new AtomicLong(0);

    /**
     * 加载失败后的冷却截止时间戳（毫秒）：失败后这段时间内不再触发加载，
     * 避免每个请求都去打一次不可达的外网。用 AtomicLong 而不是 volatile：
     * 读-判断-写三步要原子，否则并发请求会同时穿过冷却窗口。
     */
    private final AtomicLong nextLoadAttemptAt = new AtomicLong(0);

    /**
     * 加载失败重试冷却（毫秒）
     */
    private static final long LOAD_RETRY_COOLDOWN_MILLIS = 60_000L;

    /**
     * loading 标记的最长存活时间（毫秒）：超过视为线程卡死，强制放开重试。
     *
     * <p>底层 HTTP 客户端默认 30 秒连接/读取超时，两次抓取最多约 60 秒；给到 5 分钟是
     * 留足余量。真卡死时宁可多一轮重复加载，也不能让指标永久停在空索引。</p>
     */
    private static final long LOAD_STUCK_MILLIS = 5L * 60 * 1000;

    /**
     * 显式同步入口等待加载完成的上限（毫秒）
     */
    private static final long LOAD_WAIT_MILLIS = 20_000L;

    /**
     * 启动后在后台线程预热指标源。
     *
     * <p>加载要访问外网（Artificial Analysis），网络不可达时可能挂几分钟。这里只负责
     * 触发，加载始终在 daemon 线程里跑，请求线程永不等待网络 I/O。</p>
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        try {
            ensureLoaded();
        } catch (Throwable t) {
            log.debug("[AiModelMetrics] 启动预热失败: {}", t.getMessage());
        }
    }

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
     *   <li>{@code hle_score} / {@code omniscience_index}</li>
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
            putIfNotNull(row, "hle_score", rec.hleScore);
            putIfNotNull(row, "omniscience_index", rec.omniscienceIndex);
            putIfNotNull(row, "gpqa_score", rec.gpqaScore);
            putIfNotNull(row, "aime25_score", rec.aime25Score);
            putIfNotNull(row, "livecodebench_score", rec.livecodebenchScore);
            putIfNotNull(row, "scicode_score", rec.scicodeScore);
            putIfNotNull(row, "ifbench_score", rec.ifbenchScore);
            putIfNotNull(row, "critpt_score", rec.critptScore);
            putIfNotNull(row, "mmmu_pro_score", rec.mmmuProScore);
            putIfNotNull(row, "tau2_score", rec.tau2Score);
            putIfNotNull(row, "tau_banking_score", rec.tauBankingScore);
            putIfNotNull(row, "lcr_score", rec.lcrScore);
            putIfNotNull(row, "terminalbench_hard_score", rec.terminalbenchHardScore);
            putIfNotNull(row, "terminal_bench21_score", rec.terminalBench21Score);
            putIfNotNull(row, "terminal_bench40_score", rec.terminalBench40Score);
            putIfNotNull(row, "automation_bench_score", rec.automationBenchScore);
            putIfNotNull(row, "gdpval_normalized_score", rec.gdpvalNormalizedScore);
            putIfNotNull(row, "briefcase_score", rec.briefcaseScore);
            putIfNotNull(row, "harvey_lab_score", rec.harveyLabScore);
            putIfNotNull(row, "apex_agents_score", rec.apexAgentsScore);
            putIfNotNull(row, "itbench_sre_score", rec.itbenchSreScore);
            putIfNotNull(row, "omniscience_accuracy", rec.omniscienceAccuracy);
            putIfNotNull(row, "omniscience_non_hallucination", rec.omniscienceNonHallucination);
            putIfNotNull(row, "elo", rec.elo);
            putIfNotNull(row, "cost_per_task", rec.costPerTask);
            putIfNotNull(row, "json_mode", rec.jsonMode);
            putIfNotNull(row, "openai_compatible", rec.openaiCompatible);
            putIfNotNull(row, "intelligence_index_estimated", rec.intelligenceIndexEstimated);
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
     * <p>本方法给「显式同步」这类管理动作使用：触发加载后最多等
     * {@link #LOAD_WAIT_MILLIS}，拿到当前索引就返回；等不到也不抛错，
     * 由调用方按「暂无数据」处理（{@code syncAll} 已有空列表保护）。</p>
     *
     * @return 指标记录列表
     */
    public List<MetricsRecord> all() {
        ensureLoaded();
        awaitLoaded(LOAD_WAIT_MILLIS);
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
        if (index.isEmpty()) {
            return null;
        }
        String key = normalize(modelId);
        if (key.isEmpty()) {
            return null;
        }
        MetricsRecord exact = index.get(key);
        if (exact != null) {
            return exact;
        }
        // 次优：同模型变体（指标源 slug 多带版本/日期/别名后缀，或本地名多带版本后缀）。
        // 只接受「头尾多出可解释后缀」的候选，且长度差最小者胜 —— 见 ModelMetricKeyMatcher：
        // 原实现用双向 contains 取遍历到的第一个，既会串到 -mini/-lite/-free 这类别的模型，
        // 又会因 ConcurrentHashMap 桶序在索引扩容后换人，同一模型两次同步拿到不同数字。
        return ModelMetricKeyMatcher.selectNearest(key, index.values(), MetricsRecord::normalizedKey);
    }

    /**
     * 惰性触发加载并按 TTL 刷新指标索引。
     *
     * <p><b>请求线程永不等待外网</b>：这里只负责把加载交给后台 daemon 线程，
     * 调用方立刻拿到「当前索引」（可能为空）。原实现即使改成了单飞，抢到加载权的那条
     * 请求仍要等网络返回；指标源不可达时一次列表请求就要挂到分钟级。</p>
     */
    private void ensureLoaded() {
        long now = System.currentTimeMillis();
        long last = lastLoadAt.get();
        if (now - last < CACHE_TTL_MILLIS && !index.isEmpty()) {
            return;
        }
        if (now < nextLoadAttemptAt.get()) {
            // 上一次加载刚失败过：冷却期内不再打外网
            return;
        }
        if (loading.get() && now - loadStartedAt.get() > LOAD_STUCK_MILLIS) {
            // 看门狗：加载线程卡死超过上限就放开标记，宁可多一轮重复加载，
            // 也不能让指标永久停在空索引。
            log.warn("[AiModelMetrics] 上次加载已超过 {}ms 未结束，强制放开重试", LOAD_STUCK_MILLIS);
            loading.set(false);
        }
        if (!loading.compareAndSet(false, true)) {
            // 已有线程在加载：本次直接用当前索引返回
            return;
        }
        loadStartedAt.set(now);
        Thread thread = new Thread(this::loadAndSwap, "ai-metrics-load");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 后台加载主体：在线成功换索引并写盘，失败回退磁盘快照并进入冷却。
     */
    private void loadAndSwap() {
        try {
            long now = System.currentTimeMillis();
            if (now - lastLoadAt.get() < CACHE_TTL_MILLIS && !index.isEmpty()) {
                return;
            }

            List<MetricsRecord> fresh = null;
            try {
                Map<String, MetricsRecord> loaded = loadIndex();
                if (!loaded.isEmpty()) {
                    fresh = new ArrayList<>(loaded.values());
                }
            } catch (Throwable t) {
                log.warn("[AiModelMetrics] 在线指标加载失败: {}", t.getMessage());
            }

            if (fresh != null && !fresh.isEmpty()) {
                index.clear();
                index.putAll(indexOf(fresh));
                lastLoadAt.set(now);
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
            // 本轮没拿到在线数据：进入冷却，避免下一个请求立刻再打一次外网
            nextLoadAttemptAt.set(now + LOAD_RETRY_COOLDOWN_MILLIS);
        } catch (Throwable t) {
            // 兜底：任何未预期异常都不能让 loading 永久为 true
            log.warn("[AiModelMetrics] 指标加载异常: {}", t.getMessage());
            nextLoadAttemptAt.set(System.currentTimeMillis() + LOAD_RETRY_COOLDOWN_MILLIS);
        } finally {
            loadStartedAt.set(0);
            loading.set(false);
        }
    }

    /**
     * 等待后台加载结束（仅供显式同步入口使用，有硬上限）。
     *
     * @param millis 最长等待毫秒
     */
    private void awaitLoaded(long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (loading.get() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
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
        if (provider == null) {
            return Map.of();
        }
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
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        Map<String, MetricsRecord> idx = new HashMap<>(256); // AA 模型数量较多，预留容量
        for (ModelDefinition md : list) {
            String key = normalize(md.getId());
            if (key.isEmpty()) {
                continue;
            }
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
                    md.getContextWindowTokens(),
                    md.getHleScore(),
                    md.getOmniscienceIndex(),
                    md.getGpqaScore(),
                    md.getAime25Score(),
                    md.getLivecodebenchScore(),
                    md.getScicodeScore(),
                    md.getIfbenchScore(),
                    md.getCritptScore(),
                    md.getMmmuProScore(),
                    md.getTau2Score(),
                    md.getTauBankingScore(),
                    md.getLcrScore(),
                    md.getTerminalbenchHardScore(),
                    md.getTerminalBench21Score(),
                    md.getTerminalBench40Score(),
                    md.getAutomationBenchScore(),
                    md.getGdpvalNormalizedScore(),
                    md.getBriefcaseScore(),
                    md.getHarveyLabScore(),
                    md.getApexAgentsScore(),
                    md.getItbenchSreScore(),
                    md.getOmniscienceAccuracy(),
                    md.getOmniscienceNonHallucination(),
                    md.getElo(),
                    md.getCostPerTask(),
                    md.getJsonMode(),
                    md.getOpenaiCompatible(),
                    md.getIntelligenceIndexEstimated()));
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
            // 开头的 '[' 不能漏：原实现只补了结尾的 ']'，写出的文件不是合法 JSON，
            // loadFromDisk 解析必然抛异常又被 debug 日志吞掉，于是「离线兜底」这条分支一直是死的。
            sb.append('[');
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
                  .append(",\"hle\":").append(r.hleScore)
                  .append(",\"omni\":").append(r.omniscienceIndex)
                  .append(",\"gpqa_score\":").append(r.gpqaScore)
                  .append(",\"aime25_score\":").append(r.aime25Score)
                  .append(",\"livecodebench_score\":").append(r.livecodebenchScore)
                  .append(",\"scicode_score\":").append(r.scicodeScore)
                  .append(",\"ifbench_score\":").append(r.ifbenchScore)
                  .append(",\"critpt_score\":").append(r.critptScore)
                  .append(",\"mmmu_pro_score\":").append(r.mmmuProScore)
                  .append(",\"tau2_score\":").append(r.tau2Score)
                  .append(",\"tau_banking_score\":").append(r.tauBankingScore)
                  .append(",\"lcr_score\":").append(r.lcrScore)
                  .append(",\"terminalbench_hard_score\":").append(r.terminalbenchHardScore)
                  .append(",\"terminal_bench21_score\":").append(r.terminalBench21Score)
                  .append(",\"terminal_bench40_score\":").append(r.terminalBench40Score)
                  .append(",\"automation_bench_score\":").append(r.automationBenchScore)
                  .append(",\"gdpval_normalized_score\":").append(r.gdpvalNormalizedScore)
                  .append(",\"briefcase_score\":").append(r.briefcaseScore)
                  .append(",\"harvey_lab_score\":").append(r.harveyLabScore)
                  .append(",\"apex_agents_score\":").append(r.apexAgentsScore)
                  .append(",\"itbench_sre_score\":").append(r.itbenchSreScore)
                  .append(",\"omniscience_accuracy\":").append(r.omniscienceAccuracy)
                  .append(",\"omniscience_non_hallucination\":").append(r.omniscienceNonHallucination)
                  .append(",\"elo\":").append(r.elo)
                  .append(",\"cost_per_task\":").append(r.costPerTask)
                  .append(",\"json_mode\":").append(r.jsonMode)
                  .append(",\"openai_compatible\":").append(r.openaiCompatible)
                  .append(",\"intelligence_index_estimated\":").append(r.intelligenceIndexEstimated)
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
            if (!java.nio.file.Files.exists(file)) {
                return null;
            }
            String json = java.nio.file.Files.readString(file);
            List<Map<String, Object>> arr = com.chua.common.support.lang.json.Json.fromJson(
                    json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() { });
            if (arr == null || arr.isEmpty()) {
                return null;
            }
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
                        lng(o.get("ctx")),
                        dec(o.get("hle")),
                        dec(o.get("omni")),
                        dec(o.get("gpqa_score")),
                        dec(o.get("aime25_score")),
                        dec(o.get("livecodebench_score")),
                        dec(o.get("scicode_score")),
                        dec(o.get("ifbench_score")),
                        dec(o.get("critpt_score")),
                        dec(o.get("mmmu_pro_score")),
                        dec(o.get("tau2_score")),
                        dec(o.get("tau_banking_score")),
                        dec(o.get("lcr_score")),
                        dec(o.get("terminalbench_hard_score")),
                        dec(o.get("terminal_bench21_score")),
                        dec(o.get("terminal_bench40_score")),
                        dec(o.get("automation_bench_score")),
                        dec(o.get("gdpval_normalized_score")),
                        dec(o.get("briefcase_score")),
                        dec(o.get("harvey_lab_score")),
                        dec(o.get("apex_agents_score")),
                        dec(o.get("itbench_sre_score")),
                        dec(o.get("omniscience_accuracy")),
                        dec(o.get("omniscience_non_hallucination")),
                        dec(o.get("elo")),
                        dec(o.get("cost_per_task")),
                        bool(o.get("json_mode")),
                        bool(o.get("openai_compatible")),
                        bool(o.get("intelligence_index_estimated"))));
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
     *
     * <p>数据源为 Artificial Analysis（{@code SOURCE_NAME}），在线抓取失败时回退本地磁盘快照，
     * 因此除 {@code normalizedKey} 外所有组件都可能为 {@code null}——语义是「该模型没有这项数据」，
     * 而不是「这项数据为 0」。所以对外写出时一律走 {@code putIfNotNull}，为 {@code null} 的项直接不出现在结果行里，
     * 下游必须按「键不存在」而不是「键值为空」来理解缺省。</p>
     *
     * <p>量纲分三类，不要混用：价格为 <b>USD / 百万 Token</b>（源字段名即 {@code price1m*}），
     * 基准分为 <b>0~1 的小数</b>（不是百分制，展示时需自行乘 100），速度与延迟为 <b>Token/秒</b>与 <b>秒</b>。
     * {@code intelligenceIndex}、{@code omniscienceIndex}、{@code elo}、{@code costPerTask} 不在这三类之内，
     * 各自的换算与边界见对应标签。
     * 七个布尔型组件（{@code reasoning} / {@code webSearch} / {@code imageInput} / {@code functionCalling} /
     * {@code jsonMode} / {@code openaiCompatible} / {@code intelligenceIndexEstimated}）都是三态的：
     * {@code null} 表示数据源未给出（未知），不能当作 {@code false} 用。</p>
     *
     * @param normalizedKey 归一化后的模型名，同时充当指标索引的键，不允许为 {@code null}（规范构造器强制校验）。
     *                      由 {@link #normalize} 生成：转小写 → 取最后一个 {@code /} 之后的部分（剥掉厂商前缀）
     *                      → 非字母数字（含中文）连续段合并为单个连字符，如 {@code OpenAI/GPT-4o} → {@code gpt-4o}。
     *                      精确匹配失败后还参与包含式模糊匹配，此时要求长度不小于 6 以免误命中
     * @param provider     模型提供厂商（创造者）名称，允许为 {@code null}。取值来源为 {@code ModelDefinition#getProvider()}，
     *                      Artificial Analysis 侧取自模型目录的 creatorName；目录缺失时退化为 slug
     * @param inputPerMillion 输入单价，单位 <b>USD / 百万 Token</b>，允许为 {@code null}（该模型未报此项）。
     *                      源字段 {@code price1mInputTokens}，经 {@code scale} 统一保留 4 位小数（HALF_UP）并去掉尾随零；
     *                      免费模型记为 0（意为不计费），与缺数据的 {@code null} 含义不同，不要混判
     * @param outputPerMillion 输出单价，单位 <b>USD / 百万 Token</b>，允许为 {@code null}。源字段
     *                      {@code price1mOutputTokens}，同样经 {@code scale} 规范化；通常高于输入价
     * @param intelligenceIndex 智能指数（Artificial Analysis Intelligence Index），允许为 {@code null}。
     *                      本类对它不做归一化，与下方各 0~1 基准分不是同一量纲，不可按百分制解读；
     *                      源字段 {@code intelligenceIndex}，是否可信另看 {@code intelligenceIndexEstimated}
     * @param outputSpeedTps 输出速度，单位 <b>Token / 秒</b>，允许为 {@code null}。源字段
     *                      {@code medianOutputTokensPerSecond}，取的是实测中位吞吐而非峰值
     * @param latencyFirstTokenSeconds 首 Token 延迟，单位 <b>秒</b>，允许为 {@code null}。源字段
     *                      {@code medianTimeToFirstTokenSeconds}，是中位耗时不是最坏值；端到端总耗时是数据源的另一个字段，不在本记录内
     * @param iconUrl      厂商图标绝对地址（HTTP/HTTPS URL），允许为 {@code null}。源字段可能只是文件名或站内相对路径，
     *                      抓取侧已统一补全为 {@code https://artificialanalysis.ai/img/logos/<file>} 形式，可直接渲染；
     *                      落盘时经 {@code esc} 转义反斜杠与双引号，读回时按 JSON 字符串还原
     * @param reasoning    是否为推理（深度思考 / CoT）模型，允许为 {@code null}。源字段 {@code reasoningModel}；
     *                      {@code null} 表示数据源未给出，应按未知处理而非按不支持处理
     * @param webSearch    是否支持联网检索（内置搜索 / 在线检索），允许为 {@code null}。数据源无显式字段，
     *                      抓取侧按模型名包含 {@code search} / {@code online} 推断，人工配置可覆盖；
     *                      从旧磁盘快照恢复时可能为 {@code null}
     * @param imageInput   是否支持图片识别（视觉输入），允许为 {@code null}。同样无显式字段，按模型名规律
     *                      （{@code vision}、{@code -v} 结尾等）匹配推断，人工配置可覆盖
     * @param functionCalling 是否支持函数调用（工具调用），允许为 {@code null}。源字段 {@code functionCalling}，
     *                      能力标签 {@code function_calling} 由本项为真时追加
     * @param cacheHitPrice 提示词缓存命中（读缓存）输入价，单位 <b>USD / 百万 Token</b>，允许为 {@code null}。
     *                      源字段 {@code cacheHitPrice}，通常约为 {@code inputPerMillion} 的一半；不支持缓存的模型为 {@code null}
     * @param cacheWritePrice 提示词缓存写入价，单位 <b>USD / 百万 Token</b>，允许为 {@code null}。
     *                      源字段 {@code cacheWritePrice}，多数模型不提供写入计价，此时为 {@code null}
     * @param contextWindowTokens 上下文窗口上限，单位 <b>Token</b>，允许为 {@code null}。源字段
     *                      {@code contextWindowTokens}，如 {@code 262144} 表示 256K；超长上下文模型可能超出 int 范围故用
     *                      {@code Long}，快照恢复时只取整数部分
     * @param hleScore     HLE（Humanity's Last Exam，人类最后考试）得分，取值 <b>0~1 的小数，不是百分制</b>，
     *                      允许为 {@code null}。源字段 {@code hle}；未参加该基准的模型为空，不要按 0 解读
     * @param omniscienceIndex 全知指数（Omniscience Index），允许为 {@code null} 且<b>可为负</b>——它是知识准确率与幻觉水平的差值，
     *                      负值代表幻觉多于准确，因此不能套用 0~1 区间。源字段 {@code omniscience}；其两个分量另见
     *                      {@code omniscienceAccuracy} 与 {@code omniscienceNonHallucination}
     * @param gpqaScore    GPQA 得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段 {@code gpqa}
     * @param aime25Score  AIME 2025 数学基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段 {@code aime25}
     * @param livecodebenchScore LiveCodeBench 编程基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段
     *                      {@code livecodebench}
     * @param scicodeScore SciCode 科学编程基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段 {@code scicode}
     * @param ifbenchScore IFBench 指令遵循基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段 {@code ifbench}
     * @param critptScore  CritPt 物理基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段 {@code critpt}
     * @param mmmuProScore MMMU-Pro 多模态基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段 {@code mmmuPro}
     * @param tau2Score    Tau2-Bench 工具调用基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段 {@code tau2}
     * @param tauBankingScore Tau-Banking 银行场景基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段
     *                      {@code tauBanking}
     * @param lcrScore     LCR 长上下文推理得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段 {@code lcr}
     * @param terminalbenchHardScore Terminal-Bench Hard 得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段
     *                      {@code terminalbenchHard}
     * @param terminalBench21Score Terminal-Bench 2.1 得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段
     *                      {@code terminalBench21}；与 {@code terminalBench40Score} 分属不同基准版本，跨版本分数不可直接比较
     * @param terminalBench40Score Terminal-Bench 4.0 得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段
     *                      {@code terminalBench40}
     * @param automationBenchScore AutomationBench 自动化基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为
     *                      {@code null}。源字段 {@code automationBench}
     * @param gdpvalNormalizedScore GDPval 归一化得分，取值 <b>0~1 的小数</b>（数据源已归一化，故非百分制），允许为
     *                      {@code null}。源字段 {@code gdpvalNormalized}
     * @param briefcaseScore Briefcase 专业任务基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段
     *                      {@code briefcase}
     * @param harveyLabScore Harvey Lab 法律任务基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段
     *                      {@code harveyLab}
     * @param apexAgentsScore Apex Agents 基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段
     *                      {@code apexAgents}
     * @param itbenchSreScore ITBench SRE 运维基准得分，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。源字段
     *                      {@code itbenchSre}
     * @param omniscienceAccuracy 全知指数中的知识准确率分量，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。
     *                      源字段 {@code omniscienceAccuracy}，与 {@code omniscienceIndex}（准确率减幻觉率）配套
     * @param omniscienceNonHallucination 全知指数中的非幻觉率分量，取值 <b>0~1 的小数，不是百分制</b>，允许为 {@code null}。
     *                      源字段 {@code omniscienceNonHallucination}，越高表示编造越少
     * @param elo          Artificial Analysis Elo 评分，允许为 {@code null}。<b>既不是 0~1 小数也不是百分制</b>，
     *                      是相对分而非绝对量，只适合横向排序，不能按固定区间解读。源字段 {@code elo}
     * @param costPerTask  单任务平均成本，单位 <b>USD / 次任务</b>（不是每 Token 单价，也不是每次调用价），允许为 {@code null}。
     *                      源字段 {@code costPerTask}，折算口径由数据源在其基准任务上定义，本类不参与计算，
     *                      仅可用于跨模型比价，不能当作精确计费依据
     * @param jsonMode     是否支持 JSON 输出（结构化输出）模式，允许为 {@code null}。源字段 {@code jsonMode}；
     *                      {@code null} 表示数据源未给出
     * @param openaiCompatible 是否兼容 OpenAI 协议（可用 OpenAI SDK / 兼容端点接入），允许为 {@code null}。源字段
     *                      {@code openaiCompatible}
     * @param intelligenceIndexEstimated {@code intelligenceIndex} 是否为估算值，允许为 {@code null}。源字段
     *                      {@code intelligenceIndexIsEstimated}；为 {@code true} 表示该分并非实测跑分而是推算，
     *                      与 {@code null}（数据源未标注）含义不同
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
            Long contextWindowTokens,
            /**
             * HLE（人类最后考试）得分（0~1）
             */
            BigDecimal hleScore,
            /**
             * 全知指数（知识准确-幻觉，可为负）
             */
            BigDecimal omniscienceIndex,
            /**
             * GPQA 得分（0~1）
             */
            BigDecimal gpqaScore,
            /**
             * AIME 2025 数学基准得分（0~1）
             */
            BigDecimal aime25Score,
            /**
             * LiveCodeBench 编程基准得分（0~1）
             */
            BigDecimal livecodebenchScore,
            /**
             * SciCode 科学编程基准得分（0~1）
             */
            BigDecimal scicodeScore,
            /**
             * IFBench 指令遵循基准得分（0~1）
             */
            BigDecimal ifbenchScore,
            /**
             * CritPt 物理基准得分（0~1）
             */
            BigDecimal critptScore,
            /**
             * MMMU-Pro 多模态基准得分（0~1）
             */
            BigDecimal mmmuProScore,
            /**
             * Tau2-Bench 工具调用基准得分（0~1）
             */
            BigDecimal tau2Score,
            /**
             * Tau-Banking 银行场景基准得分（0~1）
             */
            BigDecimal tauBankingScore,
            /**
             * LCR 长上下文推理得分（0~1）
             */
            BigDecimal lcrScore,
            /**
             * Terminal-Bench Hard 得分（0~1）
             */
            BigDecimal terminalbenchHardScore,
            /**
             * Terminal-Bench 2.1 得分（0~1）
             */
            BigDecimal terminalBench21Score,
            /**
             * Terminal-Bench 4.0 得分（0~1）
             */
            BigDecimal terminalBench40Score,
            /**
             * AutomationBench 自动化基准得分（0~1）
             */
            BigDecimal automationBenchScore,
            /**
             * GDPval 归一化得分（0~1）
             */
            BigDecimal gdpvalNormalizedScore,
            /**
             * Briefcase 专业任务基准得分（0~1）
             */
            BigDecimal briefcaseScore,
            /**
             * Harvey Lab 法律任务基准得分（0~1）
             */
            BigDecimal harveyLabScore,
            /**
             * Apex Agents 基准得分（0~1）
             */
            BigDecimal apexAgentsScore,
            /**
             * ITBench SRE 运维基准得分（0~1）
             */
            BigDecimal itbenchSreScore,
            /**
             * 全知指数-准确率（0~1）
             */
            BigDecimal omniscienceAccuracy,
            /**
             * 全知指数-非幻觉率（0~1）
             */
            BigDecimal omniscienceNonHallucination,
            /**
             * Artificial Analysis Elo 评分
             */
            BigDecimal elo,
            /**
             * 单任务平均成本（USD）
             */
            BigDecimal costPerTask,
            /**
             * 是否支持 JSON 输出模式
             */
            Boolean jsonMode,
            /**
             * 是否兼容 OpenAI 协议
             */
            Boolean openaiCompatible,
            /**
             * 智能指数是否为估算值
             */
            Boolean intelligenceIndexEstimated) {

        /**
         * 规范构造器：归一化键做空值校验。
         *
         * <p>value class 前置条件——值类要求空值敌对；其余组件取自外部指标源，
         * 允许为空（见 {@code putIfNotNull} 与快照读写），故不做校验。</p>
         *
         * @param normalizedKey 归一化键
         */
        public MetricsRecord {
            normalizedKey = Objects.requireNonNull(normalizedKey, "normalizedKey 不能为 null");
        }
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
