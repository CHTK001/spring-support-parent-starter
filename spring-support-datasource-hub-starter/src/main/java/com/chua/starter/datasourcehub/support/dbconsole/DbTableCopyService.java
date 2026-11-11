package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.common.support.lang.datasource.dialect.SqlName;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 跨库复制表：把一张表的建表语句与数据搬到另一个库（或同一库的另一个名字）。
 *
 * <p><b>为什么服务端做而不是前端拼 SQL</b>：表结构要走 {@code SHOW CREATE TABLE}
 * （索引、字符集、注释、生成列全在原文里），数据要按批流式搬，任何一步都不适合
 * 让浏览器参与。前端只负责发一个请求、看进度。</p>
 *
 * <p><b>为什么源和目标各持一条物理连接</b>：{@code DatasourceService.connection(id)}
 * 返回的是缓存共享的同一条物理连接，同一数据源跨库复制时源和目标会抢同一条连接，
 * 一边流式读游标一边写目标必然互相打断。这里用 {@code newConnection(id)} 各开一条，
 * 归本方法所有、用完即关。</p>
 *
 * <p><b>为什么只支持 MySQL/MariaDB 系</b>：{@link DbTableSpec#showCreateTable} 与
 * {@link DbImporter} 的 DDL 拼装都以反引号为准；跨到 PG/H2 会把反引号原样带过去。
 * 判定不通过时直接报错，不做「看起来复制成功、实际建出一张坏表」的静默降级。</p>
 *
 * <p><b>为什么数据区段放进一个事务</b>：表已经建好后中途失败/取消，半张表的数据
 * 比一张空表更难收拾。整段写入放在目标连接的一个事务里，失败统一回滚，
 * 用户看到的是「表建好了但没有数据」，语义干净。</p>
 *
 * <p>DDL 改写只做三件事：把表名换成目标名、剔除 {@code FOREIGN KEY} 约束
 * （跨库时被引用的表不一定存在）、去掉 {@code AUTO_INCREMENT=数字}
 * （让新表从 1 开始，而不是继承源表的自增值）。普通索引与列定义原样保留。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbTableCopyService {

    /** 步骤键：读取源表结构 */
    private static final String STEP_STRUCTURE = "structure";

    /** 步骤键：检查并创建目标表 */
    private static final String STEP_CREATE = "create";

    /** 步骤键：复制表数据 */
    private static final String STEP_DATA = "data";

    /** 单批写入行数：太小则网络往返多，太大则内存与 undo 压力大 */
    private static final int BATCH_ROWS = 500;

    /** 结构阶段结束时的百分比 */
    private static final int STRUCTURE_DONE = 15;

    /** 建表阶段结束时的百分比 */
    private static final int CREATE_DONE = 25;

    /** 数据阶段可推进到的上限（留 2% 给收尾） */
    private static final int DATA_END = 98;

    /** DDL 里 {@code AUTO_INCREMENT=123} 这个表选项 */
    private static final String AUTO_INCREMENT_OPTION = "(?i)AUTO_INCREMENT\\s*=\\s*\\d+";

    private final DatasourceService datasourceService;

    private final DbCopyTaskRegistry taskRegistry;

    /**
     * 提交一次复制任务。
     *
     * <p>校验与建任务同步完成，真正搬数据丢到后台线程：请求立刻返回 {@code taskId}，
     * 前端据此订阅全局进度流。这样即使复制一张几百万行的表，请求也不会超时。</p>
     *
     * @param request 复制请求
     * @return 任务 id
     * @throws IllegalArgumentException 表名/库名不合法、目标与源完全相同
     */
    public String start(DbTableCopyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("复制请求不能为空");
        }
        String sourceTable = ident(request.sourceTable(), "源表名");
        String targetTable = ident(request.targetTable(), "目标表名");
        String sourceDatabase = blankToNull(request.sourceDatabase());
        String targetDatabase = blankToNull(request.targetDatabase());
        if (sourceDatabase != null) {
            ident(sourceDatabase, "源数据库名");
        }
        if (targetDatabase != null) {
            ident(targetDatabase, "目标数据库名");
        }
        // 同源同库同名 = 自己复制到自己：开了覆盖会把源表直接 DROP 掉，
        // 必须在建任务之前拦下来，不能等后台线程执行到 DROP 才报错
        if (java.util.Objects.equals(request.sourceDatasourceId(), request.targetDatasourceId())
                && equalsName(sourceDatabase, targetDatabase)
                && sourceTable.equalsIgnoreCase(targetTable)) {
            throw new IllegalArgumentException("目标表与源表相同，请换一个目标表名");
        }
        Map<String, String> steps = new LinkedHashMap<>();
        steps.put(STEP_STRUCTURE, "读取源表结构");
        steps.put(STEP_CREATE, "创建目标表");
        if (request.copyDataOrDefault()) {
            steps.put(STEP_DATA, "复制表数据");
        }
        String subtitle = label(sourceDatabase, sourceTable) + " → " + label(targetDatabase, targetTable);
        String taskId = taskRegistry.create("复制表", subtitle, steps);
        taskRegistry.submit(() -> run(taskId, request, sourceDatabase, sourceTable, targetDatabase, targetTable));
        return taskId;
    }

    /**
     * 任务主体：读结构 → 建表 → 搬数据。
     *
     * @param taskId         任务 id
     * @param request        原始请求
     * @param sourceDatabase 源库（已去空白）
     * @param sourceTable    源表
     * @param targetDatabase 目标库（已去空白）
     * @param targetTable    目标表
     */
    private void run(String taskId, DbTableCopyRequest request, String sourceDatabase, String sourceTable,
                     String targetDatabase, String targetTable) {
        String targetLabel = label(targetDatabase, targetTable);
        try (Connection source = datasourceService.newConnection(request.sourceDatasourceId());
             Connection target = datasourceService.newConnection(request.targetDatasourceId())) {
            requireMysqlFamily(source, "源数据源");
            requireMysqlFamily(target, "目标数据源");
            useDatabase(source, sourceDatabase);
            useDatabase(target, targetDatabase);

            // ---- 1. 读取源表结构 ----
            taskRegistry.startStep(taskId, STEP_STRUCTURE, "读取 " + sourceTable + " 的建表语句");
            DbTableSpec spec = DbTableSpec.showCreateTable(source, sourceDatabase, sourceTable);
            String ddl = targetDdl(spec.ddl(), targetTable);
            taskRegistry.finishStep(taskId, STEP_STRUCTURE, "SUCCESS",
                    "建表语句 " + ddl.length() + " 字节");
            taskRegistry.progress(taskId, STRUCTURE_DONE, "源表结构已读取");
            taskRegistry.detail(taskId, "源表 " + sourceTable + " 共 "
                    + (spec.columns() == null ? 0 : spec.columns().size()) + " 列");

            // ---- 2. 检查并创建目标表 ----
            checkCancelled(taskId);
            taskRegistry.startStep(taskId, STEP_CREATE, "检查目标表是否同名");
            // 目标表重名判定必须锁在目标库内：{@code DbImporter.tableExists} 的
            // 兜底 scope 是 (null, null)，MySQL 驱动在 catalog 为空时会把**所有库**的
            // 同名表都算进来 —— 跨库复制（目标表名与源表同名，正好是最常见的用法）
            // 会被误判成「目标已存在」而直接失败。
            boolean exists = tableExistsInDatabase(target, targetDatabase, targetTable);
            if (exists && !request.overwriteOrDefault()) {
                throw new IllegalStateException("目标表 " + targetLabel + " 已存在；如需覆盖请重新勾选「覆盖同名表」");
            }
            if (exists) {
                execute(target, "DROP TABLE " + qualified(targetDatabase, targetTable));
                taskRegistry.detail(taskId, "已删除同名目标表 " + targetTable);
            }
            execute(target, ddl);
            taskRegistry.finishStep(taskId, STEP_CREATE, "SUCCESS", exists ? "已重建目标表" : "已创建目标表");
            taskRegistry.progress(taskId, CREATE_DONE, "目标表 " + targetLabel + " 已就绪");

            // ---- 3. 复制数据 ----
            if (request.copyDataOrDefault()) {
                copyData(taskId, source, target, sourceDatabase, sourceTable, targetDatabase, targetTable);
            }
            taskRegistry.finish(taskId, "SUCCESS", "已复制到 " + targetLabel);
        } catch (CopyCancelledException e) {
            log.info("[db-copy] 任务 {} 已取消", taskId);
            taskRegistry.finish(taskId, "CANCELLED", "已取消复制");
        } catch (Exception e) {
            log.warn("[db-copy] 任务 {} 失败: {}", taskId, e.getMessage(), e);
            taskRegistry.finish(taskId, "FAILED", describe(e));
        }
    }

    /**
     * 复制表数据：统计行数 → 流式读源表 → 分批写目标表。
     *
     * @param taskId         任务 id
     * @param source         源连接
     * @param target         目标连接
     * @param sourceDatabase 源库
     * @param sourceTable    源表
     * @param targetDatabase 目标库
     * @param targetTable    目标表
     * @throws Exception 读写失败或用户取消
     */
    private void copyData(String taskId, Connection source, Connection target,
                          String sourceDatabase, String sourceTable,
                          String targetDatabase, String targetTable) throws Exception {
        taskRegistry.startStep(taskId, STEP_DATA, "统计源表行数");
        long total = countRows(source, sourceDatabase, sourceTable);
        if (total <= 0) {
            taskRegistry.finishStep(taskId, STEP_DATA, "SKIPPED", "源表没有数据，只复制了结构");
            return;
        }
        List<String> columns = dataColumns(source, sourceDatabase, sourceTable);
        if (columns.isEmpty()) {
            throw new IllegalStateException("源表没有可复制的列（可能全部是生成列）");
        }
        taskRegistry.detail(taskId, "源表 " + sourceTable + " 共 " + total + " 行、" + columns.size() + " 列");
        taskRegistry.progress(taskId, CREATE_DONE, "开始写入 " + total + " 行");

        String quotedColumns = joinQuoted(columns);
        String selectSql = "SELECT " + quotedColumns + " FROM " + qualified(sourceDatabase, sourceTable);
        String insertSql = "INSERT INTO " + qualified(targetDatabase, targetTable)
                + " (" + quotedColumns + ") VALUES (" + placeholders(columns.size()) + ")";

        long done = 0L;
        target.setAutoCommit(false);
        try (PreparedStatement read = source.prepareStatement(selectSql,
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            // Connector/J 的逐行流式读法：必须 Integer.MIN_VALUE，否则驱动会把整个结果集读进内存。
            // 源与目标各一条连接，因此边读边写不会互相干扰
            read.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet rs = read.executeQuery();
                 PreparedStatement write = target.prepareStatement(insertSql)) {
                int pending = 0;
                while (rs.next()) {
                    checkCancelled(taskId);
                    for (int i = 1; i <= columns.size(); i++) {
                        bind(write, i, rs);
                    }
                    write.addBatch();
                    pending++;
                    if (pending >= BATCH_ROWS) {
                        write.executeBatch();
                        done += pending;
                        pending = 0;
                        reportDataProgress(taskId, done, total);
                    }
                }
                if (pending > 0) {
                    write.executeBatch();
                    done += pending;
                }
                target.commit();
            }
        } catch (Exception e) {
            // 数据区段整体回滚：失败后目标表保持为空表，而不是留下半张表的数据
            try {
                target.rollback();
            } catch (SQLException ignored) {
                // 连接已坏时回滚也会失败，异常本身照常往上抛
            }
            throw e;
        } finally {
            try {
                target.setAutoCommit(true);
            } catch (SQLException ignored) {
                // 连接即将关闭，恢复失败无需处理
            }
        }
        taskRegistry.finishStep(taskId, STEP_DATA, "SUCCESS", "已写入 " + done + " 行");
        taskRegistry.progress(taskId, DATA_END, "数据复制完成");
    }

    /**
     * 把数据阶段的进度换算成百分比并广播。
     *
     * @param taskId 任务 id
     * @param done   已写入行数
     * @param total  总行数
     */
    private void reportDataProgress(String taskId, long done, long total) {
        int span = DATA_END - CREATE_DONE;
        int percentage = CREATE_DONE;
        if (total > 0) {
            long advanced = done * span / total;
            percentage = CREATE_DONE + (int) Math.min(span, Math.max(0L, advanced));
        }
        taskRegistry.progress(taskId, percentage, "已复制 " + done + " / " + total + " 行");
    }

    /**
     * 把源表 DDL 改写成目标表的建表语句。
     *
     * <p>只截取「列定义起始的 {@code (}」之后的部分并重拼表名，源表名不会残留；
     * 逐行剔除外键约束后修补最后一个定义行的尾逗号；最后去掉 {@code AUTO_INCREMENT=数字}
     * 表选项。普通索引、列属性、表选项原样保留。</p>
     *
     * @param ddl         源表建表语句（SHOW CREATE TABLE 原文）
     * @param targetTable 目标表名
     * @return 可直接在目标库执行的建表语句
     */
    static String targetDdl(String ddl, String targetTable) {
        String text = ddl == null ? "" : ddl;
        int createAt = text.toUpperCase(Locale.ROOT).indexOf("CREATE TABLE");
        if (createAt < 0) {
            throw new IllegalStateException("源表建表语句无法识别，无法复制");
        }
        int paren = text.indexOf('(', createAt);
        if (paren < 0) {
            throw new IllegalStateException("源表建表语句缺少列定义，无法复制");
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.substring(paren).split("\\R")) {
            if (isForeignKeyLine(line)) {
                continue;
            }
            lines.add(line);
        }
        stripTrailingCommaBeforeClosing(lines);
        String body = String.join("\n", lines).replaceAll(AUTO_INCREMENT_OPTION, "");
        return "CREATE TABLE " + SqlName.quoteMysql(targetTable, "目标表名") + " " + body.strip();
    }

    /**
     * 判断某一行是否是外键约束定义。
     *
     * @param line DDL 中的一行
     * @return true=外键约束（跨库复制时必须剔除）
     */
    private static boolean isForeignKeyLine(String line) {
        String upper = line.strip().toUpperCase(Locale.ROOT);
        return upper.startsWith("CONSTRAINT ") && upper.contains("FOREIGN KEY")
                || upper.startsWith("FOREIGN KEY");
    }

    /**
     * 删掉列定义末尾的逗号（剔除外键后最后一行会多出一个逗号）。
     *
     * @param lines DDL 行（原地修改）
     */
    private static void stripTrailingCommaBeforeClosing(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).stripLeading().startsWith(")")) {
                continue;
            }
            int prev = i - 1;
            while (prev >= 0 && lines.get(prev).isBlank()) {
                prev--;
            }
            if (prev < 0) {
                return;
            }
            String line = lines.get(prev);
            int last = line.length() - 1;
            while (last >= 0 && Character.isWhitespace(line.charAt(last))) {
                last--;
            }
            if (last >= 0 && line.charAt(last) == ',') {
                lines.set(prev, line.substring(0, last) + line.substring(last + 1));
            }
            return;
        }
    }

    /**
     * 读源表可复制的列（排除虚拟列/存储生成列，它们不能显式写入）。
     *
     * @param conn     源连接
     * @param database 源库，为空时取会话当前库
     * @param table    源表
     * @return 列名，按定义顺序
     * @throws SQLException 查询 information_schema 失败
     */
    private static List<String> dataColumns(Connection conn, String database, String table) throws SQLException {
        String schema = database == null || database.isBlank() ? currentDatabase(conn) : database;
        String sql = "SELECT COLUMN_NAME, EXTRA FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        List<String> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String extra = rs.getString("EXTRA");
                    if (extra != null && isGenerated(extra)) {
                        continue;
                    }
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }

    /**
     * 是否生成列（{@code DEFAULT_GENERATED} 只是表达式默认值，不是生成列，不能算进来）。
     *
     * @param extra information_schema.COLUMNS.EXTRA
     * @return true=虚拟列/存储生成列
     */
    private static boolean isGenerated(String extra) {
        String upper = extra.toUpperCase(Locale.ROOT);
        return upper.contains("VIRTUAL GENERATED") || upper.contains("STORED GENERATED");
    }

    /**
     * 统计源表行数（进度分母）。
     *
     * @param conn     源连接
     * @param database 源库
     * @param table    源表
     * @return 行数
     * @throws SQLException 查询失败
     */
    private static long countRows(Connection conn, String database, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + qualified(database, table))) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /**
     * 读会话当前库，用于请求没带库名时定位 information_schema 的 schema。
     *
     * @param conn 连接
     * @return 库名，读不到返回空串
     */
    private static String currentDatabase(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
            if (rs.next()) {
                String value = rs.getString(1);
                return value == null ? "" : value;
            }
        } catch (SQLException ignored) {
            // 读不到就让 information_schema 查空，由调用方报「没有可复制的列」
        }
        return "";
    }

    /**
     * 把一个结果集当前行的值绑定到待写入的 INSERT 参数上。
     *
     * <p>二进制必须走 {@code setBytes}：走 {@code DbImporter} 的 CSV 式转换会把
     * {@code byte[]} 退化成 {@code [B@1f2a3b} 字符串，BLOB 表复制过去就是一堆垃圾。</p>
     *
     * @param ps    目标 INSERT 语句
     * @param index 参数下标（1 起）
     * @param rs    源结果集（已定位到当前行）
     * @throws SQLException 读取或绑定失败
     */
    private static void bind(PreparedStatement ps, int index, ResultSet rs) throws SQLException {
        Object value = rs.getObject(index);
        if (value == null) {
            ps.setObject(index, null);
            return;
        }
        if (value instanceof byte[] bytes) {
            ps.setBytes(index, bytes);
            return;
        }
        if (value instanceof Blob blob) {
            ps.setBinaryStream(index, blob.getBinaryStream(), blob.length());
            return;
        }
        if (value instanceof Clob clob) {
            ps.setCharacterStream(index, clob.getCharacterStream(), clob.length());
            return;
        }
        ps.setObject(index, value);
    }

    /**
     * 切换连接的会话库。
     *
     * <p>{@code setCatalog} 只更新驱动内部字段（供元数据定位），{@code USE} 才真正切
     * 服务端会话库，两者都要：缺前者元数据读错库，缺后者数据读写错库。</p>
     *
     * @param conn     连接
     * @param database 目标库，为空表示不切换
     * @throws SQLException 切换失败
     */
    private static void useDatabase(Connection conn, String database) throws SQLException {
        if (database == null || database.isBlank()) {
            return;
        }
        try {
            conn.setCatalog(database);
        } catch (SQLException ignored) {
            // 驱动不支持 setCatalog 时元数据退化为按会话默认库，不影响 USE
        }
        try (Statement st = conn.createStatement()) {
            st.execute("USE " + SqlName.quoteMysql(database, "数据库名"));
        }
    }

    /**
     * 校验连接是 MySQL/MariaDB 系。
     *
     * @param conn  连接
     * @param label 报错时用来说明是源还是目标
     * @throws SQLException 读不到元数据
     */
    private static void requireMysqlFamily(Connection conn, String label) throws SQLException {
        String product = conn.getMetaData().getDatabaseProductName();
        String lower = product == null ? "" : product.toLowerCase(Locale.ROOT);
        boolean mysqlFamily = lower.contains("mysql") || lower.contains("mariadb") || lower.contains("oceanbase");
        if (!mysqlFamily) {
            throw new IllegalStateException(label + "不是 MySQL/MariaDB 系（" + product + "），暂不支持跨库复制表");
        }
    }

    /**
     * 执行一条 DDL。
     *
     * @param conn 连接
     * @param sql  语句
     * @throws SQLException 执行失败
     */
    private static void execute(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * 目标库内是否已有同名表。
     *
     * <p>只查 {@code information_schema} 且库名精确限定，不用 JDBC 元数据的多级兜底
     * —— 那条兜底在 catalog 为空时会跨库命中同名表，把「源库有这张表」误当成
     * 「目标库已有目标表」。</p>
     *
     * <p>MySQL 的小写表名策略取决于 {@code lower_case_table_names}：Windows 上服务端
     * 存的就是小写，Linux 上大小写敏感。用 {@code LOWER()} 比较，两边都能命中同名的表。</p>
     *
     * @param conn     目标连接
     * @param database 目标库，可为空（回落到连接当前库）
     * @param table    目标表名
     * @return true=目标库内已有同名表
     * @throws SQLException 查询失败
     */
    private static boolean tableExistsInDatabase(Connection conn, String database, String table) throws SQLException {
        String schema = (database == null || database.isBlank()) ? conn.getCatalog() : database;
        if (schema == null || schema.isBlank()) {
            return DbImporter.tableExists(conn, table);
        }
        String sql = "SELECT 1 FROM information_schema.tables"
                + " WHERE table_schema = ? AND LOWER(table_name) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 拼「库.表」的引用名。
     *
     * @param database 库名，可为空
     * @param table    表名
     * @return 形如 {@code `db`.`tbl`}
     */
    private static String qualified(String database, String table) {
        String quotedTable = SqlName.quoteMysql(table, "表名");
        if (database == null || database.isBlank()) {
            return quotedTable;
        }
        return SqlName.quoteMysql(database, "数据库名") + "." + quotedTable;
    }

    /**
     * 列名加反引号并拼接（列名可能含中文/空格，走转义而不是白名单）。
     *
     * @param columns 列名
     * @return 形如 {@code `a`, `b`}
     */
    private static String joinQuoted(List<String> columns) {
        StringBuilder sb = new StringBuilder();
        for (String column : columns) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(SqlName.escape(column, '`', "列名"));
        }
        return sb.toString();
    }

    /**
     * 生成 INSERT 的占位符列表。
     *
     * @param count 列数
     * @return 形如 {@code ?, ?, ?}
     */
    private static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('?');
        }
        return sb.toString();
    }

    /**
     * 校验并裁剪标识符（表名/库名）。
     *
     * @param name  原始值
     * @param label 用途
     * @return 去空白后的合法标识符
     * @throws IllegalArgumentException 不合法
     */
    private static String ident(String name, String label) {
        try {
            return SqlName.checkSimple(name == null ? null : name.trim(), label);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(label + "只能由字母、数字和下划线组成，且最长 64 字符: " + name, ex);
        }
    }

    /**
     * 去空白，空白串视为「未指定」。
     *
     * @param value 原始值
     * @return 去空白后的值或 null
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 比较两个库名（都可能为空）。
     *
     * @param left  库名
     * @param right 库名
     * @return 两者都为空、或忽略大小写相等时返回 true
     */
    private static boolean equalsName(String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.equalsIgnoreCase(right);
    }

    /**
     * 展示用的「库.表」。
     *
     * @param database 库名，可为空
     * @param table    表名
     * @return 形如 {@code db.tbl}
     */
    private static String label(String database, String table) {
        return database == null || database.isBlank() ? table : database + "." + table;
    }

    /**
     * 取可读的失败原因。
     *
     * @param e 异常
     * @return 消息；为空时退回异常类名
     */
    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    /**
     * 批次边界检查取消标志。
     *
     * @param taskId 任务 id
     * @throws CopyCancelledException 用户已请求取消
     */
    private void checkCancelled(String taskId) {
        if (taskRegistry.cancelled(taskId)) {
            throw new CopyCancelledException();
        }
    }

    /**
     * 用户取消复制（只在批次边界抛出，让当前批次写完再停）。
     */
    private static final class CopyCancelledException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private CopyCancelledException() {
            super("已取消");
        }
    }
}
