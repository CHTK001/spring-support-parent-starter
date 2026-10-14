package com.chua.starter.datasourcehub.support.middleware;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.datasource.support.middleware.MiddlewareClientProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多实现装配测试：验证「同一个 SPI 有多个实现时，宿主能各自按 name() 取到」。
 *
 * <p>这是「宿主不依赖具体中间件」架构的第二层验证：单实现能发现只是起点，
 * 多实现并存且互不串号才是真实场景（Kafka / InfluxDB / ZooKeeper 同时在 classpath 上）。</p>
 */
class MiddlewareMultiProviderTest {

    private Map<String, MiddlewareClientProvider> snapshot() {
        ServiceProvider<MiddlewareClientProvider> sp = ServiceProvider.of(MiddlewareClientProvider.class);
        Map<String, MiddlewareClientProvider> map = new LinkedHashMap<>();
        for (ServiceDefinition definition : sp.getPriorityServiceDefinitions()) {
            MiddlewareClientProvider impl = (MiddlewareClientProvider) definition.getObj(sp.getServiceAutowire());
            if (impl != null && impl.name() != null && !impl.name().isBlank()) {
                map.put(impl.name().toLowerCase(), impl);
            }
        }
        return map;
    }

    @Test
    @DisplayName("Kafka / InfluxDB / ZooKeeper 三个实现同时被发现，互不覆盖")
    void discoversAll() {
        Map<String, MiddlewareClientProvider> all = snapshot();
        assertTrue(all.containsKey("kafka"), "缺 kafka，实际: " + all.keySet());
        assertTrue(all.containsKey("influxdb"), "缺 influxdb，实际: " + all.keySet());
        assertTrue(all.containsKey("zookeeper"), "缺 zookeeper，实际: " + all.keySet());
    }

    @Test
    @DisplayName("各实现的 name() 与其在注册表中的键一致（不串号）")
    void noCrossWiring() {
        Map<String, MiddlewareClientProvider> all = snapshot();
        for (Map.Entry<String, MiddlewareClientProvider> e : all.entrySet()) {
            assertEquals(e.getKey(), e.getValue().name().toLowerCase(),
                    "注册键与实现自报的 name 不一致: " + e.getKey());
        }
    }

    @Test
    @DisplayName("各实现的默认端口互不相同（防止复制粘贴错）")
    void distinctPorts() {
        Map<String, MiddlewareClientProvider> all = snapshot();
        assertEquals(9092, all.get("kafka").defaultPort());
        assertEquals(8086, all.get("influxdb").defaultPort());
        assertEquals(2181, all.get("zookeeper").defaultPort());
    }

    @Test
    @DisplayName("未装配的类型（neo4j/mongodb/activemq）判为未支持")
    void unsupportedTypes() {
        Map<String, MiddlewareClientProvider> all = snapshot();
        // mongodb 目前仍在 datasource-hub 里用原生实现，未走 SPI，因此这里应当「未支持」
        assertFalse(all.containsKey("mongodb"), "mongodb 尚未 SPI 化，不应出现在注册表: " + all.keySet());
        assertFalse(all.containsKey("neo4j"), "neo4j 不应出现在注册表: " + all.keySet());
        assertFalse(all.containsKey("activemq"), "activemq 不应出现在注册表: " + all.keySet());
    }

    @Test
    @DisplayName("每个实现的能力声明都可用（前端据此渲染工具栏）")
    void capabilitiesUsable() {
        Map<String, MiddlewareClientProvider> all = snapshot();
        List<String> names = List.of("kafka", "influxdb", "zookeeper");
        for (String n : names) {
            MiddlewareClientProvider p = all.get(n);
            assertNotNull(p, "缺实现: " + n);
            assertNotNull(p.capability(), n + " 的能力声明为 null");
            assertFalse(p.displayName().isBlank(), n + " 的展示名为空");
            assertTrue(p.defaultPort() > 0, n + " 的默认端口非法");
        }
    }
}
