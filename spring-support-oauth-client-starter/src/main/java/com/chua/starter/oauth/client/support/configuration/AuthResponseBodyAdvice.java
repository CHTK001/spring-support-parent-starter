package com.chua.starter.oauth.client.support.configuration;

import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.oauth.client.support.advice.Advice;
import com.chua.starter.oauth.client.support.advice.AdviceResolver;
import com.chua.starter.oauth.client.support.advice.HtmlAdviceResolver;
import com.chua.starter.oauth.client.support.exception.OauthException;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * auth 统一响应
 *
 * @author CH
 * @since 2022/7/29 9:42
 */
@Slf4j
@RestControllerAdvice
public class AuthResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Resource
    private AuthClientProperties authProperties;

    /**
     * 鉴权错误
     *
     * @param request  请求
     * @param response 响应
     * @param error    异常
     */
    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    public Object httpRequestMethodNotSupportedException(HttpServletRequest request, HttpServletResponse response, Throwable error) {
        return ReturnResult.of(405, null, "请求类型不支持");
    }

    /**
     * 鉴权错误
     *
     * @param request  请求
     * @param response 响应
     * @param error    异常
     */
    @ExceptionHandler(value = ServletException.class)
    public Object servletException(HttpServletRequest request, HttpServletResponse response, ServletException error) {
        return ReturnResult.of(405, null, "请求方式不允许");
    }

    /**
     * 鉴权错误
     *
     * @param request  请求
     * @param response 响应
     * @param error    异常
     */
    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public Object missingServletRequestParameterException(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException error) {
        log.info("{} -> 参数缺失[{}({})]", request.getRequestURI(), error.getParameterName(), error.getParameterType());
        return ReturnResult.of(400, null, "请联系管理员");
    }

    /**
     * 鉴权错误
     *
     * @param error 异常
     */
    @ExceptionHandler(value = Exception.class)
    public Object exception(HttpServletRequest request, Exception error) {
        return ReturnResult.of(500, null, "请联系管理员");
    }

    /**
     * 鉴权错误
     *
     * @param error 异常
     */
    @ExceptionHandler(value = IllegalArgumentException.class)
    public Object illegalArgumentException(HttpServletRequest request, IllegalArgumentException error) {
        log.info("{} -> 参数不一致[{}]", request.getRequestURI(), error.getMessage());
        return ReturnResult.of(500, null, "请联系管理员");
    }

    /**
     * 鉴权错误
     *
     * @param request  请求
     * @param response 响应
     * @param error    异常
     */
    @ExceptionHandler(value = OauthException.class)
    public void oauthException(HttpServletRequest request, HttpServletResponse response, Throwable error) {
        String accept = request.getHeader("accept");
        String contentType = response.getContentType();
        Advice advice = Advice.create();
        AdviceResolver resolve = advice.resolve(accept, contentType);
        if (null == resolve) {
            return;
        }

        if (!resolve.isHtml()) {
            try {
                resolve.resolve(response, 403, "无权限访问");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        ((HtmlAdviceResolver) resolve).resolve(request, response, 403, authProperties);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return returnType.getGenericParameterType() != ReturnResult.class;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Map) {
            Object status = ((Map<?, ?>) body).get("status");
            return advice(status, (Map<?, ?>) body, selectedContentType);
        }
        return body;
    }

    /**
     * 异常处理
     *
     * @param status              状态
     * @param body                结果
     * @param selectedContentType 类型
     * @return 结果
     */
    private Object advice(Object status, Map body, MediaType selectedContentType) {
        if (null == status) {
            return body;
        }
        String string = status.toString();
        Advice advice = Advice.create();
        AdviceResolver resolve = advice.resolve(selectedContentType);
        try {
            return resolve.resolve(null, Integer.valueOf(string), body.get("error").toString());
        } catch (IOException ignored) {
        }
        return null;
    }
}
