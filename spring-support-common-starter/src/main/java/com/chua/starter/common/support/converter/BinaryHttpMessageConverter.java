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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 二进制消息转换器
 *
 * @since 2025/10/10 14:22
 * @author CH
 */
public class BinaryHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(BinaryHttpMessageConverter.class);
        /** API响应encoderegister */
        private final ApiResponseEncodeRegister apiResponseEncodeRegister;
    private final List<HttpMessageConverter<?>> messageConverters; // 消息转换器

    /**
      * 创建 binaryhttp消息转换器 实例
     * @param apiResponseEncodeRegister api响应encode注册
     * @param messageConverters 列表
     * @param messageConverters 消息转换器
     */
    public BinaryHttpMessageConverter(ApiResponseEncodeRegister apiResponseEncodeRegister, List<HttpMessageConverter<?>> messageConverters) {
        /**
         * 父。
         * @param MediaType.parseMediaType("application/encrypted-data" media类型.解析media类型("application/encrypted-数据"
         */
        super(MediaType.parseMediaType("application/encrypted-data"));
        this.apiResponseEncodeRegister = apiResponseEncodeRegister;
        this.messageConverters = messageConverters;
        log.info("[消息转换器]创建 BinaryHttpMessageConverter, 支持的媒体类型: application/encrypted-data, 委托转换器数量: {}", messageConverters.size());
    }

    @Override
    /** 读取内部 */
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    /** 写入内部 */
    protected void writeInternal(Object o, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        if (log.isTraceEnabled()) {
            log.trace("[消息转换器]BinaryHttpMessageConverter 开始写入, 对象类型: {}", o != null ? o.getClass().getName() : "null");
        }

        if (o instanceof ResponseEntity<?> entity) {
            if (log.isTraceEnabled()) {
                log.trace("[消息转换器]处理 ResponseEntity, 状态码: {}", entity.getStatusCode());
            }
            /**
             * 注册响应实体。
             * @param entity 实体
             * @param outputMessage 输出消息
             */
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
            /**
             * 注册accept。
             * @param outputMessage 输出消息
             * @param o o
             */
            registerAccept(outputMessage, o);
            return;
        }
    }

    /**
     * 注册Accept
     *
     * @param outputMessage 输出消息
     * @param o o
     */
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

    /**
     * 注册响应实体
     *
     * @param entity 实体
     * @param outputMessage 输出消息
     */
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
    /** 读取 */
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    /** 是否可以读取 */
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
    /** 是否可以写入 */
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

