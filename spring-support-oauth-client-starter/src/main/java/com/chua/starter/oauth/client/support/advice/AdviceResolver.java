package com.chua.starter.oauth.client.support.advice;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 解释器
 *
 * @author CH
 * @since 2022/7/29 9:43
 */
public interface AdviceResolver {
    /**
     * 类型
     *
     * @return 类型
     */
    String type();

    /**
     * 解释器
     *
     * @param response 响应
     * @param status   状态
     * @param message  错误消息
     * @return 结果
     * @throws IOException ex
     */
    Object resolve(HttpServletResponse response, Integer status, String message) throws IOException;

    /**
     * 是否是html
     *
     * @return html
     */
    default boolean isHtml() {
        return false;
    }
}
