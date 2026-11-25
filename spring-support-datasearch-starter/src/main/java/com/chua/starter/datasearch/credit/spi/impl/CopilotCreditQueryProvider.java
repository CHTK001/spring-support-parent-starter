package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub Copilot 配额采样实现（copilot_internal 令牌接口）。
 *
 * <p>对接 {@code GET https://api.github.com/copilot_internal/v2/token}：用设备码
 * 授权流程换来的 GitHub OAuth 令牌（ghu_ 前缀）作 Bearer 鉴权，响应里同时携带
 * 短时效 Copilot 令牌与当前计费周期的配额读数。本实现只消费配额部分
 * （{@code limited_user_quotas}），短时效令牌由 ai-starter 的令牌管理器单独
 * 续期，这里不落地、不转发。</p>
 *
 * <p>{@code limited_user_quotas} 为「配额项 → 剩余量」映射（2026-11 实测形如
 * {@code {"chat":0,"completions":1924}}），只含剩余、不含上限——上限只出现在
 * 429 响应头里，为取上限而多发请求会白烧配额，这里明确不做。解析同时兼容
 * 嵌套形态（值是含 limit / used / remaining 的对象）。读数单位为「次」
 * （按请求次数计的配额，不是货币金额）。</p>
 *
 * <p>上游对客户端身份有校验：请求须带 Copilot 客户端 User-Agent 与
 * Editor-Version 头（调用方未配时本实现补齐），否则可能被拒。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Spi("copilot")
public class CopilotCreditQueryProvider implements CreditQueryProvider {

    /**
     * 配额接口默认地址
     */
    private static final String DEFAULT_URL = "https://api.github.com/copilot_internal/v2/token";

    /**
     * Copilot 客户端 User-Agent（上游按客户端身份校验）
     */
    private static final String COPILOT_USER_AGENT = "GitHubCopilotChat/0.30.0";

    /**
     * Editor-Version 请求头（与 User-Agent 配套的客户端标识）
     */
    private static final String EDITOR_VERSION = "vscode/1.99.0";

    /**
     * 读数单位：请求次数（不是货币金额）
     */
    private static final String QUOTA_UNIT = "次";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "copilot";
    }

    /**
     * 按 Copilot 口径查询配额。
     *
     * <p>鉴权走标准 Bearer（ghu_ OAuth 令牌，由 {@code CreditHttp} 自动补头）。
     * 显式配了表达式时以表达式为准，否则按 {@code limited_user_quotas} 逐项读数。</p>
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        if (query.getToken() == null || query.getToken().isBlank()) {
            return CreditResult.fail(name(), "缺少 GitHub OAuth 令牌（ghu_）：请先完成设备码授权");
        }
        Map<String, String> headers = query.getHeaders() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(query.getHeaders());
        headers.putIfAbsent("User-Agent", COPILOT_USER_AGENT);
        headers.putIfAbsent("Editor-Version", EDITOR_VERSION);
        query.setHeaders(headers);
        try {
            String url = blank(query.getUrl()) ? DEFAULT_URL : query.getUrl().trim();
            String raw = CreditHttp.fetch(query, url);
            if (!blank(query.getExpression())) {
                CreditResult parsed = CreditExpressions.parse(query.getExpression(), raw, name(), query.getBilling());
                if (parsed.isOk()) {
                    parsed.setSource(name());
                }
                return parsed;
            }
            return parseQuotas(raw);
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), friendlyError(e.getMessage()));
        }
    }

    /**
     * 把典型失败翻译成可操作的提示。
     *
     * <p>Copilot 的配额接口只认设备码授权换来的 ghu_ OAuth 令牌：令牌过期 / 被撤销
     * 时返回 401，这不是 Key 填错，而是要重新走一遍设备码授权。</p>
     *
     * @param message 原始异常消息
     * @return 可读的错误提示
     */
    private static String friendlyError(String message) {
        if (message == null) {
            return null;
        }
        if (message.contains("401")) {
            return "GitHub OAuth 令牌无效或已过期：请重新发起设备码授权，换取新的 ghu_ 令牌";
        }
        if (message.contains("403")) {
            return "GitHub 拒绝访问：令牌权限不足、账号未开通 Copilot 或触发限流";
        }
        if (message.contains("404")) {
            return "配额接口地址不可用（HTTP 404）：请检查余额地址配置";
        }
        return message;
    }

    /**
     * 按 {@code limited_user_quotas} 结构解析配额。
     *
     * <p>除扁平读数 {@code parts} 外，把套餐（sku）、重置时间、短时效令牌过期时间
     * 与逐项配额放进 {@link CreditResult#getExtra()}（mode / plan / reset_at /
     * expires_at / refresh_in / unit / quotas），前端据此按 Copilot 的配额口径渲染
     * 专属余额视图；{@code raw} 有截断，结构化数据以 extra 为准。包可见以便单测
     * 直接喂样例、不走网络。</p>
     *
     * @param raw 原始响应
     * @return 结果
     */
    static CreditResult parseQuotas(String raw) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            return CreditResult.fail("copilot", "响应不是合法 JSON: " + e.getMessage());
        }
        JsonNode quotas = root.path("limited_user_quotas");
        if (!quotas.isObject() || quotas.isEmpty()) {
            return CreditResult.fail("copilot", "响应里没有 limited_user_quotas 配额数据，请改用「自定义映射」指定字段");
        }
        List<BalancePart> parts = new ArrayList<>();
        List<Map<String, Object>> quotaViews = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = quotas.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode node = field.getValue();
            JsonNode remainingNode = node.isObject() ? node.path("remaining") : node;
            BigDecimal remaining = decimalOf(remainingNode);
            if (remaining == null) {
                continue;
            }
            BalancePart part = new BalancePart();
            part.setLabel(quotaLabel(key));
            part.setAmount(remaining);
            part.setCurrency(QUOTA_UNIT);
            parts.add(part);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("key", key);
            view.put("label", quotaLabel(key));
            view.put("remaining", remaining);
            if (node.isObject()) {
                BigDecimal used = decimalOf(node.path("used"));
                BigDecimal limit = decimalOf(node.path("limit"));
                if (used != null) {
                    view.put("used", used);
                }
                if (limit != null) {
                    view.put("limit", limit);
                }
            }
            quotaViews.add(view);
        }
        if (parts.isEmpty()) {
            return CreditResult.fail("copilot", "响应里没有可读的配额数值");
        }
        CreditResult result = CreditResult.ok("copilot", parts, raw);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("mode", "copilot");
        String sku = root.path("sku").asText("");
        if (!sku.isEmpty()) {
            extra.put("plan", sku);
        }
        Long resetAt = longOf(root.path("limited_user_reset_date"));
        if (resetAt != null) {
            extra.put("reset_at", resetAt);
        }
        Long expiresAt = longOf(root.path("expires_at"));
        if (expiresAt != null) {
            extra.put("expires_at", expiresAt);
        }
        Long refreshIn = longOf(root.path("refresh_in"));
        if (refreshIn != null) {
            extra.put("refresh_in", refreshIn);
        }
        extra.put("unit", QUOTA_UNIT);
        extra.put("quotas", quotaViews);
        result.setExtra(extra);
        return result;
    }

    /**
     * 配额项展示名：已知键给中文名，未知键原样展示。
     *
     * @param key 配额键（chat / completions / premium_requests 等）
     * @return 展示名
     */
    private static String quotaLabel(String key) {
        return switch (key) {
            case "chat" -> "Chat 对话";
            case "completions" -> "Completions 补全";
            case "premium_requests" -> "Premium 请求";
            default -> key;
        };
    }

    /**
     * 取节点数值；上游把配额数编码成数值或字符串，这里统一转成数值。
     *
     * @param node 节点
     * @return 数值；无法解析时返回空
     */
    private static BigDecimal decimalOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (!node.isTextual()) {
            return null;
        }
        String text = node.asText().trim().replace(",", "");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取节点长整数（ epoch 秒类字段）。
     *
     * @param node 节点
     * @return 长整数；无法解析时返回空
     */
    private static Long longOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.canConvertToLong() ? node.longValue() : null;
        }
        if (!node.isTextual()) {
            return null;
        }
        try {
            return Long.parseLong(node.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 空判断。
     *
     * @param text 文本
     * @return 为空为 true
     */
    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
