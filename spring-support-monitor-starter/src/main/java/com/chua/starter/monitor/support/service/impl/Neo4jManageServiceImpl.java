package com.chua.starter.monitor.support.service.impl;

import com.chua.starter.monitor.support.middleware.Neo4jClient;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.monitor.support.model.Neo4jLabelVO;
import com.chua.starter.monitor.support.model.Neo4jQueryResultVO;
import com.chua.starter.monitor.support.model.Neo4jRelationshipVO;
import com.chua.starter.monitor.support.model.Neo4jTestVO;
import com.chua.starter.monitor.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.monitor.support.model.dto.Neo4jQueryDTO;
import com.chua.starter.monitor.support.service.Neo4jManageService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Neo4j 管理实现：标签与关系类型名走 {@code db.labels()/db.relationshipTypes()}，
 * 计数走一句聚合扫描，扫描代价过高时宁缺不滥。
 *
 * <p>Neo4j 没有「按标签取节点数」的常量级接口（5.x 移除了 {@code sizeOfLabel}），逐标签统计只能是
 * 全图扫描。因此在扫描前先取一次全库节点/关系总量，超过 {@value #SCAN_BUDGET} 就跳过聚合、
 * 统计列留空，避免管理员刷新一次页面就把图库 CPU 打满；聚合语句本身失败同样只留空，
 * 不让标签列表整体失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jManageServiceImpl implements Neo4jManageService {

    /**
     * 查询结果一次返回的行数上限，超过即截断并回显 truncated
     */
    private static final int QUERY_ROW_LIMIT = 2000;

    /**
     * 允许做逐标签/逐类型聚合扫描的图规模上限（节点数或关系数）
     */
    private static final long SCAN_BUDGET = 2_000_000L;

    private static final String NODE_TOTAL = "MATCH (n) RETURN count(n) AS total";

    private static final String REL_TOTAL = "MATCH ()-[r]->() RETURN count(r) AS total";

    private static final String LABEL_NAMES = "CALL db.labels() YIELD label RETURN label AS name";

    private static final String REL_TYPE_NAMES =
            "CALL db.relationshipTypes() YIELD relationshipType RETURN relationshipType AS name";

    private static final String LABEL_NODE_COUNTS =
            "MATCH (n)\nUNWIND labels(n) AS name\nRETURN name, count(n) AS count";

    private static final String LABEL_PROPERTY_COUNTS =
            "MATCH (n)\nUNWIND labels(n) AS name\nUNWIND keys(n) AS key\n"
                    + "WITH DISTINCT name, key\nRETURN name, count(key) AS count";

    private static final String REL_COUNTS = "MATCH ()-[r]->()\nRETURN type(r) AS name, count(r) AS count";

    private static final String REL_PROPERTY_COUNTS =
            "MATCH ()-[r]->()\nUNWIND keys(r) AS key\nWITH DISTINCT type(r) AS name, key\n"
                    + "RETURN name, count(key) AS count";

    /**
     * Neo4j 客户端
     */
    private final Neo4jClient client;

    @Override
    public Neo4jTestVO test(MiddlewareTestDTO request) {
        Neo4jClient.Target target = testTarget(request);
        Neo4jClient.Server server;
        try {
            server = client.probeServer(target);
        } catch (IllegalStateException e) {
            return new Neo4jTestVO(false, null, null, e.getMessage());
        }
        return new Neo4jTestVO(true, emptyToNull(server.version()), emptyToNull(server.edition()), "连接成功");
    }

    @Override
    public List<Neo4jLabelVO> labels(Long datasourceId) {
        Neo4jClient.Target target = client.target(datasourceId);
        List<String> names = names(target, LABEL_NAMES);
        if (names.isEmpty()) {
            return List.of();
        }
        boolean withinBudget = withinBudget(target, NODE_TOTAL);
        Map<String, Long> nodeCounts = withinBudget ? counts(target, LABEL_NODE_COUNTS) : Map.of();
        Map<String, Long> propertyCounts = withinBudget ? counts(target, LABEL_PROPERTY_COUNTS) : Map.of();
        List<Neo4jLabelVO> result = new ArrayList<>(names.size());
        for (String name : names) {
            result.add(new Neo4jLabelVO(name, nodeCounts.get(name), propertyCounts.get(name)));
        }
        return result;
    }

    @Override
    public List<Neo4jRelationshipVO> relationships(Long datasourceId) {
        Neo4jClient.Target target = client.target(datasourceId);
        List<String> names = names(target, REL_TYPE_NAMES);
        if (names.isEmpty()) {
            return List.of();
        }
        boolean withinBudget = withinBudget(target, REL_TOTAL);
        Map<String, Long> relCounts = withinBudget ? counts(target, REL_COUNTS) : Map.of();
        Map<String, Long> propertyCounts = withinBudget ? counts(target, REL_PROPERTY_COUNTS) : Map.of();
        List<Neo4jRelationshipVO> result = new ArrayList<>(names.size());
        for (String name : names) {
            result.add(new Neo4jRelationshipVO(name, relCounts.get(name), propertyCounts.get(name)));
        }
        return result;
    }

    @Override
    public Neo4jQueryResultVO query(Neo4jQueryDTO request) {
        String cypher = requireText(request == null ? null : request.cypher(), "Cypher 语句不能为空");
        Neo4jClient.Target target = client.target(request.datasourceId());
        long start = System.nanoTime();
        Neo4jClient.Result result = client.run(target, cypher);
        long elapsed = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
        List<Map<String, Object>> rows = new ArrayList<>(Math.min(result.rows().size(), QUERY_ROW_LIMIT));
        for (int i = 0; i < result.rows().size() && i < QUERY_ROW_LIMIT; i++) {
            rows.add(result.rows().get(i));
        }
        return new Neo4jQueryResultVO(cypher, elapsed, rows, rows.size(), result.rows().size(),
                result.rows().size() > QUERY_ROW_LIMIT);
    }

    /**
     * 解析测试目标：优先库中数据源，其次即席填写的地址，最后落到第一个 neo4j 数据源。
     *
     * @param request 测试请求，可为空
     * @return 访问目标
     */
    private Neo4jClient.Target testTarget(MiddlewareTestDTO request) {
        if (request == null || request.host() == null || request.host().isBlank()) {
            return client.target(request == null ? null : request.datasourceId());
        }
        if (request.datasourceId() != null && request.datasourceId() > 0L) {
            return client.target(request.datasourceId());
        }
        DataSourceVO adhoc = new DataSourceVO(null, "ad-hoc", Neo4jClient.DS_TYPE, request.host().trim(),
                request.port(), request.contextPath(), request.username(), request.password(), null, 1, null, null, null,
                null, null, null, null, null, null);
        return client.target(adhoc);
    }

    /**
     * 取名单列的第一列值。
     *
     * @param target 访问目标
     * @param cypher 只返回一列名称的语句
     * @return 名称列表，保持服务端顺序
     */
    private List<String> names(Neo4jClient.Target target, String cypher) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : client.run(target, cypher).rows()) {
            String name = text(row.get("name"));
            if (name.isEmpty()) {
                name = text(firstValue(row));
            }
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 执行统计语句，失败或超预算时返回空表让对应列留白。
     *
     * @param target 访问目标
     * @param cypher 形如 {@code RETURN name, count AS count} 的统计语句
     * @return 名称 → 计数
     */
    private Map<String, Long> counts(Neo4jClient.Target target, String cypher) {
        Map<String, Long> counts = new LinkedHashMap<>();
        try {
            for (Map<String, Object> row : client.run(target, cypher).rows()) {
                String name = text(row.get("name"));
                Long count = longOrNull(row.get("count"));
                if (!name.isEmpty() && count != null) {
                    counts.put(name, count);
                }
            }
        } catch (IllegalStateException e) {
            log.debug("[neo4j] 统计失败: {}", e.getMessage());
        }
        return counts;
    }

    /**
     * 图规模是否在允许聚合的预算内。
     *
     * @param target     访问目标
     * @param totalCypher 取总量的语句
     * @return 超出预算或总量读不到时为 false
     */
    private boolean withinBudget(Neo4jClient.Target target, String totalCypher) {
        try {
            List<Map<String, Object>> rows = client.run(target, totalCypher).rows();
            Long total = rows.isEmpty() ? null : longOrNull(firstValue(rows.get(0)));
            if (total == null) {
                return false;
            }
            if (total > SCAN_BUDGET) {
                log.info("[neo4j] 图规模 {} 超过聚合预算 {}，跳过标签/关系统计", total, SCAN_BUDGET);
                return false;
            }
            return true;
        } catch (IllegalStateException e) {
            log.debug("[neo4j] 读取图规模失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 要求字符串字段非空。
     *
     * @param value   原值
     * @param message 为空时的提示
     * @return 去空白后的值
     * @throws IllegalStateException 值为空
     */
    private static String requireText(String value, String message) {
        String text = trim(value);
        if (text.isEmpty()) {
            throw new IllegalStateException(message);
        }
        return text;
    }

    /**
     * 取一行的第一个值，兼容服务端未按要求起别名。
     *
     * @param row 行数据
     * @return 首个值，空行为 null
     */
    private static Object firstValue(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        return row.values().iterator().next();
    }

    /**
     * 宽松取字符串。
     *
     * @param value 原始值
     * @return 文本，null 转空串
     */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 宽松取长整数，兼容 JSON 里的 Double 与字符串。
     *
     * @param value 原始值
     * @return 长整数，取不到时为 null
     */
    private static Long longOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    /**
     * 空串归一为 null，配合全局 NON_NULL 策略从 JSON 中省略。
     *
     * @param value 原值
     * @return null 或原值
     */
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * 空值安全的去空白。
     *
     * @param value 原值
     * @return 去空白结果，null 转空串
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
