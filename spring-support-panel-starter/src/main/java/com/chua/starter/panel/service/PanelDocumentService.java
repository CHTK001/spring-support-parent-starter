package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelDatabaseDocumentView;

/**
 * 面板文档服务。
 */
public interface PanelDocumentService {

    String buildJdbcTableDocument(String connectionId, String catalog, String schema, String tableName);

    PanelDatabaseDocumentView buildJdbcDatabaseDocument(String connectionId, String catalog);
}
