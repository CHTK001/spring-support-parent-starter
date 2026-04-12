package com.chua.starter.panel.model;

import lombok.Data;

/**
 * 面板备注写入请求。
 */
@Data
public class PanelRemarkRequest {

    private String panelConnectionId;
    private String panelNodeType;
    private String panelCatalogName;
    private String panelSchemaName;
    private String panelTableName;
    private String panelColumnName;
    private String panelRemarkContent;
}
