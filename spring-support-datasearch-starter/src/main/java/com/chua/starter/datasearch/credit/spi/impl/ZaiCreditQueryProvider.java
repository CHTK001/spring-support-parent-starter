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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * z.ai（智谱 GLM）余额与 Coding Plan 订阅配额实现。
 *
 * <p>鉴权为标准 {@code Authorization: Bearer <API Key>}（探活实证：配额接口接受
 * 渠道 API Key，不需要登录会话）。站点按 Key 所属上游地址区分：</p>
 * <ul>
 *   <li>国际站：{@code GET https://api.z.ai/api/monitor/usage/quota/limit}；</li>
 *   <li>国内站：{@code GET https://open.bigmodel.cn/api/monitor/usage/quota/limit}，
 *       另可查账户余额 {@code GET https://www.bigmodel.cn/api/biz/account/query-customer-account-report}
 *       （取 {@code availableBalance}，回退 {@code balance}，人民币口径）；
 *       国际站没有官方余额接口，只出订阅配额。</li>
 * </ul>
 *
 * <p>配额响应为 {@code {success, code, data:{planName?, limits:[{type, unit, number,
 * usage, currentValue, remaining, percentage, nextResetTime}]}}}：{@code type} 区分
 * Token / 积分 / MCP 配额，{@code unit}（1=天、3=时、5=分、6=周）与 {@code number}
 * 相乘得窗口时长。国内站未订阅 Coding Plan 时上游返回
 * {@code success=false, code=500, msg=当前用户不存在coding plan}——这不算失败，
 * 降级为「仅余额 / 无订阅」，页面以 {@code extra.coding_plan=未开通} 提示。</p>
 *
 * <p>读数行为：国内站 parts[0] = 可用余额（¥）；无余额口径时 parts[0] = 第一个
 * 配额窗口的剩余（单位 tokens，与 MiniMax 的「剩余次数」同口径）。逐窗口明细按
 * 配额类型分组放进 {@link CreditResult#getExtra()}（mode=zai, pools=[{name, windows:
 * [{label, remaining, limit, percent, reset}]}]），前端复用资源池窗口视图渲染。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Spi("zai")
public class ZaiCreditQueryProvider implements CreditQueryProvider {

    /**
     * 实现标识（登记表里的 z.ai / zhipu 平台都归它管）
     */
    static final String NAME = "zai";

    /**
     * 国际站 Coding Plan 配额接口
     */
    private static final String INTL_QUOTA_URL = "https://api.z.ai/api/monitor/usage/quota/limit";

    /**
     * 国内站 Coding Plan 配额接口
     */
    private static final String CN_QUOTA_URL = "https://open.bigmodel.cn/api/monitor/usage/quota/limit";

    /**
     * 国内站账户余额接口（国际站无官方余额接口）
     */
    private static final String CN_BALANCE_URL = "https://www.bigmodel.cn/api/biz/account/query-customer-account-report";

    /**
     * 未订阅 Coding Plan 的失败标记：query() 据此把这类失败降级为「无订阅」，
     * 不当作致命错误（国内站免费用户没有 Coding Plan 属正常状态）
     */
    static final String NO_PLAN_MARK = "Coding Plan";

    /**
     * Token 配额的读数单位
     */
    private static final String UNIT_TOKENS = "tokens";

    /**
     * 响应 映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 按 z.ai 口径查询余额与订阅配额。
     *
     * <p>鉴权为标准 Bearer API Key（调用方已把令牌原值放进 token 字段，
     * 请求出口统一补 {@code Bearer } 前缀）。显式配了地址时以调用方为准，
     * 否则按站点取默认地址；国内站额外 best-effort 查一次账户余额。</p>
     *
     * @param query 请求
     * @return 结果；配额与余额都没查到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            if (blank(query.getToken())) {
                return CreditResult.fail(NAME, "缺少 API Key（z.ai 用标准 API Key Bearer 鉴权）");
            }
            boolean cn = isCnSite(query);
            String quotaUrl = blank(query.getUrl()) ? (cn ? CN_QUOTA_URL : INTL_QUOTA_URL) : query.getUrl().trim();
            CreditResult quota = fetchQuota(query, quotaUrl);
            boolean noPlan = quota.getError() != null && quota.getError().contains(NO_PLAN_MARK);
            CreditResult balance = cn ? fetchBalance(query) : null;
            return merge(quota, balance, noPlan);
        } catch (RuntimeException e) {
            return CreditResult.fail(NAME, e.getMessage());
        }
    }

    /**
     * 判断 Key 所属站点是否为国内站（bigmodel.cn）。
     *
     * <p>上游地址与显式配置的余额地址都参与判断：任一含 bigmodel.cn 即按国内站口径。</p>
     *
     * @param query 请求
     * @return 国内站为 true
     */
    private static boolean isCnSite(CreditQuery query) {
        return containsCnHost(query.getOriginUrl()) || containsCnHost(query.getUrl());
    }

    /**
     * 地址是否指向智谱国内站（bigmodel.cn 及其子域）。
     *
     * @param url 地址
     * @return 国内站地址为 true
     */
    private static boolean containsCnHost(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).contains("bigmodel.cn");
    }

    /**
     * 查询 Coding Plan 配额（网络出口单独包一层：失败转成 fail，不带崩合并逻辑）。
     *
     * @param query 请求
     * @param url   配额接口地址
     * @return 结果
     */
    private static CreditResult fetchQuota(CreditQuery query, String url) {
        try {
            return parseQuota(CreditHttp.fetch(query, url));
        } catch (RuntimeException e) {
            return CreditResult.fail(NAME, e.getMessage());
        }
    }

    /**
     * 查询国内站账户余额（best-effort：失败只影响提示，不影响配额读数）。
     *
     * @param query 请求
     * @return 结果
     */
    private static CreditResult fetchBalance(CreditQuery query) {
        try {
            return parseBalance(CreditHttp.fetch(query, CN_BALANCE_URL));
        } catch (RuntimeException e) {
            return CreditResult.fail(NAME, e.getMessage());
        }
    }

    /**
     * 解析 Coding Plan 配额响应。
     *
     * <p>包可见以便单测直接喂样例、不走网络。信封 {@code success=false} 时区分
     * 「未订阅 Coding Plan」（带 {@link #NO_PLAN_MARK} 标记，调用方降级）与其它错误；
     * {@code limits} 逐窗口解析并按配额类型分组进 extra.pools。</p>
     *
     * @param raw 原始响应
     * @return 结果
     */
    static CreditResult parseQuota(String raw) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            return CreditResult.fail(NAME, "响应不是合法 JSON: " + e.getMessage());
        }
        if (!root.path("success").asBoolean(true)) {
            String message = root.path("msg").asText(root.path("message").asText(""));
            if (message.toLowerCase(Locale.ROOT).contains("coding plan")) {
                return CreditResult.fail(NAME, "当前用户不存在 " + NO_PLAN_MARK);
            }
            return CreditResult.fail(NAME, "上游返回错误 code=" + root.path("code").asInt(0)
                    + (message.isBlank() ? "" : "：" + message));
        }
        JsonNode data = root.path("data");
        List<Map<String, Object>> pools = parsePools(data.path("limits"));
        if (pools.isEmpty()) {
            return CreditResult.fail(NAME, "配额响应里没有可读的窗口数值");
        }
        // parts[0]：第一个窗口的剩余（无余额口径时作为卡片主读数，与 MiniMax 同口径）
        List<BalancePart> parts = new ArrayList<>();
        Map<String, Object> firstPool = pools.get(0);
        BigDecimal firstRemaining = firstWindowRemaining(firstPool);
        if (firstRemaining != null) {
            String type = String.valueOf(firstPool.get("type"));
            parts.add(part(firstPool.get("name") + " " + firstPool.get("window_label")
                    + " 剩余", firstRemaining, unitFor(type)));
        }
        CreditResult result = CreditResult.ok(NAME, parts, raw);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("mode", NAME);
        if (allTokenPools(pools)) {
            extra.put("unit", UNIT_TOKENS);
        }
        String planName = planNameOf(data);
        if (planName != null) {
            extra.put("plan", planName);
        }
        extra.put("pools_title", "Coding Plan 配额");
        extra.put("pools", pools);
        result.setExtra(extra);
        return result;
    }

    /**
     * 解析国内站账户余额响应。
     *
     * <p>取 {@code availableBalance}（可用余额），缺失时回退 {@code balance}；
     * 人民币口径。</p>
     *
     * @param raw 原始响应
     * @return 结果
     */
    static CreditResult parseBalance(String raw) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            return CreditResult.fail(NAME, "余额响应不是合法 JSON: " + e.getMessage());
        }
        if (!root.path("success").asBoolean(false)) {
            String message = root.path("msg").asText("余额接口返回失败");
            return CreditResult.fail(NAME, message);
        }
        JsonNode data = root.path("data");
        BigDecimal available = decimal(data.path("availableBalance"));
        if (available == null) {
            available = decimal(data.path("balance"));
        }
        if (available == null) {
            return CreditResult.fail(NAME, "余额响应里没有可读的数值");
        }
        return CreditResult.ok(NAME, List.of(part("可用余额", available, "CNY")), raw);
    }

    /**
     * 合并配额与余额两次查询。
     *
     * <p>余额放 parts 最前（卡片主读数 = 钱）；配额窗口只走 extra.pools 结构化视图，
     * 不重复占 parts。未订阅 Coding Plan 时 extra 带 {@code coding_plan=未开通}。</p>
     *
     * @param quota  配额结果（可能失败）
     * @param balance 余额结果（可能失败或为 null）
     * @param noPlan 上游是否明确「未订阅 Coding Plan」
     * @return 合并结果；两边都没查到时 fail
     */
    static CreditResult merge(CreditResult quota, CreditResult balance, boolean noPlan) {
        boolean quotaOk = quota != null && quota.isOk();
        boolean balanceOk = balance != null && balance.isOk();
        if (!quotaOk && !balanceOk) {
            String quotaMsg = quota == null ? "未查询" : quota.getError();
            String balanceMsg = balance == null ? "非国内站无余额接口" : balance.getError();
            String message = noPlan
                    ? "未订阅 " + NO_PLAN_MARK + "，账户余额也未查到：" + balanceMsg
                    : "配额与余额均未查到：配额（" + quotaMsg + "）；余额（" + balanceMsg + "）";
            return CreditResult.fail(NAME, message);
        }
        CreditResult base = quotaOk ? quota : balance;
        Map<String, Object> extra = base.getExtra() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(base.getExtra());
        extra.putIfAbsent("mode", NAME);
        if (noPlan) {
            extra.put("coding_plan", "未开通");
        }
        List<BalancePart> parts = new ArrayList<>();
        if (balanceOk) {
            parts.addAll(balance.getParts() == null ? List.of() : balance.getParts());
        } else if (quota.getParts() != null) {
            parts.addAll(quota.getParts());
        }
        CreditResult result = CreditResult.ok(NAME, parts, base.getRaw());
        result.setExtra(extra);
        return result;
    }

    /**
     * 解析 limits 数组：逐窗口取数，按配额类型分组（保持上游顺序）。
     *
     * @param limits 上游 limits 节点
     * @return 池列表（name / type / window_label / windows），空数组表示无可读窗口
     */
    private static List<Map<String, Object>> parsePools(JsonNode limits) {
        if (!limits.isArray() || limits.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (JsonNode node : limits) {
            if (node == null || !node.isObject()) {
                continue;
            }
            Map<String, Object> window = windowOf(node);
            if (window == null) {
                continue;
            }
            String type = node.path("type").asText("");
            String typeLabel = typeLabel(type);
            Map<String, Object> pool = grouped.computeIfAbsent(typeLabel, key -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("name", key);
                value.put("type", type);
                value.put("window_label", windowLabel(node.path("unit").asInt(0), node.path("number").asInt(0)));
                value.put("windows", new ArrayList<Map<String, Object>>());
                return value;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> windows = (List<Map<String, Object>>) pool.get("windows");
            windows.add(window);
        }
        return new ArrayList<>(grouped.values());
    }

    /**
     * 解析一个配额窗口：剩余 / 上限 / 已用 / 百分比 / 重置时间。
     *
     * <p>剩余缺省时用 上限 − 已用 推算；百分比优先用 剩余 ÷ 上限 计算，
     * 上游只给 {@code percentage}（已用占比）时用 100 − 已用 推剩余占比。</p>
     *
     * @param node 窗口节点
     * @return 窗口数据；没有任何可读数值时为 null
     */
    private static Map<String, Object> windowOf(JsonNode node) {
        BigDecimal usage = decimal(node.path("usage"));
        BigDecimal limit = decimal(node.path("currentValue"));
        BigDecimal remaining = decimal(node.path("remaining"));
        if (remaining == null && limit != null && usage != null) {
            remaining = limit.subtract(usage);
        }
        if (remaining == null && usage == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", windowLabel(node.path("unit").asInt(0), node.path("number").asInt(0)));
        if (remaining != null) {
            row.put("remaining", remaining.stripTrailingZeros());
        }
        if (limit != null) {
            row.put("limit", limit.stripTrailingZeros());
        }
        if (usage != null) {
            row.put("usage", usage.stripTrailingZeros());
        }
        BigDecimal percent = percentOf(remaining, limit, node.path("percentage"));
        if (percent != null) {
            row.put("percent", percent);
        }
        String reset = node.path("nextResetTime").asText("").trim();
        if (!reset.isEmpty()) {
            row.put("reset", reset);
        }
        return row;
    }

    /**
     * 窗口剩余占比（0..100 整数）。
     *
     * @param remaining  剩余
     * @param limit      上限
     * @param percentage 上游已用占比（fallback）
     * @return 占比；算不出为 null
     */
    private static BigDecimal percentOf(BigDecimal remaining, BigDecimal limit, JsonNode percentage) {
        if (remaining != null && limit != null && limit.signum() > 0) {
            return remaining.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.HALF_UP);
        }
        if (percentage != null && percentage.isNumber()) {
            BigDecimal remainPct = BigDecimal.valueOf(100).subtract(percentage.decimalValue());
            if (remainPct.signum() < 0) {
                return BigDecimal.ZERO;
            }
            if (remainPct.compareTo(BigDecimal.valueOf(100)) > 0) {
                return BigDecimal.valueOf(100);
            }
            return remainPct.setScale(0, RoundingMode.HALF_UP);
        }
        return null;
    }

    /**
     * 窗口时长中文短标：unit（1=天、3=时、5=分、6=周）× number。
     *
     * @param unit   时长单位
     * @param number 时长数值
     * @return 展示标（如「5 小时」「每周」）
     */
    private static String windowLabel(int unit, int number) {
        int count = Math.max(number, 0);
        switch (unit) {
            case 6:
                return count <= 1 ? "每周" : count + " 周";
            case 1:
                return count <= 1 ? "每日" : count + " 天";
            case 3:
                return count <= 1 ? "每小时" : count + " 小时";
            case 5:
                return count + " 分钟";
            default:
                return "滚动窗口";
        }
    }

    /**
     * 配额类型中文名。
     *
     * @param type 上游类型标识
     * @return 展示名
     */
    private static String typeLabel(String type) {
        switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "TOKENS_LIMIT":
                return "Token 配额";
            case "CREDIT_LIMIT":
                return "积分配额";
            case "TIME_LIMIT":
                return "MCP 配额";
            default:
                return type == null || type.isBlank() ? "配额" : type;
        }
    }

    /**
     * 读数单位：Token 配额用 tokens、积分用 积分、MCP 用 次。
     *
     * @param type 上游类型标识
     * @return 单位
     */
    private static String unitFor(String type) {
        String key = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if ("TOKENS_LIMIT".equals(key)) {
            return UNIT_TOKENS;
        }
        if ("CREDIT_LIMIT".equals(key)) {
            return "积分";
        }
        if ("TIME_LIMIT".equals(key)) {
            return "次";
        }
        return "";
    }

    /**
     * 是否全部配额池都是 Token 类型（决定 extra.unit 是否写 tokens）。
     *
     * @param pools 池列表
     * @return 全为 Token 配额时 true
     */
    private static boolean allTokenPools(List<Map<String, Object>> pools) {
        for (Map<String, Object> pool : pools) {
            if (!"TOKENS_LIMIT".equalsIgnoreCase(String.valueOf(pool.get("type")))) {
                return false;
            }
        }
        return !pools.isEmpty();
    }

    /**
     * 第一个池第一个窗口的剩余（卡片主读数用）。
     *
     * @param pool 池数据
     * @return 剩余；没有为 null
     */
    @SuppressWarnings("unchecked")
    private static BigDecimal firstWindowRemaining(Map<String, Object> pool) {
        Object windows = pool.get("windows");
        if (!(windows instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> window)) {
            return null;
        }
        return window.get("remaining") instanceof BigDecimal amount ? amount : null;
    }

    /**
     * 套餐名：planName / plan_type / packageName / level 依次取，plan 为对象时取 name。
     *
     * @param data data 节点
     * @return 套餐名；没有为 null
     */
    private static String planNameOf(JsonNode data) {
        for (String field : new String[]{"planName", "plan_type", "packageName", "level"}) {
            String value = data.path(field).asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        JsonNode plan = data.path("plan");
        if (plan.isTextual()) {
            String value = plan.asText("").trim();
            return value.isEmpty() ? null : value;
        }
        if (plan.isObject()) {
            String value = plan.path("name").asText("").trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    /**
     * 组装一段读数。
     *
     * @param label    段名
     * @param amount   数值
     * @param currency 单位（tokens / 积分 / 次 / CNY）
     * @return 读数
     */
    private static BalancePart part(String label, BigDecimal amount, String currency) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount.stripTrailingZeros());
        part.setCurrency(currency);
        return part;
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
     * 空判断。
     *
     * @param text 文本
     * @return 为空为 true
     */
    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
