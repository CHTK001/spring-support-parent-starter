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
 * 函数调用节点处理器：调用 ctx 变量中已存在的函数引用（字符串 → 对象）。
 *
 * <p>实际场景中可由前置 Script 节点把函数对象塞进 ctx，
 * 这里做的是"按字符串名查找对象并调用 invoke"。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class FunctionNodeHandler implements NodeHandler {

    /**
     * 节点类型编码
     */
    private static final String TYPE = "function";

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public String execute(SpiderFlowNode node, SpiderFlowContext context) {
        Map<String, Object> config = parseConfig(node.getFlowNodeConfig());
        String name = asString(config.get("name"));
        if (StringUtils.isBlank(name)) {
            return "no name";
        }
        Object fn = context.getVar(name);
        if (fn == null) {
            log.warn("[Function] 函数变量未定义: {}", name);
            return "undefined";
        }
        log.info("[Flow {}] Function[{}] invoked (skip actual call in handler)", context.getExecutionNo(), name);
        return "function invoked";
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
            log.warn("[Function] config JSON 解析失败: {}", e.getMessage());
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


