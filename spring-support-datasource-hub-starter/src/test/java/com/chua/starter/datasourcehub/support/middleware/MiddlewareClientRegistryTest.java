package com.chua.starter.datasourcehub.support.middleware;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.datasource.support.middleware.MiddlewareClientProvider;
import com.chua.datasource.support.middleware.MiddlewareConnectRequest;
import com.chua.datasource.support.middleware.MiddlewareNotInstalledException;
import com.chua.datasource.support.middleware.MiddlewareTestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中间件 SPI 分发测试：验证「宿主取实现」与「未装配时提示未开放」两条路径。
 *
 * <p>不启动 Spring 上下文：SPI 发现本来就走 {@link ServiceProvider}（classpath + OSGi），
 * 与 Spring 无关。这样测试更快，也更精准地验证契约本身。</p>
 *
 * <p><b>这是整套架构的验收核心</b>：宿主不依赖任何具体中间件，
 * 装没装实现完全由 classpath 决定，行为必须都可预期。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class MiddlewareClientRegistryTest {

    /** 与 {@link MiddlewareClientRegistry#snapshot()} 相同的装配逻辑 */
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
    @DisplayName("classpath 上的实现能被 SPI 发现（Kafka 已引入）")
    void discoversKafka() {
        List<String> names = new ArrayList<>(snapshot().keySet());
        assertFalse(names.isEmpty(), "一个实现都没发现：SPI 注册链路断了");
        assertTrue(names.contains("kafka"), "已引入 utils-support-kafka-starter 但未发现 kafka 实现: " + names);
    }

    @Test
    @DisplayName("require(kafka) 返回实现且元数据正确")
    void requiresKafka() {
        MiddlewareClientProvider kafka = snapshot().get("kafka");
        assertNotNull(kafka, "未取到 kafka 实现");
        assertEquals("kafka", kafka.name());
        assertEquals("Apache Kafka", kafka.displayName());
        assertEquals(9092, kafka.defaultPort());
        assertNotNull(kafka.capability());
        assertTrue(kafka.capability().querySupported());
    }

    @Test
    @DisplayName("未装配的类型应判定为「未支持」（= 功能未开放）")
    void notSupportedWhenAbsent() {
        Map<String, MiddlewareClientProvider> all = snapshot();
        // 这个类型不在主依赖树里，正是「没装能力包」的真实场景
        assertFalse(all.containsKey("activemq"), "activemq 不应被支持（未引入实现）");
        assertFalse(all.containsKey("neo4j"), "neo4j 不在 datasource-hub 依赖树里，不应被支持");
    }

    @Test
    @DisplayName("Kafka 实现：连接测试失败时返回对象而非抛异常")
    void testUnreachableReturnsFail() {
        MiddlewareClientProvider kafka = snapshot().get("kafka");
        assertNotNull(kafka);
        MiddlewareTestResult result = kafka.test(
                MiddlewareConnectRequest.of("127.0.0.1", 19091, "", "", ""));
        assertNotNull(result, "test 不应返回 null");
        assertFalse(result.connected(), "不可达地址不应报告连通");
        assertFalse(result.message().isBlank(), "失败原因不能为空");
    }

    @Test
    @DisplayName("Kafka 实现：不支持的查询返回失败结果而不是抛异常")
    void unsupportedQueryReturnsFail() {
        MiddlewareClientProvider kafka = snapshot().get("kafka");
        assertNotNull(kafka);
        var result = kafka.query(
                MiddlewareConnectRequest.of("127.0.0.1", 19091, "", "", ""),
                "db.orders.drop()", 10);
        assertNotNull(result);
        assertTrue(result.rows().isEmpty(), "被拒的查询不应返回行");
        assertFalse(result.message().isBlank(), "拒绝原因不能为空");
    }

    @Test
    @DisplayName("MiddlewareNotInstalledException 携带类型名（供提示文案使用）")
    void exceptionCarriesType() {
        MiddlewareNotInstalledException e = new MiddlewareNotInstalledException("kafka");
        assertEquals("kafka", e.type());
        assertTrue(e.getMessage().contains("kafka"));
    }
}
