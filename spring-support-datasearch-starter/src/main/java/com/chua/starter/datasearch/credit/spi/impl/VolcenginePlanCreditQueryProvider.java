package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 火山引擎（豆包 / 火山方舟）余额 + 个人套餐订阅聚合实现。
 *
 * <p>三条官方口径一次查全。账户余额委托登记表 {@code volc-billing} 预设
 * （{@code GET billing.volcengineapi.com}，{@code volc} 签名）；套餐状态与用量窗口
 * 走方舟 OpenAPI 统一入口 {@code POST https://ark.cn-beijing.volcengineapi.com/?Action=<Action>&Version=2024-01-01}
 * （{@code volc-ark} 签名，IAM AK/SK 鉴权，模型 API Key 查不了）：</p>
 * <ul>
 *   <li>套餐状态 {@code GetPersonalPlan}：先查 {@code Plan=CodingPlan}，未订阅
 *       （{@code ResourceNotFound.Plan}）再查 {@code Plan=AgentPlan}；两类都没有时
 *       降级为「未订阅个人套餐」，不当致命错误——只订余额的用户仍能看到余额；</li>
 *   <li>用量窗口：CodingPlan 调 {@code GetCodingPlanUsage}（session / weekly /
 *       monthly 三窗口，上游只给已用百分比与重置时间戳）；AgentPlan 调
 *       {@code GetAFPUsage}（5 小时 / 日 / 周 / 月四窗口，Quota / Used 绝对值）。</li>
 * </ul>
 *
 * <p>读数行为：余额三段（可用 / 现金 / 欠费）在 parts 最前，卡片主读数 = 钱；
 * 订阅信息与用量窗口放 {@link CreditResult#getExtra()}（{@code mode=volc-plan}、
 * {@code plan/type/status/start/end/auto_renew}、{@code pools=[{name, windows:[
 * {label, remaining, limit, percent, reset}]}]}）。{@code percent} 统一为
 * 「剩余占比」（与 z.ai 口径一致，CodingPlan 的已用百分比在此换算），
 * 前端复用资源池窗口视图渲染。</p>
 *
 * @author CH
 * @since 4.0.0.61
 */
@Spi("volc-plan")
public class VolcenginePlanCreditQueryProvider implements CreditQueryProvider {

    /**
     * 实现标识（控制器 candidateKinds 里豆包 / 火山方舟渠道的第一个余额候选）
     */
    static final String NAME = "volc-plan";

    /**
     * 方舟 OpenAPI 统一入口（Action / Version 走 query，请求体走 POST JSON）
     */
    private static final String ARK_API = "https://ark.cn-beijing.volcengineapi.com/";

    /**
     * 方舟 OpenAPI 版本
     */
    private static final String ARK_VERSION = "2024-01-01";

    /**
     * 「未订阅」判定标记：上游错误码含它即视为正常状态（无订阅），不当致命错误
     */
    static final String NO_PLAN_MARK = "ResourceNotFound";

    /**
     * 两类个人套餐都没订阅时的提示
     */
    static final String NO_PLAN_TEXT = "未订阅个人套餐";

    /**
     * 响应映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 聚合查询：余额 + 套餐状态 + 用量窗口。
     *
     * <p>AK/SK 缺失时直接给明确提示，不发必然 401 的请求；余额与订阅各自
     * best-effort，任一查到即成功。</p>
     *
     * @param query 请求（需带 IAM AK/SK）
     * @return 结果；余额与订阅都没查到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            if (blank(query.getAccessKey()) || blank(query.getSecretKey())) {
                return CreditResult.fail(NAME, "缺少 IAM 访问密钥（AK/SK）：火山方舟余额与订阅查询走 OpenAPI 签名，"
                        + "模型 API Key 查不了；请配置余额密钥 accessKey / secretKey");
            }
            CreditResult balance = fetchBalance(query);
            CreditResult plan = fetchPlan(query);
            CreditResult usage = plan.isOk() ? fetchUsage(query, planKindOf(plan)) : null;
            return merge(balance, plan, usage);
        } catch (RuntimeException e) {
            return CreditResult.fail(NAME, e.getMessage());
        }
    }

    /**
     * 查账户余额：委托登记表 volc-billing 预设（volc 签名 GET，复用官方表达式）。
     *
     * @param query 请求
     * @return 余额结果；失败不影响订阅读数
     */
    private static CreditResult fetchBalance(CreditQuery query) {
        try {
            CreditQuery sub = new CreditQuery();
            sub.setKind("registry");
            sub.setProviderId(blank(query.getProviderId()) ? "volc-billing" : query.getProviderId().trim());
            sub.setOriginUrl(query.getOriginUrl());
            sub.setAccessKey(query.getAccessKey());
            sub.setSecretKey(query.getSecretKey());
            // 页面显式配了余额表达式时尊重用户配置，否则用登记表预设表达式
            sub.setExpression(blank(query.getExpression()) ? null : query.getExpression());
            sub.setBilling(query.getBilling());
            return new RegistryCreditQueryProvider().query(sub);
        } catch (RuntimeException e) {
            return CreditResult.fail(NAME, e.getMessage());
        }
    }

    /**
     * 查套餐状态：先 CodingPlan 后 AgentPlan，两类都 {@code ResourceNotFound} 降级「未订阅」。
     *
     * @param query 请求
     * @return 套餐结果；extra 带 plan / type / status / start / end / auto_renew
     */
    private static CreditResult fetchPlan(CreditQuery query) {
        for (String plan : new String[]{"CodingPlan", "AgentPlan"}) {
            ArkReply reply = postArk(query, "GetPersonalPlan", "{\"Plan\":\"" + plan + "\"}");
            CreditResult parsed = reply.ok() ? parsePlan(reply.raw()) : CreditResult.fail(NAME, reply.error());
            if (parsed.isOk() || !isNoPlan(parsed)) {
                return parsed;
            }
            // 本类套餐未订阅（ResourceNotFound）：换下一类再试
        }
        return CreditResult.fail(NAME, NO_PLAN_TEXT);
    }

    /**
     * 按套餐类型查用量窗口。
     *
     * @param query    请求
     * @param planKind 套餐类型（CodingPlan / AgentPlan）
     * @return 用量结果；extra 带 pools；无套餐时为 null
     */
    private static CreditResult fetchUsage(CreditQuery query, String planKind) {
        if ("AgentPlan".equals(planKind)) {
            ArkReply reply = postArk(query, "GetAFPUsage", "{}");
            return reply.ok() ? parseUsage(reply.raw(), planKind) : CreditResult.fail(NAME, reply.error());
        }
        if ("CodingPlan".equals(planKind)) {
            ArkReply reply = postArk(query, "GetCodingPlanUsage", "{}");
            return reply.ok() ? parseUsage(reply.raw(), planKind) : CreditResult.fail(NAME, reply.error());
        }
        return null;
    }

    /**
     * 调一次方舟 OpenAPI（volc-ark 签名 POST）。
     *
     * @param query  请求
     * @param action OpenAPI Action
     * @param body   JSON 请求体
     * @return 原始响应；网络 / 非 2xx 转成 fail，不带崩聚合
     */
    private static ArkReply postArk(CreditQuery query, String action, String body) {
        try {
            CreditQuery sub = new CreditQuery();
            sub.setKind(NAME);
            sub.setSignType("volc-ark");
            sub.setAccessKey(query.getAccessKey());
            sub.setSecretKey(query.getSecretKey());
            sub.setBody(body);
            String raw = CreditHttp.fetch(sub, ARK_API + "?Action=" + action + "&Version=" + ARK_VERSION);
            return ArkReply.of(raw);
        } catch (RuntimeException e) {
            return ArkReply.fail(e.getMessage());
        }
    }

    /**
     * 一次方舟 OpenAPI 调用的原始结果。
     *
     * @param ok    是否拿到 2xx 响应
     * @param raw   响应正文（ok 时有值）
     * @param error 失败原因（含上游错误码片段，供「无订阅」判定）
     */
    private record ArkReply(boolean ok, String raw, String error) {

        /**
         * 成功响应。
         *
         * @param raw 响应正文
         * @return 结果
         */
        static ArkReply of(String raw) {
            return new ArkReply(true, raw, null);
        }

        /**
         * 失败响应。
         *
         * @param error 失败原因
         * @return 结果
         */
        static ArkReply fail(String error) {
            return new ArkReply(false, null, error);
        }
    }

    /**
     * 解析 GetPersonalPlan 响应（信封 + Result）。
     *
     * <p>包可见以便单测直接喂官方样例、不走网络。信封带
     * {@code ResponseMetadata.Error} 时按错误码原文返回（含
     * {@code ResourceNotFound.Plan} 即「本类套餐未订阅」）。</p>
     *
     * @param raw 原始响应
     * @return 结果；extra 带 plan / type / status / start / end / auto_renew
     */
    static CreditResult parsePlan(String raw) {
        JsonNode root = jsonOf(raw);
        if (root == null) {
            return CreditResult.fail(NAME, "套餐响应不是合法 JSON");
        }
        JsonNode error = root.path("ResponseMetadata").path("Error");
        if (error.isObject() && !error.path("Code").asText("").isBlank()) {
            return CreditResult.fail(NAME, errorText(error));
        }
        JsonNode result = root.path("Result");
        if (!result.isObject() || result.isEmpty()) {
            return CreditResult.fail(NAME, "套餐响应缺少 Result 节点");
        }
        String plan = textOf(result, "Plan");
        String type = textOf(result, "PlanType");
        String kind = planKindOf(plan, type);
        String label;
        if ("AgentPlan".equals(kind)) {
            label = "Agent Plan";
        } else if ("CodingPlan".equals(kind)) {
            label = "Coding Plan";
        } else {
            label = plan.isBlank() ? "个人套餐" : plan;
        }
        if (!type.isBlank()) {
            label = label + " · " + type;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("plan", label);
        if (!type.isBlank()) {
            extra.put("type", type);
        }
        String status = textOf(result, "Status");
        if (!status.isBlank()) {
            extra.put("status", statusLabel(status));
        }
        String start = textOf(result, "StartTime");
        if (!start.isBlank()) {
            extra.put("start", start);
        }
        String end = textOf(result, "EndTime");
        if (!end.isBlank()) {
            extra.put("end", end);
        }
        if (result.hasNonNull("AutoRenew")) {
            extra.put("auto_renew", result.path("AutoRenew").asBoolean() ? "自动续费" : "不自动续费");
        }
        CreditResult ok = CreditResult.okData(NAME, raw);
        ok.setExtra(extra);
        return ok;
    }

    /**
     * 解析用量响应（GetCodingPlanUsage / GetAFPUsage）。
     *
     * <p>包可见以便单测直接喂官方样例。按套餐类型分两种结构：
     * CodingPlan 的 {@code Result.QuotaUsage[]}（只给已用百分比）与 AgentPlan 的
     * {@code Result.AFP*} 四窗口（Quota / Used 绝对值）。</p>
     *
     * @param raw      原始响应
     * @param planKind 套餐类型（CodingPlan / AgentPlan）
     * @return 结果；extra 带 pools_title / pools
     */
    static CreditResult parseUsage(String raw, String planKind) {
        JsonNode root = jsonOf(raw);
        if (root == null) {
            return CreditResult.fail(NAME, "用量响应不是合法 JSON");
        }
        JsonNode error = root.path("ResponseMetadata").path("Error");
        if (error.isObject() && !error.path("Code").asText("").isBlank()) {
            return CreditResult.fail(NAME, errorText(error));
        }
        JsonNode result = root.path("Result");
        if (!result.isObject()) {
            return CreditResult.fail(NAME, "用量响应缺少 Result 节点");
        }
        List<Map<String, Object>> pools = "AgentPlan".equals(planKind)
                ? parseAfpPools(result) : parseCodingPools(result);
        if (pools.isEmpty()) {
            return CreditResult.fail(NAME, "用量响应里没有可读的窗口数值");
        }
        CreditResult ok = CreditResult.okData(NAME, raw);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("pools_title", "AgentPlan".equals(planKind) ? "Agent Plan 用量窗口" : "Coding Plan 用量窗口");
        extra.put("pools", pools);
        ok.setExtra(extra);
        return ok;
    }

    /**
     * 合并余额、套餐、用量三路结果。
     *
     * <p>余额放 parts 最前（卡片主读数 = 钱）；套餐信息与用量窗口进 extra。
     * 三路全失败时把余额与套餐的失败原因拼成一行返回。</p>
     *
     * @param balance 余额结果（可能失败）
     * @param plan    套餐结果（可能失败或为「未订阅」）
     * @param usage   用量结果（无套餐时为 null）
     * @return 合并结果
     */
    static CreditResult merge(CreditResult balance, CreditResult plan, CreditResult usage) {
        boolean balanceOk = balance != null && balance.isOk();
        boolean planOk = plan != null && plan.isOk();
        boolean usageOk = usage != null && usage.isOk();
        if (!balanceOk && !planOk && !usageOk) {
            return CreditResult.fail(NAME, "余额与订阅均未查到：余额（" + errorOf(balance)
                    + "）；套餐（" + errorOf(plan) + "）");
        }
        CreditResult base = balanceOk ? balance : (planOk ? plan : usage);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("mode", NAME);
        if (planOk && plan.getExtra() != null) {
            extra.putAll(plan.getExtra());
        } else if (plan != null && !plan.isOk()) {
            // 未订阅个人套餐 或 套餐接口硬错误：原文留给页面
            extra.put("plan", plan.getError());
        }
        if (usageOk && usage.getExtra() != null) {
            Object title = usage.getExtra().get("pools_title");
            Object pools = usage.getExtra().get("pools");
            if (title != null) {
                extra.put("pools_title", title);
            }
            if (pools != null) {
                extra.put("pools", pools);
            }
        }
        List<BalancePart> parts = balanceOk && balance.getParts() != null
                ? balance.getParts() : List.of();
        CreditResult result = CreditResult.ok(NAME, parts, base.getRaw());
        result.setExtra(extra);
        return result;
    }

    /**
     * CodingPlan 用量：{@code Result.QuotaUsage[]} 逐窗口解析。
     *
     * <p>上游 {@code Percent} 是「已用占比」，这里换算成「剩余占比」与 z.ai 口径对齐；
     * {@code ResetTimestamp} 为秒（session 窗口可能是 -1，不输出 reset）。</p>
     *
     * @param result Result 节点
     * @return 池列表；无可读窗口时为空
     */
    private static List<Map<String, Object>> parseCodingPools(JsonNode result) {
        JsonNode usage = result.path("QuotaUsage");
        if (!usage.isArray() || usage.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> windows = new ArrayList<>();
        for (JsonNode node : usage) {
            if (node == null || !node.isObject()) {
                continue;
            }
            Map<String, Object> window = new LinkedHashMap<>();
            window.put("label", levelLabel(node.path("Level").asText("").trim()));
            BigDecimal used = decimal(node.path("Percent"));
            if (used != null) {
                BigDecimal remaining = BigDecimal.valueOf(100).subtract(used)
                        .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
                window.put("percent", remaining.setScale(0, RoundingMode.HALF_UP));
            }
            long reset = node.path("ResetTimestamp").asLong(-1);
            if (reset > 0) {
                window.put("reset", Instant.ofEpochSecond(reset).toString());
            }
            windows.add(window);
        }
        if (windows.isEmpty()) {
            return List.of();
        }
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("name", "Coding Plan");
        pool.put("windows", windows);
        return List.of(pool);
    }

    /**
     * AgentPlan 用量：{@code Result.AFPFiveHour / AFPDaily / AFPWeekly / AFPMonthly} 四窗口。
     *
     * <p>每窗口 {@code Quota / Used} 为绝对值：remaining = Quota − Used，
     * percent = 剩余 ÷ Quota；{@code ResetTime} 为毫秒时间戳。</p>
     *
     * @param result Result 节点
     * @return 池列表；无可读窗口时为空
     */
    private static List<Map<String, Object>> parseAfpPools(JsonNode result) {
        String[][] spec = {
                {"AFPFiveHour", "每 5 小时"},
                {"AFPDaily", "每日"},
                {"AFPWeekly", "每周"},
                {"AFPMonthly", "每月"},
        };
        List<Map<String, Object>> windows = new ArrayList<>();
        for (String[] row : spec) {
            JsonNode node = result.path(row[0]);
            if (!node.isObject()) {
                continue;
            }
            BigDecimal quota = decimal(node.path("Quota"));
            BigDecimal used = decimal(node.path("Used"));
            if (quota == null && used == null) {
                continue;
            }
            Map<String, Object> window = new LinkedHashMap<>();
            window.put("label", row[1]);
            if (quota != null) {
                window.put("limit", norm(quota));
            }
            if (used != null) {
                window.put("usage", norm(used));
            }
            if (quota != null && used != null) {
                window.put("remaining", norm(quota.subtract(used)));
                BigDecimal percent = remainingPercent(quota.subtract(used), quota);
                if (percent != null) {
                    window.put("percent", percent);
                }
            }
            long reset = node.path("ResetTime").asLong(-1);
            if (reset > 0) {
                window.put("reset", Instant.ofEpochMilli(reset).toString());
            }
            windows.add(window);
        }
        if (windows.isEmpty()) {
            return List.of();
        }
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("name", "Agent Plan");
        pool.put("windows", windows);
        return List.of(pool);
    }

    /**
     * 套餐类型归一：Plan 字段或 PlanType 都能判定。
     *
     * @param plan Plan 字段（CodingPlan / AgentPlan，可能缺）
     * @param type PlanType 字段（Lite/Pro 或 Small/Medium/Large/Max）
     * @return CodingPlan / AgentPlan；判定不了为空串
     */
    static String planKindOf(String plan, String type) {
        String key = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if ("AgentPlan".equalsIgnoreCase(plan == null ? "" : plan.trim()) || isAgentType(key)) {
            return "AgentPlan";
        }
        if ("CodingPlan".equalsIgnoreCase(plan == null ? "" : plan.trim()) || isCodingType(key)) {
            return "CodingPlan";
        }
        return "";
    }

    /**
     * 从套餐结果里取归一的套餐类型（决定用量接口用哪条）。
     *
     * @param plan 套餐结果
     * @return CodingPlan / AgentPlan；判定不了为空串
     */
    static String planKindOf(CreditResult plan) {
        if (plan == null || plan.getExtra() == null) {
            return "";
        }
        Object label = plan.getExtra().get("plan");
        String text = label == null ? "" : String.valueOf(label);
        if (text.startsWith("Agent Plan")) {
            return "AgentPlan";
        }
        if (text.startsWith("Coding Plan")) {
            return "CodingPlan";
        }
        return "";
    }

    /**
     * PlanType 是否属于 AgentPlan（Small / Medium / Large / Max）。
     *
     * @param type 大写 PlanType
     * @return AgentPlan 类型为 true
     */
    private static boolean isAgentType(String type) {
        switch (type == null ? "" : type) {
            case "SMALL":
            case "MEDIUM":
            case "LARGE":
            case "MAX":
                return true;
            default:
                return false;
        }
    }

    /**
     * PlanType 是否属于 CodingPlan（Lite / Pro）。
     *
     * @param type 大写 PlanType
     * @return CodingPlan 类型为 true
     */
    private static boolean isCodingType(String type) {
        switch (type == null ? "" : type) {
            case "LITE":
            case "PRO":
                return true;
            default:
                return false;
        }
    }

    /**
     * CodingPlan 配额窗口级别中文标。
     *
     * @param level 上游 Level（session / weekly / monthly）
     * @return 展示标
     */
    private static String levelLabel(String level) {
        String key = level == null ? "" : level.toLowerCase(Locale.ROOT);
        switch (key) {
            case "session":
                return "单次会话";
            case "weekly":
                return "每周";
            case "monthly":
                return "每月";
            default:
                return level == null || level.isBlank() ? "窗口" : level;
        }
    }

    /**
     * 套餐状态中文标。
     *
     * @param status 上游 Status（Running / Expired）
     * @return 展示标
     */
    private static String statusLabel(String status) {
        switch (status == null ? "" : status.trim().toLowerCase(Locale.ROOT)) {
            case "running":
                return "生效中";
            case "expired":
                return "已过期";
            default:
                return status;
        }
    }

    /**
     * 数值规范化：去尾零但不留负刻度（1000.00 → 1000 而非 1E+3），JSON / 前端都好读。
     *
     * @param value 原值
     * @return 规范化后的值
     */
    private static BigDecimal norm(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /**
     * 剩余占比（0..100 整数）。
     *
     * @param remaining 剩余
     * @param limit     上限
     * @return 占比；上限非正时为 null
     */
    private static BigDecimal remainingPercent(BigDecimal remaining, BigDecimal limit) {
        if (limit == null || limit.signum() <= 0) {
            return null;
        }
        return remaining.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    /**
     * 失败是否为「本类套餐未订阅」（上游 ResourceNotFound 类错误码）。
     *
     * @param result 套餐结果
     * @return 未订阅为 true
     */
    private static boolean isNoPlan(CreditResult result) {
        return !result.isOk() && result.getError() != null && result.getError().contains(NO_PLAN_MARK);
    }

    /**
     * 信封错误码 + 消息拼成一行。
     *
     * @param error Error 节点
     * @return 如 {@code ResourceNotFound.Plan：套餐不存在}
     */
    private static String errorText(JsonNode error) {
        String code = error.path("Code").asText("").trim();
        String message = error.path("Message").asText("").trim();
        return code + (message.isBlank() ? "" : "：" + message);
    }

    /**
     * 结果失败原因（null 兜底）。
     *
     * @param result 结果
     * @return 失败原因
     */
    private static String errorOf(CreditResult result) {
        return result == null || result.getError() == null ? "未查询" : result.getError();
    }

    /**
     * 取节点文本（去空白）。
     *
     * @param node  节点
     * @param field 字段名
     * @return 文本；缺失为空串
     */
    private static String textOf(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? "" : value;
    }

    /**
     * 取节点数值；文本按数字解析（去掉千分位）。
     *
     * @param node 节点
     * @return 数值；缺失或无法解析时为 null
     */
    private static BigDecimal decimal(JsonNode node) {
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
     * 解析 JSON，失败返回 null（由调用方给可读错误）。
     *
     * @param raw 原始响应
     * @return 根节点；解析失败为 null
     */
    private static JsonNode jsonOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
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
