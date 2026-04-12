package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.cache.PanelConnectionCache;
import com.chua.starter.panel.config.PanelProperties;
import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.panel.model.JdbcCatalogNode;
import com.chua.starter.panel.model.JdbcConnectionMetadata;
import com.chua.starter.panel.model.JdbcQueryResult;
import com.chua.starter.panel.model.JdbcTableStructure;
import com.chua.starter.panel.model.PanelCapabilitySnapshot;
import com.chua.starter.panel.model.PanelConnectionHandle;
import com.chua.starter.panel.service.JdbcPanelService;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 默认 JDBC 面板服务实现。
 */
public class DefaultJdbcPanelService implements JdbcPanelService {

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
            List<String> primaryKeys = new ArrayList<>();

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

            return JdbcTableStructure.builder()
                    .catalogName(catalog)
                    .schemaName(schema)
                    .tableName(tableName)
                    .tableComment("")
                    .columns(columns)
                    .indexes(indexes)
                    .primaryKeys(primaryKeys)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("读取表结构失败: " + e.getMessage(), e);
        }
    }

    @Override
    public JdbcQueryResult execute(String connectionId, String sql) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        try (Connection connection = requireDataSource(handle).getConnection();
             Statement statement = connection.createStatement()) {
            boolean hasResultSet = statement.execute(sql);
            if (!hasResultSet) {
                return JdbcQueryResult.builder()
                        .query(false)
                        .affectedRows(statement.getUpdateCount())
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
                    .columns(columns)
                    .rows(rows)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("执行 SQL 失败: " + e.getMessage(), e);
        }
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
        return chatClientProvider.getIfAvailable() != null;
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
