// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.engine.executor;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.crawler.support.SpiderFetcher;
import com.chua.crawler.support.fetcher.HttpFetcher;
import com.chua.crawler.support.model.SpiderProxyConfig;
import com.chua.crawler.support.model.SpiderRequest;
import com.chua.crawler.support.model.SpiderResponse;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.support.CollectJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抓取节点：调用 utils-support-crawler-starter 的抓取器取回一页内容。
 *
 * <p>抓取器通过 SPI 按名字选取（默认 {@code http}，浏览器渲染等实现可零侵入注册进来），
 * 解析结果不可用时回退到内置 HTTP 抓取器，保证只依赖爬虫模块也能跑。</p>
 *
 * <p>出参写成本令牌作用域的普通值而不是响应对象：{@code resp} 是含 status/html/url 等键的映射，
 * {@code respHtml} 是正文 HTML，{@code respDoc} 是已绑定 base 的 jsoup 文档，内容像 JSON 时再给出 {@code respJson}。
 * 这样抽取节点的 CSS、下游节点的 {@code ${resp.status}} 都不需要感知抓取实现。</p>
 *
 * <p>HTTP 4xx/5xx 与传输失败都算本令牌失败，异常文本写入 {@code ex}，
 * 由连线上的异常流向决定是走异常分支还是中断该分支。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectRequestExecutor implements CollectShapeExecutor {

    /**
     * 目标 URL 参数名。
     */
    public static final String URL = "url";

    /**
     * 请求方法参数名。
     */
    public static final String METHOD = "method";

    /**
     * 请求体参数名。
     */
    public static final String BODY = "body";

    /**
     * 请求内容类型参数名。
     */
    public static final String CONTENT_TYPE = "contentType";

    /**
     * 超时毫秒参数名。
     */
    public static final String TIMEOUT = "timeout";

    /**
     * 重试次数参数名。
     */
    public static final String RETRY = "retry";

    /**
     * 重试间隔毫秒参数名。
     */
    public static final String RETRY_INTERVAL = "retryInterval";

    /**
     * 抓取器 SPI 名称参数名。
     */
    public static final String FETCHER = "fetcher";

    /**
     * 代理参数名。
     */
    public static final String PROXY = "proxy";

    /**
     * 用户-Agent 参数名。
     */
    public static final String USER_AGENT = "userAgent";

    /**
     * 请求头参数组名。
     */
    public static final String HEADER = "header";

    /**
     * Cookie 参数组名。
     */
    public static final String COOKIE = "cookie";

    /**
     * 出参变量名参数名。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 是否解析并保留 jsoup 文档对象参数名，关闭后下游 CSS 抽取不可用。
     */
    public static final String SAVE_DOCUMENT = "saveDocument";

    /**
     * 默认出参变量名。
     */
    public static final String DEFAULT_OUTPUT = "resp";

    /**
     * JDK HTTP 客户端禁止业务代码设置的请求头，命中即丢弃并告警。
     */
    private static final Set<String> RESTRICTED_HEADERS =
            Set.of("connection", "content-length", "expect", "host", "upgrade");

    /**
     * 抓取器缓存，SPI 名字到实例。抓取器持有连接池，按名字复用。
     */
    private final Map<String, SpiderFetcher> fetchers = new ConcurrentHashMap<>();

    /**
     * 平台配置。
     */
    private final CollectProperties properties;

    @Override
    public String supportShape() {
        return "request";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "抓取", "来源", "globe",
                "请求网页或接口，产出 resp/respHtml/respDoc 供下游抽取");
        meta.setOutputVariable("resp 为响应映射，respHtml 为正文 HTML，respDoc 为文档对象，可在出参变量名里改");
        CollectParamMeta headers = CollectParamMeta.of(HEADER + ":名称", "请求头", "pairs");
        headers.setTip("每行一条，存成参数 header:名称；Host、Content-Length 等受限头会被丢弃");
        CollectParamMeta cookies = CollectParamMeta.of(COOKIE + ":名称", "Cookie", "pairs");
        cookies.setTip("每行一条，存成参数 cookie:名称；与流程全局 Cookie 合并为一个 Cookie 头发送");
        CollectParamMeta outputName = CollectShapeMetas.option(OUTPUT, "输出名", DEFAULT_OUTPUT,
                "响应映射写入该变量，HTML、文档对象与 JSON 分别为其后缀 Html、Doc、Json", DEFAULT_OUTPUT);
        outputName.setRequired(true);
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.required(URL, "URL", "text", "支持 ${}，例：${item} 或 https://x.com/list?p=${index}"),
                CollectShapeMetas.option(METHOD, "请求方法", "GET", "留空按 GET 发送",
                        "GET", "POST", "PUT", "DELETE"),
                CollectParamMeta.of(BODY, "请求体", "textarea"),
                CollectParamMeta.of(CONTENT_TYPE, "请求体类型", "text"),
                CollectShapeMetas.text(TIMEOUT, "超时毫秒", "number",
                        "留空用全局配置 plugin.collect.request-timeout-ms"),
                CollectShapeMetas.text(RETRY, "重试次数", "number",
                        "留空用全局配置 plugin.collect.request-retry-times"),
                CollectParamMeta.of(RETRY_INTERVAL, "重试间隔毫秒", "number"),
                CollectShapeMetas.text(PROXY, "代理", "text",
                        "形如 127.0.0.1:7890 或 socks5://user:pwd@127.0.0.1:7890，留空直连"),
                CollectParamMeta.of(USER_AGENT, "User-Agent", "text"),
                CollectShapeMetas.text(FETCHER, "抓取器", "text",
                        "SPI 名称，默认 http；注册了浏览器渲染抓取器后填其名称"),
                outputName,
                CollectShapeMetas.option(SAVE_DOCUMENT, "保留文档对象", "1",
                        "选 0 时不解析 jsoup 文档，下游 CSS 抽取与链接节点不可用", "1", "0"),
                headers, cookies);
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        String url = CollectExpression.evalText(node.param(URL), variables).trim();
        if (url.isEmpty()) {
            throw new IllegalStateException("抓取节点的 URL 为空");
        }
        SpiderRequest request = buildRequest(node, context, variables, url);
        SpiderFetcher fetcher = resolveFetcher(node.param(FETCHER));
        int retry = Math.max(0, node.intParam(RETRY, properties.getRequestRetryTimes()));
        long interval = Math.max(0L, node.longParam(RETRY_INTERVAL, 1000L));
        SpiderResponse response = null;
        String failure = null;
        for (int attempt = 0; attempt <= retry; attempt++) {
            if (attempt > 0) {
                Thread.sleep(interval);
            }
            context.countRequest();
            response = fetcher.fetch(request);
            failure = describeFailure(request, response);
            if (failure == null) {
                break;
            }
            log.warn("[collect] 抓取失败准备重试 url={} attempt={} err={}", url, attempt + 1, failure);
        }
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
        putResponse(node, context, variables, request, response);
    }

    /**
     * 组装抓取请求：全局头/Cookie 打底，节点声明覆盖，再叠加代理与超时。
     *
     * @param node      节点
     * @param context   执行上下文
     * @param variables 令牌作用域
     * @param url       已求值的 URL
     * @return 抓取请求
     */
    private SpiderRequest buildRequest(CollectNode node, CollectContext context,
                                       Map<String, Object> variables, String url) {
        Map<String, String> headers = new LinkedHashMap<>();
        applyPairs(headers, context.getGlobalHeaders(), variables);
        applyPairs(headers, node.pairsOf(HEADER), variables);
        StringBuilder cookie = new StringBuilder();
        appendPairs(cookie, context.getGlobalCookies(), variables);
        appendPairs(cookie, node.pairsOf(COOKIE), variables);
        String method = node.param(METHOD).isEmpty() ? "GET" : node.param(METHOD).toUpperCase(Locale.ROOT);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("timeoutMs", node.intParam(TIMEOUT, properties.getRequestTimeoutMs()));
        String userAgent = CollectExpression.evalText(node.param(USER_AGENT), variables).trim();
        attributes.put("userAgent", userAgent.isEmpty() ? properties.getUserAgent() : userAgent);
        String body = CollectExpression.evalText(node.param(BODY), variables);
        if (!body.isBlank()) {
            String contentType = node.param(CONTENT_TYPE);
            if (!contentType.isBlank()) {
                headers.put("Content-Type", contentType);
            } else if (!headers.containsKey("Content-Type")) {
                headers.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            }
        }
        return SpiderRequest.builder()
                .url(url)
                .method(method)
                .body(body.isBlank() ? null : body)
                .headers(headers)
                .cookies(cookie.length() == 0 ? null : cookie.toString())
                .attributes(attributes)
                .proxy(parseProxy(CollectExpression.evalText(node.param(PROXY), variables).trim()))
                .build();
    }

    /**
     * 把键值对参数求值后写入目标映射，受限请求头直接丢弃。
     *
     * @param target    目标映射
     * @param pairs     键值对
     * @param variables 令牌作用域
     */
    private void applyPairs(Map<String, String> target, List<CollectParamDTO> pairs, Map<String, Object> variables) {
        for (CollectParamDTO pair : pairs) {
            String name = pair.name();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (RESTRICTED_HEADERS.contains(name.trim().toLowerCase(Locale.ROOT))) {
                log.warn("[collect] 请求头 {} 由 HTTP 客户端管理，已忽略", name);
                continue;
            }
            target.put(name.trim(), CollectExpression.evalText(pair.value(), variables));
        }
    }

    /**
     * 拼接 Cookie 头。
     *
     * @param builder   目标缓冲
     * @param pairs     键值对
     * @param variables 令牌作用域
     */
    private void appendPairs(StringBuilder builder, List<CollectParamDTO> pairs, Map<String, Object> variables) {
        for (CollectParamDTO pair : pairs) {
            if (pair.name() == null || pair.name().isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(pair.name().trim()).append('=')
                    .append(CollectExpression.evalText(pair.value(), variables));
        }
    }

    /**
     * 解析代理串，支持 host:port 与 scheme://[user:pass@]host:port。
     *
     * @param text 代理串
     * @return 代理配置，留空返回 null
     */
    private SpiderProxyConfig parseProxy(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String rest = text.trim();
        String scheme = "HTTP";
        int schemeAt = rest.indexOf("://");
        if (schemeAt > 0) {
            scheme = rest.substring(0, schemeAt).toUpperCase(Locale.ROOT);
            rest = rest.substring(schemeAt + 3);
        }
        String userInfo = null;
        int at = rest.lastIndexOf('@');
        if (at > 0) {
            userInfo = rest.substring(0, at);
            rest = rest.substring(at + 1);
        }
        int colon = rest.lastIndexOf(':');
        if (colon <= 0 || colon == rest.length() - 1) {
            throw new IllegalStateException("代理格式应为 [scheme://][user:pass@]host:port，当前为 " + text);
        }
        SpiderProxyConfig.SpiderProxyConfigBuilder builder = SpiderProxyConfig.builder()
                .proxyHost(rest.substring(0, colon).trim())
                .proxyPort(Integer.parseInt(rest.substring(colon + 1).trim()))
                .proxyProtocol(scheme);
        if (userInfo != null) {
            int split = userInfo.indexOf(':');
            if (split < 0) {
                builder.proxyUsername(userInfo);
            } else {
                builder.proxyUsername(userInfo.substring(0, split)).proxyPassword(userInfo.substring(split + 1));
            }
        }
        return builder.build();
    }

    /**
     * 取抓取器，优先 SPI 具名实现，缺失时回退内置 HTTP 抓取器。
     *
     * @param name SPI 名称，空则用 http
     * @return 抓取器实例
     */
    private SpiderFetcher resolveFetcher(String name) {
        String key = name == null || name.isBlank() ? "http" : name.trim();
        SpiderFetcher cached = fetchers.get(key);
        if (cached != null) {
            return cached;
        }
        SpiderFetcher fetcher = ServiceProvider.of(SpiderFetcher.class).getNewExtension(key);
        if (fetcher == null) {
            log.warn("[collect] 未找到抓取器 SPI {}，回退内置 http", key);
            fetcher = new HttpFetcher(properties.getRequestTimeoutMs());
        }
        fetchers.put(key, fetcher);
        return fetcher;
    }

    /**
     * 判定一次抓取是否失败。
     *
     * @param request  请求
     * @param response 响应
     * @return 失败说明，成功返回 null
     */
    private String describeFailure(SpiderRequest request, SpiderResponse response) {
        if (response == null) {
            return "抓取器无响应: " + request.getUrl();
        }
        if (response.getError() != null && !response.getError().isBlank()) {
            return response.getError();
        }
        int status = response.getStatusCode();
        if (status >= 400) {
            return "HTTP " + status + " " + request.getUrl();
        }
        return null;
    }

    /**
     * 把响应写成下游可直接引用的变量。
     *
     * @param node      节点
     * @param context   执行上下文
     * @param variables 令牌作用域
     * @param request   请求
     * @param response  响应
     */
    private void putResponse(CollectNode node, CollectContext context, Map<String, Object> variables,
                             SpiderRequest request, SpiderResponse response) {
        String output = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
        String html = response.getContent() == null ? "" : response.getContent();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("url", request.getUrl());
        resp.put("status", response.getStatusCode());
        resp.put("html", html);
        resp.put("contentType", response.getContentType() == null ? "" : response.getContentType());
        resp.put("length", (long) html.length());
        resp.put("costMs", response.getFetchTimeMs());
        variables.put(output, resp);
        variables.put(output + "Html", html);
        variables.put("url", request.getUrl());
        String trimmed = html.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            Object json = CollectJson.parseAny(trimmed);
            if (json != null) {
                variables.put(output + "Json", json);
            }
        }
        if (!"0".equals(node.param(SAVE_DOCUMENT))) {
            Document document = Jsoup.parse(html, request.getUrl());
            variables.put(output + "Doc", document);
            context.getSink().trace("抓取 " + request.getUrl() + " → " + response.getStatusCode()
                    + " (" + html.length() + " 字符)");
        }
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        Object output = variables.get(node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim());
        if (!(output instanceof Map)) {
            return null;
        }
        Map<?, ?> resp = (Map<?, ?>) output;
        return "HTTP " + resp.get("status") + " " + resp.get("length") + " 字符";
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return DEFAULT_OUTPUT;
    }
}