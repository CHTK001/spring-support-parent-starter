package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * JDBC 表结构。
 */
@Data
@Builder
public class JdbcTableStructure {

    private String catalogName;
    private String schemaName;
    private String tableName;
    private String tableComment;
    private List<Map<String, Object>> columns;
    private List<Map<String, Object>> indexes;
    private List<String> primaryKeys;
}
