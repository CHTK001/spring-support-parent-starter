package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * PROCESSOR 节点执行器。
 * 负责数据清洗和转换，输入 RawRecord/ProcessedRecord，输出 ProcessedRecord。
 */
@Slf4j
@Spi("PROCESSOR")
public class ProcessorNodeExecutor implements SpiderNodeExecutor {

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        Map<String, Object> record = toMap(context.inputData());

        // 字段映射规则：config["fieldMappings"] = [{"from":"src","to":"dst","transform":"trim|upper|lower"}]
        Object mappings = config.get("fieldMappings");
        Map<String, Object> processed = new HashMap<>(record);

        if (mappings instanceof Iterable<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    String from = str(m, "from", "");
                    String to = str(m, "to", from);
                    String transform = str(m, "transform", "");
                    if (!from.isBlank() && record.containsKey(from)) {
                        Object val = record.get(from);
                        processed.put(to, applyTransform(val, transform));
                    }
                }
            }
        }

        log.debug("[PROCESSOR] nodeId={} processed {} fields", node.getNodeId(), processed.size());
        return processed;
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.PROCESSOR; }

    private Object applyTransform(Object val, String transform) {
        if (val == null || transform.isBlank()) return val;
        String s = val.toString();
        return switch (transform.toLowerCase()) {
            case "trim"  -> s.trim();
            case "upper" -> s.toUpperCase();
            case "lower" -> s.toLowerCase();
            default      -> val;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object input) {
        if (input instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return new HashMap<>();
    }

    private String str(Map<?, ?> c, String k, String d) {
        Object v = c.get(k); return (v != null && !v.toString().isBlank()) ? v.toString() : d;
    }
}
