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

import java.util.List;
import java.util.Map;

/**
 * DeepSeek 平台门户余额实现（platform.deepseek.com 用户概要接口）。
 *
 * <p>对接 {@code GET https://platform.deepseek.com/api/v0/users/get_user_summary}，
 * 鉴权为标准 {@code Authorization: Bearer <令牌>}。注意这里的令牌是<b>门户会话令牌</b>
 * （用户在 platform.deepseek.com 登录后页面持有的凭据，含 {@code /} 等字符），不是
 * {@code api.deepseek.com} 的 {@code sk-} API Key：两者不同源，门户接口不认 API Key，
 * API 余额接口（{@code /user/balance}，见 host 登记表）也不认会话令牌。会话令牌可由
 * 页面上的「账号登录」流程自动截获（见 {@link #loginPageUrl()}）。</p>
 *
 * <p>响应为 {@code data.biz_data} 双层包裹结构（2026-11 社区实证）：
 * {@code normal_wallets}（充值余额）、{@code bonus_wallets}（赠送余额）、
 * {@code total_costs}（累计消费）各是 {@code [{currency, balance/amount}]} 数组，
 * 金额为十进制字符串（如 {@code "16.8287497600000000"}）。读数行为：
 * parts[0]=充值余额（剩余）、parts[1]=赠送余额，累计消费等其余读数进前端指标格
 * 原样展示。多币种账户与未公开字段请改走「自定义映射」指定表达式。</p>
 *
 * <p>令牌失效时上游返回 {@code code=40003}（{@code data=null}），这里把错误码与
 * 原因透传给页面，而不是报「未识别的响应结构」。</p>
 *
 * @author CH
 * @since 4.0.0.59
 */
@Spi("deepseek")
public class DeepSeekCreditQueryProvider implements CreditQueryProvider {

    /**
     * 平台门户余额接口默认地址
     */
    private static final String DEFAULT_URL = "https://platform.deepseek.com/api/v0/users/get_user_summary";

    /**
     * 门户登录页地址：页面上的「账号登录」按钮据此打开内置浏览器
     */
    private static final String LOGIN_PAGE_URL = "https://platform.deepseek.com/sign_in";

    /**
     * 登录成功后要截获的响应地址特征：密码 / 短信验证码登录（{@code users/login}、
     * {@code users/login_by_mobile_sms}）与 OAuth 令牌交换（{@code users/oauth/get_token}，
     * 微信 / Apple 扫码）。实测 2026-11 页面抓包：密码登录请求的是
     * {@code POST auth-api/v0/users/login}，令牌并不走 OAuth 端点
     */
    private static final String LOGIN_CAPTURE_URL_PATTERN = "auth-api/v0/users/(oauth/get_token|login)";

    /**
     * 令牌在登录响应体里的 JSON 路径（与 Moss 的 Cookie 模式、Agnes 的
     * access_token 模式都不同）：密码 / 短信登录的令牌在 {@code data.biz_data.user.token}
     * （页面归一化要求 user 必须带 token 与 id）；OAuth 令牌交换的令牌在
     * {@code data.biz_data.token}，由会话管理器的递归兜底逻辑覆盖
     */
    private static final String LOGIN_TOKEN_JSON_PATH = "data.biz_data.user.token";

    /**
     * 按常见字段名依次试探；显式配了表达式时以表达式为准。
     * 首选实测结构（2026-11 验证）：一次带出 充值余额 / 赠送余额 / 累计消费，
     * 前一段算剩余、第二段算赠送，其余读数按原样展示；旧版扁平结构留作兜底
     */
    private static final List<String> DEFAULT_EXPRESSIONS = List.of(
            "¥data.biz_data.normal_wallets.0.balance; ¥data.biz_data.bonus_wallets.0.balance;"
                    + " ¥data.biz_data.total_costs.0.amount",
            "¥data.biz_data.normal_wallets.0.balance; ¥data.biz_data.bonus_wallets.0.balance",
            "¥data.biz_data.normal_wallets.0.balance",
            "¥data.normal_wallets.0.balance; ¥data.bonus_wallets.0.balance; ¥data.total_costs.0.amount",
            "¥data.total_balance; ¥data.granted_balance; ¥data.topped_up_balance",
            "¥data.balance",
            "¥balance_infos.0.total_balance; ¥balance_infos.0.granted_balance"
    );

