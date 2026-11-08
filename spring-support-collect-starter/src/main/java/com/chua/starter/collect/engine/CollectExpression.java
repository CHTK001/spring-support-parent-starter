// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.engine;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.collect.support.CollectJson;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节点参数表达式求值。
 *
 * <p>语法为 {@code ${...}}。整段恰好一个表达式时返回原始类型，混排时按字符串拼接。</p>
 *
 * <p>表达式语言是本模块自带的一元求值器，不是 SpEL 或脚本引擎：只支持字面量、变量、成员/下标访问、
 * 算术比较逻辑运算、三元，以及三种同源的调用写法——平铺 {@code ${attr(el, 'href')}}、
 * 链式 {@code ${doc.selector('a').attr('href')}}（宿主并入首参）与命名空间
 * {@code ${string.substring(s, 5)}}（前缀只作语法）。三者都只解析
 * {@link CollectBuiltinInvoker} 里的同一个白名单，链式写法不额外开代码执行面。调用什么函数由白名单决定——
 * 内置的 {@link CollectFunctions} 是编译期白名单，
 * 自定义函数库允许运行期登记 Groovy 函数，那道口子与脚本节点同一条边界（控制台会话认证 +
 * {@code plugin.collect.function.enable}），关掉函数库时求值器回到纯白名单。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectExpression {

    /**
     * 占位符前缀。
     */
    private static final String PREFIX = "${";

    /**
     * 工具类禁止实例化。
     */
    private CollectExpression() {
    }

    /**
     * 求值，返回原始类型。
     *
     * @param raw       原始文本，可含 0 到多个 {@code ${}}
     * @param variables 变量作用域
     * @return 求值结果，无占位符时返回原文
     */
    public static Object eval(String raw, Map<String, Object> variables) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String single = singleExpression(raw);
        if (single != null) {
            return new Parser(single, variables).parseTop();
        }
        if (!raw.contains(PREFIX)) {
            return raw;
        }
        StringBuilder builder = new StringBuilder(raw.length());
        int cursor = 0;
        while (cursor < raw.length()) {
            int start = raw.indexOf(PREFIX, cursor);
            if (start < 0) {
                builder.append(raw, cursor, raw.length());
                break;
            }
            builder.append(raw, cursor, start);
            int end = raw.indexOf('}', start + PREFIX.length());
            if (end < 0) {
                builder.append(raw.substring(start));
                break;
            }
            Object value = new Parser(raw.substring(start + PREFIX.length(), end), variables).parseTop();
            builder.append(CollectFunctions.stringify(value));
            cursor = end + 1;
        }
        return builder.toString();
    }

    /**
     * 按「表达式」求值，不要求 {@code ${}} 包裹。
     *
     * <p>{@link #eval} 对不含 {@code ${}} 的文本会**原样返回字符串** —— 那是给「普通文本参数」
     * 用的语义，但对判断节点是个陷阱：用户写 {@code 1 == 2}，{@code eval} 返回字符串
     * {@code "1 == 2"}，{@link #truthy} 判非空串为真，于是**假条件走了真分支且没有任何提示**
     * （验收实测：表达式 {@code 1 == 2} 的节点日志写着「真」）。</p>
     *
     * <p>本方法把裸文本也当表达式解析，因此 {@code 1 == 2} 与 {@code ${1 == 2}} 等价。
     * 刻意**不改** {@link #evalCondition}：连线条件也在用它，把裸文本改成按表达式解析会
     * 改变既有流程里「条件写普通文本」的语义。</p>
     *
     * @param raw       表达式文本；空或纯空白返回 null
     * @param variables 变量作用域
     * @return 求值结果
     * @throws IllegalArgumentException 裸文本不是合法表达式时抛出，便于节点报出可读错误
     */
    public static Object evalExpr(String raw, Map<String, Object> variables) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String single = singleExpression(raw);
        if (single != null) {
            return new Parser(single, variables).parseTop();
        }
        if (raw.contains(PREFIX)) {
            return eval(raw, variables);
        }
        try {
            return new Parser(raw, variables).parseTop();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("判断表达式无法求值: " + raw + "（" + ex.getMessage() + "）", ex);
        }
    }

    /**
     * 求值并转为文本。
     *
     * @param raw       原始文本
     * @param variables 变量作用域
     * @return 文本结果，null 转空串
     */
    public static String evalText(String raw, Map<String, Object> variables) {
        return CollectFunctions.stringify(eval(raw, variables));
    }

    /**
     * 条件求值，空条件恒真。
     *
     * @param raw       条件文本
     * @param variables 变量作用域
     * @return 是否放行
     */
    public static boolean evalCondition(String raw, Map<String, Object> variables) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return truthy(eval(raw, variables));
    }

    /**
     * 真值判定。
     *
     * @param value 任意值
     * @return 非空、非 false、非零、非空集合、非空串返回 true
     */
    public static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0D;
        }
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }
        if (value instanceof Elements) {
            return !((Elements) value).isEmpty();
        }
        String text = String.valueOf(value).trim();
        return !text.isEmpty() && !"false".equalsIgnoreCase(text) && !"0".equals(text);
    }

    /**
     * 整段是否恰好是一个表达式。
     *
     * @param raw 原始文本
     * @return 是则返回表达式体，否则返回 null
     */
    private static String singleExpression(String raw) {
        String text = raw.trim();
        if (!text.startsWith(PREFIX) || !text.endsWith("}")) {
            return null;
        }
        String body = text.substring(PREFIX.length(), text.length() - 1);
        return body.contains(PREFIX) ? null : body;
    }

    /**
     * 递归下降求值器。
     *
     * @author CH
     * @since 4.0.0.42
     */
    private static final class Parser {

        /**
         * 表达式体。
         */
        private final String text;

        /**
         * 变量作用域。
         */
        private final Map<String, Object> variables;

        /**
         * 读游标。
         */
        private int cursor;

        /**
         * 构造求值器。
         *
         * @param text      表达式体
         * @param variables 变量作用域
         */
        private Parser(String text, Map<String, Object> variables) {
            this.text = text == null ? "" : text;
            this.variables = variables == null ? Map.of() : variables;
        }

        /**
         * 解析整段表达式。
         *
         * @return 求值结果
         */
        private Object parseTop() {
            Object value = parseTernary();
            skipSpace();
            if (cursor < text.length()) {
                throw new IllegalArgumentException("表达式存在多余内容: " + text.substring(cursor));
            }
            return value;
        }

        /**
         * 三元表达式。
         *
         * @return 求值结果
         */
        private Object parseTernary() {
            Object condition = parseBinary(0);
            skipSpace();
            if (consume('?')) {
                Object truthy = parseTernary();
                if (!consume(':')) {
                    throw new IllegalArgumentException("三元表达式缺少冒号: " + text);
                }
                Object falsy = parseTernary();
                return CollectExpression.truthy(condition) ? truthy : falsy;
            }
            return condition;
        }

        /**
         * 二元运算，按优先级表递归。
         *
         * @param precedence 当前优先级
         * @return 求值结果
         */
        private Object parseBinary(int precedence) {
            if (precedence >= LEVELS.length) {
                return parseUnary();
            }
            Object left = parseBinary(precedence + 1);
            while (true) {
                skipSpace();
                String operator = peekOperator(LEVELS[precedence]);
                if (operator == null) {
                    return left;
                }
                cursor += operator.length();
                Object right = parseBinary(precedence + 1);
                left = apply(operator, left, right);
            }
        }

        /**
         * 优先级从低到高。
         */
        private static final String[][] LEVELS = {
                {"||"},
                {"&&"},
                {"==", "!="},
                {">=", "<=", ">", "<"},
                {"+", "-"},
                {"*", "/", "%"}
        };

        /**
         * 命名空间前缀，仅作语法用：{@code ${string.substring(s, 5)}} 与 {@code ${substring(s, 5)}} 等价。
         */
        private static final Set<String> NAMESPACES = Set.of(
                "string", "str", "text", "char", "number", "num", "math", "date", "time", "json", "xml",
                "list", "array", "map", "object", "obj", "element", "elements", "dom", "html",
                "url", "uri", "base64", "md5", "sha1", "random", "rand", "uuid", "regex", "regx",
                "extract", "fn", "util", "utils", "collections", "file", "thread");

        /**
         * 前缀是否按命名空间解析。
         *
         * <p>作用域里有同名变量时不认命名空间，保证 {@code ${list.sort()}} 这类链式调用
         * 仍然作用在用户自己的变量上。</p>
         *
         * @param name 前缀
         * @return 按命名空间解析返回 true
         */
        private boolean isNamespace(String name) {
            return !variables.containsKey(name) && NAMESPACES.contains(name);
        }

        /**
         * 取当前位置匹配的运算符。
         *
         * @param operators 候选运算符组
         * @return 命中的运算符，未命中返回 null
         */
        private String peekOperator(String[] operators) {
            for (String operator : operators) {
                if (text.startsWith(operator, cursor)) {
                    if ("&".equals(operator) && !text.startsWith("&&", cursor)) {
                        continue;
                    }
                    return operator;
                }
            }
            return null;
        }

        /**
         * 一元运算。
         *
         * @return 求值结果
         */
        private Object parseUnary() {
            skipSpace();
            if (consume('!')) {
                return !CollectExpression.truthy(parseUnary());
            }
            if (consume('-')) {
                Object value = parseUnary();
                return subtract(0D, value);
            }
            return parsePostfix();
        }

        /**
         * 成员与下标访问。
         *
         * @return 求值结果
         */
        private Object parsePostfix() {
            Object value = parsePrimary();
            while (true) {
                skipSpace();
                if (cursor < text.length() && text.charAt(cursor) == '.') {
                    cursor++;
                    String name = readName();
                    if (name.isEmpty()) {
                        throw new IllegalArgumentException("成员访问缺少名称: " + text);
                    }
                    if (consume('(')) {
                        value = CollectBuiltinInvoker.invoke(name, withReceiver(value, parseArguments(name)));
                        continue;
                    }
                    value = member(value, name);
                    continue;
                }
                if (cursor < text.length() && text.charAt(cursor) == '[') {
                    cursor++;
                    Object index = parseTernary();
                    if (!consume(']')) {
                        throw new IllegalArgumentException("下标缺少右方括号: " + text);
                    }
                    value = index(value, index);
                    continue;
                }
                return value;
            }
        }

        /**
         * 把宿主插到实参首位。
         *
         * @param receiver  链式调用的宿主
         * @param arguments 括号里的实参
         * @return 合并后的实参
         */
        private List<Object> withReceiver(Object receiver, List<Object> arguments) {
            List<Object> merged = new ArrayList<>(arguments.size() + 1);
            merged.add(receiver);
            merged.addAll(arguments);
            return merged;
        }

        /**
         * 原子项：字面量、括号、变量、函数调用。
         *
         * @return 求值结果
         */
        private Object parsePrimary() {
            skipSpace();
            if (cursor >= text.length()) {
                throw new IllegalArgumentException("表达式不完整: " + text);
            }
            char current = text.charAt(cursor);
            if (current == '(') {
                cursor++;
                Object value = parseTernary();
                if (!consume(')')) {
                    throw new IllegalArgumentException("表达式缺少右括号: " + text);
                }
                return value;
            }
            if (current == '\'' || current == '"') {
                return readString(current);
            }
            if (Character.isDigit(current) || current == '.') {
                return readNumber();
            }
            String name = readName();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("表达式存在无法识别的字符: " + current);
            }
            skipSpace();
            if (consume('(')) {
                return callFunction(name);
            }
            if (isNamespace(name) && text.startsWith(".", cursor)) {
                cursor++;
                String function = readName();
                if (function.isEmpty()) {
                    throw new IllegalArgumentException("命名空间 " + name + " 后缺少函数名: " + text);
                }
                skipSpace();
                if (consume('(')) {
                    return CollectBuiltinInvoker.invoke(function, parseArguments(function));
                }
                throw new IllegalArgumentException("命名空间只支持函数调用: " + name + "." + function);
            }
            switch (name) {
                case "true":
                    return Boolean.TRUE;
                case "false":
                    return Boolean.FALSE;
                case "null":
                    return null;
                default:
                    return variables.get(name);
            }
        }

        /**
         * 解析函数调用实参。
         *
         * @param name 函数名
         * @return 调用结果
         */
        private Object callFunction(String name) {
            return CollectBuiltinInvoker.invoke(name, parseArguments(name));
        }

        /**
         * 解析括号内的实参列表。
         *
         * @param name 函数名，仅用于报错
         * @return 实参列表
         */
        private List<Object> parseArguments(String name) {
            List<Object> arguments = new ArrayList<>();
            skipSpace();
            if (consume(')')) {
                return arguments;
            }
            while (true) {
                arguments.add(parseTernary());
                skipSpace();
                if (consume(',')) {
                    continue;
                }
                if (consume(')')) {
                    break;
                }
                throw new IllegalArgumentException("函数实参解析失败: " + name);
            }
            return arguments;
        }

        /**
         * 读取标识符。
         *
         * @return 标识符文本
         */
        private String readName() {
            skipSpace();
            int start = cursor;
            while (cursor < text.length()
                    && (Character.isLetterOrDigit(text.charAt(cursor)) || text.charAt(cursor) == '_'
                    || (cursor > start && text.charAt(cursor) == '$'))) {
                cursor++;
            }
            return text.substring(start, cursor);
        }

        /**
         * 读取字符串字面量。
         *
         * <p>只把 {@code \\n \\t \\r \\0} 与引号本身的转义翻成字符，其余 {@code \\x}
         * 原样保留反斜杠——正则 {@code '\\w'}、{@code '\\s'}、{@code '\\.'} 依赖它。</p>
         *
         * @param quote 引号
         * @return 字符串内容
         */
        private String readString(char quote) {
            cursor++;
            StringBuilder builder = new StringBuilder();
            while (cursor < text.length()) {
                char current = text.charAt(cursor++);
                if (current == quote) {
                    return builder.toString();
                }
                if (current == '\\' && cursor < text.length()) {
                    char escaped = text.charAt(cursor++);
                    switch (escaped) {
                        case 'n':
                            builder.append('\n');
                            break;
                        case 't':
                            builder.append('\t');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        case '0':
                            builder.append('\0');
                            break;
                        // 未识别的转义原样保留反斜杠：正则里的 \\w / \\s / \\d / \\. 必须进到
                        // Pattern.compile 才有效，历史上把它们吃成 w/s/d 导致「正则对但永远不命中」。
                        default:
                            builder.append('\\').append(escaped);
                            break;
                    }
                    continue;
                }
                builder.append(current);
            }
            throw new IllegalArgumentException("字符串字面量未闭合: " + text);
        }

        /**
         * 读取数字字面量。
         *
         * @return Long 或 Double
         */
        private Object readNumber() {
            int start = cursor;
            boolean decimal = false;
            while (cursor < text.length()) {
                char current = text.charAt(cursor);
                if (Character.isDigit(current)) {
                    cursor++;
                } else if (current == '.' && !decimal) {
                    decimal = true;
                    cursor++;
                } else {
                    break;
                }
            }
            String literal = text.substring(start, cursor);
            return decimal ? (Object) Double.parseDouble(literal) : (Object) Long.parseLong(literal);
        }

        /**
         * 跳过空白。
         */
        private void skipSpace() {
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
        }

        /**
         * 匹配并消费一个字符。
         *
         * @param expected 期望字符
         * @return 命中并消费返回 true
         */
        private boolean consume(char expected) {
            skipSpace();
            if (cursor < text.length() && text.charAt(cursor) == expected) {
                cursor++;
                return true;
            }
            return false;
        }

        /**
         * 成员访问：映射键、jsoup 元素属性、集合长度。
         *
         * @param target 宿主
         * @param name   成员名
         * @return 成员值
         */
        private Object member(Object target, String name) {
            if (target == null || name.isEmpty()) {
                return null;
            }
            if ("length".equals(name)) {
                return CollectFunctions.length(target);
            }
            if ("size".equals(name)) {
                return CollectFunctions.size(target);
            }
            // ${data.toJson}：把当前数据转成 JSON 字符串（用户明确要求的变量写法）。
            // 与引擎派生的 ${dataJson} 等价，这里让 .toJson 成员访问直接可用。
            if ("toJson".equals(name)) {
                return CollectJson.toJson(target);
            }
            if (target instanceof Map) {
                return ((Map<?, ?>) target).get(name);
            }
            // 行列表投影：${data.word} 把 List<Map> 里每行的 word 字段抽成扁平 List，
            // 供 SQL 节点 IN (${data.word}) 批量化处理上游抽取出的成千上万个值。
            if (target instanceof List<?> list) {
                List<Object> projected = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map) {
                        projected.add(((Map<?, ?>) item).get(name));
                    } else {
                        projected.add(member(item, name));
                    }
                }
                return projected;
            }
            if (target instanceof Element) {
                return elementProperty((Element) target, name);
            }
            if (target instanceof Elements) {
                Elements elements = (Elements) target;
                switch (name) {
                    case "text":
                        return elements.text();
                    case "html":
                        return elements.html();
                    case "first":
                        return elements.isEmpty() ? null : elements.first();
                    case "last":
                        return elements.isEmpty() ? null : elements.last();
                    default:
                        return null;
                }
            }
            return null;
        }

        /**
         * jsoup 元素常用派生属性。
         *
         * @param element 元素
         * @param name    属性名
         * @return 属性值
         */
        private Object elementProperty(Element element, String name) {
            switch (name) {
                case "text":
                    return element.text();
                case "html":
                    return element.html();
                case "outerHtml":
                    return element.outerHtml();
                case "ownText":
                    return element.ownText();
                case "tag":
                    return element.tagName();
                case "baseUri":
                    return element.baseUri();
                default:
                    return element.attr(name);
            }
        }

        /**
         * 下标访问。
         *
         * @param target 宿主
         * @param index  下标
         * @return 元素
         */
        private Object index(Object target, Object index) {
            if (target == null) {
                return null;
            }
            if (target instanceof Map) {
                return ((Map<?, ?>) target).get(CollectFunctions.stringify(index));
            }
            int position = (int) Math.floor(Double.parseDouble(String.valueOf(index).trim()));
            List<Object> items = toList(target);
            if (position < 0) {
                position += items.size();
            }
            return position >= 0 && position < items.size() ? items.get(position) : null;
        }

        /**
         * 宿主转列表。
         *
         * @param target 宿主
         * @return 元素列表
         */
        private List<Object> toList(Object target) {
            List<Object> items = new ArrayList<>();
            if (target instanceof Collection) {
                items.addAll((Collection<?>) target);
            } else if (target instanceof Elements) {
                for (Element element : (Elements) target) {
                    items.add(element);
                }
            } else if (target.getClass().isArray()) {
                int length = ReflectUtils.arrayLength(target);
                for (int i = 0; i < length; i++) {
                    items.add(ReflectUtils.arrayGet(target, i));
                }
            } else {
                String text = String.valueOf(target);
                for (int i = 0; i < text.length(); i++) {
                    items.add(String.valueOf(text.charAt(i)));
                }
            }
            return items;
        }

        /**
         * 执行二元运算。
         *
         * @param operator 运算符
         * @param left     左值
         * @param right    右值
         * @return 运算结果
         */
        private Object apply(String operator, Object left, Object right) {
            switch (operator) {
                case "&&":
                    return CollectExpression.truthy(left) && CollectExpression.truthy(right);
                case "||":
                    return CollectExpression.truthy(left) || CollectExpression.truthy(right);
                case "==":
                    return equalsLoose(left, right);
                case "!=":
                    return !equalsLoose(left, right);
                case "+":
                    return add(left, right);
                case "-":
                    return subtract(left, right);
                case "*":
                    return multiply(left, right);
                case "/":
                    return divide(left, right);
                case "%":
                    return remainder(left, right);
                default:
                    return compare(operator, left, right);
            }
        }

        /**
         * 宽松相等：两侧可转数字则按数字比较，否则按文本比较。
         *
         * @param left  左值
         * @param right 右值
         * @return 是否相等
         */
        private boolean equalsLoose(Object left, Object right) {
            if (left == null || right == null) {
                return left == right;
            }
            Double leftNumber = asNumber(left);
            Double rightNumber = asNumber(right);
            if (leftNumber != null && rightNumber != null) {
                return leftNumber.doubleValue() == rightNumber.doubleValue();
            }
            return String.valueOf(left).equals(String.valueOf(right));
        }

        /**
         * 关系比较。
         *
         * @param operator 运算符
         * @param left     左值
         * @param right    右值
         * @return 比较结果
         */
        private Object compare(String operator, Object left, Object right) {
            Double leftNumber = asNumber(left);
            Double rightNumber = asNumber(right);
            int result;
            if (leftNumber != null && rightNumber != null) {
                result = Double.compare(leftNumber, rightNumber);
            } else {
                result = String.valueOf(left).compareTo(String.valueOf(right));
            }
            switch (operator) {
                case ">":
                    return result > 0;
                case ">=":
                    return result >= 0;
                case "<":
                    return result < 0;
                default:
                    return result <= 0;
            }
        }

        /**
         * 加法，任一侧非数字则按字符串拼接。
         *
         * @param left  左值
         * @param right 右值
         * @return 结果
         */
        private Object add(Object left, Object right) {
            Double leftNumber = asNumber(left);
            Double rightNumber = asNumber(right);
            if (leftNumber != null && rightNumber != null) {
                return combine(leftNumber + rightNumber, left, right);
            }
            return CollectFunctions.stringify(left) + CollectFunctions.stringify(right);
        }

        /**
         * 减法。
         *
         * @param left  左值
         * @param right 右值
         * @return 结果
         */
        private Object subtract(Object left, Object right) {
            return combine(number(left) - number(right), left, right);
        }

        /**
         * 乘法。
         *
         * @param left  左值
         * @param right 右值
         * @return 结果
         */
        private Object multiply(Object left, Object right) {
            return combine(number(left) * number(right), left, right);
        }

        /**
         * 除法，除零返回 0 以避免采集流程中断。
         *
         * @param left  左值
         * @param right 右值
         * @return 结果
         */
        private Object divide(Object left, Object right) {
            double divisor = number(right);
            return divisor == 0D ? 0L : combine(number(left) / divisor, left, right);
        }

        /**
         * 取余。
         *
         * @param left  左值
         * @param right 右值
         * @return 结果
         */
        private Object remainder(Object left, Object right) {
            double divisor = number(right);
            return divisor == 0D ? 0L : combine(number(left) % divisor, left, right);
        }

        /**
         * 两个整数操作数的结果保持整数。
         *
         * @param value  计算结果
         * @param left   左操作数
         * @param right  右操作数
         * @return 整数或浮点结果
         */
        private Object combine(double value, Object left, Object right) {
            boolean integral = isIntegral(left) && isIntegral(right);
            return integral ? (Object) (long) value : (Object) value;
        }

        /**
         * 是否整数操作数。
         *
         * @param value 操作数
         * @return 整数返回 true
         */
        private boolean isIntegral(Object value) {
            return value instanceof Integer || value instanceof Long || value instanceof Short
                    || value instanceof BigDecimal || value instanceof java.math.BigInteger;
        }

        /**
         * 转数字，失败返回 0。
         *
         * @param value 操作数
         * @return 数值
         */
        private double number(Object value) {
            Double parsed = asNumber(value);
            return parsed == null ? 0D : parsed;
        }

        /**
         * 尝试转数字。
         *
         * @param value 操作数
         * @return 数值，非数字返回 null
         */
        private Double asNumber(Object value) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value == null || value instanceof Boolean || value instanceof Collection) {
                return null;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Double.valueOf(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
