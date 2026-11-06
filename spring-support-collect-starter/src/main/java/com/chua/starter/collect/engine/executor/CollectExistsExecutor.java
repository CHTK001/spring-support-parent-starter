package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.support.CollectJdbcSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查已存在节点：批量判断上游数据的主键是否已在目标表存在。
 *
 * <p>替代「手写 SQL + groovy 去重脚本」的做法：判重键由上游抽取节点的字段映射
 * 而来（设计器自动填 {@code keyField}），查询走 {@link CollectJdbcSupport#queryIn}
 * —— 表名与列名过严格标识符白名单、值全部经 PreparedStatement 绑定、IN 按片拆分
 * （每片默认 {@code plugin.collect.exists-batch-size} 条），既不拼出几 MB 的巨 SQL
 * 顶爆 {@code max_allowed_packet}，也没有注入面。</p>
 *
 * <p>输出一个结构（默认写 {@code data}）：</p>
 * <ul>
 *   <li>{@code exists}：已存在的键值集合</li>
 *   <li>{@code fresh}：全新行（原样保留上游行的所有字段），直接喂给下游循环/写库</li>
 *   <li>{@code total} / {@code existsCount} / {@code freshCount}：计数，供摘要与判断</li>
 * </ul>
 *
 * <p>「已存在则跳过」的接法：循环集合填 {@code ${data.fresh}}，只对全新行跑后续链路；
 * 也可以在逐条循环里用判断节点检查 {@code ${data.exists}} 是否包含当前 {@code item}
 * 的键。两种都不用手写去重脚本。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RequiredArgsConstructor
@Component
public class CollectExistsExecutor implements CollectShapeExecutor {

    /**
     * 数据源引用参数名。
     */
    public static final String DATASOURCE = "datasource";

    /**
     * 目标表参数名。
     */
    public static final String TABLE = "table";

    /**
     * 判重键列参数名。
     */
    public static final String KEY_COLUMN = "keyColumn";

    /**
     * 数据字段参数名。
     */
    public static final String KEY_FIELD = "keyField";

    /**
     * 输出名参数名。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 默认输出变量名。
     */
    public static final String DEFAULT_OUTPUT = "data";

    /**
     * JDBC 通道。
     */
    private final CollectJdbcSupport jdbc;

    @Override
    public String supportShape() {
        return "exists";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "查已存在", "过滤", "check",
                "批量查上游数据里哪些主键已在目标表存在，避免重复入库");
        // 已下线：数据库节点 mode=skip/update 已覆盖查重，单独查已存在节点不再进入调色板。
        meta.setHidden(true);
        meta.setOutputVariable("结果写入出参变量，默认 data：{ exists: 已存在键, fresh: 全新行, existsCount, freshCount, total }");
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.datasource(DATASOURCE, "数据源", CollectJdbcSupport.APP_DATASOURCE,
                        "默认应用主库；下拉里是 collect_datasource 已启用的数据源"),
                CollectShapeMetas.table(TABLE, "目标表", null,
                        "要查重的表，下拉随数据源级联"),
                CollectShapeMetas.required(KEY_COLUMN, "判重键列", "text",
                        "目标表里用作唯一判据的列名，如 id / word"),
                CollectShapeMetas.text(KEY_FIELD, "数据字段", "text",
                        "上游数据行里取值的字段名，留空则与判重键列同名"),
                CollectShapeMetas.outputVariable("查重结果写入的变量名，默认 data"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        String table = node.param(TABLE).trim();
        if (table.isEmpty()) {
            throw new IllegalStateException("查已存在节点没有填写目标表");
        }
        String keyColumn = node.param(KEY_COLUMN).trim();
        if (keyColumn.isEmpty()) {
            throw new IllegalStateException("查已存在节点没有填写判重键列");
        }
        String keyField = node.param(KEY_FIELD).trim();
        if (keyField.isEmpty()) {
            keyField = keyColumn;
        }
        String datasource = node.param(DATASOURCE).trim();

        // 输入：上游产出（${data} 即上一个节点的输出，引擎已别名）
        List<Map<String, Object>> rows = toRows(variables.get("data"));

        // 收集判重键值（保留顺序、去重）
        List<Object> keys = new ArrayList<>();
        Set<Object> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(keyField);
            if (value != null && seen.add(value)) {
                keys.add(value);
            }
        }

        // 分批查已存在：每片一次 PreparedStatement IN 查询，值逐位绑定
        int batchSize = Math.max(1, context.getProperties().getExistsBatchSize());
        Set<Object> existing = new LinkedHashSet<>();
        for (int i = 0; i < keys.size(); i += batchSize) {
            List<Object> chunk = keys.subList(i, Math.min(i + batchSize, keys.size()));
            existing.addAll(jdbc.queryIn(datasource, table, keyColumn, chunk));
        }

        // 成员判定按字符串归一化比较，避免上游是 Integer、库端是 Long 这类类型差导致漏判
        Set<String> existingKeys = new LinkedHashSet<>();
        for (Object value : existing) {
            existingKeys.add(String.valueOf(value));
        }
        List<Map<String, Object>> fresh = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(keyField);
            if (value == null || !existingKeys.contains(String.valueOf(value))) {
                fresh.add(row);
            }
        }

        String output = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", new ArrayList<>(existing));
        result.put("fresh", fresh);
        result.put("total", rows.size());
        result.put("existsCount", existing.size());
        result.put("freshCount", fresh.size());
        variables.put(output, result);
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        String output = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
        Object value = variables.get(output);
        if (value instanceof Map<?, ?> map) {
            return "已存在 " + map.get("existsCount") + " / 全新 " + map.get("freshCount");
        }
        return null;
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return DEFAULT_OUTPUT;
    }

    /**
     * 把上游产出规整成行列表：列表逐行取，单条 Map 包装成一行，其余按空处理。
     *
     * @param input 上游产出
     * @return 行列表
     */
    private List<Map<String, Object>> toRows(Object input) {
        if (input instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>(list.size());
            for (Object item : list) {
                Map<String, Object> row = toRow(item);
                if (row != null) {
                    rows.add(row);
                }
            }
            return rows;
        }
        Map<String, Object> row = toRow(input);
        return row == null ? List.of() : List.of(row);
    }

    /**
     * 单个元素转行映射，非 Map 元素返回 null。
     *
     * @param item 元素
     * @return 行映射或 null
     */
    private Map<String, Object> toRow(Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            row.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return row;
    }
}
