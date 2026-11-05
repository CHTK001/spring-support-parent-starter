// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.dbconsole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 多方言列类型白名单的验收测试。
 *
 * <p>设计表按方言出类型表之后，后端 {@link DbColumnDef#SUPPORTED_TYPES} 是唯一的
 * 权威白名单：前端下拉里出现的每一项，都要能在这里构造成功，否则就是
 * 「下拉能选、提交被后端拒」。本测试把各方言的新增类型逐一钉死。</p>
 *
 * <p><b>为什么不用真实数据库</b>：校验发生在构造期（拿不到连接），
 * 白名单本身就是全部行为，桩不需要数据库。</p>
 *
 * @author CH
 * @since 4.0.0.46
 */
class DbColumnDefDialectTypeTest {

    /**
     * 构造一个列定义。
     *
     * @param type  类型
     * @param size  长度（null=不给）
     * @param scale 小数位（null=不给）
     * @return 列定义
     */
    private DbColumnDef def(String type, Integer size, Integer scale) {
        return new DbColumnDef(
                "c", type, size, scale,
                false, false, null, null, true, null, null, false, null, false, null);
    }

    /**
     * 各方言代表类型都要能构造成功。
     */
    @Test
    @DisplayName("各方言代表类型可构造")
    void dialectTypesAccepted() {
        // Oracle
        assertTypeOk("VARCHAR2", 64, null);
        assertTypeOk("NVARCHAR2", 64, null);
        assertTypeOk("NCHAR", 10, null);
        assertTypeOk("NUMBER", 10, 2);
        assertTypeOk("NUMBER", null, null); // Oracle 允许裸 NUMBER
        assertTypeOk("RAW", 16, null);
        assertTypeOk("CLOB", null, null);
        assertTypeOk("NCLOB", null, null);
        assertTypeOk("BINARY_FLOAT", null, null);
        assertTypeOk("BINARY_DOUBLE", null, null);
        // PostgreSQL
        assertTypeOk("BYTEA", null, null);
        assertTypeOk("TIMESTAMPTZ", null, null);
        assertTypeOk("INTERVAL", null, null);
        assertTypeOk("JSONB", null, null);
        // SQL Server
        assertTypeOk("NVARCHAR", 128, null);
        assertTypeOk("DATETIME2", null, null);
        assertTypeOk("DATETIMEOFFSET", null, null);
        assertTypeOk("SMALLDATETIME", null, null);
        assertTypeOk("UNIQUEIDENTIFIER", null, null);
        assertTypeOk("MONEY", null, null);
        assertTypeOk("SMALLMONEY", null, null);
        assertTypeOk("NTEXT", null, null);
        assertTypeOk("IMAGE", null, null);
        assertTypeOk("XML", null, null);
        // DuckDB
        assertTypeOk("HUGEINT", null, null);
        assertTypeOk("UTINYINT", null, null);
        assertTypeOk("USMALLINT", null, null);
        assertTypeOk("UINTEGER", null, null);
        assertTypeOk("UBIGINT", null, null);
    }

    /**
     * 构造成功即打印 DDL 片段，顺带验证 toTypeOnlyFragment 的输形态。
     */
    private void assertTypeOk(String type, Integer size, Integer scale) {
        DbColumnDef d = def(type, size, scale);
        assertTrue(d.toTypeOnlyFragment().toUpperCase().startsWith(type), type + " 拼出的 DDL 片段异常: "
                + d.toTypeOnlyFragment());
    }

    /**
     * VECTOR（MySQL 9 / pgvector）：维度必填、有上限。
     */
    @Test
    @DisplayName("VECTOR 维度必填且上限 16383")
    void vectorRules() {
        // 维度必填
        IllegalArgumentException noSize = assertThrows(IllegalArgumentException.class, () -> def("VECTOR", null, null));
        assertTrue(noSize.getMessage().contains("VECTOR"), "报错应点名类型: " + noSize.getMessage());
        // 正常维度 → VECTOR(1536)
        assertEquals("VECTOR(1536)", def("VECTOR", 1536, null).toTypeOnlyFragment());
        assertEquals("VECTOR(16383)", def("VECTOR", 16383, null).toTypeOnlyFragment());
        // 超上限（16384 恰好是 MySQL 9 / pgvector 都不收的值）
        IllegalArgumentException tooBig = assertThrows(IllegalArgumentException.class, () -> def("VECTOR", 16384, null));
        assertTrue(tooBig.getMessage().contains("16383"), "报错应说明上限: " + tooBig.getMessage());
    }

    /**
     * 未知类型与大小写/空格归一化。
     */
    @Test
    @DisplayName("未知类型拒绝、大小写空格归一")
    void unknownRejectedAndNormalized() {
        // 未知类型
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> def("VARCHAR_MAGIC", 10, null));
        assertTrue(unknown.getMessage().contains("不支持的列类型"), unknown.getMessage());
        // 大小写与空格归一（ColumnDef 构造器内 normalizeType）
        assertEquals("VARCHAR(64)", def(" varchar ", 64, null).toTypeOnlyFragment());
        assertEquals("VECTOR(8)", def(" vector ", 8, null).toTypeOnlyFragment());
    }

    /**
     * 需要长度的新类型：缺长度直接被拒，装不出「下拉能选、后端拒」。
     */
    @Test
    @DisplayName("VARCHAR2/NVARCHAR2/NCHAR/RAW 缺长度被拒")
    void sizeRequiredNewTypes() {
        for (String type : List.of("VARCHAR2", "NVARCHAR2", "NCHAR", "RAW")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> def(type, null, null));
            assertTrue(e.getMessage().contains("必须指定长度"), type + " 应要求长度: " + e.getMessage());
        }
    }}