package com.chua.starter.sync.data.support.adapter.jdbc;

import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * JDBC ResultSet流式读取Spliterator
 */
@Slf4j
public class JdbcResultSetSpliterator implements Spliterator<Map<String, Object>> {
    
    private final ResultSet resultSet;
    private final PreparedStatement statement;
    private final ResultSetMetaData metaData;
    private final int columnCount;
    
    public JdbcResultSetSpliterator(ResultSet resultSet, PreparedStatement statement) {
        this.resultSet = resultSet;
        this.statement = statement;
        try {
            this.metaData = resultSet.getMetaData();
            this.columnCount = metaData.getColumnCount();
        } catch (SQLException e) {
            throw new RuntimeException("获取ResultSet元数据失败", e);
        }
    }
    
    @Override
    public boolean tryAdvance(Consumer<? super Map<String, Object>> action) {
        try {
            if (resultSet.next()) {
                Map<String, Object> row = new HashMap<>(columnCount);
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    Object value = resultSet.getObject(i);
                    row.put(columnName, value);
                }
                
                action.accept(row);
                return true;
            }
            return false;
        } catch (SQLException e) {
            log.error("读取ResultSet失败", e);
            return false;
        }
    }
    
    @Override
    public Spliterator<Map<String, Object>> trySplit() {
        return null;
    }
    
    @Override
    public long estimateSize() {
        return Long.MAX_VALUE;
    }
    
    @Override
    public int characteristics() {
        return ORDERED | NONNULL | IMMUTABLE;
    }
}
