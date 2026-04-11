package com.chua.common.support.data.materialized;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * SQL 物理化请求。
 *
 * @author CH
 * @since 2026/4/2
 */
@Value
@Builder
public class MaterializedSqlRequest {

    String statementId;

    String sourceDataSource;

    String sql;

    MaterializedSqlCommandType commandType;

    MaterializedRouteDefinition definition;

    @Singular("table")
    List<String> tables;

    @Singular("parameter")
    List<Object> parameters;
}
