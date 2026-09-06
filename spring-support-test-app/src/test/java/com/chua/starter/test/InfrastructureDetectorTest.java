package com.chua.starter.test;

import com.chua.starter.common.support.serviceinfo.ServiceInfoEndpointDetector;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基础设施连接信息探测测试: 验证 Redis/JDBC/Kafka/RabbitMQ/SpringCloud/Dubbo
 * 配置能正确注册到 {@link ServiceInfoRegistry} 且不泄露密码。
 *
 * @author CH
 * @since 4.0.0.42
 */
class InfrastructureDetectorTest {

    private MockEnvironment environment;
    private ServiceInfoEndpointDetector detector;

    @BeforeEach
    void setUp() {
        ServiceInfoRegistry.getInstance().reset();
        environment = new MockEnvironment();
        detector = new ServiceInfoEndpointDetector(environment);
    }

    @AfterEach
    void tearDown() {
        ServiceInfoRegistry.getInstance().reset();
    }

    @Test
    void detectRedis() {
        environment.setProperty("spring.data.redis.host", "10.0.0.1");
        environment.setProperty("spring.data.redis.port", "6380");
        environment.setProperty("spring.data.redis.database", "2");
        environment.setProperty("spring.data.redis.password", "secret");
        detector.detectAndRegister();

        var info = ServiceInfoRegistry.getInstance().getInfo();
        var endpoints = ((java.util.Map<?, ?>) info.get("endpoints")).values();
        boolean found = endpoints.stream().anyMatch(e -> {
            var ep = (ServiceInfoRegistry.EndpointInfo) e;
            return "基础设施".equals(ep.getGroup())
                    && "Redis".equals(ep.getName())
                    && "redis://10.0.0.1:6380/2".equals(ep.getUrl())
                    && ep.getDescription().contains("认证")
                    && !ep.getUrl().contains("secret");
        });
        assertTrue(found, "Redis 端点应注册且不包含密码");
    }

    @Test
    void detectJdbc() {
        environment.setProperty("spring.datasource.url",
                "jdbc:mysql://dbhost:3306/mydb?useSSL=false&serverTimezone=UTC");
        environment.setProperty("spring.datasource.username", "root");
        environment.setProperty("spring.datasource.password", "pwd123");
        detector.detectAndRegister();

        var endpoints = ((java.util.Map<?, ?>) ServiceInfoRegistry.getInstance()
                .getInfo().get("endpoints")).values();
        boolean found = endpoints.stream().anyMatch(e -> {
            var ep = (ServiceInfoRegistry.EndpointInfo) e;
            return "基础设施".equals(ep.getGroup())
                    && "JDBC".equals(ep.getName())
                    && "jdbc:mysql://dbhost:3306/mydb".equals(ep.getUrl())
                    && ep.getDescription().contains("root")
                    && !ep.getUrl().contains("pwd123");
        });
        assertTrue(found, "JDBC 端点应精简 URL 且不包含密码");
    }

    @Test
    void detectSpringCloudAndDubbo() {
        environment.setProperty("spring.cloud.nacos.discovery.server-addr", "nacos:8848");
        environment.setProperty("spring.cloud.nacos.config.server-addr", "nacos:8848");
        environment.setProperty("spring.cloud.consul.host", "consul");
        environment.setProperty("spring.cloud.consul.port", "8500");
        environment.setProperty("eureka.client.service-url.defaultZone", "http://eureka:8761/eureka");
        environment.setProperty("dubbo.registry.address", "zookeeper://zk:2181");
        environment.setProperty("dubbo.protocol.name", "dubbo");
        environment.setProperty("dubbo.protocol.port", "20880");
        detector.detectAndRegister();

        var endpoints = ((java.util.Map<?, ?>) ServiceInfoRegistry.getInstance()
                .getInfo().get("endpoints")).values();
        long springCloud = endpoints.stream().filter(e -> "SpringCloud".equals(
                ((ServiceInfoRegistry.EndpointInfo) e).getGroup())).count();
        long dubbo = endpoints.stream().filter(e -> "Dubbo".equals(
                ((ServiceInfoRegistry.EndpointInfo) e).getGroup())).count();
        assertEquals(5, springCloud, "Nacos*2 + Consul + Eureka + 应有 5 个 SpringCloud 端点");
        assertEquals(2, dubbo, "Dubbo 注册中心 + 协议 = 2 个端点");
    }

    @Test
    void noInfrastructureWhenUnconfigured() {
        detector.detectAndRegister();
        var endpoints = ((java.util.Map<?, ?>) ServiceInfoRegistry.getInstance()
                .getInfo().get("endpoints")).values();
        long infra = endpoints.stream().filter(e -> "基础设施".equals(
                ((ServiceInfoRegistry.EndpointInfo) e).getGroup())).count();
        long sc = endpoints.stream().filter(e -> "SpringCloud".equals(
                ((ServiceInfoRegistry.EndpointInfo) e).getGroup())).count();
        long dubbo = endpoints.stream().filter(e -> "Dubbo".equals(
                ((ServiceInfoRegistry.EndpointInfo) e).getGroup())).count();
        assertEquals(0, infra, "无 Redis/JDBC/Kafka/RabbitMQ 配置时不显示");
        assertEquals(0, sc, "无 SpringCloud 配置时不显示");
        assertEquals(0, dubbo, "无 Dubbo 配置时不显示");
    }
}
