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
 * @author CH
 * @since 2026/8/5
 */
@Slf4j
public class TableGraphReader {

    private final DataSource dataSource;

    public TableGraphReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 读取全部表节点 + 外键边
     */
    public GraphReadResult read() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            // 1) 读全部表
            Map<String, TableNodeInfo> tableMap = new LinkedHashMap<>();
            List<GraphEdge> edges = new ArrayList<>();
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
                fillColumns(meta, t);
                fillPrimaryKeys(meta, t);
                fillImportedKeys(meta, t, edges, tableMap);
            }

            // 3) 统计出入度
            for (GraphEdge e : edges) {
                TableNodeInfo from = tableMap.get(e.getFrom());
                TableNodeInfo to = tableMap.get(e.getTo());
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
            return new GraphReadResult(new ArrayList<>(tableMap.values()), edges);
        }
    }

    private void fillColumns(DatabaseMetaData meta, TableNodeInfo t) throws SQLException {
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

    private void fillPrimaryKeys(DatabaseMetaData meta, TableNodeInfo t) throws SQLException {
        try (ResultSet rs = meta.getPrimaryKeys(t.getSchema(), null, t.getName())) {
            Map<Short, String> ordered = new LinkedHashMap<>();
            while (rs.next()) {
                ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
            t.getPrimaryKeys().addAll(ordered.values());
        }
    }

    private void fillImportedKeys(DatabaseMetaData meta, TableNodeInfo t,
                                  List<GraphEdge> edges, Map<String, TableNodeInfo> tableMap) throws SQLException {
        try (ResultSet rs = meta.getImportedKeys(t.getSchema(), null, t.getName())) {
            while (rs.next()) {
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkCol = rs.getString("PKCOLUMN_NAME");
                String fkCol = rs.getString("FKCOLUMN_NAME");
                String fkName = rs.getString("FK_NAME");
                if (pkTable == null || !tableMap.containsKey(pkTable)) {
                    // 目标表不在本批（视图或不同 schema），跳过
                    continue;
                }
                GraphEdge edge = new GraphEdge();
                edge.setFrom(t.getName());
                edge.setTo(pkTable);
                edge.setText(fkCol + " → " + pkCol);
                edge.setFkName(fkName);
                edge.setUpdateRule(resolveRule(rs.getShort("UPDATE_RULE")));
                edge.setDeleteRule(resolveRule(rs.getShort("DELETE_RULE")));
                edge.setId(t.getName() + "->" + pkTable + "[" + fkCol + "=" + pkCol + "]");
                edges.add(edge);
            }
        }
    }

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

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    /**
     * 读取结果封装
     */
    public static class GraphReadResult {
        public final List<TableNodeInfo> tables;
        public final List<GraphEdge> edges;

        public GraphReadResult(List<TableNodeInfo> tables, List<GraphEdge> edges) {
            this.tables = tables;
            this.edges = edges;
        }
    }
}
