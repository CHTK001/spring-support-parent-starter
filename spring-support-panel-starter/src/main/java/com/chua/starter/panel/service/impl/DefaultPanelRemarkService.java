package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.model.PanelRemarkRequest;
import com.chua.starter.panel.model.PanelRemarkView;
import com.chua.starter.panel.service.PanelRemarkService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认面板备注服务。
 */
public class DefaultPanelRemarkService implements PanelRemarkService {

    private final Map<String, PanelRemarkView> remarkStore = new ConcurrentHashMap<>();

    @Override
    public List<PanelRemarkView> listByConnectionId(String panelConnectionId) {
        return remarkStore.values().stream()
                .filter(item -> panelConnectionId != null && panelConnectionId.equals(item.getPanelConnectionId()))
                .sorted((left, right) -> left.getPanelRemarkKey().compareToIgnoreCase(right.getPanelRemarkKey()))
                .toList();
    }

    @Override
    public PanelRemarkView saveRemark(PanelRemarkRequest request) {
        String panelRemarkKey = buildRemarkKey(request);
        PanelRemarkView panelRemarkView = PanelRemarkView.builder()
                .panelRemarkKey(panelRemarkKey)
                .panelConnectionId(request.getPanelConnectionId())
                .panelNodeType(request.getPanelNodeType())
                .panelCatalogName(request.getPanelCatalogName())
                .panelSchemaName(request.getPanelSchemaName())
                .panelTableName(request.getPanelTableName())
                .panelColumnName(request.getPanelColumnName())
                .panelRemarkContent(request.getPanelRemarkContent())
                .build();
        remarkStore.put(panelRemarkKey, panelRemarkView);
        return panelRemarkView;
    }

    private String buildRemarkKey(PanelRemarkRequest request) {
        return String.join("::",
                normalize(request.getPanelConnectionId()),
                normalize(request.getPanelNodeType()),
                normalize(request.getPanelCatalogName()),
                normalize(request.getPanelSchemaName()),
                normalize(request.getPanelTableName()),
                normalize(request.getPanelColumnName()));
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? "__panel__" : value.trim();
    }
}
