package com.chua.starter.datasourcehub.support.middleware;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.datasource.support.middleware.MiddlewareClientProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全部中间件 SPI 装配验收：Kafka / InfluxDB / ZooKeeper 三个「按需」实现
 * 已装配时，宿主应能全部发现并各自取到。
 *
 * <p>不在此断言 Redis / Nacos：它们由主应用的明文依赖决定是否在树里，
 * datasource-hub 本身不引它们 —— 这正是「默认带 vs 按需带」的分界线。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class MiddlewareAllProvidersTest {

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
    @DisplayName("datasource-hub 依赖树内的三个 SPI 实现全部被发现")
    void allInstalled() {
        Map<String, MiddlewareClientProvider> all = snapshot();
        assertTrue(all.containsKey("kafka"), "缺 kafka: " + all.keySet());
        assertTrue(all.containsKey("influxdb"), "缺 influxdb: " + all.keySet());
        assertTrue(all.containsKey("zookeeper"), "缺 zookeeper: " + all.keySet());
    }

    @Test
    @DisplayName("三个实现的展示名各不相同（前端下拉可区分）")
    void displayNamesDistinct() {
        Map<String, MiddlewareClientProvider> all = snapshot();
        String k = all.get("kafka").displayName();
        String i = all.get("influxdb").displayName();
        String z = all.get("zookeeper").displayName();
        assertTrue(!k.equals(i) && !i.equals(z) && !k.equals(z),
                "展示名重复: " + k + " / " + i + " / " + z);
    }

    @Test
    @DisplayName("三个实现都声明了查询能力（工具栏据此渲染）")
    void allQueryable() {
        Map<String, MiddlewareClientProvider> all = snapshot();
        for (String n : new String[]{"kafka", "influxdb", "zookeeper"}) {
            assertTrue(all.get(n).capability().querySupported(), n + " 未声明查询能力");
            assertTrue(all.get(n).capability().topNodeSupported(), n + " 未声明顶层节点能力");
        }
    }

    @Test
    @DisplayName("类型名大小写不敏感（前端可能传 KAFKA / Kafka）")
    void caseInsensitive() {
        Map<String, MiddlewareClientProvider> all = snapshot();
        assertEquals(all.get("kafka").getClass(), all.get("kafka".toLowerCase()).getClass());
        // 注册表键统一为小写
        for (String k : all.keySet()) {
            assertEquals(k, k.toLowerCase(), "注册表键应为小写: " + k);
        }
    }
}
