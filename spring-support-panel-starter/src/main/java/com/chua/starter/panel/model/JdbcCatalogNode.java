package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * JDBC 对象树节点。
 */
@Data
@Builder
public class JdbcCatalogNode {

    private String nodeId;
    private String parentId;
    private String nodeType;
    private String nodeName;
    private String description;
    private String catalogName;
    private String schemaName;
    private String tableName;
    private List<JdbcCatalogNode> children;
}
