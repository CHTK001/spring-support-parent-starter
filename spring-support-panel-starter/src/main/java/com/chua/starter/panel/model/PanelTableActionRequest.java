package com.chua.starter.panel.model;

import lombok.Data;

/**
 * 面板表动作请求。
 */
@Data
public class PanelTableActionRequest {

    private String panelCatalogName;
    private String panelSchemaName;
    private String panelTableName;
    private String panelActionType;
    private String panelBackupTableName;
}
