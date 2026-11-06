package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.support.CollectMiddlewareSupport;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.dto.Neo4jQueryDTO;
import com.chua.starter.datasourcehub.support.service.Neo4jManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Neo4j 写入节点：把来源集合按行写成图节点（CREATE / MERGE）。
 *
 * <p>复用数据源中心 {@link Neo4jManageService#query} 提交 Cypher。该通道只带语句、不带参数，
 * 所以行数据以 {@code UNWIND [ ... ] AS row} 内联进语句 —— 转义全部收在
 * {@link #cypherLiteral} / {@link #mapLiteral}：字符串走 Cypher 转义集，属性键统一反引号包裹
 * （反引号自身加倍），标签同样反引号包裹，不存在「拼出来的语句被数据改写」的口子。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectNeo4jWriteExecutor implements CollectShapeExecutor {

    /**
     * 数据源类型。
     */
    private static final String DS_TYPE = "neo4j";

    /**
     * 来源集合参数名。
     */
    private static final String SOURCE = "source";

    /**
     * 数据源参数名。
     */
    private static final String DATASOURCE = "datasource";

    /**
     * 节点标签参数名。
     */
    private static final String LABEL = "label";

    /**
     * 写入方式参数名。
     */
    private static final String MODE = "mode";

    /**
     * 合并键列参数名。
     */
    private static final String KEYS = "keys";

    /**
     * 每条语句最多展开的行数，控制单语句体积。
     */
    private static final int CHUNK_SIZE = 500;

    /**
     * 新建。
     */
    private static final String MODE_CREATE = "create";

    /**
     * 按键合并。
     */
    private static final String MODE_MERGE = "merge";

    /**
     * 数据源中心 Neo4j 服务。
     */
    private final ObjectProvider<Neo4jManageService> neo4j;

    /**
     * 中间件公共支撑。
     */
    private final CollectMiddlewareSupport support;

    @Override
    public String supportShape() {
        return "neo4j-write";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "Neo4j写入", "输出", "share",
                "把来源集合逐行写成 Neo4j 图节点：CREATE 直接新建，MERGE 按键去重（存在即更新属性）");
        meta.setOutputVariable("无出参；写入行数看节点摘要");
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.required(SOURCE, "来源集合", "textarea",
                        "填集合表达式，例 ${rows}；抽取、SQL、数据录入节点的出参可直接喂进来"),
                CollectShapeMetas.datasource(DATASOURCE, "Neo4j 数据源", "",
                        "只接受 neo4j 类型数据源；应用主库 app 不适用"),
                CollectShapeMetas.required(LABEL, "节点标签", "text",
                        "图节点标签，可用 ${变量} 拼接；写入时自动按反引号包裹，任意合法标识都安全"),
                CollectShapeMetas.option(MODE, "写入方式", MODE_CREATE,
                        "新建 = 每行一个新节点；按键合并 = 按「合并键」查重，命中只补属性",
                        MODE_CREATE, MODE_MERGE),
                CollectShapeMetas.text(KEYS, "合并键列", "text",
                        "逗号分隔的列名；「按键合并」必填，值相同视为同一节点（联合键填多列）"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        List<Map<String, Object>> rows = support.sourceRows(node.param(SOURCE), variables);
        if (rows.isEmpty()) {
            variables.put("_written", 0);
            return;
        }
        Neo4jManageService service = neo4j.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Neo4j 支持未启用（数据源中心未装载），无法写入");
        }
        DataSourceVO datasource = support.resolve(node.param(DATASOURCE), DS_TYPE);
        String label = CollectExpression.evalText(node.param(LABEL), variables).trim();
        if (label.isEmpty()) {
            throw new IllegalStateException("未填写节点标签");
        }
        String mode = CollectExpression.evalText(node.param(MODE), variables).trim();
        if (!MODE_MERGE.equals(mode)) {
            mode = MODE_CREATE;
        }
        Set<String> keyColumns = splitColumns(CollectExpression.evalText(node.param(KEYS), variables));
        if (mode.equals(MODE_MERGE) && keyColumns.isEmpty()) {
            throw new IllegalStateException("「按键合并」必须填写合并键列");
        }

        // MERGE 行过滤：键缺失或为 null 的行在 MERGE 里是非法模式，只写新建行并告警
        List<Map<String, Object>> usable = new ArrayList<>(rows.size());
        int skipped = 0;
        for (Map<String, Object> row : rows) {
            if (mode.equals(MODE_MERGE) && missingKey(row, keyColumns)) {
                skipped++;
            } else {
                usable.add(row);
            }
        }
        if (skipped > 0) {
            log.warn("[collect] Neo4j 写入跳过 {} 行：合并键缺失或为 null", skipped);
        }

        String pattern = mode.equals(MODE_MERGE) ? mergePattern(label, keyColumns) : label;
        String verb = mode.equals(MODE_MERGE) ? "MERGE" : "CREATE";
        int written = 0;
        for (int start = 0; start < usable.size(); start += CHUNK_SIZE) {
            List<Map<String, Object>> chunk = usable.subList(start, Math.min(start + CHUNK_SIZE, usable.size()));
            StringBuilder cypher = new StringBuilder("UNWIND [");
            for (int i = 0; i < chunk.size(); i++) {
                if (i > 0) {
                    cypher.append(", ");
                }
                cypher.append(mapLiteral(chunk.get(i)));
            }
            cypher.append("] AS row ").append(verb).append(" (n:").append(pattern).append(") SET n = row");
            service.query(new Neo4jQueryDTO(cypher.toString(), datasource.id()));
            written += chunk.size();
        }
        variables.put("_written", written);
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        Object written = variables.get("_written");
        return "写入 " + (written instanceof Number ? ((Number) written).intValue() : 0) + " 行";
    }

    /**
     * MERGE 匹配模式，如 {@code Person {email: row.email}}。
     *
     * @param label      节点标签
     * @param keyColumns 合并键列
     * @return 模式文本
     */
    private String mergePattern(String label, Set<String> keyColumns) {
        StringBuilder pattern = new StringBuilder(backtick(label)).append(" {");
        int index = 0;
        for (String key : keyColumns) {
            if (index++ > 0) {
                pattern.append(", ");
            }
            pattern.append(backtick(key)).append(": row.").append(columnRef(key));
        }
        return pattern.append('}').toString();
    }

    /**
     * 行是否缺合并键（任一键缺失即整行不可 MERGE）。
     *
     * @param row        数据行
     * @param keyColumns 合并键列
     * @return 缺失返回 true
     */
    private boolean missingKey(Map<String, Object> row, Set<String> keyColumns) {
        for (String key : keyColumns) {
            Object value = row.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Map → Cypher map 字面量。
     *
     * @param row 数据行
     * @return {@code {k: v, ...}} 文本
     */
    private String mapLiteral(Map<String, Object> row) {
        StringBuilder builder = new StringBuilder("{");
        int index = 0;
        for (Map.Entry<String, Object> cell : row.entrySet()) {
            if (cell.getValue() == null) {
                continue; // null 属性不写：SET null 会删已有值，与「写入本行」语义不符
            }
            if (index++ > 0) {
                builder.append(", ");
            }
            builder.append(backtick(cell.getKey())).append(": ").append(cypherLiteral(cell.getValue()));
        }
        return builder.append('}').toString();
    }

    /**
     * 取合并键引用：简单标识符直通，否则反引号包裹。
     *
     * @param column 列名
     * @return row 后接的引用文本
     */
    private String columnRef(String column) {
        return column.matches("[A-Za-z_][A-Za-z0-9_]*") ? column : backtick(column);
    }

    /**
     * 反引号包裹标识符，内部反引号加倍。
     *
     * @param name 原文
     * @return 反引号标识符
     */
    private String backtick(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    /**
     * 值 → Cypher 字面量。
     *
     * @param value 值
     * @return 字面量文本
     */
    private String cypherLiteral(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        if (value instanceof Number number) {
            return number instanceof java.math.BigDecimal decimal ? decimal.toPlainString() : number.toString();
        }
        if (value instanceof CharSequence text) {
            return cypherString(text.toString());
        }
        if (value instanceof Map || value instanceof Iterable || value.getClass().isArray()) {
            return cypherString(CollectJson.toJson(value));
        }
        return cypherString(String.valueOf(value));
    }

    /**
     * Cypher 字符串字面量：单引号包裹，转义集与 Cypher 词法一致。
     *
     * @param text 原文
     * @return 单引号字面量
     */
    private String cypherString(String text) {
        StringBuilder builder = new StringBuilder(text.length() + 8).append('\'');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '\'' -> builder.append("\\'");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> builder.append(ch);
            }
        }
        return builder.append('\'').toString();
    }

    /**
     * 解析列清单。
     *
     * @param text 逗号分隔的列名
     * @return 列名集合
     */
    private Set<String> splitColumns(String text) {
        Set<String> columns = new HashSet<>();
        if (text == null || text.isBlank()) {
            return columns;
        }
        for (String part : text.split("[,，]")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                columns.add(name);
            }
        }
        return columns;
    }
}
