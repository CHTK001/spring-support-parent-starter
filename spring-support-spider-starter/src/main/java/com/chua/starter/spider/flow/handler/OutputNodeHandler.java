package com.chua.starter.spider.flow.handler;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.flow.NodeHandler;
import com.chua.starter.spider.flow.SpiderFlowContext;
import com.chua.starter.spider.flow.SpiderFlowUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 输出节点处理器：把指定变量写到日志（用于调试 / 流程变量透出）。
 *
 * <p>config JSON 结构：</p>
 * <pre>
 * {
 *   "source":   "items",     // 变量名
 *   "preview":  true          // 是否只打印摘要（默认 true）
 * }
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class OutputNodeHandler implements NodeHandler {

    /**
     * 节点类型编码
     */
    private static final String TYPE = "output";

    /**
     * 预览长度上限
     */
    private static final int PREVIEW_LIMIT = 200;

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public String execute(SpiderFlowNode node, SpiderFlowContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getFlowNodeConfig());
        String source = asString(config.get("source"));
        boolean preview = !Boolean.FALSE.equals(config.get("preview"));

        Object value = source == null ? null : context.getVar(source);
        String text = render(value, preview);

        log.info("[Flow {}] OUTPUT[{}] = {}",
                context.getExecutionNo(), source == null ? "(null)" : source, text);
        return "output(" + text.length() + " chars)";
    }

    /**
     * 把变量渲染成字符串（按需截断）。
     */
    private String render(Object value, boolean preview) {
        if (value == null) {
            return "null";
        }
        String text;
        try {
            text = objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            text = value.toString();
        }
        if (preview && text.length() > PREVIEW_LIMIT) {
            return text.substring(0, PREVIEW_LIMIT) + "...";
        }
        return text;
    }

    /**
     * 解析 config JSON。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[Output] config JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Object 转 String。
     */
    private String asString(Object o) {
        return o == null ? null : o.toString();
    }
}


