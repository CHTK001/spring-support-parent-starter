package com.chua.starter.panel.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.panel.config.PanelProperties;
import com.chua.starter.panel.model.PanelConnectionDefinition;
import com.chua.starter.panel.model.PanelConnectionHandle;
import com.chua.starter.panel.model.PanelConnectionType;
import com.chua.starter.panel.model.PanelDatasourceRequest;
import com.chua.starter.panel.model.PanelDatasourceTestView;
import com.chua.starter.panel.model.PanelDatasourceView;
import com.chua.starter.panel.model.PanelDriverUploadView;
import com.chua.starter.panel.model.PanelRedisInfoView;
import com.chua.starter.panel.service.DockerPanelService;
import com.chua.starter.panel.service.InfluxPanelService;
import com.chua.starter.panel.service.InfluxV3PanelService;
import com.chua.starter.panel.service.KafkaPanelService;
import com.chua.starter.panel.service.MongoPanelService;
import com.chua.starter.panel.service.NacosPanelService;
import com.chua.starter.panel.service.NginxPanelService;
import com.chua.starter.panel.service.PanelConnectionService;
import com.chua.starter.panel.service.PanelDatasourceService;
import com.chua.starter.panel.service.PrometheusPanelService;
import com.chua.starter.panel.service.RedisPanelService;
import com.chua.starter.panel.service.ZookeeperPanelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 Panel 数据源服务。
 *
 * @author CH
 */
public class DefaultPanelDatasourceService implements PanelDatasourceService {

    private static final TypeReference<List<PanelDatasourceView>> DATASOURCE_LIST_TYPE = new TypeReference<>() {
    };
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ENCRYPTED_PREFIX = "enc::";
    private static final String ATTR_DIALECT_TYPE = "panelDialectType";
    private static final String ATTR_DRIVER_CLASS_NAME = "panelDriverClassName";
    private static final String ATTR_DRIVER_JAR_PATH = "panelDriverJarPath";

    private final ObjectMapper objectMapper;
    private final Path storePath;
    private final Path driverStorePath;
    private final byte[] encryptionKey;
    private final PanelConnectionService panelConnectionService;
    private final ObjectProvider<RedisPanelService> redisPanelServiceProvider;
    private final ObjectProvider<MongoPanelService> mongoPanelServiceProvider;
    private final ObjectProvider<ZookeeperPanelService> zookeeperPanelServiceProvider;
    private final ObjectProvider<PrometheusPanelService> prometheusPanelServiceProvider;
    private final ObjectProvider<InfluxPanelService> influxPanelServiceProvider;
    private final ObjectProvider<InfluxV3PanelService> influxV3PanelServiceProvider;
    private final ObjectProvider<NacosPanelService> nacosPanelServiceProvider;
    private final ObjectProvider<NginxPanelService> nginxPanelServiceProvider;
    private final ObjectProvider<DockerPanelService> dockerPanelServiceProvider;
    private final ObjectProvider<KafkaPanelService> kafkaPanelServiceProvider;
    private final Map<String, PanelDatasourceView> datasourceStore = new ConcurrentHashMap<>();

    public DefaultPanelDatasourceService(
            PanelProperties properties,
            ObjectMapper objectMapper,
            PanelConnectionService panelConnectionService,
            ObjectProvider<RedisPanelService> redisPanelServiceProvider,
            ObjectProvider<MongoPanelService> mongoPanelServiceProvider,
            ObjectProvider<ZookeeperPanelService> zookeeperPanelServiceProvider,
            ObjectProvider<PrometheusPanelService> prometheusPanelServiceProvider,
            ObjectProvider<InfluxPanelService> influxPanelServiceProvider,
            ObjectProvider<InfluxV3PanelService> influxV3PanelServiceProvider,
            ObjectProvider<NacosPanelService> nacosPanelServiceProvider,
            ObjectProvider<NginxPanelService> nginxPanelServiceProvider,
            ObjectProvider<DockerPanelService> dockerPanelServiceProvider,
            ObjectProvider<KafkaPanelService> kafkaPanelServiceProvider) {
        this.objectMapper = objectMapper;
        this.storePath = Paths.get(properties.getDatasourceStorePath()).toAbsolutePath().normalize();
        this.driverStorePath = Paths.get(properties.getDatasourceDriverStorePath()).toAbsolutePath().normalize();
        this.encryptionKey = deriveEncryptionKey(properties.getDatasourceEncryptionKey());
        this.panelConnectionService = panelConnectionService;
        this.redisPanelServiceProvider = redisPanelServiceProvider;
        this.mongoPanelServiceProvider = mongoPanelServiceProvider;
        this.zookeeperPanelServiceProvider = zookeeperPanelServiceProvider;
        this.prometheusPanelServiceProvider = prometheusPanelServiceProvider;
        this.influxPanelServiceProvider = influxPanelServiceProvider;
        this.influxV3PanelServiceProvider = influxV3PanelServiceProvider;
        this.nacosPanelServiceProvider = nacosPanelServiceProvider;
        this.nginxPanelServiceProvider = nginxPanelServiceProvider;
        this.dockerPanelServiceProvider = dockerPanelServiceProvider;
        this.kafkaPanelServiceProvider = kafkaPanelServiceProvider;
        loadStore();
    }

