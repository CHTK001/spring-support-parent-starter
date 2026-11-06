package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectEngine;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.CollectFunctions;
import com.chua.starter.collect.engine.CollectRecordEntry;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.store.CollectDatabaseSink;
import com.chua.starter.collect.support.CollectJdbcSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集平台节点：把抽取结果落成采集平台自身的采集记录。
 *
 * <p>字段用可重复参数 {@code cell:字段名} 声明，每行一条。填了集合表达式时按集合逐条产出，
 * 集合的每一项若是映射，其键会直接成为字段表达式里的同名变量，因此抽取节点产出的
 * {@code ${rows}} 可以原样喂给本节点。</p>
 *
 * <p>结果一律写入采集结果表；{@code targetTable} 非空时再镜像写入一张业务表，
 * 表不存在会自动建，缺列会自动补，列类型统一用大文本。超过单任务记录上限的记录两边都不落。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectOutputExecutor implements CollectShapeExecutor {

    /**
     * 逐条产出的集合表达式参数名。
     */
    public static final String EACH = "each";

    /**
     * 字段参数组名。
     */
    public static final String CELL = "cell";

    /**
     * 业务主键表达式参数名。
     */
    public static final String KEY = "key";

    /**
     * 来源地址表达式参数名。
     */
    public static final String URL = "url";

    /**
     * 业务表参数名。
     */
    public static final String TABLE = "targetTable";

    /**
     * 业务表数据源参数名。
     */
    public static final String DATASOURCE = "datasource";

    /**
     * 默认来源地址表达式。
     */
    public static final String DEFAULT_URL = "${url}";

    /**
     * 本次产出条数的暂存变量名，仅供摘要使用。
     */
    private static final String AFFECTED = "_emitted";
    /**
     * 本次被唯一键挡下的重复行数，仅供摘要使用。
     */
    private static final String VAR_SKIPPED = "_skipped";
    /**
     * JDBC 通道。
     */
    private final CollectJdbcSupport jdbcSupport;

    @Override
    public String supportShape() {
        return "output";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "采集平台", "输出", "database",
                "把字段写入采集平台自身的采集结果表，可同时镜像到业务表");
        meta.setOutputVariable("无出参；记录条数看任务详情的采集记录数");
        // 已下线：写业务表这件事由「数据库」节点统一承接（追加 / 跳过 / 覆盖 / 按主键更新 / 全覆盖），
        // 再摆一个平行的写库节点只会让人在两个语义重叠的节点之间反复犹豫。
        // 这里只从调色板撤掉，存量流程里的该节点仍能正常渲染与运行。
        meta.setHidden(true);
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.text(EACH, "逐条集合", "text",
                        "留空按当前令牌产出一条；填 ${rows} 则每条集合项产出一条"),
                CollectShapeMetas.datasource(DATASOURCE, "业务表数据源", CollectJdbcSupport.APP_DATASOURCE,
                        "默认应用主库；选其它数据源后，下面的业务表与字段候选会跟着换成该库的表"),
                CollectShapeMetas.table(TABLE, "业务表", "",
                        "留空只进采集结果表；选定后按该表真实列名配置字段映射，缺列会自动补齐"),
                CollectShapeMetas.mapping(CELL + ":字段名", "字段映射", TABLE,
                        "左列选目标表字段，右列填取值表达式，支持 ${} 与行内同名变量"),
                CollectShapeMetas.text(KEY, "业务主键", "text",
                        "例：${url}；非空时同键记录覆盖写入，可与去重节点配成同一表达式；"
                                + "最长 " + CollectDatabaseSink.KEY_LIMIT + " 字符，超长报错而不是截断，"
                                + "因为截断会让不同主键撞成同一条"),
                CollectShapeMetas.text(URL, "来源地址", "text", "留空取 ${url}"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        List<CollectParamDTO> cells = new ArrayList<>();
        for (CollectParamDTO cell : node.pairsOf(CELL)) {
            if (cell.name() != null && !cell.name().isBlank()) {
                cells.add(cell);
            }
        }
        if (cells.isEmpty()) {
            throw new IllegalStateException("采集平台节点没有声明字段，请用 cell:字段名 逐行填写");
        }
        String table = CollectExpression.evalText(node.param(TABLE), variables).trim();
        if (!table.isEmpty()) {
            jdbcSupport.ensureTable(node.param(DATASOURCE), table, columnNames(cells));
        }
        int emitted = 0;
        int dropped = 0;
        // 攒批写入：单条 insert 走一次连接 + 一次自动提交，1000 行要 50 多秒；
        // 同一节点的行共用一条预编译语句，执行器一次 executeBatch 提交。
        List<Map<String, Object>> pending = new ArrayList<>();
        for (Map<String, Object> scope : itemScopes(node, variables)) {
            CollectRecordEntry entry = new CollectRecordEntry();
            entry.setNodeId(node.id());
            entry.setUrl(CollectExpression.evalText(
                    node.param(URL).isEmpty() ? DEFAULT_URL : node.param(URL), scope).trim());
            entry.setKey(CollectExpression.evalText(node.param(KEY), scope).trim());
            requireKeyWithinColumn(entry.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            for (CollectParamDTO cell : cells) {
                String name = cell.name().trim();
                Object value = CollectExpression.eval(cell.value(), scope);
                entry.getCells().add(new CollectParamDTO(name,
                        CollectFunctions.stringify(value), cell.description()));
                row.put(name, value);
            }
            if (!context.offerRecord(entry)) {
                dropped++;
                break;
            }
            emitted++;
            if (!table.isEmpty()) {
                pending.add(row);
            }
        }
        if (!table.isEmpty() && !pending.isEmpty()) {
            int inserted = jdbcSupport.insertBatch(node.param(DATASOURCE), table, pending);
            int skipped = Math.max(0, pending.size() - inserted);
            if (skipped > 0) {
                // 唯一键挡住的行没进库：既不算本次产出，也要从流程累计里收回，
                // 否则摘要写「落库 989 条」而表里只多出 580 行，总采集数量还会一路虚高
                context.discardRecords(skipped);
                emitted -= skipped;
                variables.put(VAR_SKIPPED, skipped);
            }
        }
        variables.put(AFFECTED, emitted);
        if (dropped > 0) {
            variables.put(CollectEngine.VAR_STOP, Boolean.TRUE);
        }
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        Object emitted = variables.get(AFFECTED);
        if (emitted == null) {
            return null;
        }
        String text = "落库 " + emitted + " 条";
        Object skipped = variables.get(VAR_SKIPPED);
        if (skipped instanceof Number number && number.intValue() > 0) {
            text += "（唯一键重复 " + number.intValue() + " 条未写入）";
        }
        if (CollectExpression.truthy(variables.get(CollectEngine.VAR_STOP))) {
            text += "，已达单任务记录上限，本分支停止";
        }
        return text;
    }

    /**
     * 校验业务主键没有超出结果表列宽。
     *
     * <p>主键列是 {@code varchar(255)}，超长只能截断或报错两种选择。这里选报错：
     * 截断会把两个不同的主键压成同一个前缀，同键覆盖直接吃掉一条记录，去重也会
     * 把没采过的东西判成已采过，属于静默丢数据；报错则把问题留在编排期可见。</p>
     *
     * @param key 求值后的业务主键，空值表示不启用同键覆盖
     * @throws IllegalStateException 主键超长
     */
    private void requireKeyWithinColumn(String key) {
        if (key == null || key.isEmpty() || key.length() <= CollectDatabaseSink.KEY_LIMIT) {
            return;
        }
        throw new IllegalStateException("业务主键长度 " + key.length() + " 超出结果表列宽 "
                + CollectDatabaseSink.KEY_LIMIT + "，请改用更短的主键表达式或先做哈希，"
                + "直接落库会被截断成同一个主键而丢数据");
    }

    /**
     * 取每条记录的求值作用域。
     *
     * @param node      节点
     * @param variables 令牌作用域
     * @return 作用域列表，集合为空返回空列表
     */
    private List<Map<String, Object>> itemScopes(CollectNode node, Map<String, Object> variables) {
        List<Map<String, Object>> scopes = new ArrayList<>();
        String each = node.param(EACH);
        if (each.isEmpty()) {
            scopes.add(variables);
            return scopes;
        }
        Object value = CollectExpression.eval(each, variables);
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                scopes.add(scopeOf(variables, item));
            }
            return scopes;
        }
        if (value != null) {
            scopes.add(scopeOf(variables, value));
        }
        return scopes;
    }

    /**
     * 构造单条记录的作用域：行内键覆盖令牌变量，集合项始终可用 {@code item} 引用。
     *
     * @param variables 令牌作用域
     * @param item      集合项
     * @return 本条记录作用域
     */
    private Map<String, Object> scopeOf(Map<String, Object> variables, Object item) {
        Map<String, Object> scope = new LinkedHashMap<>(variables);
        if (item instanceof Map<?, ?> row) {
            for (Map.Entry<?, ?> entry : row.entrySet()) {
                scope.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        scope.put("item", item);
        return scope;
    }

    /**
     * 取业务表列名。
     *
     * @param cells 字段声明
     * @return 列名列表
     */
    private List<String> columnNames(List<CollectParamDTO> cells) {
        List<String> names = new ArrayList<>(cells.size());
        for (CollectParamDTO cell : cells) {
            names.add(cell.name().trim());
        }
        return names;
    }
}
