package com.chua.starter.oauth.server.support.advice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import javax.servlet.http.HttpServletRequest;


/**
 * advice
 *
 * @author CH
 */
@Data
@Accessors(chain = true)
@RequiredArgsConstructor
@AllArgsConstructor
public class AdviceView<T> {
    /**
     * 数据
     */
    @NonNull
    private T data;
    /**
     * 请求
     */
    private HttpServletRequest request;
}
