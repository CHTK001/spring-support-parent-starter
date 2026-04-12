package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelAiSqlRequest;

/**
 * 面板 AI 服务。
 */
public interface PanelAiService {

    String explainJdbcStructure(String connectionId, String catalog, String schema, String tableName);

    String explainSql(String connectionId, String sql);

    String generateSql(String connectionId, PanelAiSqlRequest request);
}
