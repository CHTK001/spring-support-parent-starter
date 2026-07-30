package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelAiSqlRequest;
import com.chua.starter.panel.model.PanelAiMockDataRequest;

import java.util.List;
import java.util.Map;

/**
 * 面板 AI 服务。
 *
 * @author CH
 */
public interface PanelAiService {

    /**
     * 解释指定表结构。
     *
     * @param connectionId 连接标识
     * @param catalog catalog 名称
     * @param schema schema 名称
     * @param tableName 表名
     * @return 解释文本
     */
    String explainJdbcStructure(String connectionId, String catalog, String schema, String tableName);

    /**
     * 解释 SQL 语句。
     *
     * @param connectionId 连接标识
     * @param sql SQL 语句
     * @return 解释文本
     */
    String explainSql(String connectionId, String sql);

    /**
     * 根据自然语言生成 SQL。
     *
     * @param connectionId 连接标识
     * @param request 生成参数
     * @return SQL 文本
     */
    String generateSql(String connectionId, PanelAiSqlRequest request);

    /**
     * 生成模拟数据。
     *
     * @param connectionId 连接标识
     * @param request 生成参数
     * @return 模拟数据列表
     */
    List<Map<String, Object>> generateMockData(String connectionId, PanelAiMockDataRequest request);
}
