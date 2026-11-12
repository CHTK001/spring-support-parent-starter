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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * <p><b>为什么源和目标各持一条连接</b>：{@code DatasourceService.connection(id)}
 * 返回的是缓存共享的同一条连接，同一数据源跨库复制时源和目标会抢同一条连接，
 * 一边流式读游标一边写目标必然互相打断。这里用 {@code newConnection(id)} 各开一条，
 * 归本方法所有、用完即关。</p>
 *
 * <p><b>用完为什么还要还原会话库</b>：台账数据源的 {@code newConnection} 是独立物理连接，
 * 关掉就没了；但主数据源（{@code datasourceId=0}）的 {@code newConnection} 拿的是容器
 * 连接池里的连接（{@code AppDatasourceConnector} 不愿意绕开池另开连接）。切库后不还原，
 * 这条连接归还池里仍停在业务库上，之后走同一连接的业务 SQL 会报「表不存在」。
 * 所以本类在切库前记下会话库，任务结束时（成功/失败/取消）一律还原。</p>
 *
 * <p><b>为什么只支持 MySQL/MariaDB 系</b>：{@link DbTableSpec#showCreateTable} 与
 * {@link DbImporter} 的 DDL 拼装都以反引号为准；跨到 PG/H2 会把反引号原样带过去。
 * 判定不通过时直接报错，不做「看起来复制成功、实际建出一张坏表」的静默降级。</p>
 *
 * <p><b>为什么数据区段放进一个事务</b>：表已经建好后中途失败/取消，半张表的数据
 * 比一张空表更难收拾。整段写入放在目标连接的一个事务里，失败统一回滚，
 * 用户看到的是「表建好了但没有数据」，语义干净。</p>
 *
 * <p><b>覆盖同名表为什么不用 DROP + CREATE</b>：那条路上建表或搬数据任何一步失败，
 * 原表已经没了，数据不可恢复。这里改成「新数据先写进临时表 → 全部提交后一条
 * {@code RENAME TABLE} 原子换名 → 删旧表备份」：换名之前原表一直是在线的，
 * 失败/取消只丢掉临时表。</p>
 *
 * <p><b>为什么目标表要占位</b>：两个任务同时改同一张表会各自换名，谁也不知道最后
 * 剩下的是哪份数据。提交任务时按「数据源+库+表」占位，重复提交直接拒绝。</p>
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

    /** 临时表/旧表备份名里插入的标记：一眼能看出是复制留下的中间表 */
    private static final String SCRATCH_MARK = "_dbmcp_";

    /** 标识符长度上限（与 MySQL 的表名上限一致） */
    private static final int MAX_IDENT_LENGTH = 64;

    /** 取消轮询间隔：统计行数这类单条长语句靠它才停得下来 */
    private static final long CANCEL_POLL_MS = 200L;

    /** 正在改写的目标表：{@code 数据源id|库|小写表名}，任务结束（含失败/取消）释放 */
    private final Set<String> activeTargets = ConcurrentHashMap.newKeySet();

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
        // 同源同库同名 = 自己复制到自己：覆盖时临时表写完会换掉目标表，
        // 而目标表就是源表 —— 换名之后源表只剩备份，语义上等于自毁，
        // 必须在建任务之前拦下来
        if (java.util.Objects.equals(request.sourceDatasourceId(), request.targetDatasourceId())
                && equalsName(sourceDatabase, targetDatabase)
                && sourceTable.equalsIgnoreCase(targetTable)) {
            throw new IllegalArgumentException("目标表与源表相同，请换一个目标表名");
        }
        String subtitle = label(sourceDatabase, sourceTable) + " → " + label(targetDatabase, targetTable);
        // 占位在提交之前完成：双击粘贴会在两个 HTTP 请求里各走一遍校验，
        // 只有「先占位再建任务」才能把第二次请求挡下来
        String lockKey = lockKey(request.targetDatasourceId(), targetDatabase, targetTable);
        if (!activeTargets.add(lockKey)) {
            throw new IllegalStateException("目标表 " + label(targetDatabase, targetTable)
                    + " 已有复制任务在执行，等它结束或取消后再试");
        }
        Map<String, String> steps = new LinkedHashMap<>();
        steps.put(STEP_STRUCTURE, "读取源表结构");
        steps.put(STEP_CREATE, "创建目标表");
        if (request.copyDataOrDefault()) {
            steps.put(STEP_DATA, "复制表数据");
        }
        try {
            String taskId = taskRegistry.create("复制表", subtitle, steps);
            taskRegistry.submit(() -> run(taskId, request, sourceDatabase, sourceTable,
                    targetDatabase, targetTable, lockKey));
            return taskId;
        } catch (RuntimeException e) {
            activeTargets.remove(lockKey);
            throw e;
        }
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
     * @param lockKey        目标表占位键（任务结束时释放）
     */
    private void run(String taskId, DbTableCopyRequest request, String sourceDatabase, String sourceTable,
                     String targetDatabase, String targetTable, String lockKey) {
        String targetLabel = label(targetDatabase, targetTable);
        try (Connection source = datasourceService.newConnection(request.sourceDatasourceId());
             Connection target = datasourceService.newConnection(request.targetDatasourceId())) {
            // 切库前先记下进入时的会话库：主数据源（datasourceId=0）的 newConnection
            // 拿的是容器连接池里的连接（见 AppDatasourceConnector），USE 过后不还原就把
            // 池里的连接留在了业务库上 —— 之后的登录、用户查询都会跑到那个库里，报
            // 「Table 'xxx.sys_user' doesn't exist」。台账数据源是独立物理连接，还原只是多发一次 USE。
            String sourceOriginal = currentDatabase(source);
            String targetOriginal = currentDatabase(target);
            try {
                requireMysqlFamily(source, "源数据源");
                requireMysqlFamily(target, "目标数据源");
                useDatabase(source, sourceDatabase);
                useDatabase(target, targetDatabase);

                // ---- 1. 读取源表结构 ----
                taskRegistry.startStep(taskId, STEP_STRUCTURE, "读取 " + sourceTable + " 的建表语句");
                DbTableSpec spec = DbTableSpec.showCreateTable(source, sourceDatabase, sourceTable);
                int ddlBytes = spec.ddl() == null ? 0 : spec.ddl().length();
                taskRegistry.finishStep(taskId, STEP_STRUCTURE, "SUCCESS", "建表语句 " + ddlBytes + " 字节");
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
                    throw new IllegalStateException("目标表 " + targetLabel
                            + " 已存在，如需覆盖请确认后再提交");
                }
                // 覆盖时先写临时表，搬完再换名：原表在换名之前一直可用，失败也不会丢数据
                String workingTable = exists
                        ? scratchTableName(target, targetDatabase, targetTable, taskId, "t") : targetTable;
                String ddl = targetDdl(spec.ddl(), workingTable);
                execute(target, ddl);
                if (exists) {
                    taskRegistry.detail(taskId, "已创建临时表 " + workingTable + "，复制完成后替换 " + targetTable);
                }
                taskRegistry.finishStep(taskId, STEP_CREATE, "SUCCESS",
                        exists ? "已创建临时表 " + workingTable : "已创建目标表");
                taskRegistry.progress(taskId, CREATE_DONE,
                        exists ? "临时表已就绪，开始搬运数据" : "目标表 " + targetLabel + " 已就绪");

                // ---- 3. 复制数据 ----
                try {
                    if (request.copyDataOrDefault()) {
                        copyData(taskId, source, target, sourceDatabase, sourceTable, targetDatabase, workingTable);
                    }
                    if (exists) {
                        swapTables(target, targetDatabase, workingTable, targetTable, taskId);
                    }
                } catch (Exception e) {
                    // 这次复制建出来的表（覆盖时是临时表，新建时就是目标表）都由我们清掉：
                    // 数据已回滚，留着只会让用户在左栏看到一张空表，还以为是复制成功了一半
                    dropQuietly(target, targetDatabase, exists ? workingTable : targetTable, taskId);
                    throw e;
                }
                taskRegistry.finish(taskId, "SUCCESS", "已复制到 " + targetLabel);
            } finally {
                // 无论成功/失败/取消都把会话库还原，绝不把切过库的连接还回池里
                restoreDatabase(source, sourceOriginal);
                restoreDatabase(target, targetOriginal);
            }
        } catch (CopyCancelledException e) {
            log.info("[db-copy] 任务 {} 已取消", taskId);
            taskRegistry.finish(taskId, "CANCELLED", "已取消复制");
        } catch (Exception e) {
            log.warn("[db-copy] 任务 {} 失败: {}", taskId, e.getMessage(), e);
            taskRegistry.finish(taskId, "FAILED", describe(e));
        } finally {
            activeTargets.remove(lockKey);
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
     * @param targetTable    写入的表（覆盖同名表时是临时表，写完后才换名成目标表）
     * @throws Exception 读写失败或用户取消
     */
    private void copyData(String taskId, Connection source, Connection target,
                          String sourceDatabase, String sourceTable,
                          String targetDatabase, String targetTable) throws Exception {
        taskRegistry.startStep(taskId, STEP_DATA, "统计源表行数");
        long total;
        try {
            total = countRows(taskId, source, sourceDatabase, sourceTable);
        } catch (SQLException e) {
            // 统计被 cancel 打断时驱动抛的是「查询被中断」，语义上就是用户取消
            if (taskRegistry.cancelled(taskId)) {
                throw new CopyCancelledException();
            }
            throw e;
        }
        checkCancelled(taskId);
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
                // 最后一批写完也要再看一次：否则在 99% 时点取消，任务仍然报「复制成功」
                checkCancelled(taskId);
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
     * <p>单条 {@code COUNT(*)} 在大表上可能跑几十秒到几分钟，这段时间里用户的取消
     * 必须生效：批次边界检查管不到这条语句，只能由旁路线程在命中取消标志时调用
     * {@link Statement#cancel()} 把它打断。</p>
     *
     * @param taskId   任务 id
     * @param conn     源连接
     * @param database 源库
     * @param table    源表
     * @return 行数
     * @throws SQLException 查询失败
     */
    private long countRows(String taskId, Connection conn, String database, String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + qualified(database, table);
        try (Statement st = conn.createStatement()) {
            CancelWatcher watcher = new CancelWatcher(taskId, st);
            watcher.start();
            try (ResultSet rs = st.executeQuery(sql)) {
                return rs.next() ? rs.getLong(1) : 0L;
            } finally {
                watcher.stopWatching();
            }
        }
    }

    /**
     * 取消旁路：轮询取消标志，命中时 {@code cancel} 掉正在执行的那条语句。
     *
     * <p>JDBC 没有「中断执行中的语句」的标准方式，{@link Statement#cancel()} 是唯一
     * 能从另一个线程安全调用的入口；本线程只在轮询间隔里睡眠，命中或停止后即退出。</p>
     */
    private final class CancelWatcher extends Thread {

        private final String taskId;

        private final Statement statement;

        private volatile boolean stopped;

        /**
         * 建一个取消旁路线程。
         *
         * @param taskId    任务 id
         * @param statement 正在执行的长语句
         */
        private CancelWatcher(String taskId, Statement statement) {
            super("db-copy-cancel-watch");
            setDaemon(true);
            this.taskId = taskId;
            this.statement = statement;
        }

        @Override
        public void run() {
            while (!stopped) {
                if (taskRegistry.cancelled(taskId)) {
                    try {
                        statement.cancel();
                    } catch (SQLException e) {
                        // 语句已经结束或连接已关：取消本来就无从下手，交给主线程收尾
                        log.debug("[db-copy] 取消统计语句失败: {}", e.getMessage());
                    }
                    return;
                }
                try {
                    Thread.sleep(CANCEL_POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        /** 主线程侧结束轮询（语句已正常结束或已抛错）。 */
        private void stopWatching() {
            stopped = true;
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
     * 把连接还原到切库前的会话库。
     *
     * <p>主数据源（{@code datasourceId=0}）的连接来自容器连接池，{@code newConnection}
     * 拿到的也是池里的连接。切库后不还原，这条连接归还池里仍停在业务库上，
     * 之后任何走同一连接的业务 SQL（登录、用户查询）都会报「表不存在」。</p>
     *
     * @param conn     连接
     * @param original 切库前的会话库；为空表示当时读不到，不做还原
     */
    private static void restoreDatabase(Connection conn, String original) {
        if (original == null || original.isBlank()) {
            return;
        }
        try {
            useDatabase(conn, original);
        } catch (SQLException e) {
            // 还原失败只能随连接一起被池回收；这里不掩盖任务本身的结论
            log.warn("[db-copy] 还原会话库 {} 失败: {}", original, e.getMessage());
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
     * 生成临时表/旧表备份的表名。
     *
     * <p>名字要满足两件事：长度不超 64（MySQL 表名上限），且能被 {@code SqlName}
     * 的标识符规则接受。表名本身超长时截断基名，后缀始终完整 —— 后缀里的任务序号
     * 是唯一性来源，截掉它两个并发任务就会撞名。</p>
     *
     * @param table  目标表名
     * @param taskId 任务 id
     * @param kind   {@code t}=临时表，{@code b}=旧表备份
     * @return 形如 {@code orders_dbmcp_t7}
     */
    static String scratchTableName(String table, String taskId, String kind) {
        String token = taskId == null ? "0" : taskId.substring(taskId.lastIndexOf('-') + 1);
        String suffix = SCRATCH_MARK + kind + token;
        int maxBase = Math.max(1, MAX_IDENT_LENGTH - suffix.length());
        String base = table.length() > maxBase ? table.substring(0, maxBase) : table;
        return base + suffix;
    }

    /**
     * 生成临时表名并避开目标库里已存在的同名表。
     *
     * <p>撞名只可能出现在「上一轮异常退出留下的中间表」这种场景，最多试 9 次；
     * 仍然撞名时照原样返回，让建表语句自己报「表已存在」，不会误删别人的表。</p>
     *
     * @param conn     目标连接
     * @param database 目标库
     * @param table    目标表名
     * @param taskId   任务 id
     * @param kind     {@code t}=临时表，{@code b}=旧表备份
     * @return 可用的中间表名
     * @throws SQLException 查询表是否存在失败
     */
    private static String scratchTableName(Connection conn, String database, String table,
                                           String taskId, String kind) throws SQLException {
        String candidate = scratchTableName(table, taskId, kind);
        for (int i = 1; i <= 9 && tableExistsInDatabase(conn, database, candidate); i++) {
            String suffix = "_" + i;
            String base = candidate.length() + suffix.length() > MAX_IDENT_LENGTH
                    ? candidate.substring(0, MAX_IDENT_LENGTH - suffix.length()) : candidate;
            candidate = base + suffix;
        }
        return candidate;
    }

    /**
     * 用临时表原子替换目标表，再把旧表备份删掉。
     *
     * <p>{@code RENAME TABLE} 在一条语句里同时完成「旧表→备份」「临时表→目标表」，
     * 中途不会出现目标表不存在的窗口：执行期间读这张表的会话要么看到旧表、
     * 要么看到新表。</p>
     *
     * @param target        目标连接
     * @param database      目标库
     * @param workingTable  已写完数据的临时表
     * @param targetTable   目标表
     * @param taskId        任务 id
     * @throws SQLException 换名失败（此时目标表未被替换）
     */
    private void swapTables(Connection target, String database, String workingTable,
                            String targetTable, String taskId) throws SQLException {
        String backup = scratchTableName(target, database, targetTable, taskId, "b");
        execute(target, "RENAME TABLE " + qualified(database, targetTable) + " TO " + qualified(database, backup)
                + ", " + qualified(database, workingTable) + " TO " + qualified(database, targetTable));
        taskRegistry.detail(taskId, "已用新表替换 " + targetTable + "，旧表备份为 " + backup);
        try {
            execute(target, "DROP TABLE " + qualified(database, backup));
        } catch (SQLException e) {
            // 换名已经生效，删备份失败只影响磁盘占用；报成失败会让用户以为数据没换
            log.warn("[db-copy] 任务 {} 删除旧表备份 {} 失败: {}", taskId, backup, e.getMessage());
            taskRegistry.detail(taskId, "旧表备份 " + backup + " 删除失败，请手动清理");
        }
    }

    /**
     * 尽力删掉一张中间表（失败/取消时的清理）。
     *
     * @param conn     目标连接
     * @param database 目标库
     * @param table    要删的表
     * @param taskId   任务 id
     */
    private void dropQuietly(Connection conn, String database, String table, String taskId) {
        try {
            execute(conn, "DROP TABLE IF EXISTS " + qualified(database, table));
        } catch (SQLException e) {
            // 连接已经坏了的时候清理也会失败；留一张中间表总比把失败原因盖掉好
            log.warn("[db-copy] 任务 {} 清理临时表 {} 失败: {}", taskId, table, e.getMessage());
        }
    }

    /**
     * 拼「数据源|库|表」占位键，标识符按忽略大小写归一。
     *
     * @param datasourceId 目标数据源 id
     * @param database     目标库，可为空
     * @param table        目标表
     * @return 占位键
     */
    static String lockKey(Long datasourceId, String database, String table) {
        return datasourceId + "|" + lower(database) + "|" + lower(table);
    }

    /**
     * 忽略大小写归一（MySQL 表名在 Windows 上大小写不敏感）。
     *
     * @param value 原值
     * @return 小写值，null 视为空串
     */
    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
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
