package com.chua.starter.panel.service.impl;

import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.panel.config.PanelProperties;
import com.chua.starter.panel.model.PanelDatasourceRequest;
import com.chua.starter.panel.model.PanelDriverUploadView;
import com.chua.starter.panel.model.PanelDatasourceView;
import com.chua.starter.panel.service.PanelDatasourceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 Panel 数据源服务。
 */
public class DefaultPanelDatasourceService implements PanelDatasourceService {

    private static final TypeReference<List<PanelDatasourceView>> DATASOURCE_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path storePath;
    private final Path driverStorePath;
    private final Map<String, PanelDatasourceView> datasourceStore = new ConcurrentHashMap<>();

    public DefaultPanelDatasourceService(PanelProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.storePath = Paths.get(properties.getDatasourceStorePath()).toAbsolutePath().normalize();
        this.driverStorePath = Paths.get(properties.getDatasourceDriverStorePath()).toAbsolutePath().normalize();
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
                .panelSourceType(defaultValue(request.getPanelSourceType(), "JDBC"))
                .panelConnectionName(defaultValue(request.getPanelConnectionName(), "未命名数据源"))
                .panelHost(trimToNull(request.getPanelHost()))
                .panelPort(request.getPanelPort())
                .panelDatabaseName(trimToNull(request.getPanelDatabaseName()))
                .panelUsername(trimToNull(request.getPanelUsername()))
                .panelPassword(defaultValue(request.getPanelPassword(), ""))
                .panelProtocol(trimToNull(request.getPanelProtocol()))
                .panelDialectType(defaultValue(request.getPanelDialectType(), "MYSQL"))
                .panelDriverClassName(trimToNull(request.getPanelDriverClassName()))
                .panelDriverJarName(trimToNull(request.getPanelDriverJarName()))
                .panelDriverJarPath(trimToNull(request.getPanelDriverJarPath()))
                .panelNote(trimToNull(request.getPanelNote()))
                .panelFavorite(Boolean.TRUE.equals(request.getPanelFavorite()))
                .panelUpdatedAt(OffsetDateTime.now().toString())
                .build();
        datasourceStore.put(panelSourceId, panelDatasourceView);
        persistStore();
        return panelDatasourceView;
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

    private void loadStore() {
        if (!Files.exists(storePath)) {
            return;
        }
        try {
            List<PanelDatasourceView> datasourceViews = objectMapper.readValue(storePath.toFile(), DATASOURCE_LIST_TYPE);
            datasourceViews.forEach(item -> {
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
            List<PanelDatasourceView> values = new ArrayList<>(listAll());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), values);
        } catch (IOException exception) {
            throw new IllegalStateException("保存 panel 数据源失败", exception);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private String defaultValue(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value.trim();
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
}
