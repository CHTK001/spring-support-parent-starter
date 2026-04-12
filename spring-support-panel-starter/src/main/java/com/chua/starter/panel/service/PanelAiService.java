package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelAiSqlRequest;
import com.chua.starter.panel.model.PanelAiMockDataRequest;

import java.util.List;
import java.util.Map;

/**
 * 面板 AI 服务。
 */
public interface PanelAiService {

    String explainJdbcStructure(String connectionId, String catalog, String schema, String tableName);

    String explainSql(String connectionId, String sql);

    String generateSql(String connectionId, PanelAiSqlRequest request);

    List<Map<String, Object>> generateMockData(String connectionId, PanelAiMockDataRequest request);
}
