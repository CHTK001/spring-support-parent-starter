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

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenCode（Go 套餐 / Zen 按量）余额聚合实现。
 *
 * <p>两个端点都来自官方源码（anomalyco/opencode {@code packages/core/src/usage-limits.ts}、
 * {@code console-access.ts}），同一把 API Key、标准 Bearer 鉴权：</p>
 * <ul>
 *   <li>Go 套餐用量：{@code GET https://opencode.ai/zen/go/v1/usage}；200 返回
 *       {@code {usage:{rolling|weekly|monthly:{status, percent, resetsAt}}}}，{@code percent}
 *       是「已用百分比」，这里翻成「剩余百分比」展示；<b>403 EntitlementError = 该 Key 无
 *       Go 订阅</b>（官方逻辑：403 视为无 Go，不算致命错误）。</li>
 *   <li>预算：{@code GET https://opencode.ai/console/api/policies/current-budgets}；200 返回
 *       {@code [{ruleID, limitDollars, spentDollars, scope, per, shared, resetsAt}]} 数组；
 *       <b>403 = 服务账号 Key 或 Key 无 Budgets:Read 权限</b>（官方注释原话）。官方还会带
 *       {@code x-org-id} / {@code x-opencode-org-id} 请求头；Key 本身绑定单一工作区，这里不传。</li>
 * </ul>
 *
 * <p>Zen 按量计费的现金余额<b>官方没有提供任何 API</b>（仓库无端点、文档明确只在控制台网页
 * 展示，upstream #10448 open），这里如实注明「不可查」，不编造读数。两端点都没查到数时整体
 * 失败，把各自原因（403 的两种语义分开说明）拼进错误信息。Go 用量与预算互相独立：一边失败
 * 不影响另一边的读数，失败原因进 {@code extra.notes}。</p>
 *
 * <p>显式配置 {@code credit_url} 时覆盖 Go 用量端点；预算端点取其同源下的固定路径
 * {@code /console/api/policies/current-budgets}（默认与用量端点同为 opencode.ai）。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Spi("opencode")
public class OpenCodeCreditQueryProvider implements CreditQueryProvider {

    /**
     * 实现标识
     */
    static final String NAME = "opencode";

    /**
     * Go 套餐官方用量端点（Zen 渠道复用，Key 同一把）
     */
    private static final String DEFAULT_USAGE_URL = "https://opencode.ai/zen/go/v1/usage";

    /**
     * 预算端点路径（与用量端点同源）
     */
    private static final String BUDGETS_PATH = "/console/api/policies/current-budgets";

    /**
     * 预算端点默认完整地址
     */
    private static final String DEFAULT_BUDGETS_URL = "https://opencode.ai" + BUDGETS_PATH;

    /**
     * Go 套餐的三个用量窗口：字段名 → 展示名（顺序即展示顺序）
     */
    private static final List<String[]> WINDOWS = List.of(
            new String[]{"rolling", "滚动"},
            new String[]{"weekly", "本周"},
            new String[]{"monthly", "本月"});

    /**
     * 错误片段截取长度
     */
    private static final int SNIPPET_LIMIT = 200;

    /**
     * 响应映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 聚合查询：先查 Go 用量，再查预算，两边都查不到数才失败。
     *
     * <p>鉴权为标准 Bearer：调用方把 API Key 原值放进 token 字段，请求出口统一补
     * {@code Bearer } 前缀。403 的两种语义（无 Go 订阅 / 无 Budgets:Read 权限）分别
     * 转成可读提示，不当成神秘错误。</p>
     *
     * @param query 请求
     * @return 结果；两端点都没查到数时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            if (blank(query.getToken())) {
                return CreditResult.fail(NAME, "缺少 API Key（OpenCode 用 API Key Bearer 鉴权）");
            }
            String usageUrl = blank(query.getUrl()) ? DEFAULT_USAGE_URL : query.getUrl().trim();
            String budgetsUrl = budgetsUrlOf(usageUrl);
            List<BalancePart> parts = new ArrayList<>();
            Map<String, Object> extra = new LinkedHashMap<>();
            List<String> notes = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            String raw = null;

            // 1) Go 套餐用量（403 EntitlementError = 无 Go 订阅，属预期状态，不算失败）
            ClientResponse usageResponse = CreditHttp.exchange(query, usageUrl);
            int usageStatus = usageResponse.getStatusCode();
            String usageBody = usageResponse.getBodyString();
            if (usageStatus == 200) {
                raw = usageBody;
                parseUsage(usageBody, parts, extra, errors);
            } else if (usageStatus == 403) {
                notes.add("Go 套餐用量不可查：该 Key 无 Go 订阅（403 EntitlementError，属预期）");
            } else {
                errors.add("Go 用量：HTTP " + usageStatus + " " + snippet(usageBody));
            }

            // 2) 预算（403 = 服务账号 Key 或无 Budgets:Read 权限，同样属权限状态，不算失败）
            ClientResponse budgetsResponse = CreditHttp.exchange(query, budgetsUrl);
            int budgetsStatus = budgetsResponse.getStatusCode();
            String budgetsBody = budgetsResponse.getBodyString();
            if (budgetsStatus == 200) {
                if (raw == null) {
                    raw = budgetsBody;
                }
                parseBudgets(budgetsBody, parts, extra, errors);
            } else if (budgetsStatus == 403) {
                notes.add("预算不可查：Key 无 Budgets:Read 权限或为服务账号 Key（403）");
            } else {
                errors.add("预算：HTTP " + budgetsStatus + " " + snippet(budgetsBody));
            }

            // 3) Zen 按量余额：官方无 API，如实注明，不编造
            notes.add("Zen 按量余额官方未提供 API（upstream #10448），仅控制台网页可见");
            notes.addAll(errors);
            extra.put("notes", notes);

            if (parts.isEmpty()) {
                if (errors.isEmpty()) {
                    // 两个端点都是「预期内的不可查」（403）：如实说明，不编造读数
                    List<String> reasons = new ArrayList<>(2);
                    if (usageStatus == 403) {
                        reasons.add("Go 用量 403（无 Go 订阅）");
                    }
                    if (budgetsStatus == 403) {
                        reasons.add("预算 403（无 Budgets:Read 权限）");
                    }
                    return CreditResult.fail(NAME, "没有可读的余额口径：" + String.join("；", reasons)
                            + "；Zen 按量余额官方无 API，仅控制台网页可见");
                }
                return CreditResult.fail(NAME, String.join("；", errors));
            }
            CreditResult result = CreditResult.ok(NAME, parts, raw);
            result.setExtra(extra);
            return result;
        } catch (RuntimeException e) {
            return CreditResult.fail(NAME, e.getMessage());
        }
    }

    /**
     * 解析 Go 套餐用量响应：三个窗口的「已用百分比」翻成「剩余百分比」。
     *
     * <p>窗口缺失或百分比不可读时跳过该窗口；一个都读不出来时把原因写进 errors。
     * 窗口明细（含重置时间）放 {@code extra.go_usage}。</p>
     *
     * @param body   原始响应
     * @param parts  读数出口
     * @param extra  附加数据出口
     * @param errors 错误出口
     */
    void parseUsage(String body, List<BalancePart> parts,
                    Map<String, Object> extra, List<String> errors) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception e) {
            errors.add("Go 用量响应不是合法 JSON: " + e.getMessage());
            return;
        }
        JsonNode usage = root.path("usage");
        if (!usage.isObject()) {
            errors.add("Go 用量响应缺少 usage 对象");
            return;
        }
        List<Map<String, Object>> windows = new ArrayList<>(WINDOWS.size());
        for (String[] window : WINDOWS) {
            JsonNode node = usage.path(window[0]);
            if (!node.isObject() || !node.hasNonNull("percent")) {
                continue;
            }
            BigDecimal percent = decimalOf(node.path("percent"));
            if (percent == null) {
                continue;
            }
            BigDecimal remaining = BigDecimal.valueOf(100).subtract(percent);
            parts.add(part("Go 剩余（" + window[1] + "）", remaining, "%"));
            Map<String, Object> detail = new LinkedHashMap<>(6);
            detail.put("window", window[0]);
            detail.put("used_percent", percent);
            detail.put("remaining_percent", remaining);
            detail.put("status", node.path("status").asText(null));
            detail.put("resets_at", node.path("resetsAt").asText(null));
            windows.add(detail);
        }
        if (windows.isEmpty()) {
            errors.add("Go 用量响应里没有可读的窗口百分比");
            return;
        }
        extra.put("go_usage", windows);
    }

    /**
     * 解析预算响应：逐条预算算「上限 − 已花 = 剩余」，美元口径。
     *
     * <p>没有金额上限（{@code limitDollars} 缺失）的预算读不出「剩余」，跳过不编造；
     * 预算明细（scope / per / shared / 重置时间）放 {@code extra.budgets}。</p>
     *
     * @param body   原始响应
     * @param parts  读数出口
     * @param extra  附加数据出口
     * @param errors 错误出口
     */
    void parseBudgets(String body, List<BalancePart> parts,
                      Map<String, Object> extra, List<String> errors) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception e) {
            errors.add("预算响应不是合法 JSON: " + e.getMessage());
            return;
        }
        if (!root.isArray()) {
            errors.add("预算响应不是数组");
            return;
        }
        List<Map<String, Object>> budgets = new ArrayList<>(root.size());
        for (JsonNode node : root) {
            JsonNode limitNode = node.path("limitDollars");
            if (!limitNode.isNumber()) {
                continue;
            }
            BigDecimal limit = decimalOf(limitNode);
            JsonNode spentNode = node.path("spentDollars");
            BigDecimal spent = spentNode.isNumber() ? decimalOf(spentNode) : BigDecimal.ZERO;
            if (limit == null || spent == null) {
                continue;
            }
            BigDecimal remaining = limit.subtract(spent);
            String ruleId = node.path("ruleID").asText("");
            parts.add(part("预算剩余（" + ruleId + "）", remaining, "USD"));
            Map<String, Object> detail = new LinkedHashMap<>(8);
            detail.put("rule_id", ruleId);
            detail.put("limit", limit);
            detail.put("spent", spent);
            detail.put("remaining", remaining);
            detail.put("scope", node.path("scope").asText(null));
            detail.put("per", node.path("per").asText(null));
            detail.put("shared", node.path("shared").asBoolean(false));
            detail.put("resets_at", node.path("resetsAt").asText(null));
            budgets.add(detail);
        }
        if (budgets.isEmpty()) {
            errors.add("预算响应里没有可读的美元预算");
            return;
        }
        extra.put("budgets", budgets);
    }

    /**
     * 预算端点地址：取用量端点的同源（协议 + 域名 + 端口）拼固定路径。
     *
     * <p>显式配置 {@code credit_url} 指向自建代理 / 中转时，预算端点跟着走同源，
     * 两边一起被覆盖；解析失败回落官方默认地址。</p>
     *
     * @param usageUrl 用量端点地址
     * @return 预算端点地址
     */
    static String budgetsUrlOf(String usageUrl) {
        try {
            URI uri = URI.create(usageUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return DEFAULT_BUDGETS_URL;
            }
            StringBuilder origin = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() != -1) {
                origin.append(':').append(uri.getPort());
            }
            return origin.append(BUDGETS_PATH).toString();
        } catch (RuntimeException e) {
            return DEFAULT_BUDGETS_URL;
        }
    }

    /**
     * 组一段读数。
     *
     * @param label    段名
     * @param amount   金额
     * @param currency 币种或单位
     * @return 读数
     */
    private static BalancePart part(String label, BigDecimal amount, String currency) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount);
        part.setCurrency(currency);
        return part;
    }

    /**
     * 数字节点 → BigDecimal（数字节点的 asText 是无损失的十进制串）。
     *
     * @param node 数字节点
     * @return 数值；不是合法数字为 null
     */
    private static BigDecimal decimalOf(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            return new BigDecimal(node.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 截取响应片段用于错误提示。
     *
     * @param text 响应
     * @return 单行片段
     */
    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= SNIPPET_LIMIT ? oneLine : oneLine.substring(0, SNIPPET_LIMIT);
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
