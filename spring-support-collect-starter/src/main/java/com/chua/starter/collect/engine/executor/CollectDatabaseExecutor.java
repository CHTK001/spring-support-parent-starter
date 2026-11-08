package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.support.CollectJdbcSupport;
import com.chua.starter.datasourcehub.support.model.dto.DdlColumn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库节点：把一个集合整批写进目标表。
 *
 * <p>与已有两个写库节点分工明确：</p>
 * <ul>
 *   <li>{@link CollectSqlExecutor} 手写一条 SQL，语句由作者负责，适合取数与改数；</li>
 *   <li>{@link CollectOutputExecutor} 逐条产出采集记录，可选镜像一张业务表，
 *       表结构一律按大文本建——它面向"把这一条记录留下来"，不是批量搬数据；</li>
 *   <li>本节点面向"把这一批数据搬进这张表"：不产采集记录，按批提交，
 *       列类型由数据源模块按来源值推断，写入方式可选追加、跳过、覆盖、
 *       按主键更新或全覆盖（TRUNCATE 后全量写入）。</li>
 * </ul>
 *
 * <p>表结构与字段归属一律委托数据源模块（{@code DatasourceSchemaService}）：
 * 表不存在则建、缺列则补，类型推断与跨方言 DDL 由数据源模块统一口径，
 * 采集侧不自己拼 {@code CREATE TABLE}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectDatabaseExecutor implements CollectShapeExecutor {

    /**
     * 目标数据源参数名。
     */
    public static final String DATASOURCE = "datasource";

    /**
     * 目标表参数名。
     */
    public static final String TABLE = "table";

    /**
     * 来源集合表达式参数名。
     */
    public static final String SOURCE = "source";

    /**
     * 写入方式参数名。
     */
    public static final String MODE = "mode";

    /**
     * 字段映射组名，键为目标列名、值为取值表达式。
     */
    public static final String COLUMN = "column";

    /**
     * 业务主键列组名，键为主键列名，仅按主键更新模式使用。
     */
    public static final String KEY = "key";

    /**
     * 每批行数参数名。
     */
    public static final String BATCH = "batchSize";

    /**
     * 追加写入。
     */
    public static final String MODE_APPEND = "append";

    /**
     * 覆盖写入：先清空目标表再写入（TRUNCATE）。
     */
    public static final String MODE_REPLACE = "replace";

    /**
     * 覆盖更新：命中原键的行改写，未命中的行新增。键由流程作者声明。
     */
    public static final String MODE_UPDATE = "update";

    /**
     * 跳过写入：命中原键的行整行跳过，未命中的行新增。键由流程作者声明。
     */
    public static final String MODE_SKIP = "skip";

    /**
     * 按主键更新：与 {@link #MODE_UPDATE} 行为相同，区别是键直接取目标表的真实主键，
     * 不由流程声明。
     */
    public static final String MODE_PK = "pk";

    /**
     * 补空更新：命中原键的行只补库里为空的列，库里已有值的列一律不动。
     *
     * <p>与 {@link #MODE_UPDATE} 的差别只在「命中的行怎么更新」：合并更新看新值，
     * 补空更新还要看库里原值。详情页回填这类「只为把缺失补齐」的流程必须用它，
     * 否则站点给的另一个版本会覆盖掉别处已经采到、可能更完整的值。</p>
     */
    public static final String MODE_FILL = "fill";

    /**
     * 批次失败策略参数名。
     */
    public static final String ON_BATCH_ERROR = "onBatchError";

    /**
     * 批次失败策略：整批失败即终止（默认，保持既有行为）。
     */
    public static final String BATCH_ERROR_STOP = "stop";

    /**
     * 批次失败策略：记录失败批次与原因，继续写后续批次。
     */
    public static final String BATCH_ERROR_CONTINUE = "continue";

    /**
     * 默认每批行数。
     */
    private static final int DEFAULT_BATCH = 500;

    /**
     * 本次写入行数的暂存变量名，仅供摘要使用。
     */
    private static final String VAR_WRITTEN = "_written";

    /**
     * 本次清空行数的暂存变量名，仅供摘要使用。
     */
    private static final String VAR_CLEARED = "_cleared";

    /**
     * 本次按主键更新行数的暂存变量名，仅供摘要使用。
     */
    private static final String VAR_UPDATED = "_updated";

    /**
     * 本次按主键新增行数的暂存变量名，仅供摘要使用。
     */
    private static final String VAR_INSERTED = "_inserted";

    /**
     * 本次补空更新行数的暂存变量名，仅供摘要使用。
     */
    private static final String VAR_FILLED = "_filled";

    /**
     * 本次因命中业务键而整行跳过的行数暂存变量名，仅供摘要使用。
     */
    private static final String VAR_SKIPPED = "_skipped";

    /**
     * 失败批次数变量名。
     */
    private static final String VAR_FAILED = "_failedBatches";

    /**
     * 最后一条批次失败原因变量名。
     */
    private static final String VAR_LAST_ERROR = "_lastBatchError";

    /**
     * JDBC 通道。
     */
    private final CollectJdbcSupport jdbc;

    @Override
    public String supportShape() {
        return "database";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "数据库", "输出", "database",
                "把一个集合整批写进目标表，不产采集记录");
        meta.setOutputVariable("无出参；写入行数看节点摘要");
        // 主键列同样取自目标表的真实列，因此复用字段映射控件；
        // 但只有 update 模式用得上，append / replace 下不置必填，避免给旧流程标上无谓的红星
        var keyParam = CollectShapeMetas.mapping(KEY + ":主键列", "主键列", TABLE,
                "「跳过」与「覆盖」两种方式需要：一条填一个业务键列，列名必须是上面字段映射里已声明的目标列，"
                        + "联合键填多条；右侧无需填写。"
                        + "「按主键更新」不用填这里，它直接取目标表的主键");
        keyParam.setRequired(false);
        var datasource = CollectShapeMetas.datasource(DATASOURCE, "目标数据库", CollectJdbcSupport.APP_DATASOURCE,
                "默认应用主库；下拉里是数据源中心已启用的数据源");
        datasource.setRequired(true);
        var table = CollectShapeMetas.table(TABLE, "目标表", "",
                "表不存在自动建，缺列自动补；列类型按这一批实际取值推断");
        table.setRequired(true);
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.required(SOURCE, "来源集合", "textarea",
                        "填集合表达式，例 ${rows}；也接受单条映射（按一行处理）"),
                datasource,
                table,
                CollectShapeMetas.option(MODE, "写入方式", MODE_APPEND,
                        "追加：不判断，直接插入；"
                                + "跳过：按下面的主键列命中已有行就整行跳过（已有数据不动）；"
                                + "覆盖：按下面的主键列命中已有行就更新、未命中就新增；"
                                + "补空：按下面的主键列命中已有行时，只补库里为空的那几列，"
                                + "库里已有值的列一律不动（适合只为补齐缺失字段的回填流程）；"
                                + "按主键更新：同「覆盖」，但主键取目标表的真实主键，不用手填；"
                                + "全覆盖：先 TRUNCATE 清空目标表再全量写入（自增列复位）。"
                                + "来源集合解析不出任何行时直接报错并保留原表，不会把表清空",
                        MODE_APPEND, MODE_SKIP, MODE_UPDATE, MODE_FILL, MODE_PK, MODE_REPLACE),
                CollectShapeMetas.mapping(COLUMN + ":目标列", "字段映射", TABLE,
                        "左列选目标表字段，右列填来源字段名或 ${} 表达式；行内键可直接当变量名引用"),
                keyParam,
                CollectShapeMetas.text(BATCH, "每批行数", "text", "默认 " + DEFAULT_BATCH
                        + "；按批提交，值全部走预编译绑定；按主键更新时每批一个事务"),
                CollectShapeMetas.option(ON_BATCH_ERROR, "批次失败时", BATCH_ERROR_STOP,
                        "选 stop：任一批写失败即让节点失败（该批事务回滚，后续批次不再写）；"
                                + "选 continue：记录失败批次与原因、继续写后续批次，节点不失败，"
                                + "失败批次数看节点摘要与 _failedBatches / _lastBatchError 变量",
                        BATCH_ERROR_STOP, BATCH_ERROR_CONTINUE));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        String table = CollectExpression.evalText(node.param(TABLE), variables).trim();
        if (table.isEmpty()) {
            throw new IllegalStateException("数据库节点没有填写目标表");
        }
        List<CollectParamDTO> columns = mapping(node);
        if (columns.isEmpty()) {
            throw new IllegalStateException("数据库节点没有声明字段，请用 column:目标列 逐行填写");
        }
        String mode = CollectExpression.evalText(node.param(MODE), variables).trim();
        // 主键声明要在动表之前校验：配置写错时不能先建出半张表或写出第一批数据
        List<String> keys = updateKeys(node, columns, mode);
        List<Map<String, Object>> rows = buildRows(node, columns, variables);
        String datasource = node.param(DATASOURCE);
        if (rows.isEmpty()) {
            // 覆盖写入绝不能拿空集合去清表：上游一个数都没抓到时，
            // 目标表里昨天的数据才是唯一可信的那份，清掉就等于把历史一起丢了。
            throw new IllegalStateException("来源集合没有解析出任何行，已中止，目标表未做任何改动");
        }
        jdbc.ensureTypedTable(datasource, table, columnsOf(rows, columns));
        // 建表只补缺失列、不会放宽已存在的窄列：老表遇到本批更长的值时先放宽，
        // 否则整批写入会以「值超出列长度」失败（见 widenTextColumns 的说明）
        jdbc.widenTextColumns(datasource, table, rows);
        if (MODE_PK.equals(mode)) {
            // 建表之后才取主键：表不存在时 ensureTypedTable 刚把它建出来，这时才读得到主键
            keys = pkKeys(datasource, table, columns);
        }
        int cleared = 0;
        if (MODE_REPLACE.equals(mode)) {
            cleared = jdbc.truncate(datasource, table);
        }
        int written = 0;
        int updated = 0;
        int inserted = 0;
        int skipped = 0;
        int failed = 0;
        String lastError = null;
        String onError = node.param(ON_BATCH_ERROR).trim();
        int batch = batchSize(node, variables);
        int batchCount = (rows.size() + batch - 1) / batch;
        for (int start = 0; start < rows.size(); start += batch) {
            List<Map<String, Object>> slice = rows.subList(start, Math.min(start + batch, rows.size()));
            try {
                if (MODE_UPDATE.equals(mode) || MODE_PK.equals(mode)) {
                    var result = jdbc.updateByKey(datasource, table, keys, slice);
                    updated += result.updated();
                    inserted += result.inserted();
                    written += result.total();
                } else if (MODE_FILL.equals(mode)) {
                    var result = jdbc.fillBlankByKey(datasource, table, keys, slice);
                    updated += result.updated();
                    inserted += result.inserted();
                    written += result.total();
                } else if (MODE_SKIP.equals(mode)) {
                    var result = jdbc.insertIfAbsent(datasource, table, keys, slice);
                    skipped += result.skipped();
                    inserted += result.inserted();
                    written += result.total();
                } else {
                    written += jdbc.insertBatch(datasource, table, slice);
                }
            } catch (RuntimeException ex) {
                // 批次失败策略：默认 stop 保持既有行为（抛出即节点失败）；continue 记录后继续写后续批次。
                // 只捕获这一批 —— 该批的事务由 CollectJdbcSupport 内部回滚，不会留下半写数据。
                if (!BATCH_ERROR_CONTINUE.equals(onError)) {
                    throw ex;
                }
                failed++;
                lastError = ex.getMessage();
                log.warn("[采集] 批次 {}/{} 写入 {} 失败，按 continue 策略继续: {}",
                        (start / batch) + 1, batchCount, table, ex.getMessage());
            }
        }
        variables.put(VAR_WRITTEN, written);
        variables.put(VAR_CLEARED, cleared);
        if (failed > 0) {
            variables.put(VAR_FAILED, failed);
            variables.put(VAR_LAST_ERROR, lastError);
        }
        if (MODE_UPDATE.equals(mode) || MODE_PK.equals(mode)) {
            variables.put(VAR_UPDATED, updated);
            variables.put(VAR_INSERTED, inserted);
        }
        if (MODE_FILL.equals(mode)) {
            variables.put(VAR_FILLED, updated);
            variables.put(VAR_INSERTED, inserted);
        }
        if (MODE_SKIP.equals(mode)) {
            variables.put(VAR_SKIPPED, skipped);
            variables.put(VAR_INSERTED, inserted);
        }
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        String result;
        Object skipped = variables.get(VAR_SKIPPED);
        Object filled = variables.get(VAR_FILLED);
        Object updated = variables.get(VAR_UPDATED);
        if (skipped != null) {
            result = "跳过已存在 " + skipped + " 行、新增 " + variables.get(VAR_INSERTED) + " 行";
        } else if (filled != null) {
            result = "补空更新 " + filled + " 行（库里已有值的列未动）、新增 "
                    + variables.get(VAR_INSERTED) + " 行";
        } else if (updated != null) {
            result = "按主键更新 " + updated + " 行、新增 " + variables.get(VAR_INSERTED) + " 行";
        } else {
            Object written = variables.get(VAR_WRITTEN);
            if (written == null) {
                return null;
            }
            Object cleared = variables.get(VAR_CLEARED);
            if (cleared instanceof Integer count && count > 0) {
                result = "全覆盖写入 " + written + " 行（先清空 " + count + " 行）";
            } else {
                result = "写入 " + written + " 行";
            }
        }
        // 批次失败策略为 continue 时节点不失败，失败信息必须出现在摘要里 —— 否则「部分写入成功」
        // 会被看成「全部成功」，这正是把失败信息藏起来最危险的地方。
        Object failed = variables.get(VAR_FAILED);
        if (failed instanceof Integer count && count > 0) {
            result = result + "；有 " + count + " 批写入失败（按策略已继续）：" + variables.get(VAR_LAST_ERROR);
        }
        return result;
    }

    /**
     * 读字段映射声明。
     *
     * @param node 节点
     * @return 目标列到取值表达式的声明，键已去空白
     */
    private List<CollectParamDTO> mapping(CollectNode node) {
        List<CollectParamDTO> columns = new ArrayList<>();
        for (CollectParamDTO param : node.pairsOf(COLUMN)) {
            if (param.name() != null && !param.name().isBlank()) {
                columns.add(param);
            }
        }
        return columns;
    }

    /**
     * 读需要业务键的写入方式（跳过 / 覆盖）的键声明。
     *
     * <p>键是这两种模式的唯一定位依据，缺省时绝不能退化成追加：同一批数据每跑一次
     * 就多一份，错误要到对账时才会暴露，所以这里直接报错。键列还必须已经出现在
     * 字段映射的目标列里，否则没有来源值可绑定，定位条件会恒为同一个值。</p>
     *
     * <p>「按主键更新」（{@link #MODE_PK}）不走这里：它的键取自目标表元数据，
     * 不由流程声明，见 {@link #pkKeys}。</p>
     *
     * @param node    节点
     * @param columns 字段映射声明
     * @param mode    写入方式
     * @return 键列名，不需要键的模式返回空列表
     * @throws IllegalStateException 未声明键，或键列不在字段映射的目标列里
     */
    private List<String> updateKeys(CollectNode node, List<CollectParamDTO> columns, String mode) {
        if (!MODE_UPDATE.equals(mode) && !MODE_SKIP.equals(mode) && !MODE_FILL.equals(mode)) {
            return List.of();
        }
        String label = MODE_SKIP.equals(mode) ? "skip" : (MODE_FILL.equals(mode) ? "fill" : "update");
        List<String> targets = new ArrayList<>(columns.size());
        for (CollectParamDTO column : columns) {
            targets.add(column.name().trim());
        }
        List<String> keys = new ArrayList<>();
        for (CollectParamDTO pair : node.pairsOf(KEY)) {
            if (pair.name() == null || pair.name().isBlank()) {
                continue;
            }
            String name = pair.name().trim();
            if (!targets.contains(name)) {
                throw new IllegalStateException(label + " 模式的主键列 " + name
                        + " 不在字段映射的目标列里，请先用 column:" + name + " 声明该列");
            }
            if (!keys.contains(name)) {
                keys.add(name);
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException(label + " 模式需要声明主键，请用 key:列名 逐行填写（联合键填多条）");
        }
        return keys;
    }

    /**
     * 「按主键更新」模式的键：直接取目标表的真实主键列。
     *
     * <p>表还没有主键时直接报错而不是退回「覆盖」：这两种模式定位的是不同的行，
     * 悄悄换一种会让"更新失败"变成"数据被改坏"，比报错难查得多。</p>
     *
     * @param datasource 目标数据源
     * @param table      目标表
     * @param columns    字段映射声明
     * @return 主键列名
     * @throws IllegalStateException 表无主键，或主键列不在字段映射的目标列里
     */
    private List<String> pkKeys(String datasource, String table, List<CollectParamDTO> columns) {
        List<String> keys = jdbc.primaryKeys(datasource, table);
        if (keys.isEmpty()) {
            throw new IllegalStateException("按主键更新需要目标表 " + table
                    + " 有主键；这张表没有，请改用「覆盖」并在下面声明主键列");
        }
        List<String> targets = new ArrayList<>(columns.size());
        for (CollectParamDTO column : columns) {
            targets.add(column.name().trim());
        }
        for (String key : keys) {
            if (!targets.contains(key)) {
                throw new IllegalStateException("按主键更新时主键列 " + key
                        + " 不在字段映射的目标列里，请先用 column:" + key + " 声明该列");
            }
        }
        return keys;
    }

    /**
     * 按映射声明求出待写入的行。
     *
     * <p>每行的作用域是「令牌变量 + 该行键」，与输出节点一致：集合项的键直接
     * 成为同名字段，所以抽取节点产出的 {@code ${rows}} 可以原样喂进来，
     * 映射值既可以写 {@code ${name}}，也可以直接写 {@code name}。</p>
     *
     * @param node      节点
     * @param columns   字段映射声明
     * @param variables 令牌作用域
     * @return 待写入行
     */
    private List<Map<String, Object>> buildRows(CollectNode node, List<CollectParamDTO> columns,
                                                 Map<String, Object> variables) {
        Object source = CollectExpression.eval(node.param(SOURCE), variables);
        List<Object> items = new ArrayList<>();
        if (source instanceof Collection<?> collection) {
            items.addAll(collection);
        } else if (source instanceof Map<?, ?> row) {
            items.add(row);
        } else if (source != null) {
            items.add(source);
        }
        List<Map<String, Object>> rows = new ArrayList<>(items.size());
        for (Object item : items) {
            Map<String, Object> scope = scopeOf(variables, item);
            Map<String, Object> row = new LinkedHashMap<>();
            for (CollectParamDTO column : columns) {
                row.put(column.name().trim(), CollectExpression.eval(column.value(), scope));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 构造单行作用域：行内键覆盖令牌变量，集合项始终可用 {@code item} 引用。
     *
     * @param variables 令牌作用域
     * @param item      集合项
     * @return 本行作用域
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
     * 按这一批实际取值推断列定义。
     *
     * <p>只看抽样而不是全量：列类型是建表时一次性决定的，取若干行足以区分
     * 数字与布尔，再往下扫几十万行只是在做重复的类型判断。</p>
     *
     * <p>但**文本长度不能按抽样定**：抽样命中长值只说明这一批里有长值，
     * 抽样没命中长值也不代表后续批次没有。列宽是建表时一次性钉死的，
     * 一旦按短值落成 {@code VARCHAR(255)}，后续批次里更长的值就会写入报错
     * （2026-11-04 实测：GitHub 的 description 混有 120 与 343 两档，
     * 首批抽样最长 ≤200 落成 VARCHAR(255)，第二批 343 字符那条直接
     * {@code Value too long for column "description CHARACTER VARYING(255)"}）。
     * 因此文本列一律落大文本，不参与长度推断。</p>
     *
     * @param rows    待写入行
     * @param columns 字段映射声明，用于补列注释
     * @return 列定义
     */
    private List<DdlColumn> columnsOf(List<Map<String, Object>> rows, List<CollectParamDTO> columns) {
        List<String> names = new ArrayList<>(rows.get(0).keySet());
        List<DdlColumn> definitions = new ArrayList<>(names.size());
        for (String name : names) {
            String comment = columns.stream()
                    .filter(column -> column.name() != null && column.name().trim().equals(name))
                    .map(CollectParamDTO::value)
                    .findFirst()
                    .orElse(null);
            definitions.add(new DdlColumn(CollectJdbcSupport.identifier(name), null, Boolean.TRUE,
                    comment, null, name, sourceType(rows, name)));
        }
        return definitions;
    }

    /**
     * 推断某一列的来源类型。
     *
     * <p>只区分「数值 / 布尔 / JSON / 文本」四类，文本类的具体长度不在这里定：
     * 采到的字符串有多长只有写进去那一刻才知道，而列宽是建表时就钉死的，
     * 按抽样长度定列宽必然会在后续批次上翻车（见 {@link #columnsOf} 的说明）。
     * 文本一律返回 {@code TEXT}，由数据源模块落成能容纳任意长度的大文本列。</p>
     *
     * @param rows 待写入行
     * @param name 列名
     * @return 数据源模块认识的来源类型标识
     */
    private String sourceType(List<Map<String, Object>> rows, String name) {
        boolean integer = false;
        boolean decimal = false;
        boolean bool = false;
        boolean other = false;
        for (Map<String, Object> row : rows) {
            Object value = row.get(name);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean) {
                bool = true;
            } else if (value instanceof BigDecimal) {
                decimal = true;
            } else if (value instanceof Number) {
                if (value instanceof Double || value instanceof Float) {
                    decimal = true;
                } else {
                    integer = true;
                }
            } else if (!(value instanceof CharSequence)) {
                other = true;
            }
        }
        if (other) {
            // 集合、对象这类值由 JDBC 通道序列化成 JSON 文本落库，建成 JSON 语义列更贴切
            return "JSON";
        }
        if (bool && !integer && !decimal) {
            return "BOOLEAN";
        }
        if (integer && !decimal) {
            return "BIGINT";
        }
        if (decimal) {
            return "DECIMAL";
        }
        return "TEXT";
    }

    /**
     * 取每批行数，非法值退回默认。
     *
     * @param node      节点
     * @param variables 令牌作用域
     * @return 每批行数，至少为 1
     */
    private int batchSize(CollectNode node, Map<String, Object> variables) {
        String raw = CollectExpression.evalText(node.param(BATCH), variables).trim();
        if (raw.isEmpty()) {
            return DEFAULT_BATCH;
        }
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : DEFAULT_BATCH;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("每批行数不是整数: " + raw);
        }
    }
}
