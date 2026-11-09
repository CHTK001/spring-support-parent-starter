package com.chua.starter.collect.support;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * 块文本里需要保留边界的块级标签。
     *
     * <p>这些标签的前后边界折成一个空格 —— 与库内历史数据的口径一致：段落之间用空格连接，
     * 只有 {@code <br>} 才断行。行内标签（{@code strong}、{@code a}、{@code span}）
     * 与相邻文本直接相连，避免把一句话拆开。</p>
     */
    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "section", "article", "li", "tr", "blockquote", "pre",
            "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table");

    /**
     * 分节标题词：块内用它把「译文 / 注释 / 赏析」切成多段。
     *
     * <p>只放规范化之后的标题：站点上的同义写法由 {@link #HEAD_ALIASES} 收口，
     * 带编号的写法（{@code 其一译文}、{@code 译文二}）由 {@link #matchHead(String)} 去掉编号后比对。</p>
     */
    private static final Set<String> SECTION_HEADS = Set.of(
            "译文", "注释", "赏析", "创作背景", "文言知识");

    /**
     * 分节标题同义写法到规范标题的映射。
     *
     * <p>古文岛同一节在不同页面上的标题并不统一：注释节会写成「注解」甚至错字「注人」「部释」，
     * 译文节会写成「释文」「翻译」，赏析节会写成「评析 / 简析 / 鉴赏 / 解读」。
     * 归一到同一节之后，{@code sectionText(块, '注释')} 才取得到这些页面的注释。</p>
     *
     * <p>「释文」在古籍整理里通常指白话译文（如《再上湘江》页「释文」下是整首白话），
     * 归到译文节；它下面的「注释」才是注释。</p>
     */
    private static final Map<String, String> HEAD_ALIASES = Map.ofEntries(
            Map.entry("注解", "注释"),
            Map.entry("部释", "注释"),
            Map.entry("注人", "注释"),
            Map.entry("译注", "注释"),
            Map.entry("释文", "译文"),
            Map.entry("翻译", "译文"),
            Map.entry("译的", "译文"),
            Map.entry("评析", "赏析"),
            Map.entry("简析", "赏析"),
            Map.entry("鉴赏", "赏析"),
            Map.entry("解读", "赏析"));

    /**
     * 标题开头的编号：{@code 其一译文}、{@code 第一译文}、{@code 二译文}。
     */
    private static final Pattern HEAD_NUMBER_PREFIX = Pattern.compile("^(?:其|第)?[一二三四五六七八九十百0-9]+");

    /**
     * 标题结尾的编号：{@code 译文二}、{@code 译文（1）}。
     */
    private static final Pattern HEAD_NUMBER_SUFFIX =
            Pattern.compile("[（(]?[一二三四五六七八九十百0-9]+[）)]?$");

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
     * 块正文：把「标题 + 正文」结构的 HTML 块转成多行文本。
     *
     * <p>与 {@link #text(Object)} 的差别在换行：{@code text} 按 HTML 显式语义把空白折叠成空格，
     * {@code <br>} 处也不留边界；这里把 {@code <br>} 保留成 {@code \n}，块级标签的边界折成空格
     * （与库内历史数据的口径一致），并去掉块内的标题（{@code h1}~{@code h6}）与
     * 「展开阅读全文 / 收起」这类脚本锚点，得到可以直接入库的正文。
     * 段首缩进（U+3000）保留，行首行尾的 ASCII 空白清掉。</p>
     *
     * @param item 宿主，元素、元素集合或 HTML 文本
     * @return 多行正文，取不到返回空串
     */
    public static String blockText(Object item) {
        Element root = unwrapBlock(element(item));
        if (root == null) {
            return "";
        }
        Element copy = prepareBlock(root);
        StringBuilder builder = new StringBuilder();
        appendBlockText(copy, builder);
        return stripLeadingHead(normalizeBlockText(builder.toString()));
    }

    /**
     * 丢掉重解析带进来的缩进空白节点。
     *
     * <p>{@code outerHtml()} 是美化输出，标签之间有换行缩进；把它重新解析成文档后，
     * 这些缩进会变成含换行的纯空白文本节点。留着它们会把原本同一行的内容拆成多行，
     * 让「元素直传」与「HTML 字符串」两条路径得到不同正文。判据是「纯空白且含换行」，
     * 站点正文里的真实空格不含换行，不会被误删。</p>
     *
     * @param root 块根
     */
    private static void dropPrettyText(Element root) {
        for (Node child : new ArrayList<>(root.childNodes())) {
            if (child instanceof TextNode textNode) {
                String raw = textNode.getWholeText();
                if (raw.isBlank() && (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0)) {
                    textNode.remove();
                }
                continue;
            }
            if (child instanceof Element element) {
                dropPrettyText(element);
            }
        }
    }

    /**
     * 解析块宿主后剥掉 {@code html/body} 包装。
     *
     * <p>抽取节点用 {@code attribute=outer} 取出的是整段块 HTML，{@link #element(Object)}
     * 会把它解析成一整篇文档；分节要靠块的直接子元素，所以这里必须先落到真正的块元素上，
     * 否则 {@code html} 元素会被当成分节的直接子元素、永远找不到 {@code <p>} 分界。</p>
     *
     * @param root 解析出的元素
     * @return 真正的块元素
     */
    private static Element unwrapBlock(Element root) {
        Element current = root;
        if (current instanceof Document document) {
            current = document.body();
        }
        while (current != null && isWrapper(current)) {
            Elements children = current.children();
            if (children.size() != 1) {
                break;
            }
            Element only = children.first();
            // 只有「容器本身没有正文」时才继续往下剥：正文块的直接子元素是分节段落，
            // 再剥一层就会把分节边界剥掉。
            if (hasOwnText(current) || !isContainer(only)) {
                break;
            }
            current = only;
        }
        return current;
    }

    /**
     * 是否是可以安全剥掉的包装层：{@code body} 或自身没有正文的容器。
     *
     * @param element 元素
     * @return 是包装层返回 true
     */
    private static boolean isWrapper(Element element) {
        return "body".equals(element.tagName()) || (!hasOwnText(element) && isContainer(element));
    }

    /**
     * 元素自身是否带非空白直接文本。
     *
     * @param element 元素
     * @return 有直接文本返回 true
     */
    private static boolean hasOwnText(Element element) {
        for (Node child : element.childNodes()) {
            if (child instanceof TextNode textNode && !textNode.getWholeText().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否是块容器标签。
     *
     * @param element 元素
     * @return 块容器返回 true
     */
    private static boolean isContainer(Element element) {
        String tag = element.tagName();
        return "div".equals(tag) || "section".equals(tag) || "article".equals(tag);
    }

    /**
     * 去掉正文开头单独一行的分节标题（如「译文」）。
     *
     * @param text 归一化后的正文
     * @return 去掉首行标题的正文
     */
    private static String stripLeadingHead(String text) {
        if (text.isEmpty()) {
            return text;
        }
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        if (matchHead(first) == null) {
            return text;
        }
        return end < 0 ? "" : text.substring(end + 1);
    }

    /**
     * 分节正文：从「标题 + 正文」块里取「译文 / 注释 / 赏析」中的一段。
     *
     * <p>站点把译文与注释放在同一个块里，用 {@code <p><strong>译文</strong><br>} 与
     * {@code <p><strong>注释</strong><br>} 分界；这一节要把它们拆成两个字段。
     * 标题有三种写法都要认：{@code <p><strong>} 包裹、裸 {@code <strong>/<b>}、
     * 以及带编号的 {@code 其一译文 / 译文二}。</p>
     *
     * <p>同一节出现多次时（如「送应氏二首」的 {@code 其一译文} 与 {@code 其二译文}）
     * 按出现顺序用换行拼接，避免只取到第一首。</p>
     *
     * <p>返回值有三种情形：块里没有任何分节标记时返回 {@code null}（整块只有一种内容）；
     * 有标记但缺目标节时返回空串；命中目标节时返回该节正文。</p>
     *
     * @param item    宿主，元素、元素集合或 HTML 文本
     * @param heading 目标节标题，如 {@code 译文}、{@code 注释}
     * @return 该节正文，无分节标记返回 null
     */
    public static String sectionText(Object item, String heading) {
        Element root = unwrapBlock(element(item));
        String target = matchHead(heading);
        if (root == null || target == null) {
            return null;
        }
        Element copy = prepareBlock(root);
        List<String> heads = new ArrayList<>();
        List<StringBuilder> bodies = new ArrayList<>();
        for (Node child : copy.childNodes()) {
            String head = child instanceof Element element ? sectionHead(element) : null;
            if (head != null) {
                heads.add(head);
                bodies.add(new StringBuilder());
                appendBlockText(withoutHead((Element) child, head), bodies.get(bodies.size() - 1));
                continue;
            }
            // 首个分节标题之前的内容不属于任何一节（通常是块标题与「朗读」锚点）
            if (!bodies.isEmpty()) {
                appendBlockText(child, bodies.get(bodies.size() - 1));
            }
        }
        if (heads.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < heads.size(); i++) {
            if (!target.equals(heads.get(i))) {
                continue;
            }
            String text = normalizeBlockText(bodies.get(i).toString());
            if (!text.isEmpty()) {
                parts.add(text);
            }
        }
        return String.join("\n", parts);
    }

    /**
     * 分节正文，块里没有分节标记时回落到整块正文。
     *
     * <p>与 {@code defaultIfEmpty(sectionText(item, heading), blockText(item))} 的差别只在
     * 「有分节标记但缺目标节」这一种：那时 {@code sectionText} 是空串、{@code defaultIfEmpty}
     * 会回落到整块正文，于是「只有注释」的页面会把注释写进译文列；这里只在**没有分节标记**时
     * 回落，缺目标节仍返回空串。</p>
     *
     * @param item    宿主，元素、元素集合或 HTML 文本
     * @param heading 目标节标题
     * @return 该节正文；无分节标记时返回整块正文；缺目标节返回空串
     */
    public static String sectionTextOrBlock(Object item, String heading) {
        String section = sectionText(item, heading);
        return section == null ? blockText(item) : section;
    }

    /**
     * 复制块并去掉块内标题与「收起 / 展开阅读全文」锚点。
     *
     * @param root 块根
     * @return 可继续遍历的副本
     */
    private static Element prepareBlock(Element root) {
        Element copy = root.clone();
        // outerHtml 默认带缩进换行，重解析后会在块与行内标签之间留下纯空白文本节点。
        // 这些节点会把「同一行」拆成多行，必须按「含换行的纯空白」这个特征清掉：
        // 站点正文里的真实空格不含换行，不会被误删。
        dropPrettyText(copy);
        for (Element heading : new ArrayList<>(copy.select("h1,h2,h3,h4,h5,h6"))) {
            heading.remove();
        }
        for (Element anchor : new ArrayList<>(copy.select("a[href]"))) {
            String href = anchor.attr("href").trim();
            String label = anchor.text().trim();
            if (href.startsWith("javascript:") && (label.isEmpty()
                    || "收起".equals(label) || "展开阅读全文".equals(label))) {
                anchor.remove();
            }
        }
        return copy;
    }

    /**
     * 判定元素是否以分节标题开头，是则返回规范标题。
     *
     * <p>三种写法都要认：完整版是 {@code <p><strong>译文<br></strong>……}，
     * 截断版是 {@code <p>译文<br>……}（只有纯文本），
     * 裸标题版是 {@code <strong>译文</strong><br>……}（{@code strong} 是块的直接子元素）；
     * 三种都可能带编号，如 {@code 其一译文}。</p>
     *
     * @param element 块内直接子元素
     * @return 命中的分节标题，未命中返回 null
     */
    private static String sectionHead(Element element) {
        String tag = element.tagName();
        if ("strong".equals(tag) || "b".equals(tag)) {
            return matchHead(element.text());
        }
        if (!"p".equals(tag) && !"div".equals(tag)) {
            return null;
        }
        Element first = element.children().first();
        if (first != null && ("strong".equals(first.tagName()) || "b".equals(first.tagName()))) {
            String head = matchHead(first.text());
            if (head != null) {
                return head;
            }
        }
        for (Node child : element.childNodes()) {
            if (child instanceof TextNode textNode) {
                String raw = textNode.getWholeText();
                if (raw.isBlank()) {
                    continue;
                }
                return matchHead(raw);
            }
            if (!"br".equals(child.nodeName())) {
                return null;
            }
        }
        return null;
    }

    /**
     * 去掉元素开头的分节标题，保留标题后面的正文。
     *
     * @param element 带标题的元素
     * @param head    已识别的标题
     * @return 去掉标题后的副本
     */
    private static Element withoutHead(Element element, String head) {
        Element copy = element.clone();
        Element first = copy.children().first();
        if (first != null && ("strong".equals(first.tagName()) || "b".equals(first.tagName()))
                && head.equals(matchHead(first.text()))) {
            first.remove();
            if (!copy.childNodes().isEmpty() && "br".equals(copy.childNodes().get(0).nodeName())) {
                copy.childNodes().get(0).remove();
            }
            return copy;
        }
        for (Node child : new ArrayList<>(copy.childNodes())) {
            if (child instanceof TextNode textNode) {
                String raw = textNode.getWholeText();
                int strip = headPrefixLength(raw, head);
                if (strip > 0) {
                    textNode.text(raw.substring(strip).replaceFirst("^[\\s\\u3000\\u00a0：:]+", ""));
                }
                break;
            }
        }
        return copy;
    }

    /**
     * 纯文本标题在正文里的结束位置。
     *
     * <p>标题可能是同义写法（{@code 释文}）或带编号的写法（{@code 其一译文}），
     * 所以不能拿规范标题做字面替换；这里从最长前缀往下试，第一个能被
     * {@link #matchHead(String)} 认成同一节的长度就是标题长度。</p>
     *
     * @param raw  文本节点的原文
     * @param head 规范标题
     * @return 标题结束下标（不含前导空白），未命中返回 -1
     */
    private static int headPrefixLength(String raw, String head) {
        int start = 0;
        while (start < raw.length() && (Character.isWhitespace(raw.charAt(start))
                || raw.charAt(start) == '\u3000' || raw.charAt(start) == '\u00a0')) {
            start++;
        }
        int limit = Math.min(raw.length(), start + 16);
        for (int end = limit; end > start; end--) {
            if (head.equals(matchHead(raw.substring(start, end)))) {
                return end;
            }
        }
        return -1;
    }

    /**
     * 标题规范化：去空白、去编号后与分节标题词比对。
     *
     * <p>先按原样查同义词表（{@code 注解 → 注释}、{@code 评析 → 赏析}），
     * 再查规范词表；都不中就剥掉首/尾编号（{@code 其一译文}、{@code 译文二}）重新比对。
     * {@code 译文及注释} 这类复合标题剥不出规范词，按普通正文处理，不会被误当成分节标题。</p>
     *
     * @param text 原文
     * @return 规范标题，未命中返回 null
     */
    private static String matchHead(String text) {
        if (text == null) {
            return null;
        }
        String compacted = text.replaceAll("[\\s\\u3000\\u00a0：:]+", "");
        if (compacted.isEmpty()) {
            return null;
        }
        String head = canonicalHead(compacted);
        if (head != null) {
            return head;
        }
        String bare = HEAD_NUMBER_PREFIX.matcher(compacted).replaceFirst("");
        head = canonicalHead(bare);
        if (head != null) {
            return head;
        }
        return canonicalHead(HEAD_NUMBER_SUFFIX.matcher(compacted).replaceFirst(""));
    }

    /**
     * 同义词与规范词比对。
     *
     * @param word 已去空白的标题词
     * @return 规范标题，未命中返回 null
     */
    private static String canonicalHead(String word) {
        if (word == null || word.isEmpty()) {
            return null;
        }
        String alias = HEAD_ALIASES.get(word);
        if (alias != null) {
            return alias;
        }
        return SECTION_HEADS.contains(word) ? word : null;
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
     * 递归拼接块文本，{@code <br>} 的边界写成换行，块级标签的边界写成空格。
     *
     * @param node    当前节点
     * @param builder 结果缓冲
     */
    private static void appendBlockText(Node node, StringBuilder builder) {
        if (node instanceof TextNode textNode) {
            builder.append(textNode.getWholeText());
            return;
        }
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode textNode) {
                builder.append(textNode.getWholeText());
                continue;
            }
            if (!(child instanceof Element element)) {
                continue;
            }
            String tag = element.tagName();
            if ("br".equals(tag)) {
                builder.append('\n');
                continue;
            }
            if ("script".equals(tag) || "style".equals(tag)) {
                continue;
            }
            boolean block = BLOCK_TAGS.contains(tag);
            if (block) {
                builder.append(' ');
            }
            appendBlockText(element, builder);
            if (block) {
                builder.append(' ');
            }
        }
    }

    /**
     * 归一化块文本：统一换行符、把 ASCII 空格压成单空格、去掉行首行尾空白与空行。
     *
     * <p>{@code U+3000} 段首缩进必须留下 —— 站点用它表示段落缩进，
     * 库内历史数据也是这个口径，不能被当成空白清掉。</p>
     *
     * @param raw 原始文本
     * @return 归一化文本
     */
    private static String normalizeBlockText(String raw) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\u00a0', ' ')
                // 块级边界折出来的空格紧挨段首缩进时要丢掉，否则会变成「： 　　我平素」
                // 这种半角空格 + 全角缩进混排，与站点原文的「：　　我平素」不一致。
                .replaceAll("[ \\t]+", " ").replace(" \u3000", "\u3000");
        StringBuilder builder = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            String trimmed = line.replaceAll("^[ \\t]+", "").replaceAll("[ \\t]+$", "");
            if (trimmed.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(trimmed);
        }
        return builder.toString();
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
