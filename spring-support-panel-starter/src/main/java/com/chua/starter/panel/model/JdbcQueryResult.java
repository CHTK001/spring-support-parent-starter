package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * JDBC 查询结果。
 */
@Data
@Builder
public class JdbcQueryResult {

    private List<String> columns;
    private List<Map<String, Object>> rows;
    private long affectedRows;
    private boolean query;
}
