package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FILTER 节点执行器。
 * 负责脏数据过滤和去重，输入 RawRecord/ProcessedRecord，输出同类型数据（null 表示过滤掉）。
 */
@Slf4j
@Spi("FILTER")
public class FilterNodeExecutor implements SpiderNodeExecutor {

    /** 简单内存去重集合（生产环境应使用 DatabaseUrlStore） */
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        boolean dedup = Boolean.parseBoolean(str(config, "dedup", "true"));
        String dedupField = str(config, "dedupField", "url");
        String emptyCheck = str(config, "emptyCheck", "");

        Object input = context.inputData();
        if (input == null) return null;

        Map<?, ?> record = input instanceof Map<?, ?> m ? m : null;

        // 空值检查
        if (!emptyCheck.isBlank() && record != null) {
            Object val = record.get(emptyCheck);
            if (val == null || val.toString().isBlank()) {
                log.debug("[FILTER] nodeId={} 过滤空值记录 field={}", node.getNodeId(), emptyCheck);
                return null;
            }
        }

        // 去重
        if (dedup && record != null) {
            Object key = record.get(dedupField);
            if (key != null && !seen.add(key.toString())) {
                log.debug("[FILTER] nodeId={} 过滤重复记录 key={}", node.getNodeId(), key);
                return null;
            }
        }

        return input;
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.FILTER; }

    private String str(Map<String, Object> c, String k, String d) {
        Object v = c.get(k); return (v != null && !v.toString().isBlank()) ? v.toString() : d;
    }
}