    @Override
    public List<PanelDatasourceView> listAll() {
        return datasourceStore.values().stream()
                .sorted(Comparator.comparing(
                        PanelDatasourceView::getPanelUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    @Override
    public PanelDatasourceView save(PanelDatasourceRequest request) {
        String panelSourceId = StringUtils.isBlank(request.getPanelSourceId())
                ? "panel-source-" + UUID.randomUUID()
                : request.getPanelSourceId().trim();
        PanelDatasourceView panelDatasourceView = PanelDatasourceView.builder()
                .panelSourceId(panelSourceId)
                .panelConnectionId(trimToNull(request.getPanelConnectionId()))
                .panelSourceType(resolveSourceType(request))
                .panelConnectionName(defaultValue(request.getPanelConnectionName(), "未命名数据源"))
                .panelHost(trimToNull(request.getPanelHost()))
                .panelPort(request.getPanelPort())
                .panelDatabaseName(trimToNull(request.getPanelDatabaseName()))
                .panelUsername(trimToNull(request.getPanelUsername()))
                .panelPassword(defaultValue(request.getPanelPassword(), ""))
                .panelProtocol(trimToNull(request.getPanelProtocol()))
                .panelDialectType(resolveDialectType(request))
                .panelDriverClassName(trimToNull(request.getPanelDriverClassName()))
                .panelDriverJarName(trimToNull(request.getPanelDriverJarName()))
                .panelDriverJarPath(trimToNull(request.getPanelDriverJarPath()))
                .panelNote(trimToNull(request.getPanelNote()))
                .panelFavorite(Boolean.TRUE.equals(request.getPanelFavorite()))
                .panelEnvironment(trimToNull(request.getPanelEnvironment()))
                .panelOwner(trimToNull(request.getPanelOwner()))
                .panelReadOnly(Boolean.TRUE.equals(request.getPanelReadOnly()))
                .panelSslEnabled(Boolean.TRUE.equals(request.getPanelSslEnabled()))
                .panelConnectTimeoutMillis(request.getPanelConnectTimeoutMillis())
                .panelSocketTimeoutMillis(request.getPanelSocketTimeoutMillis())
                .panelCharset(trimToNull(request.getPanelCharset()))
                .panelLastLatencyMs(request.getPanelLastLatencyMs())
                .panelLastConnectedAt(trimToNull(request.getPanelLastConnectedAt()))
                .panelDetectedVersion(trimToNull(request.getPanelDetectedVersion()))
                .panelAttributes(normalizeAttributes(request.getPanelAttributes()))
                .panelUpdatedAt(OffsetDateTime.now().toString())
                .build();
        datasourceStore.put(panelSourceId, panelDatasourceView);
        persistStore();
        return panelDatasourceView;
    }

    @Override
    public PanelDatasourceTestView test(PanelDatasourceRequest request) {
        long startTime = System.nanoTime();
        PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder = PanelDatasourceTestView.builder()
                .panelSuccess(Boolean.FALSE)
                .panelResolvedProtocol(resolveTestProtocol(request));
        try {
            Map<String, Object> details = switch (resolveSourceType(request)) {
                case "REDIS" -> testRedis(request, builder);
                case "MONGODB" -> testMongo(request, builder);
                case "ZOOKEEPER" -> testZookeeper(request, builder);
                case "PROMETHEUS" -> testPrometheus(request, builder);
                case "INFLUXDB2" -> testInflux(request, builder);
                case "INFLUXDB3" -> testInfluxV3(request, builder);
                case "NACOS" -> testNacos(request, builder);
                case "NGINX" -> testNginx(request, builder);
                case "DOCKER" -> testDocker(request, builder);
                case "KAFKA" -> testKafka(request, builder);
                default -> testJdbc(request, builder);
            };
            builder.panelSuccess(Boolean.TRUE)
                    .panelMessage(defaultValue(builder.build().getPanelMessage(), "连接成功"))
                    .panelDetails(details);
        } catch (Exception exception) {
            builder.panelSuccess(Boolean.FALSE)
                    .panelMessage(extractRootMessage(exception))
                    .panelDetails(Map.of(
                            "sourceType", resolveSourceType(request),
                            "error", extractRootMessage(exception)
                    ));
        }
        long latencyMs = (System.nanoTime() - startTime) / 1_000_000L;
        return builder.panelLatencyMs(latencyMs).build();
    }

    @Override
    public PanelDriverUploadView uploadDriver(String panelDialectType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("驱动文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String safeFileName = sanitizeDriverFileName(originalFilename);
        if (!safeFileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("仅支持上传 .jar 驱动包");
        }
        try {
            Files.createDirectories(driverStorePath);
            String storedFileName = System.currentTimeMillis() + "-" + safeFileName;
            Path target = driverStorePath.resolve(storedFileName).normalize();
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String dialectType = defaultValue(panelDialectType, detectDialectType(safeFileName));
            return PanelDriverUploadView.builder()
                    .panelDialectType(dialectType)
                    .panelDriverClassName(resolveDriverClassName(dialectType, safeFileName))
                    .panelDriverJarName(safeFileName)
                    .panelDriverJarPath(target.toString())
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("上传 JDBC 驱动失败", exception);
        }
    }

    @Override
    public void delete(String panelSourceId) {
        if (StringUtils.isBlank(panelSourceId)) {
            return;
        }
        datasourceStore.remove(panelSourceId.trim());
        persistStore();
    }

    private Map<String, Object> testJdbc(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) throws Exception {
        PanelConnectionDefinition definition = buildJdbcDefinition(request);
        PanelConnectionHandle handle = panelConnectionService.open(definition);
        try {
            Object nativeConnection = handle.getNativeConnection();
            if (!(nativeConnection instanceof DataSource dataSource)) {
                throw new IllegalStateException("未创建可用的 JDBC 数据源");
            }
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String jdbcUrl = trimToNull(metaData.getURL());
                builder.panelDetectedVersion(trimToNull(metaData.getDatabaseProductVersion()))
                        .panelResolvedProtocol(defaultValue(jdbcUrl, builder.build().getPanelResolvedProtocol()))
                        .panelMessage(defaultValue(metaData.getDatabaseProductName(), "JDBC") + " 连接成功");
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("databaseProductName", metaData.getDatabaseProductName());
                details.put("databaseProductVersion", metaData.getDatabaseProductVersion());
                details.put("driverName", metaData.getDriverName());
                details.put("driverVersion", metaData.getDriverVersion());
                details.put("jdbcUrl", jdbcUrl);
                return details;
            }
        } finally {
            panelConnectionService.evict(handle.getConnectionId());
        }
    }

    private Map<String, Object> testRedis(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) {
        RedisPanelService redisPanelService = requireProvider(redisPanelServiceProvider, "Redis");
        int dbIndex = intAttribute(request, "dbIndex", parseIntOrDefault(request.getPanelDatabaseName(), 0));
        String connectionId = redisPanelService.open(
                required(request.getPanelHost(), "Redis host 不能为空"),
                requiredPort(request.getPanelPort()),
                defaultValue(request.getPanelPassword(), ""),
                dbIndex);
        try {
            PanelRedisInfoView infoView = redisPanelService.info(connectionId);
            builder.panelDetectedVersion(trimToNull(infoView.getVersion()))
                    .panelMessage("Redis 连接成功");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("version", infoView.getVersion());
            details.put("mode", infoView.getMode());
            details.put("connectedClients", infoView.getConnectedClients());
            details.put("dbIndex", dbIndex);
            return details;
        } finally {
            redisPanelService.close(connectionId);
        }
    }

    private Map<String, Object> testMongo(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) {
        MongoPanelService mongoPanelService = requireProvider(mongoPanelServiceProvider, "MongoDB");
        String authDatabase = stringAttribute(request, "authDatabase", request.getPanelDatabaseName());
        String connectionId = mongoPanelService.open(
                required(request.getPanelHost(), "MongoDB host 不能为空"),
                requiredPort(request.getPanelPort()),
                trimToNull(request.getPanelUsername()),
                trimToNull(request.getPanelPassword()),
                authDatabase);
        try {
            List<String> databases = mongoPanelService.listDatabases(connectionId);
            Object buildInfo = mongoPanelService.executeCommand(
                    connectionId,
                    defaultValue(authDatabase, "admin"),
                    "{\"buildInfo\":1}");
            String version = mapString(buildInfo, "version");
            builder.panelDetectedVersion(version)
                    .panelMessage("MongoDB 连接成功");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("databaseCount", databases.size());
            details.put("authDatabase", defaultValue(authDatabase, "admin"));
            if (StringUtils.isNotBlank(version)) {
                details.put("version", version);
            }
            String replicaSet = stringAttribute(request, "replicaSet", null);
            if (StringUtils.isNotBlank(replicaSet)) {
                details.put("replicaSet", replicaSet);
            }
            return details;
        } finally {
            mongoPanelService.close(connectionId);
        }
    }

    private Map<String, Object> testZookeeper(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) {
        ZookeeperPanelService zookeeperPanelService = requireProvider(zookeeperPanelServiceProvider, "ZooKeeper");
        int sessionTimeoutMs = intAttribute(request, "sessionTimeoutMillis", 30_000);
        String connectionId = zookeeperPanelService.open(
                required(request.getPanelHost(), "ZooKeeper host 不能为空"),
                requiredPort(request.getPanelPort()),
                sessionTimeoutMs);
        try {
            List<String> children = zookeeperPanelService.ls(connectionId, "/");
            builder.panelMessage("ZooKeeper 连接成功");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("rootChildren", children.size());
            details.put("sessionTimeoutMillis", sessionTimeoutMs);
            return details;
        } finally {
            zookeeperPanelService.close(connectionId);
        }
    }

    private Map<String, Object> testPrometheus(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) {
        PrometheusPanelService prometheusPanelService = requireProvider(prometheusPanelServiceProvider, "Prometheus");
        String connectionId = prometheusPanelService.open(
                required(request.getPanelHost(), "Prometheus host 不能为空"),
                requiredPort(request.getPanelPort()),
                trimToNull(request.getPanelUsername()),
                trimToNull(request.getPanelPassword()));
        try {
            Map<String, Object> targets = prometheusPanelService.listTargets(connectionId);
            builder.panelMessage("Prometheus 连接成功");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("targetGroups", mapListSize(targets, "activeTargets"));
            details.put("scheme", defaultValue(request.getPanelProtocol(), "http"));
            return details;
        } finally {
            prometheusPanelService.close(connectionId);
        }
    }

    private Map<String, Object> testInflux(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) {
        InfluxPanelService influxPanelService = requireProvider(influxPanelServiceProvider, "InfluxDB 2.x");
        String connectionId = influxPanelService.open(
                required(request.getPanelHost(), "InfluxDB host 不能为空"),
                requiredPort(request.getPanelPort()),
                defaultValue(request.getPanelPassword(), ""),
                defaultValue(request.getPanelUsername(), ""),
                defaultValue(request.getPanelDatabaseName(), ""));
        try {
            Map<String, Object> health = influxPanelService.health(connectionId);
            String version = mapString(health, "version");
            builder.panelDetectedVersion(version)
                    .panelMessage(defaultValue(mapString(health, "message"), "InfluxDB 连接成功"));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("status", mapString(health, "status"));
            if (StringUtils.isNotBlank(version)) {
                details.put("version", version);
            }
            details.put("bucket", defaultValue(request.getPanelDatabaseName(), ""));
            return details;
        } finally {
            influxPanelService.close(connectionId);
        }
    }

    private Map<String, Object> testInfluxV3(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) {
        InfluxV3PanelService influxV3PanelService = requireProvider(influxV3PanelServiceProvider, "InfluxDB 3.x");
        String connectionId = influxV3PanelService.open(
                required(request.getPanelHost(), "InfluxDB 3 host 不能为空"),
                requiredPort(request.getPanelPort()),
                defaultValue(request.getPanelPassword(), ""),
                defaultValue(request.getPanelDatabaseName(), ""));
        try {
            Map<String, Object> health = influxV3PanelService.health(connectionId);
            String version = mapString(health, "version");
            builder.panelDetectedVersion(version)
                    .panelMessage(defaultValue(mapString(health, "message"), "InfluxDB 3.x 连接成功"));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("status", mapString(health, "status"));
            if (StringUtils.isNotBlank(version)) {
                details.put("version", version);
            }
            details.put("database", defaultValue(request.getPanelDatabaseName(), ""));
            return details;
        } finally {
            influxV3PanelService.close(connectionId);
        }
    }

    private Map<String, Object> testNginx(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) {
        NginxPanelService nginxPanelService = requireProvider(nginxPanelServiceProvider, "Nginx");
        String connectionId = nginxPanelService.open(
                required(request.getPanelHost(), "Nginx host 不能为空"),
                requiredPort(request.getPanelPort()),
                defaultValue(request.getPanelUsername(), "root"),
                trimToNull(request.getPanelPassword()),
                stringAttribute(request, "privateKey", null));
        try {
            Map<String, Object> status = nginxPanelService.status(connectionId);
            String version = mapString(status, "version");
            builder.panelDetectedVersion(version)
                    .panelMessage("Nginx SSH 连接成功");
            Map<String, Object> details = new LinkedHashMap<>();
            if (StringUtils.isNotBlank(version)) {
                details.put("version", version);
            }
            details.put("pid", mapString(status, "pid"));
            details.put("configTest", mapString(status, "configTest"));
            return details;
        } finally {
            nginxPanelService.close(connectionId);
        }
    }

    private Map<String, Object> testNacos(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) {
        NacosPanelService nacosPanelService = requireProvider(nacosPanelServiceProvider, "Nacos");
        String protocol = defaultValue(request.getPanelProtocol(), "http");
        String connectionId = nacosPanelService.open(
                required(request.getPanelHost(), "Nacos host 不能为空"),
                requiredPort(request.getPanelPort()),
                trimToNull(request.getPanelUsername()),
                trimToNull(request.getPanelPassword()),
                protocol);
        try {
            Map<String, Object> namespaces = nacosPanelService.namespaces(connectionId);
            Object data = namespaces.get("data");
            int namespaceCount = data instanceof List<?> listValue ? listValue.size() : 0;
            builder.panelMessage("Nacos 连接成功");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("namespaceCount", namespaceCount);
            details.put("protocol", protocol);
            details.put("tenant", defaultValue(request.getPanelDatabaseName(), "public"));
            return details;
        } finally {
            nacosPanelService.close(connectionId);
        }
    }

    private Map<String, Object> testDocker(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) {
        DockerPanelService dockerPanelService = requireProvider(dockerPanelServiceProvider, "Docker");
        String protocol = defaultValue(request.getPanelProtocol(), "tcp");
        String connectionId = dockerPanelService.open(
                required(request.getPanelHost(), "Docker host 不能为空"),
                requiredPort(request.getPanelPort()),
                protocol);
        try {
            String ping = dockerPanelService.ping(connectionId);
            Map<String, Object> info = dockerPanelService.info(connectionId);
            String version = mapString(info, "serverVersion");
            builder.panelDetectedVersion(version)
                    .panelMessage("Docker " + ("pong".equals(ping) ? "连接成功" : "连接异常"));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("ping", ping);
            if (StringUtils.isNotBlank(version)) {
                details.put("serverVersion", version);
            }
            details.put("nCPU", info.get("nCPU"));
            details.put("memTotal", info.get("memTotal"));
            details.put("containers", info.get("containers"));
            details.put("containersRunning", info.get("containersRunning"));
            details.put("images", info.get("images"));
            details.put("driver", info.get("driver"));
            details.put("protocol", protocol);
            return details;
        } finally {
            dockerPanelService.close(connectionId);
        }
    }

    private Map<String, Object> testKafka(
            PanelDatasourceRequest request,
            PanelDatasourceTestView.PanelDatasourceTestViewBuilder builder) {
        KafkaPanelService kafkaPanelService = requireProvider(kafkaPanelServiceProvider, "Kafka");
        String connectionId = kafkaPanelService.open(
                required(request.getPanelHost(), "Kafka host 不能为空"),
                requiredPort(request.getPanelPort()),
                trimToNull(request.getPanelUsername()),
                trimToNull(request.getPanelPassword()),
                defaultValue(request.getPanelProtocol(), "plaintext"));
        try {
            Map<String, Object> health = kafkaPanelService.clusterHealth(connectionId);
            String clusterId = mapString(health, "clusterId");
            builder.panelDetectedVersion(clusterId)
                    .panelMessage("Kafka 连接成功");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("clusterId", clusterId);
            details.put("brokerCount", health.get("brokerCount"));
            details.put("controller", health.get("controller"));
            return details;
        } finally {
            kafkaPanelService.close(connectionId);
        }
    }

    private PanelConnectionDefinition buildJdbcDefinition(PanelDatasourceRequest request) {
        Map<String, Object> attributes = normalizeAttributes(request.getPanelAttributes());
        attributes.put(ATTR_DIALECT_TYPE, resolveDialectType(request));
        putIfNotBlank(attributes, ATTR_DRIVER_CLASS_NAME, request.getPanelDriverClassName());
        putIfNotBlank(attributes, ATTR_DRIVER_JAR_PATH, request.getPanelDriverJarPath());
        return PanelConnectionDefinition.builder()
                .connectionId(UUID.randomUUID().toString())
                .connectionName(defaultValue(request.getPanelConnectionName(), "panel-test"))
                .connectionType(PanelConnectionType.JDBC)
                .protocol(trimToNull(request.getPanelProtocol()))
                .host(required(request.getPanelHost(), "数据库 host 不能为空"))
                .port(requiredPort(request.getPanelPort()))
                .databaseName(trimToNull(request.getPanelDatabaseName()))
                .username(trimToNull(request.getPanelUsername()))
                .password(defaultValue(request.getPanelPassword(), ""))
                .enabled(true)
                .attributes(attributes)
                .build();
    }

    private void loadStore() {
        if (!Files.exists(storePath)) {
            return;
        }
        try {
            List<PanelDatasourceView> datasourceViews = objectMapper.readValue(storePath.toFile(), DATASOURCE_LIST_TYPE);
            datasourceViews.stream()
                    .map(this::decodeStoredView)
                    .forEach(item -> {
                        if (item != null && !StringUtils.isBlank(item.getPanelSourceId())) {
                            datasourceStore.put(item.getPanelSourceId(), item);
                        }
                    });
        } catch (IOException ignored) {
            datasourceStore.clear();
        }
    }

    private synchronized void persistStore() {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<PanelDatasourceView> values = listAll().stream()
                    .map(this::encodeStoredView)
                    .toList();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), values);
        } catch (IOException exception) {
            throw new IllegalStateException("保存 panel 数据源失败", exception);
        }
    }

