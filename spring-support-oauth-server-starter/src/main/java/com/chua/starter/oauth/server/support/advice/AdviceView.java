package com.chua.starter.oauth.server.support.advice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.*;
import lombok.experimental.Accessors;


/**
 * advice
 *
 * @author CH
 */
@Data
@Accessors(chain = true)
@RequiredArgsConstructor
@AllArgsConstructor
@NoArgsConstructor
public class AdviceView<T> {
    /**
     * 数据
     */
    @NonNull
    private T data;
    /**
     * 请求
     */
    private volatile transient HttpServletRequest request;
}
