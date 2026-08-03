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

import java.util.List;
import java.util.Map;

/**
 * 循环节点处理器：遍历列表变量，对每个元素执行"出边指向的节点"（直到回边）。
 *
 * <p>config JSON 结构：</p>
 * <pre>
 * {
 *   "source":     "items",       // 待遍历的列表变量
 *   "itemVar":    "item",        // 单个元素别名（默认 item）
 *   "indexVar":   "index",       // 索引别名（默认 index）
 *   "maxLoops":   1000           // 安全上限，防止死循环
 * }
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class LoopNodeHandler implements NodeHandler {

    /**
     * 节点类型编码
     */
    private static final String TYPE = "loop";

    /**
     * 默认单项变量名
     */
    private static final String DEFAULT_ITEM_VAR = "item";

    /**
     * 默认索引变量名
     */
    private static final String DEFAULT_INDEX_VAR = "index";

    /**
     * 默认最大循环次数
     */
    private static final int DEFAULT_MAX_LOOPS = 1000;

    /**
     * 循环单项变量 key（实际值存于 ctx 的临时变量）
     */
    public static final String LOOP_ITEM_KEY = "_loop_item";

    /**
     * 循环索引变量 key
     */
    public static final String LOOP_INDEX_KEY = "_loop_index";

    /**
     * 循环列表 key
     */
    public static final String LOOP_LIST_KEY = "_loop_list";

    /**
     * 当前循环下标 key
     */
    public static final String LOOP_POSITION_KEY = "_loop_position";

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(SpiderFlowNode node, SpiderFlowContext context) {
        Map<String, Object> config = parseConfig(node.getFlowNodeConfig());
        String source = asString(config.get("source"));
        String itemVar = SpiderFlowUtils.defaultIfEmpty(asString(config.get("itemVar")), DEFAULT_ITEM_VAR);
        String indexVar = SpiderFlowUtils.defaultIfEmpty(asString(config.get("indexVar")), DEFAULT_INDEX_VAR);
        int maxLoops = asInt(config.get("maxLoops"), DEFAULT_MAX_LOOPS);

        Object src = source == null ? null : context.getVar(source);
        if (!(src instanceof List<?> list)) {
            log.warn("[Flow {}] 循环源非列表: source={}", context.getExecutionNo(), source);
            return "not a list";
        }

        // 初始化循环状态（SpiderFlowExecutionService 会在边求值时读取这些 key）
        context.putVar(LOOP_LIST_KEY, list);
        context.putVar(LOOP_POSITION_KEY, -1);
        context.putVar(itemVar, null);
        context.putVar(indexVar, null);

        int size = Math.min(list.size(), maxLoops);
        log.info("[Flow {}] 进入循环: source={}, size={}, maxLoops={}",
                context.getExecutionNo(), source, list.size(), maxLoops);
        return "loop(" + size + ")";
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
            log.warn("[Loop] config JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Object 转 String。
     */
    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * Object 转 int（带默认）。
     */
    private int asInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}


