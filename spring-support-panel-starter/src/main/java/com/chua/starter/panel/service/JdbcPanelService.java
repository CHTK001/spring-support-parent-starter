package com.chua.starter.panel.service;

import com.chua.starter.panel.model.JdbcCatalogNode;
import com.chua.starter.panel.model.JdbcConnectionMetadata;
import com.chua.starter.panel.model.JdbcQueryResult;
import com.chua.starter.panel.model.JdbcTableStructure;
import com.chua.starter.panel.model.PanelCapabilitySnapshot;

import java.util.List;

/**
 * JDBC 面板服务。
 */
public interface JdbcPanelService {

    List<JdbcCatalogNode> listCatalogTree(String connectionId);

    List<JdbcCatalogNode> search(String connectionId, String keyword);

    JdbcTableStructure tableStructure(String connectionId, String catalog, String schema, String tableName);

    JdbcQueryResult execute(String connectionId, String sql);

    JdbcConnectionMetadata metadata(String connectionId);

    PanelCapabilitySnapshot capabilities(String connectionId);
}
