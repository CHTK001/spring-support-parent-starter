package com.chua.starter.common.support.api.decode;

import com.chua.common.support.network.net.UserAgent;
import com.chua.starter.common.support.utils.NonceUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import lombok.extern.slf4j.Slf4j;

/**
 * 请求签名校验处理器
 * <p>
 * 对携带 x-sign 等签名头的请求进行合法性校验，不修改请求体。
 * </p>
 *
 * @author CH
 * @since 2024/12/07
 * @version 1.0.0
 */
@Slf4j
@RestControllerAdvice
@SuppressWarnings("ALL")
public class ApiRequestDecodeBodyAdvice implements RequestBodyAdvice {

    private final ApiRequestDecodeRegister decodeRegister;

    public ApiRequestDecodeBodyAdvice(ApiRequestDecodeRegister decodeRegister) {
        this.decodeRegister = decodeRegister;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
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
            return inputMessage;
        }

        if (!NonceUtils.validateXhrRequest(request)) {
            log.error("[RequestSign] x-sign 校验失败 uri={}", request.getRequestURI());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请求校验失败，请勿重放请求");
        }

        log.debug("[RequestSign] x-sign 校验通过 uri={}", request.getRequestURI());
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

    private String getRequestPath() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            var request = attributes.getRequest();
            return request.getRequestURI();
        }
        return null;
    }

    private HttpServletRequest getRequest() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    static class EmptyHttpInputMessage implements HttpInputMessage {
        private static final EmptyHttpInputMessage EMPTY_MESSAGE = new EmptyHttpInputMessage();
        private static final InputStream EMPTY = new ByteArrayInputStream(new byte[0]);
        private static final org.springframework.http.HttpHeaders EMPTY_HEADER = new org.springframework.http.HttpHeaders();

        @Override
        public InputStream getBody() throws IOException {
            return EMPTY;
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return EMPTY_HEADER;
        }

        public static EmptyHttpInputMessage getInstance() {
            return EMPTY_MESSAGE;
        }
    }
}
