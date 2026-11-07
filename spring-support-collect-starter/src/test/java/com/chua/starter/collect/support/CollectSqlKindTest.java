package com.chua.starter.collect.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SQL 语句只读判定测试。
 *
 * <p>守住两处缺陷的回归：</p>
 * <ol>
 *   <li>MySQL/MariaDB 里 {@code (select …) union all (select …)} 以**左括号**开头，
 *       只按首词判定会把它误判成写语句 —— SQL 节点在
 *       {@code plugin.collect.sql-allow-write=false} 下直接拒收（「写语句已被配置禁用」），
 *       走到 JDBC 支撑层又抛「查询节点只能执行 SELECT 语句」，两头都堵死合法的只读 UNION；</li>
 *   <li>判定口径必须**不放松**对真写语句的拦截，否则就是把安全闸门开大了。</li>
 * </ol>
 *
 * <p>SQL 节点执行器与 JDBC 支撑层共用 {@link CollectJdbcSupport#isReadOnlyStatement}，
 * 所以这一份测试同时是两侧口径的唯一真源校验。</p>
 *
 * @author chua
 * @since 4.0.0.51
 */
class CollectSqlKindTest {

    @Test
    @DisplayName("select / with / show 开头的普通查询是只读")
    void plainQueriesAreReadOnly() {
        assertTrue(CollectJdbcSupport.isReadOnlyStatement("select 1 as id"));
        assertTrue(CollectJdbcSupport.isReadOnlyStatement("  SELECT collect_task_id FROM collect_task  "));
        assertTrue(CollectJdbcSupport.isReadOnlyStatement("WITH t AS (SELECT 1) SELECT * FROM t"));
        assertTrue(CollectJdbcSupport.isReadOnlyStatement("show tables"));
    }

    @Test
    @DisplayName("括号包裹的 UNION 查询块是只读（修复回归）")
    void parenthesizedUnionIsReadOnly() {
        assertTrue(CollectJdbcSupport.isReadOnlyStatement(
                "(select collect_flow_id as id from collect_flow limit 2) union all (select 888888 as id)"));
        assertTrue(CollectJdbcSupport.isReadOnlyStatement(
                "( SELECT a FROM t ) UNION ( SELECT b FROM u )"));
        assertTrue(CollectJdbcSupport.isReadOnlyStatement("((select 1))"));
    }

    @Test
    @DisplayName("真写语句必须被判成非只读")
    void writeStatementsAreNotReadOnly() {
        assertFalse(CollectJdbcSupport.isReadOnlyStatement("insert into t values (1)"));
        assertFalse(CollectJdbcSupport.isReadOnlyStatement("update t set a = 1"));
        assertFalse(CollectJdbcSupport.isReadOnlyStatement("delete from t"));
        assertFalse(CollectJdbcSupport.isReadOnlyStatement("drop table t"));
        assertFalse(CollectJdbcSupport.isReadOnlyStatement("create table t (a int)"));
        assertFalse(CollectJdbcSupport.isReadOnlyStatement("truncate table t"));
        assertFalse(CollectJdbcSupport.isReadOnlyStatement("alter table t add b int"));
    }

    @Test
    @DisplayName("空与 null 不是只读查询")
    void blankIsNotReadOnly() {
        assertFalse(CollectJdbcSupport.isReadOnlyStatement(null));
        assertFalse(CollectJdbcSupport.isReadOnlyStatement(""));
        assertFalse(CollectJdbcSupport.isReadOnlyStatement("   "));
    }

    @Test
    @DisplayName("仅前导标识符为写关键字的语句仍被拦下，不因跳过括号而放松")
    void parenSkippingDoesNotOpenTheGate() {
        // 括号后紧跟写关键字：跳过括号后首词是 insert，仍判非只读
        assertFalse(CollectJdbcSupport.isReadOnlyStatement("(insert into t values (1))"));
    }
}
