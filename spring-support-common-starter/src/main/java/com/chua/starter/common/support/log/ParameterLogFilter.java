package com.chua.starter.common.support.log;

import com.chua.common.support.constant.NumberConstant;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.logger.InterfaceLoggerInfo;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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

import static com.chua.common.support.constant.NameConstant.*;
import static com.chua.common.support.http.HttpConstant.HTTP_HEADER_CONTENT_TYPE;
import static java.time.LocalDateTime.*;
import static java.time.format.DateTimeFormatter.*;

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

        // 打印请求分隔线
        log.debug("╔══════════════════════════════════════════════════════════════════════════════");
        log.debug("║ 【请求开始】 TraceId: {}", traceId);
        log.debug("║ 请求时间: {}", now().format(DATE_TIME_FORMATTER));
        log.debug("╠══════════════════════════════════════════════════════════════════════════════");
        
        String contentType = requestWrapper.getHeader(HTTP_HEADER_CONTENT_TYPE);
        
        try {
            // form-data 请求
            if (StringUtils.contains(contentType, "form-data")) {
                printUrl(requestWrapper);
                printHeader(requestWrapper);
                log.debug("║ 请求类型: multipart/form-data (文件上传)");
                filterChain.doFilter(requestWrapper, responseWrapper);
                injectInterfaceServiceLog(requestWrapper);
                printResponse(responseWrapper, startTime);
                return;
            }

            // GET/DELETE 请求
            if (StringUtils.isEmpty(contentType) || GET.equals(method) || DELETE.equals(method)) {
                printUrl(requestWrapper);
                printQuery(requestWrapper);
                printHeader(requestWrapper);
                filterChain.doFilter(requestWrapper, responseWrapper);
                injectInterfaceServiceLog(request);
                printResponse(responseWrapper, startTime);
                return;
            }

            // POST/PUT/PATCH 请求
            if (POST.equalsIgnoreCase(method) || PUT.equalsIgnoreCase(method) || "patch".equalsIgnoreCase(method)) {
                printBody(requestWrapper);
                printHeader(requestWrapper);
                filterChain.doFilter(requestWrapper, responseWrapper);
                injectInterfaceServiceLog(requestWrapper);
                printResponse(responseWrapper, startTime);
                return;
            }

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
        
        log.debug("╠══════════════════════════════════════════════════════════════════════════════");
        log.debug("║ 【响应信息】");
        log.debug("║ 状态码: {}", status);
        log.debug("║ 响应类型: {}", responseWrapper.getContentType());
        
        // 打印响应体（限制大小）
        byte[] content = responseWrapper.getContentAsByteArray();
        if (content.length > 0 && content.length < NumberConstant.TWE_THOUSAND) {
            String responseBody = new String(content, StandardCharsets.UTF_8);
            // 截取前500字符
            if (responseBody.length() > 500) {
                responseBody = responseBody.substring(0, 500) + "...(截断)";
            }
            log.debug("║ 响应内容: \r{}", responseBody);
        } else if (content.length >= NumberConstant.TWE_THOUSAND) {
            log.debug("║ 响应内容: (内容过长，共 {} 字节)", content.length);
        }
        
        log.debug("║ 耗时: {}ms", costTime);
        log.debug("╚══════════════════════════════════════════════════════════════════════════════\r\n");
        
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
     * 打印URL
     *
     * @param request 请求
     */
    private void printUrl(HttpServletRequest request) {
        log.debug("║ 请求方法: {}", request.getMethod());
        log.debug("║ 请求URI: {}", request.getRequestURI());
        log.debug("║ 完整URL: {}", request.getRequestURL());
        String queryString = request.getQueryString();
        if (StringUtils.isNotEmpty(queryString)) {
            log.debug("║ 查询字符串: {}", queryString);
        }
    }

    /**
     * 打印请求头
     *
     * @param request 请求
     */
    private void printHeader(HttpServletRequest request) {
        log.debug("║ 客户端IP: {}", RequestUtils.getIpAddress(request));
        log.debug("║ User-Agent: {}", request.getHeader("User-Agent"));
        log.debug("║ Content-Type: {}", request.getContentType());
        log.debug("║ Content-Length: {}", request.getContentLength());
        
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
            log.debug("╟──────────────────── 自定义请求头 ────────────────────");
            for (String headerName : customHeaders) {
                log.debug("║ {}: {}", headerName, request.getHeader(headerName));
            }
        }
    }

    /**
     * 打印查询参数
     *
     * @param request 请求
     * @throws IOException IO异常
     */
    private void printQuery(HttpServletRequest request) throws IOException {
        String queryString = request.getQueryString();
        if (StringUtils.isNotEmpty(queryString)) {
            if (queryString.length() < NumberConstant.ONE_THOUSAND) {
                String decoded = URLDecoder.decode(queryString, StandardCharsets.UTF_8);
                log.debug("╟──────────────────── 请求参数 ────────────────────");
                log.debug("║ {}", decoded);
            } else {
                log.debug("╟──────────────────── 请求参数 ────────────────────");
                log.debug("║ (参数过长，共 {} 字符)", queryString.length());
            }
        }
    }

    /**
     * 打印请求体
     *
     * @param requestWrapper 请求包装器
     * @throws IOException IO异常
     */
    private void printBody(ContentCachingRequestWrapper requestWrapper) throws IOException {
        log.debug("║ 请求方法: {}", requestWrapper.getMethod());
        log.debug("║ 请求URL: {}", requestWrapper.getRequestURL());
        
        String body = IoUtils.toString(requestWrapper.getContentAsByteArray(), requestWrapper.getCharacterEncoding());
        if (StringUtils.isNotEmpty(body)) {
            log.debug("╟──────────────────── 请求体 ────────────────────");
            if (body.length() < NumberConstant.TWE_THOUSAND) {
                // 格式化 JSON 输出（如果是 JSON）
                if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
                    log.debug("║ {}", body);
                } else {
                    log.debug("║ {}", body);
                }
            } else {
                log.debug("║ (请求体过长，共 {} 字符)", body.length());
            }
        }
    }
}
