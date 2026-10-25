package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMetricMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderMetric;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 厂商指标系数服务：按厂商取「一厂商一系数」的兜底指标。
 *
 * <p>厂商系数是计费取价的<b>最末一层兜底</b>：AA 缓存、{@code sys_ai_pricing}、
 * 全量模型指标库（{@code sys_ai_model_metrics}）都查不到模型级价格时，按厂商键
 * 取本表系数做预估。系数由 {@link #syncProviderMetrics()} 从 Models.dev 原始目录
 * 聚合生成：输入/输出价为该厂商全部模型单价的中位数（USD / 百万 Token），
 * 上下文为中位数窗口，模型数为收录模型总数。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiProviderMetricService {

    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "aiProviderMetric";

    /**
     * 数据源标识
     */
    public static final String SOURCE = "models.dev";

    /**
     * Models.dev 公开目录端点（无需 Key）
     */
    private static final String CATALOG_URL = "https://models.dev/api.json";

    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    private static final ObjectMapper MAPPER = new ObjectMapper(); // 映射器

    /**
     * 厂商指标系数 映射器
     */
    private final SysAiProviderMetricMapper providerMetricMapper;

    /**
     * 按厂商键查询系数（第 4 步兜底）。
     *
     * <p>厂商键小写化后精确匹配；未查询到返回 空，由上层结束兜底链。</p>
     *
     * @param provider 厂商键，如 openai、deepseek
     * @return 厂商系数记录，未找到返回 空
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "#provider == null ? '' : #provider.toLowerCase()",
            unless = "#result == null")
    public SysAiProviderMetric findForEstimate(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        String providerKey = normalizeProvider(provider);
        if (providerKey == null) {
            return null;
        }
        return providerMetricMapper.selectOne(new LambdaQueryWrapper<SysAiProviderMetric>()
                .eq(SysAiProviderMetric::getProvider, providerKey)
                .last("limit 1"));
    }

    /**
     * 业务 provider 标识 → Models.dev 厂商键。
     *
     * <p>前端模板 / 配置使用的 provider id（如 {@code qwen}、{@code together}、
     * {@code moonshot}）与 Models.dev 目录的厂商键（如 {@code alibaba}、
     * {@code togetherai}、{@code moonshotai}）并不总一致，这里做归一化；
     * 归一化后查不到返回 null（结束兜底链）。</p>
     *
     * @param provider 业务 provider 标识
     * @return Models.dev 厂商键，无法归一化时返回 null
     */
    private static String normalizeProvider(String provider) {
        String id = provider.trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "openai", "openai-response", "custom", "github", "gitee", "dots" -> "openai";
            case "anthropic", "claude" -> "anthropic";
            case "google", "gemini", "google-ai-studio" -> "google";
            case "deepseek" -> "deepseek";
            case "qwen", "dashscope", "tongyi", "bailian" -> "alibaba";
            case "zhipu", "glm", "zhipuai", "chatglm" -> "zhipuai";
            case "moonshot", "kimi", "moonshotai" -> "moonshotai";
            case "minimax", "minimaxi" -> "minimax";
            case "stepfun", "step" -> "stepfun";
            case "groq" -> "groq";
            case "xai", "grok" -> "xai";
            case "mistral" -> "mistral";
            case "cerebras" -> "cerebras";
            case "cohere" -> "cohere";
            case "perplexity" -> "perplexity";
            case "huggingface", "hf" -> "huggingface";
            case "nvidia", "nvidia-nim", "nim" -> "nvidia";
            case "siliconflow", "silicon" -> "siliconflow";
            case "sensenova", "sensetime" -> "sensenova";
            case "openrouter", "router" -> "openrouter";
            case "commandcode" -> "commandcode";
            case "opencode", "zen" -> "opencode";
            case "agnes", "apihub" -> "agnes";
            case "volcengine", "doubao", "ark" -> "volcengine";
            case "azure", "microsoft", "azure-openai" -> "azure";
            case "modelscope", "damo" -> "modelscope";
            case "zai" -> "zai";
            case "together", "together-ai" -> "togetherai";
            case "poe" -> "poe";
            case "longcat" -> "longcat";
            case "kilo" -> "kilo";
            case "amazon", "aws-bedrock", "bedrock" -> "amazon-bedrock";
            case "vercel" -> "vercel";
            case "ai302", "302" -> "302ai";
            case "qiniu" -> "qiniu-ai";
            case "ollama", "onnx" -> null;
            default -> id;
        };
    }

    /**
     * 全量同步厂商系数：Models.dev 目录 → 厂商级中位数聚合 → 覆盖写。
     *
     * <p>先清空旧数据再全量插入（与官方定价同步同范式）；目录不可达或解析失败时
     * 不打乱既有数据并返回 0。</p>
     *
     * @return 入库厂商数；未同步返回 0
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public int syncProviderMetrics() {
        JsonNode root = fetchCatalog();
        if (root == null || !root.isObject()) {
            log.warn("[ProviderMetric] Models.dev 目录不可达或结构异常，跳过厂商系数同步");
            return 0;
        }
        List<SysAiProviderMetric> rows = new ArrayList<>(256);
        LocalDateTime now = LocalDateTime.now();
        Iterator<java.util.Map.Entry<String, JsonNode>> providers = root.fields();
        while (providers.hasNext()) {
            var entry = providers.next();
            String providerId = entry.getKey();
            JsonNode providerNode = entry.getValue();
            if (providerNode == null || !providerNode.isObject()) {
                continue;
            }
            SysAiProviderMetric metric = aggregate(providerId, providerNode, now);
            if (metric != null) {
                rows.add(metric);
            }
        }
        if (rows.isEmpty()) {
            return 0;
        }
        int deleted = providerMetricMapper.delete(null);
        for (SysAiProviderMetric row : rows) {
            providerMetricMapper.insert(row);
        }
        log.info("[ProviderMetric] 厂商系数同步完成，清空 {} 条旧数据，入库 {} 家厂商（{}/{}）",
                deleted, rows.size(), SOURCE, CATALOG_URL);
        return rows.size();
    }

    /**
     * 抓取 Models.dev 目录 JSON。
     *
     * @return 目录根节点；不可达返回 null
     */
    private JsonNode fetchCatalog() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CATALOG_URL))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            log.warn("[ProviderMetric] Models.dev 目录抓取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 聚合单个厂商的系数：中位数单价/上下文 + 厂商名 + 模型数。
     *
     * @param providerId   厂商键
     * @param providerNode 厂商节点
     * @param now          入库时间
     * @return 系数记录
     */
    private SysAiProviderMetric aggregate(String providerId, JsonNode providerNode, LocalDateTime now) {
        SysAiProviderMetric metric = new SysAiProviderMetric();
        metric.setProvider(providerId.toLowerCase(Locale.ROOT));
        metric.setProviderName(textOf(providerNode, "name"));
        metric.setSource(SOURCE);
        metric.setCreateTime(now);
        metric.setUpdateTime(now);

        JsonNode models = providerNode.path("models");
        if (!models.isObject()) {
            return null;
        }
        List<BigDecimal> inputs = new ArrayList<>();
        List<BigDecimal> outputs = new ArrayList<>();
        List<BigDecimal> contexts = new ArrayList<>();
        int modelCount = 0;
        Iterator<JsonNode> modelIter = models.elements();
        while (modelIter.hasNext()) {
            JsonNode model = modelIter.next();
            if (model == null || !model.isObject()) {
                continue;
            }
            modelCount++;
            JsonNode cost = model.path("cost");
            if (cost.isObject()) {
                BigDecimal input = decimalOf(cost, "input");
                if (input != null && input.signum() >= 0) {
                    inputs.add(input);
                }
                BigDecimal output = decimalOf(cost, "output");
                if (output != null && output.signum() >= 0) {
                    outputs.add(output);
                }
            }
            JsonNode limit = model.path("limit");
            if (limit.isObject()) {
                BigDecimal context = decimalOf(limit, "context");
                if (context != null && context.signum() > 0) {
                    contexts.add(context);
                }
            }
        }
        metric.setModelCount(modelCount);
        metric.setPriceInputPerMusd(median(inputs));
        metric.setPriceOutputPerMusd(median(outputs));
        if (!contexts.isEmpty()) {
            metric.setContextWindowTokens(median(contexts).longValue());
        }
        return metric;
    }

    /**
     * 计算列表的中位数（尾数取整到 6 位，避免过长小数）。
     *
     * @param values 数值列表
     * @return 中位数；列表为空返回 null
     */
    private static BigDecimal median(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(java.util.Comparator.naturalOrder());
        int n = sorted.size();
        BigDecimal result;
        if ((n & 1) == 1) {
            result = sorted.get(n / 2);
        } else {
            result = sorted.get(n / 2 - 1)
                    .add(sorted.get(n / 2))
                    .divide(BigDecimal.valueOf(2), 6, java.math.RoundingMode.HALF_UP);
        }
        return result.setScale(6, java.math.RoundingMode.HALF_UP);
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isEmpty() ? null : text;
    }

    private static BigDecimal decimalOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.decimalValue();
    }
}