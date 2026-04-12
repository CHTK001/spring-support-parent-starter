package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.model.JdbcTableStructure;
import com.chua.starter.panel.model.PanelDatabaseDocumentView;
import com.chua.starter.panel.model.PanelDatabaseTableDocumentView;
import com.chua.starter.panel.service.JdbcPanelService;
import com.chua.starter.panel.service.PanelDocumentService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 默认文档服务实现。
 */
public class DefaultPanelDocumentService implements PanelDocumentService {

    private final JdbcPanelService jdbcPanelService;

    public DefaultPanelDocumentService(JdbcPanelService jdbcPanelService) {
        this.jdbcPanelService = jdbcPanelService;
    }

    @Override
    public String buildJdbcTableDocument(String connectionId, String catalog, String schema, String tableName) {
        JdbcTableStructure structure = jdbcPanelService.tableStructure(connectionId, catalog, schema, tableName);
        StringBuilder builder = new StringBuilder();
        builder.append("# 表结构文档\n\n");
        builder.append("- 数据库: ").append(catalog == null ? "-" : catalog).append("\n");
        builder.append("- Schema: ").append(schema == null ? "-" : schema).append("\n");
        builder.append("- 表名: ").append(structure.getTableName()).append("\n");
        builder.append("- 主键: ").append(
                structure.getPrimaryKeys() == null || structure.getPrimaryKeys().isEmpty()
                        ? "-"
                        : String.join(", ", structure.getPrimaryKeys())
        ).append("\n\n");

        builder.append("## 字段说明\n\n");
        builder.append("| 字段 | 类型 | 注释 |\n");
        builder.append("| --- | --- | --- |\n");
        for (var column : structure.getColumns()) {
            builder.append("| ")
                    .append(column.getOrDefault("name", "-"))
                    .append(" | ")
                    .append(column.getOrDefault("type", "-"))
                    .append(" | ")
                    .append(column.getOrDefault("comment", "-"))
                    .append(" |\n");
        }

        builder.append("\n## 索引\n\n");
        if (structure.getIndexes() == null || structure.getIndexes().isEmpty()) {
            builder.append("- 无\n");
        } else {
            for (var index : structure.getIndexes()) {
                builder.append("- ")
                        .append(index.getOrDefault("name", "-"))
                        .append(" -> ")
                        .append(index.getOrDefault("column", "-"))
                        .append("\n");
            }
        }
        return builder.toString();
    }

    @Override
    public PanelDatabaseDocumentView buildJdbcDatabaseDocument(String connectionId, String catalog) {
        List<PanelDatabaseTableDocumentView> tables = new ArrayList<>();
        jdbcPanelService.listCatalogTree(connectionId).stream()
                .filter(node -> isTargetCatalog(node.getCatalogName(), catalog))
                .flatMap(node -> node.getChildren().stream())
                .flatMap(node -> node.getChildren().stream())
                .forEach(tableNode -> {
                    JdbcTableStructure structure = jdbcPanelService.tableStructure(
                            connectionId,
                            tableNode.getCatalogName(),
                            tableNode.getSchemaName(),
                            tableNode.getTableName());
                    tables.add(PanelDatabaseTableDocumentView.builder()
                            .panelCatalogName(tableNode.getCatalogName())
                            .panelSchemaName(tableNode.getSchemaName())
                            .panelTableName(tableNode.getTableName())
                            .panelTableComment(structure.getTableComment())
                            .panelPrimaryKeys(structure.getPrimaryKeys())
                            .panelColumns(structure.getColumns())
                            .panelIndexes(structure.getIndexes())
                            .build());
                });

        long schemaCount = tables.stream()
                .map(PanelDatabaseTableDocumentView::getPanelSchemaName)
                .map(value -> value == null ? "__default__" : value)
                .distinct()
                .count();

        return PanelDatabaseDocumentView.builder()
                .panelCatalogName(catalog)
                .panelSchemaCount(schemaCount)
                .panelTableCount(tables.size())
                .panelGeneratedAt(LocalDateTime.now().toString())
                .panelTables(tables.stream()
                        .sorted((left, right) -> compareTableDocument(left, right))
                        .collect(Collectors.toList()))
                .build();
    }

    private boolean isTargetCatalog(String sourceCatalog, String targetCatalog) {
        String left = sourceCatalog == null ? "" : sourceCatalog.trim();
        String right = targetCatalog == null ? "" : targetCatalog.trim();
        return left.equalsIgnoreCase(right);
    }

    private int compareTableDocument(
            PanelDatabaseTableDocumentView left,
            PanelDatabaseTableDocumentView right) {
        int schemaResult = normalize(left.getPanelSchemaName())
                .compareTo(normalize(right.getPanelSchemaName()));
        if (schemaResult != 0) {
            return schemaResult;
        }
        return normalize(left.getPanelTableName()).compareTo(normalize(right.getPanelTableName()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
