package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;

/**
 * OpenAI 官方额度接口。
 *
 * <p>{@code GET https://api.openai.com/v1/dashboard/billing/credit_grants} 返回
 * {@code total_granted / total_used / total_available}，金额单位是美元。</p>
 *
 * <p>注意：该接口只认「账户级 key」（{@code sk-...} 里带 dashboard 权限的那种），
 * 普通项目 key 会 401；OpenAI 已逐步收紧这条路径，查不到时页面会显示原始错误，
 * 换了计费口径请改用通用实现自填地址。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi("openai")
public class OpenAiCreditQueryProvider implements CreditQueryProvider {

    /**
     * 默认地址
     */
    private static final String DEFAULT_URL = "https://api.openai.com/v1/dashboard/billing/credit_grants";

    /**
     * 默认表达式
     */
    private static final String DEFAULT_EXPRESSION = "$total_available; $total_used";

    @Override
    public String name() {
        return "openai";
    }

    /**
     * 按 OpenAI 口径查询余额。
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String url = query.getUrl() == null || query.getUrl().isBlank() ? DEFAULT_URL : query.getUrl();
            String raw = CreditHttp.fetch(query, url);
            String expression = query.getExpression() == null || query.getExpression().isBlank()
                    ? DEFAULT_EXPRESSION : query.getExpression();
            return CreditExpressions.parse(expression, raw, name(), query.getBilling());
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }
}
