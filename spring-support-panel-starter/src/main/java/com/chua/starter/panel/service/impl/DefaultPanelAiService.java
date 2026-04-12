package com.chua.starter.panel.service.impl;

import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.panel.config.PanelProperties;
import com.chua.starter.panel.model.JdbcTableStructure;
import com.chua.starter.panel.model.PanelAiSqlRequest;
import com.chua.starter.panel.service.JdbcPanelService;
import com.chua.starter.panel.service.PanelAiService;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 默认 AI 服务实现。
 */
public class DefaultPanelAiService implements PanelAiService {

    private final JdbcPanelService jdbcPanelService;
    private final PanelProperties panelProperties;
    private final ObjectProvider<ChatClient> chatClientProvider;

    public DefaultPanelAiService(
            JdbcPanelService jdbcPanelService,
            PanelProperties panelProperties,
            ObjectProvider<ChatClient> chatClientProvider) {
        this.jdbcPanelService = jdbcPanelService;
        this.panelProperties = panelProperties;
        this.chatClientProvider = chatClientProvider;
    }

    @Override
    public String explainJdbcStructure(String connectionId, String catalog, String schema, String tableName) {
        JdbcTableStructure structure = jdbcPanelService.tableStructure(connectionId, catalog, schema, tableName);
        return "表 `" + tableName + "` 共 "
                + structure.getColumns().size()
                + " 个字段，主键为 "
                + (structure.getPrimaryKeys().isEmpty() ? "无" : String.join(", ", structure.getPrimaryKeys()))
                + "，索引数量为 "
                + structure.getIndexes().size()
                + "。";
    }

    @Override
    public String explainSql(String connectionId, String sql) {
        String normalized = sql == null ? "" : sql.trim().toLowerCase();
        if (normalized.startsWith("select")) {
            return "该 SQL 为查询语句，建议优先关注返回列范围、过滤条件和 limit 设置。";
        }
        if (normalized.startsWith("update")) {
            return "该 SQL 为更新语句，执行前建议确认 where 条件是否精准。";
        }
        if (normalized.startsWith("delete")) {
            return "该 SQL 为删除语句，执行前建议确认 where 条件并做好备份。";
        }
        if (normalized.startsWith("insert")) {
            return "该 SQL 为插入语句，建议确认字段顺序与默认值策略。";
        }
        return "暂未识别 SQL 类型，请结合业务语义人工确认。";
    }

    @Override
    public String generateSql(String connectionId, PanelAiSqlRequest request) {
        String prompt = request == null ? "" : safeText(request.getPrompt());
        if (prompt.isEmpty()) {
            return "-- 请输入自然语言需求";
        }

        String fullPrompt = """
                你是数据库客户端内置 SQL 助手。
                请根据用户中文需求输出一段可直接执行的 SQL，不要输出解释文字。
                如果需要限制结果量，默认使用 limit 1000。
                已知上下文：
                %s

                用户需求：
                %s
                """.formatted(buildSqlPromptContext(connectionId, request), prompt);

        String aiResult = tryChatClient(fullPrompt);
        if (aiResult != null && !aiResult.isBlank()) {
            return aiResult.trim();
        }
        return buildFallbackSql(request);
    }

    private String buildSqlPromptContext(String connectionId, PanelAiSqlRequest request) {
        StringBuilder builder = new StringBuilder();
        if (request != null && request.getTableNames() != null && !request.getTableNames().isEmpty()) {
            builder.append("候选表：").append(String.join(", ", request.getTableNames())).append('\n');
        }
        try {
            if (request != null && request.getTableNames() != null && !request.getTableNames().isEmpty()) {
                String tableName = request.getTableNames().get(0);
                JdbcTableStructure structure = jdbcPanelService.tableStructure(connectionId, null, null, tableName);
                builder.append("示例表结构：").append(tableName).append('\n');
                structure.getColumns().forEach(column ->
                        builder.append("- ")
                                .append(column.get("name"))
                                .append(" : ")
                                .append(column.get("type"))
                                .append('\n'));
            }
        } catch (Exception ignored) {
            builder.append("示例表结构暂不可用\n");
        }
        return builder.toString().trim();
    }

    private String tryChatClient(String prompt) {
        if (!panelProperties.isAiEnabled()) {
            return null;
        }
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return null;
        }
        return chatClient.chatSync(prompt);
    }

    private String buildFallbackSql(PanelAiSqlRequest request) {
        String tableName = request == null || request.getTableNames() == null || request.getTableNames().isEmpty()
                ? "your_table"
                : request.getTableNames().get(0);
        return """
                -- AI Starter 不可用，已生成默认模板，请按实际字段补充
                select *
                from %s
                limit 1000;
                """.formatted(tableName);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
