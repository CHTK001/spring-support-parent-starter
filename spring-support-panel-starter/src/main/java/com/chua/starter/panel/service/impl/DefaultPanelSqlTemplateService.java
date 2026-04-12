package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.model.PanelSqlTemplateRequest;
import com.chua.starter.panel.service.PanelSqlTemplateService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * 默认面板 SQL 模板服务。
 */
public class DefaultPanelSqlTemplateService implements PanelSqlTemplateService {

    @Override
    public String generateTemplate(String panelConnectionId, PanelSqlTemplateRequest request) {
        String panelActionType = normalize(request.getPanelActionType());
        String panelFullTableName = buildFullTableName(
                request.getPanelCatalogName(),
                request.getPanelSchemaName(),
                request.getPanelTableName());
        int panelPreviewLimit = request.getPanelPreviewLimit() == null || request.getPanelPreviewLimit() <= 0
                ? 1000
                : request.getPanelPreviewLimit();

        return switch (panelActionType) {
            case "select" -> "select * from " + panelFullTableName + " limit " + panelPreviewLimit + ";";
            case "count" -> "select count(*) as total from " + panelFullTableName + ";";
            case "truncate" -> "truncate table " + panelFullTableName + ";";
            case "clear" -> "delete from " + panelFullTableName + ";";
            case "drop" -> "drop table " + panelFullTableName + ";";
            case "backup" -> buildBackupSql(panelFullTableName, request);
            default -> throw new IllegalArgumentException("不支持的面板动作: " + panelActionType);
        };
    }

    private String buildBackupSql(String panelFullTableName, PanelSqlTemplateRequest request) {
        String panelBackupTableName = request.getPanelBackupTableName();
        if (panelBackupTableName == null || panelBackupTableName.trim().isEmpty()) {
            panelBackupTableName = request.getPanelTableName()
                    + "_panel_backup_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        }

        return "create table " + panelBackupTableName + " as\n"
                + "select * from " + panelFullTableName + ";";
    }

    private String buildFullTableName(String panelCatalogName, String panelSchemaName, String panelTableName) {
        StringJoiner stringJoiner = new StringJoiner(".");
        if (panelCatalogName != null && !panelCatalogName.trim().isEmpty()) {
            stringJoiner.add(panelCatalogName.trim());
        }
        if (panelSchemaName != null && !panelSchemaName.trim().isEmpty()) {
            stringJoiner.add(panelSchemaName.trim());
        }
        if (panelTableName != null && !panelTableName.trim().isEmpty()) {
            stringJoiner.add(panelTableName.trim());
        }
        return stringJoiner.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
