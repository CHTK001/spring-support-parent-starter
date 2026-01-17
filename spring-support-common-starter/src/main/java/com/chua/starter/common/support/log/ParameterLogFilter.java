package com.chua.starter.common.support.log;
import com.chua.common.support.core.constant.NumberConstant;
import com.chua.common.support.core.utils.CollectionUtils;
import com.chua.common.support.core.utils.IoUtils;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.common.support.logger.InterfaceLoggerInfo;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.chua.common.support.core.constant.NameConstant.*;
import static com.chua.common.support.network.http.HttpConstant.HTTP_HEADER_CONTENT_TYPE;
import static java.time.LocalDateTime.*;
import static java.time.format.DateTimeFormatter.*;

import lombok.extern.slf4j.Slf4j;

/**
 * 参数日志过滤器
 * <p>
 * 用于记录 HTTP 请求的详细信息，包括：
 * <ul>
 *     <li>请求方法和 URL</li>
 *     <li>请求参数（Query String / Request Body）</li>
 *     <li>自定义请求头（以 x- 开头）</li>
 *     <li>客户端 IP 地址</li>
 *     <li>请求耗时统计</li>
 * </ul>
 * </p>
 *
 * <h3>配置项：</h3>
 * <pre>
 * plugin:
 *   log:
 *     enable: true              # 是否开启日志过滤器
 *     open-interface-log: true  # 是否开启接口日志事件
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2023/09/08
 */
@Slf4j
public class ParameterLogFilter implements Filter {
        public static final DateTimeFormatter DATE_TIME_FORMATTER = ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final LogProperties logProperties;
    private final ApplicationContext applicationContext;
    private final ExecutorService interfaceServiceLog = Executors.newVirtualThreadPerTaskExecutor();
    
    /**
     * 请求开始时间的属性名
     */
    private static final String REQUEST_START_TIME = "REQUEST_START_TIME";

    public ParameterLogFilter(LogProperties logProperties, ApplicationContext applicationContext) {
        this.logProperties = logProperties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest request) 
                || !(servletResponse instanceof HttpServletResponse response)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        request.setAttribute(REQUEST_START_TIME, startTime);

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        
        String connection = requestWrapper.getHeader("upgrade");
        
        // WebSocket 请求跳过日志记录
        if ("websocket".equalsIgnoreCase(connection)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String requestURI = requestWrapper.getRequestURI();
        String method = requestWrapper.getMethod();
        String traceId = MDC.get("traceId");
        
        // 静态资源跳过详细日志
        if (isPass(requestURI)) {
            filterChain.doFilter(requestWrapper, responseWrapper);
            responseWrapper.copyBodyToResponse();
            injectInterfaceServiceLog(requestWrapper);
            printCostTime(method, requestURI, startTime, responseWrapper.getStatus());
            return;
        }

        String contentType = requestWrapper.getHeader(HTTP_HEADER_CONTENT_TYPE);
        StringBuilder logBuilder = new StringBuilder();
        
        // 构建请求头日志
        logBuilder.append("╔══════════════════════════════════════════════════════════════════════════════\n");
        logBuilder.append("║ [请求开始] TraceId: ").append(traceId).append("\n");
        logBuilder.append("║ 请求时间: ").append(now().format(DATE_TIME_FORMATTER)).append("\n");
        logBuilder.append("╠══════════════════════════════════════════════════════════════════════════════\n");
        
        try {
            // form-data 请求
            if (StringUtils.contains(contentType, "form-data")) {
                appendUrl(logBuilder, requestWrapper);
                appendHeader(logBuilder, requestWrapper);
                logBuilder.append("║ 请求类型: multipart/form-data (文件上传)\n");
                log.debug(logBuilder.toString());
                filterChain.doFilter(requestWrapper, responseWrapper);
                injectInterfaceServiceLog(requestWrapper);
                printResponse(responseWrapper, startTime);
                return;
            }

            // GET/DELETE 请求
            if (StringUtils.isEmpty(contentType) || GET.equals(method) || DELETE.equals(method)) {
                appendUrl(logBuilder, requestWrapper);
                appendQuery(logBuilder, requestWrapper);
                appendHeader(logBuilder, requestWrapper);
                log.debug("\n{}", logBuilder);
                filterChain.doFilter(requestWrapper, responseWrapper);
                injectInterfaceServiceLog(request);
                printResponse(responseWrapper, startTime);
                return;
            }

            // POST/PUT/PATCH 请求
            if (POST.equalsIgnoreCase(method) || PUT.equalsIgnoreCase(method) || "patch".equalsIgnoreCase(method)) {
                appendBody(logBuilder, requestWrapper);
                appendHeader(logBuilder, requestWrapper);
                log.debug(logBuilder.toString());
                filterChain.doFilter(requestWrapper, responseWrapper);
                injectInterfaceServiceLog(requestWrapper);
                printResponse(responseWrapper, startTime);
                return;
            }

            log.debug(logBuilder.toString());
            injectInterfaceServiceLog(requestWrapper);
            filterChain.doFilter(requestWrapper, responseWrapper);
            printResponse(responseWrapper, startTime);
        } finally {
            responseWrapper.copyBodyToResponse();
        }
    }

