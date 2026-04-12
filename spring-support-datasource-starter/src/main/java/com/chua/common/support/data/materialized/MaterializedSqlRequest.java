package com.chua.common.support.data.materialized;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * 物理化 SQL 请求。
 *
 * @author CH
 * @since 2026/4/2
 */
@Data
@Builder(toBuilder = true)
public class MaterializedSqlRequest {

    private String statementId;
    private String sourceDataSource;
    private String sql;
    private MaterializedSqlCommandType commandType;
    private MaterializedRouteDefinition definition;

    @Singular("table")
    private List<String> tables;

    @Singular("parameter")
    private List<Object> parameters;
}
