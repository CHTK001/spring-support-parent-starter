package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;

/**
 * sub2api 系中转站的余额实现。
 *
 * <p>接口为 {@code GET /v1/usage}，令牌走 {@code Authorization: Bearer sk-xxx}。
 * 不同分支返回结构不完全一致，默认表达式按「顶层 remaining → data.remaining」依次尝试，
 * 取不到的那一段会被跳过，不会整体失败；站点返回口径不同时可在页面上改表达式覆盖。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi("sub2api")
public class Sub2ApiCreditQueryProvider implements CreditQueryProvider {

    /**
     * 余额接口路径
     */
    private static final String USAGE_PATH = "/v1/usage";

    /**
     * 默认表达式：两种常见结构依次尝试
     */
    private static final String DEFAULT_EXPRESSION = "$remaining; $data.remaining";

    @Override
    public String name() {
        return "sub2api";
    }

    /**
     * 按 sub2api 口径查询余额。
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String base = stripSuffix(query.getUrl());
            String url = CreditHttp.join(base, USAGE_PATH);
            String raw = CreditHttp.fetch(query, url);
            String expression = query.getExpression() == null || query.getExpression().isBlank()
                    ? DEFAULT_EXPRESSION : query.getExpression();
            return CreditExpressions.parse(expression, raw, name(), query.getBilling());
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 地址已带 /v1/usage 时原样使用，否则剥掉 /v1 后缀再拼。
     *
     * @param url 原始地址
     * @return 站点根地址
     */
    private static String stripSuffix(String url) {
        if (url == null) {
            return null;
        }
        String text = url.trim();
        if (text.endsWith(USAGE_PATH)) {
            return text.substring(0, text.length() - USAGE_PATH.length());
        }
        if (text.endsWith("/v1")) {
            return text.substring(0, text.length() - "/v1".length());
        }
        return text;
    }
}
