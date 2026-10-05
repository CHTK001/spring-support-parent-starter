package com.chua.starter.eduai.collect.support;

import com.chua.common.support.lang.datasource.dialect.SqlName;
import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * 通用入库支持：目标表白名单 + 列元数据 + 唯一键冲突策略（跳过 / 覆盖）。
 *
 * <p>供「数据表入库」采集源使用：把三方 JSON 按字段映射写入指定表，无需为每张表写映射文件。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbCollectSupport {

    /**
     * TEXT/BLOB 等大对象列的精度下限：精度大于该值的列视为「不限制长度」，不做截断
     */
    private static final int LOB_PRECISION_THRESHOLD = 65535;

    /**
     * 允许写入的表白名单（表名 → 展示名）——只放开业务内容表，避免任意表写入风险
     */
    private static final Map<String, String> ALLOWED_TABLES = new LinkedHashMap<>() {{
        put("edu_idiom", "成语库");
        put("edu_question", "题库");
        put("edu_poem", "古诗库");
        put("edu_knowledge_point", "知识点");
        put("edu_hanzi", "汉字库");
    }};

    /**
     * 各表建议的唯一键（多列逗号分隔），前端默认选中
     */
    private static final Map<String, String> SUGGESTED_KEYS = Map.of(
            "edu_idiom", "idiom_content",
            "edu_question", "question_stem",
            "edu_poem", "poem_title,poem_author",
            "edu_knowledge_point", "knowledge_point_name",
            "edu_hanzi", "hanzi_char"
    );

    private final JdbcTemplate jdbcTemplate;

    /**
     * 静态引用（采集 SPI 非 Spring Bean，需要静态取用）
     */
    private static DbCollectSupport instance;

    /**
     * 注入后暴露静态引用。
     */
    @PostConstruct
    void init() {
        instance = this;
    }

    /**
     * 获取静态引用（供 SPI 采集源使用）。
     *
     * @return 实例，未初始化返回 null
     */
    public static DbCollectSupport get() {
        return instance;
    }

    /**
     * JdbcTemplate（供采集源做表专用补充写入，如成语学段映射）。
     *
     * @return JdbcTemplate
     */
    public JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    /**
     * 白名单表清单（含列与建议唯一键），供前端下拉与字段映射。
     *
     * @return 表元数据列表
     */
    public List<Map<String, Object>> tableMetas() {
        List<Map<String, Object>> list = new ArrayList<>();
        ALLOWED_TABLES.forEach((table, label) -> {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", table);
            meta.put("label", label);
            meta.put("uniqueKey", SUGGESTED_KEYS.getOrDefault(table, ""));
            meta.put("columns", columns(table));
            list.add(meta);
        });
        return list;
    }

    /**
     * 读取表列名与类型。
     *
     * @param table 表名（必须在白名单内）
     * @return 列元数据（name/type/size）
     */
    public List<Map<String, String>> columns(String table) {
        String name = normalize(table);
        List<Map<String, String>> columns = new ArrayList<>();
        if (name == null) {
            return columns;
        }
        try {
            Map<String, Integer> sizes = columnLengths(name);
            for (String column : columnNames(name)) {
                Map<String, String> col = new LinkedHashMap<>();
                col.put("name", column);
                col.put("type", "");
                // 附加列宽（字符数），供入库时截断保护；-1 表示不限制
                col.put("size", String.valueOf(sizes.getOrDefault(column, -1)));
                columns.add(col);
            }
        } catch (Exception e) {
            log.warn("[collect] 读取表结构失败: table={} cause={}", name, e.getMessage());
        }
        return columns;
    }

    /**
     * 读取表各列的最大字符数。
     *
     * <p>用于入库前的长度保护：MySQL/MariaDB 在非严格模式会截断，在严格模式则直接报错，
     * 而报错会让<b>整批</b>采集失败。TEXT/BLOB 这类大对象列的精度值很大，
     * 这里按 {@link #LOB_PRECISION_THRESHOLD} 视为「不限制」，不做截断。</p>
     *
     * @param table 表名（已通过白名单校验）
     * @return 列名（小写）到最大字符数的映射；无限制的列不出现在结果里
     */
    private Map<String, Integer> columnLengths(String table) {
        Map<String, Integer> sizes = jdbcTemplate.query(
                "SELECT * FROM " + table + " WHERE 1=0",
                (ResultSetExtractor<Map<String, Integer>>) rs -> {
                    Map<String, Integer> map = new LinkedHashMap<>();
                    ResultSetMetaData md = rs.getMetaData();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        int size = md.getPrecision(i);
                        if (size > 0 && size <= LOB_PRECISION_THRESHOLD) {
                            map.put(md.getColumnName(i).toLowerCase(Locale.ROOT), size);
                        }
                    }
                    return map;
                });
        return sizes == null ? Map.of() : sizes;
    }

    /**
     * 读取表列名（小写）。
     *
     * @param table 表名
     * @return 列名列表
     */
    private List<String> columnNames(String table) {
        List<String> names = jdbcTemplate.query(
                "SELECT * FROM " + table + " WHERE 1=0",
                (ResultSetExtractor<List<String>>) rs -> {
                    List<String> list = new ArrayList<>();
                    ResultSetMetaData md = rs.getMetaData();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        list.add(md.getColumnName(i).toLowerCase(Locale.ROOT));
                    }
                    return list;
                });
        return names == null ? List.of() : names;
    }

    /**
     * 三方 JSON 取数预览：返回前 limit 条的原始字段（供配置字段映射前确认字段名）。
     *
     * @param url      三方接口地址
     * @param dataPath 数组路径（点号，可空）
     * @param limit    预览条数
     * @return 原始条目列表
     */
    public List<Map<String, Object>> preview(String url, String dataPath, int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String json = com.chua.starter.eduai.collect.support.HttpFetchSupport.get(url);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode array = root;
            if (dataPath != null && !dataPath.isBlank()) {
                for (String segment : dataPath.split("\\.")) {
                    if (array == null || segment.isBlank()) {
                        continue;
                    }
                    array = array.get(segment);
                }
            } else if (!root.isArray()) {
                array = root.path("data");
            }
            if (array == null || !array.isArray()) {
                return list;
            }
            int count = 0;
            for (JsonNode node : array) {
                if (count++ >= Math.max(1, limit)) {
                    break;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                if (node.isObject()) {
                    node.fields().forEachRemaining(e -> item.put(e.getKey(),
                            e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString()));
                }
                list.add(item);
            }
        } catch (Exception e) {
            throw new IllegalStateException("预览失败: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * 批量写入：按唯一键选择跳过或覆盖。
     *
     * @param table      目标表
     * @param uniqueKeys 唯一键列（可空；为空则纯插入）
     * @param overwrite  true=覆盖（存在则更新），false=跳过
     * @param rows       行数据（列名 → 值）
     * @return 计数：{inserted, updated, skipped}
     */
    public Map<String, Integer> upsert(String table, List<String> uniqueKeys, boolean overwrite,
                                       List<Map<String, Object>> rows) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        counter.put("inserted", 0);
        counter.put("updated", 0);
        counter.put("skipped", 0);
        String name = normalize(table);
        if (name == null || rows == null || rows.isEmpty()) {
            return counter;
        }
        List<String> tableColumns = columnNames(name);
        Map<String, Integer> columnSizes = columnLengths(name);
        List<String> keys = uniqueKeys == null ? List.of() : uniqueKeys.stream()
                .map(this::normalizeIdentifier)
                .filter(k -> !k.isEmpty())
                .distinct()
                .peek(k -> requireColumn(tableColumns, k, "唯一键"))
                .toList();
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            row.forEach((k, v) -> {
                if (k != null && v != null) {
                    String column = normalizeIdentifier(k);
                    requireColumn(tableColumns, column, "写入列");
                    data.put(column, clampToColumnWidth(name, column, v, columnSizes));
                }
            });

            if (data.isEmpty()) {
                continue;
            }
            Long existsId = keys.isEmpty() ? null : findId(name, keys, data);
            if (existsId != null) {
                if (!overwrite) {
                    counter.merge("skipped", 1, Integer::sum);
                    continue;
                }
                update(name, existsId, data);
                counter.merge("updated", 1, Integer::sum);
            } else {
                insert(name, data);
                counter.merge("inserted", 1, Integer::sum);
            }
        }
        return counter;
    }

    /**
     * 按目标列宽截断字符串值。
     *
     * <p>三方数据源不可控：某个字段（如把数组序列化成 JSON 的诗体/平仄标注）轻易超过
     * VARCHAR 列宽。MariaDB 严格模式会抛 {@code Data too long}，
     * 而这会让<b>整批</b>采集全部回滚——几百行数据因为一行超长而白跑。
     * 这里在写库前按字符截断，把影响收敛到单行，并留下告警便于排查映射配置。</p>
     *
     * @param table  表名
     * @param column 目标列
     * @param value  原始值
     * @param sizes  列宽映射（列名 → 最大字符数）
     * @return 截断后的值；非字符串或无列宽限制时原样返回
     */
    private Object clampToColumnWidth(String table, String column, Object value,
                                      Map<String, Integer> sizes) {
        if (!(value instanceof String text)) {
            return value;
        }
        Integer limit = sizes.get(column);
        if (limit == null || limit <= 0 || text.length() <= limit) {
            return value;
        }
        log.warn("[collect] 字段超出列宽已截断: table={} column={} 长度={} -> {}", table, column,
                text.length(), limit);
        return text.substring(0, limit);
    }

    /**
     * 按唯一键查主键（约定第一列为主键，取 id 结尾或首列）。
     *
     * <p>安全豁免：表名命中模块表白名单，唯一键与主键列必须同时命中 JDBC 列元数据，
     * 所有查询值通过参数绑定。</p>
     *
     * @param table 表名
     * @param keys  唯一键列
     * @param data  行数据
     * @return 主键值，未找到返回 null
     */
    private Long findId(String table, List<String> keys, Map<String, Object> data) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (String key : keys) {
            Object value = data.get(key);
            if (value == null) {
                continue;
            }
            if (where.length() > 0) {
                where.append(" AND ");
            }
            where.append(key).append(" = ?");
            args.add(value);
        }
        if (where.length() == 0) {
            return null;
        }
        String idColumn = idColumnOf(table);
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT " + idColumn + " FROM " + table + " WHERE " + where + " LIMIT 1",
                Long.class, args.toArray());
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 插入一行白名单业务数据。
     *
     * <p>安全豁免：表名和列名经模块白名单、标识符白名单及 JDBC 元数据三重校验，
     * 所有行值通过参数绑定。</p>
     */
    private void insert(String table, Map<String, Object> data) {
        String columns = String.join(", ", data.keySet());
        String placeholders = String.join(", ", data.keySet().stream().map(k -> "?").toList());
        jdbcTemplate.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")",
                data.values().toArray());
    }

    /**
     * 更新一行白名单业务数据。
     *
     * <p>安全豁免：动态列名来自 JDBC 元数据且行值全部参数绑定。</p>
     */
    private void update(String table, Long id, Map<String, Object> data) {
        String idColumn = idColumnOf(table);
        List<Object> args = new ArrayList<>(data.values());
        String sets = String.join(", ", data.keySet().stream().map(k -> k + " = ?").toList());
        args.add(id);
        jdbcTemplate.update("UPDATE " + table + " SET " + sets + " WHERE " + idColumn + " = ?", args.toArray());
    }

    /**
     * 推导主键列：优先 <表名去前缀>_id，其次含 id 的第一列。
     *
     * @param table 表名
     * @return 主键列名
     */
    private String idColumnOf(String table) {
        List<String> cols = columnNames(table);
        String prefix = table.startsWith("edu_") ? table.substring(4) : table;
        String prefer = prefix + "_id";
        if (cols.contains(prefer)) {
            return prefer;
        }
        for (String col : cols) {
            if (col.endsWith("_id")) {
                return col;
            }
        }
        return cols.isEmpty() ? "id" : cols.get(0);
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty()) {
            return SqlName.checkSimple(normalized, "SQL 列标识符");
        }
        return normalized;
    }

    private void requireColumn(List<String> columns, String identifier, String label) {
        if (!columns.contains(identifier.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(label + "不属于表白名单: " + identifier);
        }
    }

    /**
     * 表名规范化（白名单校验）。

     *
     * @param table 表名
     * @return 合法表名，非法返回 null
     */
    private String normalize(String table) {
        if (table == null || table.isBlank()) {
            return null;
        }
        String name = table.trim().toLowerCase(Locale.ROOT);
        return SqlName.checkAllowedSimple(name, "写入表名", ALLOWED_TABLES.keySet());
    }
}
