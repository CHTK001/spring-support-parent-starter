package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.common.support.lang.datasource.dialect.SqlName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据导入执行器：把解析好的行写入目标表。
 *
 * <p>行为对齐 Navicat 的导入向导：六种导入模式、可只导入指定字段、可选先清表、
 * 单行失败不中断整批而是汇总成错误报告，并把每条语句的执行结果实时回调给调用方
 * （SSE 日志用）。</p>
 *
 * <p><b>一条语句覆盖全部模式</b>：六个模式都只需要<b>一种</b>参数化语句 ——
 * 「追加 / 复制」是 {@code INSERT}，「不更新追加」是 {@code INSERT IGNORE}，
 * 「追加或更新」是 {@code INSERT ... ON DUPLICATE KEY UPDATE}，
 * 「更新」与「删除」是带 {@code WHERE} 键列的 {@code UPDATE} / {@code DELETE}。
 * 语句形态在循环外定好，循环里只换参数，因此仍然可以 {@code addBatch}。</p>
 *
 * <p><b>安全豁免</b>：目标表名与参与写入的列名全部经 {@link SqlName} 白名单校验并用
 * {@link SqlName#quoteMysql} 引用；单元格值一律走 {@link PreparedStatement} 参数绑定，
 * 任何用户输入都不拼进 SQL 文本。字符串值按目标列的 {@link Types} 常量做类型转换，
 * 避免把原始文本直接塞进强类型列。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class DbImporter {

    /** 错误报告最多保留的明细条数，超出只计数不逐条列出（避免响应体爆炸） */
    private static final int MAX_REPORTED_ERRORS = 50;

    /** 批量写入的攒批大小 */
    private static final int BATCH_SIZE = 500;

    private DbImporter() {
    }

    /**
     * 导入选项（旧协议，保留给既有调用方）。
     *
     * @param table         目标表名（须通过白名单校验）
     * @param sourceColumns 源列名（文件里的表头）
     * @param columnMapping 源列名 → 目标列名；未列出的源列忽略
     * @param truncateFirst 导入前是否清空目标表
     * @param onConflict    冲突策略：{@code skip} 跳过 / {@code replace} 替换
     * @param keyColumns    {@code replace} 模式用于排除主键列的更新（为空则自动取主键）
     * @param batchSize     每批行数
     */
    public record ImportOptions(
            String table,
            List<String> sourceColumns,
            Map<String, String> columnMapping,
            boolean truncateFirst,
            String onConflict,
            List<String> keyColumns,
            int batchSize
    ) {
        /**
         * 规范构造器：集合做防御性拷贝。
         *
         * @param table         目标表名
         * @param sourceColumns 源列名
         * @param columnMapping 列映射
         * @param truncateFirst 是否先清空
         * @param onConflict    冲突策略
         * @param keyColumns    定位列
         * @param batchSize     批大小
         */
        public ImportOptions {
            sourceColumns = sourceColumns == null ? List.of() : List.copyOf(sourceColumns);
            columnMapping = columnMapping == null ? Map.of() : Map.copyOf(columnMapping);
            keyColumns = keyColumns == null ? List.of() : List.copyOf(keyColumns);
        }
    }

    /**
     * 完整导入计划（新协议）。
     *
     * @param table         目标表名
     * @param sourceColumns 源列名
     * @param columnMapping 源列 → 目标列
     * @param mode          导入模式
     * @param keyColumns    匹配列（空=按主键）
     * @param advanced      高级选项
     * @param batchSize     每批行数
     * @param createTable   目标表不存在时是否按源列自动建表
     * @param truncateFirst 是否先清空（{@link ImportMode#COPY} 隐含为 true）
     * @param dateOptions   日期解读规则（目标列为日期/时间时生效）
     */
    public record ImportPlan(
            String table,
            List<String> sourceColumns,
            Map<String, String> columnMapping,
            ImportMode mode,
            List<String> keyColumns,
            ImportAdvanced advanced,
            int batchSize,
            boolean createTable,
            boolean truncateFirst,
            DateOptions dateOptions
    ) {
        /**
         * 规范构造器：集合做防御性拷贝并收敛空值。
         *
         * @param table         目标表名
         * @param sourceColumns 源列名
         * @param columnMapping 列映射
         * @param mode          导入模式
         * @param keyColumns    匹配列
         * @param advanced      高级选项
         * @param batchSize     批大小
         * @param createTable   是否自动建表
         * @param truncateFirst 是否先清空
         * @param dateOptions   日期解读规则
         */
        public ImportPlan {
            sourceColumns = sourceColumns == null ? List.of() : List.copyOf(sourceColumns);
            columnMapping = columnMapping == null ? Map.of() : Map.copyOf(columnMapping);
            keyColumns = keyColumns == null ? List.of() : List.copyOf(keyColumns);
            mode = mode == null ? ImportMode.APPEND : mode;
            advanced = advanced == null ? ImportAdvanced.defaults() : advanced;
            dateOptions = dateOptions == null ? DateOptions.defaults() : dateOptions;
            // 复制模式必须清表，否则「复制」与「追加」没有区别
            truncateFirst = truncateFirst || mode.truncateFirst();
        }

        /**
         * 实际批行数（扩展插入关闭时为 1）。
         *
         * @return 批行数
         */
        int effectiveBatch() {
            return advanced.effectiveBatchRows(batchSize);
        }
    }

    /**
     * 进度回调（SSE 日志）。
     *
     * <p>方法都给了默认空实现，调用方按需覆写：导入向导要的是
     * {@link #onStatement}，而单测只关心结果。</p>
     */
    public interface Progress {

        /** 不关心进度的占位实现 */
        Progress NOOP = new Progress() {
        };

        /**
         * 一条语句执行完毕。
         *
         * @param no       序号（从 1 起）
         * @param sql      语句文本（已截断，仅用于日志展示）
         * @param ok       是否成功
         * @param affected 影响行数
         * @param error    失败原因；成功为 null
         */
        default void onStatement(int no, String sql, boolean ok, int affected, String error) {
        }

        /**
         * 阶段提示（如「已清空目标表」「已创建表」）。
         *
         * @param text 提示文本
         */
        default void onPhase(String text) {
        }

        /**
         * 正在写第几批。
         *
         * @param done  已处理行数
         * @param total 总行数
         */
        default void onProgress(int done, int total) {
        }
    }

    /**
     * 一个「源列 → 目标列」的写入配对。
     *
     * @param source 源列名（行 Map 的键）
     * @param target 目标列名（表里的列）
     */
    private record ColumnPair(String source, String target) {
    }

    /**
     * 执行导入（旧协议入口）。
     *
     * @param conn   数据库连接
     * @param rows   源行
     * @param option 导入选项
     * @return 导入结果
     * @throws SQLException 目标表不存在、无可导入列、或清表失败
     */
    public static ImportResult importRows(Connection conn, List<Map<String, Object>> rows, ImportOptions option)
            throws SQLException {
        ImportMode mode = ImportMode.of(option.onConflict());
        ImportPlan plan = new ImportPlan(option.table(), option.sourceColumns(), option.columnMapping(),
                mode, option.keyColumns(), ImportAdvanced.defaults(), option.batchSize(), false,
                option.truncateFirst(), DateOptions.defaults());
        return importRows(conn, rows, plan, Progress.NOOP);
    }

    /**
     * 执行导入（新协议入口）。
     *
     * @param conn    数据库连接
     * @param rows    源行
     * @param plan    导入计划
     * @param progress 进度回调
     * @return 导入结果
     * @throws SQLException 目标表不存在、无可导入列、或清表失败
     */
    public static ImportResult importRows(Connection conn, List<Map<String, Object>> rows,
                                          ImportPlan plan, Progress progress) throws SQLException {
        Progress sink = progress == null ? Progress.NOOP : progress;
        String table = SqlName.checkSimple(plan.table(), "表名");
        // 建表用的列名与写入用的目标列名必须是同一批：否则「建表用清洗后的 a_b、
        // 写入按用户填的 a-b」会立刻报列不存在。因此先把映射规整成建表规格，再用它回填映射。
        java.util.Map<String, String> effectiveMapping = plan.columnMapping();
        if (plan.createTable() && !tableExists(conn, table)) {
            List<DbImportDdl.ColumnSpec> specs =
                    DbImportDdl.planColumns(plan.columnMapping(), plan.sourceColumns());
            DbImportDdl.createTable(conn, table, specs, rows);
            java.util.Map<String, String> sanitized = new LinkedHashMap<>();
            for (DbImportDdl.ColumnSpec spec : specs) {
                sanitized.put(spec.source(), spec.target());
            }
            effectiveMapping = sanitized;
            sink.onPhase("已创建表 " + table + "（" + specs.size() + " 列）");
        }
        List<String> actualColumns = readTableColumns(conn, table);
        if (actualColumns.isEmpty()) {
            throw new SQLException("表不存在或读不到列: " + table);
        }
        List<ColumnPair> pairs = resolvePairs(effectiveMapping, plan.sourceColumns(), actualColumns);
        if (pairs.isEmpty()) {
            throw new SQLException("没有可导入的列：请把文件里的列映射到目标表的列");
        }
        List<String> keyColumns = resolveKeyColumns(conn, table, pairs, plan.keyColumns());
        if ((plan.mode().updateExisting() || plan.mode().skipExisting() || plan.mode().deleteOnly())
                && keyColumns.isEmpty()) {
            throw new SQLException("「" + plan.mode().label() + "」模式需要用于匹配的键列："
                    + "该表没有主键，请在向导里指定匹配列");
        }
        boolean foreignKeyOff = plan.advanced().ignoreForeignKey() && isMysqlFamily(conn);
        if (foreignKeyOff) {
            setForeignKeyChecks(conn, false);
            sink.onPhase("已临时关闭外键约束检查");
        }
        try {
            if (plan.truncateFirst()) {
                truncate(conn, table);
                sink.onPhase("已清空目标表 " + table);
            }
            return execute(conn, table, rows, pairs, keyColumns, plan, sink);
        } finally {
            if (foreignKeyOff) {
                try {
                    setForeignKeyChecks(conn, true);
                } catch (SQLException ignored) {
                    // 连接即将归还，恢复失败不影响本次导入结果
                }
            }
        }
    }

    /**
     * 主执行循环。
     *
     * @param conn       连接
     * @param table      表名
     * @param rows       源行
     * @param pairs      列配对
     * @param keyColumns 匹配键列（已引用）
     * @param plan       导入计划
     * @param sink       进度回调
     * @return 导入结果
     * @throws SQLException 表级失败
     */
    private static ImportResult execute(Connection conn, String table, List<Map<String, Object>> rows,
                                        List<ColumnPair> pairs, List<String> keyColumns,
                                        ImportPlan plan, Progress sink) throws SQLException {
        Map<String, Integer> columnTypes = readColumnTypes(conn, table);
        String sql = buildSql(conn, table, pairs, keyColumns, plan);
        int size = Math.max(1, Math.min(plan.effectiveBatch(), 5000));
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int deleted = 0;
        List<String> errors = new ArrayList<>();
        boolean autoCommit = conn.getAutoCommit();
        if (autoCommit) {
            conn.setAutoCommit(false);
        }
        try {
            List<Map<String, Object>> buffer = new ArrayList<>(size);
            int done = 0;
            for (Map<String, Object> row : rows) {
                buffer.add(row);
                if (buffer.size() >= size) {
                    BatchOutcome outcome = flush(conn, sql, pairs, keyColumns, columnTypes, plan, buffer, done, sink);
                    inserted += outcome.inserted();
                    updated += outcome.updated();
                    skipped += outcome.skipped();
                    deleted += outcome.deleted();
                    collectErrors(errors, outcome);
                    done += buffer.size();
                    buffer.clear();
                    sink.onProgress(done, rows.size());
                }
            }
            if (!buffer.isEmpty()) {
                BatchOutcome outcome = flush(conn, sql, pairs, keyColumns, columnTypes, plan, buffer, done, sink);
                inserted += outcome.inserted();
                updated += outcome.updated();
                skipped += outcome.skipped();
                deleted += outcome.deleted();
                collectErrors(errors, outcome);
                done += buffer.size();
                sink.onProgress(done, rows.size());
            }
            conn.commit();
            return new ImportResult(rows.size(), inserted, updated, skipped, deleted, errors);
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            if (autoCommit) {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * 写入一批。
     *
     * <p>先整批 {@code executeBatch}：全成功即结束，最快路径。任一行失败则整批回滚，
     * 再逐行重试以定位到具体坏行 —— 约束冲突（唯一键、外键、非空）只有真执行才知道，
     * 所以「一行坏数据不毁掉整批」只能靠这个降级。若首行也失败，说明是表级问题
     * （列不存在、表不可写），此时直接抛出，不做无意义的逐行重试
     * （除非用户勾了「遇到错误时继续」）。</p>
     */
    private static BatchOutcome flush(Connection conn, String sql, List<ColumnPair> pairs,
                                      List<String> keyColumns, Map<String, Integer> columnTypes,
                                      ImportPlan plan, List<Map<String, Object>> buffer,
                                      int done, Progress sink) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, Object> row : buffer) {
                bind(ps, row, pairs, keyColumns, columnTypes, plan);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            conn.commit();
            Counters counters = new Counters(plan.mode());
            for (int i = 0; i < results.length; i++) {
                int result = results[i];
                counters.accept(result);
                sink.onStatement(done + i + 1, sql, true, result, null);
            }
            if (results.length < buffer.size()) {
                // 驱动可能返回数组但也可能只是省略 SUCCESS_NO_INFO；少了就按实际条数兜底
                for (int i = results.length; i < buffer.size(); i++) {
                    counters.accept(Statement.SUCCESS_NO_INFO);
                    sink.onStatement(done + i + 1, sql, true, 1, null);
                }
            }
            return counters.toOutcome(List.of());
        } catch (SQLException batchFailure) {
            conn.rollback();
            return flushRowByRow(conn, sql, pairs, keyColumns, columnTypes, plan, buffer, done,
                    batchFailure, sink);
        }
    }

    /**
     * 降级逐行写入，定位到具体坏行。
     *
     * <p><b>每行自成事务</b>：逐行 {@code commit}，某行失败只回滚该行，
     * 不会连带回滚已成功的前序行。早期版本在这里调 {@code conn.rollback()}，
     * 实测出现「报 inserted=2 但库里只有后一行」的错账——前一行已被回滚却仍计数。</p>
     */
    private static BatchOutcome flushRowByRow(Connection conn, String sql, List<ColumnPair> pairs,
                                              List<String> keyColumns, Map<String, Integer> columnTypes,
                                              ImportPlan plan, List<Map<String, Object>> buffer,
                                              int done, SQLException batchFailure, Progress sink)
            throws SQLException {
        Counters counters = new Counters(plan.mode());
        List<String> errors = new ArrayList<>();
        boolean continueOnError = plan.advanced().continueOnError();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int index = 0; index < buffer.size(); index++) {
                try {
                    bind(ps, buffer.get(index), pairs, keyColumns, columnTypes, plan);
                    int affected = ps.executeUpdate();
                    conn.commit();
                    counters.accept(affected);
                    sink.onStatement(done + index + 1, sql, true, affected, null);
                } catch (SQLException rowFailure) {
                    conn.rollback();
                    String reason = rootMessage(rowFailure);
                    // 首行就失败且用户没要求「遇错继续」= 表级问题（列不存在 / 表不可写 / 语法错），
                    // 继续逐行试没有意义
                    if (index == 0 && !continueOnError) {
                        throw rowFailure;
                    }
                    counters.skipped++;
                    if (errors.size() < MAX_REPORTED_ERRORS) {
                        errors.add("第 " + (done + index + 1) + " 行: " + reason);
                    }
                    sink.onStatement(done + index + 1, sql, false, 0, reason);
                    if (!continueOnError) {
                        throw rowFailure;
                    }
                }
            }
        }
        if (counters.applied() == 0 && counters.skipped == buffer.size() && batchFailure != null
                && !continueOnError) {
            throw batchFailure;
        }
        return new BatchOutcome(counters.inserted, counters.updated, counters.skipped, counters.deleted, errors);
    }

    /**
     * 按导入模式拼语句。
     *
     * <p>冲突策略直接决定动词，不叠加子句：`REPLACE INTO ... ON DUPLICATE KEY UPDATE`
     * 是语法冲突（实测 MariaDB 直接报错），只有 `INSERT ... ON DUPLICATE KEY UPDATE`
     * 才需要显式列出被更新的列。</p>
     *
     * @param conn       连接（用于判断方言）
     * @param table      目标表名
     * @param pairs      列配对
     * @param keyColumns 匹配键列（已引用）
     * @param plan       导入计划
     * @return 参数化语句
     * @throws SQLException 当前内核不支持该模式
     */
    private static String buildSql(Connection conn, String table, List<ColumnPair> pairs,
                                   List<String> keyColumns, ImportPlan plan) throws SQLException {
        List<String> quotedColumns = pairs.stream()
                .map(pair -> SqlName.quoteMysql(pair.target(), "列名"))
                .toList();
        String quotedTable = SqlName.quoteMysql(table, "表名");
        ImportMode mode = plan.mode();
        if (mode.deleteOnly()) {
            return "DELETE FROM " + quotedTable + " WHERE " + whereClause(keyColumns);
        }
        if (mode == ImportMode.UPDATE) {
            // SET 子句排除键列：键列是定位条件，把同一个值写回去没有意义
            List<String> settable = pairs.stream()
                    .filter(pair -> keyColumns.stream().noneMatch(k -> k.equalsIgnoreCase(
                            SqlName.quoteMysql(pair.target(), "列名"))))
                    .map(pair -> SqlName.quoteMysql(pair.target(), "列名"))
                    .toList();
            if (settable.isEmpty()) {
                throw new SQLException("「更新」模式没有可更新的列：所有列都是匹配键");
            }
            String set = String.join(", ", settable.stream().map(c -> c + " = ?").toList());
            return "UPDATE " + quotedTable + " SET " + set + " WHERE " + whereClause(keyColumns);
        }
        String insert = "INSERT INTO " + quotedTable + " (" + String.join(", ", quotedColumns)
                + ") VALUES (" + "?, ".repeat(quotedColumns.size() - 1) + "?)";
        if (mode == ImportMode.UPSERT) {
            String dialect = dialectOf(conn);
            if ("postgres".equals(dialect)) {
                return insert + " ON CONFLICT (" + String.join(", ", keyColumns) + ") DO UPDATE SET "
                        + String.join(", ", quotedColumns.stream()
                        .filter(c -> keyColumns.stream().noneMatch(k -> k.equalsIgnoreCase(c)))
                        .map(c -> c + " = EXCLUDED." + c).toList());
            }
            if (!"mysql".equals(dialect) && !"mariadb".equals(dialect)) {
                throw new SQLException("「" + mode.label() + "」模式暂不支持当前内核（" + dialect + "）");
            }
            List<String> updates = quotedColumns.stream()
                    .filter(c -> keyColumns.stream().noneMatch(k -> k.equalsIgnoreCase(c)))
                    .map(c -> c + " = VALUES(" + c + ")")
                    .toList();
            // 所有列都是键时没有可更新列，退化成「重复即忽略」
            return updates.isEmpty() ? insert + " ON DUPLICATE KEY UPDATE "
                    + quotedColumns.get(0) + " = " + quotedColumns.get(0)
                    : insert + " ON DUPLICATE KEY UPDATE " + String.join(", ", updates);
        }
        if (mode.skipExisting()) {
            String dialect = dialectOf(conn);
            if ("postgres".equals(dialect)) {
                // keys 为空时无法拼 ON CONFLICT (...)，退回 DO NOTHING（等价于忽略任意冲突）
                return keyColumns.isEmpty() ? insert + " ON CONFLICT DO NOTHING"
                        : insert + " ON CONFLICT (" + String.join(", ", keyColumns) + ") DO NOTHING";
            }
            if (!"mysql".equals(dialect) && !"mariadb".equals(dialect)) {
                throw new SQLException("「" + mode.label() + "」模式暂不支持当前内核（" + dialect + "）");
            }
            return "INSERT IGNORE INTO " + quotedTable + " (" + String.join(", ", quotedColumns)
                    + ") VALUES (" + "?, ".repeat(quotedColumns.size() - 1) + "?)";
        }
        return insert;
    }

    /**
     * WHERE 子句（全部参数绑定）。
     *
     * @param keyColumns 已引用的键列
     * @return 形如 {@code `a` = ? AND `b` = ?}
     */
    private static String whereClause(List<String> keyColumns) {
        return String.join(" AND ", keyColumns.stream().map(c -> c + " = ?").toList());
    }

    /**
     * 绑定参数。
     *
     * <p>参数顺序随模式变化：{@code UPDATE} 先绑 SET 值再绑 WHERE 的键值；
     * {@code DELETE} 只绑 WHERE 的键值；其余按列序绑定（{@code UPSERT} 的
     * {@code VALUES(col)} 子句不占参数位）。</p>
     *
     * @param ps          语句
     * @param row         源行
     * @param pairs       列配对
     * @param keyColumns  已引用的键列
     * @param columnTypes 列类型
     * @param plan        导入计划
     * @throws SQLException 绑定失败
     */
    private static void bind(PreparedStatement ps, Map<String, Object> row, List<ColumnPair> pairs,
                             List<String> keyColumns, Map<String, Integer> columnTypes, ImportPlan plan)
            throws SQLException {
        int index = 1;
        ImportMode mode = plan.mode();
        if (mode.deleteOnly()) {
            for (String key : keyColumns) {
                String source = sourceOfTarget(pairs, key);
                ps.setObject(index++, coerce(row.get(source), columnTypes.get(unquote(key)),
                        plan.advanced().nullForEmpty(), plan.dateOptions()));
            }
            return;
        }
        List<ColumnPair> settable = mode == ImportMode.UPDATE
                ? pairs.stream().filter(pair -> keyColumns.stream().noneMatch(
                        k -> k.equalsIgnoreCase(SqlName.quoteMysql(pair.target(), "列名")))).toList()
                : pairs;
        for (ColumnPair pair : settable) {
            ps.setObject(index++, coerce(row.get(pair.source()), columnTypes.get(pair.target()),
                    plan.advanced().nullForEmpty(), plan.dateOptions()));
        }
        if (mode == ImportMode.UPDATE) {
            for (String key : keyColumns) {
                String source = sourceOfTarget(pairs, key);
                ps.setObject(index++, coerce(row.get(source), columnTypes.get(unquote(key)),
                        plan.advanced().nullForEmpty(), plan.dateOptions()));
            }
        }
    }

    /**
     * 由目标列反查源列名。
     *
     * @param pairs 列配对
     * @param quotedTarget 已引用的目标列
     * @return 源列名
     */
    private static String sourceOfTarget(List<ColumnPair> pairs, String quotedTarget) {
        String target = unquote(quotedTarget);
        return pairs.stream()
                .filter(pair -> pair.target().equalsIgnoreCase(target))
                .map(ColumnPair::source)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("匹配列未参与映射: " + target));
    }

    /**
     * 去掉引用符（反引号 / 双引号）。
     *
     * @param quoted 已引用的标识符
     * @return 原始列名
     */
    private static String unquote(String quoted) {
        return quoted.replace("`", "").replace("\"", "");
    }

    /**
     * 解析「源列 → 目标列」配对，按目标表列顺序排列。
     *
     * <p>目标顺序来自表定义，因此生成的 INSERT 列序稳定、可被数据库复用索引。</p>
     *
     * @param columnMapping 源列 → 目标列（自动建表时已换成清洗后的目标名）
     * @param sourceColumns 源列名（映射未覆盖时按同名对齐）
     * @param actualColumns 目标表实际列名
     * @return 列配对
     */
    private static List<ColumnPair> resolvePairs(Map<String, String> columnMapping,
                                                 List<String> sourceColumns,
                                                 List<String> actualColumns) {
        Map<String, String> reverse = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : columnMapping.entrySet()) {
            reverse.putIfAbsent(entry.getValue().trim().toLowerCase(Locale.ROOT), entry.getKey());
        }
        List<ColumnPair> pairs = new ArrayList<>();
        List<String> usedSources = new ArrayList<>();
        for (String target : actualColumns) {
            String lower = target.toLowerCase(Locale.ROOT);
            String source = reverse.get(lower);
            if (source == null) {
                for (String candidate : sourceColumns) {
                    if (candidate.equalsIgnoreCase(target)) {
                        source = candidate;
                        break;
                    }
                }
            }
            if (source != null && !usedSources.contains(source)) {
                usedSources.add(source);
                pairs.add(new ColumnPair(source, target));
            }
        }
        return pairs;
    }

    /**
     * 位置列：用户指定优先，否则取表主键。
     *
     * @param conn        连接
     * @param table       表名
     * @param pairs       列配对
     * @param declared    用户指定的键列
     * @return 已引用的键列
     * @throws SQLException 读取主键失败
     */
    private static List<String> resolveKeyColumns(Connection conn, String table,
                                                  List<ColumnPair> pairs, List<String> declared)
            throws SQLException {
        List<String> keys = new ArrayList<>();
        List<String> targetNames = pairs.stream().map(ColumnPair::target).toList();
        if (!declared.isEmpty()) {
            for (String key : declared) {
                String hit = targetNames.stream().filter(c -> c.equalsIgnoreCase(key)).findFirst().orElse(null);
                if (hit != null) {
                    keys.add(SqlName.quoteMysql(hit, "列名"));
                }
            }
            return keys;
        }
        for (String[] scope : new String[][]{{conn.getCatalog(), conn.getSchema()}, {null, null}}) {
            try (ResultSet rs = conn.getMetaData().getPrimaryKeys(scope[0], scope[1], table)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    if (targetNames.stream().anyMatch(c -> c.equalsIgnoreCase(name))) {
                        keys.add(SqlName.quoteMysql(name, "主键列"));
                    }
                }
            } catch (SQLException ignored) {
                // 取不到主键就继续试下一种 scope
            }
            if (!keys.isEmpty()) {
                return keys;
            }
        }
        return keys;
    }

    /**
     * 把字符串值转成列的目标 JDBC 类型。
     *
     * @param value      源值
     * @param sqlType    目标列的 {@link Types} 常量；读不到时按文本处理
     * @param nullForEmpty 是否把空串当 NULL（对应「使用 NULL 取代空白字符串」）
     * @param dateOptions 日期解读规则（目标列为日期/时间时生效）
     * @return 可直接 {@code setObject} 的值；空串与 {@code \N} 视为 NULL
     * @throws SQLException 转换失败
     */
    private static Object coerce(Object value, Integer sqlType, boolean nullForEmpty,
                                 DateOptions dateOptions) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof java.util.Date) {
            return value;
        }
        String text = String.valueOf(value).trim();
        // \N 是 mysqldump 的 NULL 记号，任何模式下都该还原成 NULL
        if ("\\N".equals(text)) {
            return null;
        }
        if (text.isEmpty()) {
            // 未勾选「NULL 取代空白」时空串按空串写（原行为）；
            // 勾选后写 NULL —— 这正是该选项的语义
            return nullForEmpty ? null : text;
        }
        if (sqlType == null) {
            return text;
        }
        // 日期列：先按用户给的顺序与分隔符归一化，再交给驱动。
        // 归不出来就原样传下去，让数据库报「值不合法」——静默写错日期更难查。
        if (isDateLike(sqlType) && dateOptions != null) {
            String normalized = dateOptions.normalize(text);
            if (normalized != null) {
                return normalized;
            }
        }
        try {
            return switch (sqlType) {
                case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> Integer.valueOf(text);
                case Types.BIGINT -> Long.valueOf(text);
                case Types.FLOAT, Types.REAL -> Float.valueOf(text);
                case Types.DOUBLE -> Double.valueOf(text);
                case Types.DECIMAL, Types.NUMERIC -> new BigDecimal(text);
                case Types.BOOLEAN, Types.BIT -> parseBoolean(text);
                default -> text;
            };
        } catch (NumberFormatException e) {
            throw new SQLException("值 \"" + abbreviate(text) + "\" 无法转为该列的数据类型");
        }
    }

    /**
     * 该 JDBC 类型是否属于日期/时间族。
     *
     * @param sqlType {@link Types} 常量
     * @return true=日期或时间
     */
    private static boolean isDateLike(int sqlType) {
        return sqlType == Types.DATE || sqlType == Types.TIME || sqlType == Types.TIMESTAMP
                || sqlType == Types.TIMESTAMP_WITH_TIMEZONE || sqlType == Types.TIME_WITH_TIMEZONE;
    }

    private static Boolean parseBoolean(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "y", "on", "t", "是" -> Boolean.TRUE;
            case "0", "false", "no", "n", "off", "f", "否" -> Boolean.FALSE;
            default -> throw new NumberFormatException("不是合法布尔值: " + abbreviate(text));
        };
    }

    private static String abbreviate(String value) {
        return value.length() <= 40 ? value : value.substring(0, 40) + "…";
    }

    /**
     * 清空目标表。
     *
     * @param conn  连接
     * @param table 表名
     * @throws SQLException 清空失败（TRUNCATE 与 DELETE 都失败时抛）
     */
    static void truncate(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE " + SqlName.quoteMysql(table, "表名"));
        } catch (SQLException fallback) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM " + SqlName.quoteMysql(table, "表名"));
            }
        }
    }

    /**
     * 表是否存在。
     *
     * @param conn  连接
     * @param table 表名
     * @return true=存在
     * @throws SQLException 元数据读取失败
     */
    static boolean tableExists(Connection conn, String table) throws SQLException {
        for (String[] scope : new String[][]{{conn.getCatalog(), conn.getSchema()}, {conn.getCatalog(), null},
                {null, conn.getSchema()}, {null, null}}) {
            try (ResultSet rs = conn.getMetaData().getTables(scope[0], scope[1], table,
                    new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<String> readTableColumns(Connection conn, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        for (String[] scope : new String[][]{{conn.getCatalog(), conn.getSchema()}, {null, null}}) {
            try (ResultSet rs = conn.getMetaData().getColumns(scope[0], scope[1], table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    if (name != null && !columns.contains(name)) {
                        columns.add(name);
                    }
                }
            }
            if (!columns.isEmpty()) {
                return columns;
            }
        }
        return columns;
    }

    private static Map<String, Integer> readColumnTypes(Connection conn, String table) {
        Map<String, Integer> types = new LinkedHashMap<>();
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), conn.getSchema(), table, "%")) {
            while (rs.next()) {
                types.put(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE"));
            }
        } catch (SQLException ignored) {
            // 读不到类型时按文本处理
        }
        return types;
    }

    /**
     * 方言名（小写）。
     *
     * @param conn 连接
     * @return mysql / mariadb / postgres / 其它产品名小写
     */
    private static String dialectOf(Connection conn) {
        try {
            String product = conn.getMetaData().getDatabaseProductName();
            String lower = product == null ? "" : product.toLowerCase(Locale.ROOT);
            if (lower.contains("mariadb")) {
                return "mariadb";
            }
            if (lower.contains("mysql")) {
                return "mysql";
            }
            if (lower.contains("postgres")) {
                return "postgres";
            }
            return lower;
        } catch (SQLException ignored) {
            return "";
        }
    }

    private static boolean isMysqlFamily(Connection conn) {
        String dialect = dialectOf(conn);
        return "mysql".equals(dialect) || "mariadb".equals(dialect);
    }

    /**
     * 会话级开关外键检查（仅 MySQL/MariaDB）。
     *
     * @param conn  连接
     * @param on    true=开启
     * @throws SQLException 执行失败
     */
    private static void setForeignKeyChecks(Connection conn, boolean on) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS=" + (on ? "1" : "0"));
        }
    }

    private static void collectErrors(List<String> target, BatchOutcome outcome) {
        for (String error : outcome.errors()) {
            if (target.size() < MAX_REPORTED_ERRORS) {
                target.add(error);
            }
        }
    }

    private static String rootMessage(SQLException e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }

    /**
     * 按模式把 JDBC 的影响行数翻译成「插入 / 更新 / 跳过 / 删除」。
     *
     * <p>MySQL 的 {@code ON DUPLICATE KEY UPDATE} 是有信息量的：1=插入、2=更新、
     * 0=值未变（既不插也不更新）。据此才能给出与 Navicat 一致的分项统计。</p>
     */
    private static final class Counters {

        private final ImportMode mode;
        private int inserted;
        private int updated;
        private int skipped;
        private int deleted;

        private Counters(ImportMode mode) {
            this.mode = mode;
        }

        private void accept(int affected) {
            switch (mode) {
                case UPDATE -> {
                    if (affected > 0) {
                        updated += affected;
                    } else {
                        skipped++;
                    }
                }
                case UPSERT -> {
                    // 1=插入，2=更新，0=值未变
                    if (affected == 1) {
                        inserted++;
                    } else if (affected >= 2) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
                case INSERT_IGNORE -> {
                    if (affected > 0) {
                        inserted += affected;
                    } else {
                        skipped++;
                    }
                }
                case DELETE -> {
                    if (affected > 0) {
                        deleted += affected;
                    } else {
                        skipped++;
                    }
                }
                default -> inserted += affected >= 0 ? affected : 1;
            }
        }

        private int applied() {
            return inserted + updated + deleted;
        }

        private BatchOutcome toOutcome(List<String> errors) {
            return new BatchOutcome(inserted, updated, skipped, deleted, errors);
        }
    }

    /**
     * 批结果。
     *
     * @param inserted 插入成功行数
     * @param updated  更新成功行数
     * @param skipped  跳过行数
     * @param deleted  删除行数
     * @param errors   错误明细
     */
    private record BatchOutcome(int inserted, int updated, int skipped, int deleted, List<String> errors) {
    }

    /**
     * 导入结果。
     *
     * @param totalRows 源行总数
     * @param inserted  新插入行数
     * @param updated   更新行数
     * @param skipped   跳过行数
     * @param deleted   删除行数
     * @param errors    错误明细
     */
    public record ImportResult(int totalRows, int inserted, int updated, int skipped, int deleted,
                               List<String> errors) {

        /**
         * 规范构造器：集合做防御性拷贝。
         *
         * @param totalRows 源行总数
         * @param inserted  新插入行数
         * @param updated   更新行数
         * @param skipped   跳过行数
         * @param deleted   删除行数
         * @param errors    错误明细
         */
        public ImportResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        /**
         * 是否全部成功。
         *
         * @return true=无失败
         */
        public boolean allSucceeded() {
            return skipped == 0 && errors.isEmpty();
        }

        /**
         * 旧协议的 {@code replaced} 字段（前端老版本读它）。
         *
         * @return 等价于 {@link #updated()}
         */
        public int replaced() {
            return updated;
        }
    }
}
