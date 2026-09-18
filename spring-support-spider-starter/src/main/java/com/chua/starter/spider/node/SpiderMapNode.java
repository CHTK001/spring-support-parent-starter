package com.chua.starter.spider.node;

import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeField;
import com.chua.common.support.task.flow.FlowProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* 字段映射节点。
*
* <p>按映射规则将来源数据字段重命名/复制到目标字段，支持默认值兜底。
* 常用于将抓取字段映射为数据记录的标准字段（标题、内容、来源等）。</p>
*
* <p>节点配置说明：</p>
* <ul>
*   <li>{@code source} 来源数据，当前数据 {@code current} 或上下文属性名，默认 current</li>
*   <li>{@code mappings} 映射规则列表，每项含 {@code from}/{@code to}/{@code defaultValue}</li>
*   <li>{@code saveAs} 结果写入的上下文属性名（可选，缺省时写入当前数据）</li>
* </ul>
*
* @since 4.0.0.42
* @author CH
 */
@Component
public class SpiderMapNode implements FlowNode {

    /**
    * 日志记录器
    */
    private static final Logger log = LoggerFactory.getLogger(SpiderMapNode.class);

    /**
    * 来源数据：当前数据
    */
    private static final String SOURCE_CURRENT = "current";

    /**
    * 节点类型标识。
    *
    * @return 节点类型
    */
    @Override
    public String type() {
        return "spider.map";
    }

    /**
    * 节点配置表单元信息。
    *
    * @return 表单元信息列表
    */
    @Override
    public List<FlowNodeField> configSchema() {
        List<FlowNodeField> schema = new ArrayList<>();
        schema.add(FlowNodeField.input("source", "来源数据"));
        schema.add(FlowNodeField.textarea("mappings", "映射规则",
                "[{\"from\":\"原字段\",\"to\":\"目标字段\",\"defaultValue\":\"默认值\"}]"));
        schema.add(FlowNodeField.input("saveAs", "写入属性名"));
        return schema;
    }

    /**
    * 执行映射节点。
    *
    * @param context 当前流程上下文
    */
    @Override
    public void execute(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        String source = props.getString("source", SOURCE_CURRENT);
        String saveAs = props.getString("saveAs");
        List<Map<String, Object>> mappings = parseMappings(props.get("mappings"));

        Object sourceValue = resolveSource(source, context);
        Map<String, Object> result = applyMappings(sourceValue, mappings);
        if (saveAs != null && !saveAs.isEmpty()) {
            context.setAttribute(saveAs, result);
        } else {
            context.setData(result);
        }
        log.info("spider.map 完成: 映射 {} 个字段", result.size());
    }

    /**
    * 解析映射规则。
    *
    * @param raw 原始映射配置
    * @return 映射规则列表
    */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseMappings(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> mapping = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        mapping.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(mapping);
                }
            }
        }
        return result;
    }

    /**
    * 解析来源数据。
    *
    * @param source  来源标识
    * @param context 当前流程上下文
    * @return 来源值
    */
    private Object resolveSource(String source, FlowContext context) {
        if (SOURCE_CURRENT.equals(source)) {
            return context.getData();
        }
        return context.getAttribute(source);
    }

    /**
    * 按映射规则转换来源数据。
    *
    * @param sourceValue 来源值
    * @param mappings    映射规则
    * @return 映射结果
    */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyMappings(Object sourceValue, List<Map<String, Object>> mappings) {
        Map<String, Object> sourceMap = new LinkedHashMap<>();
        if (sourceValue instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sourceMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> mapping : mappings) {
            String from = String.valueOf(mapping.get("from"));
            String to = String.valueOf(mapping.get("to"));
            Object value = sourceMap.get(from);
            if (value == null) {
                value = mapping.get("defaultValue");
            }
            result.put(to, value);
        }
        return result;
    }
}
