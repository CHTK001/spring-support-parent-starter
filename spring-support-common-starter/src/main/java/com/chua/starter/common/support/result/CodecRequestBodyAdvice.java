package com.chua.starter.common.support.result;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.codec.CodecFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * 请求加密
 * @author CH
 */
@RestControllerAdvice
@Slf4j
@SuppressWarnings("ALL")
public class CodecRequestBodyAdvice implements RequestBodyAdvice {

    private final CodecFactory codecFactory;

    public CodecRequestBodyAdvice(CodecFactory codecFactory) {
        this.codecFactory = codecFactory;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        HttpHeaders headers = inputMessage.getHeaders();
        String keyHeader = headers.getFirst(codecFactory.getKeyHeader());
        if(StringUtils.isEmpty(keyHeader)) {
            return inputMessage;
        }

        return inputMessage;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
