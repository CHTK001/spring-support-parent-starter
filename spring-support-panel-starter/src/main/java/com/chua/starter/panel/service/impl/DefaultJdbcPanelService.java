package com.chua.starter.panel.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.starter.panel.cache.PanelConnectionCache;
import com.chua.starter.panel.config.PanelProperties;
import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.panel.model.JdbcCatalogNode;
import com.chua.starter.panel.model.JdbcConnectionMetadata;
import com.chua.starter.panel.model.JdbcQueryResult;
import com.chua.starter.panel.model.JdbcTableStructure;
import com.chua.starter.panel.model.PanelJdbcAccountSaveRequest;
import com.chua.starter.panel.model.PanelJdbcAccountView;
import com.chua.starter.panel.model.PanelCapabilitySnapshot;
import com.chua.starter.panel.model.PanelConnectionHandle;
import com.chua.starter.panel.model.PanelJdbcPrivilegeRequest;
import com.chua.starter.panel.model.PanelTableDataRequest;
import com.chua.starter.panel.model.PanelTableDataView;
import com.chua.starter.panel.model.PanelTableMutationView;
import com.chua.starter.panel.model.PanelTableRowUpdate;
import com.chua.starter.panel.model.PanelTableSaveRequest;
import com.chua.starter.panel.service.JdbcPanelService;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 默认 JDBC 面板服务实现。
 */
