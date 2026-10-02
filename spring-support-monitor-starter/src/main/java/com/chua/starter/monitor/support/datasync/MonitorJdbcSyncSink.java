package com.chua.starter.monitor.support.datasync;

import com.chua.datasync.agent.support.DataSyncAgentSink;
import com.chua.datasync.agent.support.model.Direction;
import com.chua.datasync.agent.support.model.Directional;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 监控侧的数据同步 Sink：按行批量写入目标库，并给出**可信的写入行数与错误**。
 *
 * <p>本类实现 datasync 引擎的 {@link DataSyncAgentSink} / {@link Directional} 契约，
 * 因此既能被引擎的调度管线驱动，也能被控制台同步调用，两条路径的写入语义完全一致。</p>
 *
 * <p><b>为什么不用引擎的 {@code JdbcDataSyncAgentSink}</b>：它的 {@code processBatch}
 * 把 {@link SQLException} 捕获后只记日志并回滚，既不把异常抛出、也不暴露写入行数
 * （只有一行 {@code log.info} 写完成多少条）。控制台必须把「读了多少、真正落库多少、
 * 失败原因是什么」如实回给用户，用它会得到「永远成功、数字为 0」的假象，因此这里自己
 * 实现写入，但复用引擎的源读取、字段映射转换与 Sink 契约。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class MonitorJdbcSyncSink implements DataSyncAgentSink, Directional {

    /**
     * 引擎异步调用时的等待上限（分钟）
     */
    private static final long BLOCK_TIMEOUT_MINUTES = 30L;

    /**
     * Sink 标识
     */
    private final String sinkId;

    /**
     * 目标库连接信息
     */
    private final DatasourceJdbcInfo target;

    /**
     * 目标写入语句（INSERT / REPLACE / UPSERT），占位符顺序与 {@link #rowKeys} 一致
     */
    private final String writeSql;

    /**
     * 行内取值键，与写入语句的占位符一一对应
     */
    private final List<String> rowKeys;

    /**
     * 批量提交条数
     */
    private final int batchSize;

    /**
     * 已引用的目标表名（仅 OVERWRITE 模式用于先清空）
     */
    private final String quotedTable;

    /**
     * 是否在写入前清空目标表
     */
    private final boolean overwriteFirst;

    /**
     * 构造监控侧 Sink。
     *
     * @param sinkId          Sink 标识
     * @param target          目标库连接信息
     * @param writeSql        目标写入语句
     * @param rowKeys         行内取值键，与写入语句占位符一一对应
     * @param batchSize       批量提交条数，小于 1 时按 1 处理
     * @param quotedTable     已引用的目标表名
     * @param overwriteFirst  是否先清空目标表
     */
    public MonitorJdbcSyncSink(String sinkId, DatasourceJdbcInfo target, String writeSql,
                               List<String> rowKeys, int batchSize, String quotedTable, boolean overwriteFirst) {
        this.sinkId = sinkId;
        this.target = target;
        this.writeSql = writeSql;
        this.rowKeys = List.copyOf(rowKeys);
        this.batchSize = Math.max(1, batchSize);
        this.quotedTable = quotedTable;
        this.overwriteFirst = overwriteFirst;
    }

    @Override
    public String sinkId() {
        return sinkId;
    }

    @Override
    public Direction direction() {
        return Direction.OUTPUT;
    }

    /**
     * 引擎契约入口：阻塞收集后同步写入。
     *
     * <p>引擎的调度线程调用本方法；这里用 {@code block} 把异步流收敛成一次同步写入，
     * 保证「方法返回时数据已落库」，便于引擎侧后续判断与复用。异常只记日志——
     * 引擎契约不允许向外抛，控制台路径请用 {@link #runOnce(List)}。</p>
     *
     * @param data 待写入的数据流
     */
    @Override
    public void write(Flux<Map<String, Object>> data) {
        try {
            List<Map<String, Object>> rows = data.collectList().block(Duration.ofMinutes(BLOCK_TIMEOUT_MINUTES));
            long written = runOnce(rows == null ? List.of() : rows);
            log.info("[sync-sink] 写入完成 sinkId={}, rows={}", sinkId, written);
        } catch (Exception e) {
            log.error("[sync-sink] 写入失败 sinkId={}, table={}", sinkId, quotedTable, e);
        }
    }

    /**
     * 同步执行一次写入。
     *
     * <p>写入在单个事务内按 {@link #batchSize} 分批提交：某一批失败即抛异常，
     * 已提交的批次不回滚（与引擎 sink 的「逐批提交」语义一致），调用方据异常报告失败。</p>
     *
     * @param rows 待写入的行（键为 {@link #rowKeys} 中的目标列名）
     * @return JDBC 实际确认写入的行数
     * @throws SQLException 建连或写入失败
     */
    public long runOnce(List<Map<String, Object>> rows) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", target.username() == null ? "" : target.username());
        props.setProperty("password", target.password() == null ? "" : target.password());
        long written = 0L;
        try (Connection conn = DriverManager.getConnection(target.url(), props)) {
            conn.setAutoCommit(false);
            if (overwriteFirst) {
                try (Statement statement = conn.createStatement()) {
                    statement.executeUpdate("DELETE FROM " + quotedTable);
                }
                conn.commit();
            }
            try (PreparedStatement ps = conn.prepareStatement(writeSql)) {
                int pending = 0;
                for (Map<String, Object> row : rows) {
                    for (int i = 0; i < rowKeys.size(); i++) {
                        ps.setObject(i + 1, row.get(rowKeys.get(i)));
                    }
                    ps.addBatch();
                    pending++;
                    if (pending >= batchSize) {
                        written += executeBatch(ps);
                        conn.commit();
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    written += executeBatch(ps);
                    conn.commit();
                }
            }
        }
        return written;
    }

    /**
     * 执行一次批量提交并返回成功条数。
     *
     * <p>驱动在部分失败时会返回含 {@code Statement.EXECUTE_FAILED} 的数组；这里按「非失败即成功」
     * 计数，并在全部失败时抛出，避免把 0 行写入当成成功。</p>
     *
     * @param ps 预编译语句
     * @return 本批成功条数
     * @throws SQLException 批量提交失败
     */
    private long executeBatch(PreparedStatement ps) throws SQLException {
        int[] results = ps.executeBatch();
        long ok = 0L;
        boolean anyFailure = false;
        for (int result : results) {
            if (result == Statement.EXECUTE_FAILED) {
                anyFailure = true;
            } else {
                ok++;
            }
        }
        if (anyFailure && ok == 0L) {
            throw new SQLException("批量写入全部失败: " + writeSql);
        }
        return ok;
    }

    @Override
    public void close() {
        // 连接由 DriverManager 按次获取并在 try-with-resources 中关闭，本 Sink 不持有连接池，无需释放资源
    }
}