package com.chua.starter.common.support.result;

import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonArray;
import com.chua.common.support.net.UserAgent;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.codec.CodecFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

        String userAgent = headers.getFirst("user-agent");
        if(UserAgent.isCrawler(userAgent)) {
            return EmptyHttpInputMessage.getInstance();
        }
        byte[] originByteArray = IoUtils.toByteArray(inputMessage.getBody());
        String encodeBody = StringUtils.utf8Str(originByteArray);
        String data = null;
        try {
            if(encodeBody.startsWith("[")) {
                JsonArray jsonArray = Json.getJsonArray(encodeBody);
                if(jsonArray.isEmpty()){
                    return new ByteInputMessage(originByteArray, headers);
                }

                data = jsonArray.getJsonObject(0).getString("data");
            } else {
                data = Json.getJsonObject(encodeBody).getString("data");
            }
        } catch (Exception e) {
            return new ByteInputMessage(originByteArray, headers);
        }

        if(StringUtils.isNotBlank(data)) {
            byte[] sourceByteArray = codecFactory.decodeRequest(data);
        }
        return new ByteInputMessage(originByteArray, headers);
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    static class ByteInputMessage implements HttpInputMessage {
        private final byte[] body;
        private final ByteArrayInputStream inputStream;
        private final HttpHeaders headers;

        ByteInputMessage(byte[] body, HttpHeaders headers) {
            this.body = body;
            this.headers = headers;
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public InputStream getBody() throws IOException {
            return inputStream;
        }
        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

     static class EmptyHttpInputMessage implements HttpInputMessage{
        private static final EmptyHttpInputMessage EMPTY_MESSAGE = new EmptyHttpInputMessage();

        static final InputStream EMPTY = new ByteArrayInputStream(new byte[0]);
        static final HttpHeaders EMPTY_HEADER = new HttpHeaders();
         @Override
         public InputStream getBody() throws IOException {
             return EMPTY;
         }

         @Override
         public HttpHeaders getHeaders() {
             return EMPTY_HEADER;
         }

         public static EmptyHttpInputMessage getInstance() {
             return EMPTY_MESSAGE;
         }
     }
}
