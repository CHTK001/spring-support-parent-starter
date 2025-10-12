package com.chua.starter.common.support.converter;

import com.chua.starter.common.support.codec.CodecFactory;
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

    private final CodecFactory codecFactory;
    private final List<HttpMessageConverter<?>> messageConverters;

    public BinaryHttpMessageConverter(CodecFactory codecFactory, List<HttpMessageConverter<?>> messageConverters) {
        super(MediaType.parseMediaType("application/encrypted-data"));
        this.codecFactory = codecFactory;
        this.messageConverters = messageConverters;
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    protected void writeInternal(Object o, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {

        if(o instanceof ResponseEntity<?> entity) {
            registerResponseEntity(entity, outputMessage);
            return;
        }

        if(o instanceof byte[] bytes) {
            outputMessage.getBody().write(bytes);
            return;
        }

        HttpHeaders headers = outputMessage.getHeaders();
        String string = headers.getFirst("access-control-no-data");
        String accept = headers.getFirst(HttpHeaders.ACCEPT);
        if(accept != null && accept.contains("application/json")) {
            registerAccept(outputMessage, o);
            return;
        }
    }

    private void registerAccept(HttpOutputMessage outputMessage, Object o) throws IOException {
        for (HttpMessageConverter<?> messageConverter : messageConverters) {
            if(messageConverter.canWrite(o.getClass(), null)) {
                ((HttpMessageConverter) messageConverter).write(o, null, outputMessage);
                return;
            }
        }
    }

    private void registerResponseEntity(ResponseEntity<?> entity, HttpOutputMessage outputMessage) throws IOException {
        Object body = entity.getBody();
        if(body == null) {
            return;
        }

        if(body instanceof byte[]) {
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
        if(codecFactory.isPass()) {
            return false;
        }
        return super.canRead(type, contextClass, mediaType);
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        if(codecFactory.isPass()) {
            return false;
        }
        return super.canWrite(type, clazz, mediaType);
    }
}