    private PanelDatasourceView decodeStoredView(PanelDatasourceView view) {
        if (view == null) {
            return null;
        }
        return copyView(
                view,
                decryptSensitiveValue(view.getPanelPassword()),
                transformSensitiveValues(view.getPanelAttributes(), false));
    }

    private PanelDatasourceView encodeStoredView(PanelDatasourceView view) {
        if (view == null) {
            return null;
        }
        return copyView(
                view,
                encryptSensitiveValue(view.getPanelPassword()),
                transformSensitiveValues(view.getPanelAttributes(), true));
    }

    private PanelDatasourceView copyView(
            PanelDatasourceView source,
            String password,
            Map<String, Object> attributes) {
        return PanelDatasourceView.builder()
                .panelSourceId(source.getPanelSourceId())
                .panelConnectionId(source.getPanelConnectionId())
                .panelSourceType(source.getPanelSourceType())
                .panelConnectionName(source.getPanelConnectionName())
                .panelHost(source.getPanelHost())
                .panelPort(source.getPanelPort())
                .panelDatabaseName(source.getPanelDatabaseName())
                .panelUsername(source.getPanelUsername())
                .panelPassword(password)
                .panelProtocol(source.getPanelProtocol())
                .panelDialectType(source.getPanelDialectType())
                .panelDriverClassName(source.getPanelDriverClassName())
                .panelDriverJarName(source.getPanelDriverJarName())
                .panelDriverJarPath(source.getPanelDriverJarPath())
                .panelNote(source.getPanelNote())
                .panelFavorite(source.getPanelFavorite())
                .panelEnvironment(source.getPanelEnvironment())
                .panelOwner(source.getPanelOwner())
                .panelReadOnly(source.getPanelReadOnly())
                .panelSslEnabled(source.getPanelSslEnabled())
                .panelConnectTimeoutMillis(source.getPanelConnectTimeoutMillis())
                .panelSocketTimeoutMillis(source.getPanelSocketTimeoutMillis())
                .panelCharset(source.getPanelCharset())
                .panelLastLatencyMs(source.getPanelLastLatencyMs())
                .panelLastConnectedAt(source.getPanelLastConnectedAt())
                .panelDetectedVersion(source.getPanelDetectedVersion())
                .panelAttributes(attributes)
                .panelUpdatedAt(source.getPanelUpdatedAt())
                .build();
    }

