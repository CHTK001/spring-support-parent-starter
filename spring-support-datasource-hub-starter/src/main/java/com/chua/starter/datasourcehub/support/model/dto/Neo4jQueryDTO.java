package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Neo4j Cypher 查询请求
 *
 * @param cypher       Cypher 语句，原样提交给 Neo4j
 * @param datasourceId 数据源 ID，为空时取第一个 neo4j 类型数据源
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Neo4jQueryDTO(
        String cypher,
        Long datasourceId
) {
}
