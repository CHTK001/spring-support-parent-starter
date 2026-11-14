package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;

/**
 * 通用余额实现：地址 + 方法 + 令牌 + 字段表达式，全部由调用方给出。
 *
 * <p>没有专用实现的上游（自建中转、冷门厂商）都走这里：只要能配出「查哪个地址」和
 * 「余额在哪个字段」，就能出数，不用为此加代码。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi("expression")
public class ExpressionCreditQueryProvider implements CreditQueryProvider {

    @Override
    public String name() {
        return "expression";
    }

    /**
     * 显式指定 expression，或未指定实现但表达式与地址都齐全时归本实现。
     *
     * @param query 请求
     * @return 能处理时为 true
     */
    @Override
    public boolean supports(CreditQuery query) {
        if (query == null) {
            return false;
        }
        if (name().equalsIgnoreCase(query.getKind())) {
            return true;
        }
        boolean kindBlank = query.getKind() == null || query.getKind().isBlank();
        return kindBlank && notBlank(query.getExpression()) && notBlank(query.getUrl());
    }

    /**
     * 按表达式查询余额。
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String raw = CreditHttp.fetch(query, query.getUrl());
            return CreditExpressions.parse(query.getExpression(), raw, name(), query.getBilling());
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 非空判断。
     *
     * @param text 文本
     * @return 非空为 true
     */
    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }
}
