package com.chua.starter.monitor.support.datasync;

import com.chua.common.support.lang.datasource.dialect.Dialect;
import com.chua.common.support.lang.datasource.dialect.Pagination;
import com.chua.common.support.lang.datasource.dialect.SqlName;
import com.chua.datasource.support.sql.SqlSelectFields;
import com.chua.starter.monitor.support.model.SyncColumnVO;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 数据同步的 JDBC 元数据与标识符工具。
 *
 * <p>把「读列定义」「校验并引用表名/列名」这类会拼 SQL 的动作集中在一处，统一走
 * {@link SqlName} 白名单，避免动态标识符被直接拼进语句。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class SyncJdbcSupport {

    /**
     * 工具类禁止实例化。
     */
    private SyncJdbcSupport() {
    }

    /**
     * 读取一段 SELECT 的结果集列定义。
     *
     * <p><b>字段名来自解析</b>：字段名单本来就写在语句文本里（{@code SELECT a, b AS c FROM t}），
     * 先用 {@code utils-support-datasource-starter} 的
     * {@link SqlSelectFields} 从 SQL 字符串解析；只有 {@code SELECT *}、
     * 无别名的表达式这类文本里看不出名字的情况才用驱动返回的标签。</p>
     *
     * <p><b>校验只跑 1 条</b>：解析看不出名字、或需要确认表/列真实存在时，按当前连接的
     * 数据库方言把语句收敛成「第 1 页第 1 条」再执行（{@link #probeColumns}），
     * 并叠 {@link PreparedStatement#setMaxRows(int)} 兜底，避免把整表拉进内存。</p>
     *
     * @param conn 连接
     * @param sql  必须是 SELECT 的语句
     * @return 列列表（按结果集顺序）
     * @throws SQLException 语句无法解析或无法执行（表/列不存在、语法错误）
     */
    public static List<SyncColumnVO> columnsOf(Connection conn, String sql) throws SQLException {
        SqlSelectFields.Result parsed = SqlSelectFields.parse(sql);
        List<SyncColumnVO> actual = probeColumns(conn, sql);
        if (actual.isEmpty()) {
            if (!parsed.resolvable()) {
                throw new SQLException("无法读取该语句的结果集结构，请确认是单条 SELECT");
            }
            return withTypes(parsed.names(), List.of());
        }
        if (!parsed.resolvable() || parsed.fields().size() != actual.size()) {
            return actual;
        }
        // 字段名以 SQL 文本为准，类型取驱动的；名字对不上（大小写折叠差异）就整体用驱动的
        for (int i = 0; i < actual.size(); i++) {
            if (!actual.get(i).name().equalsIgnoreCase(parsed.fields().get(i).label())) {
                return actual;
            }
        }
        return withTypes(parsed.names(), actual);
    }

    /**
     * 把字段名与类型按位置配对。
     *
     * @param names  字段名（按结果集顺序）
     * @param actual 带类型的结果集列；可为空表示拿不到类型
     * @return 列列表
     */
    private static List<SyncColumnVO> withTypes(List<String> names, List<SyncColumnVO> actual) {
        List<SyncColumnVO> columns = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            String type = i < actual.size() ? actual.get(i).type() : "";
            columns.add(new SyncColumnVO(names.get(i), type, ""));
        }
        return columns;
    }

    /**
     * 从 SQL 文本解析结果集字段（不连库）。
     *
     * @param sql 源 SQL
     * @return 列列表；{@code SELECT *} 或含无别名表达式时返回空列表（需回库确认）
     */
    public static List<SyncColumnVO> parseColumns(String sql) {
        SqlSelectFields.Result parsed = SqlSelectFields.parse(sql);
        if (!parsed.resolvable()) {
            return new ArrayList<>();
        }
        List<SyncColumnVO> columns = new ArrayList<>(parsed.fields().size());
        // 文本里看不出版本差异的类型，源侧类型留空（界面按「-」展示，不伪造）
        for (String name : parsed.names()) {
            columns.add(new SyncColumnVO(name, "", ""));
        }
        return columns;
    }

    /**
     * 真查一次确认源 SQL 可执行，并顺带取回真实的列名与类型。
     *
     * <p><b>为什么要限制 1 条</b>：校验只关心「语句能跑通、返回哪些列」，不关心数据量；
     * 直接执行用户的原始 SQL 可能把整表拉进内存。这里按当前连接的数据库方言把语句
     * 收敛成第 1 页第 1 条（MySQL/PG 追加 {@code LIMIT 1}、Oracle 包 {@code ROWNUM}、
     * SQL Server 用 {@code OFFSET ... FETCH}），再叠一层 {@link PreparedStatement#setMaxRows(int)}
     * 兜底——万一方言未注册或语句本身已带分页，也不会多吐数据。</p>
     *
     * @param conn 连接
     * @param sql  源 SQL
     * @return 列列表（按结果集顺序）
     * @throws SQLException 语句无法执行（表/列不存在、语法错误等）
     */
    public static List<SyncColumnVO> probeColumns(Connection conn, String sql) throws SQLException {
        String cleaned = stripTrailingSemicolon(sql);
        String probeSql = paginatedOneRowSql(conn, cleaned);
        if (!probeSql.equals(cleaned)) {
            try {
                return executeProbe(conn, probeSql);
            } catch (SQLException e) {
                // 方言模板可能对某些语句形态不适用（子查询/已有排序等）。校验不能因此判死，
                // 退回原语句再用 setMaxRows(1) 收敛；若原语句也失败，那才是真的错。
                logDebugFallback(sql, e);
            }
        }
        return executeProbe(conn, cleaned);
    }

    /**
     * 执行一次只取 1 条的查询并读列。
     *
     * @param conn 连接
     * @param sql  已收敛行数的语句
     * @return 列列表
     * @throws SQLException 语句无法执行
     */
    private static List<SyncColumnVO> executeProbe(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setMaxRows(1);
            try {
                // 让驱动只往回拉 1 条：Oracle 等方言不支持 LIMIT 包装时，这是唯一的数据量闸门
                ps.setFetchSize(1);
            } catch (SQLException | UnsupportedOperationException ignored) {
                // 个别驱动不支持设置 fetchSize，setMaxRows(1) 已经足够兜底
            }
            try (ResultSet rs = ps.executeQuery()) {
                return readMeta(rs.getMetaData());
            }
        }
    }

    /**
     * 方言分页不适用时的回落提示（不打堆栈，属预期路径）。
     *
     * @param sql 原始语句
     * @param e   分页语句的失败原因
     */
    private static void logDebugFallback(String sql, SQLException e) {
        LoggerFactory.getLogger(SyncJdbcSupport.class)
                .debug("[sync] 方言分页探针不可用，回落到原始语句（setMaxRows=1）: {}", abbreviate(sql), e);
    }

    /**
     * 截断长文本，避免日志里塞整段 SQL。
     *
     * @param text 原文
     * @return 截断结果
     */
    private static String abbreviate(String text) {
        String value = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        return value.length() <= 160 ? value : value.substring(0, 160) + "…";
    }

    /**
     * 按数据库方言把语句收敛成「只取 1 条」。
     *
     * @param conn 连接
     * @param sql  源 SQL
     * @return 分页后的语句；方言未注册时原样返回（执行侧仍有 {@code setMaxRows(1)} 兜底）
     */
    private static String paginatedOneRowSql(Connection conn, String sql) {
        String cleaned = stripTrailingSemicolon(sql);
        // 语句自己已经带了行数限制时不再叠加（"LIMIT 10 LIMIT 1 OFFSET 0" 是语法错误），
        // 此时靠 setMaxRows(1) 收敛
        if (hasOwnRowLimit(cleaned)) {
            return cleaned;
        }
        Dialect dialect = resolveDialect(conn);
        if (dialect == null || !dialect.supportsLimit()) {
            return cleaned;
        }
        try {
            return dialect.processSql(cleaned, new Pagination().setPageNum(1).setPageSize(1));
        } catch (RuntimeException e) {
            // 方言模板异常不应让校验整体失败：退回原语句，由 setMaxRows(1) 兜底
            return cleaned;
        }
    }

    /**
     * 判断语句是否已经自带行数限制（{@code LIMIT} / {@code FETCH} / {@code ROWNUM}）。
     *
     * @param sql 已去分号的语句
     * @return 已限制行数返回 true
     */
    private static boolean hasOwnRowLimit(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        return lower.matches("(?s).*\\blimit\\s+\\d+(\\s*,\\s*\\d+)?(\\s+offset\\s+\\d+)?\\s*")
                || lower.contains(" fetch first ")
                || lower.contains(" fetch next ")
                || lower.contains(" rownum");
    }

    /**
     * 按 JDBC URL 的协议段解析数据库方言（与引擎侧同口径：{@code mssql} 归一到 {@code sqlserver}）。
     *
     * @param conn 连接
     * @return 方言实例；驱动不给 URL 或协议未注册时返回 null
     */
    private static Dialect resolveDialect(Connection conn) {
        String url;
        try {
            DatabaseMetaData meta = conn.getMetaData();
            url = meta == null ? null : meta.getURL();
        } catch (SQLException e) {
            return null;
        }
        if (url == null || url.isBlank()) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("jdbc:")) {
            return null;
        }
        String rest = lower.substring("jdbc:".length());
        int cut = rest.length();
        for (char sep : new char[]{':', '/', ';', '?'}) {
            int idx = rest.indexOf(sep);
            if (idx >= 0 && idx < cut) {
                cut = idx;
            }
        }
        String scheme = rest.substring(0, cut).trim();
        if (scheme.isEmpty()) {
            return null;
        }
        if ("mssql".equals(scheme)) {
            scheme = "sqlserver";
        }
        return Dialect.getExtension(scheme);
    }

    /**
     * 去掉语句末尾的分号（分页模板会把语句当子查询用，带分号会语法错误）。
     *
     * @param sql 源 SQL
     * @return 去掉末尾分号后的语句
     */
    private static String stripTrailingSemicolon(String sql) {
        String trimmed = sql == null ? "" : sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * 把结果集元数据转成列列表。
     *
     * @param meta 结果集元数据
     * @return 列列表
     * @throws SQLException 元数据读取失败
     */
    private static List<SyncColumnVO> readMeta(ResultSetMetaData meta) throws SQLException {
        int count = meta.getColumnCount();
        List<SyncColumnVO> columns = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            String label = meta.getColumnLabel(i);
            if (label == null || label.isBlank()) {
                label = meta.getColumnName(i);
            }
            // 结果集元数据没有「注释」这一项，源侧注释留空（目标侧才有 REMARKS）
            columns.add(new SyncColumnVO(label, safe(meta.getColumnTypeName(i)), ""));
        }
        return columns;
    }

    /**
     * 读取数据库表的列定义。
     *
     * <p><b>标识符归一化</b>：调用方可能传来 {@code 库.表}、{@code `库`.`表`}、
     * {@code "库"."表"}、{@code [库].[表]} 等形态，而 JDBC 元数据的 catalog/表名参数
     * 是<b>不带引号的裸名</b>，且 {@code getTables} 的第三个参数还是 LIKE 模式
     * （{@code _} / {@code %} 是通配符）。早先这里把原样字符串直接丢给元数据，
     * 于是「界面选得到表、后台报表不存在」——前端只要多围一层引号或大小写不一致就必中。
     * 现在统一由 {@link #findTableName} 归一化后再精确查。</p>
     *
     * <p><strong>表不存在时的行为</strong>：返回空列表（不抛 MySQL 原生
     * {@code Table ... doesn't exist}），由调用方决定语义（预览建表场景允许空目标列，
     * 执行场景再单独报错），其余异常照抛。</p>
     *
     * @param conn  连接
     * @param table 表名，可带一段 {@code 库.表} 限定（允许带各家引用符）
     * @return 列列表（按表定义顺序）；表不存在时返回空列表
     * @throws SQLException 元数据读取失败（非「表不存在」）
     */
    public static List<SyncColumnVO> tableColumns(Connection conn, String table) throws SQLException {
        String[] parts = splitQualified(table);
        String catalog = parts[0];
        String tableName = parts[1];
        // 归一化后回到「元数据里真实存在的那个库.表」，后续单值判断都用它
        String resolvedCatalog = resolveCatalog(conn, catalog);
        String realTable = findTableName(conn.getMetaData(), resolvedCatalog, tableName);
        if (realTable == null) {
            return new ArrayList<>();
        }
        DatabaseMetaData meta = conn.getMetaData();
        List<SyncColumnVO> columns = new ArrayList<>();
        // 这里的表名是元数据回读的真实名字，仍要按 LIKE 规则转义后使用（表名可能含 _ ）
        try (ResultSet rs = meta.getColumns(resolvedCatalog, null, escapePattern(meta, realTable), null)) {
            while (rs.next()) {
                columns.add(new SyncColumnVO(
                        rs.getString("COLUMN_NAME"),
                        safe(rs.getString("TYPE_NAME")),
                        safe(rs.getString("REMARKS"))));
            }
        }
        return columns;
    }

    /**
     * 判断一张表（含 {@code 库.表} 限定）在当前连接里是否存在。
     *
     * <p>存在性判定与 {@link #tableColumns} 同源（同一个 {@link #findTableName}），
     * 避免「判断存在但读不到列」或反过来的分裂语义。</p>
     *
     * @param conn      连接
     * @param catalog   catalog（库名），可为 空 使用连接默认
     * @param tableName 表名（不含库前缀）
     * @return 存在返回 true
     * @throws SQLException 元数据读取失败
     */
    public static boolean tableExists(Connection conn, String catalog, String tableName) throws SQLException {
        // tableName 也可能自带「库.表」限定（调用方把整串传进来），此时以它内部的库为准
        String[] parts = splitQualified(tableName);
        String effectiveCatalog = parts[0].isEmpty() ? catalog : parts[0];
        String effectiveTable = parts[1];
        String resolvedCatalog = resolveCatalog(conn, effectiveCatalog);
        return findTableName(conn.getMetaData(), resolvedCatalog, effectiveTable) != null;
    }

    /**
     * 判断一张表（整串「库.表」，允许带引用符）是否存在。
     *
     * <p>与 {@link #tableColumns} 同一个入参口径，避免调用方为「查列」和「判存在」
     * 各写一套拆名逻辑（两套口径必然分叉）。</p>
     *
     * @param conn  连接
     * @param table 表名，可带一段 {@code 库.表} 限定
     * @return 存在返回 true
     * @throws SQLException 元数据读取失败
     */
    public static boolean tableExists(Connection conn, String table) throws SQLException {
        String[] parts = splitQualified(table);
        String resolvedCatalog = resolveCatalog(conn, parts[0]);
        return findTableName(conn.getMetaData(), resolvedCatalog, parts[1]) != null;
    }

    /**
     * 去掉标识符外层的引用符与空白。
     *
     * <p>支持 MySQL 反引号、标准双引号、SQL Server 方括号三种形态，
     * 例如 {@code `jdbc_acc`.`device_channel`} → {@code jdbc_acc.device_channel}。
     * 只去引用符，不做大小写改写（大小写由元数据回读时按真实值对齐）。</p>
     *
     * @param raw 原始标识符
     * @return 去掉引用符与空白后的标识符；入参为 空 时返回空串
     */
    public static String stripIdentifierQuotes(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '`' || ch == '"' || ch == '[' || ch == ']') {
                continue;
            }
            if (Character.isWhitespace(ch)) {
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 拆分 {@code 库.表}：前半段为 catalog，后半段为表名。
     *
     * @param table 表名，可带一段限定
     * @return 长度 2 的数组：[0]=catalog（无限定为空串），[1]=表名
     */
    private static String[] splitQualified(String table) {
        String clean = stripIdentifierQuotes(table);
        if (clean.isEmpty()) {
            return new String[]{"", ""};
        }
        int dot = clean.indexOf('.');
        if (dot > 0) {
            return new String[]{clean.substring(0, dot), clean.substring(dot + 1)};
        }
        return new String[]{"", clean};
    }

    /**
     * 把调用方给的 catalog 对齐到元数据里的真实库名。
     *
     * <p>库名在 Linux 上的 MySQL/MariaDB 是大小写敏感的，前端缓存或用户手输的库名
     * 只要大小写不同就会查不到表。这里只在**同一批候选里**做大小写不敏感对齐，
     * 绝不跨库回退 —— 跨库回退会把「这张表不在你选的库」悄悄改成「写在别的库」，
     * 那正是最难查的一类串库故障。</p>
     *
     * @param conn    连接
     * @param catalog 调用方给的库名，可为空（用连接默认库）
     * @return 元数据里的真实库名；对齐不到时原样返回入参
     * @throws SQLException 元数据读取失败
     */
    private static String resolveCatalog(Connection conn, String catalog) throws SQLException {
        String wanted = stripIdentifierQuotes(catalog);
        String effective = wanted.isEmpty() ? safe(conn.getCatalog()) : wanted;
        if (effective.isEmpty()) {
            return effective;
        }
        try (ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) {
                String name = rs.getString("TABLE_CAT");
                if (name == null) {
                    continue;
                }
                if (name.equals(effective)) {
                    return name;
                }
                if (name.equalsIgnoreCase(effective)) {
                    return name;
                }
            }
        }
        return effective;
    }

    /**
     * 在指定 catalog 内查表，返回元数据里的真实表名。
     *
     * <p>先精确匹配（快），不中再按大小写不敏感扫一遍（慢但只在失败路径上）。
     * 表名作为 LIKE 模式参与匹配，故 {@code _} / {@code %} 必须先转义。</p>
     *
     * @param meta    元数据
     * @param catalog 已对齐的库名，可为空
     * @param table   表名（裸名）
     * @return 真实表名；不存在返回 null
     * @throws SQLException 元数据读取失败
     */
    private static String findTableName(DatabaseMetaData meta, String catalog, String table) throws SQLException {
        String wanted = stripIdentifierQuotes(table);
        if (wanted.isEmpty()) {
            return null;
        }
        try (ResultSet rs = meta.getTables(catalog, null, escapePattern(meta, wanted), new String[]{"TABLE"})) {
            if (rs.next()) {
                return rs.getString("TABLE_NAME");
            }
        }
        try (ResultSet rs = meta.getTables(catalog, null, null, new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && name.equalsIgnoreCase(wanted)) {
                    return name;
                }
            }
        }
        return null;
    }

    /**
     * 转义元数据 LIKE 模式里的通配符。
     *
     * <p>{@code getTables} 的表名参数是模式串：表名 {@code a_b} 会同时匹配 {@code axb}，
     * 表名含 {@code %} 时更会匹配一切。转义符取自驱动（{@code getSearchStringEscape}），
     * 拿不到时按 JDBC 默认的反斜杠。</p>
     *
     * @param meta  元数据
     * @param value 原始表名
     * @return 转义后的模式串
     * @throws SQLException 元数据读取失败
     */
    private static String escapePattern(DatabaseMetaData meta, String value) throws SQLException {
        String escape = meta.getSearchStringEscape();
        if (escape == null || escape.isEmpty()) {
            escape = "\\";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '_' || ch == '%') {
                sb.append(escape);
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 校验并引用目标表名。
     *
     * <p>入参允许带各家引用符（{@code `库`.`表`} / {@code "库"."表"} / {@code [库].[表]}）：
     * 这些写法在 SQL 里合法，界面也可能原样回显。先归一化成裸名再校验，
     * 避免「用户把合法写法贴进来反而被拒」。</p>
     *
     * @param table 表名，可带一段 {@code 库.表} 限定
     * @return 形如 {@code `db`.`tbl`} 的片段
     * @throws IllegalArgumentException 表名含非法字符或超长
     */
    public static String quoteTable(String table) {
        return SqlName.quoteMysql(stripIdentifierQuotes(table), "目标表名");
    }

    /**
     * 校验并引用目标列名。
     *
     * @param column 列名
     * @return 形如 {@code `col`} 的片段
     * @throws IllegalArgumentException 列名含非法字符或超长
     */
    public static String quoteColumn(String column) {
        return SqlName.quoteMysql(column, "目标列名");
    }

    /**
     * 判断语句是否是一条读取用的 SELECT。
     *
     * <p>同步任务只允许读取：写入一律由目标端生成，避免有人把 {@code DELETE} / {@code DROP}
     * 填进源 SQL 把源库改了。这里只认 {@code SELECT} 开头，{@code WITH ... SELECT} 这类 CTE
     * 也被挡下（它也能包裹写语句），需要时把 CTE 封装成视图再同步。</p>
     *
     * @param sql 待判断语句
     * @return 是单条 SELECT 返回 true
     */
    public static boolean isSelect(String sql) {
        if (sql == null) {
            return false;
        }
        String trimmed = sql.stripLeading().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("select");
    }

    /**
     * 抽取列名集合，供映射校验使用。
     *
     * @param columns 列列表
     * @return 列名集合，保持顺序
     */
    public static Set<String> columnNames(List<SyncColumnVO> columns) {
        Set<String> names = new LinkedHashSet<>();
        for (SyncColumnVO column : columns) {
            names.add(column.name());
        }
        return names;
    }

    /**
     * 空值安全的字符串取值。
     *
     * @param value 原值，可为 空
     * @return 原值，为 空 时返回空串
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}