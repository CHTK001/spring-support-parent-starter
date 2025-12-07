package com.chua.starter.common.support.converter;

import com.chua.starter.common.support.api.encode.ApiResponseEncodeRegister;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author CH
 * @since 2025/10/10 14:22
 */
public class BinaryHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

    private final ApiResponseEncodeRegister apiResponseEncodeRegister;
    private final List<HttpMessageConverter<?>> messageConverters;

    public BinaryHttpMessageConverter(ApiResponseEncodeRegister apiResponseEncodeRegister, List<HttpMessageConverter<?>> messageConverters) {
        super(MediaType.parseMediaType("application/encrypted-data"));
        this.apiResponseEncodeRegister = apiResponseEncodeRegister;
        this.messageConverters = messageConverters;
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    protected void writeInternal(Object o, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {

        if (o instanceof ResponseEntity<?> entity) {
            registerResponseEntity(entity, outputMessage);
            return;
        }

        if (o instanceof byte[] bytes) {
            outputMessage.getBody().write(bytes);
            return;
        }

        HttpServletRequest request = RequestUtils.getRequest();
        if (null == request) {
            return;
        }
        String string = request.getHeader("access-control-no-data");
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null) {
            outputMessage.getHeaders().remove("access-control-no-data");
            registerAccept(outputMessage, o);
            return;
        }
    }

    private void registerAccept(HttpOutputMessage outputMessage, Object o) throws IOException {
        for (HttpMessageConverter<?> messageConverter : messageConverters) {
            Class<?> aClass = o.getClass();
            List<MediaType> supportedMediaTypes = messageConverter.getSupportedMediaTypes(aClass);
            if (supportedMediaTypes.isEmpty()) {
                continue;
            }
            for (MediaType supportedMediaType : supportedMediaTypes) {
                if (messageConverter.canWrite(aClass, supportedMediaType)) {
                    outputMessage.getHeaders().setContentType(supportedMediaType);
                    ((HttpMessageConverter) messageConverter).write(o, null, outputMessage);
                    return;
                }
            }
        }
    }

    private void registerResponseEntity(ResponseEntity<?> entity, HttpOutputMessage outputMessage) throws IOException {
        Object body = entity.getBody();
        if (body == null) {
            return;
        }

        if (body instanceof byte[]) {
            outputMessage.getBody().write((byte[]) body);
            return;
        }
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
        if (apiResponseEncodeRegister.isPass()) {
            return false;
        }
        return super.canRead(type, contextClass, mediaType);
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        if (apiResponseEncodeRegister.isPass()) {
            return false;
        }
        return super.canWrite(type, clazz, mediaType);
    }
}