public class DefaultJdbcPanelService implements JdbcPanelService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PanelConnectionCache panelConnectionCache;
    private final PanelProperties panelProperties;
    private final ObjectProvider<ChatClient> chatClientProvider;

    public DefaultJdbcPanelService(
            PanelConnectionCache panelConnectionCache,
            PanelProperties panelProperties,
            ObjectProvider<ChatClient> chatClientProvider) {
        this.panelConnectionCache = panelConnectionCache;
        this.panelProperties = panelProperties;
        this.chatClientProvider = chatClientProvider;
    }

    @Override
    public List<JdbcCatalogNode> listCatalogTree(String connectionId) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        try (Connection connection = requireDataSource(handle).getConnection()) {
            return collectCatalogTree(connection);
        } catch (Exception e) {
            throw new IllegalStateException("读取数据库列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<JdbcCatalogNode> search(String connectionId, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalizedKeyword.isEmpty()) {
            return listCatalogTree(connectionId);
        }

        PanelConnectionHandle handle = requireHandle(connectionId);
        try (Connection connection = requireDataSource(handle).getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<JdbcCatalogNode> result = new ArrayList<>();

            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE", "BASE TABLE"})) {
                while (tables.next()) {
                    String catalog = normalizeName(tables.getString("TABLE_CAT"));
                    String schema = normalizeName(tables.getString("TABLE_SCHEM"));
                    String table = tables.getString("TABLE_NAME");
                    if (table != null && table.toLowerCase(Locale.ROOT).contains(normalizedKeyword)) {
                        result.add(JdbcCatalogNode.builder()
                                .nodeId(tableNodeId(catalog, schema, table))
                                .parentId(schemaNodeId(catalog, schema))
                                .nodeType("table")
                                .nodeName(table)
                                .description(buildTableDescription(catalog, schema))
                                .catalogName(catalog)
                                .schemaName(schema)
                                .tableName(table)
                                .children(List.of())
                                .build());
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("搜索数据库对象失败: " + e.getMessage(), e);
        }
    }

    @Override
    public JdbcTableStructure tableStructure(String connectionId, String catalog, String schema, String tableName) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        try (Connection connection = requireDataSource(handle).getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<Map<String, Object>> columns = new ArrayList<>();
            List<Map<String, Object>> indexes = new ArrayList<>();
            List<Map<String, Object>> triggers = new ArrayList<>();
            List<String> primaryKeys = new ArrayList<>();
            String tableComment = "";

            try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, null)) {
                while (rs.next()) {
                    Map<String, Object> column = new LinkedHashMap<>();
                    column.put("name", rs.getString("COLUMN_NAME"));
                    column.put("type", rs.getString("TYPE_NAME"));
                    column.put("size", rs.getInt("COLUMN_SIZE"));
                    column.put("scale", rs.getInt("DECIMAL_DIGITS"));
                    column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    column.put("defaultValue", rs.getString("COLUMN_DEF"));
                    column.put("comment", rs.getString("REMARKS"));
                    columns.add(column);
                }
            }

            try (ResultSet rs = metaData.getTables(catalog, schema, tableName, new String[]{"TABLE", "BASE TABLE"})) {
                if (rs.next()) {
                    tableComment = rs.getString("REMARKS");
                }
            }

            try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }

            try (ResultSet rs = metaData.getIndexInfo(catalog, schema, tableName, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName == null) {
                        continue;
                    }
                    Map<String, Object> index = new LinkedHashMap<>();
                    index.put("name", indexName);
                    index.put("column", rs.getString("COLUMN_NAME"));
                    index.put("nonUnique", rs.getBoolean("NON_UNIQUE"));
                    index.put("type", rs.getShort("TYPE"));
                    indexes.add(index);
                }
            }

            triggers.addAll(queryTriggers(connection, catalog, schema, tableName));
            String ddlText = resolveTableDdl(connection, catalog, schema, tableName, columns, primaryKeys, tableComment);

            return JdbcTableStructure.builder()
                    .catalogName(catalog)
                    .schemaName(schema)
                    .tableName(tableName)
                    .tableComment(tableComment)
                    .ddlText(ddlText)
                    .columns(columns)
                    .indexes(indexes)
                    .triggers(triggers)
                    .primaryKeys(primaryKeys)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("读取表结构失败: " + e.getMessage(), e);
        }
    }

    @Override
    public PanelTableDataView tableData(String connectionId, PanelTableDataRequest request) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        String tableName = request == null ? null : request.getPanelTableName();
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        long pageNum = request.getPanelPageNum() <= 0 ? 1 : request.getPanelPageNum();
        long pageSize = request.getPanelPageSize() <= 0 ? 100 : Math.min(request.getPanelPageSize(), 1000);
        long offset = (pageNum - 1) * pageSize;
        long startTime = System.currentTimeMillis();

        try (Connection connection = requireDataSource(handle).getConnection()) {
            String qualifiedTableName = qualifyTableName(
                    connection,
                    request.getPanelCatalogName(),
                    request.getPanelSchemaName(),
                    tableName);
            List<String> columns = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();

            try (PreparedStatement statement = connection.prepareStatement(
                    "select * from " + qualifiedTableName + " limit ? offset ?")) {
                statement.setLong(1, pageSize);
                statement.setLong(2, offset);
                try (ResultSet resultSet = statement.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        columns.add(metaData.getColumnLabel(i));
                    }
                    while (resultSet.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= metaData.getColumnCount(); i++) {
                            row.put(columns.get(i - 1), resultSet.getObject(i));
                        }
                        rows.add(row);
                    }
                }
            }

            long total = -1;
            if (request.isPanelLoadTotal()) {
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("select count(*) from " + qualifiedTableName)) {
                    if (rs.next()) {
                        total = rs.getLong(1);
                    }
                }
            }

            return PanelTableDataView.builder()
                    .panelColumns(columns)
                    .panelRows(rows)
                    .panelTotal(total)
                    .panelPageNum(pageNum)
                    .panelPageSize(pageSize)
                    .panelElapsedMillis(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("读取表数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public PanelTableMutationView saveTableData(String connectionId, PanelTableSaveRequest request) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        if (request == null || request.getPanelUpdates() == null || request.getPanelUpdates().isEmpty()) {
            return PanelTableMutationView.builder()
                    .panelAffectedRows(0)
                    .panelElapsedMillis(0)
                    .panelMessage("没有可保存的数据变更")
                    .build();
        }

        String tableName = request.getPanelTableName();
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }

        long startTime = System.currentTimeMillis();
        try (Connection connection = requireDataSource(handle).getConnection()) {
            String qualifiedTableName = qualifyTableName(
                    connection,
                    request.getPanelCatalogName(),
                    request.getPanelSchemaName(),
                    tableName);
            JdbcTableStructure structure = tableStructure(
                    connectionId,
                    request.getPanelCatalogName(),
                    request.getPanelSchemaName(),
                    tableName);
            List<String> primaryKeys = structure.getPrimaryKeys() == null
                    ? List.of()
                    : structure.getPrimaryKeys();
            long affectedRows = 0;

            for (PanelTableRowUpdate updateItem : request.getPanelUpdates()) {
                Map<String, Object> originalRow = updateItem == null ? null : updateItem.getPanelOriginalRow();
                Map<String, Object> currentRow = updateItem == null ? null : updateItem.getPanelCurrentRow();
                if (originalRow == null || currentRow == null) {
                    continue;
                }
                affectedRows += mutateSingleRow(connection, qualifiedTableName, primaryKeys, originalRow, currentRow);
            }

            return PanelTableMutationView.builder()
                    .panelAffectedRows(affectedRows)
                    .panelElapsedMillis(System.currentTimeMillis() - startTime)
                    .panelMessage("已保存 " + affectedRows + " 行变更")
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("保存表数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public JdbcQueryResult execute(String connectionId, String sql) {
        return runQuery(connectionId, sql);
    }

    @Override
    public JdbcQueryResult explain(String connectionId, String sql) {
        String normalizedSql = normalizeExecutableSql(sql);
        if (normalizedSql.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        return runQuery(connectionId, "explain " + normalizedSql);
    }

    @Override
    public List<PanelJdbcAccountView> accounts(String connectionId) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        try (Connection connection = requireDataSource(handle).getConnection()) {
            ensureMysqlConnection(connection);

            List<PanelJdbcAccountView> result = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select user, host from mysql.user order by user, host")) {
                while (rs.next()) {
                    result.add(buildAccountView(connection, rs.getString("user"), rs.getString("host")));
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("读取数据库账号失败: " + e.getMessage(), e);
        }
    }

    @Override
    public PanelJdbcAccountView createAccount(String connectionId, PanelJdbcAccountSaveRequest request) {
        return mutateAccount(connectionId, request, true);
    }

    @Override
    public PanelJdbcAccountView updateAccount(String connectionId, PanelJdbcAccountSaveRequest request) {
        return mutateAccount(connectionId, request, false);
    }

    @Override
    public boolean deleteAccount(String connectionId, String accountName, String host) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        try (Connection connection = requireDataSource(handle).getConnection();
             Statement statement = connection.createStatement()) {
            ensureMysqlConnection(connection);
            String normalizedAccount = requireAccountName(accountName);
            String normalizedHost = normalizeHost(host);
            statement.execute("drop user if exists " + quoteAccountPrincipal(normalizedAccount, normalizedHost));
            flushPrivileges(statement);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("删除数据库账号失败: " + e.getMessage(), e);
        }
    }

    @Override
    public PanelJdbcAccountView grantAccount(String connectionId, PanelJdbcPrivilegeRequest request) {
        return mutateAccountPrivileges(connectionId, request, true);
    }

    @Override
    public PanelJdbcAccountView revokeAccount(String connectionId, PanelJdbcPrivilegeRequest request) {
        return mutateAccountPrivileges(connectionId, request, false);
    }

    @Override
    public PanelTableMutationView updateTableComment(
            String connectionId,
            String catalog,
            String schema,
            String tableName,
            String comment) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        long startTime = System.currentTimeMillis();
        try (Connection connection = requireDataSource(handle).getConnection();
             Statement statement = connection.createStatement()) {
            ensureMysqlConnection(connection);
            String qualifiedTableName = qualifyTableName(connection, catalog, schema, tableName.trim());
            statement.execute(
                    "alter table " + qualifiedTableName
                            + " comment = '" + escapeSqlLiteral(comment == null ? "" : comment.trim()) + "'");
            return PanelTableMutationView.builder()
                    .panelAffectedRows(1)
                    .panelElapsedMillis(System.currentTimeMillis() - startTime)
                    .panelMessage("表备注已写入数据库")
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("更新表备注失败: " + e.getMessage(), e);
        }
    }

    @Override
    public PanelTableMutationView updateColumnComment(
            String connectionId,
            String catalog,
            String schema,
            String tableName,
            String columnName,
            String comment) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        long startTime = System.currentTimeMillis();
        try (Connection connection = requireDataSource(handle).getConnection();
             Statement statement = connection.createStatement()) {
            ensureMysqlConnection(connection);
            String qualifiedTableName = qualifyTableName(connection, catalog, schema, tableName.trim());
            String columnDefinition = buildMysqlColumnDefinition(
                    connection,
                    catalog,
                    schema,
                    tableName.trim(),
                    columnName.trim(),
                    comment);
            statement.execute("alter table " + qualifiedTableName + " modify column " + columnDefinition);
            return PanelTableMutationView.builder()
                    .panelAffectedRows(1)
                    .panelElapsedMillis(System.currentTimeMillis() - startTime)
                    .panelMessage("字段备注已写入数据库")
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("更新字段备注失败: " + e.getMessage(), e);
        }
    }

    private JdbcQueryResult runQuery(String connectionId, String sql) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        String normalizedSql = normalizeExecutableSql(sql);
        if (normalizedSql.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        long startTime = System.currentTimeMillis();
        try (Connection connection = requireDataSource(handle).getConnection();
             Statement statement = connection.createStatement()) {
            boolean hasResultSet = statement.execute(normalizedSql);
            if (!hasResultSet) {
                return JdbcQueryResult.builder()
                        .query(false)
                        .affectedRows(statement.getUpdateCount())
                        .elapsedMillis(System.currentTimeMillis() - startTime)
                        .columns(List.of())
                        .rows(List.of())
                        .build();
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            try (ResultSet resultSet = statement.getResultSet()) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    columns.add(metaData.getColumnLabel(i));
                }
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        row.put(columns.get(i - 1), resultSet.getObject(i));
                    }
                    rows.add(row);
                }
            }
            return JdbcQueryResult.builder()
                    .query(true)
                    .affectedRows(rows.size())
                    .elapsedMillis(System.currentTimeMillis() - startTime)
                    .columns(columns)
                    .rows(rows)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("执行 SQL 失败: " + e.getMessage(), e);
        }
    }

    private String normalizeExecutableSql(String sql) {
        String normalized = sql == null ? "" : sql.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        for (int i = 0; i < 3; i++) {
            if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
                try {
                    normalized = OBJECT_MAPPER.readValue(normalized, String.class);
                } catch (Exception ignored) {
                    normalized = normalized.substring(1, normalized.length() - 1);
                }
                normalized = normalized == null ? "" : normalized.trim();
                continue;
            }
            if (normalized.length() >= 4 && normalized.startsWith("\\\"") && normalized.endsWith("\\\"")) {
                normalized = normalized.substring(2, normalized.length() - 2).trim();
                continue;
            }
            break;
        }
        normalized = normalized
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"");
        normalized = normalized
                .replaceAll("^[\\\\\"]+", "")
                .replaceAll("[\\\\\"]+$", "");
        return normalized == null ? "" : normalized.trim();
    }

    @Override
    public JdbcConnectionMetadata metadata(String connectionId) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        try (Connection connection = requireDataSource(handle).getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, Object> attributes = new LinkedHashMap<>();

            String productName = metaData.getDatabaseProductName();
            if (productName != null && productName.toLowerCase(Locale.ROOT).contains("mysql")) {
                attributes.putAll(queryMysqlVariables(connection));
            }

            return JdbcConnectionMetadata.builder()
                    .connectionId(connectionId)
                    .host(handle.getDefinition() == null ? null : handle.getDefinition().getHost())
                    .port(handle.getDefinition() == null ? null : handle.getDefinition().getPort())
                    .catalog(normalizeName(connection.getCatalog()))
                    .defaultSchema(normalizeName(connection.getSchema()))
                    .databaseProductName(productName)
                    .databaseProductVersion(metaData.getDatabaseProductVersion())
                    .driverName(metaData.getDriverName())
                    .driverVersion(metaData.getDriverVersion())
                    .attributes(attributes)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("读取数据源元信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public PanelCapabilitySnapshot capabilities(String connectionId) {
        requireHandle(connectionId);
        return PanelCapabilitySnapshot.builder()
                .jdbcEnabled(panelProperties.isJdbcEnabled())
                .documentEnabled(true)
                .aiEnabled(panelProperties.isAiEnabled())
                .aiStarterEnabled(hasAiStarter())
                .message(hasAiStarter() ? "已检测到 AI Starter 能力。" : "未检测到 AI Starter，当前使用默认规则。")
                .build();
    }

    private PanelConnectionHandle requireHandle(String connectionId) {
        PanelConnectionHandle handle = panelConnectionCache.get(connectionId);
        if (handle == null) {
            throw new IllegalStateException("连接不存在或已过期: " + connectionId);
        }
        panelConnectionCache.touch(connectionId);
        return handle;
    }

    private DataSource requireDataSource(PanelConnectionHandle handle) {
        if (!(handle.getNativeConnection() instanceof DataSource dataSource)) {
            throw new IllegalStateException("连接未绑定 DataSource: " + handle.getConnectionId());
        }
        return dataSource;
    }

    private boolean hasAiStarter() {
        try {
            return chatClientProvider.getIfAvailable() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private PanelJdbcAccountView mutateAccount(
            String connectionId,
            PanelJdbcAccountSaveRequest request,
            boolean create) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        try (Connection connection = requireDataSource(handle).getConnection();
             Statement statement = connection.createStatement()) {
            ensureMysqlConnection(connection);
            String accountName = requireAccountName(request == null ? null : request.getPanelAccountName());
            String host = normalizeHost(request == null ? null : request.getPanelHost());
            String password = request == null ? null : request.getPanelPassword();
            String principal = quoteAccountPrincipal(accountName, host);
            if (create) {
                StringBuilder sql = new StringBuilder("create user if not exists ").append(principal);
                if (password != null && !password.isBlank()) {
                    sql.append(" identified by '").append(escapeSqlLiteral(password)).append("'");
                }
                statement.execute(sql.toString());
            } else if (password != null && !password.isBlank()) {
                statement.execute("alter user " + principal + " identified by '" + escapeSqlLiteral(password) + "'");
            }
            flushPrivileges(statement);
            return buildAccountView(connection, accountName, host);
        } catch (Exception e) {
            throw new IllegalStateException((create ? "创建" : "更新") + "数据库账号失败: " + e.getMessage(), e);
        }
    }

    private PanelJdbcAccountView mutateAccountPrivileges(
            String connectionId,
            PanelJdbcPrivilegeRequest request,
            boolean grant) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        try (Connection connection = requireDataSource(handle).getConnection();
             Statement statement = connection.createStatement()) {
            ensureMysqlConnection(connection);
            String accountName = requireAccountName(request == null ? null : request.getPanelAccountName());
            String host = normalizeHost(request == null ? null : request.getPanelHost());
            List<String> privileges = sanitizePrivileges(request == null ? null : request.getPanelPrivileges());
            if (privileges.isEmpty()) {
                throw new IllegalArgumentException("权限不能为空");
            }
            String scope = buildPrivilegeScope(
                    request == null ? null : request.getPanelCatalogName(),
                    request == null ? null : request.getPanelTableName());
            String principal = quoteAccountPrincipal(accountName, host);
            String sql = (grant ? "grant " : "revoke ")
                    + String.join(", ", privileges)
                    + " on "
                    + scope
                    + (grant
                    ? " to " + principal + (request != null && request.isPanelGrantOption() ? " with grant option" : "")
                    : " from " + principal);
            statement.execute(sql);
            flushPrivileges(statement);
            return buildAccountView(connection, accountName, host);
        } catch (Exception e) {
            throw new IllegalStateException((grant ? "授予" : "回收") + "账号权限失败: " + e.getMessage(), e);
        }
    }

    private void ensureMysqlConnection(Connection connection) throws Exception {
        String productName = connection.getMetaData().getDatabaseProductName();
        if (productName == null || !productName.toLowerCase(Locale.ROOT).contains("mysql")) {
            throw new IllegalStateException("当前仅支持 MySQL 账号管理");
        }
    }

    private PanelJdbcAccountView buildAccountView(Connection connection, String username, String host) {
        return PanelJdbcAccountView.builder()
                .panelAccountName(username)
                .panelHost(host)
                .panelGrants(queryMysqlGrants(connection, username, host))
                .build();
    }

    private String requireAccountName(String accountName) {
        if (accountName == null || accountName.trim().isEmpty()) {
            throw new IllegalArgumentException("账号不能为空");
        }
        return accountName.trim();
    }

    private String normalizeHost(String host) {
        return host == null || host.trim().isEmpty() ? "%" : host.trim();
    }

    private String quoteAccountPrincipal(String username, String host) {
        return "'" + escapeSqlLiteral(username) + "'@'" + escapeSqlLiteral(host) + "'";
    }

    private String buildPrivilegeScope(String catalogName, String tableName) {
        String normalizedCatalog = catalogName == null || catalogName.isBlank() ? "*" : catalogName.trim();
        String normalizedTable = tableName == null || tableName.isBlank() ? "*" : tableName.trim();
        return normalizedCatalog + "." + normalizedTable;
    }

    private List<String> sanitizePrivileges(List<String> privileges) {
        if (privileges == null) {
            return List.of();
        }
        return privileges.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> item.replaceAll("[^A-Za-z_, ]", "").toUpperCase(Locale.ROOT))
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private void flushPrivileges(Statement statement) {
        try {
            statement.execute("flush privileges");
        } catch (Exception ignored) {
            // ignore flush failures for managed databases
        }
    }

    private String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private Map<String, Object> queryMysqlVariables(Connection connection) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "show variables where Variable_name in ('log_bin','binlog_format','character_set_server','collation_server')")) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getObject(2));
                }
            }
            try (ResultSet rs = statement.executeQuery("show master status")) {
                if (rs.next()) {
                    result.put("file", rs.getObject("File"));
                    result.put("position", rs.getObject("Position"));
                }
            }
        } catch (Exception ignored) {
            // ignore metadata probe failures from low-privilege accounts
        }
        return result;
    }

    private List<Map<String, Object>> queryTriggers(
            Connection connection,
            String catalog,
            String schema,
            String tableName) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName == null || !productName.toLowerCase(Locale.ROOT).contains("mysql")) {
                return result;
            }
            String databaseName = schema != null && !schema.isBlank()
                    ? schema
                    : (catalog != null && !catalog.isBlank() ? catalog : connection.getCatalog());
            if (databaseName == null || databaseName.isBlank()) {
                return result;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    select trigger_name,
                           event_manipulation,
                           action_timing,
                           action_statement,
                           created
                    from information_schema.triggers
                    where event_object_schema = ?
                      and event_object_table = ?
                    order by trigger_name
                    """)) {
                statement.setString(1, databaseName);
                statement.setString(2, tableName);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", rs.getString("trigger_name"));
                        row.put("event", rs.getString("event_manipulation"));
                        row.put("timing", rs.getString("action_timing"));
                        row.put("statement", rs.getString("action_statement"));
                        row.put("created", rs.getObject("created"));
                        result.add(row);
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore low-privilege trigger probe failures
        }
        return result;
    }

    private long mutateSingleRow(
            Connection connection,
            String qualifiedTableName,
            List<String> primaryKeys,
            Map<String, Object> originalRow,
            Map<String, Object> currentRow) throws Exception {
        if (originalRow.isEmpty()) {
            return insertSingleRow(connection, qualifiedTableName, currentRow);
        }
        Set<String> allColumns = new HashSet<>();
        allColumns.addAll(originalRow.keySet());
        allColumns.addAll(currentRow.keySet());

        List<String> changedColumns = allColumns.stream()
                .filter(column -> !Objects.equals(originalRow.get(column), currentRow.get(column)))
                .sorted()
                .toList();
        if (changedColumns.isEmpty()) {
            return 0;
        }

        List<String> whereColumns = primaryKeys.isEmpty()
                ? originalRow.keySet().stream().sorted().toList()
                : primaryKeys.stream().filter(originalRow::containsKey).toList();
        if (whereColumns.isEmpty()) {
            whereColumns = originalRow.keySet().stream().sorted().toList();
        }

        String quote = normalizeQuote(connection.getMetaData().getIdentifierQuoteString());
        List<Object> parameters = new ArrayList<>();
        StringBuilder sqlBuilder = new StringBuilder("update ")
                .append(qualifiedTableName)
                .append(" set ");

        for (int i = 0; i < changedColumns.size(); i++) {
            String column = changedColumns.get(i);
            if (i > 0) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append(quoteIdentifier(column, quote)).append(" = ?");
            parameters.add(currentRow.get(column));
        }

        sqlBuilder.append(" where ");
        List<String> predicates = new ArrayList<>();
        for (String column : whereColumns) {
            Object value = originalRow.get(column);
            if (value == null) {
                predicates.add(quoteIdentifier(column, quote) + " is null");
                continue;
            }
            predicates.add(quoteIdentifier(column, quote) + " = ?");
            parameters.add(value);
        }

        if (predicates.isEmpty()) {
            throw new IllegalStateException("缺少更新条件，无法保存表数据");
        }

        sqlBuilder.append(String.join(" and ", predicates));
        try (PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
            return statement.executeUpdate();
        }
    }

    private long insertSingleRow(
            Connection connection,
            String qualifiedTableName,
            Map<String, Object> currentRow) throws Exception {
        if (currentRow == null || currentRow.isEmpty()) {
            return 0;
        }
        String quote = normalizeQuote(connection.getMetaData().getIdentifierQuoteString());
        List<String> columns = currentRow.keySet().stream().sorted().toList();
        String columnSql = columns.stream()
                .map(column -> quoteIdentifier(column, quote))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        String placeholderSql = columns.stream()
                .map(column -> "?")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        String sql = "insert into " + qualifiedTableName + " (" + columnSql + ") values (" + placeholderSql + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < columns.size(); i++) {
                statement.setObject(i + 1, currentRow.get(columns.get(i)));
            }
            return statement.executeUpdate();
        }
    }

    private List<String> queryMysqlGrants(Connection connection, String username, String host) {
        List<String> grants = new ArrayList<>();
        String escapedUser = username == null ? "" : username.replace("'", "''");
        String escapedHost = host == null ? "%" : host.replace("'", "''");
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("show grants for '" + escapedUser + "'@'" + escapedHost + "'")) {
            while (rs.next()) {
                grants.add(String.valueOf(rs.getObject(1)));
            }
        } catch (Exception ignored) {
            // ignore low-privilege grant probe failures
        }
        return grants;
    }

    private String resolveTableDdl(
            Connection connection,
            String catalog,
            String schema,
            String tableName,
            List<Map<String, Object>> columns,
            List<String> primaryKeys,
            String tableComment) {
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName != null && productName.toLowerCase(Locale.ROOT).contains("mysql")) {
                return queryMysqlShowCreateTable(connection, catalog, schema, tableName);
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return buildFallbackDdl(connection, catalog, schema, tableName, columns, primaryKeys, tableComment);
    }

    private String queryMysqlShowCreateTable(Connection connection, String catalog, String schema, String tableName) throws Exception {
        String qualifiedTableName = qualifyTableName(connection, catalog, schema, tableName);
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("show create table " + qualifiedTableName)) {
            if (rs.next()) {
                ResultSetMetaData metaData = rs.getMetaData();
                if (metaData.getColumnCount() >= 2) {
                    return rs.getString(2);
                }
            }
        }
        return "";
    }

    private String buildFallbackDdl(
            Connection connection,
            String catalog,
            String schema,
            String tableName,
            List<Map<String, Object>> columns,
            List<String> primaryKeys,
            String tableComment) {
        try {
            String quote = normalizeQuote(connection.getMetaData().getIdentifierQuoteString());
            String qualifiedTableName = new StringJoiner(".")
                    .add(catalog == null || catalog.isBlank() ? null : quoteIdentifier(catalog, quote))
                    .add(schema == null || schema.isBlank() ? null : quoteIdentifier(schema, quote))
                    .add(quoteIdentifier(tableName, quote))
                    .toString()
                    .replace("null.", "");
            List<String> columnLines = new ArrayList<>();
            for (Map<String, Object> column : columns) {
                String columnName = String.valueOf(column.getOrDefault("name", ""));
                if (columnName.isBlank()) {
                    continue;
                }
                StringBuilder line = new StringBuilder("  ")
                        .append(quoteIdentifier(columnName, quote))
                        .append(" ")
                        .append(resolveColumnType(column));
                if (!Boolean.TRUE.equals(column.get("nullable"))) {
                    line.append(" not null");
                }
                Object defaultValue = column.get("defaultValue");
                if (defaultValue != null) {
                    line.append(" default '").append(escapeSqlLiteral(String.valueOf(defaultValue))).append("'");
                }
                Object comment = column.get("comment");
                if (comment != null && !String.valueOf(comment).isBlank()) {
                    line.append(" comment '").append(escapeSqlLiteral(String.valueOf(comment))).append("'");
                }
                columnLines.add(line.toString());
            }
            if (!primaryKeys.isEmpty()) {
                columnLines.add("  primary key (" + String.join(", ", primaryKeys) + ")");
            }
            StringBuilder ddl = new StringBuilder("create table ")
                    .append(qualifiedTableName)
                    .append(" (\n")
                    .append(String.join(",\n", columnLines))
                    .append("\n)");
            if (tableComment != null && !tableComment.isBlank()) {
                ddl.append(" comment='").append(escapeSqlLiteral(tableComment)).append("'");
            }
            ddl.append(";");
            return ddl.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveColumnType(Map<String, Object> column) {
        String rawType = String.valueOf(column.getOrDefault("type", "varchar"));
        if (rawType.contains("(")) {
            return rawType;
        }
        Object sizeValue = column.get("size");
        Object scaleValue = column.get("scale");
        Integer size = sizeValue instanceof Number ? ((Number) sizeValue).intValue() : null;
        Integer scale = scaleValue instanceof Number ? ((Number) scaleValue).intValue() : null;
        if (size == null || size <= 0) {
            return rawType;
        }
        if (scale == null || scale < 0) {
            return rawType + "(" + size + ")";
        }
        return rawType + "(" + size + "," + scale + ")";
    }

    private String buildMysqlColumnDefinition(
            Connection connection,
            String catalog,
            String schema,
            String tableName,
            String columnName,
            String comment) throws Exception {
        String databaseName = schema != null && !schema.isBlank()
                ? schema
                : (catalog != null && !catalog.isBlank() ? catalog : connection.getCatalog());
        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalStateException("缺少数据库名称，无法更新字段备注");
        }

        String quote = normalizeQuote(connection.getMetaData().getIdentifierQuoteString());
        try (PreparedStatement statement = connection.prepareStatement("""
                select column_name,
                       column_type,
                       is_nullable,
                       column_default,
                       extra,
                       generation_expression,
                       character_set_name,
                       collation_name
                from information_schema.columns
                where table_schema = ?
                  and table_name = ?
                  and column_name = ?
                """)) {
            statement.setString(1, databaseName);
            statement.setString(2, tableName);
            statement.setString(3, columnName);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("字段不存在: " + columnName);
                }
                StringBuilder sql = new StringBuilder()
                        .append(quoteIdentifier(rs.getString("column_name"), quote))
                        .append(" ")
                        .append(rs.getString("column_type"));
                String generationExpression = rs.getString("generation_expression");
                String extra = rs.getString("extra");
                if (generationExpression != null && !generationExpression.isBlank()) {
                    sql.append(" as (").append(generationExpression).append(")");
                    if (extra != null && !extra.isBlank()) {
                        sql.append(" ").append(extra);
                    }
                    sql.append(" comment '").append(escapeSqlLiteral(comment == null ? "" : comment.trim())).append("'");
                    return sql.toString();
                }
                String charset = rs.getString("character_set_name");
                String collation = rs.getString("collation_name");
                if (charset != null && !charset.isBlank()) {
                    sql.append(" character set ").append(charset);
                }
                if (collation != null && !collation.isBlank()) {
                    sql.append(" collate ").append(collation);
                }
                sql.append(" ")
                        .append("YES".equalsIgnoreCase(rs.getString("is_nullable")) ? "null" : "not null");
                Object defaultValue = rs.getObject("column_default");
                if (defaultValue != null) {
                    sql.append(" default ").append(renderMysqlDefaultValue(defaultValue, rs.getString("column_type")));
                }
                if (extra != null && !extra.isBlank()) {
                    sql.append(" ").append(extra);
                }
                sql.append(" comment '").append(escapeSqlLiteral(comment == null ? "" : comment.trim())).append("'");
                return sql.toString();
            }
        }
    }

    private String renderMysqlDefaultValue(Object value, String columnType) {
        if (value == null) {
            return "null";
        }
        String text = String.valueOf(value);
        String normalized = text.trim();
        if (normalized.equalsIgnoreCase("null")) {
            return "null";
        }
        if (normalized.matches("(?i)current_timestamp(\\(\\d+\\))?")) {
            return normalized;
        }
        if (normalized.startsWith("b'") || normalized.startsWith("B'")) {
            return normalized;
        }
        String type = columnType == null ? "" : columnType.toLowerCase(Locale.ROOT);
        if (type.contains("int")
                || type.contains("decimal")
                || type.contains("numeric")
                || type.contains("float")
                || type.contains("double")
                || type.contains("bit")
                || type.contains("bool")) {
            return normalized;
        }
        return "'" + escapeSqlLiteral(normalized) + "'";
    }

    private String qualifyTableName(Connection connection, String catalog, String schema, String tableName) throws Exception {
        String normalizedQuote = normalizeQuote(connection.getMetaData().getIdentifierQuoteString());
        List<String> parts = new ArrayList<>();
        if (catalog != null && !catalog.isBlank()) {
            parts.add(quoteIdentifier(catalog, normalizedQuote));
        }
        if (schema != null && !schema.isBlank()) {
            parts.add(quoteIdentifier(schema, normalizedQuote));
        }
        parts.add(quoteIdentifier(tableName, normalizedQuote));
        return String.join(".", parts);
    }

    private String normalizeQuote(String quote) {
        return quote == null ? "" : quote.trim();
    }

    private String quoteIdentifier(String value, String quote) {
        if (quote == null || quote.isBlank()) {
            return value;
        }
        return quote + value.replace(quote, quote + quote) + quote;
    }

    private List<JdbcCatalogNode> collectCatalogTree(Connection connection) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        Map<String, JdbcCatalogNode> catalogs = new LinkedHashMap<>();
        Map<String, Map<String, JdbcCatalogNode>> schemaMap = new LinkedHashMap<>();
        try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE", "BASE TABLE"})) {
            while (tables.next()) {
                String catalog = normalizeName(tables.getString("TABLE_CAT"));
                String schema = normalizeName(tables.getString("TABLE_SCHEM"));
                String table = tables.getString("TABLE_NAME");
                if (table == null) {
                    continue;
                }

                String catalogId = catalogNodeId(catalog);
                JdbcCatalogNode catalogNode = catalogs.computeIfAbsent(catalogId, key -> JdbcCatalogNode.builder()
                        .nodeId(key)
                        .parentId(null)
                        .nodeType("catalog")
                        .nodeName(displayCatalogName(catalog))
                        .description("数据库")
                        .catalogName(catalog)
                        .children(new ArrayList<>())
                        .build());

                Map<String, JdbcCatalogNode> schemas = schemaMap.computeIfAbsent(catalogId, key -> new LinkedHashMap<>());
                String schemaId = schemaNodeId(catalog, schema);
                JdbcCatalogNode schemaNode = schemas.computeIfAbsent(schemaId, key -> {
                    JdbcCatalogNode node = JdbcCatalogNode.builder()
                            .nodeId(key)
                            .parentId(catalogId)
                            .nodeType("schema")
                            .nodeName(displaySchemaName(schema))
                            .description("Schema")
                            .catalogName(catalog)
                            .schemaName(schema)
                            .children(new ArrayList<>())
                            .build();
                    catalogNode.getChildren().add(node);
                    return node;
                });

                schemaNode.getChildren().add(JdbcCatalogNode.builder()
                        .nodeId(tableNodeId(catalog, schema, table))
                        .parentId(schemaId)
                        .nodeType("table")
                        .nodeName(table)
                        .description(buildTableDescription(catalog, schema))
                        .catalogName(catalog)
                        .schemaName(schema)
                        .tableName(table)
                        .children(List.of())
                        .build());
            }
        }
        return new ArrayList<>(catalogs.values());
    }

    private String buildTableDescription(String catalog, String schema) {
        if (catalog == null && schema == null) {
            return "表";
        }
        if (catalog == null) {
            return "表 / " + displaySchemaName(schema);
        }
        if (schema == null) {
            return "表 / " + displayCatalogName(catalog);
        }
        return "表 / " + catalog + " / " + schema;
    }

    private String catalogNodeId(String catalog) {
        return "catalog::" + normalizeKey(catalog);
    }

    private String schemaNodeId(String catalog, String schema) {
        return catalogNodeId(catalog) + "::schema::" + normalizeKey(schema);
    }

    private String tableNodeId(String catalog, String schema, String table) {
        return schemaNodeId(catalog, schema) + "::table::" + table;
    }

    private String displayCatalogName(String catalog) {
        return catalog == null ? "默认目录" : catalog;
    }

    private String displaySchemaName(String schema) {
        return schema == null ? "默认 Schema" : schema;
    }

    private String normalizeKey(String value) {
        return value == null ? "__default__" : value;
    }

    private String normalizeName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }
}
