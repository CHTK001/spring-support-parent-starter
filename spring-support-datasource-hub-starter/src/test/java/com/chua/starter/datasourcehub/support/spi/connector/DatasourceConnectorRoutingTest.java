// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.spi.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 数据源连接扩展点的分发契约测试。
 *
 * <p>这里钉的是两类真实踩过的坑，都不靠 Spring、不建任何连接：</p>
 * <ol>
 *   <li><b>order 语义</b>：{@code ServiceDefinition#compareTo} 是
 *       {@code Integer.compare(o.order, this.order)}，即 order <b>大的先试</b>。
 *       应用主库是精确类型、socket 是兜底类型，若 app 的 order 不比 socket 大，
 *       {@code resolve("app")} 会落到 socket，主库连接就退化成一条裸 TCP 探测；</li>
 *   <li><b>兜底不得越权</b>：socket 实现的 {@code supports} 必须显式排除 {@code app}，
 *       否则「谁先被遍历到」就决定了主库还能不能连。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.51
 */
class DatasourceConnectorRoutingTest {

    /**
     * 四个内置实现必须都能被 SPI 扫到；缺一个就意味着某个类型会静默落到兜底分支。
     */
    @Test
    @DisplayName("SPI 能扫到 app / calcite / jdbc / socket 四个实现")
    void discoversAllBuiltinConnectors() {
        ServiceProvider<DatasourceConnector> provider = ServiceProvider.of(DatasourceConnector.class);
        Set<String> names = new LinkedHashSet<>();
        for (var definition : provider.getPriorityServiceDefinitions()) {
            DatasourceConnector connector = definition.getObj(provider.getServiceAutowire());
            if (connector != null) {
                names.add(connector.name());
            }
        }
        assertTrue(names.contains("app"), "缺少 app 实现: " + names);
        assertTrue(names.contains("calcite"), "缺少 calcite 实现: " + names);
        assertTrue(names.contains("jdbc"), "缺少 jdbc 实现: " + names);
        assertTrue(names.contains("socket"), "缺少 socket 实现: " + names);
    }

    /**
     * 应用主库必须由 app 实现认领 —— 这是「主库直接用 Spring 的 DataSource」的入口。
     */
    @Test
    @DisplayName("主库（null / 空白 / app）由 app 实现认领，不被 socket 抢走")
    void resolvesAppDatasource() {
        DatasourceConnectorRegistry registry = new DatasourceConnectorRegistry();
        assertEquals("app", registry.resolve((String) null).name());
        assertEquals("app", registry.resolve("  ").name());
        assertEquals("app", registry.resolve("app").name());
        assertEquals("app", registry.resolve((Long) null, null).name());
    }

    /**
     * JDBC 族（含 ClickHouse / DuckDB）与合并源必须落到各自的实现，不能落到 socket：
     * 落到 socket 的后果是「能测通但打不开表」。
     */
    @Test
    @DisplayName("jdbc 族命中 jdbc，合并源命中 calcite")
    void resolvesJdbcFamily() {
        DatasourceConnectorRegistry registry = new DatasourceConnectorRegistry();
        for (String type : new String[]{"jdbc", "clickhouse", "duckdb"}) {
            assertEquals("jdbc", registry.resolve(type).name(), "类型 " + type);
        }
        assertEquals("calcite", registry.resolve("calcite").name());
    }

    /**
     * 非 JDBC 类型与未知类型走 socket（只做连通性探测），且必须明确拒绝 JDBC 连接。
     */
    @Test
    @DisplayName("非 JDBC / 未知类型落到 socket，并明确拒绝提供 JDBC 连接")
    void resolvesNonJdbcToSocket() {
        DatasourceConnectorRegistry registry = new DatasourceConnectorRegistry();
        for (String type : new String[]{"redis", "kafka", "skywalking", "prometheus", "unknown-type"}) {
            assertEquals("socket", registry.resolve(type).name(), "类型 " + type);
        }
        MsDatasource row = new MsDatasource();
        row.setDatasourceType("redis");
        assertThrows(UnsupportedOperationException.class,
                () -> registry.resolve("redis").connection(1L, row));
    }

    /**
     * 主库没有单一 JDBC 连接串：必须明确拒绝，而不是返回一个假 URL。
     */
    @Test
    @DisplayName("主库 jdbcInfo 明确拒绝，不返回假连接串")
    void appHasNoJdbcInfo() {
        DatasourceConnectorRegistry registry = new DatasourceConnectorRegistry();
        assertThrows(IllegalStateException.class, () -> registry.resolve("app").jdbcInfo(null, null));
    }

    /**
     * 清退必须幂等且不抛：删除数据源时台账行已先删除，不能依赖「按类型解析」。
     */
    @Test
    @DisplayName("evict 在无缓存时幂等通过")
    void evictIsIdempotent() {
        DatasourceConnectorRegistry registry = new DatasourceConnectorRegistry();
        registry.evict(999999L);
        registry.evict(null);
    }
}
