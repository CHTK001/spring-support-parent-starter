// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        if (rows == null || rows.isEmpty()) {
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
     * （分组成员价，USD / token），输入 / 输出价都缺的条目跳过。</p>
     *
     * @param raw 原始响应
     * @return 模型价格列表（保持分组顺序，同模型首个分组胜出）
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
        Set<String> seen = new HashSet<>();
        for (JsonNode group : groups) {
            JsonNode groupModels = group.path("models");
            if (!groupModels.isArray()) {
                continue;
            }
            for (JsonNode entry : groupModels) {
                String name = entry.path("name").asText("").trim();
                if (name.isEmpty() || !seen.add(name)) {
                    continue;
                }
                JsonNode pricing = entry.path("pricing");
                Double input = per1k(pricing.path("input_price"));
                Double output = per1k(pricing.path("output_price"));
                if (input == null && output == null) {
                    continue;
                }
                models.add(new Sub2ApiModelPrice(name, input, output));
            }
        }
        return models;
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
        if (fresh) {
            pricingMapper.insert(pricing);
        } else {
            pricingMapper.updateById(pricing);
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
     * 一个模型的解析结果。
     *
     * @param model       模型名
     * @param inputPer1k  输入侧每 1k 单价（USD）
     * @param outputPer1k 输出侧每 1k 单价（USD）
     */
    private record Sub2ApiModelPrice(String model, Double inputPer1k, Double outputPer1k) {
    }
}
