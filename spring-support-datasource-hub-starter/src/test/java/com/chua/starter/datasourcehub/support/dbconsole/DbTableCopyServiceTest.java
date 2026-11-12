package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.common.support.lang.datasource.dialect.SqlName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖粘贴的中间表命名与目标表占位键。
 *
 * <p><b>为什么要测</b>：覆盖同名表不再 DROP 原表，改成「写临时表 → 原子换名 → 删备份」，
 * 临时表与备份表的命名一旦不合法（超 64 字符、含非法字符）或两个并发任务撞名，
 * 建表语句会直接失败，用户看到的是「复制失败」而不是原本的覆盖语义。
 * 这里钉死三条：名字始终是合法标识符、长度不超 MySQL 上限、任务之间不撞名。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class DbTableCopyServiceTest {

    @Test
    @DisplayName("临时表名：任务序号进后缀，不同任务不会撞名")
    void scratchNameCarriesTaskSeq() {
        assertEquals("orders_dbmcp_t3", DbTableCopyService.scratchTableName("orders", "db-copy-1794398243843-3", "t"));
        assertEquals("orders_dbmcp_t4", DbTableCopyService.scratchTableName("orders", "db-copy-1794398243843-4", "t"));
        assertEquals("orders_dbmcp_b3", DbTableCopyService.scratchTableName("orders", "db-copy-1794398243843-3", "b"));
        assertEquals("orders_dbmcp_t0", DbTableCopyService.scratchTableName("orders", null, "t"));
    }

    @Test
    @DisplayName("临时表名：64 字符表名截基名保后缀，结果仍是合法标识符且不超长")
    void scratchNameFitsMysqlLimit() {
        String longName = "t".repeat(64);
        String scratch = DbTableCopyService.scratchTableName(longName, "db-copy-1794398243843-3", "t");

        assertTrue(scratch.length() <= 64, "临时表名不能超过 64 字符，实际 " + scratch.length());
        assertTrue(scratch.endsWith("_dbmcp_t3"), "后缀必须完整保留，否则并发任务会撞名");
        assertEquals(scratch, SqlName.checkSimple(scratch, "临时表名"), "生成的临时表名必须能过标识符白名单");
    }

    @Test
    @DisplayName("占位键：库名/表名忽略大小写，空库名不产生歧义")
    void lockKeyNormalises() {
        assertEquals("0||users", DbTableCopyService.lockKey(0L, null, "Users"));
        assertEquals("0||users", DbTableCopyService.lockKey(0L, "", "users"));
        assertEquals(DbTableCopyService.lockKey(1L, "shop", "orders"),
                DbTableCopyService.lockKey(1L, "SHOP", "ORDERS"));
        assertFalse(DbTableCopyService.lockKey(1L, "shop", "orders")
                .equals(DbTableCopyService.lockKey(2L, "shop", "orders")), "不同数据源的同名表必须分开占位");
        assertNotNull(DbTableCopyService.lockKey(1L, "shop", "orders"));
    }
}
