package com.chua.starter.spider.node;

import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeField;
import com.chua.common.support.task.flow.FlowNodeOption;
import com.chua.common.support.task.flow.FlowProps;
import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* 提取节点。
*
* <p>从指定数据源中按表达式提取字段，将结果写入上下文指定属性。
* 支持 json路径 / 正则 / CSS 选择器三种解析方式，表达式支持环境变量插值。</p>
*
* <p>节点配置说明：</p>
* <ul>
*   <li>{@code source} 数据来源，当前数据 {@code current} 或上下文属性名，默认 current</li>
*   <li>{@code parserType} 解析方式，jsonpath / regex / css，默认 jsonpath</li>
*   <li>{@code expression} 提取表达式（必填）</li>
*   <li>{@code saveAs} 结果写入的上下文属性名（必填）</li>
*   <li>{@code isList} 是否提取列表结果，开关，默认关闭</li>
* </ul>
*
* <p>执行后写入上下文属性 {@code saveAs} 指定的提取结果。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Component
public class SpiderExtractNode implements FlowNode {

    /**
    * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(SpiderExtractNode.class);

    /**
    * 数据来源：当前数据
     */
    private static final String SOURCE_CURRENT = "current";

    /**
    * 解析方式：json路径
     */
    private static final String PARSER_JSONPATH = "jsonpath";

    /**
    * 解析方式：正则
     */
    private static final String PARSER_REGEX = "regex";

    /**
    * 解析方式：CSS 选择器
     */
    private static final String PARSER_CSS = "css";

    /**
    * 环境变量解析器
     */
    private final SpiderEnvResolver envResolver;

    /**
    * 通过构造器注入环境变量解析器。
    *
    * @param envResolver 环境变量解析器
     */
    public SpiderExtractNode(SpiderEnvResolver envResolver) {
        this.envResolver = envResolver;
    }

    /**
    * 节点类型标识。
    *
    * @return 节点类型
     */
    @Override
    public String type() {
        return "spider.extract";
    }

    /**
    * 节点配置表单元信息。
    *
    * @return 表单元信息列表
     */
    @Override
    public List<FlowNodeField> configSchema() {
        List<FlowNodeField> schema = new ArrayList<>();
        schema.add(FlowNodeField.input("source", "数据来源"));
        schema.add(FlowNodeField.select("parserType", "解析方式", List.of(
                FlowNodeOption.of("JSONPath", PARSER_JSONPATH),
                FlowNodeOption.of("正则", PARSER_REGEX),
                FlowNodeOption.of("CSS 选择器", PARSER_CSS))));
        schema.add(FlowNodeField.input("expression", "提取表达式", true));
        schema.add(FlowNodeField.input("saveAs", "写入属性名", true));
        schema.add(FlowNodeField.bool("isList", "提取列表"));
        return schema;
    }

    /**
    * 执行提取节点。
    *
    * <p>根据配置从数据源取值，按指定解析方式提取结果并写入上下文属性。</p>
    *
    * @param context 当前流程上下文
     */
    @Override
    public void execute(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        String source = props.getString("source", SOURCE_CURRENT);
        String parserType = props.getString("parserType", PARSER_JSONPATH);
        String expression = envResolver.resolve(props.getString("expression"));
        if (expression == null || expression.isEmpty()) {
            /**
            * illegal参数异常。
            * @param 参数" 参数"
            * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("spider.extract 节点缺少 expression 参数");
        }
        String saveAs = props.getString("saveAs");
        if (saveAs == null || saveAs.isEmpty()) {
            /**
            * illegal参数异常。
            * @param 参数" 参数"
            * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("spider.extract 节点缺少 saveAs 参数");
        }
        boolean isList = Boolean.TRUE.equals(props.getBoolean("isList"));

        Object sourceData = resolveSource(source, context);
        Object result = extract(sourceData, parserType, expression, isList);
        context.setAttribute(saveAs, result);
        log.info("spider.extract 完成: {} 提取到 {}", saveAs, result);
    }

    /**
    * 解析数据来源。
    *
    * <p>{@code current} 取当前数据，其他取值按上下文属性读取。</p>
    *
    * @param source  数据来源标识
    * @param context 当前流程上下文
    * @return 数据来源值
     */
    private Object resolveSource(String source, FlowContext context) {
        if (SOURCE_CURRENT.equals(source)) {
            return context.getData();
        }
        return context.getAttribute(source);
    }

    /**
    * 按指定解析方式提取结果。
    *
    * @param sourceData 数据来源值
    * @param parserType 解析方式
    * @param expression 提取表达式
    * @param isList     是否提取列表
    * @return 提取结果
     */
    private Object extract(Object sourceData, String parserType,
                           String expression, boolean isList) {
        switch (parserType) {
            case PARSER_JSONPATH -> {
                return extractJsonPath(sourceData, expression);
            }
            case PARSER_REGEX -> {
                return extractRegex(sourceData, expression, isList);
            }
            case PARSER_CSS -> {
                return extractCss(sourceData, expression, isList);
            }
            default -> {
                /**
                * illegal参数异常。
                * @param parserType parser类型
                * @return illegal参数异常的结果
                 */
                throw new IllegalArgumentException("不支持的解析方式: " + parserType);
            }
        }
    }

    /**
    * 按 json路径 提取。
    *
    * @param sourceData 数据来源值
    * @param expression json路径 表达式
    * @return 提取结果，匹配失败返回 空
     */
    private Object extractJsonPath(Object sourceData, String expression) {
        if (sourceData == null) {
            return null;
        }
        try {
            if (sourceData instanceof String text) {
                return JsonPath.read(text, expression);
            }
            return JsonPath.read(sourceData, expression);
        } catch (Exception e) {
            log.warn("spider.extract JSONPath 提取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
    * 按正则提取。
    *
    * <p>优先提取第一个捕获组，无捕获组时提取完整匹配。</p>
    *
    * @param sourceData 数据来源值
    * @param expression 正则表达式
    * @param isList     是否提取全部匹配
    * @return 提取结果
     */
    private Object extractRegex(Object sourceData, String expression, boolean isList) {
        if (sourceData == null) {
            return null;
        }
        Pattern pattern = Pattern.compile(expression);
        Matcher matcher = pattern.matcher(String.valueOf(sourceData));
        if (isList) {
            List<String> result = new ArrayList<>();
            while (matcher.find()) {
                result.add(matcher.groupCount() > 0 ? matcher.group(1) : matcher.group());
            }
            return result;
        }
        if (matcher.find()) {
            return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
        }
        return null;
    }

    /**
    * 按 CSS 选择器提取（简化实现）。
    *
    * <p>仅支持标签名选择器，通过正则提取标签内容。</p>
    *
    * @param sourceData 数据来源值
    * @param expression CSS 选择器
    * @param isList     是否提取全部匹配
    * @return 提取结果
     */
    private Object extractCss(Object sourceData, String expression, boolean isList) {
        if (sourceData == null) {
            return null;
        }
        String tag = expression.replaceAll("[^a-zA-Z0-9_-]", "");
        String regex = "<" + tag + "[^>]*>(.*?)</" + tag + ">";
        return extractRegex(sourceData, regex, isList);
    }
}