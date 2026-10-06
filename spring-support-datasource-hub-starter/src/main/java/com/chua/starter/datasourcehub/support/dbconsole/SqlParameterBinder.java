package com.chua.starter.datasourcehub.support.dbconsole;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.chua.starter.datasourcehub.support.dbconsole.SqlScriptSplitter.Span;

/**
 * SQL 参数占位符替换（查询控制台的「参数化」）。
 *
 * <p><b>为什么不用 {@code :name}</b>：{@code :} 在 SQL 里有别的含义
 * （PostgreSQL 的 {@code ::} 类型转换、MySQL 的时间字面量 {@code 12:30:00}），
 * 用 {@code :} 做占位符会误伤。这里只认 <b>{@code ${name}}</b> 一种写法，
 * 它在 SQL 文本里几乎不出现，冲突面最小。</p>
 *
 * <p><b>安全性</b>：替换进去的是**字面量**，不是原始片段 ——
 * 数字直接输出，其余一律按内核字面量规则加引号并转义，
 * 所以参数值里带 {@code '} 或 {@code ;} 不会变成注入。
 * 变量名本身也过白名单（字母数字下划线），否则占位符语法能被用来拼标识符。</p>
 *
 * <p><b>找不到变量就报错</b>，不替换成空串或 {@code NULL}：
 * 后者会让 {@code WHERE name = ${name}} 变成恒真，查询悄悄返回全表 ——
 * 这类「看起来成功、结果是错的」比直接失败危险得多。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public final class SqlParameterBinder {

    /** 占位符语法：{@code ${name}}，name 为字母/数字/下划线。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private SqlParameterBinder() {
    }

    /**
     * 替换脚本里的占位符。
     *
     * @param script SQL 脚本
     * @param params 变量表；为 null 或空表示不需要替换
     * @return 替换后的脚本（无占位符时原样返回同一引用）
     * @throws IllegalArgumentException 引用了未提供的变量
     */
    public static String substitute(String script, Map<String, Object> params) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        Map<String, Object> table = params == null ? Map.of() : params;
        // 只在「代码」区间里找占位符：字符串字面量与注释里的 ${x} 是普通文本。
        // 若也替换，值自带引号会与原文那对引号叠成 ''value''，
        // 用户看到的是语法错误，指不到「占位符写在了引号里」。
        List<int[]> hits = new ArrayList<>();
        List<String> used = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Span span : SqlScriptSplitter.codeSpans(script)) {
            Matcher m = PLACEHOLDER.matcher(script);
            m.region(span.start(), span.end());
            while (m.find()) {
                hits.add(new int[] {m.start(), m.end()});
                seen.add(m.group(1));
                if (!used.contains(m.group(1))) {
                    used.add(m.group(1));
                }
            }
        }
        if (hits.isEmpty()) {
            return script;
        }
        // 先校验再替换：中途才发现缺参数会留下替换到一半的脚本，
        // 报错位置也与真正的缺参位置对不上。
        List<String> missing = new ArrayList<>();
        for (String name : used) {
            if (!table.containsKey(name)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("参数 " + String.join("、", missing) + " 未提供"
                    + "（脚本里用到了 " + used.size() + " 个占位符: " + String.join("、", used) + "）");
        }
        StringBuilder out = new StringBuilder(script.length() + 32);
        int cursor = 0;
        for (int[] hit : hits) {
            out.append(script, cursor, hit[0]);
            Matcher one = PLACEHOLDER.matcher(script);
            one.region(hit[0], hit[1]);
            if (!one.find()) {
                // 理论上不可能：hit 就是这么匹配出来的。留这条分支是为了
                // 万一 pattern 变了，这里会显式炸掉而不是静默吞掉一个占位符
                throw new IllegalStateException("占位符区间与 pattern 不一致: " + script.substring(hit[0], hit[1]));
            }
            out.append(toLiteral(table.get(one.group(1))));
            cursor = hit[1];
        }
        out.append(script, cursor, script.length());
        return out.toString();
    }

    /**
     * 找出脚本里用到的全部占位符名。
     *
     * <p>用于 UI 侧提示「这个脚本需要哪些变量」，不必真的执行一次。
     * 注意：这是**纯文本扫描**，注释与字符串里的 {@code ${x}} 也会被算进去 ——
     * 宁可多提示一个变量，也不漏提示。</p>
     *
     * @param script SQL 脚本
     * @return 按出现顺序去重的变量名
     */
    public static List<String> requiredNames(String script) {
        Set<String> names = new LinkedHashSet<>();
        if (script == null) {
            return List.of();
        }
        // 与 substitute 同一套判定（只看代码区间）：
        // 若这里按纯文本扫，UI 会为一个不会被替换的 ${x} 弹输入框，
        // 用户填完发现结果没变，而界面上没有任何地方说明为什么。
        for (Span span : SqlScriptSplitter.codeSpans(script)) {
            Matcher m = PLACEHOLDER.matcher(script);
            m.region(span.start(), span.end());
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return List.copyOf(names);
    }

    /**
     * 脚本里有没有占位符。
     *
     * @param script SQL 脚本
     * @return true=有
     */
    public static boolean hasPlaceholder(String script) {
        if (script == null) {
            return false;
        }
        for (Span span : SqlScriptSplitter.codeSpans(script)) {
            Matcher m = PLACEHOLDER.matcher(script);
            m.region(span.start(), span.end());
            if (m.find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把参数值转成可安全内联的 SQL 字面量。
     *
     * @param value 参数值
     * @return 字面量文本
     */
    public static String toLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean flag) {
            return flag ? "1" : "0";
        }
        if (value instanceof Number number) {
            // NaN / Infinity 不是合法字面量，转成字符串比输出 "NaN" 让内核报错更好
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return quote(String.valueOf(number));
            }
            return String.valueOf(number);
        }
        if (value instanceof Character || value instanceof CharSequence
                || value instanceof Temporal || value instanceof java.util.Date
                || value instanceof byte[] || value instanceof java.sql.Clob) {
            return quote(String.valueOf(value));
        }
        // 兜底：结构化值一律当字符串，不去猜它的类型
        return quote(String.valueOf(value));
    }

    /**
     * 加引号并转义。
     *
     * @param value 文本
     * @return 字面量
     */
    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('\'');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '\'') {
                out.append('\\');
            }
            out.append(c);
        }
        out.append('\'');
        return out.toString();
    }

    /**
     * 校验变量名合法性。
     *
     * @param name 变量名
     * @return 原名
     * @throws IllegalArgumentException 名字不合法
     */
    public static String checkName(String name) {
        String key = name == null ? "" : name.trim();
        if (key.isEmpty() || !key.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("参数名只能由字母、数字、下划线组成且以字母或下划线开头: " + name);
        }
        return key;
    }

    /**
     * 把前端传来的变量表规整成合法名字的映射。
     *
     * @param raw 原始表
     * @return 规整后的表（保持传入顺序，便于提示）
     * @throws IllegalArgumentException 某个变量名不合法
     */
    public static Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            try {
                out.put(checkName(e.getKey()), e.getValue());
            } catch (IllegalArgumentException ex) {
                bad.add(String.valueOf(e.getKey()));
            }
        }
        if (!bad.isEmpty()) {
            throw new IllegalArgumentException("参数名不合法: " + String.join(", ", bad));
        }
        return out;
    }
}