package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.CollectFunctions;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 链接节点：从当前页面抽出下一页或详情页地址。
 *
 * <p>产出有序去重的链接集合，下游节点把它填进循环集合参数即可逐条抓取，
 * 这是"列表页 → 详情页"两跳采集的标准接法。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class CollectLinkExecutor implements CollectShapeExecutor {

    /**
     * 来源变量参数名。
     */
    public static final String SOURCE = "source";

    /**
     * 选择器参数名。
     */
    public static final String SELECTOR = "selector";

    /**
     * 属性名参数名。
     */
    public static final String ATTRIBUTE = "attribute";

    /**
     * 过滤正则参数名。
     */
    public static final String PATTERN = "pattern";

    /**
     * 是否转绝对地址参数名。
     */
    public static final String ABSOLUTE = "absolute";

    /**
     * 最大链接数参数名。
     */
    public static final String LIMIT = "limit";

    /**
     * 出参变量名参数名。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 默认来源。
     */
    public static final String DEFAULT_SOURCE = "respDoc";

    /**
     * 默认选择器。
     */
    public static final String DEFAULT_SELECTOR = "a[href]";

    /**
     * 默认出参变量名。
     */
    public static final String DEFAULT_OUTPUT = "linkList";

    @Override
    public String supportShape() {
        return "link";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "链接", "解析", "link",
                "从页面抽出链接集合，供下游循环节点逐条抓取");
        meta.setOutputVariable("默认 data，旧流程留空时按 ${linkList} 兜底");
        CollectParamMeta source = CollectShapeMetas.text(SOURCE, "页面来源", "text",
                "默认 ${respDoc}，也可填 HTML 文本变量");
        source.setDefaultValue("${" + DEFAULT_SOURCE + "}");
        CollectParamMeta selector = CollectShapeMetas.text(SELECTOR, "选择器", "text",
                "默认 a[href]，抽图片可填 img[src]");
        selector.setDefaultValue(DEFAULT_SELECTOR);
        CollectParamMeta absolute = CollectShapeMetas.option(ABSOLUTE, "转绝对地址", "1",
                "选 1 时按当前页面地址补全相对链接", "1", "0");
        return CollectShapeMetas.with(meta,
                source,
                selector,
                CollectShapeMetas.text(ATTRIBUTE, "取值的属性", "text", "留空按 href → src → content 顺序取第一个非空属性"),
                CollectShapeMetas.text(PATTERN, "过滤正则", "text", "只保留整串匹配的链接，如 .*page=\\d+"),
                absolute,
                CollectShapeMetas.text(LIMIT, "最多条数", "number", "留空或 0 表示不限制"),
                CollectShapeMetas.outputVariable("链接集合写入的变量名，默认 data"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        String sourceExpr = node.param(SOURCE).isEmpty() ? "${" + DEFAULT_SOURCE + "}" : node.param(SOURCE);
        Object source = CollectExpression.eval(sourceExpr, variables);
        Element root = elementOf(source);
        if (root == null) {
            throw new IllegalStateException("链接节点的页面来源 " + sourceExpr + " 不是 HTML 文档");
        }
        String selector = node.param(SELECTOR).isEmpty() ? DEFAULT_SELECTOR : node.param(SELECTOR);
        Pattern filter = compile(node.param(PATTERN));
        boolean absolute = !"0".equals(node.param(ABSOLUTE));
        int limit = (int) Math.max(0, node.longParam(LIMIT, 0L));
        Set<String> links = new LinkedHashSet<>();
        Elements elements = root.select(selector);
        for (Element element : elements) {
            String attribute = attributeOf(element, node.param(ATTRIBUTE));
            if (attribute == null) {
                continue;
            }
            String link = absolute ? element.absUrl(attribute) : "";
            if (link == null || link.isBlank()) {
                link = element.attr(attribute);
            }
            link = link.trim();
            if (link.isEmpty() || (filter != null && !filter.matcher(link).matches())) {
                continue;
            }
            links.add(link);
            if (limit > 0 && links.size() >= limit) {
                break;
            }
        }
        List<String> result = new ArrayList<>(links);
        String output = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT);
        variables.put(output, result);
        if (!result.isEmpty()) {
            variables.put("link", result.get(0));
        }
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        String output = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT);
        Object value = variables.get(output);
        if (value instanceof Collection) {
            return "发现 " + ((Collection<?>) value).size() + " 个链接";
        }
        return null;
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return DEFAULT_OUTPUT;
    }

    /**
     * 来源转 jsoup 元素。
     *
     * @param source 来源值
     * @return 元素或文档，取不到返回 null
     */
    private Element elementOf(Object source) {
        if (source instanceof Element) {
            return (Element) source;
        }
        if (source == null || source instanceof Map || source instanceof Collection) {
            return null;
        }
        String html = CollectFunctions.stringify(source);
        return html.isBlank() ? null : Jsoup.parse(html);
    }

    /**
     * 解析元素上实际承载链接的属性名。
     *
     * @param element   元素
     * @param attribute 指定属性名，留空按常见属性顺序探测
     * @return 有值的属性名，全为空返回 null
     */
    private String attributeOf(Element element, String attribute) {
        if (attribute != null && !attribute.isBlank()) {
            String name = attribute.trim();
            return element.attr(name).isBlank() ? null : name;
        }
        for (String name : new String[]{"href", "src", "content", "data-url"}) {
            if (!element.attr(name).isBlank()) {
                return name;
            }
        }
        return null;
    }

    /**
     * 编译过滤正则。
     *
     * @param pattern 正则文本
     * @return 匹配模式，留空返回 null
     */
    private Pattern compile(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(pattern.trim());
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException("链接过滤正则写法错误: " + ex.getMessage(), ex);
        }
    }
}
