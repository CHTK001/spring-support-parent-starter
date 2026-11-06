// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.chua.starter.datasourcehub.support.config.DatasourceHubProperties;
import com.chua.starter.datasourcehub.support.model.dto.DataSourceDTO;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 数据源校验（{@link DatasourceServiceImpl#validate(DataSourceDTO)}）的边界行为测试。
 *
 * <p>覆盖本次验收暴露的真实缺陷：DuckDB 是单文件库，前端表单用 {@code port=0}
 * 占位（该类型本就没有主机/端口），旧校验把 {@code 0} 一并按「端口非法」拒绝，
 * 导致「上传库文件 → 保存」在 UI 上建不出来。修复口径是文件库类型跳过端口范围校验。
 * 用反射调用 private {@code validate}，不连数据库、不 mock，只钉校验分支本身。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class DatasourceCreateValidateTest {

    /**
     * 反射调用 {@code DatasourceServiceImpl.validate(DataSourceDTO)}。
     *
     * @param data 数据源配置
     * @return 校验错误提示，通过时返回 null
     */
    private static String invokeValidate(DataSourceDTO data) throws Exception {
        Method m = DatasourceServiceImpl.class.getDeclaredMethod("validate", DataSourceDTO.class);
        m.setAccessible(true);
        // 只验 validate 的纯逻辑：给一个没有 Spring 依赖也能 new 出来的实例即可
        return (String) m.invoke(new InstanceForValidate(), data);
    }

    /**
     * 仅用于反射调用 private 方法的最小实例：注入真实 {@link DatasourceHubProperties}
     * 以满足文件库路径白名单分支的读取，其余依赖不参与本测试。
     *
     * <p>第二个参数（连接扩展点路由器）传 null 是安全的：{@code validate} 是纯校验，
     * 不建连、也不解析任何数据源。构造器签名随「建连收口到 SPI」调整过一个参数类型，
     * 这里同步跟上，避免测试与被测类脱节。</p>
     */
    private static final class InstanceForValidate extends DatasourceServiceImpl {
        private InstanceForValidate() {
            super(null, null, new DatasourceHubProperties(), null, null, null, null, null);
        }
    }

    @Test
    @DisplayName("DuckDB + port=0（前端占位）应通过端口范围校验")
    void duckdbWithZeroPortPasses() throws Exception {
        DataSourceDTO dto = new DataSourceDTO(
                "duck-file", "duckdb", "", 0,
                ":memory:", "", "", "accept", 1,
                "", null, "", "", null, 0);
        assertNull(invokeValidate(dto),
                "文件库类型（DuckDB）端口占位 0 不应被端口范围校验拒绝");
    }

    @Test
    @DisplayName("JDBC + port=0 仍应被端口范围校验拒绝（非文件库类型不受影响）")
    void jdbcZeroPortStillRejected() throws Exception {
        DataSourceDTO dto = new DataSourceDTO(
                "jdbc-zero", "jdbc", "127.0.0.1", 0,
                "report", "root", "root", "", 1,
                "", null, "", "", null, 0);
        assertEquals("端口非法，需为 1~65535", invokeValidate(dto),
                "非文件库类型 port=0 仍必须被拒绝");
    }

    @Test
    @DisplayName("JDBC + 合法端口（3306）应通过")
    void jdbcValidPortPasses() throws Exception {
        DataSourceDTO dto = new DataSourceDTO(
                "jdbc-ok", "jdbc", "127.0.0.1", 3306,
                "report", "root", "root", "", 1,
                "", null, "", "", null, 0);
        assertNull(invokeValidate(dto));
    }

    @Test
    @DisplayName("DuckDB + 显式非零端口也应通过（文件库仍不校验端口）")
    void duckdbWithNonNullPortPasses() throws Exception {
        DataSourceDTO dto = new DataSourceDTO(
                "duck-port", "duckdb", "", 65535,
                ":memory:", "", "", "", 1,
                "", null, "", "", null, 0);
        assertNull(invokeValidate(dto),
                "文件库类型的端口字段完全不参与范围校验");
    }}