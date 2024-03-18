package com.chua.starter.common.support.utils;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 响应
 *
 * @author CH
 */
public class ResponseUtils {

    /**
     * 请求
     *
     * @return 请求
     */
    public static HttpServletResponse getResponse() {
        ServletRequestAttributes attr = null;
        try {
            attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return null != attr ? attr.getResponse() : null;
        } catch (Exception ignored) {
        }
        return null;
    }
}
