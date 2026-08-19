package com.chua.common.support.data.materialized;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * 物理化 SQL 请求。
 *
 * @since 2026/4/2
 * @author CH
 */
@Data
@Builder(toBuilder = true)
public class MaterializedSqlRequest {

    /** StatementID */
    private String statementId;
    /** 来源数据来源 */
    private String sourceDataSource;
    /** SQL */
    private String sql;
    /** 命令类型 */
    private MaterializedSqlCommandType commandType;
    /** Definition */
    private MaterializedRouteDefinition definition;

    @Singular("table")
    /** Tables */
    private List<String> tables;

    @Singular("parameter")
    /** Parameters */
    private List<Object> parameters;
}
