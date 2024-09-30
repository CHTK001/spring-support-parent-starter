package com.chua.report.server.starter.util;

import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.dialect.DialectFactory;
import com.chua.common.support.datasource.jdbc.option.DataSourceOptions;
import com.chua.common.support.datasource.meta.Column;
import com.chua.common.support.datasource.meta.Table;
import com.chua.common.support.datasource.utils.JdbcUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.StringUtils;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库处理程序
 *
 * @author CH
 */
public class DatabaseHandler implements AutoCloseable{

    private final DataSourceOptions databaseOptions;
    private final Connection connection;
    public DatabaseHandler(DataSourceOptions databaseOptions) {
        this.databaseOptions = databaseOptions;
        try {
            this.connection = JdbcUtils.getConnection(databaseOptions.getDriver(), databaseOptions.getUrl(), databaseOptions.getUsername(), databaseOptions.getPassword(), databaseOptions.getDriverPath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取表格
     *
     * @param database         数据库
     * @param tableNamePattern 表名模式
     * @return {@link List}<{@link Table}>
     * @throws SQLException SQLException
     */
    public List<Table> getTables(String database, String tableNamePattern) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        List<Table> results = new LinkedList<>();
        ResultSet resultSet = metaData.getTables(database, null, StringUtils.defaultString(tableNamePattern, "%"), new String[]{"TABLE"});
        while (resultSet.next()) {
            Table item = new Table();
            item.setTableName(resultSet.getString("TABLE_NAME"));
            item.setName(resultSet.getString("TABLE_NAME"));
            item.setDatabase(resultSet.getString("TABLE_CAT"));
            item.setComment(resultSet.getString("REMARKS"));
            results.add(item);
        }

        return results;
    }

    /**
     * 获取表格
     *
     * @param database         数据库
     * @param tableNamePattern 表名模式
     * @return {@link List}<{@link Table}>
     * @throws SQLException SQLException
     */
    public List<Table> getView(String database, String tableNamePattern) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        List<Table> results = new LinkedList<>();
        ResultSet resultSet = metaData.getTables(database, null, StringUtils.defaultString(tableNamePattern, "%"), new String[]{"VIEW"});
        while (resultSet.next()) {
            Table item = new Table();
            item.setTableName(resultSet.getString("TABLE_NAME"));
            item.setName(resultSet.getString("TABLE_NAME"));
            item.setDatabase(resultSet.getString("TABLE_CAT"));
            item.setComment(resultSet.getString("REMARKS"));
            results.add(item);
        }

        return results;
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }

    /**
     * 猜测方言
     *
     * @return {@link Dialect}
     * @throws Exception 异常
     */
    public Dialect guessDialect() throws Exception {
        return DialectFactory.create(connection);
    }

    /**
     * 获取列
     *
     * @param database 数据库
     * @param table    桌子
     * @return {@link List}<{@link Column}>
     * @throws SQLException SQLException
     */
    public List<Column> getColumns(String database, String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        List<Column> results = new LinkedList<>();
        ResultSet resultSet = metaData.getColumns(database, null, table,null);
        while (resultSet.next()) {
            Column column = new Column();
            column.setNodeId(resultSet.getString("COLUMN_NAME"));
            column.setJdbcType(resultSet.getString("TYPE_NAME"));
            column.setComment(resultSet.getString("REMARKS"));
            column.setNullable(resultSet.getInt("NULLABLE") == 0);
            column.setNodeName(resultSet.getString("TABLE_NAME"));
//            try {
//                column.setAutoIncrement("YES".equalsIgnoreCase(resultSet.getString("IS_AUTOINCREMENT")) );
//            } catch (SQLException ignored) {
//            }
            int columnSize = resultSet.getInt("COLUMN_SIZE");
            column.setLength(columnSize);
            column.setPrecision( resultSet.getInt("DECIMAL_DIGITS"));
            results.add(column);
        }

        return results;
    }

    /**
     * 更新列
     *
     * @param collect 收集
     */
    public void updateColumn(Set<Column> collect) throws SQLException {
        if(collect.isEmpty()) {
            return;
        }
        Column Column = CollectionUtils.findFirst(collect);
        List<Column> columns = getColumns(Column.getDatabaseName(), Column.getNodeName());
        List<Column> deleteColumn = findDeleteColumn(columns, collect);
        List<Column> addColumn = findAddColumn(columns, collect);
        List<Column> updateColumn = findUpdateColumn(columns, collect);
        connection.setAutoCommit(false);
        try {
            if(!deleteColumn.isEmpty()) {
                deleteTableColumn(connection, Column, deleteColumn);
            }

            if(!addColumn.isEmpty()) {
                saveTableColumn(connection, Column, addColumn);
            }

            if(!updateColumn.isEmpty()) {
                updateTableColumn(connection, Column, updateColumn);
            }
        } catch (Exception e) {
            connection.rollback();
            throw new RuntimeException(e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * 更新表列
     *
     * @param connection   联系
     * @param Column 列结果
     * @param updateColumn 更新列
     */
    private void updateTableColumn(Connection connection, Column Column, List<Column> updateColumn) {
        for (Column result : updateColumn) {
            StringBuilder stringBuilder = new StringBuilder("ALTER TABLE ")
                    .append("`").append(Column.getDatabaseName()).append("`")
                    .append(".`").append(Column.getNodeName()).append("`")
                    .append(" MODIFY COLUMN ");

            stringBuilder.append("`").append(result.getNodeId()).append("` ");
            stringBuilder.append(result.getJdbcType());
            if(result.getLength() > 0) {
                stringBuilder.append("(").append(result.getLength());
                if(result.getPrecision() > 0) {
                    stringBuilder.append(",").append(result.getPrecision());
                }
                stringBuilder.append(")");
            }

            stringBuilder.append(" ").append(" COMMENT '").append(result.getComment()).append("'");
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(stringBuilder.toString());
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 保存表列
     *
     * @param connection   联系
     * @param Column 列结果
     * @param addColumn    列
     */
    private void saveTableColumn(Connection connection, Column Column, List<Column> addColumn) {
        for (Column result : addColumn) {
            StringBuilder stringBuilder = new StringBuilder("ALTER TABLE ")
                    .append("`").append(Column.getDatabaseName()).append("`")
                    .append(".`").append(Column.getNodeName()).append("`")
                    .append(" ADD COLUMN ");

            stringBuilder.append("`").append(result.getNodeId()).append("` ");
            stringBuilder.append(result.getJdbcType());
            if(result.getLength() > 0) {
                stringBuilder.append("(").append(result.getLength());
                if(result.getPrecision() > 0) {
                    stringBuilder.append(",").append(result.getPrecision());
                }
                stringBuilder.append(")");
            }

            stringBuilder.append(" ").append(" COMMENT '").append(Column.getComment()).append("'");
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(stringBuilder.toString());
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 删除表列
     *
     * @param connection   联系
     * @param Column 列结果
     * @param deleteColumn 删除列
     */
    private void deleteTableColumn(Connection connection, Column Column, List<Column> deleteColumn) {
        StringBuilder stringBuilder = new StringBuilder("ALTER TABLE ")
                .append("`").append(Column.getDatabaseName()).append("`")
                .append(".`").append(Column.getNodeName()).append("`")
                .append(" DROP COLUMN ");

        String collect = deleteColumn.stream().map(it -> it.getNodeId()).collect(Collectors.joining("`,`"));
        stringBuilder.append("`").append(collect).append("`");

        try {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(stringBuilder.toString());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 查找更新列
     *
     * @param columns 列
     * @param collect 收集
     * @return {@link List}<{@link Column}>
     */
    private List<Column> findUpdateColumn(List<Column> columns, Set<Column> collect) {
        List<Column> rs = new ArrayList<>(columns.size());
        for (Column column : collect) {
            boolean isDiff = false;
            Column dbColumn = getColumn(column, columns);
            if(null == dbColumn) {
                continue;
            }

            if(!dbColumn.getNodeId().equals(column.getNodeId()) ||
                    !dbColumn.getJdbcType().equals(column.getJdbcType()) ||
                    dbColumn.getPrecision() != Optional.ofNullable(column.getPrecision()).orElse(0) ||
                    !ObjectUtils.equals(dbColumn.getComment(), column.getComment()) ||
                    dbColumn.getLength() != column.getLength()
            ) {
                rs.add(column);
            }
        }

        return rs;
    }

    /**
     * 获取列
     *
     * @param column  柱
     * @param columns 列
     * @return {@link Column}
     */
    private Column getColumn(Column column, List<Column> columns) {
        for (Column Column : columns) {
            if(Column.getNodeId().equals(column.getNodeId())) {
                return Column;
            }
        }

        return null;
    }

    /**
     * 查找添加列
     *
     * @param columns 列
     * @param collect 收集
     * @return {@link List}<{@link Column}>
     */
    private List<Column> findAddColumn(List<Column> columns, Set<Column> collect) {
        List<Column> rs = new ArrayList<>(columns.size());
        Set<String> strings = columns.stream().map(Column::getNodeId).collect(Collectors.toSet());
        for (Column column : collect) {
            if(!strings.contains(column.getNodeId())) {
                rs.add(column);
            }
        }

        return rs;
    }

    /**
     * 查找删除列
     *
     * @param columns 列
     * @param collect 收集
     * @return {@link List}<{@link Column}>
     */
    private List<Column> findDeleteColumn(List<Column> columns, Set<Column> collect) {
        List<Column> rs = new ArrayList<>(columns.size());
        Set<String> strings = collect.stream().map(Column::getNodeId).collect(Collectors.toSet());
        for (Column column : columns) {
            if(!strings.contains(column.getNodeId())) {
                rs.add(column);
            }
        }

        return rs;
    }

    /**
     * 更新表
     *
     * @param tableResult 表格结果
     */
    public void updateTable(Table tableResult) {
        StringBuilder stringBuilder = new StringBuilder("ALTER TABLE ")
                .append("`").append(tableResult.getDatabase()).append("`")
                .append(".`").append(tableResult.getTableName()).append("`")
                .append(" COMMENT '").append(tableResult.getComment()).append("'");

        try {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(stringBuilder.toString());
            }
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            if(e.getMessage().contains("doesn't exist")) {
                throw new RuntimeException(tableResult.getDatabase() + "." + tableResult.getTableName() + "不存在");
            }
            throw new RuntimeException(e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
