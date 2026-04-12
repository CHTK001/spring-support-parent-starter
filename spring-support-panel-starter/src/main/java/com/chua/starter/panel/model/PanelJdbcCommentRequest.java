package com.chua.starter.panel.model;

import lombok.Data;

/**
 * JDBC 注释更新请求。
 */
@Data
public class PanelJdbcCommentRequest {

    private String panelCatalogName;
    private String panelSchemaName;
    private String panelTableName;
    private String panelColumnName;
    private String panelCommentContent;
}