    /**
     * 旧版扁平字段名 → 页面展示名：键是字段路径的最后一段。
     * 钱包数组字段（normal_wallets / bonus_wallets / total_costs）在
     * {@link #friendlyLabel(String)} 里按路径片段识别，不在这张表里
     */
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "total_balance", "余额",
            "balance", "余额",
            "available_balance", "可用余额",
            "granted_balance", "赠送余额",
            "topped_up_balance", "充值余额");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "deepseek";
    }

    /**
     * 「账号登录」入口页：用户在真实登录页登录后，由内嵌浏览器（CDP）监听
     * 登录 / OAuth 令牌交换响应（见 {@link #loginCaptureUrlPattern()}）并回填令牌。
     *
     * @return DeepSeek 门户登录页
     */
    @Override
    public String loginPageUrl() {
        return LOGIN_PAGE_URL;
    }

    /**
     * 登录响应地址特征：只截获发令牌的那个接口，不收整站流量。
     *
     * @return 地址特征正则
     */
    @Override
    public String loginCaptureUrlPattern() {
        return LOGIN_CAPTURE_URL_PATTERN;
    }

    /**
     * 令牌在登录响应体里的位置：{@code data.biz_data.user.token}。
     *
     * @return 令牌 JSON 路径
     */
    @Override
    public String loginTokenJsonPath() {
        return LOGIN_TOKEN_JSON_PATH;
    }

    /**
     * 按 DeepSeek 平台口径查询余额。
     *
     * <p>鉴权走标准 Bearer：{@code CreditHttp} 对空请求头默认补
     * {@code Authorization: Bearer <令牌>}，调用方传来的令牌是门户会话令牌原值。</p>
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String url = blank(query.getUrl()) ? DEFAULT_URL : query.getUrl().trim();
            String raw = CreditHttp.fetch(query, url);
            String envelopeError = envelopeError(raw);
            if (envelopeError != null) {
                return CreditResult.fail(name(), envelopeError);
            }
            if (!blank(query.getExpression())) {
                return translate(parse(query.getExpression(), raw, query.getBilling()));
            }
            return translate(probeDefaults(raw, query.getBilling()));
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
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
     * 响应结构未公开文档化：按常见字段名逐个试探，第一条能解析的胜出。
     *
     * @param raw     原始响应
     * @param billing 计费币种
     * @return 结果
     */
    private CreditResult probeDefaults(String raw, String billing) {
        String lastError = null;
        for (String candidate : DEFAULT_EXPRESSIONS) {
            CreditResult result = CreditExpressions.parse(candidate, raw, name(), billing);
            if (result.isOk()) {
                result.setSource(name());
                return result;
            }
            lastError = result.getError();
        }
        return CreditResult.fail(name(), "未识别的余额响应结构，请改用「自定义映射」指定字段"
                + (lastError == null ? "" : "；" + lastError));
    }

    /**
     * 识别上游的业务错误信封：{@code code} 非 0 且 {@code data} 为空时直接把
     * 错误码与原因带回去（典型如令牌失效的 40003），页面能给出准确提示。
     *
     * @param raw 原始响应
     * @return 错误描述；不是业务错误为 null
     */
    private static String envelopeError(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode code = root.path("code");
            if (!root.has("data") || root.path("data").isNull() || root.path("data").isMissingNode()) {
                if (code.isInt() && code.asInt() != 0) {
                    String msg = root.path("msg").asText("");
                    return "上游返回错误 code=" + code.asInt() + (msg.isBlank() ? "" : "：" + msg);
                }
            }
        } catch (Exception ignored) {
            // 不是 JSON 或无信封结构时交回探测逻辑处理
        }
        return null;
    }

    /**
     * 把 DeepSeek 字段名翻成页面展示名。
     *
     * <p>就地改写标签：钱包数组字段按路径片段识别（数组的下标段会让「最后一段」
     * 都变成 balance / amount，必须看全路径），认不出的字段原样保留，
     * 不影响用户自己写的表达式口径。</p>
     *
     * @param result 查询结果
     * @return 同一结果
     */
    private static CreditResult translate(CreditResult result) {
        if (result == null || !result.isOk() || result.getParts() == null) {
            return result;
        }
        for (BalancePart part : result.getParts()) {
            String label = part.getLabel();
            if (label == null || label.isBlank()) {
                continue;
            }
            part.setLabel(friendlyLabel(label));
        }
        return result;
    }

    /**
     * 单个字段路径 → 展示名。
     *
     * @param label 表达式走出来的字段路径
     * @return 展示名；认不出时原样返回
     */
    private static String friendlyLabel(String label) {
        if (label.contains("normal_wallets")) {
            return "充值余额";
        }
        if (label.contains("bonus_wallets")) {
            return "赠送余额";
        }
        if (label.contains("total_costs")) {
            return "累计消费";
        }
        String tail = label.substring(label.lastIndexOf('.') + 1);
        String friendly = FIELD_LABELS.get(tail);
        return friendly != null ? friendly : label;
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
