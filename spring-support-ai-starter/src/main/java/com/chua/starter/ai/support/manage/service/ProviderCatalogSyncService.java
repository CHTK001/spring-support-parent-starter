package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.datasearch.provider.model.ProviderModelCatalog;
import com.chua.starter.datasearch.provider.model.ProviderModelInfo;
import com.chua.starter.datasearch.provider.model.ProviderPricingRefresh;
import com.chua.starter.datasearch.provider.model.ProviderPricingRule;
import com.chua.starter.datasearch.provider.model.PriceWindow;
import com.chua.starter.datasearch.provider.support.ProviderModelCatalogs;
import com.chua.starter.datasearch.provider.support.ProviderPricings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 模型目录同步：公开目录（models.dev）→ 服务商模型行 + 指标库。
 *
 * <p>与「同步」里另外两节的边界：</p>
 * <ul>
 *   <li>上游 {@code /models}：描述与价格优先用它，因为它反映的是当前中转站 / 网关的
 *       真实目录；</li>
 *   <li>本类：在上游目录拿不到、或字段缺失时补位 —— 说明、上下文窗口、输出上限、
 *       多模态、工具调用 / 思考 / 附件 / 温度等能力位，以及计费标准；</li>
 *   <li>价格写 {@code sys_ai_model_metrics}（已有价格不覆盖，来源标记为
 *       {@code models.dev}），能力位走模型指标回填链路，只补空值。</li>
 * </ul>
 *
 * <p>模型识别按 id 精确匹配，其次按别名（模型行 alias 或目录 id）忽略大小写匹配。
 * 目录没有的字段一律不写；流式 / 异步目录不提供，返回 {@code null} 表示「未收录」。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCatalogSyncService {

    /**
     * 目录数据源标识（写到指标行 source）
     */
    private static final String SOURCE = "models.dev";

    private final SysAiProviderModelMapper providerModelMapper;

    private final SysAiModelMetricsService metricsService;

    /**
     * 按服务商同步模型目录。
     *
     * <p>说明只补空值：上游 {@code /models} 已经写过的文本（或管理员手写）不覆盖，
     * 避免公开目录的英文说明盖掉更贴近渠道的文案。</p>
     *
     * @param provider 服务商行
     * @return 各分节统计
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sync(SysAiProvider provider) {
        Map<String, Object> out = new LinkedHashMap<>(12);
        out.put("supported", false);
        if (provider == null) {
            out.put("message", "服务商不存在");
            return out;
        }
        ProviderModelCatalog catalog = ProviderModelCatalogs.of(provider.getPlatform(), provider.getBaseUrl());
        if (catalog == null || catalog.getModels() == null || catalog.getModels().isEmpty()) {
            out.put("message", "公开目录未收录该服务商，可用上游 /models 或手工维护模型信息");
            return out;
        }
        out.put("supported", true);
        out.put("source", catalog.getSource());
        out.put("provider", catalog.getProviderId());
        out.put("doc", catalog.getDoc());
        out.put("models", catalog.getModels().size());

        // 点击同步时实时抓官方价格页；抓取失败就不写规则，保留库里的上一次价格。
        ProviderPricingRefresh refresh = ProviderPricings.refresh(
                provider.getPlatform(), provider.getBaseUrl());
        if (refresh.isSupported()) {
            catalog.setPricingRules(refresh.getRules());
            catalog.setPricingLive(refresh.isLive());
            catalog.setPricingFetchedAt(refresh.getFetchedAt());
            catalog.setPricingError(refresh.getError());
        }

        List<Map<String, Object>> rows = new ArrayList<>(catalog.getModels().size());
        for (ProviderModelInfo model : catalog.getModels()) {
            Map<String, Object> metrics = metricsOf(model, catalog.getPricingRules());
            if (metrics.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(4);
            row.put("id", model.getId());
            row.put("metrics", metrics);
            rows.add(row);
        }
        SysAiModelMetricsService.UpstreamPricingResult pricing =
                metricsService.syncUpstreamPricing(provider.getProviderId(), rows, catalog.getDoc(), SOURCE);
        Map<String, Object> pricingOut = new LinkedHashMap<>(6);
        pricingOut.put("updated", pricing.getUpdated());
        pricingOut.put("inserted", pricing.getInserted());
        pricingOut.put("provider_models", pricing.getProviderModels());
        pricingOut.put("source", SOURCE);
        pricingOut.put("rules_count", catalog.getPricingRules() == null ? 0 : catalog.getPricingRules().size());
        out.put("pricing", pricingOut);

        out.put("descriptions", syncDescriptions(provider.getProviderId(), catalog));
        out.put("capabilities", capabilityCounts(catalog));
        out.put("pricing_rules", catalog.getPricingRules() == null ? 0 : catalog.getPricingRules().size());
        out.put("pricing_live", catalog.getPricingLive());
        out.put("pricing_fetched_at", catalog.getPricingFetchedAt());
        out.put("pricing_error", catalog.getPricingError());
        // 目录不提供流式 / 异步标记：显式回 null，前端显示「未收录」
        out.put("streaming", null);
        out.put("async", null);
        log.info("[ProviderSync] provider={} 目录={} 模型={} 说明更新={} 计费新增={}",
                provider.getProviderId(), catalog.getProviderId(), catalog.getModels().size(),
                ((Map<?, ?>) out.get("descriptions")).get("updated"), pricing.getInserted());
        return out;
    }

    /**
     * 说明补空（或按需覆盖）。
     *
     * @param providerId 服务商标识
     * @param catalog    目录
     * @return 统计
     */
    private Map<String, Object> syncDescriptions(String providerId, ProviderModelCatalog catalog) {
        Map<String, Object> out = new LinkedHashMap<>(6);
        List<SysAiProviderModel> rows = providerModelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId));
        Map<String, SysAiProviderModel> byModel = new HashMap<>(rows.size());
        for (SysAiProviderModel row : rows) {
            if (row.getModel() != null) {
                byModel.putIfAbsent(row.getModel().toLowerCase(Locale.ROOT), row);
            }
            if (row.getAlias() != null && !row.getAlias().isBlank()) {
                byModel.putIfAbsent(row.getAlias().toLowerCase(Locale.ROOT), row);
            }
        }
        int updated = 0;
        int unchanged = 0;
        int missing = 0;
        long now = System.currentTimeMillis();
        for (ProviderModelInfo model : catalog.getModels()) {
            SysAiProviderModel row = byModel.get(model.getId().toLowerCase(Locale.ROOT));
            if (row == null && model.getName() != null) {
                row = byModel.get(model.getName().toLowerCase(Locale.ROOT));
            }
            if (row == null) {
                missing++;
                continue;
            }
            String description = model.getDescription();
            if (description == null || description.isBlank()) {
                unchanged++;
                continue;
            }
            boolean hasCurrent = row.getDescription() != null && !row.getDescription().isBlank();
            if (hasCurrent) {
                unchanged++;
                continue;
            }
            if (description.equals(row.getDescription())) {
                unchanged++;
                continue;
            }
            SysAiProviderModel update = new SysAiProviderModel();
            update.setProviderId(providerId);
            update.setModel(row.getModel());
            update.setDescription(description.trim());
            update.setUpdatedAt(now);
            updated += providerModelMapper.updateByKey(update);
        }
        out.put("updated", updated);
        out.put("unchanged", unchanged);
        out.put("missing", missing);
        return out;
    }

    /**
     * 能力位覆盖统计（目录里有值的模型数）。
     *
     * @param catalog 目录
     * @return 统计
     */
    private Map<String, Object> capabilityCounts(ProviderModelCatalog catalog) {
        int reasoning = 0;
        int toolCall = 0;
        int attachment = 0;
        int context = 0;
        int priced = 0;
        for (ProviderModelInfo model : catalog.getModels()) {
            if (Boolean.TRUE.equals(model.getReasoning())) {
                reasoning++;
            }
            if (Boolean.TRUE.equals(model.getToolCall())) {
                toolCall++;
            }
            if (Boolean.TRUE.equals(model.getAttachment())) {
                attachment++;
            }
            if (model.getContextWindow() != null) {
                context++;
            }
            if (model.getPriceInputPerMusd() != null || model.getPriceOutputPerMusd() != null) {
                priced++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>(8);
        out.put("models", catalog.getModels().size());
        out.put("reasoning", reasoning);
        out.put("tool_call", toolCall);
        out.put("attachment", attachment);
        out.put("context_window", context);
        out.put("priced", priced);
        return out;
    }

    /**
     * 单条目录模型 → 指标键（与模型指标配置表、模型行回填链路同一套键名）。
     *
     * @param model 目录模型
     * @param rules 厂商定价规则（峰谷 / 按 N 次）
     * @return 指标
     */
    private Map<String, Object> metricsOf(ProviderModelInfo model, List<ProviderPricingRule> rules) {
        Map<String, Object> metrics = new LinkedHashMap<>(24);
        put(metrics, "price_input_per_musd", model.getPriceInputPerMusd());
        put(metrics, "price_output_per_musd", model.getPriceOutputPerMusd());
        put(metrics, "price_cache_hit_per_musd", model.getPriceCacheHitPerMusd());
        put(metrics, "price_cache_write_per_musd", model.getPriceCacheWritePerMusd());
        put(metrics, "context_window_tokens", model.getContextWindow());
        put(metrics, "max_output_tokens", model.getMaxOutputTokens());
        put(metrics, "reasoning", model.getReasoning());
        put(metrics, "function_calling", model.getToolCall());
        put(metrics, "attachment", model.getAttachment());
        put(metrics, "temperature", model.getTemperature());
        put(metrics, "open_weights", model.getOpenWeights());
        put(metrics, "knowledge_cutoff", model.getKnowledge());
        put(metrics, "release_date", model.getReleaseDate());
        put(metrics, "last_updated", model.getLastUpdated());
        put(metrics, "description", model.getDescription());
        List<String> inputs = model.getInputModalities();
        if (inputs != null) {
            put(metrics, "image_input", inputs.contains("image"));
            put(metrics, "audio_input", inputs.contains("audio"));
            put(metrics, "video_input", inputs.contains("video"));
            put(metrics, "pdf_input", inputs.contains("pdf"));
        }
        if (model.getOutputModalities() != null) {
            metrics.put("output_modalities", model.getOutputModalities());
        }
        List<ProviderPricingRule> hits = ProviderPricings.forModel(rules, model.getId());
        if (!hits.isEmpty()) {
            List<Map<String, Object>> ruleMaps = new ArrayList<>(hits.size());
            for (ProviderPricingRule rule : hits) {
                ruleMaps.add(pricingRuleMap(rule));
            }
            metrics.put("pricing_rules", ruleMaps);
        }
        return metrics;
    }

    /**
     * 定价规则 → 下划线键 Map（落 metrics JSON 与 TEXT 列，前端直接读）。
     *
     * @param rule 规则
     * @return Map
     */
    private static Map<String, Object> pricingRuleMap(ProviderPricingRule rule) {
        Map<String, Object> map = new LinkedHashMap<>(16);
        put(map, "provider_id", rule.getProviderId());
        put(map, "model_pattern", rule.getModelPattern());
        put(map, "item", rule.getItem());
        put(map, "unit", rule.getUnit());
        put(map, "quantity", rule.getQuantity());
        put(map, "peak_price", rule.getPeakPrice());
        put(map, "off_peak_price", rule.getOffPeakPrice());
        put(map, "standard_price", rule.getStandardPrice());
        put(map, "currency", rule.getCurrency());
        put(map, "off_peak_policy", rule.getOffPeakPolicy());
        put(map, "note", rule.getNote());
        put(map, "source_url", rule.getSourceUrl());
        put(map, "verified", rule.getVerified());
        put(map, "fetched_at", rule.getFetchedAt());
        if (rule.getPeakWindows() != null && !rule.getPeakWindows().isEmpty()) {
            List<Map<String, Object>> windows = new ArrayList<>(rule.getPeakWindows().size());
            for (PriceWindow window : rule.getPeakWindows()) {
                Map<String, Object> row = new LinkedHashMap<>(4);
                put(row, "timezone", window.getTimezone());
                put(row, "weekdays", window.getWeekdays());
                put(row, "start_minute", window.getStartMinute());
                put(row, "end_minute", window.getEndMinute());
                windows.add(row);
            }
            map.put("peak_windows", windows);
        }
        return map;
    }

    /**
     * 非空写入。
     *
     * @param map   目标
     * @param key   键
     * @param value 值
     */
    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
