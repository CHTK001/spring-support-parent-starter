package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.ai.support.manage.mapper.SysAiModelMetricsMapper;
import com.chua.starter.ai.support.manage.store.SysAiModelMetrics;
import com.chua.starter.ai.support.service.AiModelMetricsEnrichmentService;
import com.chua.starter.ai.support.service.AiModelMetricsEnrichmentService.MetricsRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
     * 指标富集服务（全量数据源）
     */
    private final AiModelMetricsEnrichmentService enrichment;

    /**
     * 全量同步：指标源全量记录 → 插入/更新，行内锁定字段跳过。
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
     * 计费取价回退：按模型名精确匹配，未命中做包含关系模糊匹配。
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
        for (SysAiModelMetrics m : all) {
            String k = m.getModelKey();
            if (k.length() >= 6 && (k.contains(key) || key.contains(k))) {
                return m;
            }
        }
        return null;
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
            case "output_speed_tps": return aaRec.outputSpeedTps();
            case "latency_first_token_seconds": return aaRec.latencyFirstTokenSeconds();
            case "context_window_tokens": return aaRec.contextWindowTokens();
            default: return null;
        }
    }

    /**
     * 序列化锁定字段列表为 JSON 字符串。
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
        m.put("output_speed_tps", SysAiModelMetrics::getOutputSpeedTps);
        m.put("latency_first_token_seconds", SysAiModelMetrics::getLatencyFirstTokenSeconds);
        m.put("context_window_tokens", SysAiModelMetrics::getContextWindowTokens);
        return Map.copyOf(m);
    }
}
