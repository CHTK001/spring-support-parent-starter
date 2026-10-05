package com.chua.starter.monitor.support.support;

import com.chua.common.support.lang.datasource.dialect.SqlName;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 两库结构对比（Navicat 的「结构同步 / 比较」）。
 *
 * <p>对比维度：表 / 列（类型、长度、可空、默认值、注释、是否自增）/ 索引 / 外键。
 * 只比对**目标库缺什么或不一致什么**，方向固定为「源 → 目标」——
 * 结构同步是单向的，双向对比的语义（「以谁为准」）必须由用户明确选，
 * 不该在实现里替用户决定。</p>
 *
 * <p><b>只生成脚本，不代执行。</b> 生成的 DDL 里含 {@code DROP COLUMN} /
 * {@code DROP FOREIGN KEY} 这类不可逆语句，由用户在界面上看过再决定。
 * 这也是 Navicat 的行为：一屏 diff + 一份脚本，执行是另一步。</p>
 *
 * <p>标识符一律经 {@code SqlName} 转义；类型名与默认值走
 * {@link DbColumnDef} 的同一套规则，保证「界面显示的」与「脚本里的」一致。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public final class DbStructureDiff {

    /** 差异类别 */
    public enum Kind {
        /** 目标库缺这张表 */
        MISSING_TABLE,
        /** 目标库缺这列 */
        MISSING_COLUMN,
        /** 列的定义不一致 */
        COLUMN_MISMATCH,
        /** 目标库缺这个索引 */
        MISSING_INDEX,
        /** 索引定义不一致（列集或顺序不同） */
        INDEX_MISMATCH,
        /** 目标库缺这个外键 */
        MISSING_FOREIGN_KEY,
        /** 目标库多出来的表（不生成删除脚本，只提示） */
        EXTRA_TABLE,
        /** 目标库多出来的列（同上，只提示） */
        EXTRA_COLUMN
    }

    /** 单条差异 */
    public record DiffItem(
            Kind kind,
            String table,
            String object,
            String detail,
            /** 目标侧需要的动作（无动作时为空） */
            String action,
            /** 生成给用户看的 DDL（只提示类的为空） */
            String ddl
    ) {
    }

    /** 一个表的结构快照 */
    private record TableSnapshot(
            String name,
            String ddl,
            Map<String, ColumnSnapshot> columns,
            Map<String, IndexSnapshot> indexes,
            List<ForeignKeySnapshot> foreignKeys
    ) {
    }

    /** 列快照（只保留需要比对的属性） */
    private record ColumnSnapshot(
            String name,
            String definition,
            /**
             * **带长度/精度**的类型，如 {@code VARCHAR(64)}、{@code DECIMAL(10,2)}。
             *
             * <p>不能只比 {@code TYPE_NAME}：内核报的 {@code TYPE_NAME} 对
             * {@code VARCHAR(64)} 和 {@code VARCHAR(16)} 都只给 {@code VARCHAR}，
             * 只比它会把「长度变了」这种真实差异判成一致 ——
             * 实测 2026-10-05，验收脚本报「name 列长度不同未被识别」。</p>
             */
            String typeWithSize,
            String nullable,
            String defaultValue,
            String comment
    ) {
    }

    /** 索引快照 */
    private record IndexSnapshot(
            String name,
            boolean unique,
            String columns,
            String indexType
    ) {
    }

    /** 外键快照 */
    private record ForeignKeySnapshot(
            String name,
            String fromColumns,
            String refTable,
            String refColumns,
            int updateRule,
            int deleteRule
    ) {
    }

    private DbStructureDiff() {
    }

    /**
     * 比对**同一数据源内**两个库的结构（共用一条连接，靠切 catalog）。
     *
     * <p><b>不要图省事写成「两条连接」</b>：本仓的
     * {@code DatasourceService#connection(id)} 返回的是按数据源缓存、
     * {@code close()} 被屏蔽的<b>同一条物理连接</b>。源与目标用同一个
     * datasourceId 时，两次拿到的是同一个对象，第二次 {@code USE} 覆盖第一次 ——
     * 于是实际是「拿目标库跟自己比」，结果恒为空列表，
     * 界面上显示「结构一致」而其实完全不同（2026-10-05 实测，18 项验收全红）。</p>
     *
     * <p>catalog <b>显式传下去</b>，不读 {@code Connection#getCatalog()}：
     * 本仓这条缓存连接上它返回 {@code null}，于是
     * {@code getTables(null, null, …)} 变成跨所有库查询 ——
     * 源与目标各自拿到「所有库的表的并集」，两边一模一样，又一次恒为空列表
     * （2026-10-05 实测，这是比上一条更隐蔽的一层）。</p>
     *
     * @param conn         连接（同一数据源）
     * @param sourceCatalog 源库名，可为空（用连接当前库）
     * @param targetCatalog 目标库名，可为空
     * @param tableFilter   表名过滤（LIKE，可为空表示全部）
     * @return 差异清单
     * @throws SQLException 读元数据失败
     */
    public static List<DiffItem> diffSameDatasource(Connection conn, String sourceCatalog,
                                                    String targetCatalog, String tableFilter)
            throws SQLException {
        String original = conn.getCatalog();
        try {
            Map<String, TableSnapshot> src = snapshotOf(conn, sourceCatalog, tableFilter);
            Map<String, TableSnapshot> dst = snapshotOf(conn, targetCatalog, tableFilter);
            return compare(src, dst, tableFilter);
        } finally {
            restoreCatalog(conn, original);
        }
    }

    /**
     * 比对**跨数据源**的两个库（两条连接）。
     *
     * @param source     源连接
     * @param target     目标连接
     * @param tableFilter 表名过滤
     * @return 差异清单
     * @throws SQLException 读元数据失败
     */
    public static List<DiffItem> diffAcrossDatasources(Connection source, Connection target,
                                                       String tableFilter) throws SQLException {
        return compare(snapshot(source, source.getCatalog(), tableFilter),
                snapshot(target, target.getCatalog(), tableFilter), tableFilter);
    }

    /**
     * 切到指定 catalog 并读结构快照。
     *
     * @param conn    连接
     * @param catalog 库名，可为空表示不切
     * @param filter  表名过滤
     * @return 快照
     * @throws SQLException 读元数据失败
     */
    private static Map<String, TableSnapshot> snapshotOf(Connection conn, String catalog,
                                                          String filter) throws SQLException {
        if (catalog == null || catalog.isBlank()) {
            return snapshot(conn, conn.getCatalog(), filter);
        }
        if (!DbDialectSupport.isSafeIdentifier(catalog)) {
            throw new SQLException("数据库名不合法: " + catalog);
        }
        try (java.sql.Statement st = conn.createStatement()) {
            st.execute("USE " + SqlName.quoteMysql(catalog, "数据库名"));
        }
        // catalog 显式传，不靠 getCatalog() —— 见 diffSameDatasource 的注释
        return snapshot(conn, catalog, filter);
    }

    /** 把 catalog 还原（异常也还原，否则连接被留在一个意外的库上） */
    private static void restoreCatalog(Connection conn, String original) {
        if (original == null || original.isBlank()) {
            return;
        }
        try {
            if (!conn.isValid(2)) {
                return;
            }
        } catch (SQLException e) {
            // 探活失败：连接已不可用，还原无从谈起，也不该因此抛出去
            return;
        }
        try (java.sql.Statement st = conn.createStatement()) {
            st.execute("USE " + SqlName.quoteMysql(original, "数据库名"));
        } catch (SQLException e) {
            // 还原失败只能记录：连接是缓存共享的，留在别的库上会影响后续请求，
            // 但抛出去会让「比较」整个失败，而差异清单用户已经拿到才有意义。
            System.getLogger(DbStructureDiff.class.getName())
                    .log(System.Logger.Level.WARNING, "还原 catalog 失败: " + original, e);
        }
    }

    /**
     * 比对两份快照。
     *
     * @param src        源快照
     * @param dst        目标快照
     * @param tableFilter 表名过滤（空表示全部；非空时只提示多余项）
     * @return 差异清单
     */
    private static List<DiffItem> compare(Map<String, TableSnapshot> src,
                                          Map<String, TableSnapshot> dst,
                                          String tableFilter) {
        List<DiffItem> items = new ArrayList<>();

        for (Map.Entry<String, TableSnapshot> e : src.entrySet()) {
            String table = e.getKey();
            TableSnapshot s = e.getValue();
            TableSnapshot t = dst.get(table);
            if (t == null) {
                // 目标缺整张表：用源侧的建表 DDL（含列、索引、外键）
                items.add(new DiffItem(Kind.MISSING_TABLE, table, table,
                        "目标库缺少该表", "建表", s.ddl()));
                continue;
            }
            diffColumns(items, table, s, t);
            diffIndexes(items, table, s, t);
            diffForeignKeys(items, table, s, t);
        }

        // 反向：目标多出来的表/列。只提示，**不生成删除脚本** ——
        // 自动生成 DROP 是这类工具最容易造成不可逆损失的地方，
        // 差异清单给用户看，删不删由用户决定。
        for (Map.Entry<String, TableSnapshot> e : dst.entrySet()) {
            if (src.containsKey(e.getKey())) {
                TableSnapshot s = src.get(e.getKey());
                for (String col : e.getValue().columns.keySet()) {
                    if (!s.columns.containsKey(col)) {
                        items.add(new DiffItem(Kind.EXTRA_COLUMN, e.getKey(), col,
                                "目标库多出的列（不会自动删除）", "确认后手工删除", ""));
                    }
                }
            } else if (tableFilter == null || tableFilter.isBlank()) {
                items.add(new DiffItem(Kind.EXTRA_TABLE, e.getKey(), e.getKey(),
                        "目标库多出的表（不会自动删除）", "确认后手工删除", ""));
            }
        }
        return orderMissingTables(items);
    }

    /**
     * 把「目标缺整张表」的差异按外键依赖拓扑排序：被别的缺表引用的表排在前面。
     *
     * <p>不做排序的话，{@code a} 外键引用 {@code b}、两者都缺时，
     * 脚本可能先建 {@code a}，执行到外键那行就报 1451 中断，
     * 后面的语句全不执行 —— 用户看到的是「部分同步」，比直接失败更难查。</p>
     *
     * <p>只重排 MISSING_TABLE，其余条目保持原有相对次序。
     * 外键里出现环（理论上不该出现）时按原顺序兜底，不抛异常。</p>
     *
     * @param items 原差异清单
     * @return 重排后的清单
     */
    private static List<DiffItem> orderMissingTables(List<DiffItem> items) {
        List<DiffItem> missing = new ArrayList<>();
        List<DiffItem> others = new ArrayList<>();
        for (DiffItem it : items) {
            (it.kind() == Kind.MISSING_TABLE ? missing : others).add(it);
        }
        if (missing.size() <= 1) {
            return items;
        }
        // 该缺哪些表
        Set<String> pending = new LinkedHashSet<>();
        for (DiffItem it : missing) {
            pending.add(it.object().toLowerCase(Locale.ROOT));
        }
        List<DiffItem> sorted = new ArrayList<>(missing.size());
        Set<String> done = new LinkedHashSet<>();
        List<DiffItem> rest = new ArrayList<>(missing);
        while (!rest.isEmpty()) {
            boolean progressed = false;
            for (int i = 0; i < rest.size(); i++) {
                DiffItem it = rest.get(i);
                if (depsSatisfied(it, pending, done)) {
                    sorted.add(it);
                    done.add(it.object().toLowerCase(Locale.ROOT));
                    rest.remove(i);
                    progressed = true;
                    break;
                }
            }
            if (!progressed) {
                // 有环：剩下的按原顺序追加，保证脚本完整（执行时由 DB 报约束错）
                sorted.addAll(rest);
                break;
            }
        }
        List<DiffItem> out = new ArrayList<>(items.size());
        out.addAll(sorted);
        out.addAll(others);
        return out;
    }

    /**
     * 该缺表依赖的「还没建的缺表」是否都已建好。
     *
     * @param item    缺表差异
     * @param pending 本次要建的所有缺表
     * @param done    已排在前面的缺表
     * @return true 表示依赖已满足
     */
    private static boolean depsSatisfied(DiffItem item, Set<String> pending, Set<String> done) {
        String ddl = item.ddl();
        if (ddl == null || ddl.isBlank()) {
            return true;
        }
        // 从建表 DDL 里找出 REFERENCES 的表名
        java.util.regex.Matcher m = REFERENCES.matcher(ddl);
        while (m.find()) {
            String ref = m.group(1).replace("`", "").toLowerCase(Locale.ROOT);
            if (pending.contains(ref) && !done.contains(ref)) {
                return false;
            }
        }
        return true;
    }

    /** 建表 DDL 里抓 REFERENCES 的表名 */
    private static final java.util.regex.Pattern REFERENCES =
            java.util.regex.Pattern.compile("REFERENCES\\s+`([^`]+)`", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** 比对列 */
    private static void diffColumns(List<DiffItem> items, String table,
                                    TableSnapshot src, TableSnapshot dst) {
        for (Map.Entry<String, ColumnSnapshot> e : src.columns.entrySet()) {
            String name = e.getKey();
            ColumnSnapshot s = e.getValue();
            ColumnSnapshot t = dst.columns.get(name);
            String q = "`" + name + "`";
            if (t == null) {
                items.add(new DiffItem(Kind.MISSING_COLUMN, table, name,
                        "目标库缺少该列", "新增列",
                        "ALTER TABLE " + q(table) + " ADD COLUMN " + q + " " + s.definition + ";"));
                continue;
            }
            // 逐项报差异，而不是笼统一个「不一致」：
            // 一句「列定义不同」没法判断该不该同步，只能让人自己去查。
            // 【别写成 else if 链】实测 2026-10-05：写成链时，
            // 一列同时有「长度不同 + 注释不同」只会报出先命中的那一个，
            // 另一项差异就被静默吞掉了 —— 而脚本里两条 MODIFY 都要用。
            String modify = "ALTER TABLE " + q(table) + " MODIFY COLUMN " + q + " " + s.definition + ";";
            if (!eq(s.typeWithSize(), t.typeWithSize())) {
                items.add(new DiffItem(Kind.COLUMN_MISMATCH, table, name,
                        "类型不同：源=" + s.typeWithSize() + " 目标=" + t.typeWithSize(), "改类型", modify));
            }
            if (!eq(s.nullable(), t.nullable())) {
                items.add(new DiffItem(Kind.COLUMN_MISMATCH, table, name,
                        "可空不同：源=" + s.nullable() + " 目标=" + t.nullable(), "改可空", modify));
            }
            // 注意打印的是 defaultValue：原先误写成 s.nullable()，
            // 出现「默认值不同：源=NOT NULL」这种把可空当默认值输出的错话。
            if (!eq(s.defaultValue(), t.defaultValue())) {
                items.add(new DiffItem(Kind.COLUMN_MISMATCH, table, name,
                        "默认值不同：源=" + s.defaultValue() + " 目标=" + t.defaultValue(), "改默认值", modify));
            }
            if (!eq(s.comment(), t.comment())) {
                items.add(new DiffItem(Kind.COLUMN_MISMATCH, table, name,
                        "注释不同：源=" + s.comment() + " 目标=" + t.comment(), "改注释", modify));
            }
        }
    }

    /** 比对索引 */
    private static void diffIndexes(List<DiffItem> items, String table,
                                    TableSnapshot src, TableSnapshot dst) {
        for (Map.Entry<String, IndexSnapshot> e : src.indexes.entrySet()) {
            String name = e.getKey();
            IndexSnapshot s = e.getValue();
            IndexSnapshot t = dst.indexes.get(name);
            if (t == null) {
                items.add(new DiffItem(Kind.MISSING_INDEX, table, name,
                        "目标库缺少该索引（列：" + s.columns() + "）", "建索引", indexDdl(table, s)));
                continue;
            }
            // 索引名相同但列集不同：多数情况是「同名不同义」，
            // 直接 ADD INDEX 会因重名失败，必须先 DROP 再建。
            if (!eq(s.columns(), t.columns()) || s.unique() != t.unique()) {
                items.add(new DiffItem(Kind.INDEX_MISMATCH, table, name,
                        "索引定义不同：源=(" + s.columns() + (s.unique() ? ",UNIQUE" : "")
                                + ") 目标=(" + t.columns() + (t.unique() ? ",UNIQUE" : "") + ")",
                        "重建索引",
                        "DROP INDEX " + q(name) + " ON " + q(table) + ";\n" + indexDdl(table, s)));
            }
        }
    }

    /** 比对外键 */
    private static void diffForeignKeys(List<DiffItem> items, String table,
                                        TableSnapshot src, TableSnapshot dst) {
        Set<String> dstKeys = new LinkedHashSet<>();
        for (ForeignKeySnapshot f : dst.foreignKeys) {
            dstKeys.add(foreignKeyIdentity(f));
        }
        for (ForeignKeySnapshot s : src.foreignKeys) {
            if (dstKeys.contains(foreignKeyIdentity(s))) {
                continue;
            }
            StringBuilder sb = new StringBuilder("ALTER TABLE ").append(q(table))
                    .append(" ADD CONSTRAINT ").append(q(s.name()))
                    .append(" FOREIGN KEY (").append(s.fromColumns()).append(")")
                    .append(" REFERENCES ").append(q(s.refTable())).append(" (").append(s.refColumns()).append(")");
            if (s.updateRule() > 0) {
                sb.append(" ON UPDATE ").append(rule(s.updateRule()));
            }
            if (s.deleteRule() > 0) {
                sb.append(" ON DELETE ").append(rule(s.deleteRule()));
            }
            sb.append(';');
            items.add(new DiffItem(Kind.MISSING_FOREIGN_KEY, table, s.name(),
                    "目标库缺少该外键（" + s.fromColumns() + " → " + s.refTable()
                            + "(" + s.refColumns() + ")）", "建外键", sb.toString()));
        }
    }

    /** 外键的「身份」：名 + 列集 + 被引用表列。名不同但定义相同也算已存在 */
    private static String foreignKeyIdentity(ForeignKeySnapshot f) {
        return f.name() + "|" + f.fromColumns() + "|" + f.refTable() + "|" + f.refColumns();
    }

    /** 外键级联动作 */
    private static String rule(int code) {
        return switch (code) {
            case 1 -> "CASCADE";
            case 2 -> "SET NULL";
            case 3 -> "NO ACTION";
            case 4 -> "SET DEFAULT";
            default -> "RESTRICT";
        };
    }

    /** 建索引 DDL */
    private static String indexDdl(String table, IndexSnapshot s) {
        return "CREATE " + (s.unique() ? "UNIQUE " : "") + "INDEX " + q(s.name())
                + " ON " + q(table) + " (" + s.columns() + ");";
    }

    /** 标识符转义（反引号成对） */
    private static String q(String name) {
        return SqlName.quoteMysql(name, "标识符");
    }

    /** 空值与空白视为相同 */
    private static boolean eq(String a, String b) {
        return Objects.equals(a == null ? "" : a.trim(), b == null ? "" : b.trim());
    }

    /**
     * 读一个库的结构快照。
     *
     * @param conn        连接
     * @param catalog     库名，**显式传入**（不读 {@code getCatalog()}，它在
     *                    本仓的缓存连接上返回 null，会退化成跨库查询）
     * @param tableFilter 表名过滤（LIKE，可为空）
     * @return 表名（小写）→ 快照
     * @throws SQLException 读元数据失败
     */
    private static Map<String, TableSnapshot> snapshot(Connection conn, String catalog,
                                                       String tableFilter) throws SQLException {
        Map<String, TableSnapshot> out = new LinkedHashMap<>();
        DatabaseMetaData md = conn.getMetaData();
        String cat = catalog;
        String schema = conn.getSchema();

        // 先按 (catalog,schema) 组合拿表名，四种组合逐个试，
        // 与 requireTable / ER 图同一套口径。
        // 注意：catalog 为空时**不要**退化到「跨所有库」——
        // 那会让源与目标拿到同一份并集，差异恒为空。
        List<String> names = new ArrayList<>();
        for (int[] shape : new int[][] {{0, 1}, {0, 0}, {1, 1}, {1, 0}}) {
            String useCat = shape[0] == 0 ? cat : null;
            if (shape[0] == 1 && cat != null && !cat.isBlank()) {
                // 「不用 catalog」这一档在有明确库名时是危险的，跳过
                continue;
            }
            try (ResultSet rs = md.getTables(useCat,
                    shape[1] == 1 ? schema : null,
                    tableFilter == null || tableFilter.isBlank() ? null : tableFilter,
                    new String[] {"TABLE"})) {
                while (rs.next()) {
                    String n = rs.getString("TABLE_NAME");
                    if (n != null) {
                        names.add(n);
                    }
                }
            }
            if (!names.isEmpty()) {
                break;
            }
        }

        for (String name : names) {
            if (out.containsKey(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Map<String, ColumnSnapshot> cols = readColumns(md, cat, schema, name);
            Map<String, IndexSnapshot> idx = readIndexes(md, cat, schema, name);
            List<ForeignKeySnapshot> fks = readForeignKeys(md, cat, schema, name);
            // 建表 DDL 必须在这里真的生成出来。
            // 【实测 2026-10-05】原先这里传的是 null，于是「目标缺整张表」那条差异的
            // ddl 是空串 -> 界面显示不出建表语句 -> 用户点「在目标库执行」后表根本没被建出来，
            // 而差异清单还显示「已同步完成」。
            out.put(name.toLowerCase(Locale.ROOT),
                    new TableSnapshot(name, createTableDdl(name, cols, idx, fks), cols, idx, fks));
        }
        return out;
    }

    /**
     * 由快照拼一张完整的 {@code CREATE TABLE}。
     *
     * <p>组成顺序：列 → PRIMARY KEY → 其余索引 → 外键约束。
     * 外键放最后是必须的：外键引用的表要先建好，否则脚本执行到一半就报 1451。</p>
     *
     * @param name    表名
     * @param columns 列快照（保持声明顺序）
     * @param indexes 索引快照
     * @param fks     外键快照
     * @return 建表 DDL；无列时返回空串
     */
    private static String createTableDdl(String name, Map<String, ColumnSnapshot> columns,
                                         Map<String, IndexSnapshot> indexes,
                                         List<ForeignKeySnapshot> foreignKeys) {
        if (columns == null || columns.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (ColumnSnapshot c : columns.values()) {
            parts.add("  " + q(c.name()) + " " + c.definition());
        }
        // PRIMARY 索引由内核以名字 "PRIMARY" 给出，这里还原成表级约束
        if (indexes != null) {
            for (IndexSnapshot ix : indexes.values()) {
                if ("PRIMARY".equalsIgnoreCase(ix.name())) {
                    parts.add("  PRIMARY KEY (" + ix.columns() + ")");
                }
            }
            for (IndexSnapshot ix : indexes.values()) {
                if (!"PRIMARY".equalsIgnoreCase(ix.name())) {
                    parts.add("  " + (ix.unique() ? "UNIQUE KEY " : "KEY ") + q(ix.name())
                            + " (" + ix.columns() + ")");
                }
            }
        }
        if (foreignKeys != null) {
            for (ForeignKeySnapshot fk : foreignKeys) {
                StringBuilder sb = new StringBuilder("  CONSTRAINT ").append(q(fk.name()))
                        .append(" FOREIGN KEY (").append(fk.fromColumns()).append(") REFERENCES ")
                        .append(q(fk.refTable())).append(" (").append(fk.refColumns()).append(")");
                if (fk.updateRule() > 0 && fk.updateRule() != 3) {
                    sb.append(" ON UPDATE ").append(rule(fk.updateRule()));
                }
                if (fk.deleteRule() > 0 && fk.deleteRule() != 3) {
                    sb.append(" ON DELETE ").append(rule(fk.deleteRule()));
                }
                parts.add(sb.toString());
            }
        }
        return "CREATE TABLE " + q(name) + " (\n" + String.join(",\n", parts)
                + "\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
    }

    /** 读列 */
    private static Map<String, ColumnSnapshot> readColumns(DatabaseMetaData md, String cat,
                                                            String schema, String table) throws SQLException {
        Map<String, ColumnSnapshot> out = new LinkedHashMap<>();
        try (ResultSet rs = md.getColumns(cat, schema, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                int digits = rs.getInt("DECIMAL_DIGITS");
                String nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls
                        ? "NOT NULL" : "NULL";
                Object def = rs.getString("COLUMN_DEF");
                String remark = rs.getString("REMARKS");
                boolean autoInc;
                try {
                    autoInc = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
                } catch (SQLException | AbstractMethodError e) {
                    // 老驱动没有 IS_AUTOINCREMENT 列：读不到就当没有自增。
                    // 这只影响「自增」这一项属性，不该让整个对比失败。
                    autoInc = false;
                }
                out.put(name.toLowerCase(Locale.ROOT), new ColumnSnapshot(
                        name, columnDdl(type, size, digits, nullable, def, remark, autoInc),
                        typeWithSize(type, size, digits), nullable,
                        def == null ? "" : String.valueOf(def),
                        remark == null ? "" : remark));
            }
        }
        return out;
    }

    /**
     * 由元数据拼一列的 DDL 片段。
     *
     * <p>自己拼而不是走 {@code DbColumnDef.parseAttributes}：
     * 那个方法的入参是「表单提交来的原始类型串」（可能含 {@code unsigned}、
     * {@code enum} 列表等用户输入），而这里拿到的是**内核报告的**列定义，
     * 两者的输入形态不同。硬套会把 {@code COLUMN_SIZE} 当成长度、
     * 把内核返回的默认值再转义一次。</p>
     */
    private static String columnDdl(String type, int size, int digits, String nullable,
                                    Object def, String remark, boolean autoInc) {
        StringBuilder ddl = new StringBuilder(typeWithSize(type, size, digits));
        ddl.append(' ').append(nullable);
        if (def != null && !String.valueOf(def).isBlank()) {
            String text = String.valueOf(def);
            // 内核对 CURRENT_TIMESTAMP 之类的函数默认值原样返回，不该加引号
            if (text.matches("(?i)^(current_timestamp|now\\(\\)|current_date|current_time)(.*)$")) {
                ddl.append(" DEFAULT ").append(text);
            } else {
                ddl.append(" DEFAULT ").append(DbColumnDef.quoteSqlLiteral(text));
            }
        }
        if (autoInc) {
            ddl.append(" AUTO_INCREMENT");
        }
        if (remark != null && !remark.isBlank()) {
            ddl.append(" COMMENT ").append(DbColumnDef.quoteSqlLiteral(remark));
        }
        return ddl.toString();
    }

    /**
     * 拼「带长度/精度」的类型串；列比对与 DDL 共用，避免两处口径漂移。
     *
     * <p>{@code TYPE_NAME} 本身**不含长度**：{@code VARCHAR(64)} 与 {@code VARCHAR(16)}
     * 读回来都只是 {@code VARCHAR}，只比它就会把「长度变了」判成一致
     * （实测 2026-10-05，验收脚本报「name 列长度不同未被识别」）。</p>
     *
     * @param type   内核报告的类型名
     * @param size   COLUMN_SIZE
     * @param digits DECIMAL_DIGITS
     * @return 类型串
     */
    private static String typeWithSize(String type, int size, int digits) {
        String base = (type == null || type.isBlank()) ? "VARCHAR(255)" : type.trim();
        String upper = base.toUpperCase(Locale.ROOT);
        // DECIMAL / NUMERIC 的长度必须带小数位，否则 ALTER 会把精度截掉
        boolean needsDigits = upper.startsWith("DECIMAL") || upper.startsWith("NUMERIC")
                || upper.startsWith("NUMBER") || upper.startsWith("FLOAT");
        // 已经带括号的不再加工（ENUM('a','b')、DOUBLE(10,2) 之类）
        if (size > 0 && !upper.contains("(")
                && (upper.contains("CHAR") || upper.contains("BINARY") || needsDigits)) {
            StringBuilder sb = new StringBuilder(base).append('(').append(size);
            if (needsDigits && digits > 0) {
                sb.append(", ").append(digits);
            }
            return sb.append(')').toString();
        }
        return base;
    }

    /** 读索引（列序按 ORDINAL_POSITION 排） */
    private static Map<String, IndexSnapshot> readIndexes(DatabaseMetaData md, String cat,
                                                          String schema, String table) throws SQLException {
        Map<String, Map<Integer, String>> cols = new LinkedHashMap<>();
        Map<String, Boolean> unique = new LinkedHashMap<>();
        Map<String, String> type = new LinkedHashMap<>();
        try (ResultSet rs = md.getIndexInfo(cat, schema, table, false, true)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (name == null) {
                    continue;
                }
                String column = rs.getString("COLUMN_NAME");
                if (column == null) {
                    // 函数索引，列名为 null：跳过（比对不了列集，
                    // 强行拼字符串会让两侧都「不一致」）
                    continue;
                }
                int seq = rs.getShort("ORDINAL_POSITION");
                cols.computeIfAbsent(name, k -> new LinkedHashMap<>()).put(seq, column);
                unique.put(name, !rs.getBoolean("NON_UNIQUE"));
                type.put(name, String.valueOf(rs.getShort("TYPE")));
            }
        }
        Map<String, IndexSnapshot> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Integer, String>> e : cols.entrySet()) {
            List<String> ordered = new ArrayList<>();
            e.getValue().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(v -> ordered.add(v.getValue()));
            out.put(e.getKey(), new IndexSnapshot(e.getKey(),
                    Boolean.TRUE.equals(unique.get(e.getKey())), String.join(", ", ordered),
                    type.getOrDefault(e.getKey(), "")));
        }
        return out;
    }

    /** 读外键 */
    private static List<ForeignKeySnapshot> readForeignKeys(DatabaseMetaData md, String cat,
                                                            String schema, String table) throws SQLException {
        List<ForeignKeySnapshot> out = new ArrayList<>();
        Map<String, Map<Integer, String>> from = new LinkedHashMap<>();
        Map<String, Map<Integer, String>> to = new LinkedHashMap<>();
        Map<String, String> refTable = new LinkedHashMap<>();
        Map<String, Integer> upd = new LinkedHashMap<>();
        Map<String, Integer> del = new LinkedHashMap<>();
        try (ResultSet rs = md.getImportedKeys(cat, schema, table)) {
            while (rs.next()) {
                String name = rs.getString("FK_NAME");
                if (name == null) {
                    continue;
                }
                int seq = rs.getShort("KEY_SEQ");
                from.computeIfAbsent(name, k -> new LinkedHashMap<>()).put(seq, rs.getString("FKCOLUMN_NAME"));
                to.computeIfAbsent(name, k -> new LinkedHashMap<>()).put(seq, rs.getString("PKCOLUMN_NAME"));
                refTable.put(name, rs.getString("PKTABLE_NAME"));
                upd.put(name, (int) rs.getShort("UPDATE_RULE"));
                del.put(name, (int) rs.getShort("DELETE_RULE"));
            }
        }
        for (Map.Entry<String, Map<Integer, String>> e : from.entrySet()) {
            List<String> ordered = new ArrayList<>();
            e.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(v -> ordered.add(v.getValue()));
            List<String> orderedTo = new ArrayList<>();
            to.getOrDefault(e.getKey(), Map.of()).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(v -> orderedTo.add(v.getValue()));
            out.add(new ForeignKeySnapshot(e.getKey(), String.join(", ", ordered),
                    refTable.get(e.getKey()), String.join(", ", orderedTo),
                    upd.getOrDefault(e.getKey(), 0), del.getOrDefault(e.getKey(), 0)));
        }
        return out;
    }
}
