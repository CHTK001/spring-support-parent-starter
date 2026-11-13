package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DataSourceTestVO;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.model.dto.DataSourceDTO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖粘贴 / 并发互斥 / 取消回滚的真实库验收。
 *
 * <p><b>为什么要连真库</b>：这三条都只在真实 MySQL 上成立 —— {@code RENAME TABLE}
 * 的原子换名、InnoDB 的事务回滚、临时表残留，用 mock 连接测不出来（mock 会同时
 * 「验证」错误实现）。本用例只把数据源凭据层换成直连，SQL 全部打到 127.0.0.1 的
 * MySQL。</p>
 *
 * <p><b>默认不跑</b>：加 {@code -Ddbcopy.it=true} 才执行，避免没有库的环境上构建失败。
 * 库连接串取本机验收环境口径：{@code root} / 空口令 / 3306。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@EnabledIfSystemProperty(named = "dbcopy.it", matches = "true")
class DbTableCopyServiceMysqlTest {

    /** 验收用源库（每次用例前重建） */
    private static final String SRC_DB = "dbmcp_it_src";

    /** 验收用目标库（每次用例前重建） */
    private static final String DST_DB = "dbmcp_it_dst";

    /** 服务端连接串（不带库，用例自己建库） */
    private static final String SERVER_URL = "jdbc:mysql://127.0.0.1:3306/?useSSL=false"
            + "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8";

    /** 直连账号 */
    private static final String USER = "root";

    /** 直连口令 */
    private static final String PASSWORD = "";

    /** 大表行数：够让取消落在数据段中段，又不至于让用例跑太久 */
    private static final int BIG_ROWS = 500_000;

    private DbCopyTaskRegistry registry;

    private DbTableCopyService service;

    /**
     * 确认 MySQL 可达；不可达时跳过而不是失败。
     */
    @BeforeEach
    void requireMysql() {
        Connection probe = null;
        try {
            probe = DriverManager.getConnection(SERVER_URL, USER, PASSWORD);
        } catch (SQLException e) {
            Assumptions.assumeTrue(false, "本机 MySQL 不可达，跳过真实库验收: " + e.getMessage());
        } finally {
            closeQuietly(probe);
        }
        registry = new DbCopyTaskRegistry(new ObjectMapper());
        service = new DbTableCopyService(new DirectDatasourceService(), registry);
    }

    /**
     * 关掉任务线程池。
     */
    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    /**
     * 收尾：验证库用完即删，不在本机 MySQL 上留验收残留。
     */
    @AfterAll
    static void dropSchemas() {
        for (String database : new String[]{SRC_DB, DST_DB}) {
            try (Connection conn = DriverManager.getConnection(SERVER_URL, USER, PASSWORD);
                 Statement st = conn.createStatement()) {
                st.execute("DROP DATABASE IF EXISTS " + database);
            } catch (SQLException ignored) {
                // 收尾失败不影响用例结论，下轮用例开头还会重建
            }
        }
    }

    @Test
    @DisplayName("覆盖粘贴：目标表全程可用，结束后是新数据，中间表全部清掉")
    void overwriteSwapsAtomically() throws Exception {
        rebuildSchemas();
        exec("CREATE TABLE " + SRC_DB + ".t_small (id INT NOT NULL PRIMARY KEY, note VARCHAR(32)) ENGINE=InnoDB");
        exec("INSERT INTO " + SRC_DB + ".t_small VALUES (1,'新甲'),(2,'新乙'),(3,'新丙')");
        exec("CREATE TABLE " + DST_DB + ".t_small (id INT NOT NULL PRIMARY KEY, note VARCHAR(32)) ENGINE=InnoDB");
        exec("INSERT INTO " + DST_DB + ".t_small VALUES (9,'旧数据')");

        String taskId = service.start(new DbTableCopyRequest(
                1L, SRC_DB, "t_small", 1L, DST_DB, "t_small", true, true));
        Map<String, Object> done = awaitFinished(taskId, 60_000L);

        assertEquals("SUCCESS", done.get("status"), "覆盖复制应成功: " + done.get("message"));
        assertEquals(3, count("SELECT COUNT(*) FROM " + DST_DB + ".t_small"));
        assertEquals(1, count("SELECT COUNT(*) FROM " + DST_DB + ".t_small WHERE note='新甲'"));
        assertEquals(0, count("SELECT COUNT(*) FROM " + DST_DB + ".t_small WHERE note='旧数据'"),
                "旧数据必须已被新表替换");
        assertEquals(0, scratchTables(DST_DB), "中间表必须全部清理，实际残留: " + scratchTables(DST_DB));
    }