    /**
     * 打印响应信息
     *
     * @param responseWrapper 响应包装器
     * @param startTime       开始时间
     */
    private void printResponse(ContentCachingResponseWrapper responseWrapper, long startTime) {
        long costTime = System.currentTimeMillis() - startTime;
        int status = responseWrapper.getStatus();
        
        StringBuilder sb = new StringBuilder();
        sb.append("╠══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ [响应信息]\n");
        sb.append("║ 状态码: ").append(status).append("\n");
        sb.append("║ 响应类型: ").append(responseWrapper.getContentType()).append("\n");
        
        // 打印响应体（限制大小）
        byte[] content = responseWrapper.getContentAsByteArray();
        if (content.length > 0 && content.length < NumberConstant.TWE_THOUSAND) {
            String responseBody = new String(content, StandardCharsets.UTF_8);
            // 截取前500字符
            if (responseBody.length() > 500) {
                responseBody = responseBody.substring(0, 500) + "...(截断)";
            }
            sb.append("║ 响应内容: ").append(responseBody).append("\n");
        } else if (content.length >= NumberConstant.TWE_THOUSAND) {
            sb.append("║ 响应内容: (内容过长，共 ").append(content.length).append(" 字节)\n");
        }
        
        sb.append("║ 耗时: ").append(costTime).append("ms\n");
        sb.append("╚══════════════════════════════════════════════════════════════════════════════\n");
        
        log.debug("\n{}", sb);
        
        // 慢请求警告
        if (costTime > 3000) {
            log.warn("[慢请求警告] 耗时: {}ms, 状态码: {}", costTime, status);
        }
    }

    /**
     * 打印请求耗时（简化版，用于静态资源）
     *
     * @param method    请求方法
     * @param uri       请求 URI
     * @param startTime 开始时间
     * @param status    响应状态码
     */
    private void printCostTime(String method, String uri, long startTime, int status) {
        long costTime = System.currentTimeMillis() - startTime;
        if (costTime > 3000) {
            log.warn("[慢请求] {} {} 状态码: {} 耗时: {}ms", method, uri, status, costTime);
        } else if (costTime > 1000) {
            log.info("[请求耗时] {} {} 状态码: {} 耗时: {}ms", method, uri, status, costTime);
        } else {
            log.debug("[请求耗时] {} {} 状态码: {} 耗时: {}ms", method, uri, status, costTime);
        }
    }

    /**
     * 注入接口服务日志
     *
     * @param request 请求
     */
    private void injectInterfaceServiceLog(HttpServletRequest request) {
        if (!logProperties.isOpenInterfaceLog()) {
            return;
        }

        interfaceServiceLog.execute(() -> {
            applicationContext.publishEvent(new InterfaceLoggerInfo(request));
        });
    }

    /**
     * 是否跳过
     *
     * @param requestURI 请求URI
     * @return 是否跳过
     */
    private boolean isPass(String requestURI) {
        return RequestUtils.isResource(requestURI);
    }

    /**
     * 追加URL信息到日志
     *
     * @param sb      StringBuilder
     * @param request 请求
     */
    private void appendUrl(StringBuilder sb, HttpServletRequest request) {
        sb.append("║ 请求方法: ").append(request.getMethod()).append("\n");
        sb.append("║ 请求URI: ").append(request.getRequestURI()).append("\n");
        sb.append("║ 完整URL: ").append(request.getRequestURL()).append("\n");
        String queryString = request.getQueryString();
        if (StringUtils.isNotEmpty(queryString)) {
            sb.append("║ 查询字符串: ").append(queryString).append("\n");
        }
    }

    /**
     * 追加请求头信息到日志
     *
     * @param sb      StringBuilder
     * @param request 请求
     */
    private void appendHeader(StringBuilder sb, HttpServletRequest request) {
        sb.append("║ 客户端IP: ").append(RequestUtils.getIpAddress(request)).append("\n");
        sb.append("║ User-Agent: ").append(request.getHeader("User-Agent")).append("\n");
        sb.append("║ Content-Type: ").append(request.getContentType()).append("\n");
        sb.append("║ Content-Length: ").append(request.getContentLength()).append("\n");
        
        // 打印自定义请求头 (x- 开头)
        Enumeration<String> headerNames = request.getHeaderNames();
        List<String> customHeaders = new LinkedList<>();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (headerName.startsWith("x-")) {
                customHeaders.add(headerName);
            }
        }

        if (CollectionUtils.isNotEmpty(customHeaders)) {
            sb.append("╟──────────────────── 自定义请求头 ────────────────────\n");
            for (String headerName : customHeaders) {
                sb.append("║ ").append(headerName).append(": ").append(request.getHeader(headerName)).append("\n");
            }
        }
    }

    /**
     * 追加查询参数到日志
     *
     * @param sb      StringBuilder
     * @param request 请求
     */
    private void appendQuery(StringBuilder sb, HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (StringUtils.isNotEmpty(queryString)) {
            sb.append("╟──────────────────── 请求参数 ────────────────────\n");
            if (queryString.length() < NumberConstant.ONE_THOUSAND) {
                String decoded = URLDecoder.decode(queryString, StandardCharsets.UTF_8);
                sb.append("║ ").append(decoded).append("\n");
            } else {
                sb.append("║ (参数过长，共 ").append(queryString.length()).append(" 字符)\n");
            }
        }
    }

    /**
     * 追加请求体到日志
     *
     * @param sb             StringBuilder
     * @param requestWrapper 请求包装器
     */
    private void appendBody(StringBuilder sb, ContentCachingRequestWrapper requestWrapper) {
        sb.append("║ 请求方法: ").append(requestWrapper.getMethod()).append("\n");
        sb.append("║ 请求URL: ").append(requestWrapper.getRequestURL()).append("\n");
        
        String body = IoUtils.toString(requestWrapper.getContentAsByteArray(), requestWrapper.getCharacterEncoding());
        if (StringUtils.isNotEmpty(body)) {
            sb.append("╟──────────────────── 请求体 ────────────────────\n");
            if (body.length() < NumberConstant.TWE_THOUSAND) {
                sb.append("║ ").append(body).append("\n");
            } else {
                sb.append("║ (请求体过长，共 ").append(body.length()).append(" 字符)\n");
            }
        }
    }
}
