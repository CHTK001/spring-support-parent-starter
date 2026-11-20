// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiModelSetting;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.mapper.SysAiModelSettingMapper;
import com.chua.starter.ai.support.mapper.SysAiPricingMapper;
import com.chua.starter.ai.support.service.AiDeepSeekPriceSyncService;
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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepSeek 模型价格同步服务实现。
 *
 * <p>免密钥抓取 DeepSeek 官方文档定价页（{@value #PRICING_PAGE_URL}），解析
 * 「模型 &amp; 价格」大表并写入 {@code sys_ai_pricing}：模型 id 取表格「BASE URL」
 * 之前的列头；价格取「百万tokens输入」到「并发限制」之间的 6 行矩阵（输入缓存命中
 * 空闲/高峰、输入缓存未命中空闲/高峰、输出空闲/高峰），每行按模型列依次排布。入库取
 * <b>高峰时段</b>价（页面脚注：空闲价为高峰的一半），输入侧用缓存未命中价（标准
 * 输入价）；「元 / 百万 tokens」÷1000 折算成每 1k 单价，币种 CNY，来源
 * {@value #SOURCE}。</p>
 *
 * <p>与 Moss 同步不同：DeepSeek 已被 Models.dev 指标同步覆盖，这里只写
 * {@code sys_ai_pricing}，<b>不刷新</b> {@code sys_ai_provider_metric}，避免覆盖
 * 指标同步写入的行。</p>
 *
 * @since 4.0.0.59
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDeepSeekPriceSyncServiceImpl implements AiDeepSeekPriceSyncService {

    /**
     * 厂商键（写入 sys_ai_pricing.provider）
     */
    private static final String PROVIDER = "deepseek";

    /**
     * 数据来源标识
     */
    private static final String SOURCE = "deepseek-docs";

    /**
     * DeepSeek 官方文档定价页（无需密钥）
     */
    private static final String PRICING_PAGE_URL = "https://api-docs.deepseek.com/zh-cn/quick_start/pricing/";

    /**
     * 定价查询缓存名（与 SysAiPricingServiceImpl 的 getLatestPricing 缓存一致）
     */
    private static final String PRICING_CACHE = "aiPricing";

    /**
     * 「N元」金额提取正则（[P3C 修复] 类级预编译，避免方法体内重复编译）
     */
    private static final Pattern PRICE_YUAN_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*元");

    /**
     * 抓取超时
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    /**
     * 模型 id：表格列头里的 deepseek-xxx（贪婪匹配会带上的版本号由列头边界截断）
     */
    private static final Pattern MODEL_ID = Pattern.compile("deepseek-[a-z0-9]+(?:-[a-z0-9.]+)*");

    /**
     * 「BASE URL」列头行：模型 id 段的下界（脚注里的旧模型名都在它之后，不参与解析）
     */
    private static final String BASE_URL_MARK = "BASE URL";

    /**
     * 价格矩阵上界：并发限制行
     */
    private static final String PRICE_END_MARK = "并发限制";

    /**
     * 价格矩阵下界：百万 tokens 输入行（首个价格行）
     */
    private static final String PRICE_START_MARK = "百万tokens输入";

    /**
     * 一张价格矩阵的行数：输入(命中/未命中)×(空闲/高峰) + 输出×(空闲/高峰)
     */
    private static final int PRICE_ROWS = 6;

    /**
     * 输入价取「缓存未命中 · 高峰时段」行（标准输入价，0 起数）
     */
    private static final int INPUT_ROW = 3;

    /**
     * 输出价取「高峰时段」行
     */
    private static final int OUTPUT_ROW = 5;

    /**
     * 元/百万 → 元/每 1k 的折算除数
     */
    private static final BigDecimal PER_MILLION = BigDecimal.valueOf(1000);

    /**
     * 定价 映射器
     */
    private final SysAiPricingMapper pricingMapper;

    /**
     * 模型设置 映射器（replace-models 补插模型清单）
     */
    private final SysAiModelSettingMapper modelSettingMapper;

    /**
     * 按厂商编码同步模型价格。
     *
     * <p>抓取失败或定价页没有命中编码时不改动库内数据，只记日志（不抛异常）。</p>
     *
     * @param code 厂商 / 模型编码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = PRICING_CACHE, allEntries = true)
    public void syncPrices(String code) {
        List<DeepSeekModelPrice> models = filter(fetchModelPrices(), code);
        if (models.isEmpty()) {
            log.warn("[DeepSeekPricing] 定价页未解析到可同步的价格（code={}），跳过", code);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DeepSeekModelPrice model : models) {
            upsertPricing(model, now);
        }
        log.info("[DeepSeekPricing] DeepSeek 模型价格同步完成，共 {} 个模型（{}/{}）",
                models.size(), SOURCE, PRICING_PAGE_URL);
    }

    /**
     * 用 DeepSeek 定价页补插本地模型清单（缺失才插，保留已有），并刷新价格。
     *
     * @param code 厂商 / 模型编码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = PRICING_CACHE, allEntries = true)
    public void replaceModels(String code) {
        List<DeepSeekModelPrice> models = filter(fetchModelPrices(), code);
        if (models.isEmpty()) {
            log.warn("[DeepSeekPricing] 定价页未解析到可替换的模型（code={}），跳过", code);
            return;
        }
        int inserted = 0;
        int sort = 0;
        for (DeepSeekModelPrice model : models) {
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
        log.info("[DeepSeekPricing] DeepSeek 模型清单补插完成，新增 {} 个（已有保留）", inserted);
        LocalDateTime now = LocalDateTime.now();
        for (DeepSeekModelPrice model : models) {
            upsertPricing(model, now);
        }
        log.info("[DeepSeekPricing] DeepSeek 模型价格同步完成，共 {} 个模型（{}/{}）",
                models.size(), SOURCE, PRICING_PAGE_URL);
    }

    /**
     * 抓取定价页并解析各模型价格。
     *
     * @return 模型价格列表；抓取失败返回空列表
     */
    private List<DeepSeekModelPrice> fetchModelPrices() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PRICING_PAGE_URL))
                    .timeout(TIMEOUT)
                    .header("Accept", "text/html")
                    .header("Accept-Language", "zh-CN")
                    .header("User-Agent", "Mozilla/5.0 (compatible; deepseek-price-sync)")
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("[DeepSeekPricing] 定价页抓取失败: HTTP {}", response.statusCode());
                return List.of();
            }
            return parseModelPrices(stripHtml(response.body()));
        } catch (Exception e) {
            log.warn("[DeepSeekPricing] 定价页抓取异常: {}", e.getMessage());
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
        return html.replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("(?s)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", "")
                .replace("&yen;", "¥").replace("&#165;", "¥");
    }

    /**
     * 从线性文本解析模型价格。
     *
     * <p>页面是「模型细节 + 价格」一张双列大表：模型 id 列头在 {@code BASE URL}
     * 之前；价格数字在「百万tokens输入」到「并发限制」之间，按
     * {@link #PRICE_ROWS} 行 × 模型列依次排布。数字个数必须恰好是 6 × 模型数，
     * 对不上说明页面结构变了，整页跳过、不写半份数据。</p>
     *
     * @param text 页面纯文本
     * @return 模型价格列表（保持页面列序）
     */
    private static List<DeepSeekModelPrice> parseModelPrices(String text) {
        int tableStart = text.indexOf(BASE_URL_MARK);
        int priceStart = text.indexOf(PRICE_START_MARK);
        int priceEnd = text.indexOf(PRICE_END_MARK);
        if (tableStart < 0 || priceStart < 0 || priceEnd <= priceStart) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        Matcher matcher = MODEL_ID.matcher(text.substring(0, tableStart));
        while (matcher.find()) {
            String id = matcher.group();
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<BigDecimal> numbers = pricesOf(text.substring(priceStart, priceEnd));
        if (numbers.size() != ids.size() * PRICE_ROWS) {
            return List.of();
        }
        List<DeepSeekModelPrice> models = new ArrayList<>(ids.size());
        for (int col = 0; col < ids.size(); col++) {
            // 高峰时段价为准（空闲价是其一半）；元/百万 ÷1000 折成每 1k
            Double input = per1k(numbers.get(INPUT_ROW * ids.size() + col));
            Double output = per1k(numbers.get(OUTPUT_ROW * ids.size() + col));
            models.add(new DeepSeekModelPrice(ids.get(col), input, output));
        }
        return models;
    }

    /**
     * 在价格段里按出现顺序取所有「N元」金额。
     *
     * @param segment 价格段文本
     * @return 金额列表（页面行序 × 模型列序）
     */
    private static List<BigDecimal> pricesOf(String segment) {
        Matcher matcher = PRICE_YUAN_PATTERN.matcher(segment);
        List<BigDecimal> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(new BigDecimal(matcher.group(1)));
        }
        return numbers;
    }

    /**
     * 元/百万单价折成每 1k 单价。
     *
     * @param perMillion 元/百万单价
     * @return 每 1k 单价（6 位小数）；入参为 null 返回 null
     */
    private static Double per1k(BigDecimal perMillion) {
        return perMillion == null ? null
                : perMillion.divide(PER_MILLION, 6, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 按编码过滤模型（空 / deepseek 表示全部）。
     *
     * @param models 解析结果
     * @param code   厂商 / 模型编码
     * @return 过滤后的列表
     */
    private static List<DeepSeekModelPrice> filter(List<DeepSeekModelPrice> models, String code) {
        if (code == null || code.isBlank() || PROVIDER.equalsIgnoreCase(code.trim())) {
            return models;
        }
        String target = code.trim().toLowerCase(Locale.ROOT);
        List<DeepSeekModelPrice> matched = new ArrayList<>(1);
        for (DeepSeekModelPrice model : models) {
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
    private void upsertPricing(DeepSeekModelPrice model, LocalDateTime now) {
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
     * 一个模型的解析结果。
     *
     * @param model       稳定模型 id
     * @param inputPer1k  输入侧每 1k 单价（CNY，缓存未命中高峰价）
     * @param outputPer1k 输出侧每 1k 单价（CNY，高峰价）
     */
    private record DeepSeekModelPrice(String model, Double inputPer1k, Double outputPer1k) {
    }
}
