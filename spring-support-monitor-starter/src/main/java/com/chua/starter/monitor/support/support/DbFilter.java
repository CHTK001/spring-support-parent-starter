package com.chua.starter.monitor.support.support;

import com.chua.common.support.lang.datasource.dialect.SqlName;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Navicat 式结构化筛选条件（表数据页的「高级筛选」）。
 *
 * <p><b>为什么不只靠 {@code WHERE} 文本框</b>：Navicat 的筛选面板是
 * 「列 + 运算符 + 值」三段式，能拼出区间、{@code IN} 多选、{@code IS NULL}
 * 这些手写很难写对的条件。本类把这些条件编译成
 * {@code WHERE} 片段与**参数值列表**，由调用方交给
 * {@code PreparedStatement} 绑定 —— 也就是说值一律走占位符，不做字符串拼接。</p>
 *
 * <p><b>列名必须先过白名单</b>：它是拼进 SQL 的标识符，
 * {@link #compile} 只接受 {@code checkSimple} 通过的名字。
 * 值则相反，<b>原样交给 PreparedStatement</b>，所以值里的引号、分号、
 * 注释符都不构成注入面 —— 与 {@code where} 文本框那条路径不同，
 * 那条路径只能靠「拒绝分隔符」这种黑名单（见 {@code sanitizeWhere}）。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public final class DbFilter {

    /** 单条筛选条件 */
    public record Condition(String column, String op, String value, String value2) {
        /** 规整构造：空白列名/运算符直接判为无效，避免上层各处重复判空 */
        public Condition {
            column = column == null ? "" : column.trim();
            op = op == null ? "" : op.trim().toUpperCase(Locale.ROOT);
            value = value == null ? "" : value;
            value2 = value2 == null ? "" : value2;
        }
    }

    /** 编译结果：WHERE 片段（不含 {@code WHERE} 关键字）与按顺序的参数值 */
    public record Compiled(String whereSql, List<Object> params) {
        /**
         * 规范构造器：参数列表做防御性拷贝。
         *
         * @param whereSql WHERE 片段
         * @param params   参数值
         */
        public Compiled {
            params = params == null ? List.of() : List.copyOf(params);
        }

        /**
         * 是否为空条件（无需拼 WHERE）。
         *
         * @return true=无需过滤
         */
        public boolean isEmpty() {
            return whereSql == null || whereSql.isBlank();
        }
    }

    /** 支持的运算符 → SQL 片段模板。{@code {0}} 是列名占位，{@code ?} 是参数占位 */
    private static final List<String[]> OPS = List.of(
            new String[] {"=", "`{0}` = ?"},
            new String[] {"<>", "`{0}` <> ?"},
            new String[] {"!=", "`{0}` <> ?"},
            new String[] {">", "`{0}` > ?"},
            new String[] {">=", "`{0}` >= ?"},
            new String[] {"<", "`{0}` < ?"},
            new String[] {"<=", "`{0}` <= ?"},
            new String[] {"LIKE", "`{0}` LIKE ?"},
            new String[] {"NOT LIKE", "`{0}` NOT LIKE ?"},
            new String[] {"IS NULL", "`{0}` IS NULL"},
            new String[] {"IS NOT NULL", "`{0}` IS NOT NULL"});

    private DbFilter() {
    }

    /** 供 UI 渲染下拉用的运算符清单 */
    public static List<String> operators() {
        List<String> out = new ArrayList<>(OPS.size() + 2);
        for (String[] pair : OPS) {
            // <> 与 != 是同一个语义，去重后 UI 列表更干净
            if (!out.contains(pair[0])) {
                out.add(pair[0]);
            }
        }
        out.add("IN");
        out.add("NOT IN");
        out.add("BETWEEN");
        return out;
    }

    /**
     * 编译条件列表。
     *
     * <p>多个条件按 {@code AND} 连接 —— 这与 Navicat 的默认行为一致
     * （它另有「或」分组，本控制台暂不提供，避免 UI 上出现看不懂的半吊子分组）。</p>
     *
     * @param conditions 条件列表，可为空
     * @return 编译结果；无条件时 {@code whereSql} 为空串
     * @throws IllegalArgumentException 列名非法或运算符不认识
     */
    public static Compiled compile(List<Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return new Compiled("", List.of());
        }
        List<String> parts = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Condition c : conditions) {
            if (c.column().isEmpty()) {
                continue;
            }
            String column = SqlName.checkSimple(c.column(), "筛选列名");
            String op = c.op();
            if ("IN".equals(op) || "NOT IN".equals(op)) {
                // 多值按逗号切；空值列表直接跳过这一条 —— 拼出 `IN ()` 是语法错误，
                // 而用户看到「条件没生效」比看到「这一条被忽略」更难排查
                List<String> values = splitIn(c.value());
                if (values.isEmpty()) {
                    continue;
                }
                StringBuilder ph = new StringBuilder();
                for (String v : values) {
                    if (ph.length() > 0) {
                        ph.append(", ");
                    }
                    ph.append('?');
                    params.add(coerce(v));
                }
                parts.add("`" + column + "` " + op + " (" + ph + ")");
                continue;
            }
            if ("BETWEEN".equals(op)) {
                if (c.value().isEmpty() || c.value2().isEmpty()) {
                    // 区间缺一端就不能比较，跳过而不是拼出 `BETWEEN ?`
                    continue;
                }
                parts.add("`" + column + "` BETWEEN ? AND ?");
                params.add(coerce(c.value()));
                params.add(coerce(c.value2()));
                continue;
            }
            String template = templateFor(op);
            if (template == null) {
                throw new IllegalArgumentException("不支持的运算符: " + op);
            }
            if (template.contains("?")) {
                if (c.value().isEmpty()) {
                    // = ? 传空串会得到「等于空字符串」而不是「等于 NULL」，
                    // 与用户预期差很远；显式跳过并让 UI 提示更诚实
                    continue;
                }
                params.add(coerce(c.value()));
            }
            parts.add(template.replace("{0}", column));
        }
        return new Compiled(String.join(" AND ", parts), params);
    }

    /**
     * 找运算符对应的模板。
     *
     * @param op 运算符（已大写）
     * @return 模板；不认识返回 null
     */
    private static String templateFor(String op) {
        for (String[] pair : OPS) {
            if (pair[0].equals(op)) {
                return pair[1];
            }
        }
        return null;
    }

    /**
     * 切分 {@code IN} 的多值。
     *
     * <p>支持英文逗号与中文逗号 —— 中文输入法下打出的 {@code ，} 很常见，
     * 而静默忽略会让用户以为筛选没生效。</p>
     *
     * @param value 原始文本
     * @return 值列表（去空白、去空项）
     */
    public static List<String> splitIn(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return out;
        }
        for (String part : value.split("[,，]")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * 把文本值转成合适的绑定类型。
     *
     * <p>纯数字转成数字，才能让 {@code id > 100} 走索引；
     * 保持字符串则内核要做隐式转换，{@code VARCHAR} 列上还会发生
     * 「数字与文本比较」这种静默走错索引的行为。
     * 非数字一律按字符串 —— 宁可走全表扫描，也不要猜错类型导致结果不对。</p>
     *
     * @param value 原始文本
     * @return 绑定值
     */
    private static Object coerce(String value) {
        String v = value.trim();
        if (v.isEmpty()) {
            return v;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException ignored) {
            return v;
        }
    }
}