    @Test
    @DisplayName("同名且不覆盖：失败且目标表原数据不动")
    void existingTableUntouchedWithoutOverwrite() throws Exception {
        rebuildSchemas();
        exec("CREATE TABLE " + SRC_DB + ".t_small (id INT NOT NULL PRIMARY KEY, note VARCHAR(32)) ENGINE=InnoDB");
        exec("INSERT INTO " + SRC_DB + ".t_small VALUES (1,'新甲')");
        exec("CREATE TABLE " + DST_DB + ".t_small (id INT NOT NULL PRIMARY KEY, note VARCHAR(32)) ENGINE=InnoDB");
        exec("INSERT INTO " + DST_DB + ".t_small VALUES (9,'旧数据')");

        String taskId = service.start(new DbTableCopyRequest(
                1L, SRC_DB, "t_small", 1L, DST_DB, "t_small", true, false));
        Map<String, Object> done = awaitFinished(taskId, 60_000L);

        assertEquals("FAILED", done.get("status"));
        assertTrue(String.valueOf(done.get("message")).contains("已存在"), "失败原因应说明目标表已存在");
        assertEquals(1, count("SELECT COUNT(*) FROM " + DST_DB + ".t_small WHERE note='旧数据'"));
        assertEquals(0, scratchTables(DST_DB));
    }

    @Test
    @DisplayName("并发互斥：同一目标表的第二个任务在建任务时就被挡下")
    void concurrentTaskOnSameTargetRejected() throws Exception {
        rebuildSchemas();
        createBigSource();

        String taskId = service.start(new DbTableCopyRequest(
                1L, SRC_DB, "t_big", 1L, DST_DB, "t_big", true, true));
        IllegalStateException rejected = assertThrows(IllegalStateException.class, () ->
                        service.start(new DbTableCopyRequest(
                                1L, SRC_DB, "t_big", 1L, DST_DB, "t_big", true, true)),
                "同一目标表的第二个任务必须被拒绝");
        assertTrue(rejected.getMessage().contains("已有复制任务在执行"), "报错应说明已有任务在执行");

        registry.cancel(taskId);
        awaitFinished(taskId, 120_000L);
    }

    @Test
    @DisplayName("覆盖中途取消：目标表保持原数据，临时表已清理")
    void cancelKeepsOriginalTable() throws Exception {
        rebuildSchemas();
        createBigSource();
        exec("CREATE TABLE " + DST_DB + ".t_big (id INT NOT NULL PRIMARY KEY, k INT, note VARCHAR(32)) ENGINE=InnoDB");
        exec("INSERT INTO " + DST_DB + ".t_big VALUES (1,1,'原表甲'),(2,2,'原表乙'),(3,3,'原表丙')");

        String taskId = service.start(new DbTableCopyRequest(
                1L, SRC_DB, "t_big", 1L, DST_DB, "t_big", true, true));
        waitUntilDataPhase(taskId, 120_000L);
        assertTrue(registry.cancel(taskId), "取消请求应被受理");
        Map<String, Object> done = awaitFinished(taskId, 120_000L);

        assertEquals("CANCELLED", done.get("status"), "取消后终态应是 CANCELLED: " + done.get("message"));
        assertEquals(3, count("SELECT COUNT(*) FROM " + DST_DB + ".t_big"), "原表数据必须原样保留");
        assertEquals(1, count("SELECT COUNT(*) FROM " + DST_DB + ".t_big WHERE note='原表甲'"));
        assertEquals(0, scratchTables(DST_DB), "临时表必须被清理，实际残留: " + scratchTables(DST_DB));
    }

