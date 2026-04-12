package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.model.JdbcTableStructure;
import com.chua.starter.panel.service.PanelDocumentService;
import com.chua.starter.panel.service.JdbcPanelService;

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
}
