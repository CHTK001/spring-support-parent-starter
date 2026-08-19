package com.chua.starter.datasource.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 表名通配符匹配工具
 * <p>支持 {@code *} 通配，{@code *} 可匹配任意字符（含 {@code _}），
 * 不支持 {@code ?} 与 {@code [...]}。空字符串视为通配全部。</p>
 *
 * @since 2026/8/5
 */
public final class TablePatternMatcher {

    /**
     * 编译后的模式
     */
    private final String regex;

    private TablePatternMatcher(String regex) {
        this.regex = regex;
    }

    /**
     * 编译逗号分隔的多模式字符串
     *
     * @param csv 模式字符串
     * @return 匹配器列表（空列表表示无过滤）
     */
    public static List<TablePatternMatcher.Matcher> build(String csv) {
        List<TablePatternMatcher.Matcher> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return list;
        }
        for (String raw : csv.split(",")) {
            String pattern = raw.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            String regex = toRegex(pattern);
            list.add(name -> name != null && name.matches(regex));
        }
        return list;
    }

    /**
     * 任意一个匹配器命中即返回 true
     */
    public static boolean anyMatch(List<TablePatternMatcher.Matcher> matchers, String name) {
        for (Matcher m : matchers) {
            if (m.test(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将通配符模式转为正则
     */
    private static String toRegex(String pattern) {
        StringBuilder sb = new StringBuilder(pattern.length() + 4);
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            } else {
                sb.append("\\").append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 简单函数式匹配器
 * @author CH
     */
    @FunctionalInterface
    public interface Matcher {
        boolean test(String name);
    }
}
