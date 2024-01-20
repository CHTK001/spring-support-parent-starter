package com.chua.starter.common.support.result;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.utils.Hex;
import com.chua.starter.common.support.properties.CodecProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.security.PrivateKey;

/**
 * @author CH
 */
@RestControllerAdvice
@Slf4j
public class CodeResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final CodecProperties codecProperties;

    private PrivateKey privateKey;

    public CodeResponseBodyAdvice(CodecProperties codecProperties) {
        this.codecProperties = codecProperties;
        privateKey = createPrivateKey();
    }

    private PrivateKey createPrivateKey() {
        if(codecProperties.isEnable()) {
            try {
                Codec codec = Codec.build(codecProperties.getCodecType());
                if(codec instanceof CodecKeyPair) {
                    return ((CodecKeyPair) codec).getPrivateKey(Hex.decodeHex(codecProperties.getPrivateKey()));
                }
            } catch (Exception e) {
            }
        }
        return null;

    }

    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        if(!codecProperties.isEnable()) {
            return o;
        }

        Codec codec = Codec.build(codecProperties.getCodecType());
        CodecKeyPair codecKeyPair = (CodecKeyPair) codec;
        codecKeyPair.setPrivateKey(privateKey);
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("data", codec.encodeHex(Json.toJson(o)));
        jsonObject.put("codec", true);
        return jsonObject;
    }
}
