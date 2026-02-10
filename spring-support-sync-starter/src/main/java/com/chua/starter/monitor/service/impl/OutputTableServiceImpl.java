package com.chua.starter.monitor.service.impl;

import com.chua.common.support.text.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.monitor.pojo.sync.ColumnDefinition;
import com.chua.starter.monitor.service.sync.OutputTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 输出节点表管理服务实现
 *
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutputTableServiceImpl implements OutputTableService {

    @Override
    public ReturnResult<Boolean> checkTableExists(String nodeConfig, String tableName) {
        try {
            Map<String, Object> config = parseConfig(nodeConfig);
            try (Connection conn = getConnection(config)) {
                DatabaseMetaData metaData = conn.getMetaData();
                try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
                    return ReturnResult.ok(rs.next());
                }
            }
        } catch (Exception e) {
            log.error("检查表存在性失败: {}", tableName, e);
            return ReturnResult.error("检查失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> createTable(String nodeConfig, String tableName, List<ColumnDefinition> columns) {
        if (columns == null || columns.isEmpty()) {
            return ReturnResult.error("列定义不能为空");
        }

        try {
            Map<String, Object> config = parseConfig(nodeConfig);
            String dbType = detectDbType(config);
            String sql = buildCreateTableSql(tableName, columns, dbType);

            log.info("自动建表SQL: {}", sql);

            try (Connection conn = getConnection(config);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                log.info("表 {} 创建成功", tableName);
                return ReturnResult.ok(true);
            }
        } catch (Exception e) {
            log.error("创建表失败: {}", tableName, e);
            return ReturnResult.error("创建表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<ColumnDefinition>> getTableStructure(String nodeConfig, String tableName) {
        try {
            Map<String, Object> config = parseConfig(nodeConfig);
            List<ColumnDefinition> columns = new ArrayList<>();

            try (Connection conn = getConnection(config)) {
                DatabaseMetaData metaData = conn.getMetaData();

                // 获取主键
                Set<String> primaryKeys = new HashSet<>();
                try (ResultSet pkRs = metaData.getPrimaryKeys(null, null, tableName)) {
                    while (pkRs.next()) {
                        primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                    }
                }

                // 获取列信息
                try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
                    int order = 0;
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");
                        ColumnDefinition col = ColumnDefinition.builder()
                                .name(columnName)
                                .type(mapJdbcTypeToSimpleType(rs.getInt("DATA_TYPE")))
                                .length(rs.getInt("COLUMN_SIZE"))
                                .scale(rs.getInt("DECIMAL_DIGITS"))
                                .nullable("YES".equals(rs.getString("IS_NULLABLE")))
                                .defaultValue(rs.getString("COLUMN_DEF"))
                                .primaryKey(primaryKeys.contains(columnName))
                                .autoIncrement("YES".equals(rs.getString("IS_AUTOINCREMENT")))
                                .comment(rs.getString("REMARKS"))
                                .order(order++)
                                .build();
                        columns.add(col);
                    }
                }
            }

            return ReturnResult.ok(columns);
        } catch (Exception e) {
            log.error("获取表结构失败: {}", tableName, e);
            return ReturnResult.error("获取表结构失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> dropTable(String nodeConfig, String tableName) {
        try {
            Map<String, Object> config = parseConfig(nodeConfig);
            try (Connection conn = getConnection(config);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + quoteIdentifier(tableName));
                log.info("表 {} 已删除", tableName);
                return ReturnResult.ok(true);
            }
        } catch (Exception e) {
            log.error("删除表失败: {}", tableName, e);
            return ReturnResult.error("删除表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<String> previewCreateTableSql(String tableName, List<ColumnDefinition> columns, String dbType) {
        if (columns == null || columns.isEmpty()) {
            return ReturnResult.error("列定义不能为空");
        }

        try {
            String sql = buildCreateTableSql(tableName, columns, StringUtils.defaultString(dbType, "mysql"));
            return ReturnResult.ok(sql);
        } catch (Exception e) {
            log.error("生成建表SQL失败", e);
            return ReturnResult.error("生成SQL失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> syncTableStructure(String nodeConfig, String tableName, List<ColumnDefinition> columns) {
        try {
            // 获取现有表结构
            ReturnResult<List<ColumnDefinition>> existingResult = getTableStructure(nodeConfig, tableName);
            if (!existingResult.isSuccess()) {
                return ReturnResult.error(existingResult.getMsg());
            }

            List<ColumnDefinition> existingColumns = existingResult.getData();
            Set<String> existingColumnNames = existingColumns.stream()
                    .map(c -> c.getName().toLowerCase())
                    .collect(Collectors.toSet());

            // 找出需要添加的列
            List<ColumnDefinition> columnsToAdd = columns.stream()
                    .filter(c -> !existingColumnNames.contains(c.getName().toLowerCase()))
                    .collect(Collectors.toList());

            if (columnsToAdd.isEmpty()) {
                return ReturnResult.ok(true);
            }

            Map<String, Object> config = parseConfig(nodeConfig);
            String dbType = detectDbType(config);

            try (Connection conn = getConnection(config);
                 Statement stmt = conn.createStatement()) {
                for (ColumnDefinition col : columnsToAdd) {
                    String alterSql = buildAddColumnSql(tableName, col, dbType);
                    log.info("添加列SQL: {}", alterSql);
                    stmt.execute(alterSql);
                }
            }

            log.info("表 {} 结构同步完成，添加了 {} 列", tableName, columnsToAdd.size());
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("同步表结构失败: {}", tableName, e);
            return ReturnResult.error("同步失败: " + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String nodeConfig) {
        if (StringUtils.isEmpty(nodeConfig)) {
            throw new IllegalArgumentException("节点配置不能为空");
        }
        return Json.fromJson(nodeConfig, Map.class);
    }

    private Connection getConnection(Map<String, Object> config) throws SQLException {
        String url = (String) config.get("url");
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        String driverClass = (String) config.get("driverClass");

        if (StringUtils.isEmpty(url)) {
            throw new IllegalArgumentException("数据库URL不能为空");
        }

        // 加载驱动
        if (StringUtils.isNotEmpty(driverClass)) {
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e) {
                log.warn("加载驱动失败: {}", driverClass);
            }
        }

        return DriverManager.getConnection(url, username, password);
    }

    private String detectDbType(Map<String, Object> config) {
        String url = (String) config.get("url");
        if (url == null) {
            return "mysql";
        }
        url = url.toLowerCase();
        if (url.contains("mysql")) {
            return "mysql";
        } else if (url.contains("postgresql")) {
            return "postgresql";
        } else if (url.contains("oracle")) {
            return "oracle";
        } else if (url.contains("sqlserver")) {
            return "sqlserver";
        } else if (url.contains("sqlite")) {
            return "sqlite";
        }
        return "mysql";
    }

    private String buildCreateTableSql(String tableName, List<ColumnDefinition> columns, String dbType) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(quoteIdentifier(tableName)).append(" (\n");

        List<String> columnDefs = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();

        // 按 order 排序
        columns.sort(Comparator.comparingInt(c -> c.getOrder() != null ? c.getOrder() : Integer.MAX_VALUE));

        for (ColumnDefinition col : columns) {
            columnDefs.add("  " + buildColumnDefinition(col, dbType));
            if (Boolean.TRUE.equals(col.getPrimaryKey())) {
                primaryKeys.add(quoteIdentifier(col.getName()));
            }
        }

        sb.append(String.join(",\n", columnDefs));

        if (!primaryKeys.isEmpty()) {
            sb.append(",\n  PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        }

        sb.append("\n)");

        // MySQL特有的表选项
        if ("mysql".equals(dbType)) {
            sb.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }

        return sb.toString();
    }

    private String buildColumnDefinition(ColumnDefinition col, String dbType) {
        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdentifier(col.getName())).append(" ");

        // 类型映射
        String sqlType = mapColumnType(col, dbType);
        sb.append(sqlType);

        // NOT NULL
        if (Boolean.FALSE.equals(col.getNullable())) {
            sb.append(" NOT NULL");
        }

        // AUTO_INCREMENT
        if (Boolean.TRUE.equals(col.getAutoIncrement())) {
            if ("mysql".equals(dbType)) {
                sb.append(" AUTO_INCREMENT");
            } else if ("postgresql".equals(dbType)) {
                // PostgreSQL使用SERIAL类型，已在mapColumnType中处理
            }
        }

        // DEFAULT
        if (StringUtils.isNotEmpty(col.getDefaultValue())) {
            sb.append(" DEFAULT ").append(formatDefaultValue(col.getDefaultValue(), col.getType()));
        }

        // COMMENT (MySQL)
        if ("mysql".equals(dbType) && StringUtils.isNotEmpty(col.getComment())) {
            sb.append(" COMMENT '").append(escapeString(col.getComment())).append("'");
        }

        return sb.toString();
    }

    private String buildAddColumnSql(String tableName, ColumnDefinition col, String dbType) {
        return "ALTER TABLE " + quoteIdentifier(tableName) + " ADD COLUMN " + buildColumnDefinition(col, dbType);
    }

    private String mapColumnType(ColumnDefinition col, String dbType) {
        String type = col.getType().toUpperCase();
        Integer length = col.getLength();
        Integer scale = col.getScale();

        switch (type) {
            case "VARCHAR":
            case "STRING":
                int varcharLen = length != null && length > 0 ? length : 255;
                return "VARCHAR(" + varcharLen + ")";
            case "INT":
            case "INTEGER":
                if (Boolean.TRUE.equals(col.getAutoIncrement()) && "postgresql".equals(dbType)) {
                    return "SERIAL";
                }
                return "INT";
            case "BIGINT":
            case "LONG":
                if (Boolean.TRUE.equals(col.getAutoIncrement()) && "postgresql".equals(dbType)) {
                    return "BIGSERIAL";
                }
                return "BIGINT";
            case "TEXT":
                return "TEXT";
            case "DATETIME":
            case "TIMESTAMP":
                return "postgresql".equals(dbType) ? "TIMESTAMP" : "DATETIME";
            case "DATE":
                return "DATE";
            case "DECIMAL":
            case "NUMERIC":
                int precision = length != null && length > 0 ? length : 10;
                int decScale = scale != null && scale > 0 ? scale : 2;
                return "DECIMAL(" + precision + "," + decScale + ")";
            case "BOOLEAN":
            case "BOOL":
                return "mysql".equals(dbType) ? "TINYINT(1)" : "BOOLEAN";
            case "FLOAT":
                return "FLOAT";
            case "DOUBLE":
                return "DOUBLE";
            case "BLOB":
                return "postgresql".equals(dbType) ? "BYTEA" : "BLOB";
            case "JSON":
                return "JSON";
            default:
                return type;
        }
    }

    private String mapJdbcTypeToSimpleType(int jdbcType) {
        switch (jdbcType) {
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.NVARCHAR:
            case Types.NCHAR:
                return "VARCHAR";
            case Types.INTEGER:
                return "INT";
            case Types.BIGINT:
                return "BIGINT";
            case Types.SMALLINT:
            case Types.TINYINT:
                return "INT";
            case Types.DECIMAL:
            case Types.NUMERIC:
                return "DECIMAL";
            case Types.FLOAT:
            case Types.REAL:
                return "FLOAT";
            case Types.DOUBLE:
                return "DOUBLE";
            case Types.DATE:
                return "DATE";
            case Types.TIME:
            case Types.TIMESTAMP:
                return "DATETIME";
            case Types.BOOLEAN:
            case Types.BIT:
                return "BOOLEAN";
            case Types.CLOB:
            case Types.LONGVARCHAR:
                return "TEXT";
            case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
                return "BLOB";
            default:
                return "VARCHAR";
        }
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    private String escapeString(String str) {
        return str.replace("'", "''").replace("\\", "\\\\");
    }

    private String formatDefaultValue(String defaultValue, String type) {
        if (defaultValue == null) {
            return "NULL";
        }
        String upperType = type.toUpperCase();
        if ("INT".equals(upperType) || "BIGINT".equals(upperType) ||
                "FLOAT".equals(upperType) || "DOUBLE".equals(upperType) ||
                "DECIMAL".equals(upperType) || "BOOLEAN".equals(upperType)) {
            return defaultValue;
        }
        if ("CURRENT_TIMESTAMP".equalsIgnoreCase(defaultValue) ||
                "NOW()".equalsIgnoreCase(defaultValue)) {
            return "CURRENT_TIMESTAMP";
        }
        return "'" + escapeString(defaultValue) + "'";
    }
}
