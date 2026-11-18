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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * xAI 官方预付余额实现。
 *
 * <p>xAI 的余额在<b>管理 API</b>（base {@code https://management-api.x.ai}）：
 * {@code GET /v1/billing/teams/{team_id}/prepaid/balance}，要用控制台单独创建的
 * <b>管理密钥</b>（Management Key，需 Billing 读取权限），推理 API Key 查不了。
 * team-scoped 管理密钥的 {@code scopeId} 就是 team_id；org-scoped 密钥需要单独提供
 * team_id。team_id 可预先嵌入余额地址，也可留空由
 * {@code GET /auth/management-keys/validation}（校验当前管理密钥）宽容解析自动发现。</p>
 *
 * <p>响应里 {@code total.val} 为<b>美元美分字符串，且符号取反</b>：负数（如
 * {@code -2433}）表示剩余 24.33 美元；非负表示预付额度已用尽（剩余按 0 展示，原始值进
 * extra）。表达式引擎无一元减号，取反逻辑在这里用代码完成。币种 USD。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Spi("xai")
public class XaiCreditQueryProvider implements CreditQueryProvider {

    /**
     * 实现标识
     */
    static final String NAME = "xai";

    /**
     * 预付余额端点模板（{team_id} 由配置或自动发现补齐）
     */
    private static final String BALANCE_URL_TEMPLATE =
            "https://management-api.x.ai/v1/billing/teams/{team_id}/prepaid/balance";

    /**
     * 管理密钥校验端点（自动发现 team_id 用）
     */
    private static final String VALIDATION_URL = "https://management-api.x.ai/auth/management-keys/validation";

    /**
     * team_id 占位符
     */
    private static final String TEAM_PLACEHOLDER = "{team_id}";

    /**
     * 币种：美元
     */
    private static final String CURRENCY = "USD";

    /**
     * 金额单位换算：total.val 单位为美分（1/100 美元）
     */
    private static final int SCALE = 2;

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
     * 查 xAI 预付余额。
     *
     * <p>鉴权为管理密钥的 Bearer；team_id 按余额地址的三种形态解析（完整地址 / 裸
     * team_id / 占位符），都没给时经管理密钥校验端点自动发现。401 / 403 分开提示。</p>
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            if (blank(query.getToken())) {
                return CreditResult.fail(NAME, "缺少 xAI 管理密钥（Management Key）:余额在 Management API，"
                        + "推理 API Key 查不了；请在 xAI Console -> Settings -> Management Keys 创建"
                        + "（需 Billing 读取权限）并填入「余额密钥」");
            }
            String configured = blank(query.getUrl()) ? null : query.getUrl().trim();
            if (configured != null && configured.contains(TEAM_PLACEHOLDER)) {
                String teamId = resolveTeamId(query);
                if (teamId == null) {
                    return CreditResult.fail(NAME, "余额地址含 {team_id} 占位符，"
                            + "但未能从管理密钥校验接口自动发现 team_id");
                }
                return fetchBalance(query, configured.replace(TEAM_PLACEHOLDER, teamId), teamId);
            }
            if (configured != null && configured.contains("/prepaid/balance")) {
                // 完整余额地址（team_id 已嵌入）：原样使用
                return fetchBalance(query, configured, teamIdOfPath(configured));
            }
            if (configured != null) {
                // 裸 team_id：按默认端点模板补齐
                return fetchBalance(query, BALANCE_URL_TEMPLATE.replace(TEAM_PLACEHOLDER, configured), configured);
            }
            String teamId = resolveTeamId(query);
            if (teamId == null) {
                return CreditResult.fail(NAME, "未能从管理密钥校验接口自动发现 team_id："
                        + "org-scoped 管理密钥需把 team_id 或完整余额地址填入「余额地址」");
            }
            return fetchBalance(query, BALANCE_URL_TEMPLATE.replace(TEAM_PLACEHOLDER, teamId), teamId);
        } catch (RuntimeException | IOException e) {
            return CreditResult.fail(NAME, e.getMessage());
        }
    }

    /**
     * 自动发现 team_id：GET /auth/management-keys/validation 校验当前管理密钥，
     * 响应里的 scope / team 字段名做了宽容解析。401 / 403 分开提示。
     *
     * @param query 请求（已带 Bearer 管理密钥）
     * @return team_id；发现不了为 null
     * @throws IOException 响应不可读
     */
    private String resolveTeamId(CreditQuery query) throws IOException {
        ClientResponse response = CreditHttp.exchange(query, VALIDATION_URL);
        int status = response.getStatusCode();
        String body = response.getBodyString();
        if (status == 401) {
            throw new IllegalStateException("xAI 管理密钥无效（HTTP 401）:"
                    + "请确认「余额密钥」填的是 xAI Console 创建的 Management Key");
        }
        if (status == 403) {
            throw new IllegalStateException("无权校验管理密钥（HTTP 403）:"
                    + "当前管理密钥可能缺少 Billing 读取权限");
        }
        if (status != 200) {
            throw new IllegalStateException("校验管理密钥失败：HTTP " + status + " " + snippet(body));
        }
        return teamIdOf(MAPPER.readTree(body));
    }

    /**
     * 从管理密钥校验响应里宽容解析 team_id：字段名可能是 scopeId / teamId /
     * team_id / scope.id / team.id，或 teams 数组首项的 id。
     *
     * @param root 校验响应根节点
     * @return team_id；解析不出为 null
     */
    static String teamIdOf(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        for (String field : new String[]{"scopeId", "scope_id", "teamId", "team_id",
                "activeTeamId", "active_team_id"}) {
            String value = textOf(root.path(field));
            if (value != null) {
                return value;
            }
        }
        for (String field : new String[]{"scope", "team"}) {
            JsonNode node = root.path(field);
            if (node.isObject()) {
                for (String sub : new String[]{"id", "scopeId", "scope_id", "teamId", "team_id"}) {
                    String value = textOf(node.path(sub));
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        JsonNode teams = root.path("teams");
        if (teams.isArray() && teams.size() > 0) {
            String value = textOf(teams.get(0).path("id"));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 值节点 → 非空文本。
     *
     * @param node 节点
     * @return 文本；节点缺失或空白为 null
     */
    private static String textOf(JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    /**
     * 从完整余额地址里提取 team_id（仅用于 extra 展示，取不到不影响查询）。
     *
     * @param url 完整余额地址
     * @return team_id；取不到为 null
     */
    private static String teamIdOfPath(String url) {
        if (url == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("/billing/teams/([^/]+)/prepaid/balance").matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 请求预付余额端点并解析：{@code total.val} 为美分字符串、符号取反
     * （负数 = 剩余美分绝对值，非负 = 预付额度已用尽）。
     *
     * @param query  请求（已带 Bearer 管理密钥）
     * @param url    完整余额地址
     * @param teamId team_id（进 extra 便于排查，可为 null）
     * @return 结果；查不到时 error 有值
     * @throws IOException 响应不可读
     */
    private CreditResult fetchBalance(CreditQuery query, String url, String teamId) throws IOException {
        ClientResponse response = CreditHttp.exchange(query, url);
        int status = response.getStatusCode();
        String body = response.getBodyString();
        if (status == 401) {
            return CreditResult.fail(NAME, "xAI 管理密钥无效（HTTP 401）:"
                    + "请确认「余额密钥」填的是 xAI Console 创建的 Management Key");
        }
        if (status == 403) {
            return CreditResult.fail(NAME, "无权读取该团队的预付余额（HTTP 403）:"
                    + "管理密钥可能缺少 Billing 读取权限，或无该团队访问权");
        }
        if (status != 200) {
            return CreditResult.fail(NAME, "HTTP " + status + " " + snippet(body));
        }
        JsonNode root = MAPPER.readTree(body);
        JsonNode valNode = root.path("total").path("val");
        if (!valNode.isValueNode() || valNode.isNull() || valNode.asText().isBlank()) {
            return CreditResult.fail(NAME, "预付余额响应缺少 total.val 字段（接口结构可能已变更）");
        }
        BigDecimal cents;
        try {
            cents = new BigDecimal(valNode.asText().trim());
        } catch (NumberFormatException e) {
            return CreditResult.fail(NAME, "预付余额响应的 total.val 不是合法数字: " + valNode.asText());
        }
        // 符号取反：负数表示剩余预付额度（美分），非负表示已用尽
        BigDecimal remain = cents.signum() < 0 ? cents.negate().movePointLeft(SCALE) : BigDecimal.ZERO;
        List<BalancePart> parts = new ArrayList<>(1);
        parts.add(part("预付余额", remain));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("total_val_cents", cents);
        if (teamId != null && !teamId.isBlank()) {
            extra.put("team_id", teamId);
        }
        if (cents.signum() >= 0) {
            extra.put("note", "预付额度已用尽（total.val 非负）");
        }
        CreditResult result = CreditResult.ok(NAME, parts, body);
        result.setExtra(extra);
        return result;
    }

    /**
     * 组一段读数（美元）。
     *
     * @param label  段名
     * @param amount 金额
     * @return 读数
     */
    private static BalancePart part(String label, BigDecimal amount) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount);
        part.setCurrency(CURRENCY);
        return part;
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
