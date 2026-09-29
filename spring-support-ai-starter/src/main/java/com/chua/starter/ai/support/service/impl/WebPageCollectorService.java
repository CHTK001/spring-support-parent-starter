package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.model.WebPageContent;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 网页采集服务：按网址抓取网页并提取正文。
 *
 * <p>两件事必须做对，否则「采集」等于白做：</p>
 * <ol>
 *   <li><strong>SSRF 防护</strong>：只允许 http/https，且域名解析出的任一 IP 命中内网/回环/
 *       链路本地即拒绝。缺失这一步时，任何能调用采集接口的人都能让服务器去访问
 *       云元数据地址（169.254.169.254）和内网服务。</li>
 *   <li><strong>正文提取</strong>：去掉 script/style/nav/footer 等非正文节点并还原 HTML 实体。
 *       早前用裸正则 {@code replaceAll("<[^>]+>", " ")} 会把 JS 代码和实体当正文抽出来，
 *       直接污染向量库与关系抽取。</li>
 * </ol>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
public class WebPageCollectorService {

    /**
     * 允许的协议白名单。
     */
    private static final Set<String> SAFE_PROTOCOLS = Set.of("http", "https");

    /**
     * 单页正文长度上限，防止超长页面把向量库与 LLM 撑爆。
     */
    private static final int MAX_PAGE_CHARS = 60000;

    /**
     * 浏览器 UA，部分站点对空 UA 直接返回 403。
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    /**
     * 抓取单个网页。
     *
     * @param url 目标网址
     * @return 网页正文
     * @throws IllegalArgumentException 协议不允许或指向内网
     * @throws IllegalStateException    抓取失败或正文为空
     */
    public WebPageContent fetch(String url) {
        if (!isSafeUrl(url)) {
            throw new IllegalArgumentException("URL 不合法：仅允许 http/https 的公网地址");
        }
        ClientResponse response;
        try {
            response = HttpClientFactory.getClient().request(url, HttpMethod.GET)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.8")
                    .execute();
        } catch (Exception e) {
            throw new IllegalStateException("抓取失败：" + e.getMessage(), e);
        }
        if (response == null || !response.isSuccess()) {
            int code = response == null ? -1 : response.getStatusCode();
            throw new IllegalStateException("HTTP 抓取失败，状态码 " + code);
        }
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("响应内容为空");
        }
        return parse(url, body, response.getHeader("Content-Type"));
    }

    /**
     * 抓取入口页并按同域链接展开，返回去重后的页面列表。
     *
     * <p>广度优先：入口页先抓，再从它的同域链接里继续，每页都会再发现新链接，直到达到
     * 页数上限。只跟随同域，避免顺着外链爬到别的站点去。</p>
     *
     * @param entryUrl  入口网址
     * @param maxPages  最多抓多少页（含入口页），小于 1 时按 1 处理
     * @return 抓取成功的页面列表
     */
    public List<WebPageContent> fetchCrawl(String entryUrl, int maxPages) {
        int limit = Math.max(1, maxPages);
        List<WebPageContent> pages = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> failed = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        String host = hostOf(entryUrl);
        queue.add(entryUrl);
        while (!queue.isEmpty() && pages.size() < limit) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            try {
                WebPageContent page = fetch(current);
                pages.add(page);
                if (pages.size() >= limit) {
                    break;
                }
                if (page.links() != null) {
                    for (String link : page.links()) {
                        if (host.equals(hostOf(link)) && !visited.contains(link)) {
                            queue.add(link);
                        }
                    }
                }
            } catch (Exception e) {
                failed.add(current);
                log.debug("[WebPageCollector] 抓取失败 {}: {}", current, e.getMessage());
            }
        }
        if (!failed.isEmpty()) {
            log.info("[WebPageCollector] 采集完成：成功 {} 页，失败 {} 页", pages.size(), failed.size());
        }
        return pages;
    }

    /**
     * 校验网址是否允许抓取（协议白名单 + 内网地址拦截）。
     *
     * @param url 目标网址
     * @return 允许返回 true
     */
    public boolean isSafeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String protocol = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!SAFE_PROTOCOLS.contains(protocol)) {
                return false;
            }
            String host = uri.getHost();
            if (StringUtils.isBlank(host)) {
                return false;
            }
            // 域名可能解析到多个 IP，任一命中内网即拒绝，避免 DNS rebinding 绕过
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isInternalAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (URISyntaxException | java.net.UnknownHostException e) {
            return false;
        }
    }

    /**
     * 判断 IP 是否属于内网/回环/链路本地等不该被采集访问的地址。
     *
     * @param address IP 地址
     * @return 属于内网返回 true
     */
    private boolean isInternalAddress(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    /**
     * 取网址的主机名，用于同域判断。
     *
     * @param url 网址
     * @return 主机名，解析失败返回空串
     */
    private String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return "";
        }
    }

    /**
     * 解析响应体为网页正文。
     *
     * @param url         实际 URL
     * @param body        响应字节
     * @param contentType 响应 Content-Type，用于判断是否 HTML
     * @return 网页正文
     */
    private WebPageContent parse(String url, byte[] body, String contentType) {
        String html = decode(body, contentType);
        Document doc = Jsoup.parse(html, url);
        // 先摘掉非正文节点：脚本与样式里的字符会被当作正文抽出来，导航/页脚是全站噪音
        doc.select("script, style, noscript, iframe, svg, nav, header, footer, form, aside").remove();
        String title = doc.title();
        if (!StringUtils.hasText(title)) {
            Element h1 = doc.selectFirst("h1");
            title = h1 == null ? "" : h1.text();
        }
        // article/main 命中就用它，拿不到再退到 body
        Element content = doc.selectFirst("article");
        if (content == null) {
            content = doc.selectFirst("main");
        }
        if (content == null) {
            content = doc.body();
        }
        String text = collapseWhitespace(content == null ? "" : content.text());
        if (text.length() > MAX_PAGE_CHARS) {
            text = text.substring(0, MAX_PAGE_CHARS);
        }
        List<String> links = new ArrayList<>();
        for (Element anchor : doc.select("a[href]")) {
            String abs = anchor.absUrl("href");
            if (StringUtils.hasText(abs) && abs.startsWith("http")) {
                links.add(abs);
            }
        }
        return new WebPageContent(url, title.trim(), text, links);
    }

    /**
     * 按 Content-Type 里的 charset 解码，解码不出来时回退 UTF-8。
     *
     * <p>中文站点常见 GBK/GB2312，直接按 UTF-8 解会得到乱码并污染向量库。</p>
     *
     * @param body        响应字节
     * @param contentType 响应 Content-Type
     * @return 解码后的字符串
     */
    private String decode(byte[] body, String contentType) {
        Charset charset = StandardCharsets.UTF_8;
        if (StringUtils.hasText(contentType)) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf("charset=");
            if (idx > 0) {
                String name = lower.substring(idx + 8).trim();
                int end = name.indexOf(';');
                if (end > 0) {
                    name = name.substring(0, end);
                }
                name = name.replace("\"", "").replace("'", "").trim();
                try {
                    charset = Charset.forName(name);
                } catch (Exception ignore) {
                    charset = StandardCharsets.UTF_8;
                }
            }
        }
        return new String(body, charset);
    }

    /**
     * 压缩连续空白为单个空格并去掉首尾空白。
     *
     * <p>HTML 渲染成文本后每行都会带大量缩进与换行，不压缩会让分块与向量都被空白稀释。</p>
     *
     * @param text 原始文本
     * @return 压缩后的文本
     */
    private String collapseWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
