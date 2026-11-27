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
import com.chua.starter.ai.support.service.AiSub2ApiPriceSyncService;
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
 * sub2api 模型价格同步服务实现。
 *
 * <p>抓取 sub2api 站点的模型广场接口（{@value #MODEL_PLAZA_PATH}，无需密钥），解析
 * {@code data.groups[].models[]} 的 {@code pricing}（分组成员价，USD / token），
 * 折算成每 1k 单价写入 {@code sys_ai_pricing}，来源 {@value #SOURCE}。同一模型出现在
 * 多个分组时以第一个分组为准（分组倍率不同价也不同，避免后写覆盖先写）。与 DeepSeek
 * 同步不同：只写 {@code sys_ai_pricing}，不刷新 {@code sys_ai_provider_metric}。</p>
 *
 * <p>站点地址解析顺序：调用方显式指定 → 库内第一个登记了上游地址的 sub2api 供应商行
 * （走 {@link AccountChannelService} 查询契约，不直接读渠道表）。都拿不到时只记日志、
 * 不抛异常。</p>
 *
 * @since 4.0.0.60
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSub2ApiPriceSyncServiceImpl implements AiSub2ApiPriceSyncService {

    /**
     * 厂商键（写入 sys_ai_pricing.provider）
     */
    private static final String PROVIDER = "sub2api";

    /**
     * 数据来源标识
     */
    private static final String SOURCE = "sub2api-model-plaza";

    /**
     * 模型广场接口路径（无需密钥）
     */
    private static final String MODEL_PLAZA_PATH = "/api/v1/model-plaza";

    /**
     * 定价查询缓存名（与 SysAiPricingServiceImpl 的 getLatestPricing 缓存一致）
     */
    private static final String PRICING_CACHE = "aiPricing";

    /**
     * 站点根地址可能带的接口前缀（长前缀优先）
     */
    private static final String[] API_PREFIXES = {"/api/v1", "/v1", "/api"};

    /**
     * sub2api 平台名（供应商行 platform 字段，含目录别名）
     */
    private static final Set<String> PLATFORMS = Set.of("sub2api", "sub-api");

    /**
     * 抓取超时
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    /**
     * 每 token 单价折成每 1k 单价的乘数
     */
    private static final BigDecimal PER_TOKEN_TO_PER_1K = BigDecimal.valueOf(1000);

    /**
     * 读数小数位
     */
    private static final int SCALE_DIGITS = 6;

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
     * 渠道服务（读 sub2api 供应商行的上游地址，走 account 查询契约，不直接读渠道表）
     */
    private final AccountChannelService accountChannelService;

    /**
     * 按厂商编码同步模型价格。
     *
     * @param code    厂商 / 模型编码
     * @param baseUrl sub2api 站点根地址（可空）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = PRICING_CACHE, allEntries = true)
    public void syncPrices(String code, String baseUrl) {
        List<Sub2ApiModelPrice> models = filter(fetchModelPrices(baseUrl), code);
        if (models.isEmpty()) {
            log.warn("[Sub2ApiPricing] 模型广场未解析到可同步的价格（code={}），跳过", code);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Sub2ApiModelPrice model : models) {
            upsertPricing(model, now);
        }
        log.info("[Sub2ApiPricing] sub2api 模型价格同步完成，共 {} 个模型（{}）", models.size(), SOURCE);
    }

    /**
     * 用模型广场补插本地模型清单（缺失才插，保留已有），并刷新价格。
     *
     * @param code    厂商 / 模型编码
     * @param baseUrl sub2api 站点根地址（可空）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = PRICING_CACHE, allEntries = true)
    public void replaceModels(String code, String baseUrl) {
        List<Sub2ApiModelPrice> models = filter(fetchModelPrices(baseUrl), code);
        if (models.isEmpty()) {
            log.warn("[Sub2ApiPricing] 模型广场未解析到可替换的模型（code={}），跳过", code);
            return;
        }
        int inserted = 0;
        int sort = 0;
        for (Sub2ApiModelPrice model : models) {
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
        log.info("[Sub2ApiPricing] sub2api 模型清单补插完成，新增 {} 个（已有保留）", inserted);
        LocalDateTime now = LocalDateTime.now();
        for (Sub2ApiModelPrice model : models) {
            upsertPricing(model, now);
        }
        log.info("[Sub2ApiPricing] sub2api 模型价格同步完成，共 {} 个模型（{}）", models.size(), SOURCE);
    }

    /**
     * 抓取模型广场并解析各模型价格。
     *
     * @param baseUrl 站点根地址（可空，空时从供应商行解析）
     * @return 模型价格列表；抓取失败返回空列表
     */
    private List<Sub2ApiModelPrice> fetchModelPrices(String baseUrl) {
        String base = resolveBaseUrl(baseUrl);
        if (base == null) {
            log.warn("[Sub2ApiPricing] 未指定 sub2api 站点地址，且库内没有登记上游地址的 sub2api 供应商行，跳过");
            return List.of();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CreditHttp.join(base, MODEL_PLAZA_PATH)))
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
                log.warn("[Sub2ApiPricing] 模型广场抓取失败: HTTP {}", response.statusCode());
                return List.of();
            }
            return parseModelPlaza(response.body());
        } catch (Exception e) {
            log.warn("[Sub2ApiPricing] 模型广场抓取异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 sub2api 站点根地址：显式指定优先，否则取库内第一个登记了上游地址的
     * sub2api 供应商行（走 account 的查询契约，不直接读渠道表）。
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
     * 解析模型广场响应。
     *
     * <p>信封 {@code code != 0} 视为上游错误；模型价取 {@code pricing}
     * （分组成员价，USD / token），输入 / 输出 / 按次价都缺的条目跳过。
     * 除拍平单价外，同步保留分档（intervals，长文本 &gt;272K 加价 / 图像按分辨率按张）、
     * 峰值时段价、分组倍率、官方价对照与同模型跨分组差异（结构化 JSON 落列）。</p>
     *
     * @param raw 原始响应
     * @return 模型价格列表（保持分组顺序，同模型首个分组胜出，其余分组记入差异 JSON）
     */
    private static List<Sub2ApiModelPrice> parseModelPlaza(String raw) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            log.warn("[Sub2ApiPricing] 模型广场响应不是合法 JSON: {}", e.getMessage());
            return List.of();
        }
        JsonNode code = root.path("code");
        if (code.isNumber() && code.asInt() != 0) {
            log.warn("[Sub2ApiPricing] 模型广场返回错误 code={}：{}", code.asInt(), root.path("message").asText(""));
            return List.of();
        }
        JsonNode groups = root.path("data").path("groups");
        if (!groups.isArray() || groups.isEmpty()) {
            log.warn("[Sub2ApiPricing] 模型广场未返回分组模型清单");
            return List.of();
        }
        List<Sub2ApiModelPrice> models = new ArrayList<>();
        Map<String, List<Sub2ApiModelPrice>> byModel = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode group : groups) {
            JsonNode groupModels = group.path("models");
            if (!groupModels.isArray()) {
                continue;
            }
            String groupName = textOrNull(group.path("name"));
            Double rateMultiplier = doubleOrNull(group.path("rate_multiplier"));
            String peakJson = buildPeakJson(group);
            for (JsonNode entry : groupModels) {
                String name = entry.path("name").asText("").trim();
                if (name.isEmpty()) {
                    continue;
                }
                JsonNode pricing = entry.path("pricing");
                Double input = per1k(pricing.path("input_price"));
                Double output = per1k(pricing.path("output_price"));
                Double cacheRead = per1k(pricing.path("cache_read_price"));
                Double cacheWrite = per1k(pricing.path("cache_write_price"));
                Double perRequest = decimalValue(pricing.path("per_request_price"));
                String billingMode = textOrDefault(pricing.path("billing_mode"), "token");
                // 基础价全空但带分档（如图像模型按张价全在 intervals）的条目要保留
                boolean hasTiers = pricing.path("intervals").isArray() && !pricing.path("intervals").isEmpty();
                if (input == null && output == null && perRequest == null && !hasTiers) {
                    continue;
                }
                String tiersJson = buildTiersJson(pricing, billingMode, entry.path("long_context_basis"));
                String officialJson = buildOfficialJson(entry.path("official_pricing"));
                Sub2ApiModelPrice model = new Sub2ApiModelPrice(name, input, output, cacheRead, cacheWrite,
                        billingMode, perRequest, rateMultiplier, groupName, peakJson, tiersJson, officialJson, null);
                if (seen.add(name)) {
                    models.add(model);
                }
                byModel.computeIfAbsent(name, k -> new ArrayList<>()).add(model);
            }
        }
        // 分组差异：首个分组是入库基准，其余分组的同模型报价记入差异 JSON（展示用）
        for (int i = 0; i < models.size(); i++) {
            List<Sub2ApiModelPrice> variants = byModel.get(models.get(i).model());
            if (variants != null && variants.size() > 1) {
                models.set(i, models.get(i).withGroupQuotes(buildGroupQuotesJson(variants)));
            }
        }
        return models;
    }

    /**
     * 构建分档计价 JSON（上游 intervals，逐档四价 + 按次价，折算每 1k）。
     *
     * @param pricing     模型 pricing 节点
     * @param billingMode 计费模式
     * @param basis       长文本计费口径（如 whole_request=超门槛整笔按高档计）
     * @return 分档 JSON；无档位返回 null
     */
    private static String buildTiersJson(JsonNode pricing, String billingMode, JsonNode basis) {
        JsonNode intervals = pricing.path("intervals");
        if (!intervals.isArray() || intervals.isEmpty()) {
            return null;
        }
        ArrayNode tiers = MAPPER.createArrayNode();
        for (JsonNode interval : intervals) {
            ObjectNode tier = tiers.addObject();
            tier.put("label", textOrNull(interval.path("tier_label")));
            tier.put("minTokens", longOrNull(interval.path("min_tokens")));
            tier.put("maxTokens", longOrNull(interval.path("max_tokens")));
            tier.put("inputPer1k", per1k(interval.path("input_price")));
            tier.put("outputPer1k", per1k(interval.path("output_price")));
            tier.put("cacheReadPer1k", per1k(interval.path("cache_read_price")));
            tier.put("cacheWritePer1k", per1k(interval.path("cache_write_price")));
            tier.put("cacheWrite1hPer1k", per1k(interval.path("cache_write_1h_price")));
            tier.put("perRequestPrice", decimalValue(interval.path("per_request_price")));
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.put("billingMode", billingMode);
        if (basis != null && !basis.isMissingNode() && !basis.isNull()) {
            root.put("basis", basis.asText());
        }
        root.set("tiers", tiers);
        return root.toString();
    }

    /**
     * 构建峰值时段价 JSON（分组级 peak_* 字段）。
     *
     * @param group 分组节点
     * @return 峰值 JSON；未启用峰值返回 null
     */
    private static String buildPeakJson(JsonNode group) {
        if (!group.path("peak_rate_enabled").asBoolean(false)) {
            return null;
        }
        ObjectNode peak = MAPPER.createObjectNode();
        peak.put("enabled", true);
        peak.put("start", textOrNull(group.path("peak_start")));
        peak.put("end", textOrNull(group.path("peak_end")));
        peak.put("multiplier", doubleOrNull(group.path("peak_rate_multiplier")));
        return peak.toString();
    }

    /**
     * 构建官方价对照 JSON（上游 official_pricing，折算每 1k，USD）。
     *
     * @param official 官方价节点
     * @return 官方价 JSON；节点缺失返回 null
     */
    private static String buildOfficialJson(JsonNode official) {
        if (official == null || official.isMissingNode() || official.isNull() || !official.isObject()) {
            return null;
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.put("inputPer1k", per1k(official.path("input_price")));
        root.put("outputPer1k", per1k(official.path("output_price")));
        root.put("cacheReadPer1k", per1k(official.path("cache_read_price")));
        root.put("cacheWritePer1k", per1k(official.path("cache_write_price")));
        JsonNode intervals = official.path("intervals");
        if (intervals.isArray() && !intervals.isEmpty()) {
            ArrayNode tiers = MAPPER.createArrayNode();
            for (JsonNode interval : intervals) {
                ObjectNode tier = tiers.addObject();
                tier.put("label", textOrNull(interval.path("tier_label")));
                tier.put("inputPer1k", per1k(interval.path("input_price")));
                tier.put("outputPer1k", per1k(interval.path("output_price")));
                tier.put("perRequestPrice", decimalValue(interval.path("per_request_price")));
            }
            root.set("tiers", tiers);
        }
        return root.toString();
    }

    /**
     * 构建同模型跨分组差异 JSON。
     *
     * @param variants 同一模型在各分组的解析结果（含基准分组）
     * @return 差异数组 JSON
     */
    private static String buildGroupQuotesJson(List<Sub2ApiModelPrice> variants) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (Sub2ApiModelPrice variant : variants) {
            ObjectNode item = arr.addObject();
            item.put("group", variant.groupName());
            item.put("rateMultiplier", variant.rateMultiplier());
            item.put("inputPer1k", variant.inputPer1k());
            item.put("outputPer1k", variant.outputPer1k());
            item.put("perRequestPrice", variant.perRequestPrice());
        }
        return arr.toString();
    }

    /**
     * 数值取值（不折算，保留原口径，如按次价 / 倍率）。
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
     * 整数取值（档位 token 门槛）。
     *
     * @param node 数值节点
     * @return 整数；缺失返回 null
     */
    private static Long longOrNull(JsonNode node) {
        Double value = decimalValue(node);
        return value == null ? null : value.longValue();
    }

    /**
     * 浮点取值（倍率等）。
     *
     * @param node 数值节点
     * @return 浮点；缺失返回 null
     */
    private static Double doubleOrNull(JsonNode node) {
        return decimalValue(node);
    }

    /**
     * 文本取值（去空白，空串归 null）。
     *
     * @param node 文本节点
     * @return 文本；缺失或空白返回 null
     */
    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 文本取值带缺省。
     *
     * @param node        文本节点
     * @param defaultText 缺省文本
     * @return 文本；缺失或空白返回缺省
     */
    private static String textOrDefault(JsonNode node, String defaultText) {
        String text = textOrNull(node);
        return text == null ? defaultText : text;
    }

    /**
     * 每 token 单价折成每 1k 单价。
     *
     * @param perToken 每 token 单价（USD，数字或十进制字符串）
     * @return 每 1k 单价（6 位小数）；入参缺失或无法解析返回 null
     */
    private static Double per1k(JsonNode perToken) {
        if (perToken == null || perToken.isMissingNode() || perToken.isNull()) {
            return null;
        }
        BigDecimal value = perToken.isNumber() ? perToken.decimalValue() : null;
        if (value == null && perToken.isTextual()) {
            String text = perToken.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                value = new BigDecimal(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return value == null ? null
                : value.multiply(PER_TOKEN_TO_PER_1K).setScale(SCALE_DIGITS, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 按编码过滤模型（空 / sub2api 表示全部）。
     *
     * @param models 解析结果
     * @param code   厂商 / 模型编码
     * @return 过滤后的列表
     */
    private static List<Sub2ApiModelPrice> filter(List<Sub2ApiModelPrice> models, String code) {
        if (code == null || code.isBlank() || PROVIDER.equalsIgnoreCase(code.trim())) {
            return models;
        }
        String target = code.trim().toLowerCase(Locale.ROOT);
        List<Sub2ApiModelPrice> matched = new ArrayList<>(1);
        for (Sub2ApiModelPrice model : models) {
            if (model.model().toLowerCase(Locale.ROOT).equals(target)) {
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
    private void upsertPricing(Sub2ApiModelPrice model, LocalDateTime now) {
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
        pricing.setBillingMode(model.billingMode());
        pricing.setCacheReadPer1k(model.cacheReadPer1k());
        pricing.setCacheWritePer1k(model.cacheWritePer1k());
        pricing.setPerRequestPrice(model.perRequestPrice());
        pricing.setTiers(model.tiersJson());
        pricing.setPeak(model.peakJson());
        pricing.setGroupName(model.groupName());
        pricing.setRateMultiplier(model.rateMultiplier());
        pricing.setGroupQuotes(model.groupQuotesJson());
        pricing.setOfficial(model.officialJson());
        if (fresh) {
            pricingMapper.insert(pricing);
        } else {
            // updateById 对 null 字段不更新（MyBatis-Plus 默认 NOT_NULL 策略）；
            // 上游去掉档位/峰值时旧 JSON 会残留，这里按主键显式全列 set，保证与上游一致
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
     * 一个模型的解析结果（价格均已折算每 1k，USD；按次价保留 USD/次原口径）。
     *
     * @param model           模型名
     * @param inputPer1k      输入侧每 1k 单价
     * @param outputPer1k     输出侧每 1k 单价
     * @param cacheReadPer1k  缓存读每 1k 单价
     * @param cacheWritePer1k 缓存写每 1k 单价
     * @param billingMode     计费模式：token / image
     * @param perRequestPrice 按次计价基准价（USD/次）
     * @param rateMultiplier  分组倍率
     * @param groupName       来源分组名
     * @param peakJson        峰值时段价 JSON
     * @param tiersJson       分档计价 JSON
     * @param officialJson    官方价对照 JSON
     * @param groupQuotesJson 同模型跨分组差异 JSON
     */
    private record Sub2ApiModelPrice(String model, Double inputPer1k, Double outputPer1k,
                                     Double cacheReadPer1k, Double cacheWritePer1k,
                                     String billingMode, Double perRequestPrice,
                                     Double rateMultiplier, String groupName,
                                     String peakJson, String tiersJson, String officialJson,
                                     String groupQuotesJson) {

        /**
         * 附加跨分组差异 JSON（record 不可变，返回带新字段的副本）。
         *
         * @param groupQuotesJson 跨分组差异 JSON
         * @return 带差异 JSON 的新实例
         */
        private Sub2ApiModelPrice withGroupQuotes(String groupQuotesJson) {
            return new Sub2ApiModelPrice(model, inputPer1k, outputPer1k, cacheReadPer1k, cacheWritePer1k,
                    billingMode, perRequestPrice, rateMultiplier, groupName,
                    peakJson, tiersJson, officialJson, groupQuotesJson);
        }
    }
}
