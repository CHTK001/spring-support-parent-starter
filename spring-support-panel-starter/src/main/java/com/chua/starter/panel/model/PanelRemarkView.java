package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

/**
 * 面板备注视图。
 */
@Data
@Builder
public class PanelRemarkView {

    private String panelRemarkKey;
    private String panelConnectionId;
    private String panelNodeType;
    private String panelCatalogName;
    private String panelSchemaName;
    private String panelTableName;
    private String panelColumnName;
    private String panelRemarkContent;
}
