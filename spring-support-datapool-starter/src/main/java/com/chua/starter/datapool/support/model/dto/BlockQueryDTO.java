package com.chua.starter.datapool.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 大屏区块受限 SQL 查询请求。
 *
 * <p>自定义大屏的 sql 区块通过 {@code POST /ms/datasource/block-query} 取数，
 * 入参只收数据源 ID 与只读 SQL，不暴露任何连接信息。</p>
 *
 * @param datasourceId 数据源 ID；null 或 0 表示应用默认主数据源
 * @param sql          只读查询 SQL，仅允许 SELECT/WITH/SHOW/EXPLAIN，尾部分号可省略
 * @author CH
 * @since 4.0.0.51
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BlockQueryDTO(
        /** 数据源 ID；null 或 0 表示应用默认主数据源 */
        Long datasourceId,
        /** 只读查询 SQL */
        String sql
) {
}
