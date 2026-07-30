package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelDatabaseDocumentView;

/**
 * 面板文档服务。
 *
 * @author CH
 */
public interface PanelDocumentService {

    /**
     * 生成数据表结构文档。
     *
     * @param connectionId 连接标识
     * @param catalog catalog 名称
     * @param schema schema 名称
     * @param tableName 表名
     * @return 文档内容
     */
    String buildJdbcTableDocument(String connectionId, String catalog, String schema, String tableName);

    /**
     * 生成数据库文档。
     *
     * @param connectionId 连接标识
     * @param catalog catalog 名称
     * @return 数据库文档视图
     */
    PanelDatabaseDocumentView buildJdbcDatabaseDocument(String connectionId, String catalog);
}
