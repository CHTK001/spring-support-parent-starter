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

    /** 是否展示基础设施连接信息 (默认 true, 且仅展示真实配置的条目) */
    private boolean infrastructureEnabled = true;

    /**
     * 创建 服务信息端点detector 实例
     * @param environment 环境
     */
    public ServiceInfoEndpointDetector(Environment environment) {
        this.environment = environment;
    }

    /**
     * 设置是否展示基础设施连接信息。
     *
     * @param infrastructureEnabled true 展示已配置的中间件连接信息
     * @return this
     */
    public ServiceInfoEndpointDetector infrastructureEnabled(boolean infrastructureEnabled) {
        this.infrastructureEnabled = infrastructureEnabled;
        return this;
    }

    /**
     * 探测基础端点 (应用自身 / Swagger / Actuator) 并注册到 {@link ServiceInfoRegistry}。
     */
    public void detectAndRegister() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (contextPath == null) {
            contextPath = "";
        }
        String trimmedContext = contextPath.replaceAll("^/|/$", "");
        String base = "http://127.0.0.1:" + port
                + (trimmedContext.isEmpty() ? "" : "/" + trimmedContext);

        ServiceInfoRegistry registry = ServiceInfoRegistry.getInstance();

        registry.registerEndpoint("通用", "应用自身",
                base + "/service-info", "服务信息聚合端点");

        // swagger 开关由 spring-support-swagger-starter 自己注册 (模块维度: 生效即开启),
 // 这里只负责补上带 主机:端口 的完整端点 URL, 避免同一端点被注册两次
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
     * 注册基础设施连接信息 (Redis / JDBC / Kafka / RabbitMQ / Spring Cloud / Dubbo)。
     *
     * <p>从 {@link Environment} 读取 Spring Boot / Spring Cloud / Dubbo 标准配置,
     * 以端点的形式展示真实连接地址, 便于联调排查。所有条目均做脱敏: 不输出任何密码/密钥。</p>
     * @param registry registry
     */
    private void registerInfrastructure(ServiceInfoRegistry registry) {
        if (!infrastructureEnabled) {
            return;
        }
        registerRedis(registry);
        registerJdbc(registry);
        registerKafka(registry);
        registerRabbitMq(registry);
        registerSpringCloud(registry);
        registerDubbo(registry);
        registerFrameworkDiscovery(registry);
        registerFrameworkConfigCenter(registry);
    }

    /**
     * 注册 Redis 连接信息 (spring.数据.Redis.*)
     *
     * @param registry registry
     */
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

    /**
     * 注册 JDBC 连接信息 (spring.datasource.*)
     *
     * @param registry registry
     */
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
     * @param url url
     * @return compactJdbcUrl的结果
     */
    private static String compactJdbcUrl(String url) {
        int query = url.indexOf('?');
        if (query >= 0) {
            url = url.substring(0, query);
        }
        return url;
    }

    /**
     * 注册 Kafka 连接信息 (spring.Kafka.bootstrap-服务端)
     *
     * @param registry registry
     */
    private void registerKafka(ServiceInfoRegistry registry) {
        String servers = environment.getProperty("spring.kafka.bootstrap-servers");
        if (!hasText(servers)) {
            return;
        }
        String clientId = environment.getProperty("spring.kafka.client-id");
        String desc = hasText(clientId) ? "client-id: " + clientId : "Kafka 集群";
        registry.registerEndpoint("基础设施", "Kafka", servers, desc);
    }

    /**
     * 注册 RabbitMQ 连接信息 (spring.RabbitMQ.*)
     *
     * @param registry registry
     */
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

    /**
     * 是否拥有财产
     *
     * @param key 键
     * @return 是否包含财产的结果
     */
    private boolean hasProperty(String key) {
        return environment.getProperty(key) != null;
    }

    /**
     * 是否非空文本
     *
     * @param text 文本
     * @return 是否包含文本的结果
     */
    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    // ------------------------------------------------------------------ Spring Cloud / Dubbo

    /**
     * 注册 Spring Cloud 体系注册中心/配置中心连接信息。
     *
     * <p>覆盖 Nacos / Consul / Eureka / ZooKeeper / Spring Cloud Config Server,
     * 从 Spring Cloud 标准配置读取。</p>
     * @param registry registry
     */
    private void registerSpringCloud(ServiceInfoRegistry registry) {
        registerNacos(registry);
        registerConsul(registry);
        registerEureka(registry);
        registerZookeeper(registry);
        registerSpringCloudConfig(registry);
    }

    /**
     * 注册 Nacos (spring.cloud.nacos.*)
     *
     * @param registry registry
     */
    private void registerNacos(ServiceInfoRegistry registry) {
        String discovery = environment.getProperty("spring.cloud.nacos.discovery.server-addr");
        String config = environment.getProperty("spring.cloud.nacos.config.server-addr");
        if (hasText(discovery)) {
            registry.registerEndpoint("SpringCloud", "Nacos 注册中心", discovery, "服务发现");
        }
        if (hasText(config)) {
            registry.registerEndpoint("SpringCloud", "Nacos 配置中心", config, "配置中心");
        }
    }

    /**
     * 注册 Consul (spring.cloud.consul.*)
     *
     * @param registry registry
     */
    private void registerConsul(ServiceInfoRegistry registry) {
        String host = environment.getProperty("spring.cloud.consul.host");
        if (!hasText(host)) {
            return;
        }
        String port = environment.getProperty("spring.cloud.consul.port", "8500");
        registry.registerEndpoint("SpringCloud", "Consul", host + ":" + port, "服务注册与配置");
    }

    /**
     * 注册 Eureka (eureka.客户端.服务-url.*)
     *
     * @param registry registry
     */
    private void registerEureka(ServiceInfoRegistry registry) {
        String zone = environment.getProperty("eureka.client.service-url.defaultZone");
        if (!hasText(zone)) {
            return;
        }
        registry.registerEndpoint("SpringCloud", "Eureka", zone, "服务注册中心");
    }

    /**
     * 注册 ZooKeeper (spring.cloud.ZooKeeper.连接-字符串)
     *
     * @param registry registry
     */
    private void registerZookeeper(ServiceInfoRegistry registry) {
        String connect = environment.getProperty("spring.cloud.zookeeper.connect-string");
        if (!hasText(connect)) {
            return;
        }
        registry.registerEndpoint("SpringCloud", "ZooKeeper", connect, "服务注册中心");
    }

    /**
     * 注册 Spring Cloud 配置 服务端 (spring.cloud.配置.uri)
     *
     * @param registry registry
     */
    private void registerSpringCloudConfig(ServiceInfoRegistry registry) {
        String uri = environment.getProperty("spring.cloud.config.uri");
        if (!hasText(uri)) {
            return;
        }
        registry.registerEndpoint("SpringCloud", "Config Server", uri, "集中配置中心");
    }

    /**
     * 注册 Dubbo 连接信息。
     *
     * <p>读取 {@code dubbo.registry.address} 与 {@code dubbo.protocol.*}。</p>
     * @param registry registry
     */
    private void registerDubbo(ServiceInfoRegistry registry) {
        String registryAddr = environment.getProperty("dubbo.registry.address");
        if (hasText(registryAddr)) {
            registry.registerEndpoint("Dubbo", "注册中心", registryAddr,
                    environment.getProperty("dubbo.registry.group", "dubbo"));
        }
        String protocolName = environment.getProperty("dubbo.protocol.name");
        String protocolPort = environment.getProperty("dubbo.protocol.port");
        if (hasText(protocolName) || hasText(protocolPort)) {
            String desc = (hasText(protocolName) ? protocolName : "dubbo")
                    + (hasText(protocolPort) ? ":" + protocolPort : "");
            registry.registerEndpoint("Dubbo", "服务协议", desc, "RPC 服务端口");
        }
    }

    /**
     * 注册框架自身服务发现配置 (plugin.discovery.*)
     *
     * @param registry registry
     */
    private void registerFrameworkDiscovery(ServiceInfoRegistry registry) {
        String address = environment.getProperty("plugin.discovery.address");
        if (!hasText(address)) {
            return;
        }
        String protocol = environment.getProperty("plugin.discovery.protocol", "discovery");
        registry.registerEndpoint("SpringCloud", "Discovery", protocol + "://" + address, "框架服务发现");
    }

    /**
     * 注册框架自身配置中心 (plugin.配置-center.*)
     *
     * @param registry registry
     */
    private void registerFrameworkConfigCenter(ServiceInfoRegistry registry) {
        String address = environment.getProperty("plugin.config-center.address");
        if (!hasText(address)) {
            return;
        }
        String protocol = environment.getProperty("plugin.config-center.protocol", "config-center");
        String desc = environment.getProperty("plugin.config-center.namespace-id");
        registry.registerEndpoint("SpringCloud", "Config Center", protocol + "://" + address,
                hasText(desc) ? "命名空间: " + desc : "框架配置中心");
    }
}
