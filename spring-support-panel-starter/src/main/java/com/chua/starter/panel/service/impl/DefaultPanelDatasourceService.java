package com.chua.starter.panel.service.impl;

import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.panel.config.PanelProperties;
import com.chua.starter.panel.model.PanelDatasourceRequest;
import com.chua.starter.panel.model.PanelDatasourceView;
import com.chua.starter.panel.service.PanelDatasourceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private final Map<String, PanelDatasourceView> datasourceStore = new ConcurrentHashMap<>();

    public DefaultPanelDatasourceService(PanelProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.storePath = Paths.get(properties.getDatasourceStorePath()).toAbsolutePath().normalize();
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
                .panelNote(trimToNull(request.getPanelNote()))
                .panelFavorite(Boolean.TRUE.equals(request.getPanelFavorite()))
                .panelUpdatedAt(OffsetDateTime.now().toString())
                .build();
        datasourceStore.put(panelSourceId, panelDatasourceView);
        persistStore();
        return panelDatasourceView;
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
}
