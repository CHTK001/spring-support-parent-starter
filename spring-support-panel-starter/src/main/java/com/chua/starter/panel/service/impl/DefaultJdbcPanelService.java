package com.chua.starter.panel.service.impl;

import com.chua.common.support.data.datasource.dialect.Dialect;
import com.chua.common.support.data.datasource.dialect.DialectFactory;
import com.chua.common.support.ops.backup.BackupConfig;
import com.chua.common.support.ops.backup.BackupRecord;
import com.chua.common.support.ops.backup.BackupResult;
import com.chua.common.support.ops.backup.OpsBackup;
import com.chua.common.support.ops.listener.OpsListener;
import com.chua.common.support.ops.node.OpsNode;
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
import com.chua.starter.panel.model.PanelTableFilterItem;
import com.chua.starter.panel.model.PanelTableDataView;
import com.chua.starter.panel.model.PanelTableMutationView;
import com.chua.starter.panel.model.PanelTableRowUpdate;
import com.chua.starter.panel.model.PanelTableSaveRequest;
import com.chua.starter.panel.model.PanelMysqlProcessKillRequest;
import com.chua.starter.panel.model.PanelMysqlProcessKillView;
import com.chua.starter.panel.model.PanelBackupSchedule;
import com.chua.starter.panel.model.PanelBackupRecord;
import com.chua.starter.panel.model.PanelBinlogEvent;
import com.chua.starter.panel.service.JdbcPanelService;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 默认 JDBC 面板服务实现。
 *
 * @author CH
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
            String quote = normalizeQuote(connection.getMetaData().getIdentifierQuoteString());
            Map<String, Integer> tableColumnTypes = loadTableColumnTypes(
                    connection,
                    request.getPanelCatalogName(),
                    request.getPanelSchemaName(),
                    tableName);
            List<String> tableColumns = new ArrayList<>(tableColumnTypes.keySet());
            String sortField = normalizeSortableColumn(request.getPanelSortField(), tableColumns);
            String sortOrder = normalizeSortOrder(request.getPanelSortOrder());
            String filterKeyword = normalizeFilterKeyword(request.getPanelFilterKeyword());
            List<Object> filterParameters = new ArrayList<>();
            String filterClause = buildFilterClause(
                    connection,
                    tableColumnTypes,
                    quote,
                    filterKeyword,
                    request.getPanelFilterJoin(),
                    request.getPanelFilters(),
                    filterParameters);
            StringBuilder sql = new StringBuilder("select * from ").append(qualifiedTableName);
            appendFilterClause(sql, filterClause);
            if (sortField != null) {
                sql.append(" order by ")
                        .append(quoteIdentifier(sortField, quote))
                        .append(" ")
                        .append(sortOrder);
            }
            sql.append(" limit ? offset ?");

            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                int parameterIndex = bindParameters(statement, filterParameters, 1);
                statement.setLong(parameterIndex++, pageSize);
                statement.setLong(parameterIndex, offset);
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
                StringBuilder countSql = new StringBuilder("select count(*) from ").append(qualifiedTableName);
                appendFilterClause(countSql, filterClause);
                try (PreparedStatement statement = connection.prepareStatement(countSql.toString())) {
                    bindParameters(statement, filterParameters, 1);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            total = rs.getLong(1);
                        }
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

    private Map<String, Integer> loadTableColumnTypes(
            Connection connection,
            String catalog,
            String schema,
            String tableName) throws Exception {
        Map<String, Integer> result = new LinkedHashMap<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, null)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName != null && !columnName.isBlank()) {
                    result.put(columnName, rs.getInt("DATA_TYPE"));
                }
            }
        }
        return result;
    }

    private String normalizeFilterKeyword(String filterKeyword) {
        if (filterKeyword == null || filterKeyword.isBlank()) {
            return null;
        }
        return filterKeyword.trim();
    }

    private String buildFilterClause(
            Connection connection,
            Map<String, Integer> tableColumnTypes,
            String quote,
            String filterKeyword,
            String filterJoin,
            List<PanelTableFilterItem> filterItems,
            List<Object> parameters) throws Exception {
        if (tableColumnTypes == null || tableColumnTypes.isEmpty()) {
            return "";
        }
        String keywordClause = buildKeywordFilterClause(connection, tableColumnTypes, quote, filterKeyword, parameters);
        String advancedClause = buildAdvancedFilterClause(connection, tableColumnTypes, quote, filterJoin, filterItems, parameters);
        return combineFilterClauses(keywordClause, advancedClause);
    }

    private String buildKeywordFilterClause(
            Connection connection,
            Map<String, Integer> tableColumnTypes,
            String quote,
            String filterKeyword,
            List<Object> parameters) throws Exception {
        if (filterKeyword == null || filterKeyword.isBlank()) {
            return "";
        }
        String productName = connection.getMetaData().getDatabaseProductName();
        String normalizedFilterValue = "%" + escapeLikeValue(filterKeyword.toLowerCase(Locale.ROOT)) + "%";
        List<String> predicates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tableColumnTypes.entrySet()) {
            if (!isFilterableColumnType(entry.getValue())) {
                continue;
            }
            String columnSql = quoteIdentifier(entry.getKey(), quote);
            predicates.add("lower(coalesce("
                    + resolveFilterColumnExpression(productName, columnSql)
                    + ", '')) like ? escape '\\'");
            parameters.add(normalizedFilterValue);
        }
        return String.join(" or ", predicates);
    }

    private String buildAdvancedFilterClause(
            Connection connection,
            Map<String, Integer> tableColumnTypes,
            String quote,
            String filterJoin,
            List<PanelTableFilterItem> filterItems,
            List<Object> parameters) throws Exception {
        if (filterItems == null || filterItems.isEmpty()) {
            return "";
        }
        String normalizedJoin = normalizeFilterJoin(filterJoin);
        String productName = connection.getMetaData().getDatabaseProductName();
        List<String> availableColumns = new ArrayList<>(tableColumnTypes.keySet());
        List<String> predicates = new ArrayList<>();
        for (PanelTableFilterItem item : filterItems) {
            String predicate = buildSingleFilterPredicate(
                    productName,
                    tableColumnTypes,
                    availableColumns,
                    quote,
                    item,
                    parameters);
            if (predicate != null && !predicate.isBlank()) {
                predicates.add("(" + predicate + ")");
            }
        }
        return String.join(" " + normalizedJoin + " ", predicates);
    }

    private String buildSingleFilterPredicate(
            String productName,
            Map<String, Integer> tableColumnTypes,
            List<String> availableColumns,
            String quote,
            PanelTableFilterItem item,
            List<Object> parameters) {
        if (item == null) {
            return "";
        }
        String columnName = normalizeSortableColumn(item.getPanelColumnName(), availableColumns);
        if (columnName == null) {
            return "";
        }
        String operator = normalizeFilterOperator(item.getPanelOperator());
        String rawValue = item.getPanelValue() == null ? "" : item.getPanelValue().trim();
        Integer dataType = tableColumnTypes.get(columnName);
        String columnSql = quoteIdentifier(columnName, quote);
        String textColumnSql = "lower(coalesce(" + resolveFilterColumnExpression(productName, columnSql) + ", ''))";

        return switch (operator) {
            case "contains" -> {
                parameters.add("%" + escapeLikeValue(rawValue.toLowerCase(Locale.ROOT)) + "%");
                yield textColumnSql + " like ? escape '\\'";
            }
            case "notContains" -> {
                parameters.add("%" + escapeLikeValue(rawValue.toLowerCase(Locale.ROOT)) + "%");
                yield textColumnSql + " not like ? escape '\\'";
            }
            case "startsWith" -> {
                parameters.add(escapeLikeValue(rawValue.toLowerCase(Locale.ROOT)) + "%");
                yield textColumnSql + " like ? escape '\\'";
            }
            case "endsWith" -> {
                parameters.add("%" + escapeLikeValue(rawValue.toLowerCase(Locale.ROOT)));
                yield textColumnSql + " like ? escape '\\'";
            }
            case "equals" -> buildEqualsPredicate(textColumnSql, columnSql, dataType, rawValue, parameters, true);
            case "notEquals" -> buildEqualsPredicate(textColumnSql, columnSql, dataType, rawValue, parameters, false);
            case "gt" -> buildComparablePredicate(columnSql, dataType, rawValue, ">", parameters);
            case "ge" -> buildComparablePredicate(columnSql, dataType, rawValue, ">=", parameters);
            case "lt" -> buildComparablePredicate(columnSql, dataType, rawValue, "<", parameters);
            case "le" -> buildComparablePredicate(columnSql, dataType, rawValue, "<=", parameters);
            case "isEmpty" -> "trim(coalesce(" + resolveFilterColumnExpression(productName, columnSql) + ", '')) = ''";
            case "isNotEmpty" -> "trim(coalesce(" + resolveFilterColumnExpression(productName, columnSql) + ", '')) <> ''";
            case "isNotNull" -> columnSql + " is not null";
            default -> columnSql + " is null";
        };
    }

    private String buildEqualsPredicate(
            String textColumnSql,
            String columnSql,
            Integer dataType,
            String rawValue,
            List<Object> parameters,
            boolean equals) {
        String operator = equals ? "=" : "<>";
        if (shouldUseTypedComparison(dataType)) {
            parameters.add(parseFilterValue(dataType, rawValue));
            return columnSql + " " + operator + " ?";
        }
        parameters.add(rawValue.toLowerCase(Locale.ROOT));
        return textColumnSql + " " + operator + " ?";
    }

    private String buildComparablePredicate(
            String columnSql,
            Integer dataType,
            String rawValue,
            String operator,
            List<Object> parameters) {
        parameters.add(parseFilterValue(dataType, rawValue));
        return columnSql + " " + operator + " ?";
    }

    private String normalizeFilterJoin(String filterJoin) {
        return "or".equalsIgnoreCase(filterJoin == null ? "" : filterJoin.trim()) ? "or" : "and";
    }

    private String normalizeFilterOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return "contains";
        }
        return switch (operator.trim()) {
            case "contains",
                    "notContains",
                    "equals",
                    "notEquals",
                    "startsWith",
                    "endsWith",
                    "gt",
                    "ge",
                    "lt",
                    "le",
                    "isEmpty",
                    "isNotEmpty",
                    "isNull",
                    "isNotNull" -> operator.trim();
            default -> "contains";
        };
    }

    private String combineFilterClauses(String... clauses) {
        List<String> availableClauses = new ArrayList<>();
        for (String clause : clauses) {
            if (clause != null && !clause.isBlank()) {
                availableClauses.add("(" + clause + ")");
            }
        }
        return String.join(" and ", availableClauses);
    }

    private boolean shouldUseTypedComparison(Integer dataType) {
        if (dataType == null) {
            return false;
        }
        return switch (dataType) {
            case Types.CHAR,
                    Types.VARCHAR,
                    Types.LONGVARCHAR,
                    Types.NCHAR,
                    Types.NVARCHAR,
                    Types.LONGNVARCHAR,
                    Types.CLOB,
                    Types.NCLOB -> false;
            default -> true;
        };
    }

    private Object parseFilterValue(Integer dataType, String rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (dataType == null) {
            return rawValue;
        }
        try {
            return switch (dataType) {
                case Types.TINYINT,
                        Types.SMALLINT -> Short.valueOf(rawValue);
                case Types.INTEGER -> Integer.valueOf(rawValue);
                case Types.BIGINT -> Long.valueOf(rawValue);
                case Types.FLOAT,
                        Types.REAL,
                        Types.DOUBLE -> Double.valueOf(rawValue);
                case Types.DECIMAL,
                        Types.NUMERIC -> new BigDecimal(rawValue);
                case Types.BIT,
                        Types.BOOLEAN -> parseBooleanValue(rawValue);
                case Types.DATE -> Date.valueOf(rawValue);
                case Types.TIME -> Time.valueOf(rawValue);
                case Types.TIMESTAMP -> Timestamp.valueOf(normalizeTimestampValue(rawValue));
                default -> rawValue;
            };
        } catch (Exception ignored) {
            return rawValue;
        }
    }

    private Boolean parseBooleanValue(String rawValue) {
        String normalizedValue = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (Objects.equals(normalizedValue, "1")
                || Objects.equals(normalizedValue, "true")
                || Objects.equals(normalizedValue, "yes")) {
            return Boolean.TRUE;
        }
        if (Objects.equals(normalizedValue, "0")
                || Objects.equals(normalizedValue, "false")
                || Objects.equals(normalizedValue, "no")) {
            return Boolean.FALSE;
        }
        return Boolean.valueOf(rawValue);
    }

    private String normalizeTimestampValue(String rawValue) {
        String normalizedValue = rawValue == null ? "" : rawValue.trim();
        if (normalizedValue.contains("T")) {
            normalizedValue = normalizedValue.replace("T", " ");
        }
        if (normalizedValue.length() == 16) {
            normalizedValue = normalizedValue + ":00";
        }
        return normalizedValue;
    }

    private boolean isFilterableColumnType(Integer dataType) {
        if (dataType == null) {
            return true;
        }
        return switch (dataType) {
            case Types.BINARY,
                    Types.VARBINARY,
                    Types.LONGVARBINARY,
                    Types.BLOB,
                    Types.CLOB,
                    Types.NCLOB,
                    Types.SQLXML,
                    Types.ARRAY,
                    Types.DATALINK,
                    Types.DISTINCT,
                    Types.JAVA_OBJECT,
                    Types.OTHER,
                    Types.REF,
                    Types.ROWID,
                    Types.STRUCT -> false;
            default -> true;
        };
    }

    private String resolveFilterColumnExpression(String productName, String columnSql) {
        String normalizedProductName = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        if (normalizedProductName.contains("oracle")) {
            return "cast(" + columnSql + " as varchar2(4000))";
        }
        if (normalizedProductName.contains("sql server") || normalizedProductName.contains("microsoft")) {
            return "cast(" + columnSql + " as nvarchar(max))";
        }
        if (normalizedProductName.contains("mysql")
                || normalizedProductName.contains("mariadb")) {
            return "cast(" + columnSql + " as char)";
        }
        if (normalizedProductName.contains("postgresql")
                || normalizedProductName.contains("h2")
                || normalizedProductName.contains("sqlite")) {
            return "cast(" + columnSql + " as varchar)";
        }
        return "cast(" + columnSql + " as varchar(4000))";
    }

    private String escapeLikeValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private void appendFilterClause(StringBuilder sql, String filterClause) {
        if (filterClause == null || filterClause.isBlank()) {
            return;
        }
        sql.append(" where (").append(filterClause).append(")");
    }

    private int bindParameters(PreparedStatement statement, List<Object> parameters, int startIndex) throws Exception {
        int index = startIndex;
        for (Object parameter : parameters) {
            statement.setObject(index++, parameter);
        }
        return index;
    }

    private String normalizeSortableColumn(String columnName, List<String> availableColumns) {
        if (columnName == null || columnName.isBlank() || availableColumns == null || availableColumns.isEmpty()) {
            return null;
        }
        for (String item : availableColumns) {
            if (item != null && item.equalsIgnoreCase(columnName.trim())) {
                return item;
            }
        }
        return null;
    }

    private String normalizeSortOrder(String sortOrder) {
        if (sortOrder == null || sortOrder.isBlank()) {
            return "asc";
        }
        String normalized = sortOrder.trim().toLowerCase(Locale.ROOT);
        if ("desc".equals(normalized) || "descending".equals(normalized)) {
            return "desc";
        }
        return "asc";
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
        return runQuery(connectionId, sql, null, null);
    }

    @Override
    public JdbcQueryResult execute(String connectionId, String sql, Integer pageNum, Integer pageSize) {
        return runQuery(connectionId, sql, pageNum, pageSize);
    }

    @Override
    public JdbcQueryResult explain(String connectionId, String sql) {
        String normalizedSql = normalizeExecutableSql(sql);
        if (normalizedSql.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        return runQuery(connectionId, "explain " + normalizedSql, null, null);
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

    private JdbcQueryResult runQuery(String connectionId, String sql, Integer pageNum, Integer pageSize) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        String normalizedSql = normalizeExecutableSql(sql);
        if (normalizedSql.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        boolean paginate = pageNum != null && pageNum > 0 && pageSize != null && pageSize > 0
                && looksLikeRowsetQuery(normalizedSql);
        int resolvedPage = paginate ? pageNum : 1;
        int resolvedSize = paginate ? pageSize : 0;
        long startTime = System.currentTimeMillis();
        try (Connection connection = requireDataSource(handle).getConnection();
             Statement statement = connection.createStatement()) {
            // 分页模式：用方言包装 SQL
            String executableSql;
            if (paginate) {
                Dialect dialect = null;
                try { dialect = DialectFactory.create(connection); } catch (RuntimeException ignored) {}
                if (dialect != null && dialect.supportsLimit()) {
                    executableSql = dialect.getPaginationSql(normalizedSql, resolvedPage, resolvedSize);
                } else {
                    // 方言不支持分页，降级为 setMaxRows
                    applyStatementMaxRows(statement, resolvedSize);
                    executableSql = normalizedSql;
                }
            } else {
                executableSql = applyQueryPreviewLimit(statement, normalizedSql);
            }

            boolean hasResultSet = statement.execute(executableSql);
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

            // 分页时查询总数
            long total = -1;
            if (paginate) {
                try (Statement countStmt = connection.createStatement()) {
                    String countSql = "select count(*) from (" + normalizedSql + ") __panel_count__";
                    try (ResultSet countRs = countStmt.executeQuery(countSql)) {
                        if (countRs.next()) {
                            total = countRs.getLong(1);
                        }
                    }
                } catch (Exception ignored) {
                    // COUNT 查询失败不影响主结果
                }
            }

            return JdbcQueryResult.builder()
                    .query(true)
                    .affectedRows(rows.size())
                    .elapsedMillis(System.currentTimeMillis() - startTime)
                    .columns(columns)
                    .rows(rows)
                    .total(total)
                    .pageNum(resolvedPage)
                    .pageSize(resolvedSize)
                    .build();
        } catch (Exception e) {
            return JdbcQueryResult.builder()
                    .query(false)
                    .affectedRows(0)
                    .elapsedMillis(System.currentTimeMillis() - startTime)
                    .columns(List.of())
                    .rows(List.of())
                    .errorMessage("执行 SQL 失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 为 SQL 工作台查询设置预览抓取上限，避免改写用户 SQL。
     *
     * @param statement JDBC Statement
     * @param normalizedSql 规范化后的 SQL
     * @return 原始 SQL，不做分页拼接
     */
    private String applyQueryPreviewLimit(Statement statement, String normalizedSql) {
        int previewLimit = Math.max(1, panelProperties.getSqlQueryPreviewLimit());
        if (looksLikeRowsetQuery(normalizedSql)) {
            applyStatementFetchSize(statement, previewLimit);
            applyStatementMaxRows(statement, previewLimit);
        }
        return normalizedSql;
    }

    /**
     * 设置 JDBC 抓取批次，尽量减少一次性拉取过多结果集的压力。
     *
     * @param statement JDBC Statement
     * @param fetchSize 建议抓取数量
     */
    private void applyStatementFetchSize(Statement statement, int fetchSize) {
        try {
            statement.setFetchSize(fetchSize);
        } catch (SQLException ignored) {
            // ignore driver-specific fetchSize failures and continue
        }
    }

    /**
     * 设置最大返回行数，限制 SQL 工作台预览结果规模。
     *
     * @param statement JDBC Statement
     * @param previewLimit 预览上限
     */
    private void applyStatementMaxRows(Statement statement, int previewLimit) {
        try {
            statement.setMaxRows(previewLimit);
        } catch (SQLException ignored) {
            // ignore driver-specific maxRows failures and continue
        }
    }

    private boolean looksLikeRowsetQuery(String sql) {
        String normalized = sql == null ? "" : sql.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("select ")
                || normalized.startsWith("select\n")
                || normalized.startsWith("with ")
                || normalized.startsWith("with\n")
                || normalized.startsWith("show ")
                || normalized.startsWith("show\n")
                || normalized.startsWith("desc ")
                || normalized.startsWith("desc\n")
                || normalized.startsWith("describe ")
                || normalized.startsWith("describe\n")
                || normalized.startsWith("explain ")
                || normalized.startsWith("explain\n");
    }

    private String normalizeExecutableSql(String sql) {
        String normalized = sql == null ? "" : sql.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        // 去除尾部分号，避免在追加 LIMIT 等子句时引发语法错误
        while (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
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
            if (isMysqlCompatibleProduct(productName)) {
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

    /**
     * 查询 MySQL 进程列表，仅对 MySQL / MariaDB 生效。
     *
     * @param connectionId 连接标识
     * @return 进程查询结果
     * @author CH
     */
    @Override
    public JdbcQueryResult mysqlProcessList(String connectionId) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        try (Connection connection = requireDataSource(handle).getConnection()) {
            ensureMysqlConnection(connection);
            return runQuery(connectionId, "show full processlist", null, null);
        } catch (Exception e) {
            throw new IllegalStateException("读取 MySQL 进程列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 关闭 MySQL 进程，仅对 MySQL / MariaDB 生效。
     *
     * @param connectionId 连接标识
     * @param request 关闭参数
     * @return 关闭结果
     * @author CH
     */
    @Override
    public PanelMysqlProcessKillView killMysqlProcess(String connectionId, PanelMysqlProcessKillRequest request) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        List<Long> processIds = request == null ? null : request.getProcessIds();
        if (processIds == null || processIds.isEmpty()) {
            throw new IllegalArgumentException("进程 ID 不能为空");
        }
        try (Connection connection = requireDataSource(handle).getConnection();
             Statement statement = connection.createStatement()) {
            ensureMysqlConnection(connection);
            List<Long> killed = new ArrayList<>();
            Map<Long, String> failed = new LinkedHashMap<>();
            for (Long processId : processIds) {
                if (processId == null || processId <= 0) {
                    continue;
                }
                try {
                    statement.execute("kill " + processId);
                    killed.add(processId);
                } catch (Exception e) {
                    failed.put(processId, e.getMessage());
                }
            }
            return PanelMysqlProcessKillView.builder()
                    .killed(killed)
                    .failed(failed)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("关闭 MySQL 进程失败: " + e.getMessage(), e);
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

    /**
     * 判断是否为 MySQL 或与 MySQL 变量体系兼容的服务端（含 MariaDB），用于采集 {@code SHOW VARIABLES} 等元数据。
     *
     * @param databaseProductName {@link DatabaseMetaData#getDatabaseProductName()} 返回值，可为 null
     * @return 名称包含 mysql 或 mariadb（忽略大小写）时返回 true
     * @author CH
     */
    private boolean isMysqlCompatibleProduct(String databaseProductName) {
        if (databaseProductName == null) {
            return false;
        }
        String lower = databaseProductName.toLowerCase(Locale.ROOT);
        return lower.contains("mysql") || lower.contains("mariadb");
    }

    private Map<String, Object> queryMysqlVariables(Connection connection) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("""
                    show variables where Variable_name in (
                        'datadir','basedir','tmpdir','socket','port',
                        'max_connections','read_only','super_read_only',
                        'version_comment','innodb_buffer_pool_size','innodb_version',
                        'log_bin','binlog_format','character_set_server','collation_server'
                    )""")) {
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
            appendMysqlSessionVariable(statement, result, "max_connections");
        } catch (Exception ignored) {
            // ignore metadata probe failures from low-privilege accounts
        }
        return result;
    }

    /**
     * 在变量采集缺失时使用 {@code SELECT @@xxx} 方式补齐关键会话变量，降低不同驱动与托管数据库差异导致的空值概率。
     *
     * @param statement 已建立连接的 SQL 语句对象
     * @param result 目标变量集合
     * @param variableName 变量名（例如 max_connections）
     * @author CH
     */
    private void appendMysqlSessionVariable(
            Statement statement,
            Map<String, Object> result,
            String variableName) {
        if (result.containsKey(variableName)) {
            return;
        }
        try (ResultSet rs = statement.executeQuery("select @@" + variableName + " as " + variableName)) {
            if (rs.next()) {
                Object value = rs.getObject(variableName);
                if (value != null && !value.toString().isBlank()) {
                    result.put(variableName, value);
                }
            }
        } catch (Exception ignored) {
            // ignore fallback failures
        }
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

    // ── 备份管理 ──────────────────────────────────────────────────────────

    private final ScheduledExecutorService backupScheduler = Executors.newScheduledThreadPool(2);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> backupSchedules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PanelBackupSchedule> backupConfigs = new ConcurrentHashMap<>();

    // ── Binlog 增量监听状态 ──
    private final ConcurrentHashMap<String, OpsBackup> binlogMonitors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<PanelBinlogEvent>> binlogEvents = new ConcurrentHashMap<>();
    private static final int MAX_BINLOG_EVENTS = 500;

    @Override
    public PanelBackupSchedule startBackupSchedule(String connectionId, String cronExpression, String backupPath, Integer keepCount) {
        requireHandle(connectionId);
        if (cronExpression == null || cronExpression.isBlank()) {
            throw new IllegalArgumentException("cron 表达式不能为空");
        }
        // 解析 cron（5 段格式：秒 分 时 日 月）
        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 6) {
            throw new IllegalArgumentException("cron 表达式格式不正确，应为 5 段（分 时 日 月 周）或 6 段（秒 分 时 日 月 周）");
        }
        String resolvedPath = (backupPath == null || backupPath.isBlank())
                ? "./backup/panel-mysql"
                : backupPath.trim();
        int resolvedKeep = (keepCount == null || keepCount <= 0) ? 5 : keepCount;

        // 取消已有任务
        cancelBackupSchedule(connectionId);

        PanelBackupSchedule config = PanelBackupSchedule.builder()
                .connectionId(connectionId)
                .enabled(true)
                .cronExpression(cronExpression.trim())
                .backupPath(resolvedPath)
                .keepCount(resolvedKeep)
                .build();
        backupConfigs.put(connectionId, config);

        // 用简单定时替代 Spring CronSequenceGenerator，解析间隔
        long intervalSeconds = estimateCronInterval(cronExpression.trim());
        config.setNextRunTime(java.time.LocalDateTime.now().plusSeconds(intervalSeconds));

        ScheduledFuture<?> future = backupScheduler.scheduleAtFixedRate(
                () -> executeScheduledBackup(connectionId),
                intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        backupSchedules.put(connectionId, future);
        return config;
    }

    @Override
    public void stopBackupSchedule(String connectionId) {
        cancelBackupSchedule(connectionId);
        PanelBackupSchedule config = backupConfigs.remove(connectionId);
        if (config != null) {
            config.setEnabled(false);
        }
    }

    @Override
    public PanelBackupSchedule getBackupStatus(String connectionId) {
        PanelBackupSchedule config = backupConfigs.get(connectionId);
        if (config != null) {
            return config;
        }
        return PanelBackupSchedule.builder()
                .connectionId(connectionId)
                .enabled(false)
                .keepCount(5)
                .build();
    }

    @Override
    public PanelBackupRecord executeBackup(String connectionId) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        PanelBackupSchedule config = backupConfigs.getOrDefault(connectionId,
                PanelBackupSchedule.builder()
                        .connectionId(connectionId)
                        .backupPath("./backup/panel-mysql")
                        .keepCount(5)
                        .build());

        BackupConfig backupConfig = buildBackupConfig(handle, config);
        OpsNode localNode = new LocalOpsNode(connectionId);

        long startTime = System.currentTimeMillis();
        OpsBackup backup = loadOpsBackup("mysql");
        BackupResult result = backup.backup(localNode, backupConfig, new OpsListener() {});

        PanelBackupSchedule scheduleConfig = backupConfigs.get(connectionId);
        if (scheduleConfig != null) {
            scheduleConfig.setLastRunTime(java.time.LocalDateTime.now());
            scheduleConfig.setLastResult(result.isSuccess() ? "成功" : "失败: " + result.getMessage());
        }

        if (!result.isSuccess()) {
            throw new IllegalStateException("备份失败: " + result.getMessage(),
                    result.getException() instanceof Exception ? (Exception) result.getException() : null);
        }

        BackupRecord record = result.getRecord();
        if (record != null) {
            // 清理多余备份
            cleanOldBackups(config.getBackupPath(), connectionId, config.getKeepCount());
            return toBackupRecord(record);
        }
        return PanelBackupRecord.builder()
                .backupId(UUID.randomUUID().toString())
                .backupType("full")
                .description("备份完成，耗时 " + (System.currentTimeMillis() - startTime) + " ms")
                .build();
    }

    @Override
    public List<PanelBackupRecord> listBackups(String connectionId) {
        PanelBackupSchedule config = backupConfigs.get(connectionId);
        String backupPath = config != null ? config.getBackupPath() : "./backup/panel-mysql";
        String nodeId = connectionId;
        File backupDir = Path.of(backupPath, nodeId).toFile();
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            return List.of();
        }
        List<PanelBackupRecord> records = new ArrayList<>();
        File[] files = backupDir.listFiles((dir, name) -> name.endsWith(".sql.gz") || name.endsWith(".sql"));
        if (files == null) {
            return List.of();
        }
        for (File file : files) {
            records.add(PanelBackupRecord.builder()
                    .backupId(file.getName())
                    .fileName(file.getName())
                    .fileSize(file.length())
                    .backupTime(java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(file.lastModified()),
                            java.time.ZoneId.systemDefault()))
                    .backupType(file.getName().endsWith(".sql.gz") ? "full" : "raw")
                    .description("大小: " + formatFileSize(file.length()))
                    .build());
        }
        records.sort(Comparator.comparing(PanelBackupRecord::getBackupTime).reversed());
        return records;
    }

    @Override
    public boolean deleteBackup(String connectionId, String backupId) {
        if (backupId == null || backupId.isBlank()) {
            return false;
        }
        // 防止路径穿越
        if (backupId.contains("..") || backupId.contains("/") || backupId.contains("\\")) {
            return false;
        }
        PanelBackupSchedule config = backupConfigs.get(connectionId);
        String backupPath = config != null ? config.getBackupPath() : "./backup/panel-mysql";
        File file = Path.of(backupPath, connectionId, backupId).toFile();
        return file.exists() && file.delete();
    }

    // ── 备份内部方法 ──────────────────────────────────────

    /**
     * 加载 OpsBackup 实现，优先使用 SPI，回退到直接反射实例化。
     */
    private static OpsBackup loadOpsBackup(String type) {
        // 先尝试 SPI
        OpsBackup backup = OpsBackup.of(type);
        if (backup != null) {
            return backup;
        }
        // SPI 失败时，通过反射直接实例化已知实现
        Map<String, String> knownImplementations = Map.of(
                "mysql", "com.chua.mysql.support.backup.MysqlOpsBackup",
                "postgresql", "com.chua.postgres.support.backup.PostgresOpsBackup",
                "redis", "com.chua.redis.support.backup.RedisOpsBackup"
        );
        String className = knownImplementations.get(type);
        if (className == null) {
            throw new IllegalStateException("不支持的备份类型: " + type);
        }
        try {
            Class<?> clazz = Class.forName(className);
            return (OpsBackup) clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("备份模块未加载: " + className + "，请确保对应 starter 在 classpath 中", e);
        } catch (Exception e) {
            throw new IllegalStateException("无法实例化备份实现: " + className, e);
        }
    }

    @Override
    public java.io.File getBackupFile(String connectionId, String backupId) {
        if (backupId == null || backupId.isBlank()) {
            return null;
        }
        if (backupId.contains("..") || backupId.contains("/") || backupId.contains("\\")) {
            return null;
        }
        PanelBackupSchedule config = backupConfigs.get(connectionId);
        String backupPath = config != null ? config.getBackupPath() : "./backup/panel-mysql";
        java.io.File file = java.nio.file.Path.of(backupPath, connectionId, backupId).toFile();
        return file.exists() ? file : null;
    }

    private void cancelBackupSchedule(String connectionId) {
        ScheduledFuture<?> future = backupSchedules.remove(connectionId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void executeScheduledBackup(String connectionId) {
        try {
            PanelBackupSchedule config = backupConfigs.get(connectionId);
            if (config == null || !config.isEnabled()) {
                return;
            }
            executeBackup(connectionId);
        } catch (Exception e) {
            PanelBackupSchedule config = backupConfigs.get(connectionId);
            if (config != null) {
                config.setLastRunTime(java.time.LocalDateTime.now());
                config.setLastResult("定时备份失败: " + e.getMessage());
            }
        }
    }

    private BackupConfig buildBackupConfig(PanelConnectionHandle handle, PanelBackupSchedule config) {
        var definition = handle.getDefinition();
        BackupConfig bc = new BackupConfig();
        bc.setDbHost(definition.getHost());
        bc.setDbPort(definition.getPort() != null ? definition.getPort() : 3306);
        bc.setDbName(definition.getDatabaseName());
        bc.setDbUsername(definition.getUsername());
        bc.setDbPassword(definition.getPassword());
        bc.setBackupPath(config.getBackupPath());
        bc.setAppName(definition.getConnectionName() != null ? definition.getConnectionName() : "panel-mysql");
        bc.setCompress(true);
        bc.setKeepCount(config.getKeepCount() != null ? config.getKeepCount() : 5);
        Map<String, Object> extra = new LinkedHashMap<>();
        String host = definition.getHost();
        int port = definition.getPort() != null ? definition.getPort() : 3306;
        String dbName = definition.getDatabaseName();
        extra.put("jdbcUrl", "jdbc:mysql://" + host + ":" + port + (dbName != null && !dbName.isBlank() ? "/" + dbName : "") + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        bc.setExtra(extra);
        return bc;
    }

    private void cleanOldBackups(String backupPath, String nodeId, int keepCount) {
        File dir = Path.of(backupPath, nodeId).toFile();
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".sql.gz") || name.endsWith(".sql"));
        if (files == null || files.length <= keepCount) return;
        java.util.Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - keepCount; i++) {
            files[i].delete();
        }
    }

    // ── Binlog 增量监听 ──────────────────────────────────

    @Override
    public void startBinlogMonitor(String connectionId) {
        PanelConnectionHandle handle = requireHandle(connectionId);
        try {
            OpsBackup backup = loadOpsBackup("mysql");
            BackupConfig config = buildBackupConfig(handle,
                    backupConfigs.getOrDefault(connectionId, PanelBackupSchedule.builder().build()));

            // 注册回调，接收变更事件
            backup.addCallback(event -> {
                PanelBinlogEvent binlogEvent = PanelBinlogEvent.builder()
                        .type(event.getType().name())
                        .database(event.getDatabase())
                        .table(event.getTable())
                        .sql(event.getSql())
                        .timestamp(event.getTimestamp())
                        .build();
                List<PanelBinlogEvent> events = binlogEvents.computeIfAbsent(connectionId,
                        k -> Collections.synchronizedList(new ArrayList<>()));
                synchronized (events) {
                    events.add(binlogEvent);
                    if (events.size() > MAX_BINLOG_EVENTS) {
                        events.subList(0, events.size() - MAX_BINLOG_EVENTS).clear();
                    }
                }
            });

            backup.start(null, config, null);
            binlogMonitors.put(connectionId, backup);

        } catch (Exception e) {
            throw new RuntimeException("启动Binlog监听失败", e);
        }
    }

    @Override
    public void stopBinlogMonitor(String connectionId) {
        OpsBackup backup = binlogMonitors.remove(connectionId);
        if (backup != null) {
            backup.removeCallback(null);
            backup.stop();
        }
    }

    @Override
    public boolean isBinlogRunning(String connectionId) {
        OpsBackup backup = binlogMonitors.get(connectionId);
        return backup != null && backup.isRunning();
    }

    @Override
    public List<PanelBinlogEvent> getBinlogEvents(String connectionId, int limit) {
        List<PanelBinlogEvent> events = binlogEvents.get(connectionId);
        if (events == null) return List.of();
        synchronized (events) {
            int size = events.size();
            if (limit <= 0 || limit >= size) {
                return new ArrayList<>(events);
            }
            return new ArrayList<>(events.subList(Math.max(0, size - limit), size));
        }
    }

    private PanelBackupRecord toBackupRecord(BackupRecord record) {
        return PanelBackupRecord.builder()
                .backupId(record.getId())
                .fileName(record.getBackupPath() != null ? new File(record.getBackupPath()).getName() : record.getId())
                .fileSize(record.getSize())
                .backupTime(record.getBackupTime() != null
                        ? java.time.LocalDateTime.ofInstant(record.getBackupTime().toInstant(), java.time.ZoneId.systemDefault())
                        : java.time.LocalDateTime.now())
                .backupType(record.getBackupType() != null ? record.getBackupType() : "full")
                .description(record.getDescription())
                .build();
    }

    private long estimateCronInterval(String cronExpression) {
        // 简单估算：解析 cron 分钟字段
        // 常见格式： "0 0 2 * * ?" (每天凌晨2点) → 24h
        //           "0 */6 * * * ?" (每6小时) → 6h
        //           "0 0 */1 * * ?" (每小时) → 1h
        String[] parts = cronExpression.split("\\s+");
        // 去掉秒字段（如果存在6段格式）
        String minuteField = parts.length == 6 ? parts[1] : parts[0];
        String hourField = parts.length == 6 ? parts[2] : parts[1];

        if (minuteField.startsWith("*/")) {
            return Long.parseLong(minuteField.substring(2)) * 60L;
        }
        if (hourField.startsWith("*/")) {
            return Long.parseLong(hourField.substring(2)) * 3600L;
        }
        // 默认每天执行一次
        return 86400L;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 简单的本地 OpsNode 实现，用于 JDBC 驱动备份。
     */
    private static class LocalOpsNode implements OpsNode {
        private final String id;
        LocalOpsNode(String id) { this.id = id; }

        @Override
        public String getId() { return id; }

        @Override
        public String execute(String command) throws Exception {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("命令执行失败 (exit=" + exitCode + "): " + output);
            }
            return output;
        }
    }
}
