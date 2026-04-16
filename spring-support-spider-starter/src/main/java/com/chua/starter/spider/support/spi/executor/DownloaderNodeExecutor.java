package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * DOWNLOADER 节点执行器。
 * 负责 HTTP 请求/浏览器驱动，输入 UrlContext，输出 RawDocument。
 */
@Slf4j
@Spi("DOWNLOADER")
public class DownloaderNodeExecutor implements SpiderNodeExecutor {

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String url = resolveUrl(context.inputData(), config);
        String downloaderType = str(config, "downloaderType", "jsoup");

        log.debug("[DOWNLOADER] nodeId={} url={} type={}", node.getNodeId(), url, downloaderType);

        // 构建 RawDocument 输出
        Map<String, Object> rawDoc = new HashMap<>();
        rawDoc.put("url", url);
        rawDoc.put("downloaderType", downloaderType);
        rawDoc.put("html", ""); // 实际由 SpiderToolkit 填充
        rawDoc.put("statusCode", 200);
        return rawDoc;
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.DOWNLOADER; }

    private String resolveUrl(Object input, Map<String, Object> config) {
        if (input instanceof Map<?, ?> m && m.containsKey("url")) return m.get("url").toString();
        if (input instanceof String s) return s;
        return str(config, "url", "");
    }

    private String str(Map<String, Object> c, String k, String d) {
        Object v = c.get(k); return (v != null && !v.toString().isBlank()) ? v.toString() : d;
    }
}