    @Test
    @DisplayName("统计行数阶段取消：秒级转 CANCELLED，而不是等统计跑完")
    void cancelInterruptsCountQuery() throws Exception {
        rebuildSchemas();
        createBigSource();
        exec("CREATE TABLE " + DST_DB + ".t_big (id INT NOT NULL PRIMARY KEY, k INT, note VARCHAR(32)) ENGINE=InnoDB");
        exec("INSERT INTO " + DST_DB + ".t_big VALUES (1,1,'原表甲')");

        String taskId = service.start(new DbTableCopyRequest(
                1L, SRC_DB, "t_big", 1L, DST_DB, "t_big", true, true));
        awaitRunning(taskId, 10_000L);
        // 统计阶段紧跟建表：这里尽快取消，命中「统计源表行数」那一刻
        assertTrue(registry.cancel(taskId), "取消请求应被受理");
        long startedAt = System.nanoTime();
        Map<String, Object> done = awaitFinished(taskId, 120_000L);
        long costMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals("CANCELLED", done.get("status"));
        assertTrue(costMs < 60_000L, "取消应尽早生效，实际耗时 " + costMs + "ms");
        assertEquals(1, count("SELECT COUNT(*) FROM " + DST_DB + ".t_big"), "原表数据必须原样保留");
        assertEquals(0, scratchTables(DST_DB));
    }

    /* ==================== 造数与断言辅助 ==================== */

    /**
     * 重建源库与目标库（用例之间互不影响）。
     *
     * @throws SQLException 重建失败
     */
    private void rebuildSchemas() throws Exception {
        exec("DROP DATABASE IF EXISTS " + SRC_DB);
        exec("DROP DATABASE IF EXISTS " + DST_DB);
        exec("CREATE DATABASE " + SRC_DB + " DEFAULT CHARACTER SET utf8mb4");
        exec("CREATE DATABASE " + DST_DB + " DEFAULT CHARACTER SET utf8mb4");
    }

    /**
     * 造 {@link #BIG_ROWS} 行的源表（十字连接生成，比逐行插入快两个数量级）。
     *
     * @throws SQLException 造数失败
     */
    private void createBigSource() throws Exception {
        exec("CREATE TABLE " + SRC_DB + ".t_big (id INT NOT NULL PRIMARY KEY, k INT, note VARCHAR(32)) ENGINE=InnoDB");
        exec("INSERT INTO " + SRC_DB + ".t_big (id, k, note) "
                + "SELECT n + 1, (n + 1) % 97, CONCAT('row-', n + 1) FROM ("
                + "SELECT a.d + b.d * 10 + c.d * 100 + e.d * 1000 + f.d * 10000 + g.d * 100000 AS n FROM "
                + digits("a") + ", " + digits("b") + ", " + digits("c") + ", "
                + digits("e") + ", " + digits("f") + ", " + digits("g")
                + ") t WHERE t.n < " + BIG_ROWS);
        assertEquals(BIG_ROWS, count("SELECT COUNT(*) FROM " + SRC_DB + ".t_big"), "造数行数不对");
    }

    /**
     * 0..9 的十行数字子查询。
     *
     * @param alias 别名
     * @return 子查询
     */
    private static String digits(String alias) {
        StringBuilder sb = new StringBuilder("(SELECT 0 AS d");
        for (int i = 1; i <= 9; i++) {
            sb.append(" UNION ALL SELECT ").append(i);
        }
        return sb.append(") ").append(alias).toString();
    }

