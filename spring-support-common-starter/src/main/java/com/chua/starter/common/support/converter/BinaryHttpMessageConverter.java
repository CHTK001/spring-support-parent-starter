package com.chua.starter.common.support.converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
 * 二进制消息转换器
 *
 * @author CH
 * @since 2025/10/10 14:22
 */
public class BinaryHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BinaryHttpMessageConverter.class);


    private final ApiResponseEncodeRegister apiResponseEncodeRegister;
    private final List<HttpMessageConverter<?>> messageConverters;

    public BinaryHttpMessageConverter(ApiResponseEncodeRegister apiResponseEncodeRegister, List<HttpMessageConverter<?>> messageConverters) {
        super(MediaType.parseMediaType("application/encrypted-data"));
        this.apiResponseEncodeRegister = apiResponseEncodeRegister;
        this.messageConverters = messageConverters;
        log.info("[消息转换器]创建 BinaryHttpMessageConverter, 支持的媒体类型: application/encrypted-data, 委托转换器数量: {}", messageConverters.size());
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    protected void writeInternal(Object o, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        if (log.isTraceEnabled()) {
            log.trace("[消息转换器]BinaryHttpMessageConverter 开始写入, 对象类型: {}", o != null ? o.getClass().getName() : "null");
        }

        if (o instanceof ResponseEntity<?> entity) {
            if (log.isTraceEnabled()) {
                log.trace("[消息转换器]处理 ResponseEntity, 状态码: {}", entity.getStatusCode());
            }
            registerResponseEntity(entity, outputMessage);
            return;
        }

        if (o instanceof byte[] bytes) {
            if (log.isTraceEnabled()) {
                log.trace("[消息转换器]直接写入字节数组, 长度: {}", bytes.length);
            }
            outputMessage.getBody().write(bytes);
            return;
        }

        HttpServletRequest request = RequestUtils.getRequest();
        if (null == request) {
            log.warn("[消息转换器]无法获取 HttpServletRequest, 跳过写入");
            return;
        }
        String string = request.getHeader("access-control-no-data");
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null) {
            if (log.isTraceEnabled()) {
                log.trace("[消息转换器]根据 Accept 头处理, Accept: {}", accept);
            }
            outputMessage.getHeaders().remove("access-control-no-data");
            registerAccept(outputMessage, o);
            return;
        }
    }

    private void registerAccept(HttpOutputMessage outputMessage, Object o) throws IOException {
        Class<?> aClass = o.getClass();
        if (log.isTraceEnabled()) {
            log.trace("[消息转换器]查找合适的转换器处理类型: {}", aClass.getName());
        }
        for (HttpMessageConverter<?> messageConverter : messageConverters) {
            List<MediaType> supportedMediaTypes = messageConverter.getSupportedMediaTypes(aClass);
            if (supportedMediaTypes.isEmpty()) {
                continue;
            }
            for (MediaType supportedMediaType : supportedMediaTypes) {
                if (messageConverter.canWrite(aClass, supportedMediaType)) {
                    if (log.isDebugEnabled()) {
                        log.debug("[消息转换器]使用转换器: {} 处理类型: {}, 媒体类型: {}", 
                                messageConverter.getClass().getName(), aClass.getName(), supportedMediaType);
                    }
                    outputMessage.getHeaders().setContentType(supportedMediaType);
                    ((HttpMessageConverter) messageConverter).write(o, null, outputMessage);
                    return;
                }
            }
        }
        log.warn("[消息转换器]未找到合适的转换器处理类型: {}", aClass.getName());
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
            if (log.isTraceEnabled()) {
                log.trace("[消息转换器]BinaryHttpMessageConverter 跳过读取 (isPass=true), 类型: {}", type);
            }
            return false;
        }
        boolean canRead = super.canRead(type, contextClass, mediaType);
        if (log.isTraceEnabled()) {
            log.trace("[消息转换器]BinaryHttpMessageConverter canRead: {}, 类型: {}, 媒体类型: {}", canRead, type, mediaType);
        }
        return canRead;
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        if (apiResponseEncodeRegister.isPass()) {
            if (log.isTraceEnabled()) {
                log.trace("[消息转换器]BinaryHttpMessageConverter 跳过写入 (isPass=true), 类型: {}", type);
            }
            return false;
        }
        boolean canWrite = super.canWrite(type, clazz, mediaType);
        if (log.isTraceEnabled()) {
            log.trace("[消息转换器]BinaryHttpMessageConverter canWrite: {}, 类型: {}, 媒体类型: {}", canWrite, type, mediaType);
        }
        return canWrite;
    }
}

