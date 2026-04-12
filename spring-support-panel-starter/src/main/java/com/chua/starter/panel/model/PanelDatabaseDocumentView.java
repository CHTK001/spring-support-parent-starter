package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 面板数据库文档视图。
 */
@Data
@Builder
public class PanelDatabaseDocumentView {

    private String panelCatalogName;
    private long panelSchemaCount;
    private long panelTableCount;
    private String panelGeneratedAt;
    private List<PanelDatabaseTableDocumentView> panelTables;
}
