package com.chua.starter.spider.flow.handler;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.flow.NodeHandler;
import com.chua.starter.spider.flow.SpiderFlowContext;
import com.chua.starter.spider.flow.SpiderFlowUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 数据库执行节点处理器：通过 Spring {@link JdbcTemplate} 执行 SQL，结果按操作类型返回。
 *
 * <p>config JSON 结构：</p>
 * <pre>
 * {
 *   "dataSource": "master",        // 数据源 bean 名称（默认 master）
 *   "operation":  "select",         // select / selectOne / selectInt / update
 *   "sql":        "select * from t where id = #${id}#",
 *   "params":     { "id": "${lastId}" },
 *   "saveAs":     "rows"
 * }
 * </pre>
 *
 * <p>sql 中参数占位符用 {@code #${name}#}（与 spider-flow 约定一致），
 * params 中可引用 ctx 变量。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class DbNodeHandler implements NodeHandler {

    /**
     * 默认数据源 bean 名
     */
    private static final String DEFAULT_DATA_SOURCE = "master";

    /**
     * 操作类型：select（列表）
     */
    private static final String OP_SELECT = "select";

    /**
     * 操作类型：selectOne（单行）
     */
    private static final String OP_SELECT_ONE = "selectOne";

    /**
     * 操作类型：selectInt（单 int）
     */
    private static final String OP_SELECT_INT = "selectInt";

    /**
     * 操作类型：update（写操作）
     */
    private static final String OP_UPDATE = "update";

    /**
     * 节点类型编码
     */
    private static final String TYPE = "db";

    /**
     * 占位符前缀
     */
    private static final String PLACEHOLDER_PREFIX = "#${";

    /**
     * 占位符后缀
     */
    private static final String PLACEHOLDER_SUFFIX = "}#";

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 数据源 bean 容器（key = bean 名称）
     */
    @Autowired(required = false)
    private Map<String, JdbcTemplate> jdbcTemplates;

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(SpiderFlowNode node, SpiderFlowContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getFlowNodeConfig());
        String dataSource = SpiderFlowUtils.defaultIfEmpty(asString(config.get("dataSource")), DEFAULT_DATA_SOURCE);
        String operation = SpiderFlowUtils.defaultIfEmpty(asString(config.get("operation")), OP_SELECT);
        String sql = render(asString(config.get("sql")), context);
        Map<String, Object> params = (Map<String, Object>) config.getOrDefault("params", Map.of());
        Map<String, Object> resolved = resolveParams(params, context);
        String saveAs = asString(config.get("saveAs"));

        JdbcTemplate jdbc = resolveJdbc(dataSource);
        if (jdbc == null) {
            log.warn("[Db] 数据源 {} 不存在，跳过执行", dataSource);
            return "no datasource";
        }

        Object result;
        switch (operation.toLowerCase()) {
            case OP_SELECT -> result = jdbc.queryForList(sql, resolved.values().toArray());
            case OP_SELECT_ONE -> {
                List<Map<String, Object>> rows = jdbc.queryForList(sql, resolved.values().toArray());
                result = rows.isEmpty() ? null : rows.get(0);
            }
            case OP_SELECT_INT -> result = jdbc.queryForObject(sql, Integer.class, resolved.values().toArray());
            case OP_UPDATE -> result = jdbc.update(sql, resolved.values().toArray());
            default -> result = jdbc.queryForList(sql, resolved.values().toArray());
        }
        if (saveAs != null) {
            context.putVar(saveAs, result);
        }
        log.info("[Flow {}] DB[{}] {} -> {} rows", context.getExecutionNo(), operation, dataSource,
                result instanceof List<?> list ? list.size() : 1);
        return "ok";
    }

    /**
     * 解析 config JSON。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[Db] config JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 解析数据源 bean。
     */
    private JdbcTemplate resolveJdbc(String dataSource) {
        if (jdbcTemplates == null || jdbcTemplates.isEmpty()) {
            return null;
        }
        return jdbcTemplates.get(dataSource);
    }

    /**
     * 替换 sql 中的 #${var}# 占位符为 ?（同时把变量收集到 ordered params）。
     */
    private String render(String template, SpiderFlowContext context) {
        if (template == null || !template.contains(PLACEHOLDER_PREFIX)) {
            return template;
        }
        return template.replaceAll("#\\$\\{[^}]+}#", "?");
    }

    /**
     * 把 ctx 变量替换到 params 中。
     */
    private Map<String, Object> resolveParams(Map<String, Object> params, SpiderFlowContext context) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        params.forEach((k, v) -> {
            if (v instanceof String s && s.startsWith("${") && s.endsWith("}")) {
                String key = s.substring(2, s.length() - 1);
                out.put(k, context.getVar(key));
            } else {
                out.put(k, v);
            }
        });
        return out;
    }

    /**
     * Object 转 String。
     */
    private String asString(Object o) {
        return o == null ? null : o.toString();
    }
}


