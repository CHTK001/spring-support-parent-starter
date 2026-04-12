package com.chua.starter.panel.service;

import com.chua.starter.panel.model.JdbcCatalogNode;
import com.chua.starter.panel.model.JdbcConnectionMetadata;
import com.chua.starter.panel.model.JdbcQueryResult;
import com.chua.starter.panel.model.JdbcTableStructure;
import com.chua.starter.panel.model.PanelJdbcAccountSaveRequest;
import com.chua.starter.panel.model.PanelJdbcAccountView;
import com.chua.starter.panel.model.PanelJdbcPrivilegeRequest;
import com.chua.starter.panel.model.PanelTableMutationView;
import com.chua.starter.panel.model.PanelCapabilitySnapshot;
import com.chua.starter.panel.model.PanelTableSaveRequest;
import com.chua.starter.panel.model.PanelTableDataRequest;
import com.chua.starter.panel.model.PanelTableDataView;

import java.util.List;

/**
 * JDBC 面板服务。
 */
public interface JdbcPanelService {

    List<JdbcCatalogNode> listCatalogTree(String connectionId);

    List<JdbcCatalogNode> search(String connectionId, String keyword);

    JdbcTableStructure tableStructure(String connectionId, String catalog, String schema, String tableName);

    PanelTableDataView tableData(String connectionId, PanelTableDataRequest request);

    PanelTableMutationView saveTableData(String connectionId, PanelTableSaveRequest request);

    JdbcQueryResult execute(String connectionId, String sql);

    JdbcQueryResult explain(String connectionId, String sql);

    List<PanelJdbcAccountView> accounts(String connectionId);

    PanelJdbcAccountView createAccount(String connectionId, PanelJdbcAccountSaveRequest request);

    PanelJdbcAccountView updateAccount(String connectionId, PanelJdbcAccountSaveRequest request);

    boolean deleteAccount(String connectionId, String accountName, String host);

    PanelJdbcAccountView grantAccount(String connectionId, PanelJdbcPrivilegeRequest request);

    PanelJdbcAccountView revokeAccount(String connectionId, PanelJdbcPrivilegeRequest request);

    PanelTableMutationView updateTableComment(String connectionId, String catalog, String schema, String tableName, String comment);

    PanelTableMutationView updateColumnComment(String connectionId, String catalog, String schema, String tableName, String columnName, String comment);

    JdbcConnectionMetadata metadata(String connectionId);

    PanelCapabilitySnapshot capabilities(String connectionId);
}
