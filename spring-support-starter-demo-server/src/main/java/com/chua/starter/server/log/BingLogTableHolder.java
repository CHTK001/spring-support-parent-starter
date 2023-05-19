package com.chua.starter.server.log;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * mysql
 * @author CH
 */
public class BingLogTableHolder {

    @Resource
    private JdbcTemplate jdbcTemplate;

    private static final String INIT_SQL = "select * from information_schema.columns where table_schema not in ('information_schema', 'mysql', 'performance_schema', 'sys') ";

    private static final Table<String, Integer, String> TABLE_COLUMN_MAP = HashBasedTable.create();

    @PostConstruct
    public void initTable() {
        final List<TableColumn> query = jdbcTemplate.query(INIT_SQL, (rs, index) -> new TableColumn().mapRow(rs, index));
        for (TableColumn tableColumn : query) {
            TABLE_COLUMN_MAP.put(tableColumn.getTableSchema() + "." + tableColumn.getTableName(), tableColumn.getPosition(), tableColumn.getColumnName());
        }
//        TABLE_COLUMN_MAP.put(databaseName + ":" + tableName, collect);
    }

    public String getTable(String databaseName, String tableName, Integer position) {
        return TABLE_COLUMN_MAP.get(databaseName + "." + tableName, position);
    }

    public String getTable(String tableName, Integer position) {
        return TABLE_COLUMN_MAP.get(tableName, position);
    }

    @Getter
    @Setter
    public static class TableColumn implements RowMapper<TableColumn> {
        private String columnName;
        private Integer position;
        private String tableSchema;
        private String tableName;

        @Override
        public TableColumn mapRow(ResultSet rs, int i) throws SQLException {
            this.columnName = rs.getString("column_name");
            this.position = rs.getInt("ordinal_position");
            this.tableSchema = rs.getString("table_schema");
            this.tableName = rs.getString("table_name");
            return this;
        }
    }
}
