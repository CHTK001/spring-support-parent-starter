// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiModelSetting;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.mapper.SysAiModelSettingMapper;
import com.chua.starter.ai.support.mapper.SysAiPricingMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMetricMapper;
import com.chua.starter.ai.support.manage.service.SysAiProviderMetricService;
import com.chua.starter.ai.support.manage.store.SysAiProviderMetric;
import com.chua.starter.ai.support.service.AiMossPriceSyncService;
import com.chua.starter.ai.support.service.SysAiPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moss 模型价格同步服务实现。
 *
 * <p>免密钥抓取 Moss 公开模型页（{@value #MODELS_PAGE_URL}），把每个模型卡片的
 * 官方计费口径解析成 {@code sys_ai_pricing} 行：限时免费记 0；「¥X / 万字符」折算成
 * 每 1k（÷10）；「¥X / 小时」「¥X / 张」按原生计量单位记入单价列（币种 CNY，
 * 来源 {@value #SOURCE}）。解析成功后按同一批数据刷新 Moss 的厂商指标系数
 * （中位数 ×1000 折算成百万口径；Moss 只公布人民币牌价，该系数为 CNY 口径的兜底预估值）。</p>
 *
 * @since 4.0.0.58
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMossPriceSyncServiceImpl implements AiMossPriceSyncService {

    /**
     * 厂商键（写入 sys_ai_pricing.provider / sys_ai_provider_metric.provider）
     */
    private static final String PROVIDER = "moss";

    /**
     * 厂商展示名
     */
    private static final String PROVIDER_NAME = "Moss";

    /**
     * 数据来源标识
     */
    private static final String SOURCE = "moss-docs";

    /**
     * Moss 公开模型页（无需密钥）
     */
    private static final String MODELS_PAGE_URL = "https://platform.moss.cn/docs/models";

    /**
     * 定价查询缓存名（与 SysAiPricingServiceImpl 的 getLatestPricing 缓存一致）
     */
    private static final String PRICING_CACHE = "aiPricing";

    /**
     * 抓取超时
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    /**
     * 模型 id：nex-* / moss-*（贪婪匹配会带上的 snapshot 日期后缀由 canonicalId 剥掉）
     */
    private static final Pattern MODEL_ID = Pattern.compile("(?:nex|moss)-[a-z0-9]+(?:-[a-z0-9.]+)*");

    /**
     * snapshot 日期后缀（如 -2026-11-05）
     */
    private static final Pattern SNAPSHOT_SUFFIX = Pattern.compile("-20\\d{2}-\\d{2}-\\d{2}$");

    /**
     * 「¥X / 万字符」计费（折算每 1k = ÷10）
     */
    private static final Pattern PRICE_PER_10K_CHARS = Pattern.compile("¥\\s*([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*万字符");

    /**
     * 「¥X / 小时」计费（转写按音频时长）
     */
    private static final Pattern PRICE_PER_HOUR = Pattern.compile("¥\\s*([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*小时");

    /**
     * 「¥X / 张」计费（图像按张，取第一个档位即低分辨率价）
     */
    private static final Pattern PRICE_PER_IMAGE = Pattern.compile("¥\\s*([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*张");

    /**
     * 定价 映射器
     */
    private final SysAiPricingMapper pricingMapper;

    /**
     * 模型设置 映射器（replace-models 补插模型清单）
     */
    private final SysAiModelSettingMapper modelSettingMapper;

    /**
     * 厂商指标系数 映射器（指标刷新）
     */
    private final SysAiProviderMetricMapper providerMetricMapper;

    /**
     * 按厂商编码同步模型价格。
     *
     * <p>抓取失败或模型页没有命中编码时不改动库内数据，只记日志（与旧同步服务一致不抛异常）。</p>
     *
     * @param code 厂商 / 模型编码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = {PRICING_CACHE, SysAiProviderMetricService.CACHE_NAME}, allEntries = true)
    public void syncPrices(String code) {
        List<MossModelPrice> models = filter(fetchModelPrices(), code);
        if (models.isEmpty()) {
            log.warn("[MossPricing] 模型页未解析到可同步的价格（code={}），跳过", code);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (MossModelPrice model : models) {
            upsertPricing(model, now);
        }
        refreshProviderMetric(models, now);
        log.info("[MossPricing] Moss 模型价格同步完成，共 {} 个模型（{}/{}）", models.size(), SOURCE, MODELS_PAGE_URL);
    }

    /**
     * 用 Moss 公开模型页补插本地模型清单（缺失才插，保留已有），并刷新价格。
     *
     * @param code 厂商 / 模型编码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = {PRICING_CACHE, SysAiProviderMetricService.CACHE_NAME}, allEntries = true)
    public void replaceModels(String code) {
        List<MossModelPrice> models = filter(fetchModelPrices(), code);
        if (models.isEmpty()) {
            log.warn("[MossPricing] 模型页未解析到可替换的模型（code={}），跳过", code);
            return;
        }
        int inserted = 0;
        int sort = 0;
        for (MossModelPrice model : models) {
            List<SysAiModelSetting> existing = modelSettingMapper.selectList(
                    new LambdaQueryWrapper<SysAiModelSetting>().eq(SysAiModelSetting::getCode, model.model()));
            if (existing.isEmpty()) {
                SysAiModelSetting setting = new SysAiModelSetting();
                setting.setCode(model.model());
                setting.setName(model.model());
                setting.setProvider(PROVIDER);
                setting.setStatus(1);
                setting.setSort(sort);
                modelSettingMapper.insert(setting);
                inserted++;
            }
            sort++;
        }
        log.info("[MossPricing] Moss 模型清单补插完成，新增 {} 个（已有保留）", inserted);
        LocalDateTime now = LocalDateTime.now();
        for (MossModelPrice model : models) {
            upsertPricing(model, now);
        }
        refreshProviderMetric(models, now);
        log.info("[MossPricing] Moss 模型价格同步完成，共 {} 个模型（{}/{}）", models.size(), SOURCE, MODELS_PAGE_URL);
    }

    /**
     * 抓取模型页并解析各模型价格。
     *
     * @return 模型价格列表；抓取失败返回空列表
     */
    private List<MossModelPrice> fetchModelPrices() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MODELS_PAGE_URL))
                    .timeout(TIMEOUT)
                    .header("Accept", "text/html")
                    .header("Accept-Language", "zh-CN")
                    .header("User-Agent", "Mozilla/5.0 (compatible; moss-price-sync)")
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("[MossPricing] 模型页抓取失败: HTTP {}", response.statusCode());
                return List.of();
            }
            return parseModelPrices(stripHtml(response.body()));
        } catch (Exception e) {
            log.warn("[MossPricing] 模型页抓取异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 去掉 script/style 块与全部标签，得到线性文本。
     *
     * @param html 页面源码
     * @return 纯文本
     */
    private static String stripHtml(String html) {
        String text = html.replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("(?s)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", "");
        return text.replace("&yen;", "¥").replace("&#165;", "¥");
    }

    /**
     * 从线性文本解析模型价格。
     *
     * <p>模型卡片在文本里按「模型 id → 标签 → snapshot → 说明 → 端点 → 计费」顺序排列，
     * 以模型 id 切段，同 id（含 snapshot 后缀）只保留首段。</p>
     *
     * @param text 页面纯文本
     * @return 模型价格列表（保持页面顺序）
     */
    private static List<MossModelPrice> parseModelPrices(String text) {
        Matcher matcher = MODEL_ID.matcher(text);
        List<String> ids = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String canonical = canonicalId(matcher.group());
            if (!seen.add(canonical)) {
                continue;
            }
            ids.add(canonical);
            starts.add(matcher.start());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        int limit = text.indexOf("复制本页 Markdown");
        if (limit <= starts.get(starts.size() - 1)) {
            limit = text.length();
        }
        List<MossModelPrice> models = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            int end = i + 1 < ids.size() ? starts.get(i + 1) : limit;
            String segment = text.substring(starts.get(i), Math.min(end, text.length()));
            models.add(new MossModelPrice(ids.get(i), inputPriceOf(segment), outputPriceOf(segment)));
        }
        return models;
    }

    /**
     * 剥掉 snapshot 日期后缀，得到稳定模型 id。
     *
     * @param raw 匹配到的原始 id
     * @return 稳定模型 id
     */
    private static String canonicalId(String raw) {
        return SNAPSHOT_SUFFIX.matcher(raw).replaceAll("");
    }

    /**
     * 解析模型卡片的输入侧单价。
     *
     * @param segment 模型卡片文本段
     * @return 每 1k 单价（万字符口径 ÷10；小时 / 张口径记原生单价）；限时免费为 0；未识别为 null
     */
    private static Double inputPriceOf(String segment) {
        if (segment.contains("限时免费")) {
            return 0D;
        }
        BigDecimal price = priceOf(segment, PRICE_PER_10K_CHARS);
        if (price != null) {
            return price.divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP).doubleValue();
        }
        price = priceOf(segment, PRICE_PER_HOUR);
        if (price != null) {
            return price.doubleValue();
        }
        price = priceOf(segment, PRICE_PER_IMAGE);
        return price == null ? null : price.doubleValue();
    }

    /**
     * 解析模型卡片的输出侧单价（当前模型页只有语言模型按 token 计费且限时免费）。
     *
     * @param segment 模型卡片文本段
     * @return 每 1k 输出单价；未识别为 null
     */
    private static Double outputPriceOf(String segment) {
        return segment.contains("限时免费") ? 0D : null;
    }

    /**
     * 在文本段里取第一个命中的价格。
     *
     * @param segment 模型卡片文本段
     * @param pattern 价格模式
     * @return 价格；未命中返回 null
     */
    private static BigDecimal priceOf(String segment, Pattern pattern) {
        Matcher matcher = pattern.matcher(segment);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    /**
     * 按编码过滤模型（空 / moss 表示全部）。
     *
     * @param models 解析结果
     * @param code   厂商 / 模型编码
     * @return 过滤后的列表
     */
    private static List<MossModelPrice> filter(List<MossModelPrice> models, String code) {
        if (code == null || code.isBlank() || PROVIDER.equalsIgnoreCase(code.trim())) {
            return models;
        }
        String target = code.trim().toLowerCase(Locale.ROOT);
        List<MossModelPrice> matched = new ArrayList<>(1);
        for (MossModelPrice model : models) {
            if (model.model().toLowerCase(Locale.ROOT).equals(target)) {
                matched.add(model);
            }
        }
        return matched;
    }

    /**
     * 写入 / 更新一条官方定价（按厂商 + 模型取最新一条原地更新，缺失才插入）。
     *
     * @param model 模型价格
     * @param now   生效时间
     */
    private void upsertPricing(MossModelPrice model, LocalDateTime now) {
        if (model.inputPer1k() == null && model.outputPer1k() == null) {
            return;
        }
        SysAiPricing pricing = pricingMapper.selectOne(new LambdaQueryWrapper<SysAiPricing>()
                .eq(SysAiPricing::getProvider, PROVIDER)
                .eq(SysAiPricing::getModel, model.model())
                .orderByDesc(SysAiPricing::getEffectiveDate)
                .last("limit 1"));
        // 人工单价不被自动同步覆盖
        if (pricing != null && SysAiPricingService.SOURCE_MANUAL.equals(pricing.getSource())) {
            log.info("[Pricing] 模型 {} 已配置人工单价，跳过 {} 同步覆盖", model.model(), PROVIDER);
            return;
        }
        boolean fresh = pricing == null;
        if (fresh) {
            pricing = new SysAiPricing();
            pricing.setProvider(PROVIDER);
            pricing.setModel(model.model());
        }
        pricing.setInputPricePer1k(model.inputPer1k());
        pricing.setOutputPricePer1k(model.outputPer1k());
        pricing.setCurrency("CNY");
        pricing.setEffectiveDate(now);
        pricing.setSource(SOURCE);
        if (fresh) {
            pricingMapper.insert(pricing);
        } else {
            pricingMapper.updateById(pricing);
        }
    }

    /**
     * 刷新 Moss 的厂商指标系数：中位数单价 ×1000 折成百万口径。
     *
     * <p>Moss 未收录在 Models.dev，这里用自家模型页数据补一条兜底系数；
     * 页面只公布人民币牌价，系数为 CNY 口径，source 标记 {@value #SOURCE}。</p>
     *
     * @param models 参与聚合的模型
     * @param now    入库时间
     */
    private void refreshProviderMetric(List<MossModelPrice> models, LocalDateTime now) {
        List<BigDecimal> inputs = new ArrayList<>(models.size());
        List<BigDecimal> outputs = new ArrayList<>(models.size());
        for (MossModelPrice model : models) {
            if (model.inputPer1k() != null) {
                inputs.add(BigDecimal.valueOf(model.inputPer1k()));
            }
            if (model.outputPer1k() != null) {
                outputs.add(BigDecimal.valueOf(model.outputPer1k()));
            }
        }
        SysAiProviderMetric metric = providerMetricMapper.selectOne(new LambdaQueryWrapper<SysAiProviderMetric>()
                .eq(SysAiProviderMetric::getProvider, PROVIDER)
                .last("limit 1"));
        boolean fresh = metric == null;
        if (fresh) {
            metric = new SysAiProviderMetric();
            metric.setProvider(PROVIDER);
            metric.setCreateTime(now);
        }
        metric.setProviderName(PROVIDER_NAME);
        metric.setSource(SOURCE);
        metric.setModelCount(models.size());
        metric.setPriceInputPerMusd(perMillion(median(inputs)));
        metric.setPriceOutputPerMusd(perMillion(median(outputs)));
        metric.setUpdateTime(now);
        if (fresh) {
            providerMetricMapper.insert(metric);
        } else {
            providerMetricMapper.updateById(metric);
        }
    }

    /**
     * 每 1k 单价折成百万口径。
     *
     * @param per1k 每 1k 单价
     * @return 百万口径单价；入参为 null 返回 null
     */
    private static BigDecimal perMillion(BigDecimal per1k) {
        return per1k == null ? null : per1k.multiply(BigDecimal.valueOf(1000)).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 计算中位数（尾数 6 位）。
     *
     * @param values 数值列表
     * @return 中位数；空列表返回 null
     */
    private static BigDecimal median(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(java.util.Comparator.naturalOrder());
        int n = sorted.size();
        BigDecimal result = (n & 1) == 1
                ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2))
                        .divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
        return result.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 一个模型的解析结果。
     *
     * @param model      稳定模型 id
     * @param inputPer1k 输入侧每 1k 单价（CNY）
     * @param outputPer1k 输出侧每 1k 单价（CNY）
     */
    private record MossModelPrice(String model, Double inputPer1k, Double outputPer1k) {
    }
}
