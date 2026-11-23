// [P3C A20 豁免] A20/A30/A31: 含界面文案/排序键等字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.service;

import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.manage.mapper.SysAiModelMetricsMapper;
import com.chua.starter.ai.support.manage.store.SysAiModelMetrics;
import com.chua.starter.ai.support.mapper.SysAiPricingMapper;
import com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO;
import com.chua.starter.ai.support.service.SysAiUsageService;
import com.chua.starter.datasearch.provider.model.ProviderDescriptor;
import com.chua.starter.datasearch.provider.support.ProviderRegistries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 模型价格总览服务：按「模型」聚合各提供商报价，供价格页跨模型 / 跨提供商比价。
 *
 * <p>数据源两级：
 * ① 指标库 {@code sys_ai_model_metrics}（USD/百万 token，主数据源）；
 * ② 定价表 {@code sys_ai_pricing} 的 USD 行（币种/千 token，换算 ×1000 并入，
 * 同厂商覆盖指标价，与 {@code SysAiPricingService#getLatestPricing} 的取价优先级一致）。
 * CNY 行与 USD 不同轴，不参与本页比较（人工 CNY 单价见「人工单价覆盖」页），
 * 仅返回跳过条数供前端提示。</p>
 *
 * <p>只返回至少有一档价格（输入或输出）的模型；同模型多提供商时，前端按
 * 「该提供商价格 ÷ 该模型最高价」画价格进度条，最低价行标「最便宜」。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelPriceOverviewService {

    /**
     * 价格带：经济档（价格 < $1/M）
     */
    public static final String BAND_ECONOMY = "economy";

    /**
     * 价格带：实惠档（$1 ~ $5/M）
     */
    public static final String BAND_VALUE = "value";

    /**
     * 价格带：标准档（$5 ~ $15/M）
     */
    public static final String BAND_STANDARD = "standard";

    /**
     * 价格带：旗舰档（≥ $15/M）
     */
    public static final String BAND_FLAGSHIP = "flagship";

    /**
     * 价格带分档上限
     */
    private static final BigDecimal ECONOMY_MAX = new BigDecimal("1");
    private static final BigDecimal VALUE_MAX = new BigDecimal("5");
    private static final BigDecimal STANDARD_MAX = new BigDecimal("15");

    /**
     * 排序键
     */
    private static final String SORT_PRICE = "price";
    private static final String SORT_VALUE = "value";
    private static final String SORT_INDEX = "index";
    private static final String SORT_POPULARITY = "popularity";
    private static final String ORDER_ASC = "asc";

    /**
     * 热度统计窗口（近 N 天用量，含今天）
     */
    private static final int POPULARITY_DAYS = 30;

    /**
     * 热门模型徽标上限（热度排序下标记 TOP n）
     */
    private static final int POPULARITY_RANK_LIMIT = 8;

    /**
     * 指标价来源标记
     */
    private static final String SOURCE_METRICS = "metrics";

    /**
     * 定价表来源标记前缀（pricing:manual / pricing:sync）
     */
    private static final String SOURCE_PRICING_PREFIX = "pricing:";

    /**
     * USD 定价行币种标记（sys_ai_pricing.currency，大小写不敏感）
     */
    private static final String CURRENCY_USD = "USD";

    /**
     * 千 token → 百万 token 换算倍数
     */
    private static final BigDecimal PER_1K_TO_PER_1M = BigDecimal.valueOf(1000);

    /**
     * 每页条数上限
     */
    private static final long MAX_PAGE_SIZE = 100;

    /**
     * 提供商分布最多返回条数（过滤下拉用）
     */
    private static final int MAX_PROVIDER_FACETS = 30;

    /**
     * 指标库 Mapper
     */
    private final SysAiModelMetricsMapper metricsMapper;

    /**
     * 定价表 Mapper
     */
    private final SysAiPricingMapper pricingMapper;

    /**
     * 用量服务（近 N 天模型热度统计的数据源；只读调用，不穿透持久层）
     */
    private final SysAiUsageService usageService;

    /**
     * 模型价格总览：按模型聚合各提供商报价并分页。
     *
     * @param page    当前页（从 1 起）
     * @param size    每页条数（上限 {@value #MAX_PAGE_SIZE}）
     * @param keyword 模型名/厂商关键字（可空）
     * @param provider 提供商过滤（可空，精确匹配）
     * @param band    价格带过滤：economy/value/standard/flagship（可空）
     * @param sort    排序键：price（价格，默认）/ value（性价比）/ index（智能指数）
     * @param order   排序方向：asc / desc（price 默认 asc，value/index 默认 desc）
     * @return {total, page, size, totalModels, withPrice, providerCount, bands, providers, cnyPricingSkipped, models}
     */
    public Map<String, Object> overview(long page, long size, String keyword, String provider,
                                        String band, String sort, String order) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        // 1) 指标库按模型名聚合报价（只收有价行：输入价或输出价至少一项非空）
        long totalModels = 0;
        Map<String, ModelGroup> groups = new LinkedHashMap<>();
        for (SysAiModelMetrics row : metricsMapper.selectList(null)) {
            if (row == null) {
                continue;
            }
            totalModels++;
            String modelName = firstNonBlank(row.getModelName(), row.getModelKey());
            if (modelName == null) {
                continue;
            }
            if (row.getPriceInputPerMusd() == null && row.getPriceOutputPerMusd() == null) {
                continue;
            }
            ModelGroup group = groups.computeIfAbsent(modelName, ModelGroup::new);
            group.merge(blankToNull(row.getProvider()), row.getPriceInputPerMusd(), row.getPriceOutputPerMusd(),
                    row.getPriceCacheHitPerMusd(), row.getPriceCacheWritePerMusd(), SOURCE_METRICS);
            if (row.getIntelligenceIndex() != null
                    && (group.intelligenceIndex == null
                        || row.getIntelligenceIndex().compareTo(group.intelligenceIndex) > 0)) {
                group.intelligenceIndex = row.getIntelligenceIndex();
            }
        }

        // 2) 定价表 USD 行换算并入（千 token → 百万 token），同厂商覆盖指标价；CNY 行跳过
        long cnySkipped = 0;
        for (SysAiPricing pricing : pricingMapper.selectList(null)) {
            if (pricing == null || blankToNull(pricing.getModel()) == null) {
                continue;
            }
            if (pricing.getCurrency() != null
                    && !CURRENCY_USD.equalsIgnoreCase(pricing.getCurrency().trim())) {
                cnySkipped++;
                continue;
            }
            ModelGroup group = groups.computeIfAbsent(pricing.getModel().trim(), ModelGroup::new);
            String source = SOURCE_PRICING_PREFIX + (pricing.getSource() == null ? "sync" : pricing.getSource());
            group.merge(blankToNull(pricing.getProvider()),
                    toPerMillion(pricing.getInputPricePer1k()), toPerMillion(pricing.getOutputPricePer1k()),
                    null, null, source);
        }

        // 3) 无任何可用价格的组（如定价行两价皆空）不进比价页
        List<ModelGroup> all = new ArrayList<>();
        for (ModelGroup group : groups.values()) {
            if (group.effectivePrice() != null) {
                all.add(group);
            }
        }
        long withPrice = all.size();

        // 4) 关键字过滤（模型名 / 提供商），并在过滤结果上汇总价格带与提供商分布
        String needle = blankToNull(keyword);
        List<ModelGroup> keywordFiltered = new ArrayList<>();
        for (ModelGroup group : all) {
            if (needle == null || group.matchKeyword(needle.toLowerCase(Locale.ROOT))) {
                keywordFiltered.add(group);
            }
        }
        Map<String, Long> bands = bandCounts(keywordFiltered);
        List<Map<String, Object>> providers = providerCounts(keywordFiltered);

        // 5) 提供商 / 价格带过滤
        String providerFilter = blankToNull(provider);
        String bandFilter = blankToNull(band);
        List<ModelGroup> matched = new ArrayList<>();
        for (ModelGroup group : keywordFiltered) {
            if (providerFilter != null && !group.hasProvider(providerFilter)) {
                continue;
            }
            if (bandFilter != null && !bandFilter.equals(group.band())) {
                continue;
            }
            matched.add(group);
        }

        // 6) 排序 + 分页
        sortGroups(matched, sort, order);
        long total = matched.size();
        long from = Math.min((safePage - 1) * safeSize, total);
        long to = Math.min(from + safeSize, total);
        List<Map<String, Object>> models = new ArrayList<>();
        for (ModelGroup group : matched.subList((int) from, (int) to)) {
            models.add(group.toMap());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("totalModels", totalModels);
        result.put("withPrice", withPrice);
        result.put("providerCount", providers.size());
        result.put("bands", bands);
        result.put("providers", providers);
        result.put("cnyPricingSkipped", cnySkipped);
        result.put("models", models);
        return result;
    }

    /**
     * 千 token 价 → 百万 token 价（null 透传）。
     *
     * @param pricePer1k 千 token 价
     * @return 百万 token 价
     */
    private static BigDecimal toPerMillion(Double pricePer1k) {
        return pricePer1k == null ? null : BigDecimal.valueOf(pricePer1k).multiply(PER_1K_TO_PER_1M);
    }

    /**
     * 价格带分布统计（固定顺序：经济 → 实惠 → 标准 → 旗舰）。
     *
     * @param groups 模型组
     * @return band → 数量
     */
    private static Map<String, Long> bandCounts(List<ModelGroup> groups) {
        Map<String, Long> bands = new LinkedHashMap<>();
        bands.put(BAND_ECONOMY, 0L);
        bands.put(BAND_VALUE, 0L);
        bands.put(BAND_STANDARD, 0L);
        bands.put(BAND_FLAGSHIP, 0L);
        for (ModelGroup group : groups) {
            String band = group.band();
            if (band != null) {
                bands.merge(band, 1L, Long::sum);
            }
        }
        return bands;
    }

    /**
     * 提供商分布统计（按模型数倒序，供筛选下拉）。
     *
     * @param groups 模型组
     * @return [{provider, count}]
     */
    private static List<Map<String, Object>> providerCounts(List<ModelGroup> groups) {
        Map<String, Long> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ModelGroup group : groups) {
            Set<String> seen = new HashSet<>();
            for (Quote quote : group.quotes.values()) {
                if (quote.provider != null && seen.add(quote.provider)) {
                    counts.merge(quote.provider, 1L, Long::sum);
                }
            }
        }
        List<Map<String, Object>> providers = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("provider", entry.getKey());
            item.put("count", entry.getValue());
            providers.add(item);
        }
        providers.sort(Comparator.comparingLong((Map<String, Object> m) -> ((Number) m.get("count")).longValue()).reversed());
        if (providers.size() > MAX_PROVIDER_FACETS) {
            providers = new ArrayList<>(providers.subList(0, MAX_PROVIDER_FACETS));
        }
        return providers;
    }

    /**
     * 排序：price（默认升序）/ value、index（默认降序），空值恒排最后。
     *
     * @param groups 模型组
     * @param sort   排序键
     * @param order  排序方向
     */
    private static void sortGroups(List<ModelGroup> groups, String sort, String order) {
        String key = blankToNull(sort) == null ? SORT_PRICE : sort.trim().toLowerCase(Locale.ROOT);
        Comparator<ModelGroup> comparator;
        boolean asc;
        if (SORT_VALUE.equals(key)) {
            comparator = Comparator.comparing(ModelGroup::valueScore, Comparator.nullsLast(Comparator.naturalOrder()));
            asc = ORDER_ASC.equalsIgnoreCase(order);
        } else if (SORT_INDEX.equals(key)) {
            comparator = Comparator.comparing((ModelGroup g) -> g.intelligenceIndex,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            asc = ORDER_ASC.equalsIgnoreCase(order);
        } else {
            comparator = Comparator.comparing(ModelGroup::effectivePrice, Comparator.nullsLast(Comparator.naturalOrder()));
            asc = order == null || ORDER_ASC.equalsIgnoreCase(order);
        }
        groups.sort(asc ? comparator : comparator.reversed());
    }

    /**
     * 第一个非空白字符串（可空入参）。
     *
     * @param values 候选值
     * @return 首个非空白值；全空返回 null
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 空白串转 null。
     *
     * @param value 原值
     * @return 去空格后的值；空白返回 null
     */
    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * 单个模型的聚合视图（内部构建用）。
     */
    private static final class ModelGroup {

        /**
         * 模型名（展示 / 分组键）
         */
        private final String model;

        /**
         * 智能指数（多行取最大）
         */
        private BigDecimal intelligenceIndex;

        /**
         * 提供商报价（provider 归一键 → 报价；null 提供商归一为空串键）
         */
        private final Map<String, Quote> quotes = new LinkedHashMap<>();

        /**
         * 构造模型组。
         *
         * @param model 模型名
         */
        private ModelGroup(String model) {
            this.model = model;
        }

        /**
         * 并入一条报价：指标行只补空字段；定价行（权威）覆盖非空字段。
         *
         * @param provider  提供商（可空）
         * @param input     输入价（USD/百万 token）
         * @param output    输出价（USD/百万 token）
         * @param cacheHit  缓存命中价
         * @param cacheWrite 缓存写入价
         * @param source    来源标记
         */
        private void merge(String provider, BigDecimal input, BigDecimal output,
                           BigDecimal cacheHit, BigDecimal cacheWrite, String source) {
            String key = provider == null ? "" : provider;
            Quote quote = quotes.computeIfAbsent(key, k -> new Quote(provider));
            quote.merge(input, output, cacheHit, cacheWrite, source);
        }

        /**
         * 关键字匹配：模型名或任一提供商名包含关键字。
         *
         * @param needleLower 小写关键字
         * @return 是否命中
         */
        private boolean matchKeyword(String needleLower) {
            if (model.toLowerCase(Locale.ROOT).contains(needleLower)) {
                return true;
            }
            for (Quote quote : quotes.values()) {
                if (quote.provider != null && quote.provider.toLowerCase(Locale.ROOT).contains(needleLower)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 是否包含指定提供商。
         *
         * @param provider 提供商名
         * @return 是否包含
         */
        private boolean hasProvider(String provider) {
            return quotes.containsKey(provider);
        }

        /**
         * 比价基准价：输出价最低者；无输出价时退回输入价最低者。
         *
         * @return 基准价；无价格返回 null
         */
        private BigDecimal effectivePrice() {
            BigDecimal min = null;
            for (Quote quote : quotes.values()) {
                BigDecimal price = quote.price();
                if (price != null && (min == null || price.compareTo(min) < 0)) {
                    min = price;
                }
            }
            return min;
        }

        /**
         * 价格带（按基准价分档）。
         *
         * @return economy/value/standard/flagship；无价格返回 null
         */
        private String band() {
            BigDecimal price = effectivePrice();
            if (price == null) {
                return null;
            }
            if (price.compareTo(ECONOMY_MAX) < 0) {
                return BAND_ECONOMY;
            }
            if (price.compareTo(VALUE_MAX) < 0) {
                return BAND_VALUE;
            }
            if (price.compareTo(STANDARD_MAX) < 0) {
                return BAND_STANDARD;
            }
            return BAND_FLAGSHIP;
        }

        /**
         * 性价比：智能指数 ÷ 基准价（1 位小数）。
         *
         * @return 性价比；缺智能指数或价格为 0 返回 null
         */
        private BigDecimal valueScore() {
            BigDecimal price = effectivePrice();
            if (intelligenceIndex == null || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return intelligenceIndex.divide(price, 1, RoundingMode.HALF_UP);
        }

        /**
         * 输出为前端展示结构。
         *
         * @return {model, intelligenceIndex, band, minPrice, maxPrice, valueScore, providers}
         */
        private Map<String, Object> toMap() {
            List<Quote> sorted = new ArrayList<>(quotes.values());
            // 报价按基准价升序（空价最后），最低价排最上并标 cheapest
            sorted.sort(Comparator.comparing(Quote::price, Comparator.nullsLast(Comparator.naturalOrder())));
            BigDecimal maxPrice = null;
            for (Quote quote : sorted) {
                BigDecimal price = quote.price();
                if (price != null && (maxPrice == null || price.compareTo(maxPrice) > 0)) {
                    maxPrice = price;
                }
            }
            List<Map<String, Object>> providerList = new ArrayList<>();
            BigDecimal minPrice = effectivePrice();
            for (Quote quote : sorted) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("provider", quote.provider);
                item.put("input", quote.input);
                item.put("output", quote.output);
                item.put("cacheHit", quote.cacheHit);
                item.put("cacheWrite", quote.cacheWrite);
                item.put("price", quote.price());
                item.put("source", quote.source);
                item.put("cheapest", quote.price() != null && quote.price().compareTo(minPrice) == 0);
                providerList.add(item);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("model", model);
            item.put("intelligenceIndex", intelligenceIndex);
            item.put("band", band());
            item.put("minPrice", minPrice);
            item.put("maxPrice", maxPrice);
            item.put("valueScore", valueScore());
            item.put("providers", providerList);
            return item;
        }
    }

    /**
     * 单个提供商对某个模型的一条报价（内部构建用）。
     */
    private static final class Quote {

        /**
         * 提供商名（可空，展示由前端兜底）
         */
        private final String provider;

        /**
         * 输入价（USD/百万 token）
         */
        private BigDecimal input;

        /**
         * 输出价（USD/百万 token）
         */
        private BigDecimal output;

        /**
         * 缓存命中价（USD/百万 token）
         */
        private BigDecimal cacheHit;

        /**
         * 缓存写入价（USD/百万 token）
         */
        private BigDecimal cacheWrite;

        /**
         * 来源标记：metrics / pricing:manual / pricing:sync
         */
        private String source;

        /**
         * 构造报价。
         *
         * @param provider 提供商
         */
        private Quote(String provider) {
            this.provider = provider;
        }

        /**
         * 并入字段：定价行来源（pricing:*）覆盖非空字段；指标行只补空字段。
         *
         * @param in   输入价
         * @param out  输出价
         * @param hit  缓存命中价
         * @param write 缓存写入价
         * @param src  来源标记
         */
        private void merge(BigDecimal in, BigDecimal out, BigDecimal hit, BigDecimal write, String src) {
            boolean override = src != null && src.startsWith(SOURCE_PRICING_PREFIX);
            if (override) {
                if (in != null) {
                    this.input = in;
                }
                if (out != null) {
                    this.output = out;
                }
                if (hit != null) {
                    this.cacheHit = hit;
                }
                if (write != null) {
                    this.cacheWrite = write;
                }
                this.source = src;
            } else {
                if (this.input == null) {
                    this.input = in;
                }
                if (this.output == null) {
                    this.output = out;
                }
                if (this.cacheHit == null) {
                    this.cacheHit = hit;
                }
                if (this.cacheWrite == null) {
                    this.cacheWrite = write;
                }
                if (this.source == null) {
                    this.source = src;
                }
            }
        }

        /**
         * 画条 / 比价用的基准价：输出价优先，退回输入价。
         *
         * @return 基准价；两者皆空返回 null
         */
        private BigDecimal price() {
            return output != null ? output : input;
        }
    }
}