    /**
     * 在服务端连接上执行一条语句。
     *
     * @param sql 语句
     * @throws SQLException 执行失败
     */
    private void exec(String sql) throws Exception {
        try (Connection conn = DriverManager.getConnection(SERVER_URL, USER, PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * 查询一个标量。
     *
     * @param sql 查询
     * @return 第一行第一列
     * @throws SQLException 查询失败
     */
    private long count(String sql) throws Exception {
        try (Connection conn = DriverManager.getConnection(SERVER_URL, USER, PASSWORD);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /**
     * 目标库里残留的复制中间表数量。
     *
     * @param database 库名
     * @return 中间表数量
     * @throws SQLException 查询失败
     */
    private long scratchTables(String database) throws Exception {
        try (Connection conn = DriverManager.getConnection(SERVER_URL, USER, PASSWORD);
             java.sql.PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.TABLES "
                             + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME LIKE '%\\_dbmcp\\_%'")) {
            ps.setString(1, database);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * 等任务进入 RUNNING（且拿到百分比）。
     *
     * @param taskId  任务 id
     * @param timeout 超时毫秒
     * @throws Exception 超时
     */
    private void awaitRunning(String taskId, long timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> snap = snapshot(taskId);
            if (snap != null && "RUNNING".equals(snap.get("status"))) {
                return;
            }
            // [P3C A20 豁免]
            Thread.sleep(50L);
        }
        throw new IllegalStateException("任务未进入 RUNNING: " + taskId);
    }

    /**
     * 等任务进入数据段（百分比越过建表阶段）。
     *
     * @param taskId  任务 id
     * @param timeout 超时毫秒
     * @throws Exception 超时
     */
    private void waitUntilDataPhase(String taskId, long timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> snap = snapshot(taskId);
            if (snap != null) {
                Object pct = snap.get("percentage");
                // [P3C A20 豁免]
                if (pct instanceof Number && ((Number) pct).intValue() >= 30) {
                    return;
                }
                if (Boolean.TRUE.equals(snap.get("finished"))) {
                    throw new IllegalStateException("任务在大表复制前就结束了: " + snap);
                }
            }
            // [P3C A20 豁免]
            Thread.sleep(20L);
        }
        throw new IllegalStateException("任务未进入数据段: " + taskId);
    }

    /**
     * 等任务结束。
     *
     * @param taskId  任务 id
     * @param timeout 超时毫秒
     * @return 终态快照
     * @throws Exception 超时
     */
    private Map<String, Object> awaitFinished(String taskId, long timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> snap = snapshot(taskId);
            if (snap != null && Boolean.TRUE.equals(snap.get("finished"))) {
                return snap;
            }
            // [P3C A20 豁免]
            Thread.sleep(50L);
        }
        throw new IllegalStateException("任务未在 " + timeout + "ms 内结束: " + taskId);
    }

    /**
     * 取某个任务的快照。
     *
     * @param taskId 任务 id
     * @return 快照，未找到时 null
     */
    private Map<String, Object> snapshot(String taskId) {
        for (Map<String, Object> snap : registry.snapshots()) {
            if (taskId.equals(snap.get("taskId"))) {
                return snap;
            }
        }
        return null;
    }

    /**
     * 关连接（不抛异常）。
     *
     * @param conn 连接
     */
    private static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // 探测连接关闭失败不影响用例结论
            }
        }
    }

    /**
     * 只实现 {@link #newConnection(Long)} 的数据源门面：其余方法都说明不需要。
     *
     * <p>复制服务只用到「按 id 拿一条新连接」，把凭据层换成本机直连，
     * 数据面（DDL / DML / RENAME）依然全打真库。</p>
     */
    private static final class DirectDatasourceService implements DatasourceService {

        @Override
        public Connection newConnection(Long datasourceId) throws SQLException {
            return DriverManager.getConnection(SERVER_URL, USER, PASSWORD);
        }

        @Override
        public Connection connection(Long datasourceId) {
            throw new UnsupportedOperationException("真实库验收只用 newConnection");
        }

        @Override
        public List<String> supportedTypes() {
            throw new UnsupportedOperationException("真实库验收不读数据源台账");
        }

        @Override
        public ReturnResult<List<DataSourceVO>> list() {
            throw new UnsupportedOperationException("真实库验收不读数据源台账");
        }

        @Override
        public ReturnResult<DataSourceVO> get(Long id) {
            throw new UnsupportedOperationException("真实库验收不读数据源台账");
        }

        @Override
        public ReturnResult<DataSourceVO> create(DataSourceDTO data) {
            throw new UnsupportedOperationException("真实库验收不写数据源台账");
        }

        @Override
        public ReturnResult<DataSourceVO> update(Long id, DataSourceDTO data) {
            throw new UnsupportedOperationException("真实库验收不写数据源台账");
        }

        @Override
        public ReturnResult<DataSourceVO> merge(String name, List<Long> memberIds) {
            throw new UnsupportedOperationException("真实库验收不写数据源台账");
        }

        @Override
        public ReturnResult<Boolean> delete(Long id) {
            throw new UnsupportedOperationException("真实库验收不写数据源台账");
        }

        @Override
        public ReturnResult<DataSourceVO> toggleTop(Long id, boolean top) {
            throw new UnsupportedOperationException("真实库验收不写数据源台账");
        }

        @Override
        public ReturnResult<DataSourceTestVO> test(Long id) {
            throw new UnsupportedOperationException("真实库验收不测连通性");
        }

        @Override
        public ReturnResult<DataSourceTestVO> probeOnly(Long id) {
            throw new UnsupportedOperationException("真实库验收不测连通性");
        }

        @Override
        public DatasourceJdbcInfo jdbcInfo(Long datasourceId) {
            throw new UnsupportedOperationException("真实库验收只用 newConnection");
        }
    }
}
