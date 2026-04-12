package com.chua.starter.panel.model;

import lombok.Data;

/**
 * 面板 SQL 模板请求。
 */
@Data
public class PanelSqlTemplateRequest {

    private String panelConnectionId;
    private String panelCatalogName;
    private String panelSchemaName;
    private String panelTableName;
    private String panelActionType;
    private Integer panelPreviewLimit;
    private String panelBackupTableName;
}
