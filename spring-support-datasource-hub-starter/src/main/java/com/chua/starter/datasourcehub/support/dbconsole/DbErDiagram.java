package com.chua.starter.datasourcehub.support.dbconsole;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 库级 ER 图元数据：表清单 + 全部外键关系。
 *
 * <p>为什么不直接在前端循环调「逐表外键」端点：那样 40 张表就是 40 次
 * HTTP 往返，每次都要重新连库、重新切 catalog。ER 图是一次性的整体视图，
 * 这条路径在 40 表规模上会明显卡住；这里一次连接、一个
 * {@code getImportedKeys(null, null, null)} 就能把全库外键捞完 ——
 * JDBC 允许三个参数都传 null，表示「不限表」。</p>
 *
 * <p>顺带把**只作为外键目标出现、却不在表清单里**的表补进节点
 * （跨库外键、视图被引用等情形）：图上出现一条指向空白目标的边，
 * 用户没法判断是「表没画出来」还是「约束坏了」。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public final class DbErDiagram {

    private DbErDiagram() {
    }

    /**
     * 读出 ER 图所需的全部元数据。
     *
     * @param conn      已切好 catalog 的连接
     * @param catalog   目标库名，可为空（表示连接当前库）
     * @param tableLike 表名过滤（LIKE 模式，可为空）；为空表示全部
     * @return 表清单与外键关系
     * @throws java.sql.SQLException 读元数据失败
     */
    public static Map<String, Object> load(Connection conn, String catalog, String tableLike)
            throws java.sql.SQLException {
        DatabaseMetaData md = conn.getMetaData();
        String cat = catalog == null || catalog.isBlank() ? conn.getCatalog() : catalog;
        String schema = conn.getSchema();

        List<Map<String, Object>> tables = new ArrayList<>();
        Set<String> tableNames = new LinkedHashSet<>();
        // 与 requireTable 的四种 (catalog,schema) 组合同理，逐个试。
        // 但这里只取 TABLE，**不含 VIEW**：视图没有主键，画进 ER 图会误导。
        boolean found = false;
        for (int[] shape : new int[][] {{0, 1}, {0, 0}, {1, 1}, {1, 0}}) {
            try (ResultSet rs = md.getTables(
                    shape[0] == 0 ? cat : null,
                    shape[1] == 1 ? schema : null,
                    tableLike == null || tableLike.isBlank() ? null : tableLike,
                    new String[] {"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name == null || !tableNames.add(name.toLowerCase())) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", name);
                    row.put("comment", rs.getString("REMARKS"));
                    tables.add(row);
                }
            }
            if (!tables.isEmpty()) {
                found = true;
                break;
            }
        }
        if (!found) {
            // 组合表名过滤时若一种都没命中，可能是驱动对 pattern 的支持差异，
            // 退化成「取全部再在内存里过滤」，避免「有表却返回空列表」。
            tables.clear();
            tableNames.clear();
            try (ResultSet rs = md.getTables(cat, null, null, new String[] {"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name == null) {
                        continue;
                    }
                    if (tableLike != null && !tableLike.isBlank()
                            && !name.matches(wildcardToRegex(tableLike))) {
                        continue;
                    }
                    if (!tableNames.add(name.toLowerCase())) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", name);
                    row.put("comment", rs.getString("REMARKS"));
                    tables.add(row);
                }
            }
        }

        List<Map<String, Object>> relations = new ArrayList<>();
        Set<String> referencedOnly = new LinkedHashSet<>();
        // 外键**必须逐表**取，不能图省事写 getImportedKeys(cat, schema, null)：
        // JDBC 规范允许三个参数都传 null 表示「全库」，但 MySQL/MariaDB 驱动
        // 并不支持，会抛 "Table not specified."（2026-10-05 实测，整库 ER 图直接取不到）。
        // 逐表调用仍然只有**一次连接、一次库切换**，与「前端 N 次 HTTP」完全不同量级。
        for (Map<String, Object> t : tables) {
            String table = String.valueOf(t.get("name"));
            // 只有「这张表存在外键」或「四种组合都试完」才停：
            // 不能一见空结果就 break —— 那与「这张表根本没有外键」无法区分，
            // 结果是 catalog/schema 组合选错时把整批关系都丢掉。
            boolean probed = false;
            for (int[] shape : new int[][] {{0, 1}, {0, 0}, {1, 1}, {1, 0}}) {
                int before = relations.size();
                try (ResultSet rs = md.getImportedKeys(
                        shape[0] == 0 ? cat : null,
                        shape[1] == 1 ? schema : null,
                        table)) {
                    while (rs.next()) {
                        String fromTable = rs.getString("FKTABLE_NAME");
                        String toTable = rs.getString("PKTABLE_NAME");
                        if (fromTable == null || toTable == null) {
                            continue;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", rs.getString("FK_NAME"));
                        row.put("fromTable", fromTable);
                        row.put("fromColumn", rs.getString("FKCOLUMN_NAME"));
                        row.put("toTable", toTable);
                        row.put("toColumn", rs.getString("PKCOLUMN_NAME"));
                        // 外键规则码与 JDBC 规范一致：0=RESTRICT 1=CASCADE 2=SET NULL 3=NO ACTION 4=SET DEFAULT
                        row.put("updateRule", rs.getShort("UPDATE_RULE"));
                        row.put("deleteRule", rs.getShort("DELETE_RULE"));
                        relations.add(row);
                        if (!tableNames.contains(toTable.toLowerCase())) {
                            referencedOnly.add(toTable);
                        }
                    }
                }
                probed = true;
                if (relations.size() > before) {
                    break;
                }
            }
            if (!probed) {
                // 四种组合都抛异常时静默跳过该表：一张表的外键读不到，
                // 不该让整张 ER 图失败 —— 图少几条边比整张图打不开好。
                continue;
            }
        }
        // 补齐只被引用、不在清单里的表，否则图上会出现指向空白的边
        for (String name : referencedOnly) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            // 标注出来：用户一眼能看出「这张表没在当前库里」
            row.put("comment", "（被外键引用，但不在当前库的表清单里）");
            tables.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("catalog", cat);
        out.put("tables", tables);
        out.put("relations", relations);
        return out;
    }

    /**
     * 把 JDBC 的表名匹配模式转成正则。
     *
     * <p>{@code %} 匹配任意长度、{@code _} 匹配单字符，其余按字面量。
     * 直接用 {@code String.matches} 是错的：{@code _} 会被当正则量词，
     * 而表名里出现 {@code + . ( ) [ ]} 等字符时会抛
     * {@code PatternSyntaxException}，把「列个表」变成 500。</p>
     *
     * @param pattern JDBC 模式
     * @return 等价的正则
     */
    private static String wildcardToRegex(String pattern) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '%') {
                sb.append(".*");
            } else if (c == '_') {
                sb.append('.');
            } else if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
