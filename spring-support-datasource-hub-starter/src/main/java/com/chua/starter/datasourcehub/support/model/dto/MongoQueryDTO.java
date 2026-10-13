package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * MongoDB 查询请求
 *
 * <p>{@code expression} 为 Mongo Shell 只读表达式（{@code db.<集合>.find(...)} 及其链式写法），
 * {@code database} 为 shell 里 {@code db} 所指的当前逻辑库；两者缺省时分别按空与数据源默认库处理。</p>
 *
 * @param expression   shell 表达式
 * @param database     当前逻辑库名，为空用数据源默认库
 * @param datasourceId 数据源 ID，非空时按库中配置执行
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MongoQueryDTO(
        String expression,
        String database,
        Long datasourceId
) {
}
