package com.chua.starter.collect.support;

import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 中间件类节点（InfluxDB / Neo4j 等）的公共支撑。
 *
 * <p>三件事各做一遍没有意义，集中在这一处：</p>
 * <ul>
 *   <li>数据源解析：拒绝应用主库 {@code app}（中间件没有「容器主连接」的概念）、
 *       按类型校验，报错要能直接告诉用户「该选哪种数据源」；</li>
 *   <li>来源集合展开：与入库节点同口径 —— 集合原样、单 Map 收成一行、标量包成
 *       {@code value} 列，抽取 / SQL / 录入节点的出参都能直接喂；</li>
 *   <li>摘要行数：从作用域里数出出参集合的规模。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectMiddlewareSupport {

    /**
     * 应用主库引用，中间件节点不认。
     */
    public static final String APP_DATASOURCE = "app";

    /**
     * 标量行的默认列名。
     */
    public static final String VALUE_COLUMN = "value";

    /**
     * 采集侧数据源解析器。
     */
    private final CollectDatasourceRegistry registry;

    /**
     * 解析中间件数据源并校验类型。
     *
     * @param key        数据源引用（ID 或名称）
     * @param expectType 期望类型：influxdb / neo4j
     * @return 数据源中心行
     * @throws IllegalStateException 引用为空、选了应用主库、找不到或类型不符
     */
    public DataSourceVO resolve(String key, String expectType) {
        String reference = key == null ? "" : key.trim();
        if (reference.isEmpty()) {
            throw new IllegalStateException("未选择数据源");
        }
        if (APP_DATASOURCE.equalsIgnoreCase(reference)) {
            throw new IllegalStateException(
                    "本节点不支持应用主库 app，请选择一个 " + expectType + " 类型的数据源");
        }
        DataSourceVO row = registry.findRow(reference);
        String type = row.type() == null ? "" : row.type().trim().toLowerCase(java.util.Locale.ROOT);
        if (!expectType.equals(type)) {
            throw new IllegalStateException("数据源 " + row.name() + " 的类型是 "
                    + (row.type() == null || row.type().isBlank() ? "未知" : row.type())
                    + "，本节点需要 " + expectType + " 类型");
        }
        return row;
    }

    /**
     * 来源集合表达式 → 行列表。
     *
     * @param expression 来源集合表达式，如 {@code ${rows}}
     * @param variables  令牌作用域
     * @return 行列表，表达式为空或求值为空时返回空列表
     */
    public List<Map<String, Object>> sourceRows(String expression, Map<String, Object> variables) {
        Object source = CollectExpression.eval(expression, variables);
        List<Object> items = new ArrayList<>();
        if (source instanceof Collection<?> collection) {
            items.addAll(collection);
        } else if (source instanceof Map<?, ?>) {
            items.add(source);
        } else if (source != null) {
            items.add(source);
        }
        List<Map<String, Object>> rows = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((cellKey, cellValue) -> row.put(String.valueOf(cellKey), cellValue));
                rows.add(row);
            } else if (item != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put(VALUE_COLUMN, item);
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 取出参集合的行数，供摘要展示。
     *
     * @param variables 令牌作用域
     * @param output    出参变量名
     * @return 行数，未命中集合按 0 计
     */
    public int sizeOf(Map<String, Object> variables, String output) {
        Object value = variables.get(output);
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        return value == null ? 0 : 1;
    }
}
