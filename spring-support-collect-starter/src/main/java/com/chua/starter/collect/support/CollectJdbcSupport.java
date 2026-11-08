// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.support;

import com.chua.starter.collect.entity.CollectDatasource;
import com.chua.starter.collect.mapper.CollectDatasourceMapper;
import com.chua.starter.collect.vo.CollectColumnVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.datasource.dialect.SqlName;
import com.chua.starter.datasourcehub.support.model.SchemaColumnVO;
import com.chua.starter.datasourcehub.support.model.dto.DdlColumn;
import com.chua.starter.datasourcehub.support.model.dto.TableDdlDTO;
import com.chua.starter.datasourcehub.support.service.DatasourceSchemaService;
import com.chua.starter.datasourcehub.support.support.JdbcDialectSupport;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 采集平台的 JDBC 通道。
 *
 * <p>SQL 节点与结果导出共用它。数据源引用留空或填 {@code app} 时用应用主库，
 * 否则按 {@code collect_datasource} 的ID或名称解析，连接池按数据源缓存复用。</p>
 *
 * <p>语句由流程作者编写，本类只保证一次执行一条语句、表名列名合法、值全部走
 * PreparedStatement 绑定，不做语句白名单。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectJdbcSupport {

    /**
     * 应用主库引用名。
     */
    public static final String APP_DATASOURCE = "app";

    /**
     * 结果表主键列。
     */
    private static final String ID_COLUMN = "collect_export_id";

    /**
     * 导出表列的默认类型。
     */
    private static final String COLUMN_TYPE = "TEXT";

    /**
     * 视为「无长度上限」的列宽阈值。
     *
     * <p>大文本列的 {@code size} 在驱动里报成 {@link Integer#MAX_VALUE} 或接近它的值，
     * 比这个阈值大的列一律当作已经能装下任意长度，不再参与放宽判断。</p>
     */
    private static final int UNBOUNDED_TEXT_SIZE = 65535;

    /**
     * 连接池缓存，数据源引用到连接池。
     */
    private final Map<String, DataSource> pools = new ConcurrentHashMap<>();

    /**
     * 数据源解析器，台账读数据源中心。
     */
    private final CollectDatasourceRegistry registry;

    /**
     * 数据源模块（hub）的结构与 DDL 服务。
     *
     * <p>表清单、列元数据、建表/补列都委托它：这些能力归数据源模块，采集侧不再自实现。</p>
     */
    private final DatasourceSchemaService schemaService;

    /**
     * 应用自身数据源。
     */
    private final ObjectProvider<DataSource> applicationDataSource;

    /**
     * 解析数据源。
     *
     * @param reference 留空或 app 表示应用主库，其余按 collect_datasource 的ID或名称
     * @return 数据源
     */
    public DataSource resolve(String reference) {
        String key = reference == null || reference.isBlank() ? APP_DATASOURCE : reference.trim();
        DataSource cached = pools.get(key);
        if (cached != null) {
            return cached;
        }
        DataSource created = create(key);
        pools.put(key, created);
        return created;
    }

    /**
     * 数据源配置变更后丢弃缓存的连接池，应用主库只解除引用不关闭。
     *
     * @param reference 数据源引用
     */
    public void discard(String reference) {
        String key = reference == null || reference.isBlank() ? APP_DATASOURCE : reference.trim();
        DataSource dataSource = pools.remove(key);
        if (!APP_DATASOURCE.equals(key) && dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }

    /**
     * 执行查询。
     *
     * @param reference 数据源引用
     * @param sql       单条查询语句
     * @return 有序行映射列表
     */
    public List<Map<String, Object>> query(String reference, String sql) {
        String statement = singleStatement(sql, true);
        try (Connection connection = resolve(reference).getConnection();
             PreparedStatement ps = connection.prepareStatement(statement);
             ResultSet rs = ps.executeQuery()) {
            return readRows(rs);
        } catch (SQLException ex) {
            throw new IllegalStateException("查询失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 按单列批量查「已存在」的键。
     *
     * <p>「查已存在」节点的专用通道：表名与列名走严格 ASCII 标识符白名单，
     * 值全部通过 {@link PreparedStatement} 绑定，不做任何字符串拼接 ——
     * 9429 个中文成语若拼成字面量是几 MB SQL，会顶到 {@code max_allowed_packet}，
     * 且拼接等于没有防注入。IN 列表由调用方按片拆分（每片默认 500），
     * 本方法只负责单片：占位符数量与值数量一致、逐位绑定。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @param column    列名
     * @param values    待查值集合（单片），内部去重
     * @return 命中的值集合（去重），无命中返回空集合
     */
    public Set<Object> queryIn(String reference, String table, String column, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Object> distinct = new ArrayList<>(new LinkedHashSet<>(values));
        StringBuilder placeholders = new StringBuilder(distinct.size() * 2);
        for (int i = 0; i < distinct.size(); i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append('?');
        }
        try (Connection connection = resolve(reference).getConnection()) {
            String sql = "SELECT " + quoted(connection, column)
                    + " FROM " + quoted(connection, table)
                    + " WHERE " + quoted(connection, column)
                    + " IN (" + placeholders + ")";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (int i = 0; i < distinct.size(); i++) {
                    ps.setObject(i + 1, value(distinct.get(i)));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    Set<Object> found = new LinkedHashSet<>();
                    while (rs.next()) {
                        found.add(clean(rs.getObject(1)));
                    }
                    return found;
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("查询已存在失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 列出数据源里的表与视图。
     *
     * <p>表结构属于数据源模块的能力：这里直接委托 {@code DatasourceSchemaService}，
     * 采集侧不再自己读元数据，保证"同一份实现"。</p>
     *
     * @param reference 数据源引用
     * @return 表名列表，已去重并按名称排序
     */
    public List<String> listTables(String reference) {
        return schemaService.listTables(reference);
    }

    /**
     * 列出指定表的列元数据。
     *
     * <p>同样委托数据源模块：跨方言的大小写处理、主键标记都在
     * {@code DatasourceSchemaService} 里统一做，采集侧只做一次 VO 转换。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @return 列元数据，按列序号排序
     */
    public List<CollectColumnVO> listColumns(String reference, String table) {
        List<SchemaColumnVO> columns = schemaService.listColumns(reference, table);
        List<CollectColumnVO> result = new ArrayList<>(columns.size());
        for (SchemaColumnVO column : columns) {
            result.add(new CollectColumnVO(column.name(), column.label(), column.position(),
                    column.typeName(), column.size(), column.nullable(), column.primaryKey()));
        }
        return result;
    }

    /**
     * 执行更新。
     *
     * @param reference 数据源引用
     * @param sql       单条更新语句
     * @return 受影响行数
     */
    public int update(String reference, String sql) {
        String statement = singleStatement(sql, false);
        try (Connection connection = resolve(reference).getConnection();
             PreparedStatement ps = connection.prepareStatement(statement)) {
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("更新失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 插入一行。
     *
     * <p>表名与列名只能来自严格 ASCII 标识符白名单，值全部通过 {@link PreparedStatement} 绑定。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @param row       列名到值的映射，顺序即插入顺序
     * @return 受影响行数
     */
    public int insert(String reference, String table, Map<String, Object> row) {
        List<String> columns = new ArrayList<>(row.keySet());
        for (String column : columns) {
            identifier(column);
        }
        try (Connection connection = resolve(reference).getConnection()) {
            String name = quoted(connection, table);
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(name).append(" (");
            sql.append(String.join(", ", quotedAll(connection, columns))).append(") VALUES (");
            sql.append(String.join(", ", columns.stream().map(column -> "?").toList())).append(")");
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                for (int index = 0; index < columns.size(); index++) {
                    ps.setObject(index + 1, value(row.get(columns.get(index))));
                }
                return ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("写入 " + table + " 失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 批量插入多行。
     *
     * <p>与单行插入同一套安全约束：表名与列名只能来自严格 ASCII 标识符白名单，
     * 值全部通过 {@link PreparedStatement} 绑定。列集合以首行为准——上游集合各行
     * 的键可能不齐，取首行会让后续多出来的列静默丢失，因此这里按所有行的键并集
     * 建列，缺的填 {@code null}。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @param rows      行集合，按写入顺序
     * @return 受影响行数
     */
    public int insertBatch(String reference, String table, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        List<String> columns = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> row : rows) {
            for (String column : row.keySet()) {
                if (seen.add(column)) {
                    columns.add(column);
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("没有可写入的列");
        }
        for (String column : columns) {
            identifier(column);
        }
        try (Connection connection = resolve(reference).getConnection()) {
            String name = quoted(connection, table);
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(name).append(" (");
            sql.append(String.join(", ", quotedAll(connection, columns))).append(") VALUES (");
            sql.append(String.join(", ", columns.stream().map(column -> "?").toList())).append(")");
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int affected = executeBatch(connection, sql.toString(), columns, rows);
                connection.commit();
                return affected;
            } catch (SQLException failure) {
                connection.rollback();
                if (!isDuplicateKey(failure)) {
                    throw failure;
                }
                // 唯一键冲突是采集里的常态（同一首诗的标题+作者重复、重跑撞已有行），
                // 不该让整批、进而整个任务失败：改成逐行写，只为重复行让路。
                int inserted = insertSkippingDuplicates(connection, sql.toString(), columns, rows);
                log.warn("[采集写库] {} 命中唯一键冲突，已跳过 {} 行重复数据",
                        table, rows.size() - inserted);
                connection.commit();
                return inserted;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("批量写入 " + table + " 失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 执行一次预编译批处理并统计真正写入的行数。
     */
    private int executeBatch(Connection connection, String sql, List<String> columns,
                             List<Map<String, Object>> rows) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                for (int index = 0; index < columns.size(); index++) {
                    ps.setObject(index + 1, value(row.get(columns.get(index))));
                }
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            int affected = 0;
            for (int result : results) {
                // 驱动对成功批次可能返回 SUCCESS_NO_INFO(-2)，不能当失败计数
                if (result >= 0 || result == Statement.SUCCESS_NO_INFO) {
                    affected++;
                }
            }
            return affected;
        }
    }

    /**
     * 逐行补写：唯一键冲突的行跳过，其余错误照旧抛出。
     *
     * <p>批量失败后走这条路，行数通常只有几百到几千，逐行执行慢一些但语义清晰：
     * 重复数据不写、也不报错，真正的异常（列不存在、类型不匹配）仍然让任务失败。</p>
     */
    private int insertSkippingDuplicates(Connection connection, String sql, List<String> columns,
                                         List<Map<String, Object>> rows) throws SQLException {
        int inserted = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                try {
                    for (int index = 0; index < columns.size(); index++) {
                        ps.setObject(index + 1, value(row.get(columns.get(index))));
                    }
                    inserted += ps.executeUpdate();
                } catch (SQLException rowFailure) {
                    if (!isDuplicateKey(rowFailure)) {
                        throw rowFailure;
                    }
                }
            }
        }
        return inserted;
    }

    /**
     * 判断异常链里是否含唯一键/主键冲突。
     *
     * <p>各库的错误码不同：MySQL/MariaDB 1062、SQL Server 2601/2627、PostgreSQL/H2 23505；
     * 另外 SQLState 23 段是完整性约束，再按消息里的 duplicate 兜一层。</p>
     */
    private static boolean isDuplicateKey(SQLException failure) {
        for (SQLException ex = failure; ex != null; ex = ex.getNextException()) {
            int code = ex.getErrorCode();
            if (code == 1062 || code == 2601 || code == 2627) {
                return true;
            }
            String state = ex.getSQLState();
            if ("23505".equals(state)) {
                return true;
            }
            String message = ex.getMessage();
            if (state != null && state.startsWith("23") && message != null
                    && message.toLowerCase(java.util.Locale.ROOT).contains("duplicate")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按业务主键写入：命中主键的行做 UPDATE，未命中的行做 INSERT。
     *
     * <p>跨方言的 {@code ON DUPLICATE KEY UPDATE} 与 {@code ON CONFLICT} 没法用同一句
     * SQL 拼出来，所以这里走通用两步法：先按主键 {@code SELECT} 探测某一行在不在，
     * 再据此执行 {@code UPDATE} 或 {@code INSERT}。整套语句在同一个连接、同一个事务里
     * 执行，任一行失败就整体回滚，不会留下"更新了一半"的中间状态。</p>
     *
     * <p>逐行探测而不是整批 {@code IN}：联合键要按行展开成
     * {@code (k1=? AND k2=?) OR (k1=? AND k2=?)}，参数个数随主键列数成倍增长；
     * 而且各驱动回读主键值的类型、大小写与空白填充规则并不一致，拿回读值做匹配
     * 会把"没匹配上"误判成新增。批量单位仍由调用方的每批行数决定，探测语句复用
     * 同一条预编译语句与同一个连接。</p>
     *
     * <p>表名与列名只能来自严格 ASCII 标识符白名单并统一加引用符；值全部经
     * {@link PreparedStatement} 绑定，不做任何字符串拼接。主键值参与 {@code =} 比较，
     * {@code null} 恒不命中、会退化成每次新增，因此这里遇到空主键直接报错。</p>
     *
     * @param reference 数据源引用
     * @param table     目标表
     * @param keys      业务主键列，必须都出现在待写入行的键里
     * @param rows      待写入行，列集合取所有行的键并集
     * @return 更新与新增的行数
     * @throws IllegalStateException 执行失败，事务已回滚
     */
    public UpsertResult updateByKey(String reference, String table, List<String> keys,
                                    List<Map<String, Object>> rows) {
        KeyWriteResult result = writeByKey(reference, table, keys, rows, false);
        return new UpsertResult(result.updated(), result.inserted());
    }

    /**
     * 按业务键「跳过式」写入：命中原键的行整行跳过，未命中的插入。
     *
     * <p>与 {@link #updateByKey} 共用同一套「批量事务 + 业务键定位」骨架，
     * 只把「命中则 UPDATE」换成「命中则跳过」。跳过模式下用户要的是
     * 「已有数据不许被动」，所以命中时连一列都不更新 —— 那是语义越界。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @param keys      业务键列，必须都出现在待写入行的键里
     * @param rows      待写入行
     * @return 跳过与新增的行数
     */
    public SkipResult insertIfAbsent(String reference, String table, List<String> keys,
                                     List<Map<String, Object>> rows) {
        KeyWriteResult result = writeByKey(reference, table, keys, rows, true);
        return new SkipResult(result.skipped(), result.inserted());
    }

    /**
     * 值是否为空：null 或空串（不 trim，纯空白视为有值，避免误判用户有意写入的空行）。
     *
     * @param value 待判断值
     * @return 空返回 true
     */
    private boolean isBlankValue(Object value) {
        return value == null || (value instanceof CharSequence && ((CharSequence) value).length() == 0);
    }

    /**
     * 按业务键写入的共用骨架：批量事务内逐行「定位 → 更新或跳过 → 插入」。
     *
     * <p>骨架只此一份：{@link #updateByKey} 与 {@link #insertIfAbsent} 的差别
     * 只在命中分支走 UPDATE 还是直接跳过。两侧各写一份拼接 SQL 的实现，
     * 以后改标识符白名单 / 参数绑定这类安全相关的代码必然漏改一边。</p>
     *
     * @param skipExisting true 表示命中即跳过（不更新）；false 表示命中即更新
     */
    private KeyWriteResult writeByKey(String reference, String table, List<String> keys,
                                      List<Map<String, Object>> rows, boolean skipExisting) {
        if (rows == null || rows.isEmpty()) {
            return new KeyWriteResult(0, 0, 0);
        }
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("按主键写入必须声明至少一个主键列");
        }
        List<String> columns = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> row : rows) {
            for (String column : row.keySet()) {
                if (seen.add(column)) {
                    columns.add(column);
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("没有可写入的列");
        }
        for (String key : keys) {
            if (!seen.contains(key)) {
                throw new IllegalArgumentException("主键列 " + key + " 不在待写入的列里");
            }
        }
        for (String column : columns) {
            identifier(column);
        }
        try (Connection connection = resolve(reference).getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String name = quoted(connection, table);
                List<String> predicates = new ArrayList<>(keys.size());
                for (String key : keys) {
                    predicates.add(quoted(connection, key) + " = ?");
                }
                String where = String.join(" AND ", predicates);
                String updateSqlBase = "UPDATE " + name + " SET ";
                String updateSqlTail = " WHERE " + where;
                String selectSql = "SELECT 1 FROM " + name + " WHERE " + where;
                String insertSql = "INSERT INTO " + name + " ("
                        + String.join(", ", quotedAll(connection, columns)) + ") VALUES ("
                        + String.join(", ", columns.stream().map(column -> "?").toList()) + ")";
                // 跳过模式每次执行的都是同一句 UPDATE；覆盖模式要按行「只补空」，
                // SET 子句随每行的非空列变化，所以按行现拼 SQL（列名走白名单，值仍全部预编译绑定）
                String updateSqlFull = updateSqlBase
                        + String.join(", ", columns.stream().map(column -> quoted(connection, column) + " = ?").toList())
                        + updateSqlTail;
                int updated = 0;
                int skipped = 0;
                int inserted = 0;
                try (PreparedStatement select = connection.prepareStatement(selectSql);
                     PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    for (Map<String, Object> row : rows) {
                        for (int index = 0; index < keys.size(); index++) {
                            Object keyValue = row.get(keys.get(index));
                            if (keyValue == null) {
                                throw new IllegalStateException("主键列 " + keys.get(index)
                                        + " 的值为空，无法定位已有行，请检查该列的取值表达式");
                            }
                            select.setObject(index + 1, value(keyValue));
                        }
                        boolean exists;
                        try (ResultSet rs = select.executeQuery()) {
                            exists = rs.next();
                        }
                        if (exists) {
                            if (skipExisting) {
                                // 跳过模式：命中就整行放过，字段一个都不动
                                skipped++;
                                continue;
                            }
                            // 合并更新只补空：新值为 null / 空串时这一列不进 SET 子句，
                            // 否则整行覆盖会把别的来源已补齐的译文/赏析/标签清空（实测风险）。
                            // 空值与键列必须一起排除：键列只出现在 WHERE，不能进 SET。
                            List<String> sets = new ArrayList<>(columns.size());
                            for (String column : columns) {
                                if (keys.contains(column) || isBlankValue(row.get(column))) {
                                    continue;
                                }
                                sets.add(column);
                            }
                            if (sets.isEmpty()) {
                                // 这一行没有任何能补的列，等于跳过
                                skipped++;
                                continue;
                            }
                            String updateSql = updateSqlBase
                                    + String.join(", ", sets.stream().map(column -> quoted(connection, column) + " = ?").toList())
                                    + updateSqlTail;
                            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                                int index = 1;
                                for (String column : sets) {
                                    update.setObject(index++, value(row.get(column)));
                                }
                                for (String key : keys) {
                                    update.setObject(index++, value(row.get(key)));
                                }
                                update.executeUpdate();
                            }
                            updated++;
                        } else {
                            for (int index = 0; index < columns.size(); index++) {
                                insert.setObject(index + 1, value(row.get(columns.get(index))));
                            }
                            insert.executeUpdate();
                            inserted++;
                        }
                    }
                    connection.commit();
                }
                return new KeyWriteResult(updated, skipped, inserted);
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                throw new IllegalStateException("按业务键写入 " + table + " 失败: " + ex.getMessage(), ex);
            } catch (RuntimeException ex) {
                // 主键为空这类配置错误同样要把整批回滚，不能留下写了一半的数据
                rollbackQuietly(connection);
                throw ex;
            } finally {
                restoreAutoCommit(connection, autoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("写入 " + table + " 失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 清空目标表的全部数据，保留表结构。
     *
     * <p>用于「覆盖写入」：先删后插在同一个连接里没有跨语句事务保护，
     * 中途失败会留下半张表，所以删完立刻插是最坏情况也只有数据不全，
     * 不会把旧数据和新数据混在一起让下游读到脏数据。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @return 受影响行数
     */
    public int deleteAll(String reference, String table) {
        identifier(table);
        try (Connection connection = resolve(reference).getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM " + quoted(connection, table))) {
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("清空 " + table + " 失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 清空目标表的全部数据（TRUNCATE），保留表结构。
     *
     * <p>与 {@link #deleteAll} 的差别不只是快：TRUNCATE 会把表回到"刚建好的样子"
     * （自增列复位、不写行级日志），这正是「全覆盖」要的语义。
     * TRUNCATE 本身不返回受影响行数，所以清空前的行数先 COUNT 出来 ——
     * 节点摘要里的"清空 N 行"是用户判断"这次有没有把昨天的数据冲掉"的唯一依据。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @return 清空前的行数
     */
    public int truncate(String reference, String table) {
        identifier(table);
        try (Connection connection = resolve(reference).getConnection()) {
            String name = quoted(connection, table);
            int cleared;
            try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM " + name);
                 ResultSet rs = count.executeQuery()) {
                cleared = rs.next() ? rs.getInt(1) : 0;
            }
            try (PreparedStatement ps = connection.prepareStatement("TRUNCATE TABLE " + name)) {
                ps.executeUpdate();
            }
            return cleared;
        } catch (SQLException ex) {
            throw new IllegalStateException("清空(TRUNCATE) " + table + " 失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 取目标表的主键列名。
     *
     * <p>「按主键更新」模式靠它自动定位 —— 用户不必手填主键，
     * 也就不会把业务键误当主键填进去（两者混用时更新的是不同的行）。
     * 表不存在或没有主键时返回空列表，由调用方决定是报错还是降级。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @return 主键列名，按列序号排序
     */
    public List<String> primaryKeys(String reference, String table) {
        List<String> keys = new ArrayList<>();
        for (CollectColumnVO column : listColumns(reference, table)) {
            if (Boolean.TRUE.equals(column.primaryKey())) {
                keys.add(column.name());
            }
        }
        return keys;
    }

    /**
     * 建结果表并补齐缺失列，列类型统一用大文本。
     *
     * <p>表名与列名只能来自严格 ASCII 标识符白名单；已存在表还必须由 JDBC 元数据确认，
     * 不允许将流程作者提供的任意标识符片段直接拼入 DDL。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @param columns   列名集合
     */
    public void ensureTable(String reference, String table, Collection<String> columns) {
        String name = identifier(table);
        List<DdlColumn> targets = new ArrayList<>(columns.size());
        for (String column : columns) {
            // 结果列一律按大文本建：导出内容可能是长 JSON / 长文本，
            // 交给数据源模块按来源类型推断会落成 VARCHAR(255)，会截断甚至写入报错。
            targets.add(new DdlColumn(identifier(column), null, Boolean.TRUE, null, null, column, "TEXT"));
        }
        // 建表/补列的方言拼装与"表已存在"策略由数据源模块负责：表不存在则建（自带自增主键 id），
        // 已存在则只补缺失列——与原来 ensureTable 的语义一致，但类型与主键由数据源模块统一决定。
        schemaService.executeDdl(new TableDdlDTO(reference, name, targets, null, null, "APPEND", Boolean.TRUE));
    }

    /**
     * 按给定列定义建表并补齐缺失列，列类型由列定义里的来源类型推断。
     *
     * <p>与 {@link #ensureTable(String, String, Collection)} 的区别只在列类型：
     * 这里把「这一列是什么类型」交给数据源模块按来源类型推断，产出
     * {@code BIGINT}/{@code DECIMAL(20,4)} 这类真实类型，而不是一律大文本。
     * 批量入库的列往往有排序、聚合与数值运算需求，落到 TEXT 上就只能当字符串比对。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @param columns   列定义
     */
    public void ensureTypedTable(String reference, String table, List<DdlColumn> columns) {
        String name = identifier(table);
        schemaService.executeDdl(new TableDdlDTO(reference, name, columns, null, null, "APPEND", Boolean.FALSE));
    }

    /**
     * 把「装不下本批取值」的已有文本列放宽成大文本。
     *
     * <p>为什么建表口径已经改成「文本一律大文本」之后还要再放宽一次：表是上一轮
     * 建好的，列宽在那一刻就钉死在 {@code VARCHAR(255)} 上，而数据源模块的
     * {@code APPEND} 策略只补缺失列、不比对类型差异（2026-11-04 实测：已存在的
     * VARCHAR 列不会被后续更长的值自动加宽），于是老表遇到长文本仍然整批写入失败，
     * 报 {@code Value too long for column "description CHARACTER VARYING(255)"}。</p>
     *
     * <p>判定只看「本批真的写不进去的列」：本批该列最长值超过声明长度才放宽。
     * 用户按业务自己定的短列（例如手机号 {@code VARCHAR(20)}）不会因为一次采集
     * 就被改成大文本 —— 放宽的依据是这一批真的有值超过它。</p>
     *
     * @param reference 数据源引用
     * @param table     表名
     * @param rows      本批待写入行
     */
    public void widenTextColumns(String reference, String table, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, Integer> longest = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getValue() instanceof CharSequence text) {
                    longest.merge(entry.getKey().toLowerCase(Locale.ROOT), text.length(), Math::max);
                }
            }
        }
        if (longest.isEmpty()) {
            return;
        }
        List<SchemaColumnVO> columns = schemaService.listColumns(reference, table);
        List<SchemaColumnVO> narrow = new ArrayList<>();
        for (SchemaColumnVO column : columns) {
            Integer needed = longest.get(String.valueOf(column.name()).toLowerCase(Locale.ROOT));
            if (needed == null || !isBoundedText(column)) {
                continue;
            }
            if (needed > column.size()) {
                narrow.add(column);
                log.warn("[采集写库] {}.{} 声明长度 {} 小于本批最长值 {}，放宽为大文本",
                        table, column.name(), column.size(), needed);
            }
        }
        if (narrow.isEmpty()) {
            return;
        }
        String name = identifier(table);
        try (Connection connection = resolve(reference).getConnection()) {
            for (SchemaColumnVO column : narrow) {
                // 跨方言的 ALTER 语句由数据源模块拼：MySQL 走 MODIFY COLUMN，其余方言依次
                // 尝试 ALTER COLUMN <类型> / TYPE / MODIFY，只有全部失败才抛错。
                JdbcDialectSupport.alterColumn(connection, name, identifier(String.valueOf(column.name())),
                        COLUMN_TYPE, "LONGTEXT", Boolean.TRUE.equals(column.nullable()), null, null);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("放宽 " + table + " 的文本列失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 是否「有长度上限的字符列」。
     *
     * <p>只看字符族：数值列同样带 size（{@code BIGINT} 是 64），把它们的 size 当长度
     * 比会误判。已经是大文本的列 size 是 {@code Integer.MAX_VALUE} 一类的值，
     * 也不再参与放宽。</p>
     *
     * @param column 列元数据
     * @return 需要按取值长度判断是否放宽返回 true
     */
    private static boolean isBoundedText(SchemaColumnVO column) {
        String type = column.typeName() == null ? "" : column.typeName().toUpperCase(Locale.ROOT);
        Integer size = column.size();
        return type.contains("CHAR") && size != null && size > 0 && size < UNBOUNDED_TEXT_SIZE;
    }

    /**
     * 建数据源：应用主库直接取容器里的 DataSource，其余交数据源中心解析后建连接池。
     *
     * @param key 数据源引用
     * @return 数据源
     */
    private DataSource create(String key) {
        if (APP_DATASOURCE.equals(key)) {
            DataSource dataSource = applicationDataSource.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("应用没有可用数据源，SQL 节点只能配置数据源中心里的数据源");
            }
            return dataSource;
        }
        CollectDatasourceRegistry.Target target = registry.resolve(key);
        return new HikariDataSource(registry.config(target, key, 30000L));
    }

    /**
     * 试探一份数据源配置能否连通，不产生缓存连接池。
     *
     * <p>供「保存前先测一下」用，因此入参仍是采集侧的 DTO 对象，
     * 密码按存储值（{@code b64:} 前缀）或明文均可。</p>
     *
     * @param datasource 数据源配置
     * @return 数据库产品名与版本
     * @throws IllegalStateException 连接失败
     */
    public String probe(CollectDatasource datasource) {
        HikariConfig config = config(datasource, "probe", 8000L);
        config.setInitializationFailTimeout(1L);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        try (HikariDataSource dataSource = new HikariDataSource(config);
             Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            return meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
        } catch (SQLException ex) {
            throw new IllegalStateException("连接失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 组装连接池配置。
     *
     * @param entity           数据源配置
     * @param poolName         连接池名
     * @param connectTimeoutMs 取连接超时毫秒
     * @return 连接池配置
     */
    private HikariConfig config(CollectDatasource entity, String poolName, long connectTimeoutMs) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(entity.getCollectDatasourceJdbcUrl());
        config.setUsername(entity.getCollectDatasourceUsername());
        config.setPassword(CollectSecrets.decode(entity.getCollectDatasourcePassword()));
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(connectTimeoutMs);
        config.setIdleTimeout(600000L);
        config.setMaxLifetime(1800000L);
        config.setPoolName("collect-ds-" + poolName);
        String driver = entity.getCollectDatasourceDriverClassName();
        if (driver != null && !driver.isBlank()) {
            config.setDriverClassName(driver.trim());
        }
        return config;
    }

    /**
     * 读取结果集。
     *
     * @param rs 结果集
     * @return 行映射列表
     * @throws SQLException 读取失败
     */
    private List<Map<String, Object>> readRows(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= count; index++) {
                row.put(meta.getColumnLabel(index).toLowerCase(Locale.ROOT), clean(rs.getObject(index)));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 把数据库专有类型转成可入表达式的普通值。
     *
     * @param value 原值
     * @return 转换后的值
     * @throws SQLException 读取大字段失败
     */
    private Object clean(Object value) throws SQLException {
        if (value instanceof Clob clob) {
            return clob.getSubString(1, (int) Math.min(clob.length(), 1_000_000L));
        }
        if (value instanceof java.sql.Time || value instanceof java.sql.Timestamp || value instanceof java.sql.Date) {
            return String.valueOf(value);
        }
        return value;
    }

    /**
     * 绑定前的取值转换，复杂类型序列化为 JSON 文本。
     *
     * @param value 原值
     * @return 可绑定值
     */
    private Object value(Object value) {
        if (value == null || value instanceof Number || value instanceof String || value instanceof Boolean) {
            return value;
        }
        return CollectJson.toJson(value);
    }

    /**
     * 校验并规范标识符。
     *
     * @param name 表名或列名
     * @return 合法名字
     */
    public static String identifier(String name) {
        try {
            return SqlName.checkSimple(name == null ? null : name.trim(), "表名或列名");
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法表名或列名: " + name, ex);
        }
    }

    /**
     * 按目标库方言给标识符加引用符。
     *
     * <p>建表走数据源中心的 DDL 通道（{@link JdbcDialectSupport#quote}），那里一定会
     * 加引用符；DDL 里的 {@code "collect_github_repo"} 是「原样大小写」的标识符，而 DML
     * 里的裸 {@code collect_github_repo} 会被内核折叠——H2 与 Oracle 折成大写、PostgreSQL
     * 折成小写。于是建出来的小写表在 {@code DELETE} / {@code INSERT} 里查不到，报的还是
     * 「表不存在」，极难定位。这里让 DML 与 DDL 用同一套引用规则，两侧天然对齐。</p>
     *
     * <p>调用前必须先经 {@link #identifier(String)} 校验：加引用符只解决大小写，
     * 不提供任何转义，安全性仍由白名单保证。</p>
     *
     * @param connection 数据库连接，用于判定方言
     * @param name       已校验的表名或列名
     * @return 加引用符后的标识符
     */
    private static String quoted(Connection connection, String name) {
        return JdbcDialectSupport.quote(connection, identifier(name));
    }

    /**
     * 批量给标识符加引用符。
     *
     * @param connection 数据库连接，用于判定方言
     * @param names      已校验的列名
     * @return 加引用符后的列名列表
     */
    private static List<String> quotedAll(Connection connection, Collection<String> names) {
        List<String> quoted = new ArrayList<>(names.size());
        for (String name : names) {
            quoted.add(quoted(connection, name));
        }
        return quoted;
    }

    /**
     * 回滚当前事务，失败只记日志。
     *
     * <p>调用点在抛出原始异常之前，回滚本身再失败不能把原始原因覆盖掉。</p>
     *
     * @param connection 连接
     */
    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            log.warn("[collect] 回滚失败: {}", ex.getMessage());
        }
    }

    /**
     * 还原连接的自动提交标记，失败只记日志。
     *
     * <p>连接随后会归还连接池，池本身也会重置该标记；这里显式还原是为了不依赖池实现。</p>
     *
     * @param connection 连接
     * @param autoCommit 原自动提交标记
     */
    private void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ex) {
            log.warn("[collect] 还原自动提交失败: {}", ex.getMessage());
        }
    }

    /**
     * 取已存在的列名集合，表不存在返回空集合。
     *
     * @param dataSource 数据源
     * @param table      表名
     * @return 小写列名集合
     */
    private Set<String> existingColumns(DataSource dataSource, String table) {
        Set<String> columns = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String candidate : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
                try (ResultSet rs = meta.getColumns(null, null, candidate, null)) {
                    while (rs.next()) {
                        columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
            }
            return columns;
        } catch (SQLException ex) {
            log.warn("[collect] 读取表 {} 的列失败: {}", table, ex.getMessage());
            return columns;
        }
    }

    /**
     * 是否只读查询语句（SELECT / WITH / SHOW）。
     *
     * <p>判定口径跳过前导空白与**括号包裹的查询块**：MySQL/MariaDB 里
     * {@code (select …) union all (select …)} 这类语句以左括号开头，只按首词判定会把它
     * 误判成写语句 —— SQL 节点在 {@code plugin.collect.sql-allow-write=false} 下直接拒收，
     * 走到 JDBC 支撑层又抛「查询节点只能执行 SELECT 语句」，两头都堵死合法的只读 UNION。</p>
     *
     * @param sql 原始语句，可为 null
     * @return 是只读查询返回 true
     */
    public static boolean isReadOnlyStatement(String sql) {
        String head = sql == null ? "" : sql.trim().toLowerCase(Locale.ROOT);
        while (head.startsWith("(")) {
            head = head.substring(1).trim();
        }
        return head.startsWith("select") || head.startsWith("with") || head.startsWith("show");
    }

    /**
     * 校验单条语句。
     *
     * @param sql     原始语句
     * @param query   是否查询语句
     * @return 可执行语句
     */
    private String singleStatement(String sql, boolean query) {
        String statement = sql == null ? "" : sql.trim();
        while (statement.endsWith(";")) {
            statement = statement.substring(0, statement.length() - 1).trim();
        }
        if (statement.isEmpty()) {
            throw new IllegalArgumentException("SQL 语句为空");
        }
        if (statement.contains(";")) {
            throw new IllegalArgumentException("一次只能执行一条 SQL 语句");
        }
        if (query != isReadOnlyStatement(statement)) {
            throw new IllegalArgumentException(query ? "查询节点只能执行 SELECT 语句" : "更新节点不能执行 SELECT 语句");
        }
        return statement;
    }

    /**
     * 按主键写入的结果计数。
     *
     * @param updated  命中原键、被 UPDATE 的行数
     * @param inserted 未命中原键、被 INSERT 的行数
     */
    public record UpsertResult(int updated, int inserted) {

        /**
         * 本次写入的总行数。
         *
         * @return 更新行数与新增行数之和
         */
        public int total() {
            return updated + inserted;
        }
    }

    /**
     * 跳过式写入的结果计数。
     *
     * @param skipped  命中原键、被整行跳过的行数
     * @param inserted 未命中原键、被 INSERT 的行数
     */
    public record SkipResult(int skipped, int inserted) {

        /**
         * 本次处理的总行数。
         *
         * @return 跳过行数与新增行数之和
         */
        public int total() {
            return skipped + inserted;
        }
    }

    /**
     * 按业务键写入的原始计数，供两个公开方法各自裁成对外形状。
     *
     * @param updated  命中并 UPDATE 的行数（跳过模式下恒为 0）
     * @param skipped  命中并跳过的行数（更新模式下恒为 0）
     * @param inserted 新增的行数
     */
    private record KeyWriteResult(int updated, int skipped, int inserted) {
    }
}