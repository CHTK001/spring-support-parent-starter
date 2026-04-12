package com.chua.starter.panel.service;

/**
 * 面板文档服务。
 */
public interface PanelDocumentService {

    String buildJdbcTableDocument(String connectionId, String catalog, String schema, String tableName);
}
