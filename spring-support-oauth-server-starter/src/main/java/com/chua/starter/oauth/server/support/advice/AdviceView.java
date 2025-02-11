package com.chua.starter.oauth.server.support.advice;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    private volatile transient HttpServletRequest request;
}
