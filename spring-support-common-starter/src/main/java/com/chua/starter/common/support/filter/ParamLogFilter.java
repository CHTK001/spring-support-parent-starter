package com.chua.starter.common.support.filter;

import com.chua.common.support.log.Log;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static com.chua.common.support.constant.NameConstant.GET;
import static com.chua.common.support.constant.NameConstant.POST;

/**
 * 参数日志过滤器
 *
 * @author CH
 * @since 2023/09/08
 */
public class ParamLogFilter implements Filter {

    private static final Log log = Log.getLogger(Filter.class);

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        CustomHttpServletRequestWrapper requestWrapper = new CustomHttpServletRequestWrapper((HttpServletRequest) servletRequest);
        printLog(requestWrapper);
        filterChain.doFilter(requestWrapper, servletResponse);
    }

    private void printLog(CustomHttpServletRequestWrapper requestWrapper) throws IOException {
        log.info("请求URL: {}", requestWrapper.getRequestURL());
        String body = null;
            String method = requestWrapper.getMethod();
        if (GET.equalsIgnoreCase(method)) {
            body = requestWrapper.getQueryString();
        } else if (POST.equalsIgnoreCase(method)) {
            body = IoUtils.toString(requestWrapper.getInputStream(), requestWrapper.getCharacterEncoding());
        }
        if(StringUtils.isNotEmpty(body)) {
            log.info("请求参数: {}", body);
        }
    }
}
