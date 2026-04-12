package com.chua.starter.panel.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 面板表保存请求。
 */
@Data
public class PanelTableSaveRequest {

    private String panelCatalogName;
    private String panelSchemaName;
    private String panelTableName;
    private List<PanelTableRowUpdate> panelUpdates = new ArrayList<>();
}
