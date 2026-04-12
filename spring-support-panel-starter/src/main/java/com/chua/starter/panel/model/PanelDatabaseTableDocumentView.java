package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 面板数据库表文档视图。
 */
@Data
@Builder
public class PanelDatabaseTableDocumentView {

    private String panelCatalogName;
    private String panelSchemaName;
    private String panelTableName;
    private String panelTableComment;
    private List<String> panelPrimaryKeys;
    private List<Map<String, Object>> panelColumns;
    private List<Map<String, Object>> panelIndexes;
}
