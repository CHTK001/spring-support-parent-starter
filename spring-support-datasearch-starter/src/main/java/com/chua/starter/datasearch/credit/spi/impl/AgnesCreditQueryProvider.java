package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;

import java.util.List;
import java.util.Map;

/**
 * Agnes 余额实现（platform-backend.agnes-ai.com 用户余额接口）。
 *
 * <p>对接 {@code GET https://platform-backend.agnes-ai.com/api/user/balance}，
 * 鉴权为标准 {@code Authorization: Bearer <令牌>}，令牌即「余额密钥」里填的值
 * （可由页面上的「账号登录」流程自动获取，见 {@link #loginPageUrl()}）。</p>
 *
 * <p>响应为 {@code data} 包裹结构（2026-11 实测）：金额字段以 {@code *_minor} 命名，
 * 单位按美元处理（如 {@code available_total_minor: 0.396} 即 $0.396）。Agnes 不提供
 * 「已用」数据源，读数行为：parts[0]=可用余额、parts[1]=冻结，其余读数
 * （充值余额 / 赠送余额等）进前端指标格原样展示。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Spi("agnes")
public class AgnesCreditQueryProvider implements CreditQueryProvider {

    /**
     * 官方余额接口默认地址
     */
    private static final String DEFAULT_URL = "https://platform-backend.agnes-ai.com/api/user/balance";

    /**
     * 登录页地址：页面上的「账号登录」按钮据此打开内置浏览器
     */
    private static final String LOGIN_PAGE_URL = "https://platform.agnes-ai.com/login";

    /**
     * 按常见字段名依次试探；显式配了表达式时以表达式为准。
     * 首选实测字段（2026-11 验证）：一次带出 可用余额 / 冻结 / 充值 / 赠送，
     * 前一段算剩余、第二段算冻结，其余读数按原样展示；data.balance 留作兜底
     */
    private static final List<String> DEFAULT_EXPRESSIONS = List.of(
            "$data.available_total_minor; $data.frozen_topup_minor; $data.frozen_bonus_minor;"
                    + " $data.topup_balance_minor; $data.bonus_balance_minor",
            "$data.available_total_minor",
            "$data.total_minor",
            "$data.available_topup_minor; $data.available_bonus_minor",
            "data.balance",
            "data.total_balance"
    );

    /**
     * Agnes 字段名 → 页面展示名：读数行直接给中文标签，前端不必再猜字段含义。
     * 键是字段路径的最后一段，默认探测与自定义表达式共用
     */
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("available_total_minor", "可用余额"),
            Map.entry("total_minor", "总余额"),
            Map.entry("available_topup_minor", "可用充值"),
            Map.entry("available_bonus_minor", "可用赠送"),
            Map.entry("topup_balance_minor", "充值余额"),
            Map.entry("bonus_balance_minor", "赠送余额"),
            Map.entry("frozen_topup_minor", "冻结充值"),
            Map.entry("frozen_bonus_minor", "冻结赠送"));

    @Override
    public String name() {
        return "agnes";
    }

    @Override
    public String loginPageUrl() {
        return LOGIN_PAGE_URL;
    }

    /**
     * 按 Agnes 平台口径查询余额。
     *
     * <p>鉴权走标准 Bearer：{@code CreditHttp} 对空请求头默认补
     * {@code Authorization: Bearer <令牌>}，无需像 Moss 那样改写令牌。</p>
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
     * 把 Agnes 字段名翻成页面展示名。
     *
     * <p>就地改写标签：认不出的字段原样保留，不影响用户自己写的表达式口径。</p>
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
            String friendly = FIELD_LABELS.get(label.substring(label.lastIndexOf('.') + 1));
            if (friendly != null) {
                part.setLabel(friendly);
            }
        }
        return result;
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
