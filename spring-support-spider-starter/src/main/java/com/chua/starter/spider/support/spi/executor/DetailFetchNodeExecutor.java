package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * DETAIL_FETCH 节点执行器。
 * 对列表中每条记录发起详情请求，输入 RawRecord，输出富化后的 RawRecord。
 */
@Slf4j
@Spi("DETAIL_FETCH")
public class DetailFetchNodeExecutor implements SpiderNodeExecutor {

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String urlField = str(config, "urlField", "detailUrl");
        String resultField = str(config, "resultField", "detailHtml");

        Map<String, Object> record = toMap(context.inputData());
        Object rawUrl = record.get(urlField);
        if (rawUrl == null || rawUrl.toString().isBlank()) {
            log.warn("[DETAIL_FETCH] nodeId={} 记录中无 urlField={}", node.getNodeId(), urlField);
            return record;
        }

        // 实际 HTTP 请求由 SpiderToolkit 完成；此处标记待抓取
        Map<String, Object> enriched = new HashMap<>(record);
        enriched.put("_detailFetchUrl", rawUrl.toString());
        enriched.put("_detailFetchField", resultField);
        log.debug("[DETAIL_FETCH] nodeId={} 标记详情抓取 url={}", node.getNodeId(), rawUrl);
        return enriched;
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.DETAIL_FETCH; }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object input) {
        if (input instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return new HashMap<>();
    }

    private String str(Map<String, Object> c, String k, String d) {
        Object v = c.get(k); return (v != null && !v.toString().isBlank()) ? v.toString() : d;
    }
}
