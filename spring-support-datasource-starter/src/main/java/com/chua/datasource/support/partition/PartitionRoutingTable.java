package com.chua.datasource.support.partition;

import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Calcite {@link ScannableTable} 实现 — 将源表（热表）与所有已发现的分区表组合为 UNION 全部 虚拟表，
 * 使通过 Calcite 执行的查询可以透明地访问全量冷热数据。
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class PartitionRoutingTable extends AbstractTable implements ScannableTable {

    /**
     * 来源表
    */
    private final String sourceTable;
    /**
     * 表名称
    */
    private final List<String> tableNames = new CopyOnWriteArrayList<>();
    /**
     * JDBC模板
    */
    private final JdbcTemplate jdbcTemplate;
    /**
     * 表评论
    */
    private final String tableComment;

    private volatile RelDataType rowType; // row类型

    /**
     * @param sourceTable   源表（热表）名
     * @param tableNames    所有物理表名列表（热表 + 分区表）
     * @param jdbcTemplate  JDBC 模板
     */
    public PartitionRoutingTable(String sourceTable, List<String> tableNames,
                                  JdbcTemplate jdbcTemplate) {
        this.sourceTable = PartitionRoutingService.validateTableName(sourceTable, "源表");
        this.tableNames.addAll(tableNames.stream()
                .map(t -> PartitionRoutingService.validateTableName(t, "分区表"))
                .toList());
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.tableComment = sourceTable + " (含 " + (tableNames.size() - 1) + " 个分区表)";
    }

    /**
     * 刷新分区表列表（热表始终在第一位）。
     * @param discoveredPartitions discovered分区
     */
    public void refreshTableNames(List<String> discoveredPartitions) {
        List<String> refreshed = new ArrayList<>();
        refreshed.add(sourceTable);
        discoveredPartitions.stream()
                .map(t -> PartitionRoutingService.validateTableName(t, "分区表"))
                .forEach(refreshed::add);
        this.tableNames.clear();
        this.tableNames.addAll(refreshed);
        log.debug("[分区路由] {} 表列表刷新: {} 个表", sourceTable, refreshed.size());
    }

    /**
     * 获取源table
     *
     * @return 获取源table的结果
     */
    public String getSourceTable() {
        return sourceTable;
    }

    /**
     * 获取table名称
     *
     * @return 获取table名称的结果
     */
    public List<String> getTableNames() {
        return List.copyOf(tableNames);
    }

    @Override
    /**
     * 获取row类型
    */
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        if (rowType == null) {
            synchronized (this) {
                if (rowType == null) {
                    rowType = buildRowType(typeFactory);
                }
            }
        }
        return rowType;
    }

    @Override
    /**
     * 扫描
    */
    public Enumerable<Object[]> scan(DataContext dataContext) {
        String sql = buildUnionAllSql();
        log.debug("[分区路由] 执行 UNION ALL 查询: {}", sql);
        List<Object[]> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            int colCount = rs.getMetaData().getColumnCount();
            Object[] values = new Object[colCount];
            for (int i = 0; i < colCount; i++) {
                values[i] = rs.getObject(i + 1);
            }
            return values;
        });
        return new AbstractEnumerable<Object[]>() {
            @Override
            /**
             * 迭代器
            */
            public Iterator<Object[]> iterator() {
                return rows.iterator();
            }

            @Override
            /**
             * Enumerator
            */
            public Enumerator<Object[]> enumerator() {
                return new Enumerator<Object[]>() {
                    /**
                     * 索引
                    */
                    private int index = -1;

                    @Override
                    /**
                     * 当前
                    */
                    public Object[] current() {
                        return rows.get(index);
                    }

                    @Override
                    /**
                     * 移动下一个
                    */
                    public boolean moveNext() {
                        return ++index < rows.size();
                    }

                    @Override
                    /**
                     * 重置
                    */
                    public void reset() {
                        index = -1;
                    }

                    @Override
                    /**
                     * 关闭
                    */
                    public void close() {
                    }
                };
            }
        };
    }

    /**
     * 构建union全部SQL
     *
     * @return 构建union全部sql的结果
     */
    private String buildUnionAllSql() {
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < tableNames.size(); i++) {
            if (i > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append("SELECT * FROM ").append(tableNames.get(i));
        }
        return sql.toString();
    }

    /**
     * 构建row类型
     *
     * @param typeFactory 类型工厂
     * @return 构建row类型的结果
     */
    private RelDataType buildRowType(RelDataTypeFactory typeFactory) {
        /**
         * 尝试。
         * @param Objects.requireNonNull(jdbcTemplate.getDataSource( 对象.requirenonnull(jdbctemplate.获取数据源(
         */
        try (var conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            /**
             * 尝试。
             * @param null 空
             */
            try (ResultSet rs = metaData.getColumns(null, null, sourceTable, null)) {
                List<String> names = new ArrayList<>();
                List<RelDataType> types = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString("COLUMN_NAME"));
                    int sqlType = rs.getInt("DATA_TYPE");
                    int nullable = rs.getInt("NULLABLE");
                    RelDataType calciteType = toCalciteType(typeFactory, sqlType);
                    if (nullable != DatabaseMetaData.columnNoNulls) {
                        calciteType = typeFactory.createTypeWithNullability(calciteType, true);
                    }
                    types.add(calciteType);
                }
                if (names.isEmpty()) {
                    log.warn("[分区路由] 源表 {} 未找到列元数据，使用保底类型", sourceTable);
                    return fallbackRowType(typeFactory);
                }
                return typeFactory.createStructType(types, names);
            }
        } catch (Exception e) {
            log.error("[分区路由] 获取表 {} 列元数据失败: {}", sourceTable, e.getMessage(), e);
            return fallbackRowType(typeFactory);
        }
    }

    /**
     * 转为calcite类型
     *
     * @param typeFactory 类型工厂
     * @param sqlType SQL类型
     * @return 转为calcite类型的结果
     */
    private static RelDataType toCalciteType(RelDataTypeFactory typeFactory, int sqlType) {
        SqlTypeName typeName = switch (sqlType) {
            case Types.VARCHAR, Types.CHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR ->
                    SqlTypeName.VARCHAR;
            case Types.NUMERIC, Types.DECIMAL -> SqlTypeName.DECIMAL;
            case Types.INTEGER -> SqlTypeName.INTEGER;
            case Types.BIGINT -> SqlTypeName.BIGINT;
            case Types.SMALLINT -> SqlTypeName.SMALLINT;
            case Types.TINYINT -> SqlTypeName.TINYINT;
            case Types.FLOAT, Types.REAL -> SqlTypeName.FLOAT;
            case Types.DOUBLE -> SqlTypeName.DOUBLE;
            case Types.BIT, Types.BOOLEAN -> SqlTypeName.BOOLEAN;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> SqlTypeName.TIMESTAMP;
            case Types.DATE -> SqlTypeName.DATE;
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> SqlTypeName.TIME;
            case Types.BLOB, Types.CLOB, Types.VARBINARY, Types.LONGVARBINARY -> SqlTypeName.VARBINARY;
            default -> SqlTypeName.VARCHAR;
        };
        return typeFactory.createSqlType(typeName);
    }

    /**
     * 降级row类型
     *
     * @param typeFactory 类型工厂
     * @return 降级row类型的结果
     */
    private static RelDataType fallbackRowType(RelDataTypeFactory typeFactory) {
        return typeFactory.createStructType(
                List.of(typeFactory.createSqlType(SqlTypeName.VARCHAR)),
                List.of("_dummy")
        );
    }
}
