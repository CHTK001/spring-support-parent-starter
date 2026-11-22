package com.chua.starter.logger.store;

import com.chua.common.support.lang.datasource.series.SeriesEngine;
import com.chua.datasource.support.kv.JdbcKvEngine;
import com.chua.starter.logger.event.LoggerEvent;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * logger_eps 建表脚本与 SeriesEngine 读写回路回归。
 *
 * <p>{@link KvLoggerEventStore} 的每秒事件速率（eps）依赖物理表 {@code logger_eps}
 * （{@code DataSourceSeriesEngine} 的「表名前缀 + 字段名」列规约）。历史上没有任何
 * DDL 创建该表，存量库持续报「JDBC 写入失败 target=logger_eps」，eps 曲线退化为
 * 每次全量扫描事件聚合。本测试验证：</p>
 * <ol>
 *   <li>随包脚本 {@code db/init/V1.0__init_all_logger.sql} 在 MySQL 兼容库上可执行且幂等；</li>
 *   <li>建表后 writePoint / series 读写闭环；</li>
 *   <li> KvLoggerEventStore 落库链路（事件 KV + eps 时序）真实可用。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.51
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoggerEpsDdlTest {

    /**
     * H2 MySQL 兼容模式（与 monitor 侧 H2 脚本同一 DDL 风格：反引号/ENGINE/AUTO_INCREMENT）
     */
    private static final String URL =
            "jdbc:h2:mem:logger_eps_ddl_test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE";

    private static final String DDL_RESOURCE = "/db/init/V1.0__init_all_logger.sql";

    private DataSource dataSource;

    @BeforeAll
    void initDatabase() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(URL);
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        runDdl();
        // 幂等：存量库每次启动都会重放 CREATE TABLE IF NOT EXISTS，不得报错
        runDdl();
    }

    /**
     * 建表后，时序写入与按窗口查询闭环。
     */
    @Test
    void epsWriteAndReadRoundtrip() throws Exception {
        SeriesEngine series = LoggerStores.jdbcSeriesEngine(dataSource);
        long now = System.currentTimeMillis();
        series.writePoint("logger_eps", 1001L, "count", 3, now - 120_000L);
        series.writePoint("logger_eps", 1001L, "count", 5, now);

        // 明细行按规约列落库
        assertEquals(2, countRows(1001L), "logger_eps 应落 2 行明细");

        // 窗口查询返回曲线，降采样值介于两次写入之间
        List<List<Object>> points = series.series("logger_eps", 1001L, "count", 1, "1m");
        assertFalse(points.isEmpty(), "series 查询不应为空");
        for (List<Object> point : points) {
            double value = ((Number) point.get(1)).doubleValue();
            assertTrue(value >= 3.0 && value <= 5.0, "降采样值越界: " + value);
        }

        // monitorId 隔离：其他应用查不到
        assertTrue(series.series("logger_eps", 2002L, "count", 1, "1m").isEmpty(),
                "不同 monitorId 的数据不应串");
    }

    /**
     * 保留期清理：purge 删除过期点、保留未过期点（防时序表无限增长的兜底）。
     */
    @Test
    void purgeRemovesExpiredPoints() throws Exception {
        SeriesEngine series = LoggerStores.jdbcSeriesEngine(dataSource);
        long now = System.currentTimeMillis();
        series.writePoint("logger_eps", 3003L, "count", 1, now - 10 * 3600_000L);
        series.writePoint("logger_eps", 3003L, "count", 2, now);

        int deleted = series.purge("logger_eps", 3003L, now - 3600_000L);
        assertEquals(1, deleted, "应清理 1 条过期点");
        assertEquals(1, countRows(3003L), "清理后应仅剩 1 条未过期点");
    }

    /**
     * KvLoggerEventStore 全链路：事件落 KV、eps 落时序表、统计与查询可用。
     */
    @Test
    void kvStoreAppendQueryAndStats() throws Exception {
        SeriesEngine series = LoggerStores.jdbcSeriesEngine(dataSource);
        KvLoggerEventStore store = new KvLoggerEventStore(new JdbcKvEngine(dataSource), series, 72);

        long now = System.currentTimeMillis();
        store.append(List.of(
                new LoggerEvent().setTs(now).setApp("it-app").setInstance("1@host")
                        .setLevel("INFO").setThread("t").setLogger("L").setMessage("hello"),
                new LoggerEvent().setTs(now + 1).setApp("it-app").setInstance("1@host")
                        .setLevel("ERROR").setThread("t").setLogger("L").setMessage("boom")));

        // 事件落 KV（sys_support_kv 懒建表）
        LoggerPage page = store.query("it-app", null, null, null, null, null, null, null, 10);
        assertEquals(2, page.getItems().size(), "应查回 2 条事件");

        // eps 落时序表：monitorId = |app.hashCode()|，与 KvLoggerEventStore.appId 同口径
        int monitorId = Math.abs("it-app".hashCode());
        assertTrue(countRows(monitorId) >= 1, "append 后 logger_eps 应有速率点（修复前此处 INSERT 失败）");

        // 统计与应用列表可用（eps 曲线来自时序表而非全量扫描）
        Map<String, Object> stats = store.stats("it-app", null);
        assertNotNull(stats);
        assertFalse(stats.isEmpty());
        List<Map<String, Object>> apps = store.apps();
        assertTrue(apps.stream().anyMatch(row -> "it-app".equals(row.get("app"))),
                "应用登记应包含 it-app");
    }

    /**
     * 统计指定应用的 logger_eps 明细行数。
     */
    private int countRows(long monitorId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM logger_eps WHERE logger_eps_monitor_id = ?")) {
            ps.setLong(1, monitorId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * 执行随包建表脚本（与 FlywayLike populator 扫描同一资源路径）。
     */
    private void runDdl() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(DDL_RESOURCE)) {
            assertNotNull(in, "缺少资源 " + DDL_RESOURCE);
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String stmt : sql.split(";")) {
                if (!stmt.isBlank()) {
                    statement.execute(stmt);
                }
            }
        }
    }
}
