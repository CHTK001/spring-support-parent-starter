package com.chua.starter.datasearch.credit.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 余额查询结果。
 *
 * <p>失败不抛异常：{@link #error} 有值即表示这次没查到，页面按原文提示即可。
 * 这样「N 个上游逐个查余额」不会因为某一家超时把整页带崩。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Data
public class CreditResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 原始响应保留长度上限，避免整页响应把大 JSON 带向前端
     */
    private static final int RAW_LIMIT = 2000;

    /**
     * 主余额（取 {@link #parts} 第一段）
     */
    private BigDecimal amount;

    /**
     * 币种（USD / CNY），百分比口径为 {@code %}
     */
    private String currency;

    /**
     * 命中的实现标识
     */
    private String source;

    /**
     * 多段读数
     */
    private List<BalancePart> parts = new ArrayList<>();

    /**
     * 原始响应（截断）
     */
    private String raw;

    /**
     * 失败原因；为空表示查询成功
     */
    private String error;

    /**
     * 是否查到了数。
     *
     * @return 有主余额且无错误时为 true
     */
    public boolean isOk() {
        return error == null && amount != null;
    }

    /**
     * 构造成功结果。
     *
     * @param source 实现标识
     * @param parts  多段读数（至少一段）
     * @param raw    原始响应
     * @return 结果
     */
    public static CreditResult ok(String source, List<BalancePart> parts, String raw) {
        CreditResult result = new CreditResult();
        result.source = source;
        result.parts = parts;
        result.raw = truncate(raw);
        if (parts != null && !parts.isEmpty()) {
            BalancePart first = parts.get(0);
            result.amount = first.getAmount();
            result.currency = first.getCurrency();
        }
        return result;
    }

    /**
     * 构造失败结果。
     *
     * @param source 实现标识
     * @param error  失败原因
     * @return 结果
     */
    public static CreditResult fail(String source, String error) {
        CreditResult result = new CreditResult();
        result.source = source;
        result.error = error;
        return result;
    }

    /**
     * 截断原始响应。
     *
     * @param raw 原始响应
     * @return 截断后的响应
     */
    public static String truncate(String raw) {
        if (raw == null || raw.length() <= RAW_LIMIT) {
            return raw;
        }
        return raw.substring(0, RAW_LIMIT) + "...";
    }
}
