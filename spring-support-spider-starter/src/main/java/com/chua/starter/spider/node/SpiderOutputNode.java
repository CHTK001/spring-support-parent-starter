package com.chua.starter.spider.node;

import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeField;
import com.chua.common.support.task.flow.FlowProps;
import com.chua.starter.spider.entity.SpiderDataRecord;
import com.chua.starter.spider.service.SpiderDataRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据落库节点。
 *
 * <p>将提取的数据写入 {@code spider_data_record} 表。从上下文读取标题/内容/来源等
 * 标准字段，并将完整数据序列化到原始数据列，供前端查看。</p>
 *
 * <p>节点配置说明：</p>
 * <ul>
 *   <li>{@code source} 数据来源，当前数据 {@code current} 或上下文属性名，默认 current</li>
 *   <li>{@code titleField} 标题字段名，默认 title</li>
 *   <li>{@code contentField} 内容字段名，默认 content</li>
 *   <li>{@code urlField} 来源 URL 字段名，默认 source_url</li>
 *   <li>{@code save} 是否落库，开关，默认开启</li>
 * </ul>
 *
 * <p>执行后写入上下文属性：</p>
 * <ul>
 *   <li>{@code spider.output.recordId} 落库后的记录 ID</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Component
public class SpiderOutputNode implements FlowNode {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(SpiderOutputNode.class);

    /**
     * 来源数据：当前数据
     */
    private static final String SOURCE_CURRENT = "current";

    /**
      * 落库记录 标识 属性键
     */
    private static final String ATTR_RECORD_ID = "spider.output.recordId";

    /**
     * 默认标题字段名
     */
    private static final String DEFAULT_TITLE_FIELD = "title";

    /**
     * 默认内容字段名
     */
    private static final String DEFAULT_CONTENT_FIELD = "content";

    /**
     * 默认来源 URL 字段名
     */
    private static final String DEFAULT_URL_FIELD = "source_url";

    /**
     * 数据记录服务
     */
    private final SpiderDataRecordService dataRecordService;

    /**
     * 通过构造器注入数据记录服务。
     *
     * @param dataRecordService 数据记录服务
     */
    public SpiderOutputNode(SpiderDataRecordService dataRecordService) {
        this.dataRecordService = dataRecordService;
    }

    /**
     * 节点类型标识。
     *
     * @return 节点类型
     */
    @Override
    public String type() {
        return "spider.output";
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
        schema.add(FlowNodeField.input("titleField", "标题字段名"));
        schema.add(FlowNodeField.input("contentField", "内容字段名"));
        schema.add(FlowNodeField.input("urlField", "来源 URL 字段名"));
        schema.add(FlowNodeField.bool("save", "是否落库"));
        return schema;
    }

    /**
     * 执行落库节点。
     *
     * <p>从来源数据中取标准字段，构造 {@link SpiderDataRecord} 并写入数据库。
      * 写入成功后记录 标识 放入上下文属性。</p>
     *
     * @param context 当前流程上下文
     */
    @Override
    public void execute(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        String source = props.getString("source", SOURCE_CURRENT);
        boolean save = props.getBoolean("save") == null || Boolean.TRUE.equals(props.getBoolean("save"));
        if (!save) {
            context.setAttribute(ATTR_RECORD_ID, null);
            return;
        }
        String titleField = props.getString("titleField", DEFAULT_TITLE_FIELD);
        String contentField = props.getString("contentField", DEFAULT_CONTENT_FIELD);
        String urlField = props.getString("urlField", DEFAULT_URL_FIELD);

        Map<String, Object> sourceMap = resolveMap(source, context);
        SpiderDataRecord record = new SpiderDataRecord();
        record.setExecutionNo(context.getAttribute("executionNo"));
        record.setDefinitionCode(context.getAttribute("definitionCode"));
        record.setTitle(String.valueOf(sourceMap.getOrDefault(titleField, "")));
        record.setContent(String.valueOf(sourceMap.getOrDefault(contentField, "")));
        record.setSourceUrl(String.valueOf(sourceMap.getOrDefault(urlField, "")));
        record.setRawData(serialize(sourceMap));
        record.setStatus("SUCCESS");
        record.setRecordTime(LocalDateTime.now());
        dataRecordService.save(record);
        context.setAttribute(ATTR_RECORD_ID, record.getId());
        log.info("spider.output 落库成功: id={} title={}", record.getId(), record.getTitle());
    }

    /**
     * 解析来源数据为映射。
     *
     * @param source  来源标识
     * @param context 当前流程上下文
     * @return 来源映射，非映射类型时包一层 钥匙 为 价值观 的映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveMap(String source, FlowContext context) {
        Object value;
        if (SOURCE_CURRENT.equals(source)) {
            value = context.getData();
        } else {
            value = context.getAttribute(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (value != null) {
            result.put("value", value);
        }
        return result;
    }

    /**
     * 序列化来源数据为 JSON 字符串。
     *
     * <p>使用 common-starter 的 JSON 工具，失败时回退为字符串值。</p>
     *
     * @param sourceMap 来源映射
     * @return JSON 字符串
     */
    private String serialize(Map<String, Object> sourceMap) {
        try {
            return com.chua.common.support.lang.json.Json.toJson(sourceMap);
        } catch (Exception e) {
            log.warn("spider.output 序列化失败，回退为字符串: {}", e.getMessage());
            return String.valueOf(sourceMap);
        }
    }
}