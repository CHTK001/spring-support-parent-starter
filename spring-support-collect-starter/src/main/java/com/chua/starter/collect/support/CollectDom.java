package com.chua.starter.collect.support;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 抽取来源归一化：把变量里可能出现的任意宿主统一成文档、元素集合、文本或 JSON 结构。
 *
 * <p>抽取节点与表达式函数共用一套口径，避免同一段 HTML 在页面上抽得到、在表达式里抽不到。
 * 宿主允许是 jsoup 文档/元素/元素集合、抓取响应映射、任意集合，或纯文本；
 * 响应映射带 {@code html} 键时按 HTML 解析，其余映射与集合按 JSON 处理。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectDom {

    /**
     * 响应映射里的 HTML 键。
     */
    private static final String HTML_KEY = "html";

    /**
     * 工具类禁止实例化。
     */
    private CollectDom() {
    }

    /**
     * 取 jsoup 元素，取不到返回 null。
     *
     * @param item 宿主
     * @return 元素或文档
     */
    public static Element element(Object item) {
        if (item instanceof Element) {
            return (Element) item;
        }
        if (item instanceof Elements) {
            Elements elements = (Elements) item;
            return elements.isEmpty() ? null : elements.first();
        }
        if (item instanceof Collection) {
            for (Object value : (Collection<?>) item) {
                Element element = element(value);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        String text = htmlText(item);
        return text.isBlank() ? null : Jsoup.parse(text);
    }

    /**
     * 宿主转元素集合。
     *
     * @param item 宿主
     * @return 元素集合，取不到返回空集合
     */
    public static Elements elements(Object item) {
        Elements elements = new Elements();
        if (item instanceof Elements) {
            elements.addAll((Elements) item);
            return elements;
        }
        if (item instanceof Element) {
            elements.add((Element) item);
            return elements;
        }
        if (item instanceof Collection) {
            for (Object value : (Collection<?>) item) {
                if (value instanceof Element) {
                    elements.add((Element) value);
                } else {
                    elements.addAll(elements(value));
                }
            }
            return elements;
        }
        Element element = element(item);
        if (element != null) {
            elements.add(element);
        }
        return elements;
    }

    /**
     * CSS 选择。
     *
     * @param item   宿主
     * @param cssQuery 选择器，留空表示取宿主自身
     * @return 命中的元素集合，无法按 HTML 解析时返回空集合
     */
    public static Elements select(Object item, String cssQuery) {
        if (cssQuery == null || cssQuery.isBlank()) {
            return elements(item);
        }
        Element root = element(item);
        return root == null ? new Elements() : root.select(cssQuery.trim());
    }

    /**
     * CSS 选择首个元素。
     *
     * @param item     宿主
     * @param cssQuery 选择器
     * @return 首个元素，未命中返回 null
     */
    public static Element selectFirst(Object item, String cssQuery) {
        Elements matched = select(item, cssQuery);
        return matched.isEmpty() ? null : matched.first();
    }

    /**
     * XPath 取值。
     *
     * <p><b>实现口径</b>：用 jsoup 自带的 {@link W3CDom} 把宿主转成标准
     * {@link org.w3c.dom.Document}，再用 JDK 内置的 {@link XPathFactory} 求值 ——
     * 全程只依赖 jsoup 与 JDK，不引入任何第三方 XPath 库。</p>
     *
     * <p>固定按字符串求值（{@link XPathConstants#STRING}）：节点集表达式只取首个节点的
     * 字符串值，属性表达式如 {@code //a/@href} 直接得到属性值。因此 XPath 取值不区分
     * {@code text}/{@code html} 等取出方式，属性交给表达式自己写。</p>
     *
     * @param item       宿主
     * @param expression XPath 表达式
     * @return 取值结果；表达式为空或宿主无法解析成文档返回 null，表达式写法错误抛参数异常
     */
    public static String xpath(Object item, String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        Element root = element(item);
        if (root == null) {
            return null;
        }
        // 关键：item 本身是 jsoup Document 时必须走 fromJsoup(Document) 重载。
        // element() 的返回类型是 Element，若直接把它交给 fromJsoup，编译期会选中
        // fromJsoup(Element) —— 整个文档被当成「普通元素」转换，文档级结构被削掉。
        // 症状很隐蔽：属性路径 //a/@href 能取到，元素与文本路径 //h1、//h1/text() 全是空。
        // 验收实测过：同一页面上 //a/@href 有值而 //h1 为空。
        // 还要看 element() 的实际运行时类型：响应 Map（{html: "..."}）取出的也是
        // jsoup Document，但静态类型是 Element，同样必须走 Document 重载。
        var dom = new W3CDom().namespaceAware(false);
        var document = root instanceof Document ? dom.fromJsoup((Document) root) : dom.fromJsoup(root);
        String expr = expression.trim();
        var evaluator = XPathFactory.newInstance().newXPath();
        Object result;
        try {
            // 优先 NODESET：它对元素、文本、属性三种节点都能取到人要的那串字符，
            // 于是 //h1、//h1/text()、//a/@href 三种写法统一有值。
            // 不用 STRING 直接求：STRING 在「文本节点集」上取不到值（//h1/text() 实测返回空）。
            result = evaluator.evaluate(expr, document, XPathConstants.NODESET);
        } catch (XPathException nodeSetFail) {
            // 表达式求的是标量（string(...)、count(...) 这类）时，转到 NODESET 会抛，退回 STRING。
            try {
                result = evaluator.evaluate(expr, document, XPathConstants.STRING);
            } catch (XPathException ex) {
                throw new IllegalArgumentException("XPath 写法错误: " + ex.getMessage(), ex);
            }
        }
        // 必须用 instanceof 判型而不是直接 (NodeList) 强转：某些实现对标量表达式
        // 不抛异常、而是直接把 String 作为结果返回，强转会抛 ClassCastException —— 那不是
        // XPathException，上面的 catch 抓不到，整条字段会莫名变成空（实测踩过 string(//a/@href)）。
        if (result instanceof NodeList nodes) {
            if (nodes.getLength() == 0 || nodes.item(0) == null) {
                return null;
            }
            return nodes.item(0).getTextContent();
        }
        return result == null ? null : String.valueOf(result);
    }

    /**
     * 宿主文本，用于正则与 TEXT 解析。
     *
     * @param item 宿主
     * @return 文本，元素返回可见文本，映射与集合返回 JSON
     */
    public static String text(Object item) {
        if (item instanceof Element) {
            return ((Element) item).text();
        }
        if (item instanceof Map || item instanceof Collection) {
            return CollectJson.toJson(item);
        }
        return visibleText(htmlText(item));
    }

    /**
     * 纯文本宿主带标记时取其可见文本。
     *
     * <p>没有元素就不解析，避免把日志、正文这类纯文本的空白折叠掉。</p>
     *
     * @param raw 宿主文本
     * @return 可见文本或原文
     */
    private static String visibleText(String raw) {
        Element body = Jsoup.parseBodyFragment(raw).body();
        return body.children().isEmpty() ? raw : body.text();
    }

    /**
     * 宿主 HTML。
     *
     * @param item 宿主
     * @return 元素返回 outerHtml，其余按文本
     */
    public static String html(Object item) {
        if (item instanceof Element) {
            return ((Element) item).outerHtml();
        }
        return htmlText(item);
    }

    /**
     * 宿主转 JSON 结构。
     *
     * @param item 宿主
     * @return 映射、列表或标量，解析失败返回 null
     */
    public static Object json(Object item) {
        if (item instanceof Map || item instanceof List) {
            return item;
        }
        return CollectJson.parseAny(text(item));
    }

    /**
     * 取属性：先看宿主自身，再看它下面的第一个带该属性的元素。
     *
     * <p>文档节点自己不带属性，只读首元素会让 {@code ${resp.attr('href')}} 恒空。</p>
     *
     * @param item 宿主
     * @param key  属性名
     * @return 属性值，缺失返回 null
     */
    public static String attr(Object item, String key) {
        if (key == null) {
            return null;
        }
        for (Element element : descendants(item)) {
            String value = element.attr(key);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 收集属性：扫描宿主及其后代上带该属性的元素。
     *
     * <p>整页文档上没有属性，所以这一投影按子树扫；它自身会过滤掉不带该属性的元素，
     * 不像 {@link #texts(Object)} 与 {@link #htmls(Object)} 那样按选中集合逐一对位。</p>
     *
     * @param item 宿主
     * @param key  属性名
     * @return 属性值列表，已跳过缺失项
     */
    public static List<String> attrs(Object item, String key) {
        List<String> values = new ArrayList<>();
        if (key == null) {
            return values;
        }
        for (Element element : descendants(item)) {
            String value = element.attr(key);
            if (!value.isEmpty() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * 宿主自身加全部后代元素。
     *
     * @param item 宿主
     * @return 元素集合
     */
    private static Elements descendants(Object item) {
        Elements all = new Elements();
        for (Element element : elements(item)) {
            all.add(element);
            all.addAll(element.select("*"));
        }
        return all;
    }

    /**
     * 逐元素取绝对链接。
     *
     * @param item 宿主
     * @return 链接列表，已去空
     */
    public static List<String> links(Object item) {
        List<String> links = new ArrayList<>();
        for (Element element : elements(item)) {
            collectLinks(links, element);
        }
        return links;
    }

    /**
     * 收集单个元素及其子树里的链接。
     *
     * @param links   目标列表
     * @param element 根元素
     */
    private static void collectLinks(List<String> links, Element element) {
        addLink(links, absLink(element, "href"));
        addLink(links, absLink(element, "src"));
        for (Element child : element.select("a[href], img[src], source[src], link[href]")) {
            addLink(links, absLink(child, "href"));
            addLink(links, absLink(child, "src"));
        }
    }

    /**
     * 取绝对链接，无基址时退回原值。
     *
     * @param element 元素
     * @param key     属性名
     * @return 链接，缺失返回 null
     */
    private static String absLink(Element element, String key) {
        if (!element.hasAttr(key)) {
            return null;
        }
        String abs = element.absUrl(key);
        return abs.isBlank() ? element.attr(key) : abs;
    }

    /**
     * 逐元素取文本。
     *
     * @param item 宿主
     * @return 文本列表
     */
    public static List<String> texts(Object item) {
        List<String> values = new ArrayList<>();
        for (Element element : elements(item)) {
            values.add(element.text());
        }
        return values;
    }

    /**
     * 逐元素取 HTML。
     *
     * @param item 宿主
     * @return HTML 列表
     */
    public static List<String> htmls(Object item) {
        List<String> values = new ArrayList<>();
        for (Element element : elements(item)) {
            values.add(element.outerHtml());
        }
        return values;
    }

    /**
     * 正则取首个匹配，按宿主的标记文本匹配（元素取 outerHtml、纯文本取原文、映射取 JSON）。
     *
     * @param source  宿主
     * @param pattern 正则
     * @param group   捕获组序号，空按 1、0 表示整串
     * @return 匹配结果，未命中返回 null
     */
    public static String regex(Object source, String pattern, Integer group) {
        if (pattern == null || pattern.isEmpty()) {
            return html(source);
        }
        Matcher matcher = compile(pattern).matcher(html(source));
        if (!matcher.find()) {
            return null;
        }
        return groupOf(matcher, group);
    }

    /**
     * 正则取全部匹配，口径同 {@link #regex(Object, String, Integer)}。
     *
     * @param source  宿主
     * @param pattern 正则
     * @param group   捕获组序号，空按 1、0 表示整串
     * @return 匹配结果列表，未命中返回空列表
     */
    public static List<String> regexAll(Object source, String pattern, Integer group) {
        List<String> values = new ArrayList<>();
        String markup = html(source);
        if (pattern == null || pattern.isEmpty()) {
            values.add(markup);
            return values;
        }
        Matcher matcher = compile(pattern).matcher(markup);
        while (matcher.find()) {
            String value = groupOf(matcher, group);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * 兄弟元素。
     *
     * @param item 宿主
     * @return 兄弟元素集合
     */
    public static Elements sibling(Object item) {
        Elements siblings = new Elements();
        Element element = element(item);
        Element parent = element == null ? null : element.parent();
        if (parent == null) {
            return siblings;
        }
        for (Element child : parent.children()) {
            if (child != element) {
                siblings.add(child);
            }
        }
        return siblings;
    }

    /**
     * 父级元素链。
     *
     * @param item 宿主
     * @return 自近到远的父级元素
     */
    public static Elements parents(Object item) {
        Element element = element(item);
        Elements parents = new Elements();
        Element parent = element == null ? null : element.parent();
        while (parent != null) {
            parents.add(parent);
            parent = parent.parent();
        }
        return parents;
    }

    /**
     * 子元素集合。
     *
     * @param item 宿主
     * @return 直接子元素
     */
    public static Elements children(Object item) {
        Element element = element(item);
        return element == null ? new Elements() : element.children();
    }

    /**
     * 响应映射里的 HTML 文本，非 HTML 宿主退化为字符串化。
     *
     * @param item 宿主
     * @return 文本
     */
    private static String htmlText(Object item) {
        if (item == null) {
            return "";
        }
        if (item instanceof Map) {
            Object html = ((Map<?, ?>) item).get(HTML_KEY);
            return html == null ? CollectJson.toJson(item) : String.valueOf(html);
        }
        return String.valueOf(item);
    }

    /**
     * 编译正则，语法错误转成带说明的参数异常。
     *
     * @param pattern 正则
     * @return 已编译模式
     */
    private static Pattern compile(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException("正则写法错误: " + ex.getMessage(), ex);
        }
    }

    /**
     * 取捕获组。
     *
     * @param matcher 匹配器
     * @param group   组号，空按 1
     * @return 组值，越界返回 null
     */
    private static String groupOf(Matcher matcher, Integer group) {
        int index = group == null ? 1 : group;
        if (index == 0) {
            return matcher.group();
        }
        return index <= matcher.groupCount() ? matcher.group(index) : null;
    }

    /**
     * 追加非空链接。
     *
     * @param links 目标列表
     * @param value 候选链接
     */
    private static void addLink(List<String> links, String value) {
        if (value != null && !value.isBlank() && !links.contains(value)) {
            links.add(value);
        }
    }
}
