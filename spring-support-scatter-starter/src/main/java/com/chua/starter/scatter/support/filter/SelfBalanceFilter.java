package com.chua.starter.scatter.support.filter;

import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientBuilder;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.scatter.Scatter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ArrayUtils;

/**
 * self-balance Filter：本节点收到请求时，按负载均衡从 scatter 服务池选中一个 HTTP 实例；
 * 选中其它节点则用 utils {@link HttpClientFactory} 反向代理转发，选中自己（或池内仅自己）则本地放行。
 *
 * <p>跳过条件（不转发，直接本地处理）：</p>
 * <ul>
 *   <li>请求带防循环标记头 {@link #FORWARDED_MARKER_HEADER}（来自对端节点转发）</li>
 *   <li>WebSocket 升级请求（{@code Upgrade} 头或 {@code Connection: Upgrade}）</li>
 *   <li>SSE 请求（{@code Accept} 含 {@code text/event-stream}）</li>
 *   <li>异步请求已开始（{@code isAsyncStarted()}）</li>
 *   <li>multipart 上传，或请求体超过 {@code maxForwardBodyBytes}</li>
 * </ul>
 *
 * <p>转发语义：剥离逐跳头（Host / Connection / Content-Length / Transfer-Encoding 等），
 * 追加 {@code X-Forwarded-For} / {@code X-Forwarded-Proto} / {@code X-Forwarded-Host} 与防循环标记头，
 * 请求体字节透传，响应状态码、响应头（同样剥离逐跳头）与响应体按同口径回写。
 * 转发异常且响应未提交时本地放行兜底。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public class SelfBalanceFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SelfBalanceFilter.class);

    /**
     * 防循环转发标记头：携带该头的请求说明来自对端 scatter 节点转发，本地直接处理，不再二次选路。
     */
    public static final String FORWARDED_MARKER_HEADER = "X-Scatter-Forwarded";

    /**
     * 逐跳请求头：转发时从上游请求剥离。
     */
    private static final Set<String> HOP_BY_HOP_REQUEST_HEADERS = Set.of(
            "connection", "content-length", "transfer-encoding", "host", "keep-alive",
            "proxy-connection", "proxy-authorization", "proxy-authenticate", "te", "trailer", "upgrade");

    /**
     * 逐跳响应头：回写时从下游响应剥离（Content-Length 由本容器按实际回传字节重新设置）。
     */
    private static final Set<String> HOP_BY_HOP_RESPONSE_HEADERS = Set.of(
            "connection", "content-length", "transfer-encoding", "keep-alive",
            "proxy-authenticate", "te", "trailer", "upgrade");

    /**
     * scatter 聚合入口（已启动）。
     */
    private final Scatter scatter;

    /**
     * 服务分组（发现路径）。
     */
    private final String servicePath;

    /**
     * 负载均衡策略：weight / random / round。
     */
    private final String balanceStrategy;

    /**
     * 本节点业务端点 serverId（区别于组网 self 条目的 nodeId）。
     */
    private final String selfServerId;

    /**
     * 转发请求体大小上限（字节）；&le;0 不限制。
     */
    private final long maxForwardBodyBytes;

    /**
     * 构造方法。
     *
     * @param scatter              scatter 聚合入口
     * @param servicePath          服务分组（发现路径）
     * @param balanceStrategy      负载均衡策略
     * @param selfServerId         本节点业务端点 serverId
     * @param maxForwardBodyBytes  转发请求体大小上限（字节）
     */
    public SelfBalanceFilter(Scatter scatter, String servicePath, String balanceStrategy,
                             String selfServerId, long maxForwardBodyBytes) {
        this.scatter = scatter;
        this.servicePath = servicePath;
        this.balanceStrategy = balanceStrategy == null || balanceStrategy.isBlank() ? "weight" : balanceStrategy;
        this.selfServerId = selfServerId;
        this.maxForwardBodyBytes = maxForwardBodyBytes;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse resp)
                || !shouldForward(req)) {
            chain.doFilter(request, response);
            return;
        }
        Discovery target = selectTarget();
        if (target == null || selfServerId.equals(target.getServerId())) {
            // 池内没有其它实例，或选中自己 → 本地放行
            chain.doFilter(request, response);
            return;
        }
        if (!forward(req, resp, target)) {
            // 转发异常且响应尚未提交 → 本地放行兜底
            chain.doFilter(request, response);
        }
    }

    /**
     * 判断请求是否参与 self-balance 转发。
     *
     * @param request 当前请求
     * @return {@code true} 表示可转发
     */
    private boolean shouldForward(HttpServletRequest request) {
        if (request.getHeader(FORWARDED_MARKER_HEADER) != null) {
            return false;
        }
        String upgrade = request.getHeader("Upgrade");
        String connection = request.getHeader("Connection");
        if ((upgrade != null && !upgrade.isBlank())
                || (connection != null && connection.toLowerCase(Locale.ROOT).contains("upgrade"))) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            return false;
        }
        if (request.isAsyncStarted()) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return false;
        }
        if (maxForwardBodyBytes > 0 && request.getContentLengthLong() > maxForwardBodyBytes) {
            return false;
        }
        return true;
    }

    /**
     * 从 scatter 服务池按负载均衡选中一个 HTTP 实例（协议过滤掉 tcp/udp 组网条目）。
     *
     * @return 目标实例；选择失败或池为空返回 {@code null}
     */
    private Discovery selectTarget() {
        try {
            return scatter.discovery().getService(servicePath, balanceStrategy, "http");
        } catch (Exception e) {
            log.debug("[Scatter] 选择目标实例失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 反向代理转发请求并回写响应。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param target   目标实例
     * @return {@code true} 表示转发完成（含上游返回 4xx/5xx）；{@code false} 表示转发异常、可本地兜底
     * @throws IOException 读取请求体失败
     */
    private boolean forward(HttpServletRequest request, HttpServletResponse response, Discovery target)
            throws IOException {
        String pathAndQuery = buildPathAndQuery(request);
        HttpMethod method = resolveMethod(request.getMethod());
        byte[] body = readBody(request, method);

        HttpClientBuilder builder = HttpClientFactory.of("http://" + target.getHost() + ":" + target.getPort())
                .path(pathAndQuery)
                .method(method);
        copyRequestHeaders(request, builder);
        builder.header(FORWARDED_MARKER_HEADER, selfServerId);
        if (ArrayUtils.isNotEmpty(body)) {
            builder.body(body);
        }

        ClientResponse clientResponse;
        try {
            clientResponse = builder.execute();
        } catch (Exception e) {
            log.warn("[Scatter] 转发 {} {} -> {}:{} 失败: {}", request.getMethod(), pathAndQuery,
                    target.getHost(), target.getPort(), e.getMessage());
            return false;
        }

        response.setStatus(clientResponse.getStatusCode());
        copyResponseHeaders(clientResponse, response);
        byte[] respBody = clientResponse.getBody();
        if (ArrayUtils.isNotEmpty(respBody)) {
            response.setContentLength(respBody.length);
            response.getOutputStream().write(respBody);
            response.getOutputStream().flush();
        }
        log.debug("[Scatter] 已转发 {} {} -> {}:{}，上游状态 {}",
                request.getMethod(), pathAndQuery, target.getHost(), target.getPort(), clientResponse.getStatusCode());
        return true;
    }

    /**
     * 读取请求体字节（GET / HEAD / OPTIONS 无请求体）。
     *
     * @param request 当前请求
     * @param method  上游方法
     * @return 请求体字节；无请求体返回 {@code null}
     * @throws IOException 读取失败
     */
    private byte[] readBody(HttpServletRequest request, HttpMethod method) throws IOException {
        if (method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS) {
            return null;
        }
        if (maxForwardBodyBytes > 0 && request.getContentLengthLong() > maxForwardBodyBytes) {
            return null;
        }
        return request.getInputStream().readAllBytes();
    }

    /**
     * 拼接转发路径（含上下文路径与查询串）。
     *
     * @param request 当前请求
     * @return 形如 {@code /ctx/api/x? a=b} 的路径
     */
    private String buildPathAndQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return StringUtils.isEmpty(query) ? uri : uri + "?" + query;
    }

    /**
     * 解析上游 HTTP 方法。
     *
     * @param method 请求方法名
     * @return {@link HttpMethod}；未知方法回落 {@code GET}
     */
    private HttpMethod resolveMethod(String method) {
        if (method == null) {
            return HttpMethod.GET;
        }
        try {
            return HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.debug("[Scatter] 未知请求方法 {}，按 GET 转发", method);
            return HttpMethod.GET;
        }
    }

    /**
     * 复制请求头（剥离逐跳头）并追加 X-Forwarded-* 链路头。
     *
     * @param request 当前请求
     * @param builder 转发请求构建器
     */
    private void copyRequestHeaders(HttpServletRequest request, HttpClientBuilder builder) {
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (name == null || HOP_BY_HOP_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values != null && values.hasMoreElements()) {
                builder.header(name, values.nextElement());
            }
        }
        String remoteAddr = request.getRemoteAddr();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientIp = forwardedFor == null || forwardedFor.isBlank() ? remoteAddr : forwardedFor + ", " + remoteAddr;
        if (clientIp != null && !clientIp.isBlank()) {
            builder.header("X-Forwarded-For", clientIp);
        }
        if (request.getScheme() != null) {
            builder.header("X-Forwarded-Proto", request.getScheme());
        }
        String host = request.getHeader("Host");
        if (host != null && !host.isBlank()) {
            builder.header("X-Forwarded-Host", host);
        }
    }

    /**
     * 回写上游响应头（剥离逐跳头）。
     *
     * @param clientResponse 上游响应
     * @param response       下游响应
     */
    private void copyResponseHeaders(ClientResponse clientResponse, HttpServletResponse response) {
        Map<String, String> headers = clientResponse.getHeaders() == null
                ? Map.of() : clientResponse.getHeaders().toMap();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (name == null || HOP_BY_HOP_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            response.setHeader(name, entry.getValue());
        }
    }
}
