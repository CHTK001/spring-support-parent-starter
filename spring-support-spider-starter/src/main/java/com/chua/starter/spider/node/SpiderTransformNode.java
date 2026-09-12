package com.chua.starter.spider.node;

import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeField;
import com.chua.common.support.task.flow.FlowNodeOption;
import com.chua.common.support.task.flow.FlowProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
* 转换节点。
*
* <p>对指定数据执行字符串/时间格式转换，将结果写入上下文指定属性。
* 支持大小写、日期格式（年月日/时分秒）、正则替换等能力。</p>
*
* <p>节点配置说明：</p>
* <ul>
*   <li>{@code field} 取值来源，当前数据 {@code current} 或上下文属性名，默认 current</li>
*   <li>{@code format} 转换格式，none/upper/lower/yMd/yMdHms/trim，默认 none</li>
*   <li>{@code regex} 正则表达式，配合 {@code replacement} 做替换（可选）</li>
*   <li>{@code replacement} 替换内容（可选）</li>
*   <li>{@code saveAs} 结果写入的上下文属性名（必填）</li>
* </ul>
*
* @since 4.0.0.42
* @author CH
 */
@Component
public class SpiderTransformNode implements FlowNode {

    /**
    * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(SpiderTransformNode.class);

    /**
    * 取值来源：当前数据
     */
    private static final String SOURCE_CURRENT = "current";

    /**
    * 格式：不转换
     */
    private static final String FORMAT_NONE = "none";

    /**
    * 格式：转大写
     */
    private static final String FORMAT_UPPER = "upper";

    /**
    * 格式：转小写
     */
    private static final String FORMAT_LOWER = "lower";

    /**
    * 格式：去首尾空白
     */
    private static final String FORMAT_TRIM = "trim";

    /**
    * 格式：日期转年月日
     */
    private static final String FORMAT_YMD = "yMd";

    /**
    * 格式：日期转年月日时分秒
     */
    private static final String FORMAT_YMD_HMS = "yMdHms";

    /**
    * 默认格式
     */
    private static final String DEFAULT_FORMAT = FORMAT_NONE;

    /**
    * 年月日格式
     */
    private static final DateTimeFormatter FORMATTER_YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
    * 年月日时分秒格式
     */
    private static final DateTimeFormatter FORMATTER_YMD_HMS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
    * 节点配置表单元信息。
    *
    * @return 表单元信息列表
     */
    @Override
    public List<FlowNodeField> configSchema() {
        List<FlowNodeField> schema = new ArrayList<>();
        schema.add(FlowNodeField.input("field", "取值来源"));
        schema.add(FlowNodeField.select("format", "转换格式", List.of(
                FlowNodeOption.of("不转换", FORMAT_NONE),
                FlowNodeOption.of("转大写", FORMAT_UPPER),
                FlowNodeOption.of("转小写", FORMAT_LOWER),
                FlowNodeOption.of("去空白", FORMAT_TRIM),
                FlowNodeOption.of("年月日", FORMAT_YMD),
                FlowNodeOption.of("年月日时分秒", FORMAT_YMD_HMS))));
        schema.add(FlowNodeField.input("regex", "正则表达式"));
        schema.add(FlowNodeField.input("replacement", "替换内容"));
        schema.add(FlowNodeField.input("saveAs", "写入属性名", true));
        return schema;
    }

    /**
    * 节点类型标识。
    *
    * @return 节点类型
     */
    @Override
    public String type() {
        return "spider.transform";
    }

    /**
    * 执行转换节点。
    *
    * @param context 当前流程上下文
     */
    @Override
    public void execute(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        String field = props.getString("field", SOURCE_CURRENT);
        String format = props.getString("format", DEFAULT_FORMAT);
        String regex = props.getString("regex");
        String replacement = props.getString("replacement");
        String saveAs = props.getString("saveAs");
        if (saveAs == null || saveAs.isEmpty()) {
            /**
            * illegal参数异常。
            * @param 参数" 参数"
            * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("spider.transform 节点缺少 saveAs 参数");
        }

        Object sourceValue = resolveValue(field, context);
        Object result = transform(sourceValue, format, regex, replacement);
        context.setAttribute(saveAs, result);
        log.info("spider.transform 完成: {} 转换后 {}", saveAs, result);
    }

    /**
    * 解析取值来源。
    *
    * @param field   取值来源标识
    * @param context 当前流程上下文
    * @return 来源值
     */
    private Object resolveValue(String field, FlowContext context) {
        if (SOURCE_CURRENT.equals(field)) {
            return context.getData();
        }
        return context.getAttribute(field);
    }

    /**
    * 执行转换逻辑。
    *
    * @param sourceValue 来源值
    * @param format      转换格式
    * @param regex       正则表达式
    * @param replacement 替换内容
    * @return 转换结果
     */
    private Object transform(Object sourceValue, String format,
                             String regex, String replacement) {
        if (sourceValue == null) {
            return null;
        }
        String text = String.valueOf(sourceValue);
        switch (format) {
            case FORMAT_UPPER -> text = text.toUpperCase();
            case FORMAT_LOWER -> text = text.toLowerCase();
            case FORMAT_TRIM -> text = text.trim();
            case FORMAT_YMD -> {
                return formatDateTime(sourceValue, FORMATTER_YMD);
            }
            case FORMAT_YMD_HMS -> {
                return formatDateTime(sourceValue, FORMATTER_YMD_HMS);
            }
            default -> {
                // 不转换
            }
        }
        if (regex != null && !regex.isEmpty()) {
            String actual = replacement != null ? replacement : "";
            text = text.replaceAll(regex, actual);
        }
        return text;
    }

    /**
    * 格式化日期时间值。
    *
    * @param sourceValue 来源值
    * @param formatter   日期时间格式器
    * @return 格式化后的字符串，无法解析时返回原始值
     */
    private Object formatDateTime(Object sourceValue, DateTimeFormatter formatter) {
        try {
            if (sourceValue instanceof LocalDateTime dateTime) {
                return dateTime.format(formatter);
            }
            if (sourceValue instanceof java.time.LocalDate date) {
                return date.format(formatter);
            }
            if (sourceValue instanceof Long epoch) {
                return java.time.Instant.ofEpochMilli(epoch)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime()
                        .format(formatter);
            }
            return String.valueOf(sourceValue);
        } catch (Exception e) {
            log.warn("spider.transform 日期格式化失败: {}", e.getMessage());
            return String.valueOf(sourceValue);
        }
    }
}