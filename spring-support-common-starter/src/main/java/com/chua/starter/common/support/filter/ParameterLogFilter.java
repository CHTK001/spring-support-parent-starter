package com.chua.starter.common.support.filter;

import com.chua.common.support.constant.NumberConstant;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.logger.InterfaceLoggerInfo;
import com.chua.starter.common.support.properties.LogProperties;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationContext;

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
 *
 * @author CH
 * @since 2023/09/08
 */
public class ParameterLogFilter implements Filter {

    private static final Log log = Log.getLogger(Filter.class);
    private final LogProperties loggerProperties;
    private final ApplicationContext applicationContext;
    private final ExecutorService interfaceServiceLog = Executors.newVirtualThreadPerTaskExecutor();

    public ParameterLogFilter(LogProperties loggerProperties, ApplicationContext applicationContext) {
        this.loggerProperties = loggerProperties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if(!(servletRequest instanceof HttpServletRequest request)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }


        String requestURI = request.getRequestURI();
        if(isPass(requestURI)) {
            filterChain.doFilter(servletRequest, servletResponse);
            injectInterfaceServiceLog(request);
            return;
        }
        log.info("======================================");
        hook(request);
        String header = request.getHeader(HTTP_HEADER_CONTENT_TYPE);
        if(StringUtils.contains(header, "form-data")) {
            filterChain.doFilter(servletRequest, servletResponse);
            injectInterfaceServiceLog(request);
            return;
        }
        String method = request.getMethod();

        if (StringUtils.isEmpty(header) || (GET.equals(method) || DELETE.equals(method))) {
            printUrl(request);
            printQuery(request);
            printHeader(request);
            log.info("======================================\r\n");
            injectInterfaceServiceLog(request);
            filterChain.doFilter(request, servletResponse);
            return;
        }

        if (POST.equalsIgnoreCase(method) || PUT.equalsIgnoreCase(method) || "patch".equalsIgnoreCase(method)) {
            CustomHttpServletRequestWrapper requestWrapper = new CustomHttpServletRequestWrapper((HttpServletRequest) servletRequest);
            printBody(requestWrapper);
            printHeader(request);
            log.info("======================================\r\n");
            injectInterfaceServiceLog(requestWrapper);
            filterChain.doFilter(requestWrapper, servletResponse);
            return;
        }
        injectInterfaceServiceLog(request);
        filterChain.doFilter(request, servletResponse);
    }

    /**
     * 注入接口服务日志
     *
     * @param request 请求
     */
    private void injectInterfaceServiceLog(HttpServletRequest request) {
        if (!loggerProperties.isOpenInterfaceLog()) {
            return;
        }

        interfaceServiceLog.execute(() -> {
            applicationContext.publishEvent(new InterfaceLoggerInfo(request));
        });
    }


    private boolean isPass(String requestURI) {
        return RequestUtils.isResource(requestURI);
    }

    private void hook(HttpServletRequest request) {
        request.getParameterNames();
        request.getHeaderNames();
        request.getAttributeNames();
        request.getLocales();
    }
    private void printUrl(HttpServletRequest request) {
        log.info("{} -> {}", request.getMethod(), request.getRequestURI());
    }
    private void printHeader(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        List<String> headers = new LinkedList<>();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if(headerName.startsWith("x-")) {
                headers.add(headerName);
                continue;
            }
        }

        log.info("客户端地址：{}", RequestUtils.getIpAddress(request));
        if(headers.isEmpty()) {
            return;
        }

        if(CollectionUtils.isNotEmpty(headers)) {
            log.info("************************请求头**********************");
            for (String headerName : headers) {
                log.info("{}: {}", headerName, request.getHeader(headerName));
            }
            log.info("***************************************************");
        }
    }

    private void printQuery(HttpServletRequest request) throws IOException {
        String body = request.getQueryString();
        if(StringUtils.isNotEmpty(body) && body.length() < NumberConstant.ONE_THOUSAND) {
            body = URLDecoder.decode(body, StandardCharsets.UTF_8);
            log.info("请求参数: {}", body);
        }
    }

    private void printBody(CustomHttpServletRequestWrapper requestWrapper) throws IOException {
        log.info("请求URL: {} -> {}", requestWrapper.getMethod(), requestWrapper.getRequestURL());
        String body = IoUtils.toString(requestWrapper.getInputStream(), requestWrapper.getCharacterEncoding());
        if(null != body && body.length() < NumberConstant.TWE_THOUSAND) {
            log.info("请求消息体: {}", body);
        }

    }
}
