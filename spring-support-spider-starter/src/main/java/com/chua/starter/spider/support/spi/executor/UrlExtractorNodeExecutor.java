package com.chua.starter.spider.support.spi.executor;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.spi.SpiderNodeExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL_EXTRACTOR 节点执行器。
 * 从 RawDocument 中提取待爬 URL，输出 UrlContext 列表。
 */
@Slf4j
@Spi("URL_EXTRACTOR")
public class UrlExtractorNodeExecutor implements SpiderNodeExecutor {

    private static final Pattern HREF_PATTERN = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    @Override
    public Object execute(SpiderFlowNode node, SpiderNodeExecutionContext context) throws Exception {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String html = extractHtml(context.inputData());
        String urlPattern = str(config, "urlPattern", "");
        String baseUrl = str(config, "baseUrl", "");

        List<String> urls = new ArrayList<>();
        if (!urlPattern.isBlank()) {
            Matcher m = Pattern.compile(urlPattern).matcher(html);
            while (m.find()) urls.add(m.group(m.groupCount() > 0 ? 1 : 0));
        } else {
            Matcher m = HREF_PATTERN.matcher(html);
            while (m.find()) {
                String href = m.group(1);
                if (!href.startsWith("javascript") && !href.startsWith("#")) urls.add(href);
            }
        }

        log.debug("[URL_EXTRACTOR] nodeId={} extracted {} urls", node.getNodeId(), urls.size());
        return Map.of("urls", urls, "baseUrl", baseUrl);
    }

    @Override
    public SpiderNodeType supportedType() { return SpiderNodeType.URL_EXTRACTOR; }

    private String extractHtml(Object input) {
        if (input instanceof Map<?, ?> m) {
            Object html = m.get("html");
            return html != null ? html.toString() : "";
        }
        return input != null ? input.toString() : "";
    }

    private String str(Map<String, Object> c, String k, String d) {
        Object v = c.get(k); return (v != null && !v.toString().isBlank()) ? v.toString() : d;
    }
}
