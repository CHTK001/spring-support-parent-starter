package com.chua.starter.datasource.support.graph;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取数据库全部表 → 表节点信息
 * <p>基于 JDBC 标准 {@link DatabaseMetaData}，跨数据库通用（MySQL/PG/Oracle/SQLServer/H2/SQLite/DM/Kingbase）。</p>
 *
 * @since 2026/8/5
 * @author CH
 */
@Slf4j
public class TableGraphReader {

    /**
     * 数据来源
    */
    private final DataSource dataSource;

    /**
     * 创建 table图计算读取 实例
     * @param dataSource 数据源
     */
    public TableGraphReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 读取全部表节点 + 外键边
     * @return 读取的结果
     */
    public GraphReadResult read() throws SQLException {
        /**
         * 尝试。
         * @param dataSource.getConnection( 数据源.getconnection(
         */
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            // 1) 读全部表
            Map<String, TableNodeInfo> tableMap = new LinkedHashMap<>();
            List<GraphEdge> edges = new ArrayList<>();
            /**
             * 尝试。
             * @param String[]{"TABLE"} 字符串[]{"TABLE"}
             */
            try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name == null) {
                        continue;
                    }
                    TableNodeInfo t = new TableNodeInfo();
                    t.setName(name);
                    t.setSchema(emptyToNull(rs.getString("TABLE_SCHEM")));
                    t.setRemark(rs.getString("REMARKS"));
                    t.setType(rs.getString("TABLE_TYPE"));
                    tableMap.put(name, t);
                }
            }

            // 2) 填充列、主键、外键
            for (TableNodeInfo t : tableMap.values()) {
                /**
                 * fillcolumns。
                 * @param meta meta
                 * @param t t
                 */
                fillColumns(meta, t);
                /**
                 * fillprimaryKey。
                 * @param meta meta
                 * @param t t
                 */
                fillPrimaryKeys(meta, t);
                /**
                 * fill导入Key。
                 * @param meta meta
                 * @param t t
                 * @param edges edges
                 * @param tableMap table映射
                 */
                fillImportedKeys(meta, t, edges, tableMap);
            }

            // 3) 统计出入度
            for (GraphEdge e : edges) {
                TableNodeInfo from = tableMap.get(e.from());
                TableNodeInfo to = tableMap.get(e.to());
                if (from != null) {
                    from.setOutRefCount(from.getOutRefCount() + 1);
                    from.setHasForeignKey(true);
                }
                if (to != null) {
                    to.setInRefCount(to.getInRefCount() + 1);
                }
            }

            log.debug("TableGraphReader: 读取 {} 张表，{} 条外键边",
                    tableMap.size(), edges.size());
            /**
             * 图计算读取结果。
             * @param ArrayList<>(tableMap.values( array列表<>(table映射.值(
             * @return 图计算读取结果的结果
             */
            return new GraphReadResult(new ArrayList<>(tableMap.values()), edges);
        }
    }

    /**
     * fillcolumns
     *
     * @param meta meta
     * @param t t
     */
    private void fillColumns(DatabaseMetaData meta, TableNodeInfo t) throws SQLException {
        /**
         * 尝试。
         * @param meta.getColumns(t.getSchema( meta.获取columns(t.获取模式(
         */
        try (ResultSet rs = meta.getColumns(t.getSchema(), null, t.getName(), "%")) {
            int ord = 0;
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                if (colName != null) {
                    t.getColumnNames().add(colName);
                    ord++;
                }
            }
            t.setColumnCount(ord);
        }
    }

    /**
     * fillprimaryKey
     *
     * @param meta meta
     * @param t t
     */
    private void fillPrimaryKeys(DatabaseMetaData meta, TableNodeInfo t) throws SQLException {
        /**
         * 尝试。
         * @param meta.getPrimaryKeys(t.getSchema( meta.获取primary键(t.获取模式(
         */
        try (ResultSet rs = meta.getPrimaryKeys(t.getSchema(), null, t.getName())) {
            Map<Short, String> ordered = new LinkedHashMap<>();
            while (rs.next()) {
                ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
            t.getPrimaryKeys().addAll(ordered.values());
        }
    }

    /**
     * fill导入Key
     * @param meta meta
     * @param t t
     * @param edges edges
     * @param tableMap table映射
     */
    private void fillImportedKeys(DatabaseMetaData meta, TableNodeInfo t,
                                  List<GraphEdge> edges, Map<String, TableNodeInfo> tableMap) throws SQLException {
        /**
         * 尝试。
         * @param meta.getImportedKeys(t.getSchema( meta.获取导入键(t.getschema(
         */
        try (ResultSet rs = meta.getImportedKeys(t.getSchema(), null, t.getName())) {
            while (rs.next()) {
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkCol = rs.getString("PKCOLUMN_NAME");
                String fkCol = rs.getString("FKCOLUMN_NAME");
                String fkName = rs.getString("FK_NAME");
                if (pkTable == null || !tableMap.containsKey(pkTable)) {
 // 目标表不在本批（视图或不同 模式），跳过
                    continue;
                }
                edges.add(new GraphEdge(
                        t.getName() + "->" + pkTable + "[" + fkCol + "=" + pkCol + "]",
                        t.getName(),
                        pkTable,
                        fkCol + " → " + pkCol,
                        fkName,
                        resolveRule(rs.getShort("UPDATE_RULE")),
                        resolveRule(rs.getShort("DELETE_RULE"))));
            }
        }
    }

    /**
     * 解析Rule
     *
     * @param rule rule
     * @return resolveRule的结果
     */
    private static String resolveRule(short rule) {
        switch (rule) {
            case DatabaseMetaData.importedKeyCascade: return "CASCADE";
            case DatabaseMetaData.importedKeySetNull: return "SET NULL";
            case DatabaseMetaData.importedKeySetDefault: return "SET DEFAULT";
            case DatabaseMetaData.importedKeyRestrict: return "RESTRICT";
            case DatabaseMetaData.importedKeyNoAction: return "NO ACTION";
            default: return "UNKNOWN";
        }
    }

    /**
     * 空转为空
     *
     * @param s s
     * @return 空转为空的结果
     */
    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    /**
     * 读取结果封装
     * @author CH
     * @since 4.0.0
     */
    public static class GraphReadResult {
        /**
         * Tables
        */
        public final List<TableNodeInfo> tables;
        /**
         * Edges
        */
        public final List<GraphEdge> edges;

        /**
         * 创建 图计算读取结果 实例
         * @param tables tables
         * @param edges edges
         */
        public GraphReadResult(List<TableNodeInfo> tables, List<GraphEdge> edges) {
            this.tables = tables;
            this.edges = edges;
        }
    }
}
