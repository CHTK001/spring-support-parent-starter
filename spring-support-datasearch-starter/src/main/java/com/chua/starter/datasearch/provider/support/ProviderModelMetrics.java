package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderModelInfo;
import com.chua.starter.datasearch.provider.spi.ProviderModelMetricsProvider;
import com.chua.starter.datasearch.support.SpiProviders;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 模型指标出口：按模型 id 问各个 {@link ProviderModelMetricsProvider}，把命中的字段合并成一份。
 *
 * <p>与 {@link ProviderModelCatalogs} 的「第一个命中算数」不同，这里取<b>合并</b>：
 * 各数据源各有强项（models.dev 有模态与上下限、厂商控制台有真实牌价），
 * 先命中的占位，后续源只补空字段，避免后一个源把前一个的准确值盖掉。</p>
 *
 * <p>查询键只有模型 id，因此免登录渠道、未绑定账号的登录渠道都能拿到模型能力与价格，
 * 不依赖 {@code baseUrl}。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Slf4j
public final class ProviderModelMetrics {

    private static volatile List<ProviderModelMetricsProvider> cached;

    /**
     * 工具类，禁止实例化。
     */
    private ProviderModelMetrics() {
    }

    /**
     * 全部实现（按登记顺序）。
     *
     * @return 实现列表；装载失败时为空列表
     */
    public static List<ProviderModelMetricsProvider> providers() {
        List<ProviderModelMetricsProvider> current = cached;
        if (CollectionUtils.isEmpty(current)) {
            current = SpiProviders.list(ProviderModelMetricsProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 按模型 id 查指标（不限定渠道）。
     *
     * @param model 模型 id
     * @return 合并后的模型信息；未收录返回 null
     */
    public static ProviderModelInfo of(String model) {
        return of(null, model);
    }

    /**
     * 按模型 id 查指标，命中多家厂商时优先入参平台对应的那家。
     *
     * @param platform 平台 / 渠道标识，可为 null
     * @param model    模型 id
     * @return 合并后的模型信息；未收录返回 null
     */
    public static ProviderModelInfo of(String platform, String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        ProviderModelInfo merged = null;
        for (ProviderModelMetricsProvider provider : providers()) {
            if (!supported(provider, model)) {
                continue;
            }
            ProviderModelInfo hit = query(provider, platform, model);
            if (hit != null) {
                // 合并口径统一在 ProviderModelInfos，目录出口用的是同一份规则
                merged = ProviderModelInfos.merge(merged, hit);
            }
        }
        return merged;
    }

    /**
     * 目录条目 → 指标键 Map（与指标库 / 模型行同一套键名）。
     *
     * <p>键名口径：价格是 <b>USD / 百万 token</b>，{@code context_window_tokens} /
     * {@code max_output_tokens} 是 token 数，模态是字符串列表；能力布尔
     * （{@code image_input} 等）由输入模态派生。目录没给的项<b>不出现在结果里</b>，
     * 调用方按「键不存在 = 未知」处理，不能当 0 / false 用。</p>
     *
     * <p>放在这里而不是各调用方各写一份：account 落库、ai 指标库、模型卡读路径
     * 必须对同一份目录数据给出同一个键名，否则又会「一边有、一边空」。</p>
     *
     * @param info 目录条目，可为 null
     * @return 指标 Map（空 Map 表示未收录）
     */
    public static Map<String, Object> metricsOf(ProviderModelInfo info) {
        Map<String, Object> metrics = new LinkedHashMap<>(24);
        if (info == null) {
            return metrics;
        }
        if (info.getPriceInputPerMusd() != null) {
            metrics.put("price_input_per_musd", info.getPriceInputPerMusd());
        }
        if (info.getPriceOutputPerMusd() != null) {
            metrics.put("price_output_per_musd", info.getPriceOutputPerMusd());
        }
        if (info.getPriceCacheHitPerMusd() != null) {
            metrics.put("price_cache_hit_per_musd", info.getPriceCacheHitPerMusd());
        }
        if (info.getPriceCacheWritePerMusd() != null) {
            metrics.put("price_cache_write_per_musd", info.getPriceCacheWritePerMusd());
        }
        if (info.getContextWindow() != null) {
            metrics.put("context_window_tokens", info.getContextWindow());
        }
        if (info.getMaxOutputTokens() != null) {
            metrics.put("max_output_tokens", info.getMaxOutputTokens());
        }
        List<String> inputs = info.getInputModalities();
        if (CollectionUtils.isNotEmpty(inputs)) {
            // 原始列表要保留：布尔位表达不了 vector / file，前端「输入」列读的是列表
            metrics.put("input_modalities", new ArrayList<>(inputs));
            metrics.put("image_input", inputs.contains("image"));
            metrics.put("audio_input", inputs.contains("audio"));
            metrics.put("video_input", inputs.contains("video"));
            metrics.put("pdf_input", inputs.contains("pdf"));
        }
        List<String> outputs = info.getOutputModalities();
        if (CollectionUtils.isNotEmpty(outputs)) {
            metrics.put("output_modalities", new ArrayList<>(outputs));
        }
        if (info.getAttachment() != null) {
            metrics.put("attachment", info.getAttachment());
        }
        if (info.getReasoning() != null) {
            metrics.put("reasoning", info.getReasoning());
        }
        if (info.getToolCall() != null) {
            metrics.put("function_calling", info.getToolCall());
        }
        if (info.getStructuredOutput() != null) {
            metrics.put("structured_output", info.getStructuredOutput());
        }
        if (info.getTemperature() != null) {
            metrics.put("temperature", info.getTemperature());
        }
        if (info.getOpenWeights() != null) {
            metrics.put("open_weights", info.getOpenWeights());
        }
        if (info.getKnowledge() != null && !info.getKnowledge().isBlank()) {
            metrics.put("knowledge_cutoff", info.getKnowledge());
        }
        if (info.getReleaseDate() != null && !info.getReleaseDate().isBlank()) {
            metrics.put("release_date", info.getReleaseDate());
        }
        if (info.getLastUpdated() != null && !info.getLastUpdated().isBlank()) {
            metrics.put("last_updated", info.getLastUpdated());
        }
        return metrics;
    }

    /**
     * 实现标识清单。
     *
     * @return 标识
     */
    public static List<String> kinds() {
        List<String> kinds = new ArrayList<>(4);
        for (ProviderModelMetricsProvider provider : providers()) {
            kinds.add(provider.name());
        }
        return kinds;
    }

    /**
     * 全量枚举各实现收录的模型指标（按模型 id 去重，先命中占位）。
     *
     * <p>给「显式同步」这类管理动作使用：一次拿到目录里的所有模型，不逐个 id
     * 试探。同一模型被多个实现收录时取先命中的一条；实现加载失败只跳过，
     * 不让整个枚举失败。</p>
     *
     * @return 模型信息列表；无实现提供全量数据时为空列表
     */
    public static List<ProviderModelInfo> all() {
        Map<String, ProviderModelInfo> byId = new LinkedHashMap<>(1024);
        for (ProviderModelMetricsProvider provider : providers()) {
            List<ProviderModelInfo> items;
            try {
                items = provider.listAll();
            } catch (Exception e) {
                log.debug("[provider-metrics] 实现[{}]全量枚举失败: {}", provider.name(), e.getMessage());
                continue;
            }
            if (CollectionUtils.isEmpty(items)) {
                continue;
            }
            for (ProviderModelInfo item : items) {
                if (item == null || item.getId() == null || item.getId().isBlank()) {
                    continue;
                }
                byId.putIfAbsent(item.getId(), item);
            }
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * 按模型 id 查指标并合并进行数据（只补空，不覆盖已有键）。
     *
     * <p>给模型列表 / 模型行补指标用：目录（models.dev 等）有值的键才写入，
     * 调用方已配置或上游已给的键保持原值。键名与 {@link #metricsOf(ProviderModelInfo)}
     * 同一套（价格 USD/百万 token、上下文 token 数、模态列表等）。</p>
     *
     * @param model 模型 id
     * @param row   模型行数据（原地追加）
     */
    public static void mergeInto(String model, Map<String, Object> row) {
        if (model == null || model.isBlank() || row == null) {
            return;
        }
        Map<String, Object> metrics = metricsOf(of(model));
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            row.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 收录模型数（各实现自报）。
     *
     * @return 模型数
     */
    public static int modelCount() {
        int total = 0;
        for (ProviderModelMetricsProvider provider : providers()) {
            total += provider.modelCount();
        }
        return total;
    }

    /**
     * 单实现的支持判定，异常不外抛（装载不完整时不能让调用方一起崩）。
     *
     * @param provider 实现
     * @param model    模型 id
     * @return 可能收录返回 true
     */
    private static boolean supported(ProviderModelMetricsProvider provider, String model) {
        try {
            return provider.supportsModel(model);
        } catch (Exception e) {
            log.debug("[provider-metrics] 实现[{}]支持判定失败: {}", provider.name(), e.getMessage());
            return false;
        }
    }

    /**
     * 单实现查询，异常不外抛。
     *
     * @param provider 实现
     * @param platform 平台标识
     * @param model    模型 id
     * @return 模型信息；未收录或失败返回 null
     */
    private static ProviderModelInfo query(ProviderModelMetricsProvider provider, String platform, String model) {
        try {
            return provider.query(platform, model);
        } catch (Exception e) {
            log.debug("[provider-metrics] 实现[{}]查询失败 model={} cause={}",
                    provider.name(), model, e.getMessage());
            return null;
        }
    }

}
