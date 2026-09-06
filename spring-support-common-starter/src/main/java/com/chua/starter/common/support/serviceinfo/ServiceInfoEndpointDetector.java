package com.chua.starter.common.support.serviceinfo;

import org.springframework.core.env.Environment;

/**
 * 端点探测器: 探测 Swagger / Actuator 等基础端点是否启用。
 *
 * <p>在应用就绪时由 {@link ServiceInfoBannerListener} 调用,
 * 把探测结果注册到 {@link ServiceInfoRegistry}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class ServiceInfoEndpointDetector {

    /** 环境 */
    private final Environment environment;

    /**
     * 创建 ServiceInfoEndpointDetector 实例
     * @param environment environment
     */
    public ServiceInfoEndpointDetector(Environment environment) {
        this.environment = environment;
    }

    /**
     * 探测基础端点 (应用自身 / Swagger / Actuator) 并注册到 {@link ServiceInfoRegistry}。
     */
    public void detectAndRegister() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (contextPath == null) contextPath = "";
        String trimmedContext = contextPath.replaceAll("^/|/$", "");
        String base = "http://127.0.0.1:" + port
                + (trimmedContext.isEmpty() ? "" : "/" + trimmedContext);

        ServiceInfoRegistry registry = ServiceInfoRegistry.getInstance();

        registry.registerEndpoint("通用", "应用自身",
                base + "/service-info", "服务信息聚合端点");

        // swagger 开关由 spring-support-swagger-starter 自己注册 (模块维度: 生效即开启),
        // 这里只负责补上带 host:port 的完整端点 URL, 避免同一端点被注册两次
        boolean hasSwagger = hasProperty("springdoc.swagger-ui.enabled")
                || hasProperty("knife4j.enable")
                || hasProperty("plugin.swagger.enable");
        if (hasSwagger) {
            registry.registerEndpoint("文档", "swagger",
                    base + "/doc.html", "Knife4j 接口文档");
        }

        boolean hasActuator = hasProperty("management.endpoints.web.exposure.include");
        if (hasActuator) {
            registry.registerEndpoint("监控", "actuator",
                    base + "/actuator", "Spring Boot Actuator");
        }
        registry.registerFlag("actuator", "监控", hasActuator, "Spring Boot Actuator");

        registerInfrastructure(registry);
    }

    /**
     * 注册基础设施连接信息 (Redis / JDBC / Kafka / RabbitMQ)。
     *
     * <p>从 {@link Environment} 读取 Spring Boot 标准配置, 以端点的形式展示真实连接地址,
     * 便于联调排查。所有条目均做脱敏: 不输出任何密码/密钥。</p>
     */
    private void registerInfrastructure(ServiceInfoRegistry registry) {
        registerRedis(registry);
        registerJdbc(registry);
        registerKafka(registry);
        registerRabbitMq(registry);
    }

    /** 注册 Redis 连接信息 (spring.data.redis.*) */
    private void registerRedis(ServiceInfoRegistry registry) {
        String host = environment.getProperty("spring.data.redis.host");
        if (!hasText(host)) {
            return;
        }
        String port = environment.getProperty("spring.data.redis.port", "6379");
        String db = environment.getProperty("spring.data.redis.database", "0");
        String password = environment.getProperty("spring.data.redis.password");
        boolean hasAuth = hasText(password) && !"null".equalsIgnoreCase(password);
        String url = "redis://" + host + ":" + port + "/" + db;
        String desc = "Redis" + (hasAuth ? " (认证)" : " (无密码)");
        registry.registerEndpoint("基础设施", "Redis", url, desc);
    }

    /** 注册 JDBC 连接信息 (spring.datasource.*) */
    private void registerJdbc(ServiceInfoRegistry registry) {
        String url = environment.getProperty("spring.datasource.url");
        if (!hasText(url)) {
            return;
        }
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password");
        boolean hasAuth = hasText(password);
        String desc = (hasText(username) ? "用户: " + username + " " : "")
                + (hasAuth ? " (认证)" : " (无密码)");
        registry.registerEndpoint("基础设施", "JDBC", compactJdbcUrl(url), desc.trim());
    }

    /**
     * 精简 JDBC URL: 保留协议 + 主机:端口/库名, 去掉冗长的连接参数, 便于横幅可读。
     *
     * <p>例如 {@code jdbc:mysql://host:3306/db?useSSL=false&...} -> {@code jdbc:mysql://host:3306/db}。</p>
     */
    private static String compactJdbcUrl(String url) {
        int query = url.indexOf('?');
        if (query >= 0) {
            url = url.substring(0, query);
        }
        return url;
    }

    /** 注册 Kafka 连接信息 (spring.kafka.bootstrap-servers) */
    private void registerKafka(ServiceInfoRegistry registry) {
        String servers = environment.getProperty("spring.kafka.bootstrap-servers");
        if (!hasText(servers)) {
            return;
        }
        String clientId = environment.getProperty("spring.kafka.client-id");
        String desc = hasText(clientId) ? "client-id: " + clientId : "Kafka 集群";
        registry.registerEndpoint("基础设施", "Kafka", servers, desc);
    }

    /** 注册 RabbitMQ 连接信息 (spring.rabbitmq.*) */
    private void registerRabbitMq(ServiceInfoRegistry registry) {
        String host = environment.getProperty("spring.rabbitmq.host");
        if (!hasText(host)) {
            return;
        }
        String port = environment.getProperty("spring.rabbitmq.port", "5672");
        String vhost = environment.getProperty("spring.rabbitmq.virtual-host", "/");
        String username = environment.getProperty("spring.rabbitmq.username", "");
        String password = environment.getProperty("spring.rabbitmq.password");
        boolean hasAuth = hasText(password);
        String url = "amqp://" + host + ":" + port + (hasText(vhost) ? "/" + vhost : "");
        String desc = (hasText(username) ? "用户: " + username + " " : "")
                + (hasAuth ? " (认证)" : " (无密码)");
        registry.registerEndpoint("基础设施", "RabbitMQ", url, desc.trim());
    }

    /** 是否拥有Property */
    private boolean hasProperty(String key) {
        return environment.getProperty(key) != null;
    }

    /** 是否非空文本 */
    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
