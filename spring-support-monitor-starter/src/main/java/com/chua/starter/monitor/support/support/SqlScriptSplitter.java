package com.chua.starter.monitor.support.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把一段 SQL 脚本切成单条语句。
 *
 * <p><b>为什么不能直接按 {@code ;} 切</b>：{@code ;} 在下列位置出现时不是语句分隔符：
 * 字符串字面量（{@code 'a;b'}）、双引号标识符（{@code "a;b"）、反引号标识符
 * （{@code `a;b`}）、行注释（{@code -- a;b} / {@code # a;b}）、块注释（{@code /* a;b *}{@code /}）。
 * 天真切分会把一条完整语句拦腰截断，执行时报的却是「语法错误」，
 * 离真正的原因（切错了）隔得很远。</p>
 *
 * <p>本类做的是**词法层**切分，保持原样返回每条语句（含原分号与否不影响内核），
 * 不做语义判断、不改写语句。</p>
 *
 * <p><b>刻意不支持 {@code DELIMITER}</b>：那是 MySQL 客户端指令，用于自定义语句结束符
 * （存储过程体里本来就有 {@code ;}）。按 {@code ;} 切必然切坏过程体，
 * 与其静默执行错语句，不如明确拒绝并提示改用「对象定义」入口。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public final class SqlScriptSplitter {

    /** 单条语句的字符上限，超出直接拒绝（避免误把超大脚本塞进内存）。 */
    private static final int MAX_STATEMENT_LENGTH = 200_000;

    /** 脚本的语句条数上限，超出直接拒绝。 */
    private static final int MAX_STATEMENTS = 500;

    private SqlScriptSplitter() {
    }

    /**
     * 切分 SQL 脚本。
     *
     * @param script 脚本原文，可为空
     * @return 单条语句列表（已去掉首尾空白，忽略空语句与纯注释语句）
     * @throws IllegalArgumentException 脚本报错（DELIMITER 指令、超长、超多条数）
     */
    public static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        if (script == null || script.isBlank()) {
            return statements;
        }
        if (script.length() > MAX_STATEMENT_LENGTH) {
            throw new IllegalArgumentException("脚本过长（上限 " + MAX_STATEMENT_LENGTH + " 字符）");
        }
        rejectDelimiterDirective(script);

        StringBuilder current = new StringBuilder();
        int i = 0;
        int n = script.length();
        while (i < n) {
            char c = script.charAt(i);
            char next = i + 1 < n ? script.charAt(i + 1) : '\0';

            // 行注释：-- （后面必须跟空白或行尾才是注释）与 #
            if (c == '-' && next == '-') {
                i = skipLineComment(script, i);
                current.append('\n');
                continue;
            }
            if (c == '#') {
                i = skipLineComment(script, i);
                current.append('\n');
                continue;
            }
            // 块注释
            if (c == '/' && next == '*') {
                i = skipBlockComment(script, i);
                current.append(' ');
                continue;
            }
            // 单引号字符串：反斜杠转义 + 双写单引号
            if (c == '\'') {
                int end = skipQuoted(script, i, '\'');
                current.append(script, i, end);
                i = end;
                continue;
            }
            // 双引号（ANSI_QUOTES 时是标识符，否则是字符串）—— 两种语义都不该被切开
            if (c == '"') {
                int end = skipQuoted(script, i, '"');
                current.append(script, i, end);
                i = end;
                continue;
            }
            // 反引号标识符
            if (c == '`') {
                int end = skipQuoted(script, i, '`');
                current.append(script, i, end);
                i = end;
                continue;
            }
            // 真正的语句分隔符
            if (c == ';') {
                addIfNotBlank(statements, current);
                current.setLength(0);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }
        addIfNotBlank(statements, current);
        if (statements.size() > MAX_STATEMENTS) {
            throw new IllegalArgumentException("脚本里的语句条数超过上限 " + MAX_STATEMENTS);
        }
        return statements;
    }

    /**
     * 判断一段文本在剔除注释后是否还含有实质内容。
     *
     * @param text 文本
     * @return true=有内容
     */
    public static boolean hasContent(String text) {
        return text != null && !stripComments(text).isBlank();
    }

    /**
     * 去掉注释与空白，只留实质内容（用于判断「这条是不是只有注释」）。
     *
     * @param text 文本
     * @return 去掉注释后的文本
     */
    private static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            char next = i + 1 < n ? text.charAt(i + 1) : '\0';
            if ((c == '-' && next == '-') || c == '#') {
                i = skipLineComment(text, i);
                continue;
            }
            if (c == '/' && next == '*') {
                i = skipBlockComment(text, i);
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                int end = skipQuoted(text, i, c);
                out.append(text, i, end);
                i = end;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * 收尾一条语句：非空且非纯注释才计入。
     *
     * @param statements 结果列表
     * @param current    当前累积
     */
    private static void addIfNotBlank(List<String> statements, StringBuilder current) {
        String raw = current.toString();
        if (!hasContent(raw)) {
            return;
        }
        String text = raw.strip();
        if (text.length() > MAX_STATEMENT_LENGTH) {
            throw new IllegalArgumentException("单条语句过长（上限 " + MAX_STATEMENT_LENGTH + " 字符）");
        }
        statements.add(text);
    }

    /**
     * 跳过行注释。
     *
     * @param text  原文
     * @param start {@code --} 或 {@code #} 的起始下标
     * @return 换行符（或文末）所在下标
     */
    private static int skipLineComment(String text, int start) {
        int i = start;
        int n = text.length();
        while (i < n && text.charAt(i) != '\n') {
            i++;
        }
        return i;
    }

    /**
     * 跳过块注释。
     *
     * @param text  原文
     * @param start {@code /*} 的起始下标
     * @return 注释结束后的下标（未闭合时为文末）
     */
    private static int skipBlockComment(String text, int start) {
        int end = text.indexOf("*/", start + 2);
        return end < 0 ? text.length() : end + 2;
    }

    /**
     * 跳过被引号包裹的一段。
     *
     * <p>单引号段内 {@code \\} 转义下一个字符、{@code ''} 是转义后的单引号；
     * 反引号与双引号同样支持 {@code \\} 转义（MySQL 默认开启
     * {@code NO_BACKSLASH_ESCAPES} 关闭时才需要区分，这里按更宽松的规则处理）。</p>
     *
     * @param text  原文
     * @param start 开引号下标
     * @param quote 引号字符
     * @return 闭引号之后的下标（未闭合时为文末）
     */
    private static int skipQuoted(String text, int start, char quote) {
        int i = start + 1;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == quote) {
                // 双写引号是转义，不是闭合
                if (i + 1 < n && text.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return n;
    }

    /**
     * 拒绝含 {@code DELIMITER} 指令的脚本。
     *
     * @param script 脚本原文
     */
    private static void rejectDelimiterDirective(String script) {
        int i = 0;
        int n = script.length();
        while (i < n) {
            char c = script.charAt(i);
            char next = i + 1 < n ? script.charAt(i + 1) : '\0';
            if (c == '\'' || c == '"' || c == '`') {
                i = skipQuoted(script, i, c);
                continue;
            }
            if (c == '/' && next == '*') {
                i = skipBlockComment(script, i);
                continue;
            }
            if ((c == '-' && next == '-') || c == '#') {
                i = skipLineComment(script, i);
                continue;
            }
            if (Character.isLetter(c)) {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(script.charAt(j)) || script.charAt(j) == '_')) {
                    j++;
                }
                String word = script.substring(i, j);
                // DELIMITER 必须出现在行首（允许前导空白），否则可能是普通标识符
                boolean atLineStart = script.lastIndexOf('\n', i - 1) < 0
                        || script.substring(script.lastIndexOf('\n', i - 1) + 1, i).isBlank();
                if (atLineStart && "DELIMITER".equalsIgnoreCase(word)) {
                    throw new IllegalArgumentException(
                            "脚本含 DELIMITER 指令（自定义语句结束符），本控制台不按分号切分它；"
                                    + "存储过程请用「对象定义」入口保存，查询控制台只接受普通语句");
                }
                i = j;
                continue;
            }
            i++;
        }
    }

    /**
     * 判断一条语句是否是查询类（返回结果集而非影响行数）。
     *
     * <p>与 {@code MonitorController#executeDbSql} 的口径保持一致：两条路径必须同口径，
     * 否则同一条 SQL 在「单条执行」与「批量执行」下表现会不一样。</p>
     *
     * @param statement 单条语句
     * @return true=查询类
     */
    public static boolean isQuery(String statement) {
        String head = statement.stripLeading().toUpperCase(Locale.ROOT);
        return head.startsWith("SELECT") || head.startsWith("SHOW")
                || head.startsWith("DESC") || head.startsWith("EXPLAIN")
                || head.startsWith("WITH");
    }
}