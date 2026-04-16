package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * DATA_EXTRACTOR 节点执行器。
 * 负责 XPath/CSS/AI 选择器解析，输入 RawDocument，输出 RawRecord。
 */
@Slf4j
@Spi("DATA_EXTRACTOR")
public class DataExtractorNodeExecutor implements SpiderNodeExecutor {

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String selectorType = str(config, "selectorType", "css");
        String selector = str(config, "selector", "");
        String html = extractHtml(context.inputData());

        log.debug("[DATA_EXTRACTOR] nodeId={} selectorType={} selector={}", node.getNodeId(), selectorType, selector);

        // 构建 RawRecord 输出（实际提取由 SpiderToolkit 完成）
        Map<String, Object> record = new HashMap<>();
        record.put("selectorType", selectorType);
        record.put("selector", selector);
        record.put("sourceHtml", html.length() > 200 ? html.substring(0, 200) + "..." : html);
        record.put("extracted", Map.of()); // 实际由 SpiderToolkit 填充
        return record;
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.DATA_EXTRACTOR; }

    private String extractHtml(Object input) {
        if (input instanceof Map<?, ?> m) {
            Object html = m.get("html"); return html != null ? html.toString() : "";
        }
        return input != null ? input.toString() : "";
    }

    private String str(Map<String, Object> c, String k, String d) {
        Object v = c.get(k); return (v != null && !v.toString().isBlank()) ? v.toString() : d;
    }
}
