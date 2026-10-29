package com.chua.starter.datasourcehub.support.dynamicapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 动态接口参数定义（单个）。
 *
 * <p>对应「接口管理 → 参数」页签里的一行参数：定义后可在 SQL 中以
 * {@code ${name}} 占位符引用（变量名即 {@code name}），执行时经
 * {@code SqlParameterBinder#substitute} 转成**字面量**内联，不带注入面。</p>
 *
 * @param name     参数名（SQL 占位符名，字母/数字/下划线）
 * @param type     期望类型提示（text/number/date/boolean；仅用于前端编辑与文档）
 * @param required 是否必填
 * @param example  示例值（文档用途）
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiParamDef(
        String name,
        String type,
        Boolean required,
        String example
) {
}