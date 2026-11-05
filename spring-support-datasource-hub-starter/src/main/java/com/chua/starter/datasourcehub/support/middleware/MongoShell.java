// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.middleware;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mongo Shell 查询语句解析：把 {@code db.<集合>.<方法>(参数)[.limit(n)]} 拆成可执行结构。
 *
 * <p>管理侧只需要「发一句只读查询、拿回文档」的能力，因此这里刻意做成「白名单方法 +
 * 只读执行」：find/findOne/count/countDocuments/aggregate 之外一律拒绝，
 * insert/update/delete/drop 等写操作不进入本版管理面。</p>
 *
 * <p>参数按 JSON 宽松解析：裸键（{@code {name: "x"}}）先补引号，
 * {@code ObjectId("...")}/{@code ISODate("...")} 换成 MongoDB 扩展 JSON
 * （{@code {"$oid": "..."}}/{@code {"$date": "..."}}），再由驱动的
 * {@code Document.parse} 落成 BSON。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class MongoShell {

    /**
     * 语句头：{@code db.<集合>.<方法>(}
     */
    private static final Pattern HEAD = Pattern.compile(
            "^db\\s*\\.\\s*([A-Za-z0-9_.$-]+)\\s*\\.\\s*([A-Za-z]+)\\s*\\(");

    /**
     * 链式修饰符：{@code .limit(}/{@code .skip(}/{@code .sort(}
     */
    private static final Pattern MODIFIER = Pattern.compile("^\\.(limit|skip|sort)\\s*\\(");

    /**
     * 裸键：标识符后紧跟冒号（位置校验由 {@link #isBareKeyStart} 负责，这里不重复匹配前导字符，
     * 否则 {@code { name:}} 这类前导带空格的写法会被漏掉）
     */
    private static final Pattern BARE_KEY = Pattern.compile(
            "^([A-Za-z_$][A-Za-z0-9_$]*)(\\s*:)");

    /**
     * {@code ObjectId("hex")} / {@code ObjectId('hex')}
     */
    private static final Pattern OBJECT_ID = Pattern.compile(
            "ObjectId\\s*\\(\\s*['\"]([0-9a-fA-F]{24})['\"]\\s*\\)");

    /**
     * {@code ISODate("...")} / {@code ISODate('...')}
     */
    private static final Pattern ISO_DATE = Pattern.compile(
            "ISODate\\s*\\(\\s*(['\"])(.*?)\\1\\s*\\)");

    /**
     * 支持的白名单方法
     */
    private static final List<String> READ_METHODS = List.of(
            "find", "findone", "count", "countdocuments", "aggregate");

    /**
     * 解析后的查询结构。
     *
     * @param collection 集合名
     * @param method     方法名（小写）
     * @param filter     查询条件（find/findOne/count 的第一个参数），聚合时为空串
     * @param projection 投影（find 的第二个参数），无则为空串
     * @param pipeline   聚合管道（aggregate 的整段参数），非聚合时为空串
     * @param limit      显式行数上限，未指定时为 null
     * @param skip       跳过行数，未指定时为 0
     * @param sort       排序条件 JSON，未指定时为空串
     * @author CH
     * @since 4.0.0.42
     */
    public record Query(
            String collection,
            String method,
            String filter,
            String projection,
            String pipeline,
            Integer limit,
            int skip,
            String sort
    ) {
    }

    /**
     * 工具类禁止实例化
     */
    private MongoShell() {
    }

    /**
     * 解析一句 shell 查询。
     *
     * @param expression 用户输入，允许首尾空白与结尾分号
     * @return 解析结果
     * @throws IllegalArgumentException 语句结构不支持、方法不在白名单或括号不匹配
     */
    public static Query parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("查询语句不能为空");
        }
        String s = expression.trim();
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        Matcher head = HEAD.matcher(s);
        if (!head.find()) {
            throw new IllegalArgumentException("查询语句需以 db.<集合>.<方法>(...) 开头");
        }
        String collection = head.group(1);
        String method = head.group(2).toLowerCase(Locale.ROOT);
        if (!READ_METHODS.contains(method)) {
            throw new IllegalArgumentException("仅支持只读查询: " + String.join("/", READ_METHODS));
        }
        int open = head.end() - 1;
        int close = matchParen(s, open);
        if (close < 0) {
            throw new IllegalArgumentException("括号不匹配");
        }
        String args = s.substring(head.end(), close).trim();
        String rest = s.substring(close + 1).trim();

        Integer limit = null;
        int skip = 0;
        String sort = "";
        while (!rest.isEmpty()) {
            Matcher modifier = MODIFIER.matcher(rest);
            if (!modifier.find()) {
                throw new IllegalArgumentException("不支持的链式写法: " + rest
                        + "（可用 .limit(n) / .skip(n) / .sort({...})）");
            }
            int mOpen = modifier.end() - 1;
            int mClose = matchParen(rest, mOpen);
            if (mClose < 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            String value = rest.substring(modifier.end(), mClose).trim();
            switch (modifier.group(1)) {
                case "limit" -> limit = parseCount(value, "limit");
                case "skip" -> skip = parseCount(value, "skip");
                default -> sort = value;
            }
            rest = rest.substring(mClose + 1).trim();
        }

        if ("aggregate".equals(method)) {
            return new Query(collection, method, "", "", args, limit, skip, sort);
        }
        List<String> parts = splitArgs(args);
        String filter = parts.isEmpty() ? "" : parts.get(0);
        String projection = parts.size() > 1 ? parts.get(1) : "";
        if (parts.size() > 2) {
            throw new IllegalArgumentException("find/findOne/count 最多接受 2 个参数（条件与投影）");
        }
        if ("count".equals(method) || "countdocuments".equals(method)) {
            if (parts.size() > 1) {
                throw new IllegalArgumentException("count 只接受 1 个条件参数");
            }
            if (limit != null || skip > 0 || !sort.isEmpty()) {
                throw new IllegalArgumentException("count 不支持链式修饰符");
            }
        }
        return new Query(collection, method, filter, projection, "", limit, skip, sort);
    }

    /**
     * 宽松 JSON 归一化：裸键补引号、单引号串转双引号、ObjectId/ISODate 转扩展 JSON。
     *
     * @param text 用户输入的 JSON 片段
     * @return 可被 {@code Document.parse} 直接解析的 JSON 文本
     */
    public static String normalizeJson(String text) {
        if (text == null || text.isEmpty()) {
            return "{}";
        }
        // 先做替换再统一引号：替换只碰字面量之外的壳，双引号内容原样保留
        String replaced = OBJECT_ID.matcher(text).replaceAll("{\"$oid\":\"$1\"}");
        replaced = ISO_DATE.matcher(replaced).replaceAll("{\"$date\":\"$2\"}");

        StringBuilder out = new StringBuilder(replaced.length() + 16);
        int i = 0;
        while (i < replaced.length()) {
            char c = replaced.charAt(i);
            if (c == '"') {
                int end = scanString(replaced, i, '"');
                out.append(replaced, i, end);
                i = end;
            } else if (c == '\'') {
                int end = scanString(replaced, i, '\'');
                // 单引号串按 shell 语义转双引号：\' 是串内撇号，JSON 里不认这个转义，先还原
                String inner = replaced.substring(i + 1, end - 1).replace("\\'", "'");
                out.append('"').append(inner).append('"');
                i = end;
            } else if (isBareKeyStart(replaced, i)) {
                Matcher key = BARE_KEY.matcher(replaced.substring(i));
                if (key.find() && key.start() == 0) {
                    out.append('"').append(key.group(1)).append('"').append(key.group(2));
                    i += key.end();
                    continue;
                }
                out.append(c);
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * 顶层逗号切分：只在括号/方括号/花括号深度为 0 且不在字符串内时切。
     *
     * @param text 参数段
     * @return 切分后的参数列表，空段会被丢弃
     */
    public static List<String> splitArgs(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = scanString(text, i, c);
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
            } else if (c == '}' || c == ']' || c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                String part = text.substring(start, i).trim();
                if (!part.isEmpty()) {
                    parts.add(part);
                }
                start = i + 1;
            }
            i++;
        }
        String last = text.substring(start).trim();
        if (!last.isEmpty()) {
            parts.add(last);
        }
        return parts;
    }

    /**
     * 从 {@code (} 位置扫描到配对 {@code )}，跳过字符串字面量。
     *
     * @param text  文本
     * @param open  {@code (} 的下标
     * @return 配对 {@code )} 的下标，不配对时返回 -1
     */
    private static int matchParen(String text, int open) {
        int depth = 0;
        int i = open;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = scanString(text, i, c);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    /**
     * 扫描一个字符串字面量，返回闭合引号之后的下标。
     *
     * @param text  文本
     * @param start 起始引号下标
     * @param quote 引号字符
     * @return 闭合引号的下一位；不闭合时返回文本长度
     */
    private static int scanString(String text, int start, char quote) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i++;
        }
        return text.length();
    }

    /**
     * 当前位置是否可能是裸键起点：前一个非空白字符为 {@code {}、,}。
     *
     * @param text 文本
     * @param i    当前位置
     * @return 可能是裸键时为 true
     */
    private static boolean isBareKeyStart(String text, int i) {
        char c = text.charAt(i);
        if (!(c == '_' || c == '$' || Character.isLetter(c))) {
            return false;
        }
        int j = i - 1;
        while (j >= 0 && Character.isWhitespace(text.charAt(j))) {
            j--;
        }
        return j < 0 || text.charAt(j) == '{' || text.charAt(j) == ',';
    }

    /**
     * 解析非负整数字面量。
     *
     * @param text 文本
     * @param what 参数名，用于报错
     * @return 数值
     * @throws IllegalArgumentException 不是非负整数
     */
    private static int parseCount(String text, String what) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " 需为非负整数: " + text);
        }
    }
}