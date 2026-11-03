// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
// 注：Type 为 HttpMessageConverter 接口签名要求，豁免
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
    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(BinaryHttpMessageConverter.class);
        /**
         * API响应encoderegister
        */
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
    /**
     * 读取内部
     * @param clazz 参数
     * @param inputMessage 参数
     * @return 返回值
     * @throws HttpMessageNotReadableException 异常
     * @throws IOException 异常
    */
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    /**
     * 写入内部
     * @param o 对象
     * @param outputMessage 参数
     * @param type 参数
     * @return 返回值
     * @throws HttpMessageNotWritableException 异常
     * @throws IOException 异常
    */
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

        if (null == o) {
            return;
        }

        HttpServletRequest request = RequestUtils.getRequest();
        if (null == request) {
            // 异步线程（SSE 推送、定时任务、@Async）没有请求上下文，无法做加密协商。
            //
            // 此处此前只打一条 warn 就 return —— 等于把整段响应体丢掉。实际表现是
            // 隧道页 /ms/tunnel/{provider}/stream 只有「订阅瞬间」那一帧带数据，
            // 之后每一帧都是空的 `data:`：订阅帧跑在 Servlet 请求线程上（能取到
            // request，走下面的委托分支正常写出），而后续帧跑在推送调度线程上，
            // 走到这里被静默吞掉。因为本转换器排在转换器链首位，Spring 选中它之后
            // 就没有别的转换器再去写这段 payload 了。
            //
            // 数据不该因为「没有请求」而消失：与下面 Accept 分支一样委托给普通
            // 转换器写出（异步响应此时头已提交，registerAccept 设置 contentType
            // 对已提交响应无副作用）。
            if (log.isDebugEnabled()) {
                log.debug("[消息转换器]无请求上下文（异步响应），委托普通转换器写出, 对象类型: {}",
                        o.getClass().getName());
            }
            registerAccept(outputMessage, o);
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
     * @param o o
     * @param outputMessage 输出消息
     * @return 返回值
     * @throws IOException 异常
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
     * 用普通转换器写出对象，但不修改响应头。
     *
     * <p>异步响应（SSE / {@code StreamingResponseBody}）的响应头在第一次写出时
     * 就已提交，之后再 {@code setContentType} 会被 Tomcat 拒绝或破坏已发出的
     * 分块边界。所以这里只挑「当前已提交的 Content-Type」对应的转换器写出，
     * 不碰 {@link HttpOutputMessage#getHeaders()}。</p>
     *
     * @param outputMessage 输出消息
     * @param o             待写出对象
     * @throws IOException 写出失败
     */
    private void writeWithoutHeaderMutation(HttpOutputMessage outputMessage, Object o) throws IOException {
        Class<?> aClass = o.getClass();
        MediaType contentType = outputMessage.getHeaders().getContentType();
        for (HttpMessageConverter<?> messageConverter : messageConverters) {
            if (null != contentType) {
                if (messageConverter.canWrite(aClass, contentType)) {
                    ((HttpMessageConverter) messageConverter).write(o, null, outputMessage);
                    return;
                }
                continue;
            }
            for (MediaType supportedMediaType : messageConverter.getSupportedMediaTypes(aClass)) {
                if (messageConverter.canWrite(aClass, supportedMediaType)) {
                    ((HttpMessageConverter) messageConverter).write(o, null, outputMessage);
                    return;
                }
            }
        }
        log.warn("[消息转换器]异步响应未找到合适的转换器处理类型: {}", aClass.getName());
    }

    /**
     * 注册响应实体
     * @param entity 实体
     * @param outputMessage 输出消息
     * @return 返回值
     * @throws IOException 异常
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
    /**
     * 读取
     * @param contextClass 参数
     * @param inputMessage 参数
     * @param type 参数
     * @return 返回值
     * @throws HttpMessageNotReadableException 异常
     * @throws IOException 异常
    */
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    /**
     * 是否可以读取
     * @param contextClass 参数
     * @param mediaType 参数
     * @param type 参数
     * @return 返回值
    */
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
    /**
     * 是否可以写入
     * @param clazz 参数
     * @param mediaType 参数
     * @param type 参数
     * @return 返回值
    */
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

