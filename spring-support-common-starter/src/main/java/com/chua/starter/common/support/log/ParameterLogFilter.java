package com.chua.starter.common.support.log;

import com.chua.common.support.constant.NumberConstant;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.logger.InterfaceLoggerInfo;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.chua.common.support.constant.NameConstant.*;
import static com.chua.common.support.http.HttpConstant.HTTP_HEADER_CONTENT_TYPE;

/**
 * 参数日志过滤器
 * <p>
 * 用于记录接口请求的参数信息。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2023/09/08
 */
@Slf4j
public class ParameterLogFilter implements Filter {

    private final LogProperties logProperties;
    private final ApplicationContext applicationContext;
    private final ExecutorService interfaceServiceLog = Executors.newVirtualThreadPerTaskExecutor();

    public ParameterLogFilter(LogProperties logProperties, ApplicationContext applicationContext) {
        this.logProperties = logProperties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest request)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        String connection = requestWrapper.getHeader("upgrade");
        if ("websocket".equalsIgnoreCase(connection)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String requestURI = requestWrapper.getRequestURI();
        if (isPass(requestURI)) {
            filterChain.doFilter(requestWrapper, servletResponse);
            injectInterfaceServiceLog(requestWrapper);
            return;
        }

        log.debug("======================================");
        String header = requestWrapper.getHeader(HTTP_HEADER_CONTENT_TYPE);
        if (StringUtils.contains(header, "form-data")) {
            filterChain.doFilter(requestWrapper, servletResponse);
            injectInterfaceServiceLog(requestWrapper);
            return;
        }

        String method = requestWrapper.getMethod();

        if (StringUtils.isEmpty(header) || (GET.equals(method) || DELETE.equals(method))) {
            printUrl(requestWrapper);
            printQuery(requestWrapper);
            printHeader(requestWrapper);
            log.debug("======================================\r\n");
            injectInterfaceServiceLog(request);
            filterChain.doFilter(request, servletResponse);
            return;
        }

        if (POST.equalsIgnoreCase(method) || PUT.equalsIgnoreCase(method) || "patch".equalsIgnoreCase(method)) {
            printBody(requestWrapper);
            printHeader(requestWrapper);
            log.debug("======================================\r\n");
            injectInterfaceServiceLog(requestWrapper);
            filterChain.doFilter(requestWrapper, servletResponse);
            return;
        }

        injectInterfaceServiceLog(requestWrapper);
        filterChain.doFilter(requestWrapper, servletResponse);
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
        log.debug("{} -> {}", request.getMethod(), request.getRequestURI());
    }

    /**
     * 打印请求头
     *
     * @param request 请求
     */
    private void printHeader(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        List<String> headers = new LinkedList<>();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (headerName.startsWith("x-")) {
                headers.add(headerName);
            }
        }

        log.debug("客户端地址: {}", RequestUtils.getIpAddress(request));
        if (headers.isEmpty()) {
            return;
        }

        if (CollectionUtils.isNotEmpty(headers)) {
            log.debug("************************请求头**********************");
            for (String headerName : headers) {
                log.debug("{}: {}", headerName, request.getHeader(headerName));
            }
            log.debug("***************************************************");
        }
    }

    /**
     * 打印查询参数
     *
     * @param request 请求
     * @throws IOException IO异常
     */
    private void printQuery(HttpServletRequest request) throws IOException {
        String body = request.getQueryString();
        if (StringUtils.isNotEmpty(body) && body.length() < NumberConstant.ONE_THOUSAND) {
            body = URLDecoder.decode(body, StandardCharsets.UTF_8);
            log.debug("请求参数: {}", body);
        }
    }

    /**
     * 打印请求体
     *
     * @param requestWrapper 请求包装器
     * @throws IOException IO异常
     */
    private void printBody(ContentCachingRequestWrapper requestWrapper) throws IOException {
        log.debug("请求URL: {} -> {}", requestWrapper.getMethod(), requestWrapper.getRequestURL());
        String body = IoUtils.toString(requestWrapper.getContentAsByteArray(), requestWrapper.getCharacterEncoding());
        if (body.length() < NumberConstant.TWE_THOUSAND) {
            log.debug("请求消息体: {}", body);
        }
    }
}
