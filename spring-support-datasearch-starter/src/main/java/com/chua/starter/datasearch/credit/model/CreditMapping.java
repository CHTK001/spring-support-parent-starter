package com.chua.starter.datasearch.credit.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 自定义余额映射的一行：后端需要的字段 → 取值表达式。
 *
 * <p>页面「自定义映射」把上游响应里的位置映射到后端要的字段上：
 * 左侧是字段名（{@code remain / used / currency}），右侧是表达式，
 * 支持 JSONPath（{@code $.data.balance}）与 XPath（{@code //balance/text()}）。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Data
public class CreditMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 后端字段：remain（剩余）/ used（已用）/ currency（币种）
     */
    private String field;

    /**
     * 取值表达式：{@code $} 开头按 JSONPath 解析，{@code /} 开头按 XPath 解析
     */
    private String expression;
}
