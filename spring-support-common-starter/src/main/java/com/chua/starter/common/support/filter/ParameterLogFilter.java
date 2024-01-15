package com.chua.starter.common.support.filter;

import com.chua.common.support.constant.NumberConstant;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.RequestUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

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

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if(!(servletRequest instanceof HttpServletRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String requestURI = request.getRequestURI();
        if(isPass(requestURI)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        log.info("======================================");
        hook(request);
        String header = request.getHeader(HTTP_HEADER_CONTENT_TYPE);
        if(StringUtils.contains(header, "form-data")) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        String method = request.getMethod();

        if(StringUtils.isEmpty(header) || (GET.equals(method) || PUT.equals(method))) {
            printQuery(request);
            printHeader(request);
            log.info("======================================\r\n");
            filterChain.doFilter(request, servletResponse);
            return;
        }

        if(POST.equalsIgnoreCase(method) || PUT.equalsIgnoreCase(method)) {
            CustomHttpServletRequestWrapper requestWrapper = new CustomHttpServletRequestWrapper((HttpServletRequest) servletRequest);
            printBody(requestWrapper);
            printHeader(request);
            log.info("======================================\r\n");
            filterChain.doFilter(requestWrapper, servletResponse);
            return;
        }
        filterChain.doFilter(request, servletResponse);
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

    private void printHeader(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        List<String> headers = new LinkedList<>();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if(headerName.startsWith("x-")) {
                headers.add(headerName);
            }
        }

        if(headers.isEmpty()) {
            return;
        }
        log.info("请求头");
        for (String headerName : headers) {
            log.info("{}: {}", headerName, request.getHeader(headerName));
        }
    }

    private void printQuery(HttpServletRequest request) throws IOException {
        log.info("请求URL: {}", request.getRequestURL());
        String body = request.getQueryString();
        if(StringUtils.isNotEmpty(body) && body.length() < NumberConstant.ONE_THOUSAND) {
            try {
                body = URLDecoder.decode(body, "UTF-8");
                log.info("请求参数: {}", body);
            } catch (UnsupportedEncodingException ignored) {
            }
        }
    }

    private void printBody(CustomHttpServletRequestWrapper requestWrapper) throws IOException {
        log.info("请求URL: {} -> {}", requestWrapper.getMethod(), requestWrapper.getRequestURL());
        String body = IoUtils.toString(requestWrapper.getInputStream(), requestWrapper.getCharacterEncoding());
        if(null != body && body.length() < NumberConstant.TWE_THOUSAND) {
            log.info("请求参数: {}", body);
        }

    }
}
