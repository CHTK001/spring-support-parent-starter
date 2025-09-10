package com.chua.starter.common.support.utils;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 响应工具类
 *
 * @author CH
 * @since 2023-04-01
 */
public class ResponseUtils {

    /**
     * 获取当前请求的HttpServletResponse对象
     * <p>通过Spring的RequestContextHolder获取当前线程绑定的请求属性，从而获取HttpServletResponse对象。</p>
     * <p>使用示例：</p>
     * <pre>
     *     HttpServletResponse response = ResponseUtils.getResponse();
     *     if (response != null) {
     *         // 对response进行操作
     *     }
     * </pre>
     *
     * @return {@link HttpServletResponse} 当前请求的响应对象，如果无法获取则返回null
     */
    public static HttpServletResponse getResponse() {
        ServletRequestAttributes attributes = null;
        try {
            attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return null != attributes ? attributes.getResponse() : null;
        } catch (Exception e) {
            // 忽略异常，返回null
        }
        return null;
    }
}
