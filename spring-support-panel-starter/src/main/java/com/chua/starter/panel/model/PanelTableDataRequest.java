package com.chua.starter.panel.model;

import lombok.Data;

/**
 * 面板表数据请求。
 */
@Data
public class PanelTableDataRequest {

    private String panelCatalogName;
    private String panelSchemaName;
    private String panelTableName;
    private long panelPageNum = 1;
    private long panelPageSize = 100;
    private boolean panelLoadTotal;
    private String panelSortField;
    private String panelSortOrder;
}
