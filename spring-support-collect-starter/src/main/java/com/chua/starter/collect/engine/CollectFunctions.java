package com.chua.starter.collect.engine;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.support.CollectDom;
import com.chua.starter.collect.support.CollectJson;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表达式内置函数。
 *
 * <p>DSL 中写作 {@code ${md5(x)}}，名字由 {@link CollectBuiltinInvoker} 在编译期白名单里解析；
 * 脚本节点的 {@code fn.md5(x)} 走同一张表。除 {@code date}/{@code uuid}/{@code random} 外均为纯函数。</p>
 *
 * <p>「宿主方法」一节的函数一律把宿主放在首个 {@code Object} 形参上，因此
 * {@code ${attr(selector(doc, 'a'), 'href')}} 与方法链写法
 * {@code ${doc.selector('a').attr('href')}} 解析到同一个函数。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectFunctions {

    /**
     * 随机串字母表，去掉了易混淆的 0/O/1/l。
     */
    private static final String RANDOM_CHARS = "abcdefghijkmnopqrstuvwxyz23456789";

    /**
     * 工具类禁止实例化。
     */
    private CollectFunctions() {
    }

    /**
     * MD5 摘要。
     *
     * @param text 原文
     * @return 32 位小写十六进制
     */
    public static String md5(String text) {
        return digest("MD5", text);
    }

    /**
     * SHA-1 摘要。
     *
     * @param text 原文
     * @return 40 位小写十六进制
     */
    public static String sha1(String text) {
        return digest("SHA-1", text);
    }

    /**
     * 计算摘要。
     *
     * @param algorithm 算法名
     * @param text      原文
     * @return 十六进制摘要，入参为空返回空串
     */
    private static String digest(String algorithm, String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(Character.forDigit((item >> 4) & 0xF, 16));
                builder.append(Character.forDigit(item & 0xF, 16));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(algorithm + " 计算失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * Base64 编码。
     *
     * @param text 原文
     * @return 编码结果
     */
    public static String base64Encode(String text) {
        return text == null ? "" : Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64 解码。
     *
     * @param text 编码文本
     * @return 原文
     */
    public static String base64Decode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }

    /**
     * URL 编码。
     *
     * @param text 原文
     * @return 编码结果
     */
    public static String urlEncode(String text) {
        if (text == null) {
            return "";
        }
        try {
            return URLEncoder.encode(text, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            return text;
        }
    }

    /**
     * URL 解码。
     *
     * @param text 编码文本
     * @return 原文
     */
    public static String urlDecode(String text) {
        if (text == null) {
            return "";
        }
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            return text;
        }
    }

    /**
     * 取 URL 的域名部分。
     *
     * @param url 完整地址
     * @return 域名，解析失败返回空串
     */
    public static String urlHost(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            String host = new java.net.URI(url.trim()).getHost();
            return host == null ? "" : host;
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * 正则取首个匹配。
     *
     * @param text    原文
     * @param regex   正则
     * @param group   捕获组序号，0 表示整段
     * @return 匹配内容，未命中返回空串
     */
    public static String regexFirst(String text, String regex, int group) {
        if (text == null || regex == null) {
            return "";
        }
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(Math.max(group, 0));
    }

    /**
     * 正则取全部匹配并用分隔符连接。
     *
     * @param text      原文
     * @param regex     正则
     * @param group     捕获组序号
     * @param separator 分隔符
     * @return 连接结果
     */
    public static String regexAll(String text, String regex, int group, String separator) {
        List<String> hits = new ArrayList<>();
        if (text != null && regex != null) {
            Matcher matcher = Pattern.compile(regex).matcher(text);
            while (matcher.find()) {
                hits.add(matcher.group(Math.max(group, 0)));
            }
        }
        return String.join(separator == null ? "" : separator, hits);
    }

    /**
     * 当前时间戳毫秒。
     *
     * @return epoch 毫秒
     */
    public static long timestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 当前日期时间文本。
     *
     * @param pattern 格式，例如 yyyy-MM-dd HH:mm:ss
     * @return 格式化结果
     */
    public static String dateNow(String pattern) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(defaultPattern(pattern)));
    }

    /**
     * 日期加减天数。
     *
     * @param date    起始日期，格式同 pattern
     * @param pattern 日期格式
     * @param days    增减天数
     * @return 计算结果文本
     */
    public static String dateAddDays(String date, String pattern, long days) {
        LocalDate parsed = LocalDate.parse(date, DateTimeFormatter.ofPattern(defaultPattern(pattern)));
        return parsed.plusDays(days).format(DateTimeFormatter.ofPattern(defaultPattern(pattern)));
    }

    /**
     * 默认日期格式。
     *
     * @param pattern 传入格式
     * @return 非空格式
     */
    private static String defaultPattern(String pattern) {
        return pattern == null || pattern.isBlank() ? "yyyy-MM-dd HH:mm:ss" : pattern;
    }

    /**
     * 生成不含横线的 UUID。
     *
     * @return 32 位字符串
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 集合大小。
     *
     * @param value 集合、数组或映射
     * @return 元素个数，空返回 0
     */
    public static int size(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).size();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).size();
        }
        if (value.getClass().isArray()) {
            return ReflectUtils.arrayLength(value);
        }
        if (value instanceof org.jsoup.select.Elements) {
            return ((org.jsoup.select.Elements) value).size();
        }
        return String.valueOf(value).isEmpty() ? 0 : 1;
    }

    /**
     * 集合大小，与 {@link #size(Object)} 同义。
     *
     * <p>历史上表达式里集合只能用 {@code size(x)}，而 {@code count(x)} 会报「不支持的函数」，
     * 用户按直觉写 {@code ${count(rows)}} 时无从判断是语法问题还是数据问题，本别名统一两者。</p>
     *
     * @param value 集合、数组、映射或标量
     * @return 元素个数，空返回 0
     */
    public static int count(Object value) {
        return size(value);
    }

    /**
     * 条件取值：条件为真返回 {@code whenTrue}，否则返回 {@code whenFalse}。
     *
     * <p>配合 {@code ?:} 使用，便于在单个参数里做兜底而不必写嵌套三元。</p>
     *
     * @param condition 条件，按 {@link CollectExpression#truthy(Object)} 判定
     * @param whenTrue  条件为真时的结果
     * @param whenFalse 条件为假时的结果
     * @return 命中的分支结果
     */
    public static Object ifThenElse(Object condition, Object whenTrue, Object whenFalse) {
        return CollectExpression.truthy(condition) ? whenTrue : whenFalse;
    }

    /**
     * 条件取文本：任一分支为 null 时返回空串。
     *
     * @param condition 条件
     * @param whenTrue  条件为真时的文本
     * @param whenFalse 条件为假时的文本
     * @return 命中的分支文本
     */
    public static String ifThenElseText(Object condition, Object whenTrue, Object whenFalse) {
        return stringify(ifThenElse(condition, whenTrue, whenFalse));
    }

    /**
     * 取集合元素，越界返回 null。
     *
     * @param value 集合或数组
     * @param index 下标
     * @return 元素
     */
    public static Object elementAt(Object value, int index) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return index >= 0 && index < list.size() ? list.get(index) : null;
        }
        if (value != null && value.getClass().isArray()) {
            int length = ReflectUtils.arrayLength(value);
            return index >= 0 && index < length ? ReflectUtils.arrayGet(value, index) : null;
        }
        if (value instanceof org.jsoup.select.Elements) {
            org.jsoup.select.Elements elements = (org.jsoup.select.Elements) value;
            return index >= 0 && index < elements.size() ? elements.get(index) : null;
        }
        return null;
    }

    /**
     * 集合拼接为文本。
     *
     * @param value     集合
     * @param separator 分隔符
     * @return 拼接结果
     */
    public static String join(Object value, String separator) {
        List<String> parts = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                parts.add(stringify(item));
            }
        } else if (value instanceof org.jsoup.select.Elements) {
            for (org.jsoup.nodes.Element element : (org.jsoup.select.Elements) value) {
                parts.add(element.text());
            }
        }
        return String.join(separator == null ? "" : separator, parts);
    }

    /**
     * 空值兜底。
     *
     * @param value        原值
     * @param defaultValue 默认值
     * @return 原值为空时返回默认值
     */
    public static String defaultIfEmpty(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 转文本，null 转空串。
     *
     * @param value 任意值
     * @return 文本
     */
    public static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof org.jsoup.nodes.Element) {
            return ((org.jsoup.nodes.Element) value).text();
        }
        return String.valueOf(value);
    }

    /**
     * 转整数。
     *
     * @param value 任意值
     * @param fallback 兜底值
     * @return 整数
     */
    public static int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * 参数列表转映射，供 SQL 节点与请求体使用。
     *
     * @param params 名称值对列表
     * @return 有序映射
     */
    public static Map<String, String> toMap(List<CollectParamDTO> params) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        if (params != null) {
            for (CollectParamDTO param : params) {
                if (param.name() != null && !param.name().isBlank()) {
                    map.put(param.name().trim(), param.value() == null ? "" : param.value());
                }
            }
        }
        return map;
    }

    /**
     * 截取子串，下标越界自动收窄不抛错。
     *
     * @param text  原文
     * @param start 起始下标（含）
     * @param end   结束下标（不含）
     * @return 子串，原文为空返回空串
     */
    public static String substring(String text, int start, int end) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int from = Math.min(Math.max(start, 0), text.length());
        int to = Math.min(Math.max(end, from), text.length());
        return text.substring(from, to);
    }

    /**
     * 去首尾空白。
     *
     * @param text 原文
     * @return 结果，null 转空串
     */
    public static String trim(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * 转大写。
     *
     * @param text 原文
     * @return 结果，null 转空串
     */
    public static String upper(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    /**
     * 转小写。
     *
     * @param text 原文
     * @return 结果，null 转空串
     */
    public static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    /**
     * 字面量替换（不走正则，避免 {@code $} 与 {@code \} 被误解析）。
     *
     * @param text        原文
     * @param target      被替换内容，空串时原样返回
     * @param replacement 替换内容
     * @return 结果，原文为空返回空串
     */
    public static String replace(String text, String target, String replacement) {
        if (text == null || text.isEmpty() || target == null || target.isEmpty()) {
            return stringify(text);
        }
        return text.replace(target, replacement == null ? "" : replacement);
    }

    /**
     * 按字面量分隔符切分，供 {@code loopItems} 直接遍历。
     *
     * @param text      原文
     * @param separator 分隔符，空则按逗号切
     * @return 分段列表，原文为空返回空列表
     */
    public static List<String> splitToList(String text, String separator) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String split = separator == null || separator.isEmpty() ? "," : separator;
        List<String> parts = new ArrayList<>();
        for (String part : text.split(java.util.regex.Pattern.quote(split))) {
            parts.add(part);
        }
        return parts;
    }

    /**
     * 按点号下标路径取 JSON 字段，与抽取节点的 JSON 解析器同一套实现。
     *
     * @param json JSON 文本
     * @param path 路径，形如 {@code data.items[0].title}，也接受前导 {@code $}
     * @return 字段值（映射、列表或标量），未命中返回 null
     */
    public static Object jsonGet(String json, String path) {
        return CollectJson.path(CollectJson.parseAny(json), path);
    }

    /**
     * 随机整数，用于请求节流抖动。
     *
     * @param bound 上界（不含），非正数返回 0
     * @return 随机值
     */
    public static int randomInt(int bound) {
        return bound <= 0 ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextInt(bound);
    }

    /**
     * 随机字母数字串，用于构造唯一键。
     *
     * @param length 长度，夹到 [0, 64]
     * @return 随机串
     */
    public static String randomString(int length) {
        int size = Math.min(Math.max(length, 0), 64);
        StringBuilder builder = new StringBuilder(size);
        for (int index = 0; index < size; index++) {
            builder.append(RANDOM_CHARS.charAt(randomInt(RANDOM_CHARS.length())));
        }
        return builder.toString();
    }

    /* ------------------------------ 宿主方法 ------------------------------ */

    /**
     * 取宿主可见文本，与抽取节点的 TEXT 解析同一口径。
     *
     * @param host 文档、元素、响应映射或文本
     * @return 文本
     */
    public static String text(Object host) {
        return CollectDom.text(host);
    }

    /**
     * 取宿主 HTML。
     *
     * @param host 文档、元素、响应映射或文本
     * @return HTML 文本
     */
    public static String html(Object host) {
        return CollectDom.html(host);
    }

    /**
     * 取首个元素的属性。
     *
     * @param host 宿主
     * @param key  属性名
     * @return 属性值，缺失返回 null
     */
    public static String attr(Object host, String key) {
        return CollectDom.attr(host, key);
    }

    /**
     * 逐元素取属性。
     *
     * @param host 宿主
     * @param key  属性名
     * @return 属性值列表
     */
    public static List<String> attrs(Object host, String key) {
        return CollectDom.attrs(host, key);
    }

    /**
     * CSS 选择首个元素。
     *
     * @param host  宿主
     * @param query 选择器
     * @return 元素，未命中返回 null
     */
    public static Object selector(Object host, String query) {
        return CollectDom.selectFirst(host, query);
    }

    /**
     * CSS 选择元素集合。
     *
     * @param host  宿主
     * @param query 选择器
     * @return 元素集合
     */
    public static Object selectors(Object host, String query) {
        return CollectDom.select(host, query);
    }

    /**
     * 首个元素。
     *
     * @param host 宿主
     * @return 首个元素，空宿主返回 null
     */
    public static Object first(Object host) {
        org.jsoup.select.Elements elements = CollectDom.elements(host);
        return elements.isEmpty() ? null : elements.first();
    }

    /**
     * 末尾元素。
     *
     * @param host 宿主
     * @return 最后一个元素，空宿主返回 null
     */
    public static Object last(Object host) {
        org.jsoup.select.Elements elements = CollectDom.elements(host);
        return elements.isEmpty() ? null : elements.last();
    }

    /**
     * 父级元素。
     *
     * @param host 宿主
     * @return 父元素，已在根返回 null
     */
    public static Object parent(Object host) {
        org.jsoup.nodes.Element element = CollectDom.element(host);
        return element == null ? null : element.parent();
    }

    /**
     * 父级元素链。
     *
     * @param host 宿主
     * @return 自近到远的父级
     */
    public static Object parents(Object host) {
        return CollectDom.parents(host);
    }

    /**
     * 直接子元素。
     *
     * @param host 宿主
     * @return 子元素集合
     */
    public static Object children(Object host) {
        return CollectDom.children(host);
    }

    /**
     * 兄弟元素。
     *
     * @param host 宿主
     * @return 兄弟元素集合
     */
    public static Object sibling(Object host) {
        return CollectDom.sibling(host);
    }

    /**
     * 取宿主内全部绝对链接。
     *
     * @param host 宿主
     * @return 链接列表
     */
    public static List<String> links(Object host) {
        return CollectDom.links(host);
    }

    /**
     * 逐元素取文本。
     *
     * @param host 宿主
     * @return 文本列表
     */
    public static List<String> texts(Object host) {
        return CollectDom.texts(host);
    }

    /**
     * 逐元素取 HTML。
     *
     * @param host 宿主
     * @return HTML 列表
     */
    public static List<String> htmls(Object host) {
        return CollectDom.htmls(host);
    }

    /**
     * 正则取首个匹配。
     *
     * @param host   宿主
     * @param regex  正则
     * @param group  捕获组序号，空按 1
     * @return 匹配结果，未命中返回 null
     */
    public static String regx(Object host, String regex, Integer group) {
        return CollectDom.regex(host, regex, group);
    }

    /**
     * 正则取全部匹配。
     *
     * @param host  宿主
     * @param regex 正则
     * @param group 捕获组序号，空按 1
     * @return 匹配列表
     */
    public static List<String> regxs(Object host, String regex, Integer group) {
        return CollectDom.regexAll(host, regex, group);
    }

    /**
     * JSON 路径取值，接受映射、列表、元素与文本宿主。
     *
     * @param host 宿主
     * @param path 路径，形如 {@code $.data.items[0].id}
     * @return 路径值，未命中返回 null
     */
    public static Object jsonpath(Object host, String path) {
        return CollectJson.path(CollectDom.json(host), path);
    }

    /* ------------------------------ 文本判断 ------------------------------ */

    /**
     * 是否包含子串。
     *
     * @param text 原文
     * @param part 子串
     * @return 包含返回 true
     */
    public static boolean contains(String text, String part) {
        return text != null && part != null && text.contains(part);
    }

    /**
     * 子串首次出现位置。
     *
     * @param text 原文
     * @param part 子串
     * @return 下标，未命中返回 -1
     */
    public static int indexOf(String text, String part) {
        return text == null || part == null ? -1 : text.indexOf(part);
    }

    /**
     * 是否以指定前缀开始。
     *
     * @param text  原文
     * @param prefix 前缀
     * @return 命中返回 true
     */
    public static boolean startsWith(String text, String prefix) {
        return text != null && prefix != null && text.startsWith(prefix);
    }

    /**
     * 是否以指定后缀结束。
     *
     * @param text  原文
     * @param suffix 后缀
     * @return 命中返回 true
     */
    public static boolean endsWith(String text, String suffix) {
        return text != null && suffix != null && text.endsWith(suffix);
    }

    /**
     * 整串正则匹配。
     *
     * @param text  原文
     * @param regex 正则
     * @return 匹配返回 true
     */
    public static boolean matches(String text, String regex) {
        return text != null && regex != null && Pattern.matches(regex, text);
    }

    /**
     * 长度：文本按字符数，其余按元素个数。
     *
     * @param value 文本、集合或元素集合
     * @return 长度
     */
    public static int length(Object value) {
        if (value instanceof CharSequence) {
            return ((CharSequence) value).length();
        }
        return size(value);
    }

    /* ------------------------------ 集合与映射 ------------------------------ */

    /**
     * 按文本序升序。
     *
     * @param value 集合
     * @return 新列表
     */
    public static List<String> sort(Object value) {
        List<String> parts = new ArrayList<>();
        for (Object item : items(value)) {
            parts.add(stringify(item));
        }
        java.util.Collections.sort(parts);
        return parts;
    }

    /**
     * 反转集合。
     *
     * @param value 集合
     * @return 新列表
     */
    public static List<Object> reverse(Object value) {
        List<Object> parts = items(value);
        java.util.Collections.reverse(parts);
        return parts;
    }

    /**
     * 取映射键。
     *
     * @param value 映射
     * @return 键列表
     */
    public static List<String> keys(Object value) {
        List<String> names = new ArrayList<>();
        if (value instanceof Map) {
            for (Object key : ((Map<?, ?>) value).keySet()) {
                names.add(stringify(key));
            }
        }
        return names;
    }

    /**
     * 取映射值。
     *
     * @param value 映射
     * @return 值列表
     */
    public static List<Object> values(Object value) {
        List<Object> parts = new ArrayList<>();
        if (value instanceof Map) {
            parts.addAll(((Map<?, ?>) value).values());
        }
        return parts;
    }

    /**
     * 通用取值：映射按键、集合按下标。
     *
     * @param value 宿主
     * @param key   键或下标
     * @return 值，未命中返回 null
     */
    public static Object get(Object value, Object key) {
        if (value instanceof Map) {
            return ((Map<?, ?>) value).get(stringify(key));
        }
        if (key == null) {
            return null;
        }
        return elementAt(value, toInt(stringify(key), 0));
    }

    /**
     * 集合宿主转可变列表，非集合按单元素处理。
     *
     * @param value 宿主
     * @return 可变列表
     */
    private static List<Object> items(Object value) {
        List<Object> parts = new ArrayList<>();
        if (value instanceof Collection) {
            parts.addAll((Collection<?>) value);
        } else if (value instanceof org.jsoup.select.Elements) {
            parts.addAll((org.jsoup.select.Elements) value);
        } else if (value != null && value.getClass().isArray()) {
            int length = ReflectUtils.arrayLength(value);
            for (int index = 0; index < length; index++) {
                parts.add(ReflectUtils.arrayGet(value, index));
            }
        } else if (value != null) {
            parts.add(value);
        }
        return parts;
    }
}
