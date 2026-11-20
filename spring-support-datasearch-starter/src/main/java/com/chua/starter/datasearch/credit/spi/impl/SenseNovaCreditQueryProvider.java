package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SenseNova（商汤）平台余额实现（platform.sensenova.cn 令牌用量接口）。
 *
 * <p>对接 {@code GET https://platform.sensenova.cn/lite/console/v1/tokenplan/pool-usage}，
 * 鉴权为标准 {@code Authorization: Bearer <JWT>}。这里的令牌是<b>平台登录 JWT</b>
 * （用户登录 platform.sensenova.cn 后页面持有的凭据），不是 OAuth 令牌交换接口
 * {@code /oauth2/token} 返回的 {@code biz_data.token}——后者是不透明串，实测打
 * 余额接口返回 401。JWT 可由页面上的「账号登录」流程自动获取：登录完成后前端
 * SDK 把 JWT 写进 {@code localStorage["access_token"]}（见 {@link #loginStorageKey()}）。</p>
 *
 * <p>响应为「套餐 + 资源池数组」结构（2026-11 实测）：每个资源池带
 * {@code window_5h}（5 小时滚动窗口）与 {@code window_7d}（7 天滚动窗口）两组
 * {@code limit / used / remaining}，另有 {@code grant_balance}（赠送余额）。
 * 数值都是字符串型 token 数。读数行为：每个池依次给出 7天剩余 / 7天已用 /
 * 5小时剩余 / 5小时已用 / 赠送余额，parts[0]=第一个池的 7 天剩余（主读数）。
 * 币种口径为 {@code tokens}（按 token 计数的用量窗口，不是金额）。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Spi("sensenova")
public class SenseNovaCreditQueryProvider implements CreditQueryProvider {

    /**
     * 平台余额接口默认地址
     */
    private static final String DEFAULT_URL = "https://platform.sensenova.cn/lite/console/v1/tokenplan/pool-usage";

    /**
     * 登录页地址：页面上的「账号登录」按钮据此打开内置浏览器
     */
    private static final String LOGIN_PAGE_URL = "https://platform.sensenova.cn/login";

    /**
     * 静默续期回访页：平台控制台首页（已登录态的落地页，见 {@link #renewPageUrl(String)}）
     */
    private static final String RENEW_PAGE_URL = "https://platform.sensenova.cn/";

    /**
     * 登录成功后余额 JWT 所在的 localStorage 键名（前端 SDK 写入的明文键）；
     * 兜底键 NOVA_AUTH:nova:login 由会话管理器按「值里找 JWT」自动覆盖
     */
    private static final String LOGIN_STORAGE_KEY = "access_token";

    /**
     * 读数单位：token 数量（不是货币金额）
     */
    private static final String TOKEN_UNIT = "tokens";

    /**
     * 池名取不到时的兜底展示名
     */
    private static final String FALLBACK_POOL_NAME = "资源池";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 池节点转 Map 的类型引用（保留 model_ids 等数组字段）
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    @Override
    public String name() {
        return "sensenova";
    }

    /**
     * 「账号登录」入口页：用户在真实登录页登录后，内嵌浏览器轮询
     * {@code localStorage["access_token"]}（兜底扫描含 JWT 的登录态键）并回填令牌。
     *
     * @return SenseNova 平台登录页
     */
    @Override
    public String loginPageUrl() {
        return LOGIN_PAGE_URL;
    }

    /**
     * 登录成功后余额 JWT 所在的 localStorage 键。
     *
     * @return localStorage 键名
     */
    @Override
    public String loginStorageKey() {
        return LOGIN_STORAGE_KEY;
    }

    /**
     * 支持余额令牌静默续期：JWT 由平台登录态派生，预置旧 JWT 回访门户时
     * 页面会话仍被接受；平台轮换令牌后新 JWT 仍写入
     * {@code localStorage["access_token"]}，按登录截获钩子即可读到。
     *
     * @return true
     */
    @Override
    public boolean supportsTokenRenewal() {
        return true;
    }

    /**
     * 静默续期的回访页：平台控制台首页（已登录态的落地页）。
     *
     * <p>不用登录页——会话有效时登录页只会原地停留或跳控制台，拿不到
     * 令牌轮换；控制台首页会正常拉起前端 SDK，轮换令牌时写回 localStorage。</p>
     *
     * @param baseUrl 供应商行配置的上游根地址（本实现登录态固定在平台主域，忽略该参数）
     * @return SenseNova 平台首页
     */
    @Override
    public String renewPageUrl(String baseUrl) {
        return RENEW_PAGE_URL;
    }

    /**
     * 按 SenseNova 平台口径查询余额。
     *
     * <p>鉴权走标准 Bearer：{@code CreditHttp} 对空请求头默认补
     * {@code Authorization: Bearer <JWT>}。显式配了表达式时以表达式为准，
     * 否则按资源池结构逐池读数。</p>
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String url = blank(query.getUrl()) ? DEFAULT_URL : query.getUrl().trim();
            String raw = CreditHttp.fetch(query, url);
            if (!blank(query.getExpression())) {
                return parse(query.getExpression(), raw, query.getBilling());
            }
            return parsePools(raw);
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), friendlyError(e.getMessage()));
        }
    }

    /**
     * 把典型失败翻译成可操作的提示。
     *
     * <p>商汤的余额接口在控制台域，只认平台登录 JWT；用推理 API Key（sk-）访问时
     * 平台明确返回 {@code auth_type_disabled}（Authentication type 'apikey' is not
     * enabled）。这不是 Key 填错，而是接口口径：余额要配「余额密钥」（控制台 JWT），
     * 或点「账号登录」由内嵌浏览器自动截获。</p>
     *
     * @param message 原始异常消息
     * @return 可读的错误提示
     */
    private static String friendlyError(String message) {
        if (message != null && (message.contains("auth_type_disabled")
                || message.contains("Authentication type 'apikey' is not enabled"))) {
            return "商汤余额接口不接受 API Key（sk-），请在「余额密钥」填控制台 JWT"
                    + "（登录 platform.sensenova.cn 后获取，约 180 分钟有效），或点「账号登录」自动截获";
        }
        return message;
    }

    /**
     * 显式表达式解析。
     *
     * @param expression 表达式
     * @param raw        原始响应
     * @param billing    计费币种
     * @return 结果
     */
    private CreditResult parse(String expression, String raw, String billing) {
        CreditResult result = CreditExpressions.parse(expression, raw, name(), billing);
        if (result.isOk()) {
            result.setSource(name());
        }
        return result;
    }

    /**
     * 按资源池结构解析：每个池带 5 小时 / 7 天两个滚动窗口与赠送余额。
     *
     * <p>除扁平读数 {@code parts} 外，把套餐与资源池的原始结构放进
     * {@link CreditResult#getExtra()}（plan / pools / unit），前端据此按
     * SenseNova 的窗口口径渲染专属余额视图；{@code raw} 有截断，结构化
     * 数据以 extra 为准。包可见以便单测直接喂样例、不走网络。</p>
     *
     * @param raw 原始响应
     * @return 结果
     */
    static CreditResult parsePools(String raw) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            return CreditResult.fail("sensenova", "响应不是合法 JSON: " + e.getMessage());
        }
        JsonNode pools = root.path("pools");
        if (!pools.isArray() || pools.isEmpty()) {
            return CreditResult.fail("sensenova", "未识别的余额响应结构，请改用「自定义映射」指定字段");
        }
        List<BalancePart> parts = new ArrayList<>();
        List<Map<String, Object>> poolViews = new ArrayList<>();
        for (JsonNode pool : pools) {
            String label = poolName(pool);
            appendWindow(parts, label, "7天", pool.path("window_7d"));
            appendWindow(parts, label, "5小时", pool.path("window_5h"));
            appendPart(parts, label + " 赠送余额", pool.path("grant_balance"));
            poolViews.add(MAPPER.convertValue(pool, MAP_TYPE));
        }
        if (parts.isEmpty()) {
            return CreditResult.fail("sensenova", "响应里没有可读的用量数值");
        }
        CreditResult result = CreditResult.ok("sensenova", parts, raw);
        Map<String, Object> extra = new LinkedHashMap<>();
        if (root.path("plan").isObject()) {
            extra.put("plan", MAPPER.convertValue(root.path("plan"), MAP_TYPE));
        }
        extra.put("pools", poolViews);
        extra.put("unit", TOKEN_UNIT);
        result.setExtra(extra);
        return result;
    }

    /**
     * 追加一个窗口的剩余 / 已用两段读数。
     *
     * @param parts  读数列表
     * @param pool   池名
     * @param window 窗口展示名（7天 / 5小时）
     * @param node   窗口节点（limit / used / remaining）
     */
    private static void appendWindow(List<BalancePart> parts, String pool, String window, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        appendPart(parts, pool + " " + window + "剩余", node.path("remaining"));
        appendPart(parts, pool + " " + window + "已用", node.path("used"));
    }

    /**
     * 追加一段读数；数值取不到时跳过。
     *
     * @param parts 读数列表
     * @param label 段名
     * @param node  数值节点
     */
    private static void appendPart(List<BalancePart> parts, String label, JsonNode node) {
        BigDecimal value = decimalOf(node);
        if (value != null) {
            BalancePart part = new BalancePart();
            part.setLabel(label);
            part.setAmount(value);
            part.setCurrency(TOKEN_UNIT);
            parts.add(part);
        }
    }

    /**
     * 池名：优先 name，其次 id，都取不到用兜底名。
     *
     * @param pool 池节点
     * @return 池名
     */
    private static String poolName(JsonNode pool) {
        String name = pool.path("name").asText("").trim();
        if (name.isEmpty()) {
            name = pool.path("id").asText("").trim();
        }
        return name.isEmpty() ? FALLBACK_POOL_NAME : name;
    }

    /**
     * 取节点数值；上游把 token 数编码成字符串，这里统一转成数值。
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
     * 空判断。
     *
     * @param text 文本
     * @return 为空为 true
     */
    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
