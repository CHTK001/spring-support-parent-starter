package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;

/**
 * new-api / one-api 系中转站的余额实现。
 *
 * <p>这类站点统一暴露 {@code GET /api/usage/token}，返回
 * {@code data.total_granted / data.total_used / data.total_available}，
 * 单位是站点额度（{@code 500000} 额度 = 1 美元），所以表达式默认做除法换算。</p>
 *
 * <p>令牌走 {@code Authorization: Bearer sk-xxx}。地址只给站点根地址即可，
 * 末尾的 {@code /v1}、{@code /api} 会被去掉再拼接口路径。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi("new-api")
public class NewApiCreditQueryProvider implements CreditQueryProvider {

    /**
     * 站点根地址里需要剥掉的后缀
     */
    private static final String[] BASE_SUFFIX = {"/api/usage", "/api", "/v1"};

    /**
     * 余额接口路径
     */
    private static final String USAGE_PATH = "/api/usage/token";

    /**
     * 额度换算：500000 额度 = 1 美元
     */
    private static final String DEFAULT_EXPRESSION =
            "$data.total_available / 500000; $data.total_used / 500000";

    @Override
    public String name() {
        return "new-api";
    }

    /**
     * 按 new-api 口径查询余额。
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String url = CreditHttp.join(base(query.getUrl()), USAGE_PATH);
            String raw = CreditHttp.fetch(query, url);
            String expression = blank(query.getExpression()) ? DEFAULT_EXPRESSION : query.getExpression();
            CreditResult result = CreditExpressions.parse(expression, raw, name(), query.getBilling());
            if (result.isOk()) {
                result.setSource(name());
            }
            return result;
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 剥掉站点根地址上多余的后缀。
     *
     * @param url 原始地址
     * @return 站点根地址
     */
    private static String base(String url) {
        if (url == null) {
            return null;
        }
        String text = url.trim();
        for (String suffix : BASE_SUFFIX) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length());
                break;
            }
        }
        return text;
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
