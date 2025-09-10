package com.chua.starter.common.support.result;

import com.chua.common.support.converter.Converter;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.RandomUtils;
import com.chua.starter.common.support.codec.CodecFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 响应加密
 * @author CH
 */
@RestControllerAdvice
@Slf4j
@SuppressWarnings("ALL")

public class CodecResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final CodecFactory codecFactory;


    public CodecResponseBodyAdvice(CodecFactory codecFactory) {
        this.codecFactory = codecFactory;
    }


    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        if(codecFactory.isPass()) {
            return o;
        }

        if( methodParameter.hasMethodAnnotation(com.chua.common.support.annotations.Ignore.class)) {
            return o;
        }

        if(serverHttpRequest instanceof ServletServerHttpRequest) {
            if(o instanceof ReturnResult returnResult && !returnResult.isOk()) {
                return o;
            }

            HttpServletRequest servletRequest = ((ServletServerHttpRequest) serverHttpRequest).getServletRequest();
            if(codecFactory.isPass(servletRequest.getRequestURI())) {
                return o;
            }

            Boolean aBoolean = Converter.convertIfNecessary(servletRequest.getSession().getAttribute("codec"), Boolean.class);
            if(null != aBoolean && !aBoolean) {
                return o;
            }



            HttpHeaders headers = serverHttpResponse.getHeaders();
            JsonObject jsonObject = new JsonObject();

            // 使用一次性密钥加密
            CodecFactory.CodecResult codecResult = codecFactory.encodeWithOtk(Json.toJson(o));

            // 设置响应头
            headers.set(codecFactory.getKeyHeader(), codecResult.getKey());
            headers.set("access-control-timestamp-user", codecResult.getTimestamp());

            // 如果有一次性密钥ID，添加到响应头
            if (codecResult.getOtkId() != null) {
                headers.set("access-control-otk-id", codecResult.getOtkId());
                log.debug("[CodecResponse] 响应包含一次性密钥ID: {}", codecResult.getOtkId());
            }

            // 构建响应数据（保持原有的混淆格式）
            jsonObject.put("data", "02" + RandomUtils.randomInt(1) + "200" + codecResult.getData() + "ffff");

            log.debug("[CodecResponse] 响应加密完成，数据长度: {}", codecResult.getData() != null ? codecResult.getData().length() : 0);
            return jsonObject;
        }

        return o;
    }
}
