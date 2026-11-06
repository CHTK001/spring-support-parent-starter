// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.support.CollectJdbcSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 节点：在流程里读写数据库。
 *
 * <p>语句整段支持 {@code ${}} 插值，一次只执行一条。SELECT 与 WITH/SHOW 开头的语句按查询处理，
 * 结果集合写入出参变量供下游循环节点使用；其余语句按更新处理，受影响行数写入
 * {@code _affected} 供摘要显示。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectSqlExecutor implements CollectShapeExecutor {

    /**
     * 数据源引用参数名。
     */
    public static final String DATASOURCE = "datasource";

    /**
     * 语句参数名。
     */
    public static final String STATEMENT = "statement";

    /**
     * 出参变量名参数名。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 查询结果默认变量名。
     */
    public static final String DEFAULT_OUTPUT = "rows";

    /**
     * 受影响行数暂存变量名。
     */
    private static final String VAR_AFFECTED = "_affected";

    /**
     * JDBC 通道。
     */
    private final CollectJdbcSupport jdbc;

    @Override
    public String supportShape() {
        return "sql";
    }

    /**
     * 每批行数参数名。
     */
    public static final String BATCH = "batchSize";

    /**
     * 默认每批行数。
     */
    public static final int DEFAULT_BATCH = 500;

    /**
     * IN 子句中的列表占位符模式：{@code IN (${expr})} 或 {@code in (${expr})}。
     */
    private static final Pattern IN_LIST_PATTERN = Pattern.compile(
            "(?i)\\bIN\\s*\\(\\s*\\$\\{([^}]+)\\}\\s*\\)");

    /**
     * 批次占位符，用于在求值后的 SQL 中标记 IN 列表位置。
     */
    private static final String BATCH_PLACEHOLDER = "\u0000BATCH\u0000";

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "SQL", "输出", "database",
                "读写数据库，查询结果可作为下游节点的循环集合");
        meta.setOutputVariable("默认 data，旧流程留空时按 ${rows} 兜底");
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.required(STATEMENT, "SQL 语句", "textarea",
                        "整段支持 ${}。IN 子句写 IN (${列表变量}) 即自动分批，避免超长 SQL。"
                                + "例：select * from t where id in (${ids})"),
                CollectShapeMetas.datasource(DATASOURCE, "数据源", CollectJdbcSupport.APP_DATASOURCE,
                        "默认应用主库；下拉里是 collect_datasource 已启用的数据源"),
                CollectShapeMetas.outputVariable("SELECT 结果写入的变量名，默认 data"),
                CollectShapeMetas.text(BATCH, "每批行数", "number",
                        "IN 列表超过该数量时自动分批执行，默认 " + DEFAULT_BATCH));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        if (node.param(STATEMENT).isEmpty()) {
            throw new IllegalStateException("SQL 节点没有填写语句");
        }
        String raw = node.param(STATEMENT);
        int batchSize = Math.max(1, node.intParam(BATCH, DEFAULT_BATCH));

        // 检测 IN (${...}) 模式：有则走批次执行，无则按原逻辑单条执行
        Matcher matcher = IN_LIST_PATTERN.matcher(raw);
        if (matcher.find()) {
            executeBatched(node, context, variables, raw, batchSize);
            return;
        }

        String sql = CollectExpression.evalText(raw, variables);
        String datasource = node.param(DATASOURCE);
        if (isQuery(sql)) {
            String output = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT);
            List<Map<String, Object>> rows = jdbc.query(datasource, sql);
            variables.put(output, rows);
            return;
        }
        if (!context.getProperties().isSqlAllowWrite()) {
            throw new IllegalStateException(
                    "SQL 节点的写语句已被配置禁用（plugin.collect.sql-allow-write=false）。"
                            + "需要写库请改用 database 节点（字段映射、参数绑定），或由运维放开该配置。");
        }
        log.warn("[采集审计] SQL 写语句 flowId={} taskNo={} node={} 语句: {}",
                context.getFlowId(), context.getTaskNo(), node.name(), abbreviate(sql));
        variables.put(VAR_AFFECTED, (long) jdbc.update(datasource, sql));
    }

    /**
     * 批次执行：把 {@code IN (${列表})} 中的列表拆成多片，逐片替换后执行并合并结果。
     *
     * <p>解决 IN 列表过大（如 9429 个值）导致 SQL 超长、超出 {@code max_allowed_packet}
     * 或数据库 IN 参数上限的问题。每片独立执行，SELECT 合并行、UPDATE 累加影响行数。</p>
     *
     * @param node      节点
     * @param context   上下文
     * @param variables 变量作用域
     * @param raw       原始 SQL 模板
     * @param batchSize 每批数量
     * @throws Exception 执行异常
     */
    private void executeBatched(CollectNode node, CollectContext context, Map<String, Object> variables,
                                 String raw, int batchSize) throws Exception {
        // 提取所有 IN (${...}) 中的表达式并求值
        Matcher matcher = IN_LIST_PATTERN.matcher(raw);
        List<List<Object>> allLists = new ArrayList<>();
        StringBuilder template = new StringBuilder(raw);
        // 从后往前替换，避免位置偏移
        List<int[]> matchSpans = new ArrayList<>();
        while (matcher.find()) {
            matchSpans.add(new int[]{matcher.start(), matcher.end()});
        }
        for (int i = matchSpans.size() - 1; i >= 0; i--) {
            int[] span = matchSpans.get(i);
            String expr = raw.substring(span[0], span[1]).replaceAll("(?i)^IN\\s*\\(\\s*\\$\\{", "")
                    .replaceAll("\\}\\s*\\)$", "");
            Object value = CollectExpression.eval("${" + expr + "}", variables);
            List<Object> list = toList(value);
            allLists.add(0, list);
            template.replace(span[0], span[1], BATCH_PLACEHOLDER);
        }

        // 取最大列表长度决定批次数
        int maxSize = 0;
        for (List<Object> list : allLists) {
            maxSize = Math.max(maxSize, list.size());
        }
        if (maxSize == 0) {
            variables.put(VAR_AFFECTED, 0L);
            return;
        }

        boolean query = false;
        List<Map<String, Object>> mergedRows = new ArrayList<>();
        long totalAffected = 0;
        String datasource = node.param(DATASOURCE);

        for (int offset = 0; offset < maxSize; offset += batchSize) {
            String sql = template.toString();
            int listIndex = 0;
            int searchFrom = 0;
            while (true) {
                int idx = sql.indexOf(BATCH_PLACEHOLDER, searchFrom);
                if (idx < 0) {
                    break;
                }
                List<Object> list = allLists.get(listIndex++);
                int end = Math.min(offset + batchSize, list.size());
                List<Object> chunk = offset < list.size() ? list.subList(offset, end) : List.of();
                // 占位符顶替的是整个 IN (${...})（含 IN 与括号），这里必须补回 IN (...)
                String inClause = "IN (" + buildInClause(chunk) + ")";
                sql = sql.substring(0, idx) + inClause + sql.substring(idx + BATCH_PLACEHOLDER.length());
                searchFrom = idx + inClause.length();
            }
            sql = CollectExpression.evalText(sql, variables);
            if (isQuery(sql)) {
                query = true;
                mergedRows.addAll(jdbc.query(datasource, sql));
            } else {
                if (!context.getProperties().isSqlAllowWrite()) {
                    throw new IllegalStateException(
                            "SQL 节点的写语句已被配置禁用（plugin.collect.sql-allow-write=false）。");
                }
                totalAffected += jdbc.update(datasource, sql);
            }
        }

        if (query) {
            String output = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT);
            variables.put(output, mergedRows);
        } else {
            variables.put(VAR_AFFECTED, totalAffected);
        }
    }

    /**
     * 把任意值转成列表：集合直接转，标量包装成单元素列表，null 返回空列表。
     *
     * @param value 任意值
     * @return 列表
     */
    private List<Object> toList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of(value);
    }

    /**
     * 构建 IN 子句的值列表，字符串值加单引号并转义，数字原样。
     *
     * @param values 值列表
     * @return IN 括号内的值串
     */
    private String buildInClause(List<Object> values) {
        if (values.isEmpty()) {
            return "NULL";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object v = values.get(i);
            if (v instanceof Number) {
                sb.append(v);
            } else {
                String s = String.valueOf(v).replace("'", "''");
                sb.append('\'').append(s).append('\'');
            }
        }
        return sb.toString();
    }

    /**
     * 截断语句用于审计日志，避免整条 DDL/DML 刷屏。
     *
     * @param sql 已求值语句
     * @return 最多 120 字的单行摘要
     */
    private String abbreviate(String sql) {
        String flat = sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
        return flat.length() > 120 ? flat.substring(0, 120) + "…" : flat;
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        Object affected = variables.get(VAR_AFFECTED);
        if (affected != null) {
            return "影响 " + affected + " 行";
        }
        String output = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT);
        Object rows = variables.get(output);
        if (rows instanceof Collection) {
            return "返回 " + ((Collection<?>) rows).size() + " 行";
        }
        return null;
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return DEFAULT_OUTPUT;
    }

    /**
     * 是否查询语句。
     *
     * <p>判定口径统一走 {@link CollectJdbcSupport#isReadOnlyStatement}：除 {@code select}/
     * {@code with}/{@code show} 开头，还要放过**括号包裹的查询块** —— MariaDB/MySQL 里
     * {@code (select …) union all (select …)} 以左括号开头，只按首词判定会把它误判成写语句，
     * 在 {@code sql-allow-write=false} 下直接被拒。</p>
     *
     * @param sql 已求值语句
     * @return 是只读查询返回 true
     */
    private boolean isQuery(String sql) {
        return CollectJdbcSupport.isReadOnlyStatement(sql);
    }
}
