package com.chua.starter.datasourcehub.support.dbconsole;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 建库流程验证：专用端点与通用 SQL 执行通道两条路的语义。
 *
 * <p><b>为什么要测</b>：前端「新建数据库」有两个落点 —— 专用端点
 * {@code POST /ms/monitor/db/database}（内部走 {@link DbDialectSupport#createDatabase}）
 * 与通用查询面板（{@code POST /ms/monitor/db/execute} 内部走
 * {@link SqlScriptSplitter#isQuery} 分流）。两者对建库语句的判定不一致：
 * 专用端点按内核选 {@code CREATE DATABASE}/{@code CREATE SCHEMA} 候选依次尝试；
 * 通用通道若把 {@code CREATE ...} 误判成查询（走 {@code executeQuery}），
 * 建库会因「无结果集」直接炸。此处用 H2 内嵌库同时钉死这两条路。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class DbCreateDatabaseFlowTest {

    /** H2 内存库连接（非 MySQL 系，验证 CREATE SCHEMA 分支） */
    private static Connection conn;

    @BeforeAll
    static void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:h2:mem:dbcreate;DB_CLOSE_DELAY=-1;MODE=LEGACY", "sa", "");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    @DisplayName("通用 SQL 通道：CREATE DATABASE/SCHEMA 被判定为 DDL（走 executeUpdate 而非 executeQuery）")
    void ddlDetectedByGenericChannel() {
        assertFalse(SqlScriptSplitter.isQuery("CREATE DATABASE telemetry"),
                "CREATE DATABASE 若被判成查询会被 executeQuery 误杀");
        assertFalse(SqlScriptSplitter.isQuery("CREATE SCHEMA telemetry"), "CREATE SCHEMA 同理");
        assertFalse(SqlScriptSplitter.isQuery("DROP DATABASE telemetry"), "DROP 同理");
        assertFalse(SqlScriptSplitter.isQuery("ALTER DATABASE telemetry"), "ALTER 同理");
        // 对照：真正的查询语句必须走 executeQuery
        assertTrue(SqlScriptSplitter.isQuery("SELECT 1"));
        assertTrue(SqlScriptSplitter.isQuery("SHOW DATABASES"));
    }

    @Test
    @DisplayName("通用 SQL 通道：真实执行 CREATE SCHEMA 建库成功")
    void genericChannelCreatesDatabase() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 模拟通用通道 runOneStatement：非查询走 executeUpdate
            int affected = stmt.executeUpdate("CREATE SCHEMA generic_db");
            assertEquals(0, affected, "DDL 的 executeUpdate 正常返回 0");
        }
        assertTrue(schemaExists("generic_db"), "建出的库应能查到");
    }

    @Test
    @DisplayName("专用建库端点：DbDialectSupport.createDatabase 在非 MySQL 内核上真实建库")
    void dialectCreatesDatabase() throws SQLException {
        DbDialectSupport.createDatabase(conn, "dialect_db", null, null);
        assertTrue(schemaExists("dialect_db"), "DbDialectSupport 应建出 dialect_db");
    }

    /**
     * 断言 H2 中存在指定 schema（数据库名由 product 语义决定）。
     *
     * @param schema 目标 schema 名
     * @return 是否存在
     */
    private static boolean schemaExists(String schema) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getSchemas()) {
            while (rs.next()) {
                if (schema.equalsIgnoreCase(rs.getString("TABLE_SCHEM"))) {
                    return true;
                }
            }
        }
        return false;
    }
}