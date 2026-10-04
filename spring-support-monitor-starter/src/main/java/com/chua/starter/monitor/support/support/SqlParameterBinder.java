package com.chua.starter.monitor.support.support;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        // 第一遍只收集用到的变量名，缺一个就整体报错。
        // 不这么做的后果：循环里替换到第 3 个占位符才发现第 1 个缺参数，
        // 异常从 matcher.appendReplacement 之间抛出，报错位置与真正的缺参位置对不上，
        // 而 StringBuffer 里还留着一段替换到一半的脚本，很容易被误当成「脚本被改坏了」。
        List<String> missing = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        Matcher scan = PLACEHOLDER.matcher(script);
        while (scan.find()) {
            String name = scan.group(1);
            used.add(name);
            if (!table.containsKey(name) && !missing.contains(name)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("参数 " + String.join("、", missing) + " 未提供"
                    + "（脚本里用到了 " + used.size() + " 个占位符: " + String.join("、", used) + "）");
        }
        StringBuffer sb = new StringBuffer(script.length() + 32);
        Matcher matcher = PLACEHOLDER.matcher(script);
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(toLiteral(table.get(matcher.group(1)))));
        }
        matcher.appendTail(sb);
        return sb.toString();
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
        Matcher matcher = PLACEHOLDER.matcher(script);
        while (matcher.find()) {
            names.add(matcher.group(1));
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
        return script != null && PLACEHOLDER.matcher(script).find();
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