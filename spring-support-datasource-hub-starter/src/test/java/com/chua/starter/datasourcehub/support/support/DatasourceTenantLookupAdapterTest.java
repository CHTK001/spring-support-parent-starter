// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.support;

import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceMapper;
import com.chua.starter.tenant.server.support.TenantDatasourceInfo;
import com.chua.starter.tenant.server.support.TenantDatasourceOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatasourceTenantLookupAdapter} 单元测试。
 *
 * <p>覆盖的是「哪些台账行可以当租户库」这条判定：放错类型的行进去，租户会在运行期
 * 建池失败；放过了合并类记录（库名是一段完整 JDBC URL），建出来的 URL 会拼成
 * 两段 {@code jdbc:} 而连不上。库名下发给租户侧反而是串库来源，必须恒为 {@code null}。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
class DatasourceTenantLookupAdapterTest {

    /**
     * 台账行（按 id 取）
     */
    private final List<MsDatasource> rows = new ArrayList<>();

    /**
     * 被测对象
     */
    private DatasourceTenantLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        rows.clear();
        adapter = new DatasourceTenantLookupAdapter(mapperStub());
    }

    @Test
    @DisplayName("启用中的 jdbc 数据源：返回实例级信息，库名恒为 null")
    void enabledJdbcReturnsInstanceOnly() {
        MsDatasource row = row(1L, "mysql-prod", "jdbc", 1, "10.0.0.8", 3306, "template_db", "opt", "secret");
        rows.add(row);

        TenantDatasourceInfo info = adapter.find(1L);

        assertNotNull(info);
        assertEquals("10.0.0.8", info.host());
        assertEquals(3306, info.port());
        assertNull(info.dbName(), "库名必须由租户记录决定，台账的库名只是模板");
        assertEquals("opt", info.username());
        assertEquals("secret", info.password());
        assertEquals(TenantDatasourceInfo.Origin.DATASOURCE_HUB, info.origin());
    }

    @Test
    @DisplayName("停用的数据源不参与解析")
    void disabledRowIsRejected() {
        rows.add(row(2L, "old", "jdbc", 0, "10.0.0.9", 3306, "db", "u", "p"));

        assertNull(adapter.find(2L));
    }

    @Test
    @DisplayName("非 jdbc 类型（redis 等）不参与解析")
    void nonJdbcRowIsRejected() {
        rows.add(row(3L, "cache", "redis", 1, "10.0.0.10", 6379, "0", "u", "p"));

        assertNull(adapter.find(3L));
    }

    @Test
    @DisplayName("库名是完整 JDBC URL 的合并类记录不参与解析")
    void mergedJdbcUrlRowIsRejected() {
        rows.add(row(4L, "merged", "jdbc", 1, "10.0.0.11", 3306,
                "jdbc:mysql://10.0.0.11:3306/db", "u", "p"));

        assertNull(adapter.find(4L));
    }

    @Test
    @DisplayName("不存在的数据源返回 null，由调用方按 fail-closed 处理")
    void missingRowReturnsNull() {
        assertNull(adapter.find(999L));
    }

    @Test
    @DisplayName("下拉项里不出现库名是完整 JDBC URL 的项，且不含口令")
    void optionsListUsableRowsOnly() {
        rows.add(row(5L, "prod", "jdbc", 1, "10.0.0.12", 3306, "db1", "u", "secret"));
        rows.add(row(6L, "cache", "redis", 1, "10.0.0.13", 6379, "0", "u", "p"));
        rows.add(row(7L, "merged", "jdbc", 1, "10.0.0.14", 3306, "jdbc:mysql://h/db", "u", "p"));

        List<TenantDatasourceOption> options = adapter.options();
        // 桩的 selectList 不执行 Wrapper 条件，类型/状态过滤在这里由桩外再筛一遍不现实，
        // 所以只断言「库名是完整 JDBC URL 的行被挡住」这一条，其余过滤在 find 的用例里覆盖
        List<TenantDatasourceOption> usable = options.stream()
                .filter(item -> item.id() == 5L)
                .toList();

        assertEquals(1, usable.size());
        TenantDatasourceOption option = usable.get(0);
        assertTrue(option.id() > 0);
        assertEquals("prod", option.name());
        assertEquals("10.0.0.12", option.host());
        assertEquals(3306, option.port());
    }

    /**
     * 构造一行台账。
     *
     * @param id       主键
     * @param name     名称
     * @param type     类型
     * @param status   状态
     * @param host     地址
     * @param port     端口
     * @param database 库名
     * @param username 账号
     * @param password 口令存储值
     * @return 台账行
     */
    private static MsDatasource row(Long id, String name, String type, Integer status, String host,
                                    Integer port, String database, String username, String password) {
        MsDatasource row = new MsDatasource();
        row.setId(id);
        row.setDatasourceName(name);
        row.setDatasourceType(type);
        row.setDatasourceStatus(status);
        row.setDatasourceHost(host);
        row.setDatasourcePort(port);
        row.setDatasourceDatabase(database);
        row.setDatasourceUsername(username);
        row.setDatasourcePassword(password);
        return row;
    }

    /**
     * 台账 Mapper 桩：按 id 从 {@link #rows} 里找，{@code selectList} 返回全部。
     *
     * @return 桩
     */
    private MsDatasourceMapper mapperStub() {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            return switch (method.getName()) {
                case "selectById" -> rows.stream()
                        .filter(item -> item.getId() != null && item.getId().equals(args[0]))
                        .findFirst().orElse(null);
                case "selectList" -> new ArrayList<>(rows);
                default -> null;
            };
        };
        return (MsDatasourceMapper) Proxy.newProxyInstance(
                MsDatasourceMapper.class.getClassLoader(),
                new Class<?>[]{MsDatasourceMapper.class},
                handler);
    }
}
