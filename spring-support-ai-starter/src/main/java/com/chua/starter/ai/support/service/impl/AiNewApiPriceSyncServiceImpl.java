// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.ai.support.entity.SysAiModelSetting;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.mapper.SysAiModelSettingMapper;
import com.chua.starter.ai.support.mapper.SysAiPricingMapper;
import com.chua.starter.ai.support.service.AiNewApiPriceSyncService;
import com.chua.starter.ai.support.service.SysAiPricingService;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.chua.common.support.utils.CollectionUtils;

/**
 * new-api 模型价格同步服务实现。
 *
 * <p>new-api / one-api 系站点的 {@code GET /api/pricing} 公开定价接口返回模型倍率清单
 * （{@code model_ratio} / {@code completion_ratio} / {@code model_price} / {@code quota_type}）
 * 与顶层分组倍率（{@code group_ratio}）。按站点配额口径折算：{@code 1 美元 = 500000 配额}，
 * 倍率 1 = 每 1k 令牌 0.002 美元；输入价 = {@code model_ratio × 0.002}，输出价 =
 * {@code model_ratio × completion_ratio × 0.002}；{@code quota_type=1} 的按次计费模型取
 * {@code model_price}（美元/次）写入按次价。分组价 = 基础价 × {@code group_ratio[分组]}，
 * 入库基准取倍率 1.0 的首个可用分组（缺省取第一个可用分组），同模型其余分组报价记入
 * 跨分组差异 JSON。new-api 不提供分档 / 峰值 / 官方价 / 缓存价，相应列写空。</p>
 *
 * @since 4.0.0.60
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiNewApiPriceSyncServiceImpl implements AiNewApiPriceSyncService {

    /**
     * 厂商键（写入 sys_ai_pricing.provider）
     */
    private static final String PROVIDER = "newapi";

    /**
     * 数据来源标识
     */
    private static final String SOURCE = "newapi-pricing";

    /**
     * 公开定价接口路径（无需密钥）
     */
    private static final String PRICING_PATH = "/api/pricing";

    /**
     * 定价查询缓存名（与 SysAiPricingServiceImpl 的 getLatestPricing 缓存一致）
     */
    private static final String PRICING_CACHE = "aiPricing";

    /**
     * 站点根地址可能带的接口前缀（长前缀优先）
     */
    private static final String[] API_PREFIXES = {"/api/v1", "/v1", "/api"};

    /**
     * new-api 平台名（供应商行 platform 字段，含连字符别名）
     */
    private static final Set<String> PLATFORMS = Set.of("newapi", "new-api");

    /**
     * 抓取超时
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    /**
     * new-api 配额口径：1 美元对应的配额数（QuotaPerUnit 缺省值）
     */
    private static final BigDecimal QUOTA_PER_UNIT = BigDecimal.valueOf(500000);

    /**
     * 倍率 1 对应的每 1k 令牌美元单价（1000 / 500000 = 0.002）
     */
    private static final BigDecimal PER_1K_PRICE_PER_RATIO = BigDecimal.valueOf(1000)
            .divide(QUOTA_PER_UNIT, 10, RoundingMode.HALF_UP);

    /**
     * 读数小数位
     */
    private static final int SCALE_DIGITS = 6;

    /**
     * 计费类型：按次计价（quota_type=1，取 model_price 美元/次）
     */
    private static final int QUOTA_TYPE_PER_CALL = 1;

    /**
     * 响应 映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 定价 映射器
     */
    private final SysAiPricingMapper pricingMapper;

    /**
     * 模型设置 映射器（replace-models 补插模型清单）
     */
    private final SysAiModelSettingMapper modelSettingMapper;

    /**
     * 渠道服务（读 newapi 供应商行的上游地址，走 account 查询契约，不直接读渠道表）
     */
    private final AccountChannelService accountChannelService;

    /**
     * 按厂商编码同步模型价格。
     *
     * @param code    厂商 / 模型编码
     * @param baseUrl new-api 站点根地址（可空）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = PRICING_CACHE, allEntries = true)
    public void syncPrices(String code, String baseUrl) {
        List<NewApiModelPrice> models = filter(fetchModelPrices(baseUrl), code);
        if (models.isEmpty()) {
            log.warn("[NewApiPricing] 定价接口未解析到可同步的价格（code={}），跳过", code);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (NewApiModelPrice model : models) {
            upsertPricing(model, now);
        }
        log.info("[NewApiPricing] new-api 模型价格同步完成，共 {} 个模型（{}）", models.size(), SOURCE);
    }

    /**
     * 用定价接口补插本地模型清单（缺失才插，保留已有），并刷新价格。
     *
     * @param code    厂商 / 模型编码
     * @param baseUrl new-api 站点根地址（可空）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = PRICING_CACHE, allEntries = true)
    public void replaceModels(String code, String baseUrl) {
        List<NewApiModelPrice> models = filter(fetchModelPrices(baseUrl), code);
        if (models.isEmpty()) {
            log.warn("[NewApiPricing] 定价接口未解析到可替换的模型（code={}），跳过", code);
            return;
        }
        int inserted = 0;
        int sort = 0;
        for (NewApiModelPrice model : models) {
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
        log.info("[NewApiPricing] new-api 模型清单补插完成，新增 {} 个（已有保留）", inserted);
        LocalDateTime now = LocalDateTime.now();
        for (NewApiModelPrice model : models) {
            upsertPricing(model, now);
        }
        log.info("[NewApiPricing] new-api 模型价格同步完成，共 {} 个模型（{}）", models.size(), SOURCE);
    }

    /**
     * 抓取公开定价接口并解析各模型价格。
     *
     * @param baseUrl 站点根地址（可空，空时从供应商行解析）
     * @return 模型价格列表；抓取失败返回空列表
     */
    private List<NewApiModelPrice> fetchModelPrices(String baseUrl) {
        String base = resolveBaseUrl(baseUrl);
        if (base == null) {
            log.warn("[NewApiPricing] 未指定 new-api 站点地址，且库内没有登记上游地址的 newapi 供应商行，跳过");
            return List.of();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CreditHttp.join(base, PRICING_PATH)))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "spring-support-ai/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("[NewApiPricing] 定价接口抓取失败: HTTP {}", response.statusCode());
                return List.of();
            }
            return parsePricing(response.body());
        } catch (Exception e) {
            log.warn("[NewApiPricing] 定价接口抓取异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 new-api 站点根地址：显式指定优先，否则取库内第一个登记了上游地址的
     * newapi 供应商行（走 account 的查询契约，不直接读渠道表）。
     *
     * @param baseUrl 显式站点地址
     * @return 站点根地址；解析不到为 null
     */
    private String resolveBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return stripApiPrefixes(baseUrl.trim());
        }
        List<SysAccountChannel> rows = accountChannelService.listAll();
        if (CollectionUtils.isEmpty(rows)) {
            return null;
        }
        for (SysAccountChannel row : rows) {
            if (row.getPlatform() == null || row.getBaseUrl() == null || row.getBaseUrl().isBlank()) {
                continue;
            }
            if (PLATFORMS.contains(row.getPlatform().trim().toLowerCase(Locale.ROOT))) {
                return stripApiPrefixes(row.getBaseUrl().trim());
            }
        }
        return null;
    }

    /**
     * 解析定价接口响应。
     *
     * <p>信封 {@code success=false} 视为上游错误。每个模型条目含 {@code model_name}、
     * {@code model_ratio}、{@code completion_ratio}、{@code model_price}、{@code quota_type}
     * 与 {@code enable_groups}/{@code enable_group}；顶层 {@code group_ratio} 是分组倍率映射。
     * 倍率计费（quota_type=0）
     * 折算每 1k 美元单价，按次计费（quota_type=1）保留美元/次原口径；两者都缺的条目跳过。
     * 分组价 = 基础价 × 分组倍率，基准分组优先取倍率 1.0 的首个可用分组。</p>
     *
     * @param raw 原始响应
     * @return 模型价格列表（保持响应顺序，同模型首条胜出）
     */
    private static List<NewApiModelPrice> parsePricing(String raw) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            log.warn("[NewApiPricing] 定价接口响应不是合法 JSON: {}", e.getMessage());
            return List.of();
        }
        JsonNode success = root.path("success");
        if (success.isBoolean() && !success.asBoolean()) {
            log.warn("[NewApiPricing] 定价接口返回失败：{}", root.path("message").asText(""));
            return List.of();
        }
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            log.warn("[NewApiPricing] 定价接口未返回模型清单");
            return List.of();
        }
        Map<String, Double> groupRatio = parseGroupRatio(root.path("group_ratio"));
        List<NewApiModelPrice> models = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode entry : data) {
            String name = entry.path("model_name").asText("").trim();
            if (name.isEmpty() || !seen.add(name)) {
                continue;
            }
            Double inputBase = null;
            Double outputBase = null;
            Double perRequestBase = null;
            if (entry.path("quota_type").asInt(0) == QUOTA_TYPE_PER_CALL) {
                perRequestBase = decimalValue(entry.path("model_price"));
                if (perRequestBase == null) {
                    continue;
                }
            } else {
                Double ratio = decimalValue(entry.path("model_ratio"));
                if (ratio == null) {
                    continue;
                }
                Double completion = decimalValue(entry.path("completion_ratio"));
                if (completion == null) {
                    completion = BigDecimal.ONE.doubleValue();
                }
                inputBase = ratioPrice(ratio);
                outputBase = ratioPrice(BigDecimal.valueOf(ratio)
                        .multiply(BigDecimal.valueOf(completion)).doubleValue());
            }
            List<String> groups = parseGroups(entry);
            String baseGroup = pickBaseGroup(groups, groupRatio);
            double baseRate = groupRate(groupRatio, baseGroup);
            Double input = scaleByRate(inputBase, baseRate);
            Double output = scaleByRate(outputBase, baseRate);
            Double perRequest = scaleByRate(perRequestBase, baseRate);
            String groupQuotesJson = groups.size() > 1
                    ? buildGroupQuotesJson(groups, groupRatio, inputBase, outputBase, perRequestBase)
                    : null;
            models.add(new NewApiModelPrice(name, input, output, perRequest,
                    baseRate, baseGroup, groupQuotesJson));
        }
        return models;
    }

    /**
     * 解析顶层分组倍率映射。
     *
     * @param node group_ratio 节点
     * @return 分组名 → 倍率（保持响应顺序）
     */
    private static Map<String, Double> parseGroupRatio(JsonNode node) {
        Map<String, Double> ratios = new LinkedHashMap<>();
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                Double value = decimalValue(field.getValue());
                if (value != null) {
                    ratios.put(field.getKey(), value);
                }
            });
        }
        return ratios;
    }

    /**
     * 解析模型可用分组列表。
     *
     * <p>new-api 版本间字段名有差异：新版为 {@code enable_groups}，旧版为
     * {@code enable_group}，两者都兼容。</p>
     *
     * @param entry 模型条目节点
     * @return 分组名列表（去空白）；缺失时回落 default 分组
     */
    private static List<String> parseGroups(JsonNode entry) {
        List<String> groups = new ArrayList<>();
        JsonNode node = entry.path("enable_groups");
        if (!node.isArray()) {
            node = entry.path("enable_group");
        }
        if (node.isArray()) {
            for (JsonNode group : node) {
                String name = group.asText("").trim();
                if (!name.isEmpty()) {
                    groups.add(name);
                }
            }
        }
        if (groups.isEmpty()) {
            groups.add("default");
        }
        return groups;
    }

    /**
     * 选入库基准分组：优先取倍率 1.0 的首个可用分组（即站点标准价），没有则取第一个可用分组。
     *
     * @param groups     可用分组列表
     * @param groupRatio 分组倍率映射
     * @return 基准分组名
     */
    private static String pickBaseGroup(List<String> groups, Map<String, Double> groupRatio) {
        for (String group : groups) {
            Double rate = groupRatio.get(group);
            if (rate == null || rate.compareTo(BigDecimal.ONE.doubleValue()) == 0) {
                return group;
            }
        }
        return groups.get(0);
    }

    /**
     * 取分组倍率（未登记的分组按 1.0）。
     *
     * @param groupRatio 分组倍率映射
     * @param group      分组名
     * @return 分组倍率
     */
    private static double groupRate(Map<String, Double> groupRatio, String group) {
        Double rate = groupRatio.get(group);
        return rate == null ? BigDecimal.ONE.doubleValue() : rate;
    }

    /**
     * 构建同模型跨分组差异 JSON（各分组绝对价，基准分组的价即入库价）。
     *
     * @param groups          可用分组列表
     * @param groupRatio      分组倍率映射
     * @param inputBase       倍率口径输入价（每 1k，USD）
     * @param outputBase      倍率口径输出价（每 1k，USD）
     * @param perRequestBase  倍率口径按次价（USD/次）
     * @return 差异数组 JSON
     */
    private static String buildGroupQuotesJson(List<String> groups, Map<String, Double> groupRatio,
                                               Double inputBase, Double outputBase, Double perRequestBase) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (String group : groups) {
            double rate = groupRate(groupRatio, group);
            ObjectNode item = arr.addObject();
            item.put("group", group);
            item.put("rateMultiplier", rate);
            item.put("inputPer1k", scaleByRate(inputBase, rate));
            item.put("outputPer1k", scaleByRate(outputBase, rate));
            item.put("perRequestPrice", scaleByRate(perRequestBase, rate));
        }
        return arr.toString();
    }

    /**
     * 模型倍率折成每 1k 美元单价。
     *
     * @param ratio 模型倍率
     * @return 每 1k 单价（6 位小数，USD）
     */
    private static Double ratioPrice(Double ratio) {
        return BigDecimal.valueOf(ratio).multiply(PER_1K_PRICE_PER_RATIO)
                .setScale(SCALE_DIGITS, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 基础价乘分组倍率（缺失价保持 null）。
     *
     * @param base 基础价
     * @param rate 分组倍率
     * @return 分组价（6 位小数）；基础价缺失返回 null
     */
    private static Double scaleByRate(Double base, double rate) {
        return base == null ? null
                : BigDecimal.valueOf(base).multiply(BigDecimal.valueOf(rate))
                .setScale(SCALE_DIGITS, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 数值取值（不折算，保留原口径，如倍率 / 按次价）。
     *
     * @param node 数值节点
     * @return 数值；缺失或无法解析返回 null
     */
    private static Double decimalValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue().doubleValue();
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(text).doubleValue();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 按编码过滤模型（空 / newapi / new-api 表示全部）。
     *
     * @param models 解析结果
     * @param code   厂商 / 模型编码
     * @return 过滤后的列表
     */
    private static List<NewApiModelPrice> filter(List<NewApiModelPrice> models, String code) {
        if (code == null || code.isBlank()) {
            return models;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (PLATFORMS.contains(normalized)) {
            return models;
        }
        List<NewApiModelPrice> matched = new ArrayList<>(1);
        for (NewApiModelPrice model : models) {
            if (model.model().toLowerCase(Locale.ROOT).equals(normalized)) {
                matched.add(model);
            }
        }
        return matched;
    }

    /**
     * 写入 / 更新一条定价（按厂商 + 模型取最新一条原地更新，缺失才插入）。
     *
     * @param model 模型价格
     * @param now   生效时间
     */
    private void upsertPricing(NewApiModelPrice model, LocalDateTime now) {
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
        pricing.setCurrency("USD");
        pricing.setEffectiveDate(now);
        pricing.setSource(SOURCE);
        // new-api 不提供计费模式 / 缓存价 / 分档 / 峰值 / 官方价，相应列写空
        pricing.setBillingMode(null);
        pricing.setCacheReadPer1k(null);
        pricing.setCacheWritePer1k(null);
        pricing.setPerRequestPrice(model.perRequestPrice());
        pricing.setTiers(null);
        pricing.setPeak(null);
        pricing.setGroupName(model.groupName());
        pricing.setRateMultiplier(model.rateMultiplier());
        pricing.setGroupQuotes(model.groupQuotesJson());
        pricing.setOfficial(null);
        if (fresh) {
            pricingMapper.insert(pricing);
        } else {
            // updateById 对 null 字段不更新（MyBatis-Plus 默认 NOT_NULL 策略）；
            // 这里按主键显式全列 set，保证重复同步时与上游一致
            pricingMapper.update(null, new LambdaUpdateWrapper<SysAiPricing>()
                    .eq(SysAiPricing::getId, pricing.getId())
                    .set(SysAiPricing::getInputPricePer1k, pricing.getInputPricePer1k())
                    .set(SysAiPricing::getOutputPricePer1k, pricing.getOutputPricePer1k())
                    .set(SysAiPricing::getCurrency, pricing.getCurrency())
                    .set(SysAiPricing::getEffectiveDate, pricing.getEffectiveDate())
                    .set(SysAiPricing::getSource, pricing.getSource())
                    .set(SysAiPricing::getBillingMode, pricing.getBillingMode())
                    .set(SysAiPricing::getCacheReadPer1k, pricing.getCacheReadPer1k())
                    .set(SysAiPricing::getCacheWritePer1k, pricing.getCacheWritePer1k())
                    .set(SysAiPricing::getPerRequestPrice, pricing.getPerRequestPrice())
                    .set(SysAiPricing::getTiers, pricing.getTiers())
                    .set(SysAiPricing::getPeak, pricing.getPeak())
                    .set(SysAiPricing::getGroupName, pricing.getGroupName())
                    .set(SysAiPricing::getRateMultiplier, pricing.getRateMultiplier())
                    .set(SysAiPricing::getGroupQuotes, pricing.getGroupQuotes())
                    .set(SysAiPricing::getOfficial, pricing.getOfficial()));
        }
    }

    /**
     * 剥掉地址尾部的接口前缀，得到站点根地址。
     *
     * @param url 原始地址
     * @return 站点根地址；地址缺失时为 null
     */
    private static String stripApiPrefixes(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String text = url.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        for (String suffix : API_PREFIXES) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length());
                break;
            }
        }
        return text.isBlank() ? null : text;
    }

    /**
     * 一个模型的解析结果（价格均已按分组倍率折算，USD；按次价保留 USD/次原口径）。
     *
     * @param model           模型名
     * @param inputPer1k      输入侧每 1k 单价（基准分组）
     * @param outputPer1k     输出侧每 1k 单价（基准分组）
     * @param perRequestPrice 按次计价（USD/次，基准分组）
     * @param rateMultiplier  基准分组倍率
     * @param groupName       基准分组名
     * @param groupQuotesJson 同模型跨分组差异 JSON
     */
    private record NewApiModelPrice(String model, Double inputPer1k, Double outputPer1k,
                                    Double perRequestPrice, Double rateMultiplier, String groupName,
                                    String groupQuotesJson) {
    }
}
