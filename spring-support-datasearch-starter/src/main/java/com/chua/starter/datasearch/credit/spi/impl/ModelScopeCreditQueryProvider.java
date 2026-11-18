package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ModelScope（魔搭）余额实现。
 *
 * <p>优先走魔搭官网的用量接口（与官网「个人中心 → 用量额度」同源）：
 * {@code GET https://modelscope.cn/api/v1/inference/rate-limit}，令牌按 Bearer 放在
 * {@code Authorization} 头即可通过鉴权（实测 ms- 令牌可用），返回当日已用次数
 * （{@code Data.currentUsagePerDay}）；部分返回还会带当日限额
 * （{@code Data.requestLimitPerDay}）。该接口不消耗推理调用次数。</p>
 *
 * <p>备用走官方文档的限流头口径：{@code POST {base}/v1/chat/completions}
 * （max_tokens=1 的最小请求），从响应头 {@code modelscope-ratelimit-requests-limit /
 * -remaining} 与 {@code modelscope-ratelimit-model-requests-limit / -remaining} 读当日
 * 限流配额，单位都是「次」、按自然日重置；429 响应同样带限流头。探测本身消耗 1 次调用。
 * 2026-11 实测：推理网关在 200 响应里已不再返回这套限流头（目录里靠前的模型还可能
 * 对当前账号不可调用，回 400），所以只在官网用量接口失败时兜底尝试。</p>
 *
 * <p>读数契约：官网接口同时返回限额与已用时，parts[0]=今日剩余、parts[1]=今日已用
 * （两段之和即当日限额），页面走「剩余 / 总量」进度条；只返回已用（当前 Bearer 令牌的
 * 实测形态）时按「查到数据但没有主余额」口径返回（parts 空），读数放
 * {@code extra.current_usage_per_day}，前端按 {@code mode=usage} 单独展示「今日已用」。
 * 币种位置放单位「次」；限额、单模型配额、探测模型等放 {@code extra}
 * （官网口径 {@code mode=usage}、探测口径 {@code mode=ratelimit}）。两个口径都拿不到
 * 数时按失败返回，不把报错当余额。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi("modelscope")
public class ModelScopeCreditQueryProvider implements CreditQueryProvider {

    /**
     * 官网用量接口路径：与官网「用量额度」展示同源，Bearer 令牌即可鉴权
     */
    private static final String RATE_LIMIT_PATH = "/api/v1/inference/rate-limit";

    /**
     * 对话接口路径（OpenAI 兼容），探测口径用
     */
    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    /**
     * 模型列表路径：公开免令牌，用来挑一个可用模型发探测请求
     */
    private static final String MODELS_PATH = "/v1/models";

    /**
     * 用户当日总额度响应头
     */
    private static final String H_USER_LIMIT = "modelscope-ratelimit-requests-limit";

    /**
     * 用户当日剩余额度响应头
     */
    private static final String H_USER_REMAINING = "modelscope-ratelimit-requests-remaining";

    /**
     * 单模型当日总额度响应头
     */
    private static final String H_MODEL_LIMIT = "modelscope-ratelimit-model-requests-limit";

    /**
     * 单模型当日剩余额度响应头
     */
    private static final String H_MODEL_REMAINING = "modelscope-ratelimit-model-requests-remaining";

    /**
     * 探测模型兜底：模型列表也拉不到时用一个常见开源模型
     */
    private static final String FALLBACK_MODEL = "Qwen/Qwen2.5-7B-Instruct";

    /**
     * 单次探测最多从公开模型列表里取的候选数：目录靠前的模型可能对当前账号不可调用
     * （HTTP 400 且不带限流头），按序多试几个；上限防止单次刷新打太多请求
     */
    private static final int MAX_LIST_MODELS = 3;

    /**
     * 额度单位：魔搭限流配额按「次」计
     */
    private static final String UNIT = "次";

    /**
     * 响应 映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "modelscope";
    }

    /**
     * 查当日用量 / 限流配额：先走官网用量接口（不消耗调用次数），失败再退回
     * 对话探测读响应头（官方文档口径）。
     *
     * @param query 请求
     * @return 结果；两个口径都拿不到数时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        CreditResult primary = queryByRateLimitApi(query);
        if (primary != null && primary.isOk()) {
            return primary;
        }
        CreditResult fallback = queryByProbe(query);
        if (fallback != null && fallback.isOk()) {
            return fallback;
        }
        String primaryError = primary == null ? "未执行" : primary.getError();
        String fallbackError = fallback == null ? "未执行" : fallback.getError();
        return CreditResult.fail(name(), "官网用量接口：" + primaryError + "；探测响应头：" + fallbackError);
    }

    /**
     * 官网用量接口：GET {site}/api/v1/inference/rate-limit，Bearer 令牌鉴权，
     * 返回当日已用次数与（部分返回里的）当日限额。
     *
     * @param query 请求
     * @return 结果；接口失败或响应不可解析时 error 有值
     */
    private CreditResult queryByRateLimitApi(CreditQuery query) {
        try {
            String root = stripVersionPrefix(query.getUrl());
            String site = siteBase(query, root);
            if (site == null) {
                return CreditResult.fail(name(), "无法从上游地址推断魔搭官网域名");
            }
            CreditQuery api = new CreditQuery();
            api.setKind(query.getKind());
            api.setProviderId(query.getProviderId());
            api.setUrl(CreditHttp.join(site, RATE_LIMIT_PATH));
            api.setMethod("GET");
            api.setToken(query.getToken());
            api.setTokenHeader(query.getTokenHeader());
            if (query.getHeaders() != null) {
                for (Map.Entry<String, String> entry : query.getHeaders().entrySet()) {
                    api.header(entry.getKey(), entry.getValue());
                }
            }
            String body = CreditHttp.fetch(api, api.getUrl());
            JsonNode node = MAPPER.readTree(body);
            if (!node.path("Success").asBoolean(false)) {
                return CreditResult.fail(name(),
                        "官网用量接口返回失败：" + node.path("Message").asText("未知错误"));
            }
            JsonNode data = node.path("Data");
            JsonNode usedNode = data.path("currentUsagePerDay");
            JsonNode limitNode = data.path("requestLimitPerDay");
            BigDecimal used = usedNode.isNumber() ? usedNode.decimalValue() : null;
            BigDecimal limit = limitNode.isNumber() ? limitNode.decimalValue() : null;
            if (used == null && limit == null) {
                return CreditResult.fail(name(), "官网用量接口未返回可解析的用量字段");
            }
            CreditResult result;
            List<BalancePart> parts = new ArrayList<>(2);
            if (used != null && limit != null) {
                // 限额与已用都有：按通用契约给两段（parts[0]=剩余、parts[1]=已用，和即限额），
                // 页面走「剩余 / 总量」进度条
                BigDecimal remain = limit.subtract(used);
                parts.add(part("今日剩余", remain.signum() < 0 ? BigDecimal.ZERO : remain));
                parts.add(part("今日已用", used));
                result = CreditResult.ok(name(), parts, body);
            } else {
                // 只有单边读数（当前 Bearer 令牌实测只返回当日已用、没有限额）：
                // 按「查到数据但没有主余额」口径返回（parts 空），读数放 extra，
                // 避免聚合层把已用当成剩余摆进度条；前端按 mode=usage 单独展示
                result = CreditResult.okData(name(), body);
            }
            Map<String, Object> extra = new LinkedHashMap<>(8);
            extra.put("mode", "usage");
            extra.put("unit", UNIT);
            if (used != null) {
                extra.put("current_usage_per_day", used);
            }
            if (limit != null) {
                extra.put("request_limit_per_day", limit);
            }
            result.setExtra(extra);
            return result;
        } catch (RuntimeException | IOException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 探测口径：发一次最小对话请求，从响应头读当日限流配额。
     *
     * <p>探测模型按候选序尝试：渠道配置的模型优先，其后是公开模型列表前几个，最后兜底
     * 常量。目录里的模型不一定对当前账号开放调用（魔搭对这类请求回 400 且不带限流头），
     * 所以逐个换模型重试，直到某个响应带上限流头；401/403 属令牌问题，换模型无意义，
     * 直接失败。</p>
     *
     * @param query 请求
     * @return 结果；拿不到限流头时 error 有值
     */
    private CreditResult queryByProbe(CreditQuery query) {
        try {
            String base = stripVersionPrefix(query.getUrl());
            if (base == null) {
                return CreditResult.fail(name(), "未配置魔搭上游地址");
            }
            String url = CreditHttp.join(base, CHAT_COMPLETIONS_PATH);
            CreditResult lastFailure = null;
            for (String model : candidateModels(query, base)) {
                ClientResponse response = CreditHttp.exchange(probeQuery(query, model), url);
                if (hasRatelimitHeaders(response)) {
                    return parseProbe(response, model);
                }
                // 这个模型没带回限流头（典型：目录模型对当前账号不可调用，400）：
                // 记下失败原因换下一个候选；401/403 是令牌问题，换模型无意义
                lastFailure = parseProbe(response, model);
                int status = response.getStatusCode();
                if (status == 401 || status == 403) {
                    return lastFailure;
                }
            }
            return lastFailure != null ? lastFailure
                    : CreditResult.fail(name(), "候选模型均未返回魔搭限流头");
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 组装探测请求：POST + 最小对话体（max_tokens=1），鉴权头 / 自定义头原样带过去。
     *
     * <p>入参 {@code query} 的令牌已由调用方按 Bearer 组装好；这里只补方法与请求体，
     * 不改鉴权，避免与魔搭的实际口径错位。</p>
     *
     * @param query 原始请求
     * @param model 探测模型
     * @return 探测请求
     */
    private static CreditQuery probeQuery(CreditQuery query, String model) {
        CreditQuery probe = new CreditQuery();
        probe.setKind(query.getKind());
        probe.setProviderId(query.getProviderId());
        probe.setUrl(query.getUrl());
        probe.setOriginUrl(query.getOriginUrl());
        probe.setMethod("POST");
        probe.setToken(query.getToken());
        probe.setTokenHeader(query.getTokenHeader());
        if (query.getHeaders() != null) {
            for (Map.Entry<String, String> entry : query.getHeaders().entrySet()) {
                probe.header(entry.getKey(), entry.getValue());
            }
        }
        probe.setBody("{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\","
                + "\"content\":\"hi\"}],\"max_tokens\":1,\"stream\":false}");
        return probe;
    }

    /**
     * 组装探测模型候选：渠道显式传入（query 参数 {@code model}）优先，其后取公开模型
     * 列表（免令牌）的前 {@value #MAX_LIST_MODELS} 个，最后补兜底常量；去重、保序。
     *
     * @param query 请求
     * @param base  魔搭根地址
     * @return 候选模型列表（非空）
     */
    private static List<String> candidateModels(CreditQuery query, String base) {
        List<String> candidates = new ArrayList<>(MAX_LIST_MODELS + 2);
        Map<String, String> params = query.getParams();
        if (params != null) {
            String configured = params.get("model");
            if (configured != null && !configured.isBlank()) {
                candidates.add(configured.trim());
            }
        }
        try {
            CreditQuery anonymous = new CreditQuery();
            anonymous.setUrl(CreditHttp.join(base, MODELS_PATH));
            JsonNode node = MAPPER.readTree(CreditHttp.fetch(anonymous, anonymous.getUrl()));
            JsonNode data = node.path("data");
            int taken = 0;
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String id = item.path("id").asText("");
                    if (!id.isBlank() && !candidates.contains(id)) {
                        candidates.add(id);
                        taken++;
                    }
                    if (taken >= MAX_LIST_MODELS) {
                        break;
                    }
                }
            }
        } catch (RuntimeException | IOException ignored) {
            // 拉不到模型列表就用兜底常量，不影响主流程
        }
        if (!candidates.contains(FALLBACK_MODEL)) {
            candidates.add(FALLBACK_MODEL);
        }
        return candidates;
    }

    /**
     * 判断响应是否带 ModelScope 限流头（任一即可）。
     *
     * @param response 响应
     * @return true 表示至少带一个限流配额头，可以走正常解析
     */
    private static boolean hasRatelimitHeaders(ClientResponse response) {
        return headerValue(response, H_USER_LIMIT) != null
                || headerValue(response, H_USER_REMAINING) != null
                || headerValue(response, H_MODEL_LIMIT) != null
                || headerValue(response, H_MODEL_REMAINING) != null;
    }

    /**
     * 解析探测响应头里的当日限流配额：用户级两段读数（剩余 / 已用），限额与单模型配额放 extra。
     *
     * @param response 完整响应
     * @param model    探测模型
     * @return 结果；响应头里没有任何限流配额时按失败返回
     */
    private CreditResult parseProbe(ClientResponse response, String model) {
        BigDecimal userLimit = headerValue(response, H_USER_LIMIT);
        BigDecimal userRemaining = headerValue(response, H_USER_REMAINING);
        BigDecimal modelLimit = headerValue(response, H_MODEL_LIMIT);
        BigDecimal modelRemaining = headerValue(response, H_MODEL_REMAINING);
        if (userLimit == null && userRemaining == null && modelLimit == null && modelRemaining == null) {
            if (!response.isSuccess()) {
                return CreditResult.fail(name(), "HTTP " + response.getStatusCode()
                        + " " + snippet(response.getBodyString()));
            }
            return CreditResult.fail(name(), "响应未带 ModelScope 限流头，无法读出当日额度");
        }
        List<BalancePart> parts = new ArrayList<>(2);
        if (userRemaining != null) {
            parts.add(part("今日剩余", userRemaining));
        }
        // 已用 = 限额 − 剩余：两段之和恰好是当日限额，页面「总量 / 进度条」口径保持成立；
        // 限额本身放 extra（user_limit），与单模型配额一起展示
        if (userLimit != null && userRemaining != null) {
            BigDecimal used = userLimit.subtract(userRemaining);
            parts.add(part("今日已用", used.signum() < 0 ? BigDecimal.ZERO : used));
        } else if (userLimit != null) {
            parts.add(part("今日限额", userLimit));
        }
        // 用户级头缺失时退回单模型配额，至少给页面一段可展示的读数
        if (parts.isEmpty()) {
            if (modelRemaining != null) {
                parts.add(part("模型今日剩余", modelRemaining));
            }
            if (modelLimit != null) {
                parts.add(part("模型今日限额", modelLimit));
            }
        }
        CreditResult result = CreditResult.ok(name(), parts, response.getBodyString());
        Map<String, Object> extra = new LinkedHashMap<>(8);
        extra.put("mode", "ratelimit");
        extra.put("unit", UNIT);
        extra.put("model", model);
        if (userLimit != null) {
            extra.put("user_limit", userLimit);
        }
        if (userRemaining != null) {
            extra.put("user_remaining", userRemaining);
        }
        if (modelLimit != null) {
            extra.put("model_limit", modelLimit);
        }
        if (modelRemaining != null) {
            extra.put("model_remaining", modelRemaining);
        }
        result.setExtra(extra);
        return result;
    }

    /**
     * 推断官网用量接口的站点根地址。
     *
     * <p>渠道显式传入（query 参数 {@code site}）优先；否则从上游地址的域名推断：
     * {@code api-inference.modelscope.cn}（或任意 {@code *.modelscope.cn}）都映射到
     * {@code https://modelscope.cn}。非魔搭域名（如代理地址）推断不出，返回 null，
     * 由调用方退回探测口径。</p>
     *
     * @param query 请求
     * @param root  上游根地址（可空）
     * @return 站点根地址；推断不出时为 null
     */
    private static String siteBase(CreditQuery query, String root) {
        Map<String, String> params = query.getParams();
        if (params != null) {
            String site = params.get("site");
            if (site != null && !site.isBlank()) {
                String trimmed = site.trim();
                while (trimmed.endsWith("/")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                return trimmed;
            }
        }
        if (root == null || root.isBlank()) {
            return null;
        }
        String host;
        try {
            host = URI.create(root.trim()).getHost();
        } catch (RuntimeException e) {
            return null;
        }
        if (host == null) {
            return null;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("modelscope.cn") || lower.equals("www.modelscope.cn")
                || lower.endsWith(".modelscope.cn")) {
            return "https://modelscope.cn";
        }
        return null;
    }

    /**
     * 读一个数值型响应头（限流配额都是整数次）。
     *
     * @param response 响应
     * @param name     头名
     * @return 数值；头缺失或不是数字时为 null
     */
    private static BigDecimal headerValue(ClientResponse response, String name) {
        String value = response.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 组装一段读数（单位「次」）。
     *
     * @param label  段名
     * @param amount 次数
     * @return 读数
     */
    private static BalancePart part(String label, BigDecimal amount) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount);
        part.setCurrency(UNIT);
        return part;
    }

    /**
     * 剥掉地址尾部的 {@code /v1} 接口前缀，得到站点根地址。
     *
     * <p>供应商行里的上游地址可能是 {@code https://api-inference.modelscope.cn/v1}
     * 或不带前缀的根地址，都能推出根。</p>
     *
     * @param url 原始地址
     * @return 站点根地址；地址缺失时为 null
     */
    private static String stripVersionPrefix(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String text = url.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith("/v1")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.isBlank() ? null : text;
    }

    /**
     * 截取响应片段用于错误提示。
     *
     * @param text 响应
     * @return 片段
     */
    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200);
    }
}
