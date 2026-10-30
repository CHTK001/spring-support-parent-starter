package com.chua.starter.datasourcehub.support.dbconsole;

import java.util.Locale;

/**
 * 日期文本的解读规则（对齐 Navicat 导入向导第 3 步 Excel 分支的日期选项）。
 *
 * <p>为什么需要它：Excel 单元格里的日期一旦被读成字符串，{@code 03/04/2026} 到底是
 * 3 月 4 日还是 4 月 3 日就只能靠用户告诉我们的「日期顺序」。这里按用户给的顺序与
 * 分隔符把文本重排成 {@code yyyy-MM-dd}（带时间则补 {@code HH:mm:ss}），再交给驱动。
 * </p>
 *
 * <p>刻意保守：<b>任何一步对不上就原样返回</b>。猜错日期比不猜更糟——静默写错月份
 * 的业务数据没人会去核对，而原样交给数据库至少会报一个看得见的类型错误。</p>
 *
 * @param dateOrder     日期顺序：YMD / DMY / MDY
 * @param dateTimeOrder 日期时间顺序：同上，作用于同时含日期与时间的文本
 * @param dateSeparator 日期分隔符（如 {@code /}、{@code -}、{@code .}）
 * @param yearSeparator 年份分隔符（如 {@code /}；主要用于中文 {@code 2026年1月2日}）
 * @param timeSeparator 时间分隔符（如 {@code :}）
 * @author CH
 * @since 4.0.0.45
 */
public record DateOptions(
        String dateOrder,
        String dateTimeOrder,
        char dateSeparator,
        char yearSeparator,
        char timeSeparator
) {

    /**
     * defaults
     * @return 返回值
    */
    public static DateOptions defaults() {
        return new DateOptions("YMD", "YMD", '/', '/', ':');
    }

    /**
     * 规范构造器：空值收敛成默认值，非法顺序退回 YMD。
     *
     * @param dateOrder     日期顺序
     * @param dateTimeOrder 日期时间顺序
     * @param dateSeparator 日期分隔符
     * @param yearSeparator 年份分隔符
     * @param timeSeparator 时间分隔符
     */
    public DateOptions {
        dateOrder = order(dateOrder, "YMD");
        dateTimeOrder = order(dateTimeOrder, dateOrder);
        dateSeparator = dateSeparator == 0 ? '/' : dateSeparator;
        yearSeparator = yearSeparator == 0 ? dateSeparator : yearSeparator;
        timeSeparator = timeSeparator == 0 ? ':' : timeSeparator;
    }

    /**
     * 归一化日期/日期时间文本。
     *
     * @param text 源文本
     * @return {@code yyyy-MM-dd} 或 {@code yyyy-MM-dd HH:mm:ss}；无法确定时返回 null
     */
    public String normalize(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty() || value.length() > 40) {
            return null;
        }
        // 已经是标准 ISO 形式：不必再猜
        if (value.matches("\\d{4}-\\d{2}-\\d{2}([ T]\\d{2}:\\d{2}(:\\d{2})?)?")) {
            return value.replace('T', ' ');
        }
        int split = indexOfTime(value);
        String datePart = split < 0 ? value : value.substring(0, split).trim();
        String timePart = split < 0 ? "" : value.substring(split + 1).trim();
        String order = split < 0 ? dateOrder : dateTimeOrder;
        int[] ymd = parseDate(datePart, order);
        if (ymd == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%04d-%02d-%02d", ymd[0], ymd[1], ymd[2]));
        if (!timePart.isEmpty()) {
            String clock = parseTime(timePart);
            if (clock == null) {
                return null;
            }
            sb.append(' ').append(clock);
        }
        return sb.toString();
    }

    /**
     * 找到日期与时间的分界位置（空格或 {@code T}，且其后像时间）。
     *
     * @param value 文本
     * @return 分界下标；没有时间部分返回 -1
     */
    private int indexOfTime(String value) {
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c == ' ' || c == 'T' || c == 't')
                    && i + 1 < value.length()
                    && Character.isDigit(value.charAt(i + 1))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 按给定顺序解析日期部分。
     *
     * @param text  日期文本
     * @param order YMD / DMY / MDY
     * @return {@code [year, month, day]}；不匹配返回 null
     */
    private int[] parseDate(String text, String order) {
        // 中文写法：2026年1月2日
        if (text.indexOf(yearSeparator) >= 0 && text.indexOf('年') >= 0) {
            String[] parts = text.replace('日', ' ').split("[年月]");
            if (parts.length >= 3) {
                Integer y = number(parts[0]);
                Integer m = number(parts[1]);
                Integer d = number(parts[2].trim());
                if (y != null && m != null && d != null) {
                    return ymd(y, m, d);
                }
            }
        }
        String[] parts = splitOn(text, dateSeparator);
        if (parts.length != 3) {
            // 用户可能选了 "/" 而文件是 "-"：只在两种常见分隔符上退一步
            char other = dateSeparator == '/' ? '-' : '/';
            parts = splitOn(text, other);
        }
        if (parts.length != 3) {
            return null;
        }
        Integer a = number(parts[0]);
        Integer b = number(parts[1]);
        Integer c = number(parts[2]);
        if (a == null || b == null || c == null) {
            return null;
        }
        // 四位数一定是年，优先按它定锚，避免把 2026 当成日
        if (a > 31) {
            return ymd(a, b, c);
        }
        if (c > 31) {
            return switch (order) {
                case "DMY" -> ymd(c, b, a);
                case "MDY" -> ymd(c, a, b);
                default -> ymd(c, b, a);
            };
        }
        return null;
    }

    /**
     * 解析时间部分为 {@code HH:mm:ss}。
     *
     * @param text 时间文本
     * @return 归一化时间；不匹配返回 null
     */
    private String parseTime(String text) {
        String[] parts = splitOn(text, timeSeparator);
        if (parts.length < 2) {
            parts = splitOn(text, ':');
        }
        if (parts.length < 2) {
            return null;
        }
        Integer h = number(parts[0]);
        Integer m = number(parts[1]);
        Integer s = parts.length > 2 ? number(parts[2]) : Integer.valueOf(0);
        if (h == null || m == null || s == null || h > 23 || m > 59 || s > 59) {
            return null;
        }
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }

    /**
     * 组装并校验日期三元组。
     *
     * @param y 年
     * @param m 月
     * @param d 日
     * @return 三元组；越界返回 null
     */
    private static int[] ymd(int y, int m, int d) {
        if (m < 1 || m > 12 || d < 1 || d > 31) {
            return null;
        }
        return new int[]{y, m, d};
    }

    /**
     * split On
     * @param separator 参数
     * @param text 字符串
     * @return 返回值
    */
    private static String[] splitOn(String text, char separator) {
        return text.split(java.util.regex.Pattern.quote(String.valueOf(separator)), -1);
    }

    /**
     * number
     * @param text 字符串
     * @return 返回值
    */
    private static Integer number(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty() || !value.matches("\\d{1,6}")) {
            return null;
        }
        return Integer.valueOf(value);
    }

    /**
     * order
     * @param fallback 字符串
     * @param value 字符串
     * @return 返回值
    */
    private static String order(String value, String fallback) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "YMD", "DMY", "MDY" -> v;
            default -> fallback;
        };
    }
}
