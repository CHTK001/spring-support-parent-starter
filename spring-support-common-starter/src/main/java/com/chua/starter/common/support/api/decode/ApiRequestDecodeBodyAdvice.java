package com.chua.starter.common.support.api.decode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.starter.common.support.network.UserAgent;
import com.chua.starter.common.support.utils.NonceUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 请求签名校验处理器
 * <p>
 * 对携带 x-sign 等签名头的请求进行合法性校验，不修改请求体。
 * </p>
 *
 * @since 2024/12/07
 * @version 1.0.0
 * @author CH
 */
@RestControllerAdvice
@SuppressWarnings({"unchecked", "rawtypes"})
public class ApiRequestDecodeBodyAdvice implements RequestBodyAdvice  {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(ApiRequestDecodeBodyAdvice.class);
    /** Object_mapper */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** Encrypted_request_field */
    private static final String ENCRYPTED_REQUEST_FIELD = "data";

    /** Decoderegister */
    private final ApiRequestDecodeRegister decodeRegister;

    /**
     * 创建 ApiRequestDecodeBodyAdvice 实例
     * @param decodeRegister decodeRegister
     */
    public ApiRequestDecodeBodyAdvice(ApiRequestDecodeRegister decodeRegister) {
        this.decodeRegister = decodeRegister;
    }

    @Override
    /**
     * 判断是否支持
     * @param methodParameter methodParameter
     * @param targetType targetType
     * @param converterType converterType
     */
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    /**
     * BeforeBody读取
     * @param inputMessage inputMessage
     * @param parameter parameter
     * @param targetType targetType
     * @param converterType converterType
     */
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        if (!decodeRegister.requestDecodeOpen()) {
            log.debug("[RequestSign] 签名校验未启用，跳过处理");
            return inputMessage;
        }

        String requestPath = getRequestPath();
        if (decodeRegister.isWhiteListed(requestPath)) {
            log.debug("[RequestSign] 请求路径 {} 在白名单中，跳过校验", requestPath);
            return inputMessage;
        }

        HttpServletRequest request = getRequest();
        if (request == null) {
            return inputMessage;
        }

        String userAgent = inputMessage.getHeaders().getFirst("user-agent");
        if (UserAgent.isCrawler(userAgent)) {
            log.warn("[RequestSign] 检测到爬虫请求，拒绝处理： {}", userAgent);
            return EmptyHttpInputMessage.getInstance();
        }

        if (!NonceUtils.hasSignHeaders(request)) {
            log.debug("[RequestSign] 未携带完整签名头，跳过校验");
        } else {
            if (Boolean.TRUE.equals(
                    request.getAttribute(NonceUtils.REQUEST_SIGN_VALIDATED_ATTRIBUTE))) {
                log.debug("[RequestSign] 前置过滤器已完成校验 uri={}", request.getRequestURI());
            } else if (!NonceUtils.validateXhrRequest(request)) {
                log.error("[RequestSign] x-sign 校验失败 uri={}", request.getRequestURI());
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请求校验失败，请勿重放请求");
            } else {
                request.setAttribute(NonceUtils.REQUEST_SIGN_VALIDATED_ATTRIBUTE, Boolean.TRUE);
                log.debug("[RequestSign] x-sign 校验通过 uri={}", request.getRequestURI());
            }
        }

        if (!StringUtils.hasText(request.getHeader(decodeRegister.getKeyHeader()))) {
            return inputMessage;
        }

        return decodeRequestBody(inputMessage, request);
    }

    @Override
    /**
     * AfterBody读取
     * @param body body
     * @param inputMessage inputMessage
     * @param parameter parameter
     * @param targetType targetType
     * @param converterType converterType
     */
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    /**
     * 处理EmptyBody
     * @param body body
     * @param inputMessage inputMessage
     * @param parameter parameter
     * @param targetType targetType
     * @param converterType converterType
     */
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    /** 获取RequestPath */
    private String getRequestPath() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            var request = attributes.getRequest();
            return request.getRequestURI();
        }
        return null;
    }

    /** 获取Request */
    private HttpServletRequest getRequest() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 解码RequestBody
     * @param inputMessage inputMessage
     * @param request request
     */
    private HttpInputMessage decodeRequestBody(HttpInputMessage inputMessage, HttpServletRequest request) throws IOException {
        byte[] bodyBytes = StreamUtils.copyToByteArray(inputMessage.getBody());
        if (bodyBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求解密失败: 请求体为空");
        }

        String encryptedPayload = extractEncryptedPayload(bodyBytes);
        if (!StringUtils.hasText(encryptedPayload)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求解密失败: 未找到加密数据");
        }

        try {
            byte[] decodedBytes = decodeRegister.decodeRequest(encryptedPayload);
            if (decodedBytes == null || decodedBytes.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求解密失败: 解密结果为空");
            }

            log.debug("[RequestCodec] 请求解密成功 uri={}, size={} -> {}", request.getRequestURI(), bodyBytes.length, decodedBytes.length);
            return new DecodedHttpInputMessage(inputMessage.getHeaders(), decodedBytes);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[RequestCodec] 请求解密失败 uri={}", request.getRequestURI(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求解密失败");
        }
    }

    /**
     * ExtractEncryptedPayload
     * @param bodyBytes bodyBytes
     */
    private String extractEncryptedPayload(byte[] bodyBytes) throws IOException {
        String rawBody = new String(bodyBytes, StandardCharsets.UTF_8).trim();
        if (!StringUtils.hasText(rawBody)) {
            return null;
        }

        if (!rawBody.startsWith("{") && !rawBody.startsWith("[")) {
            return rawBody;
        }

        JsonNode root = OBJECT_MAPPER.readTree(rawBody);
        if (root.isObject()) {
            JsonNode dataNode = root.get(ENCRYPTED_REQUEST_FIELD);
            if (dataNode != null && dataNode.isTextual()) {
                return dataNode.asText();
            }
            return null;
        }

        if (root.isArray() && root.size() > 0) {
            JsonNode first = root.get(0);
            if (first != null && first.isObject()) {
                JsonNode dataNode = first.get(ENCRYPTED_REQUEST_FIELD);
                if (dataNode != null && dataNode.isTextual()) {
                    return dataNode.asText();
                }
            }
        }

        return null;
    }

    static class DecodedHttpInputMessage implements HttpInputMessage {
        /** Headers */
        private final HttpHeaders headers = new HttpHeaders();
        /** 请求体 */
        private final byte[] body;

        DecodedHttpInputMessage(HttpHeaders sourceHeaders, byte[] body) {
            if (sourceHeaders != null) {
                this.headers.putAll(sourceHeaders);
            }
            this.body = body;
            this.headers.setContentLength(body.length);
        }

        @Override
        /** 获取Body */
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        /** 获取Headers */
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

    static class EmptyHttpInputMessage implements HttpInputMessage {
        /** Empty_message */
        private static final EmptyHttpInputMessage EMPTY_MESSAGE = new EmptyHttpInputMessage();
        /** 是否为空 */
        private static final InputStream EMPTY = new ByteArrayInputStream(new byte[0]);
        /** Empty_header */
        private static final org.springframework.http.HttpHeaders EMPTY_HEADER = new org.springframework.http.HttpHeaders();

        @Override
        /** 获取Body */
        public InputStream getBody() throws IOException {
            return EMPTY;
        }

        @Override
        /** 获取Headers */
        public org.springframework.http.HttpHeaders getHeaders() {
            return EMPTY_HEADER;
        }

        public static EmptyHttpInputMessage getInstance() {
            return EMPTY_MESSAGE;
        }
    }
}
