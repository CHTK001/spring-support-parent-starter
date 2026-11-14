package com.chua.starter.datasearch.credit.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 余额的一段读数。
 *
 * <p>同一份响应里常有多段不同口径的钱（可用 / 已用 / 赠送），表达式用 {@code ;} 分隔即可
 * 拿到多段，前端按 {@link #label} 逐行展示。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Data
public class BalancePart implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 段名（默认为表达式里的字段路径，如 {@code data.total_available}）
     */
    private String label;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 币种（USD / CNY）或单位（% 表示百分比口径）
     */
    private String currency;
}
