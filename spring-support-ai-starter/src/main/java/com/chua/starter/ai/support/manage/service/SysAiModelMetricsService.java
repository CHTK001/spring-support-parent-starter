// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.ai.chat.ChatClientIndicator;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.indicator.ChatClientIndicators;
import com.chua.common.support.ai.chat.indicator.HttpChatClientIndicator;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.manage.mapper.SysAiModelMetricsMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiModelMetrics;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.service.AiModelMetricsEnrichmentService;
import com.chua.starter.ai.support.service.AiModelMetricsEnrichmentService.MetricsRecord;
import com.chua.starter.ai.support.util.ModelMetricKeyMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全量模型指标库服务：全量同步、缓存读取、手动修改/锁定、计费取价回退、分页检索。
 *
 * <p>指标源（Artificial Analysis）的全量数据通过 {@link #syncAll()} 落库；
 * 行内被 {@code manual_locked} 标记的字段在同步时跳过，不被覆盖。计费在官方定价
 * 缺失时，通过 {@link #findForEstimate(String)} 关联本表价格做预估。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiModelMetricsService {

    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "aiModelMetrics";

    /**
     * 数据源标识
     */
    public static final String SOURCE = "artificialanalysis";

    /**
     * manualLocked 序列化：wrapper.set 不自动应用字段 TypeHandler，需手动转 JSON
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper LOCK_OM =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 可手动编辑/锁定的数值字段 → 列引用
     */
    private static final Map<String, SFunction<SysAiModelMetrics, ?>> FIELD_COL = fieldCol();

    /**
     * 指标库 映射器
     */
    private final SysAiModelMetricsMapper metricsMapper;

    /**
     * 提供者模型 映射器（同步价格后回写模型卡片的指标 JSON）
     */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 服务商 映射器（按已配置服务商枚举 indicator 消费）
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 指标富集服务（全量数据源）
     */
    private final AiModelMetricsEnrichmentService enrichment;

    /**
     * 用量 映射器（按 provider+model 取本地平均输出速度）
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 指标源全量同步：指标源全量记录 → 插入/更新，行内锁定字段跳过。
     *
     * @return 处理的记录条数
     */
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public int syncAll() {
        List<MetricsRecord> records = enrichment.all();
        if (records == null || records.isEmpty()) {
            log.warn("[ModelMetrics] 指标源无数据，跳过全量同步");
            return 0;
        }
        Map<String, SysAiModelMetrics> existing = new HashMap<>();
        for (SysAiModelMetrics m : metricsMapper.selectList(null)) {
            existing.put(m.getModelKey(), m);
        }
        long now = System.currentTimeMillis();
        int changed = 0;
        for (MetricsRecord r : records) {
            String key = r.normalizedKey();
            SysAiModelMetrics cur = existing.get(key);
            if (cur == null) {
                metricsMapper.insert(toEntity(r, now));
                changed++;
            } else {
                Set<String> locked = cur.getManualLocked() == null
                        ? Set.of() : new HashSet<>(cur.getManualLocked());
                LambdaUpdateWrapper<SysAiModelMetrics> uw = new LambdaUpdateWrapper<>();
                uw.eq(SysAiModelMetrics::getId, cur.getId());
                buildSyncUpdate(r, locked, now, uw);
                metricsMapper.update(null, uw);
                changed++;
            }
        }
        log.info("[ModelMetrics] 全量同步完成，处理 {} 条（指标源 {} 条）", changed, records.size());
        return changed;
    }

    /**
     * 按归一化键精确查询（缓存）。
     *
     * @param key 归一化模型键
     * @return 指标记录，未找到返回 空
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "#key", unless = "#result == null")
    public SysAiModelMetrics getByKey(String key) {
        return metricsMapper.selectOne(new LambdaQueryWrapper<SysAiModelMetrics>()
                .eq(SysAiModelMetrics::getModelKey, key).last("limit 1"));
    }

    /**
     * 全表读取（供调用方在内存里建 模型键 → 指标行 的索引）。
     *
     * <p>列表接口按模型逐个 {@link #getByKey(String)} 查询时，在跨网络访问数据库
     * 的环境下会把一次请求放大成上百次往返（实测远端 185 个模型耗时数分钟）。
     * 本方法一次取全表（千行级别），由调用方自行建索引并按需缓存。</p>
     *
     * @return 全部指标行，按 id 升序
     */
    public List<SysAiModelMetrics> listAll() {
        return metricsMapper.selectList(new LambdaQueryWrapper<SysAiModelMetrics>()
                .orderByAsc(SysAiModelMetrics::getId));
    }

    /**
     * 用量回填：按 服务商+模型 对全部本地用量的 token 输出速度取平均，写入
     * {@code sys_ai_model_metrics.avg_output_speed_tps}（归一化键关联：
     * {@code provider/model} → normalizeKey）。
     * <p>
     * 无本地用量的模型不出现（行保持 NULL，前端/计费回退到系统的
     * {@code output_speed_tps}）。</p>
     *
     * @return 回填的指标数
     */
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public int syncAvgOutputSpeedTps() {
        List<SysAiUsageMapper.ModelSpeedRow> rows = usageMapper.selectModelAvgSpeedTps(null, null);
        if (rows == null || rows.isEmpty()) {
            log.info("[ModelMetrics] 无本地用量记录，avg_output_speed_tps 不回填");
            return 0;
        }
        long now = System.currentTimeMillis();
        int updated = 0;
        for (SysAiUsageMapper.ModelSpeedRow row : rows) {
            String provider = row.provider();
            String model = row.model();
            if (provider == null || provider.isBlank() || model == null || model.isBlank()) {
                continue;
            }
            // 归一化键：provider/model → 取最后一段 / 并合并非字母数字字符（与指标源同口径）
            String key = truncate(enrichment.normalizeKey(provider + "/" + model), 160);
            if (key.isEmpty()) {
                continue;
            }
            SysAiModelMetrics cur = getByKey(key);
            if (cur == null) {
                continue;
            }
            LambdaUpdateWrapper<SysAiModelMetrics> uw = new LambdaUpdateWrapper<>();
            uw.eq(SysAiModelMetrics::getId, cur.getId());
            uw.set(SysAiModelMetrics::getAvgOutputSpeedTps, BigDecimal.valueOf(row.avgSpeedTps()));
            uw.set(SysAiModelMetrics::getUpdateTime, now);
            if (metricsMapper.update(null, uw) > 0) {
                updated++;
            }
        }
        log.info("[ModelMetrics] 本地平均输出速度回填完成：{} 行（原始分组 {} 组）", updated, rows.size());
        return updated;
    }

    /**
     * 计费取价回退：按模型名精确匹配，未命中时匹配同模型变体。
     *
     * <p>兜底匹配只接受「头尾多出版本/日期/别名后缀」的候选（见
     * {@link ModelMetricKeyMatcher}）：原实现用双向 {@code contains}，
     * 会把 {@code glm-5-3-flash-free} 的取价算到 {@code glm-5-3} 上、
     * 或把 {@code gemini-3-5-flash-lite} 的价格当成 {@code gemini-3-5-flash}，
     * 计费估算直接算错。</p>
     *
     * @param modelId 模型标识
     * @return 指标记录，未找到返回 空
     */
    public SysAiModelMetrics findForEstimate(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        String key = enrichment.normalizeKey(modelId);
        if (!StringUtils.hasText(key)) {
            return null;
        }
        SysAiModelMetrics exact = metricsMapper.selectOne(new LambdaQueryWrapper<SysAiModelMetrics>()
                .eq(SysAiModelMetrics::getModelKey, key).last("limit 1"));
        if (exact != null) {
            return exact;
        }
        List<SysAiModelMetrics> all = metricsMapper.selectList(null);
        return ModelMetricKeyMatcher.selectNearest(key, all, SysAiModelMetrics::getModelKey);
    }

    /**
     * 分页检索（设置页全量表格）。
     *
     * @param current 当前页
     * @param size    每页条数
     * @param keyword 模型名/厂商关键字（可空）
     * @return 分页结果
     */
    public Page<SysAiModelMetrics> page(long current, long size, String keyword) {
        LambdaQueryWrapper<SysAiModelMetrics> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(q -> q.like(SysAiModelMetrics::getModelKey, kw)
                    .or().like(SysAiModelMetrics::getModelName, kw)
                    .or().like(SysAiModelMetrics::getProvider, kw));
        }
        w.orderByDesc(SysAiModelMetrics::getIntelligenceIndex)
                .orderByAsc(SysAiModelMetrics::getModelKey);
        return metricsMapper.selectPage(new Page<>(current, size), w);
    }

    /**
     * 手动修改并锁定字段。
     *
     * @param key      归一化模型键
     * @param values   字段 → 数值
     * @param locked   要锁定的字段集合（完整覆盖）
     * @param operator 操作人
     * @return 更新后的指标记录
     */
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public SysAiModelMetrics applyManual(String key, Map<String, BigDecimal> values,
                                         List<String> locked, String operator) {
        SysAiModelMetrics cur = metricsMapper.selectOne(new LambdaQueryWrapper<SysAiModelMetrics>()
                .eq(SysAiModelMetrics::getModelKey, key).last("limit 1"));
        if (cur == null) {
            throw new IllegalArgumentException("指标记录不存在: " + key);
        }
        long now = System.currentTimeMillis();
        List<String> oldLocked = cur.getManualLocked() == null
                ? new ArrayList<>() : new ArrayList<>(cur.getManualLocked());
        List<String> normLocked = new ArrayList<>();
        if (locked != null) {
            for (String f : locked) {
                if (FIELD_COL.containsKey(f) && !normLocked.contains(f)) {
                    normLocked.add(f);
                }
            }
        }
        if (values != null) {
            for (String field : values.keySet()) {
                if (FIELD_COL.containsKey(field) && !normLocked.contains(field)) {
                    normLocked.add(field);
                }
            }
        }
        // 解锁字段：立即用指标源当前值回填，无需等待下次同步
        List<String> unlocked = new ArrayList<>();
        for (String f : oldLocked) {
            if (!normLocked.contains(f)) {
                unlocked.add(f);
            }
        }
        MetricsRecord aaRec = unlocked.isEmpty() ? null : enrichment.getPricingRecord(key);
        applyManualUpdate(cur, values, normLocked, unlocked, aaRec, operator, now);
        return metricsMapper.selectById(cur.getId());
    }

    /**
     * 同步时需要回写到 {@code sys_ai_provider_model.sys_ai_provider_model_metrics}
     * 的指标键（前端模型卡片与模型选择器读取的就是这些键）。
     */
    private static final String[] PROVIDER_MODEL_METRIC_KEYS = {
            "price_input_per_musd", "price_output_per_musd",
            "price_cache_hit_per_musd", "price_cache_write_per_musd",
            "context_window_tokens", "max_output_tokens",
            "reasoning", "web_search", "image_input", "audio_input", "video_input",
            "pdf_input", "attachment", "temperature", "function_calling",
            "json_mode", "structured_output", "open_weights", "intelligence_index",
            "model_name", "model_family", "knowledge_cutoff", "release_date", "last_updated",
            "reasoning_efforts", "reasoning_option_types"
    };

    /**
     * 从 {@link ChatClientIndicator} 同步价格与指标，入库 {@code sys_ai_model_metrics}，
     * 并把同一份指标回写到该服务商已配置的模型行（{@code sys_ai_provider_model}），
     * 让前端模型卡片在同步后立即显示新价格 / 上下文 / 能力。
     *
     * <p>遍历 {@code indicator.modelMetrics()}（模型键 → 指标键/值 Map），按归一化键
     * 做 upsert：已存在的行只更新未被 {@code manual_locked} 锁定的字段；不存在的行
     * 插入新记录。同一快照里可能同时存在 {@code provider/model} 全路径与短 slug
     * 两种键（OpenRouter / Pollinations 等），按归一化键去重，避免同一模型被写两次。</p>
     *
     * <p>缺省语义：指标 Map 中<b>不存在的键不落库</b>，不会把已有的上下文窗口 /
     * 缓存价格清空；只有显式提供（含显式 0，如免费档）的字段才覆盖。</p>
     *
     * @param indicator  当前服务商的指标实现
     * @param providerId 服务商 ID（写 provider 列）
     * @return 同步统计（更新数 / 插入数 / 回写的模型行数）
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public SyncResult syncFromIndicator(ChatClientIndicator indicator, String providerId) {
        if (indicator == null) {
            return new SyncResult(0, 0, 0);
        }
        Map<String, Map<String, Object>> modelMetrics = indicator.modelMetrics();
        if (modelMetrics == null || modelMetrics.isEmpty()) {
            log.info("[ModelMetrics] indicator={} 无数据，跳过同步", indicator.source());
            return new SyncResult(0, 0, 0);
        }
        String source = truncate(indicator.source(), 40);
        Long capturedAt = indicator.capturedAt();
        long now = capturedAt == null ? System.currentTimeMillis() : capturedAt;

        // 一次查全表，避免循环内逐条查
        Map<String, SysAiModelMetrics> existing = new HashMap<>();
        for (SysAiModelMetrics m : metricsMapper.selectList(null)) {
            if (m.getModelKey() != null) {
                existing.put(m.getModelKey(), m);
            }
        }

        // 归一化键去重：同一模型可能以全路径 + slug 两种键出现
        Set<String> processed = new HashSet<>(modelMetrics.size());
        int updated = 0;
        int inserted = 0;

        for (Map.Entry<String, Map<String, Object>> entry : modelMetrics.entrySet()) {
            String modelId = entry.getKey();
            Map<String, Object> metrics = entry.getValue();
            if (modelId == null || metrics == null) {
                continue;
            }
            String key = truncate(enrichment.normalizeKey(modelId), 160);
            if (key.isEmpty() || !processed.add(key)) {
                continue;
            }
            SysAiModelMetrics cur = existing.get(key);

            if (cur == null) {
                // 插入新记录
                SysAiModelMetrics e = toIndicatorEntity(key, modelId, providerId, metrics, source, now);
                metricsMapper.insert(e);
                inserted++;
            } else {
                // 更新已有记录（锁定字段跳过）
                Set<String> locked = cur.getManualLocked() == null
                        ? Set.of() : new HashSet<>(cur.getManualLocked());
                LambdaUpdateWrapper<SysAiModelMetrics> uw = new LambdaUpdateWrapper<>();
                uw.eq(SysAiModelMetrics::getId, cur.getId());
                buildIndicatorUpdate(metrics, locked, source, now, uw);
                metricsMapper.update(null, uw);
                updated++;
            }
        }

        int providerModels = applyToProviderModels(providerId, modelMetrics, source,
                indicator.sourceUrl(), now);
        log.info("[ModelMetrics] indicator={} 同步完成：更新 {}，插入 {}，回写模型行 {}",
                indicator.source(), updated, inserted, providerModels);
        return new SyncResult(updated, inserted, providerModels);
    }

    /**
     * 遍历已配置服务商，逐个同步其 {@link ChatClientIndicator} 指标到指标库。
     *
     * <p>这是 {@code ModelMetricsPushTask} 的<b>指标通道入口</b>：把人工「自动获取」
     * 才触发的 {@link #syncFromIndicator(ChatClientIndicator, String)} 引入定时链路，
     * 让已配置服务商的价格/能力/思考档位随调度自动落库，前端模型卡片在同步后
     * 立即显示新数据。来源解析与手动接口同一套
     * {@link ChatClientIndicators} 映射，避免两处登记不一致。</p>
     *
     * <p>容错语义：单个服务商解析或同步失败只记录日志，不影响其它服务商；
     * 无指标端点的服务商（如 Ollama / ONNX）直接跳过，不视为失败。</p>
     *
     * @return 成功同步的服务商数（有数据落库）
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public int syncFromConfiguredProviders() {
        List<SysAiProvider> providers = providerMapper.selectList(new LambdaQueryWrapper<>());
        if (providers.isEmpty()) {
            return 0;
        }
        int ok = 0;
        int skipped = 0;
        for (SysAiProvider provider : providers) {
            ChatClientIndicator indicator;
            try {
                indicator = resolveIndicator(provider);
            } catch (Exception e) {
                log.warn("[ModelMetrics] provider={} indicator 解析失败: {}", provider.getProviderId(), e.getMessage());
                skipped++;
                continue;
            }
            if (indicator == null) {
                skipped++;
                continue;
            }
            try {
                SyncResult result = syncFromIndicator(indicator, provider.getProviderId());
                if (result.getUpdated() + result.getInserted() > 0) {
                    ok++;
                }
            } catch (Exception e) {
                log.warn("[ModelMetrics] provider={} indicator 同步失败: {}", provider.getProviderId(), e.getMessage());
            }
        }
        log.info("[ModelMetrics] 已配置服务商 indicator 同步完成：成功 {} 家，跳过 {} 家，共 {} 家",
                ok, skipped, providers.size());
        return ok;
    }

    /**
     * 按服务商平台解析对应的 {@link ChatClientIndicator}，与前端「自动获取」
     * 走同一套 {@link ChatClientIndicators} 映射；配置了
     * {@code extra.metrics_url} 且无内置实现时用 {@link HttpChatClientIndicator} 兜底。
     *
     * @param entity 服务商行
     * @return indicator 实现或空（无可用指标端点）
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
        return ChatClientSetting.builder()
                .provider(entity.getPlatform() == null || entity.getPlatform().isBlank()
                        ? entity.getProviderId() : entity.getPlatform())
                .appKey(firstApiKey(entity))
                .baseUrl(entity.getBaseUrl())
                .metricsUrl(extraString(entity.getExtra(), "metrics_url"))
                .proxy(entity.getProxy())
                .build();
    }

    /**
     * 取服务商自身凭证中的第一个 API Key（无则返回 {@code null}）。
     * @param entity 参数
     * @return 返回值
    */
    private String firstApiKey(SysAiProvider entity) {
        if (entity.getCredentials() == null || entity.getCredentials().isEmpty()) {
            return null;
        }
        Object single = entity.getCredentials().get("api_key");
        if (single instanceof String s && !s.isBlank()) {
            return s;
        }
        Object multi = entity.getCredentials().get("api_keys");
        if (multi instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
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
     * 把指标快照回写到服务商已配置的模型行。
     *
     * <p>模型行按「原始模型名精确匹配 → 归一化键匹配」两级查找；只写白名单键，
     * 不覆盖用户手动填写的上下文窗口 / 输出上限（这两个字段仅在为空时回填）。</p>
     *
     * @param providerId   服务商标识
     * @param modelMetrics 指标快照
     * @param source       数据源标识
     * @param sourceUrl    数据源地址
     * @param capturedAt   采集时间
     * @return 实际更新的模型行数
     */
    private int applyToProviderModels(String providerId,
                                      Map<String, Map<String, Object>> modelMetrics,
                                      String source, String sourceUrl, long capturedAt) {
        if (providerId == null || providerId.isBlank() || modelMetrics.isEmpty()) {
            return 0;
        }
        List<SysAiProviderModel> rows = providerModelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId));
        if (rows.isEmpty()) {
            return 0;
        }
        Map<String, Map<String, Object>> byExact = new HashMap<>(modelMetrics.size());
        Map<String, Map<String, Object>> byNormalized = new HashMap<>(modelMetrics.size());
        for (Map.Entry<String, Map<String, Object>> entry : modelMetrics.entrySet()) {
            String id = entry.getKey();
            if (id == null || entry.getValue() == null) {
                continue;
            }
            byExact.putIfAbsent(id, entry.getValue());
            String key = truncate(enrichment.normalizeKey(id), 160);
            if (!key.isEmpty()) {
                byNormalized.putIfAbsent(key, entry.getValue());
            }
        }

        int changed = 0;
        long now = System.currentTimeMillis();
        for (SysAiProviderModel row : rows) {
            if (row.getModel() == null) {
                continue;
            }
            Map<String, Object> metrics = byExact.get(row.getModel());
            if (metrics == null) {
                metrics = byNormalized.get(truncate(enrichment.normalizeKey(row.getModel()), 160));
            }
            if (metrics == null) {
                continue;
            }
            Map<String, Object> current = row.getMetrics() == null
                    ? new LinkedHashMap<>(24)
                    : new LinkedHashMap<>(row.getMetrics());
            enrichRowFromMetrics(row, current, metrics, source, sourceUrl, capturedAt);
            row.setUpdatedAt(now);
            // 复合主键：必须同时按 providerId + model 限定，避免跨服务商误更新
            providerModelMapper.update(row, new LambdaUpdateWrapper<SysAiProviderModel>()
                    .eq(SysAiProviderModel::getProviderId, providerId)
                    .eq(SysAiProviderModel::getModel, row.getModel()));
            changed++;
        }
        return changed;
    }

    /**
     * 用单条指标快照回填一个模型行（metrics JSON 白名单合并 + 实体列空值回填）。
     *
     * <p>同时用于价格同步（{@link #applyToProviderModels}）与新增/导入模型时的
     * 指标补齐（{@code ApiProviderController.enrichFromMetrics}）：指标侧统一口径，
     * 实体列仅在空时回填，不覆盖管理员已填的值。</p>
     *
     * @param row        目标模型行（就地修改）
     * @param current    待写回的 metrics JSON（就地合并白名单键）
     * @param metrics    指标快照（Models.dev 等 indicator 产出的标准键）
     * @param source     数据源标识（可空）
     * @param sourceUrl  数据源地址（可空）
     * @param capturedAt 采集时间戳
     */
    public void enrichRowFromMetrics(SysAiProviderModel row, Map<String, Object> current,
                                     Map<String, Object> metrics, String source, String sourceUrl,
                                     long capturedAt) {
        for (String key : PROVIDER_MODEL_METRIC_KEYS) {
            Object value = metrics.get(key);
            if (value != null) {
                current.put(key, value);
            }
        }
        // 能力键兼容：指标侧叫 reasoning，模型行历史口径叫 reasoning_model
        if (metrics.get("reasoning") instanceof Boolean reasoning) {
            current.put("reasoning_model", reasoning);
        }
        if (source != null && !source.isBlank()) {
            current.put("metrics_source", source);
        }
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            current.put("metrics_source_url", sourceUrl);
        }
        current.put("metrics_captured_at", capturedAt);

        Object context = metrics.get("context_window_tokens");
        if (row.getContextWindow() == null && context instanceof Number number && number.longValue() > 0) {
            row.setContextWindow((int) Math.min(number.longValue(), Integer.MAX_VALUE));
        }
        Object maxOutput = metrics.get("max_output_tokens");
        if (row.getMaxTokens() == null && maxOutput instanceof Number number && number.longValue() > 0) {
            row.setMaxTokens((int) Math.min(number.longValue(), Integer.MAX_VALUE));
        }
        // 指标回填实体列：family / knowledge / release_date / last_updated / modalities /
        // open_weights / attachment / reasoning / tool_call / temperature ——
        // 仅在实体列当前为空时回填，不覆盖管理员已填的值
        fillIfBlank(row::getFamily, value -> row.setFamily(value), str(metrics, "model_family"));
        fillIfBlank(row::getKnowledge, row::setKnowledge, str(metrics, "knowledge_cutoff"));
        fillIfBlank(row::getReleaseDate, row::setReleaseDate, str(metrics, "release_date"));
        // last_updated：优先接口数据的 last_updated，缺失则用采集时间（capturedAt）
        fillIfBlank(row::getLastUpdated, row::setLastUpdated,
                firstNonBlank(str(metrics, "last_updated"), isoOf(capturedAt)));
        if (row.getOpenWeights() == null && bool(metrics, "open_weights") != null) {
            row.setOpenWeights(bool(metrics, "open_weights"));
        }
        if (row.getAttachment() == null && bool(metrics, "attachment") != null) {
            row.setAttachment(bool(metrics, "attachment"));
        }
        if (row.getReasoning() == null && bool(metrics, "reasoning") != null) {
            row.setReasoning(bool(metrics, "reasoning"));
        }
        if (row.getToolCall() == null && bool(metrics, "function_calling") != null) {
            row.setToolCall(bool(metrics, "function_calling"));
        }
        if (row.getTemperatureSupported() == null && bool(metrics, "temperature") != null) {
            row.setTemperatureSupported(bool(metrics, "temperature"));
        }
        // modalities：由 input 模态（image/audio/video/pdf）与 output_modalities 组装
        if (row.getModalities() == null) {
            Map<String, Object> modalities = modalitiesOf(metrics);
            if (!modalities.isEmpty()) {
                row.setModalities(modalities);
            }
        }
        // 思考档位：指标 reasoning_efforts（Models.dev reasoning_options 解析）→
        // capabilities[].thinking_efforts，仅在未配置时回填（与 applyThinkingEfforts 同存储口径）
        List<?> efforts = listOf(metrics.get("reasoning_efforts"));
        if (!efforts.isEmpty() && !hasThinkingEfforts(row)) {
            applyThinkingEfforts(row, new ArrayList<>(efforts.stream()
                    .map(String::valueOf).collect(java.util.stream.Collectors.toList())));
        }
        row.setMetrics(current);
    }

    /**
     * 模型行是否已配置思考档位（capabilities[].thinking_efforts 非空）。
     * @param row 参数
     * @return 返回值
    */
    private static boolean hasThinkingEfforts(SysAiProviderModel row) {
        List<Map<String, Object>> caps = row.getCapabilities();
        if (caps == null) {
            return false;
        }
        for (Map<String, Object> cap : caps) {
            Object efforts = cap.get("thinking_efforts");
            if (efforts instanceof List<?> list && !list.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
    /**
     * 把思考档位写入行内 capabilities[].thinking_efforts（与 ApiProviderController.applyThinkingEfforts 同款）。
     * @param efforts 参数
     * @param entity 参数
     * @return 返回值
    */
    private static void applyThinkingEfforts(SysAiProviderModel entity, List<String> efforts) {
        if (efforts == null) {
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
        node.put("thinking_efforts", efforts);
    }

    /**
     * 宽松转字符串列表：非数组返回空列表。
     * @param value 对象
     * @return 返回值
    */
    private static List<Object> listOf(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    /**
     * 实体列为空时回填。
     *
     * @param getter    当前值读取
     * @param setter    回填写入
     * @param value     候选值（空则不写）
     */
    private static void fillIfBlank(java.util.function.Supplier<String> getter,
                                    java.util.function.Consumer<String> setter,
                                    String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String current = getter.get();
        if (current == null || current.isBlank()) {
            setter.accept(value);
        }
    }

    /**
     * 取指标字符串（掐头去尾）。
     * @param Map<String 参数
     * @param key 字符串
     * @param metrics 参数
     * @return 返回值
    */
    private static String str(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    /**
     * 取指标布尔。
     * @param Map<String 参数
     * @param key 字符串
     * @param metrics 参数
     * @return 参数
    */
    private static Boolean bool(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return "true".equalsIgnoreCase(s) || "1".equals(s);
        }
        return null;
    }

    /**
     * 毫秒时间戳 → ISO 时间（最后更新时间兜底口径）。
     * @param millis 长整数
     * @return 返回值
    */
    private static String isoOf(long millis) {
        try {
            return java.time.Instant.ofEpochMilli(millis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate() + (millis > 0 ? " " + java.time.LocalTime.MIDNIGHT : "");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 首非空值。
     * @param a 字符串
     * @param b 字符串
     * @return 返回值
    */
    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    /**
     * 由 input 模态与 output_modalities 组装 modalities Map。
     *
     * @param metrics 指标 Map
     * @return 非空时 {input:[...], output:[...]} 结构
     */
    private static Map<String, Object> modalitiesOf(Map<String, Object> metrics) {
        Map<String, Object> modalities = new LinkedHashMap<>(4);
        List<String> inputs = new ArrayList<>(4);
        inputs.add("text");
        Boolean image = bool(metrics, "image_input");
        if (Boolean.TRUE.equals(image)) {
            inputs.add("image");
        }
        Boolean audio = bool(metrics, "audio_input");
        if (Boolean.TRUE.equals(audio)) {
            inputs.add("audio");
        }
        Boolean video = bool(metrics, "video_input");
        if (Boolean.TRUE.equals(video)) {
            inputs.add("video");
        }
        Boolean pdf = bool(metrics, "pdf_input");
        if (Boolean.TRUE.equals(pdf)) {
            inputs.add("pdf");
        }
        modalities.put("input", inputs);
        Object outputs = metrics.get("output_modalities");
        if (outputs instanceof List<?> outputList && !outputList.isEmpty()) {
            modalities.put("output", new ArrayList<>(outputList));
        } else {
            modalities.put("output", List.of("text"));
        }
        return modalities;
    }

    /**
     * 构建指标来源入参。
     *
     * @param key        归一化键
     * @param modelId    原始模型 ID
     * @param providerId 服务商 ID
     * @param metrics    指标键/值 Map
     * @param source     数据源标识
     * @param now        采集时间戳
     * @return 实体
     */
    private SysAiModelMetrics toIndicatorEntity(String key, String modelId, String providerId,
                                                 Map<String, Object> metrics, String source, long now) {
        SysAiModelMetrics e = new SysAiModelMetrics();
        e.setModelKey(key);
        e.setModelName(modelId);
        e.setProvider(providerId);
        e.setSource(source);
        e.setCreateTime(now);
        e.setUpdateTime(now);
        applyMetricsToEntity(e, metrics);
        return e;
    }

    /**
     * 把指标键/值 Map 写入实体字段（只做赋值，不做锁定判断，用于新增行）。
     * @param Map<String 参数
     * @param e 参数
     * @param m 参数
     * @return 返回值
    */
    private void applyMetricsToEntity(SysAiModelMetrics e, Map<String, Object> m) {
        e.setPriceInputPerMusd(bd(m, "price_input_per_musd"));
        e.setPriceOutputPerMusd(bd(m, "price_output_per_musd"));
        e.setPriceCacheHitPerMusd(bd(m, "price_cache_hit_per_musd"));
        e.setPriceCacheWritePerMusd(bd(m, "price_cache_write_per_musd"));
        e.setContextWindowTokens(longOf(m, "context_window_tokens"));
        e.setReasoning(boolOf(m, "reasoning"));
        e.setWebSearch(boolOf(m, "web_search"));
        e.setImageInput(boolOf(m, "image_input"));
        e.setFunctionCalling(boolOf(m, "function_calling"));
        e.setJsonMode(boolOf(m, "json_mode"));
        e.setOpenaiCompatible(boolOf(m, "openai_compatible"));
        e.setIntelligenceIndex(bd(m, "intelligence_index"));
        e.setOutputSpeedTps(bd(m, "output_speed_tps"));
        e.setLatencyFirstTokenSeconds(bd(m, "latency_first_token_seconds"));
        e.setElo(bd(m, "elo"));
        e.setCostPerTask(bd(m, "cost_per_task"));
    }

    /**
     * 构建指标来源的更新语句（锁定字段跳过）。
     *
     * <p>只有指标 Map 中<b>存在</b>的键才写入：缺失表示该来源没有提供这一维度，
     * 不能把已有价格 / 上下文清空。</p>
     */
    private void buildIndicatorUpdate(Map<String, Object> metrics, Set<String> locked,
                                       String source, long now,
                                       LambdaUpdateWrapper<SysAiModelMetrics> uw) {
        if (metrics.containsKey("price_input_per_musd")
                && !locked.contains("price_input_per_musd")) {
            uw.set(SysAiModelMetrics::getPriceInputPerMusd, bd(metrics, "price_input_per_musd"));
        }
        if (metrics.containsKey("price_output_per_musd")
                && !locked.contains("price_output_per_musd")) {
            uw.set(SysAiModelMetrics::getPriceOutputPerMusd, bd(metrics, "price_output_per_musd"));
        }
        if (metrics.containsKey("price_cache_hit_per_musd")
                && !locked.contains("price_cache_hit_per_musd")) {
            uw.set(SysAiModelMetrics::getPriceCacheHitPerMusd, bd(metrics, "price_cache_hit_per_musd"));
        }
        if (metrics.containsKey("price_cache_write_per_musd")
                && !locked.contains("price_cache_write_per_musd")) {
            uw.set(SysAiModelMetrics::getPriceCacheWritePerMusd, bd(metrics, "price_cache_write_per_musd"));
        }
        if (metrics.containsKey("context_window_tokens")
                && !locked.contains("context_window_tokens")) {
            uw.set(SysAiModelMetrics::getContextWindowTokens, longOf(metrics, "context_window_tokens"));
        }
        if (metrics.containsKey("intelligence_index")
                && !locked.contains("intelligence_index")) {
            uw.set(SysAiModelMetrics::getIntelligenceIndex, bd(metrics, "intelligence_index"));
        }
        if (metrics.containsKey("output_speed_tps")
                && !locked.contains("output_speed_tps")) {
            uw.set(SysAiModelMetrics::getOutputSpeedTps, bd(metrics, "output_speed_tps"));
        }
        if (metrics.containsKey("latency_first_token_seconds")
                && !locked.contains("latency_first_token_seconds")) {
            uw.set(SysAiModelMetrics::getLatencyFirstTokenSeconds,
                    bd(metrics, "latency_first_token_seconds"));
        }
        // 能力标志不锁定（始终覆盖）
        if (metrics.containsKey("reasoning")) {
            uw.set(SysAiModelMetrics::getReasoning, boolOf(metrics, "reasoning"));
        }
        if (metrics.containsKey("web_search")) {
            uw.set(SysAiModelMetrics::getWebSearch, boolOf(metrics, "web_search"));
        }
        if (metrics.containsKey("image_input")) {
            uw.set(SysAiModelMetrics::getImageInput, boolOf(metrics, "image_input"));
        }
        if (metrics.containsKey("function_calling")) {
            uw.set(SysAiModelMetrics::getFunctionCalling, boolOf(metrics, "function_calling"));
        }
        if (metrics.containsKey("json_mode")) {
            uw.set(SysAiModelMetrics::getJsonMode, boolOf(metrics, "json_mode"));
        }
        if (metrics.containsKey("openai_compatible")) {
            uw.set(SysAiModelMetrics::getOpenaiCompatible, boolOf(metrics, "openai_compatible"));
        }
        // 数据源与时间戳（始终更新；source 为空时不覆盖历史来源）
        if (source != null && !source.isBlank()) {
            uw.set(SysAiModelMetrics::getSource, source);
        }
        uw.set(SysAiModelMetrics::getUpdateTime, now);
    }

    /**
     * 截断字符串到指定长度（数据库列宽保护）。
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

    /**
     * 指标同步结果。
     */
    public static final class SyncResult {

        /**
         * 更新的指标行数
         */
        private final int updated;
        /**
         * 新增的指标行数
         */
        private final int inserted;
        /**
         * 回写的服务商模型行数
         */
        private final int providerModels;

        private SyncResult(int updated, int inserted, int providerModels) {
            this.updated = updated;
            this.inserted = inserted;
            this.providerModels = providerModels;
        }

        /**
         * 更新行数。
         *
         * @return 行数
         */
        public int getUpdated() {
            return updated;
        }

        /**
         * 新增行数。
         *
         * @return 行数
         */
        public int getInserted() {
            return inserted;
        }

        /**
         * 回写的服务商模型行数。
         *
         * @return 行数
         */
        public int getProviderModels() {
            return providerModels;
        }
    }

    // ── 指标键/值转换辅助 ────────────────────────────────────────────────────

    /**
     * 从指标 Map 取 BigDecimal，缺省返回 {@code null}。
     * @param Map<String 参数
     * @param key 字符串
     * @param m 参数
     * @return 返回值
    */
    private static BigDecimal bd(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从指标 Map 取 Long，缺省返回 {@code null}。
     * @param Map<String 参数
     * @param key 字符串
     * @param m 参数
     * @return 返回值
    */
    private static Long longOf(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从指标 Map 取 Boolean，缺省返回 {@code null}。
     * @param Map<String 参数
     * @param key 字符串
     * @param m 参数
     * @return 返回值
    */
    private static Boolean boolOf(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.valueOf(String.valueOf(v));
    }

    /**
     * 构建同步更新语句（锁定字段跳过，其余含图标/能力标志全部更新）。
     */
    private void buildSyncUpdate(MetricsRecord r, Set<String> locked, long now,
                                 LambdaUpdateWrapper<SysAiModelMetrics> uw) {
        if (!locked.contains("price_input_per_musd")) {
            uw.set(SysAiModelMetrics::getPriceInputPerMusd, r.inputPerMillion());
        }
        if (!locked.contains("price_output_per_musd")) {
            uw.set(SysAiModelMetrics::getPriceOutputPerMusd, r.outputPerMillion());
        }
        if (!locked.contains("price_cache_hit_per_musd")) {
            uw.set(SysAiModelMetrics::getPriceCacheHitPerMusd, r.cacheHitPrice());
        }
        if (!locked.contains("price_cache_write_per_musd")) {
            uw.set(SysAiModelMetrics::getPriceCacheWritePerMusd, r.cacheWritePrice());
        }
        if (!locked.contains("intelligence_index")) {
            uw.set(SysAiModelMetrics::getIntelligenceIndex, r.intelligenceIndex());
        }
        if (!locked.contains("hle_score")) {
            uw.set(SysAiModelMetrics::getHleScore, r.hleScore());
        }
        if (!locked.contains("omniscience_index")) {
            uw.set(SysAiModelMetrics::getOmniscienceIndex, r.omniscienceIndex());
        }
        if (!locked.contains("gpqa_score")) {
            uw.set(SysAiModelMetrics::getGpqaScore, r.gpqaScore());
        }
        if (!locked.contains("aime25_score")) {
            uw.set(SysAiModelMetrics::getAime25Score, r.aime25Score());
        }
        if (!locked.contains("livecodebench_score")) {
            uw.set(SysAiModelMetrics::getLivecodebenchScore, r.livecodebenchScore());
        }
        if (!locked.contains("scicode_score")) {
            uw.set(SysAiModelMetrics::getScicodeScore, r.scicodeScore());
        }
        if (!locked.contains("ifbench_score")) {
            uw.set(SysAiModelMetrics::getIfbenchScore, r.ifbenchScore());
        }
        if (!locked.contains("critpt_score")) {
            uw.set(SysAiModelMetrics::getCritptScore, r.critptScore());
        }
        if (!locked.contains("mmmu_pro_score")) {
            uw.set(SysAiModelMetrics::getMmmuProScore, r.mmmuProScore());
        }
        if (!locked.contains("tau2_score")) {
            uw.set(SysAiModelMetrics::getTau2Score, r.tau2Score());
        }
        if (!locked.contains("tau_banking_score")) {
            uw.set(SysAiModelMetrics::getTauBankingScore, r.tauBankingScore());
        }
        if (!locked.contains("lcr_score")) {
            uw.set(SysAiModelMetrics::getLcrScore, r.lcrScore());
        }
        if (!locked.contains("terminalbench_hard_score")) {
            uw.set(SysAiModelMetrics::getTerminalbenchHardScore, r.terminalbenchHardScore());
        }
        if (!locked.contains("terminal_bench21_score")) {
            uw.set(SysAiModelMetrics::getTerminalBench21Score, r.terminalBench21Score());
        }
        if (!locked.contains("terminal_bench40_score")) {
            uw.set(SysAiModelMetrics::getTerminalBench40Score, r.terminalBench40Score());
        }
        if (!locked.contains("automation_bench_score")) {
            uw.set(SysAiModelMetrics::getAutomationBenchScore, r.automationBenchScore());
        }
        if (!locked.contains("gdpval_normalized_score")) {
            uw.set(SysAiModelMetrics::getGdpvalNormalizedScore, r.gdpvalNormalizedScore());
        }
        if (!locked.contains("briefcase_score")) {
            uw.set(SysAiModelMetrics::getBriefcaseScore, r.briefcaseScore());
        }
        if (!locked.contains("harvey_lab_score")) {
            uw.set(SysAiModelMetrics::getHarveyLabScore, r.harveyLabScore());
        }
        if (!locked.contains("apex_agents_score")) {
            uw.set(SysAiModelMetrics::getApexAgentsScore, r.apexAgentsScore());
        }
        if (!locked.contains("itbench_sre_score")) {
            uw.set(SysAiModelMetrics::getItbenchSreScore, r.itbenchSreScore());
        }
        if (!locked.contains("omniscience_accuracy")) {
            uw.set(SysAiModelMetrics::getOmniscienceAccuracy, r.omniscienceAccuracy());
        }
        if (!locked.contains("omniscience_non_hallucination")) {
            uw.set(SysAiModelMetrics::getOmniscienceNonHallucination, r.omniscienceNonHallucination());
        }
        if (!locked.contains("elo")) {
            uw.set(SysAiModelMetrics::getElo, r.elo());
        }
        if (!locked.contains("cost_per_task")) {
            uw.set(SysAiModelMetrics::getCostPerTask, r.costPerTask());
        }
        if (!locked.contains("json_mode")) {
            uw.set(SysAiModelMetrics::getJsonMode, r.jsonMode());
        }
        if (!locked.contains("openai_compatible")) {
            uw.set(SysAiModelMetrics::getOpenaiCompatible, r.openaiCompatible());
        }
        if (!locked.contains("intelligence_index_estimated")) {
            uw.set(SysAiModelMetrics::getIntelligenceIndexEstimated, r.intelligenceIndexEstimated());
        }
        if (!locked.contains("output_speed_tps")) {
            uw.set(SysAiModelMetrics::getOutputSpeedTps, r.outputSpeedTps());
        }
        if (!locked.contains("latency_first_token_seconds")) {
            uw.set(SysAiModelMetrics::getLatencyFirstTokenSeconds, r.latencyFirstTokenSeconds());
        }
        if (!locked.contains("context_window_tokens")) {
            uw.set(SysAiModelMetrics::getContextWindowTokens, r.contextWindowTokens());
        }
        uw.set(SysAiModelMetrics::getProvider, r.provider());
        uw.set(SysAiModelMetrics::getIconUrl, r.iconUrl());
        uw.set(SysAiModelMetrics::getReasoning, r.reasoning());
        uw.set(SysAiModelMetrics::getWebSearch, r.webSearch());
        uw.set(SysAiModelMetrics::getImageInput, r.imageInput());
        uw.set(SysAiModelMetrics::getFunctionCalling, r.functionCalling());
        uw.set(SysAiModelMetrics::getUpdateTime, now);
    }

    /**
     * 手动更新落库（统一构造 UpdateWrapper）。
     */
    private void applyManualUpdate(SysAiModelMetrics cur, Map<String, BigDecimal> values,
                                   List<String> normLocked, List<String> unlocked,
                                   MetricsRecord aaRec, String operator, long now) {
        LambdaUpdateWrapper<SysAiModelMetrics> uw = new LambdaUpdateWrapper<>();
        uw.eq(SysAiModelMetrics::getId, cur.getId());
        if (values != null) {
            for (Map.Entry<String, BigDecimal> en : values.entrySet()) {
                String field = en.getKey();
                SFunction<SysAiModelMetrics, ?> col = FIELD_COL.get(field);
                if (col == null) {
                    continue;
                }
                if ("context_window_tokens".equals(field)) {
                    BigDecimal v = en.getValue();
                    uw.set(col, v == null ? null : v.longValue());
                } else {
                    uw.set(col, en.getValue());
                }
            }
        }
        // 解锁字段：回填指标源当前值
        for (String field : unlocked) {
            SFunction<SysAiModelMetrics, ?> col = FIELD_COL.get(field);
            if (col != null) {
                uw.set(col, aaValue(aaRec, field));
            }
        }
        String lockedJson = normLocked.isEmpty() ? null : writeLocked(normLocked);
        uw.set(SysAiModelMetrics::getManualLocked, lockedJson);
        if (normLocked.isEmpty()) {
            uw.set(SysAiModelMetrics::getManualUpdatedAt, null);
            uw.set(SysAiModelMetrics::getManualUpdatedBy, null);
        } else {
            uw.set(SysAiModelMetrics::getManualUpdatedAt, now);
            uw.set(SysAiModelMetrics::getManualUpdatedBy, operator == null ? "admin" : operator);
        }
        uw.set(SysAiModelMetrics::getUpdateTime, now);
        metricsMapper.update(null, uw);
    }

    /**
     * 取指标源记录中指定字段的值。
     * @param aaRec 参数
     * @param field 字符串
     * @return 返回值
    */
    private Object aaValue(MetricsRecord aaRec, String field) {
        if (aaRec == null) {
            return null;
        }
        switch (field) {
            case "price_input_per_musd": return aaRec.inputPerMillion();
            case "price_output_per_musd": return aaRec.outputPerMillion();
            case "price_cache_hit_per_musd": return aaRec.cacheHitPrice();
            case "price_cache_write_per_musd": return aaRec.cacheWritePrice();
            case "intelligence_index": return aaRec.intelligenceIndex();
            case "hle_score": return aaRec.hleScore();
            case "omniscience_index": return aaRec.omniscienceIndex();
            case "gpqa_score": return aaRec.gpqaScore();
            case "aime25_score": return aaRec.aime25Score();
            case "livecodebench_score": return aaRec.livecodebenchScore();
            case "scicode_score": return aaRec.scicodeScore();
            case "ifbench_score": return aaRec.ifbenchScore();
            case "critpt_score": return aaRec.critptScore();
            case "mmmu_pro_score": return aaRec.mmmuProScore();
            case "tau2_score": return aaRec.tau2Score();
            case "tau_banking_score": return aaRec.tauBankingScore();
            case "lcr_score": return aaRec.lcrScore();
            case "terminalbench_hard_score": return aaRec.terminalbenchHardScore();
            case "terminal_bench21_score": return aaRec.terminalBench21Score();
            case "terminal_bench40_score": return aaRec.terminalBench40Score();
            case "automation_bench_score": return aaRec.automationBenchScore();
            case "gdpval_normalized_score": return aaRec.gdpvalNormalizedScore();
            case "briefcase_score": return aaRec.briefcaseScore();
            case "harvey_lab_score": return aaRec.harveyLabScore();
            case "apex_agents_score": return aaRec.apexAgentsScore();
            case "itbench_sre_score": return aaRec.itbenchSreScore();
            case "omniscience_accuracy": return aaRec.omniscienceAccuracy();
            case "omniscience_non_hallucination": return aaRec.omniscienceNonHallucination();
            case "elo": return aaRec.elo();
            case "cost_per_task": return aaRec.costPerTask();
            case "output_speed_tps": return aaRec.outputSpeedTps();
            case "latency_first_token_seconds": return aaRec.latencyFirstTokenSeconds();
            case "context_window_tokens": return aaRec.contextWindowTokens();
            default: return null;
        }
    }

    /**
     * 序列化锁定字段列表为 JSON 字符串。
     * @param locked 参数
     * @return 返回值
    */
    private static String writeLocked(List<String> locked) {
        try {
            return LOCK_OM.writeValueAsString(locked);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("[ModelMetrics] 锁定字段序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 记录转实体（新增）。
     * @param now 长整数
     * @param r 参数
     * @return 返回值
    */
    private SysAiModelMetrics toEntity(MetricsRecord r, long now) {
        SysAiModelMetrics e = new SysAiModelMetrics();
        e.setModelKey(r.normalizedKey());
        e.setModelName(r.normalizedKey());
        e.setProvider(r.provider());
        e.setPriceInputPerMusd(r.inputPerMillion());
        e.setPriceOutputPerMusd(r.outputPerMillion());
        e.setPriceCacheHitPerMusd(r.cacheHitPrice());
        e.setPriceCacheWritePerMusd(r.cacheWritePrice());
        e.setIntelligenceIndex(r.intelligenceIndex());
        e.setHleScore(r.hleScore());
        e.setOmniscienceIndex(r.omniscienceIndex());
        e.setGpqaScore(r.gpqaScore());
        e.setAime25Score(r.aime25Score());
        e.setLivecodebenchScore(r.livecodebenchScore());
        e.setScicodeScore(r.scicodeScore());
        e.setIfbenchScore(r.ifbenchScore());
        e.setCritptScore(r.critptScore());
        e.setMmmuProScore(r.mmmuProScore());
        e.setTau2Score(r.tau2Score());
        e.setTauBankingScore(r.tauBankingScore());
        e.setLcrScore(r.lcrScore());
        e.setTerminalbenchHardScore(r.terminalbenchHardScore());
        e.setTerminalBench21Score(r.terminalBench21Score());
        e.setTerminalBench40Score(r.terminalBench40Score());
        e.setAutomationBenchScore(r.automationBenchScore());
        e.setGdpvalNormalizedScore(r.gdpvalNormalizedScore());
        e.setBriefcaseScore(r.briefcaseScore());
        e.setHarveyLabScore(r.harveyLabScore());
        e.setApexAgentsScore(r.apexAgentsScore());
        e.setItbenchSreScore(r.itbenchSreScore());
        e.setOmniscienceAccuracy(r.omniscienceAccuracy());
        e.setOmniscienceNonHallucination(r.omniscienceNonHallucination());
        e.setElo(r.elo());
        e.setCostPerTask(r.costPerTask());
        e.setJsonMode(r.jsonMode());
        e.setOpenaiCompatible(r.openaiCompatible());
        e.setIntelligenceIndexEstimated(r.intelligenceIndexEstimated());
        e.setOutputSpeedTps(r.outputSpeedTps());
        e.setLatencyFirstTokenSeconds(r.latencyFirstTokenSeconds());
        e.setContextWindowTokens(r.contextWindowTokens());
        e.setIconUrl(r.iconUrl());
        e.setReasoning(r.reasoning());
        e.setWebSearch(r.webSearch());
        e.setImageInput(r.imageInput());
        e.setFunctionCalling(r.functionCalling());
        e.setSource(SOURCE);
        e.setCreateTime(now);
        e.setUpdateTime(now);
        return e;
    }

    /**
     * 可编辑字段 → 列映射。
     */
    private static Map<String, SFunction<SysAiModelMetrics, ?>> fieldCol() {
        Map<String, SFunction<SysAiModelMetrics, ?>> m = new HashMap<>();
        m.put("price_input_per_musd", SysAiModelMetrics::getPriceInputPerMusd);
        m.put("price_output_per_musd", SysAiModelMetrics::getPriceOutputPerMusd);
        m.put("price_cache_hit_per_musd", SysAiModelMetrics::getPriceCacheHitPerMusd);
        m.put("price_cache_write_per_musd", SysAiModelMetrics::getPriceCacheWritePerMusd);
        m.put("intelligence_index", SysAiModelMetrics::getIntelligenceIndex);
        m.put("hle_score", SysAiModelMetrics::getHleScore);
        m.put("omniscience_index", SysAiModelMetrics::getOmniscienceIndex);
        m.put("gpqa_score", SysAiModelMetrics::getGpqaScore);
        m.put("aime25_score", SysAiModelMetrics::getAime25Score);
        m.put("livecodebench_score", SysAiModelMetrics::getLivecodebenchScore);
        m.put("scicode_score", SysAiModelMetrics::getScicodeScore);
        m.put("ifbench_score", SysAiModelMetrics::getIfbenchScore);
        m.put("critpt_score", SysAiModelMetrics::getCritptScore);
        m.put("mmmu_pro_score", SysAiModelMetrics::getMmmuProScore);
        m.put("tau2_score", SysAiModelMetrics::getTau2Score);
        m.put("tau_banking_score", SysAiModelMetrics::getTauBankingScore);
        m.put("lcr_score", SysAiModelMetrics::getLcrScore);
        m.put("terminalbench_hard_score", SysAiModelMetrics::getTerminalbenchHardScore);
        m.put("terminal_bench21_score", SysAiModelMetrics::getTerminalBench21Score);
        m.put("terminal_bench40_score", SysAiModelMetrics::getTerminalBench40Score);
        m.put("automation_bench_score", SysAiModelMetrics::getAutomationBenchScore);
        m.put("gdpval_normalized_score", SysAiModelMetrics::getGdpvalNormalizedScore);
        m.put("briefcase_score", SysAiModelMetrics::getBriefcaseScore);
        m.put("harvey_lab_score", SysAiModelMetrics::getHarveyLabScore);
        m.put("apex_agents_score", SysAiModelMetrics::getApexAgentsScore);
        m.put("itbench_sre_score", SysAiModelMetrics::getItbenchSreScore);
        m.put("omniscience_accuracy", SysAiModelMetrics::getOmniscienceAccuracy);
        m.put("omniscience_non_hallucination", SysAiModelMetrics::getOmniscienceNonHallucination);
        m.put("elo", SysAiModelMetrics::getElo);
        m.put("cost_per_task", SysAiModelMetrics::getCostPerTask);
        m.put("output_speed_tps", SysAiModelMetrics::getOutputSpeedTps);
        m.put("latency_first_token_seconds", SysAiModelMetrics::getLatencyFirstTokenSeconds);
        m.put("context_window_tokens", SysAiModelMetrics::getContextWindowTokens);
        return Map.copyOf(m);
    }
}