    private byte[] deriveEncryptionKey(String configuredKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(defaultValue(configuredKey, "panel-default-key").getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("初始化数据源加密密钥失败", exception);
        }
    }

    private String encryptSensitiveValue(String rawValue) {
        if (rawValue == null || rawValue.isEmpty() || rawValue.startsWith(ENCRYPTED_PREFIX)) {
            return rawValue;
        }
        try {
            byte[] iv = new byte[12];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(rawValue.getBytes(StandardCharsets.UTF_8));
            return ENCRYPTED_PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + "::"
                    + Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception exception) {
            throw new IllegalStateException("加密数据源敏感信息失败", exception);
        }
    }

    private String decryptSensitiveValue(String value) {
        if (value == null || value.isEmpty() || !value.startsWith(ENCRYPTED_PREFIX)) {
            return value;
        }
        try {
            String[] parts = value.substring(ENCRYPTED_PREFIX.length()).split("::", 2);
            if (parts.length != 2) {
                return value;
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipherBytes = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }

    private Map<String, Object> normalizeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            if (key != null) {
                normalized.put(String.valueOf(key), deepCopyValue(value));
            }
        });
        return normalized;
    }

    private Map<String, Object> transformSensitiveValues(Map<String, Object> attributes, boolean encrypt) {
        Map<String, Object> normalized = normalizeAttributes(attributes);
        Map<String, Object> transformed = new LinkedHashMap<>();
        normalized.forEach((key, value) -> transformed.put(key, transformSensitiveValue(key, value, encrypt)));
        return transformed;
    }

    private Object transformSensitiveValue(String key, Object value, boolean encrypt) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nested = new LinkedHashMap<>();
            mapValue.forEach((nestedKey, nestedValue) -> {
                if (nestedKey != null) {
                    nested.put(String.valueOf(nestedKey),
                            transformSensitiveValue(String.valueOf(nestedKey), nestedValue, encrypt));
                }
            });
            return nested;
        }
        if (value instanceof List<?> listValue) {
            List<Object> nested = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                nested.add(transformSensitiveValue(key, item, encrypt));
            }
            return nested;
        }
        if (value instanceof String stringValue && isSensitiveKey(key)) {
            return encrypt ? encryptSensitiveValue(stringValue) : decryptSensitiveValue(stringValue);
        }
        return value;
    }

    private Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> copy = new LinkedHashMap<>();
            mapValue.forEach((nestedKey, nestedValue) -> {
                if (nestedKey != null) {
                    copy.put(String.valueOf(nestedKey), deepCopyValue(nestedValue));
                }
            });
            return copy;
        }
        if (value instanceof List<?> listValue) {
            List<Object> copy = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("privatekey")
                || normalized.contains("apikey")
                || normalized.contains("accesskey")
                || normalized.contains("passphrase");
    }

    private <T> T requireProvider(ObjectProvider<T> provider, String label) {
        T service = provider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException(label + " 支持未启用或缺少运行时依赖");
        }
        return service;
    }

    private String resolveSourceType(PanelDatasourceRequest request) {
        return defaultValue(request.getPanelSourceType(), "JDBC").toUpperCase(Locale.ROOT);
    }

    private String resolveDialectType(PanelDatasourceRequest request) {
        if (!"JDBC".equalsIgnoreCase(resolveSourceType(request))) {
            return trimToNull(request.getPanelDialectType());
        }
        return defaultValue(request.getPanelDialectType(), "MYSQL").toUpperCase(Locale.ROOT);
    }

    private String resolveTestProtocol(PanelDatasourceRequest request) {
        String configured = trimToNull(request.getPanelProtocol());
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        return switch (resolveSourceType(request)) {
            case "REDIS" -> "redis";
            case "MONGODB" -> "mongodb";
            case "ZOOKEEPER" -> "zookeeper";
            case "PROMETHEUS", "INFLUXDB2", "INFLUXDB3", "NACOS" -> "http";
            case "NGINX" -> "ssh";
            case "DOCKER" -> "tcp";
            case "KAFKA" -> "plaintext";
            default -> "jdbc";
        };
    }

    private String sanitizeDriverFileName(String originalFilename) {
        String fileName = StringUtils.isBlank(originalFilename) ? "driver.jar" : originalFilename.trim();
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private String detectDialectType(String fileName) {
        String lowerCaseFileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lowerCaseFileName.contains("oracle") || lowerCaseFileName.contains("ojdbc")) {
            return "ORACLE";
        }
        if (lowerCaseFileName.contains("postgres")) {
            return "POSTGRESQL";
        }
        if (lowerCaseFileName.contains("sqlserver") || lowerCaseFileName.contains("mssql")) {
            return "SQLSERVER";
        }
        if (lowerCaseFileName.contains("clickhouse")) {
            return "CLICKHOUSE";
        }
        if (lowerCaseFileName.contains("mariadb")) {
            return "MARIADB";
        }
        return "MYSQL";
    }

    private String resolveDriverClassName(String panelDialectType, String fileName) {
        String dialectType = defaultValue(panelDialectType, detectDialectType(fileName)).toUpperCase(Locale.ROOT);
        return switch (dialectType) {
            case "ORACLE" -> "oracle.jdbc.OracleDriver";
            case "POSTGRESQL" -> "org.postgresql.Driver";
            case "SQLSERVER" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "CLICKHOUSE" -> "com.clickhouse.jdbc.ClickHouseDriver";
            case "MARIADB" -> "org.mariadb.jdbc.Driver";
            default -> "com.mysql.cj.jdbc.Driver";
        };
    }

    private String required(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private int requiredPort(Integer port) {
        if (port == null || port <= 0) {
            throw new IllegalArgumentException("端口必须大于 0");
        }
        return port;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            target.put(key, trimmed);
        }
    }

    private String stringAttribute(PanelDatasourceRequest request, String key, String fallback) {
        Object value = normalizeAttributes(request.getPanelAttributes()).get(key);
        if (value == null) {
            return trimToNull(fallback);
        }
        String text = trimToNull(String.valueOf(value));
        return text == null ? trimToNull(fallback) : text;
    }

    private int intAttribute(PanelDatasourceRequest request, String key, int fallback) {
        Object value = normalizeAttributes(request.getPanelAttributes()).get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return parseIntOrDefault(String.valueOf(value), fallback);
    }

    private int parseIntOrDefault(String value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String mapString(Object object, String key) {
        if (!(object instanceof Map<?, ?> mapValue)) {
            return null;
        }
        Object value = mapValue.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int mapListSize(Object object, String key) {
        if (!(object instanceof Map<?, ?> mapValue)) {
            return 0;
        }
        Object value = mapValue.get(key);
        return value instanceof List<?> listValue ? listValue.size() : 0;
    }

    private String extractRootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return StringUtils.isBlank(message) ? throwable.getClass().getSimpleName() : message;
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private String defaultValue(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value.trim();
    }
}
