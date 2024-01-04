package com.chua.starter.common.support.view;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.media.MediaTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * response
 *
 * @author CH
 * @since 2022/8/12 8:40
 */
@Slf4j
public class ResponseHandler<T> {
    private static final String APPLICATION_JSON = "application/json";
    private static final String CONTENT_TYPE = "content-type";
    private static final String TEXT_XML = "text/xml";
    private final HttpServletResponse response;

    /**
     * 状态
     */
    private final int status;

    private final HttpHeaders httpHeaders = new HttpHeaders();
    private final List<Cookie> httpCookie = new ArrayList<>();
    private T body;
    private boolean isEnd;
    private boolean isJson;
    private boolean isXml;

    ResponseHandler(int status) {
        this.status = status;
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        this.response = servletRequestAttributes.getResponse();
    }

    /**
     * 初始化
     *
     * @return 初始化
     */
    public static <T> ResponseHandler<T> of(int status) {
        return new ResponseHandler<T>(status);
    }

    /**
     * 初始化
     *
     * @return 初始化
     */
    public static <T> ResponseHandler<T> notFound() {
        return new ResponseHandler<T>(404);
    }

    /**
     * 初始化
     *
     * @return 初始化
     */
    public static <T> ResponseHandler<T> ok() {
        return new ResponseHandler<T>(200);
    }

    /**
     * 初始化
     *
     * @return 初始化
     */
    public static <T> ResponseHandler<T> serverError() {
        return new ResponseHandler<T>(500);
    }

    /**
     * 构建
     */
    public ResponseHandler<T> build() {
        response.setStatus(status);
        for (Map.Entry<String, List<String>> entry : httpHeaders.entrySet()) {
            String key = entry.getKey();
            List<String> value = entry.getValue();
            value.forEach(item -> {
                response.addHeader(key, item);
            });
        }

        for (Cookie cookie : httpCookie) {
            response.addCookie(cookie);
        }

        if (null == body) {
            return this;
        }

        ServletOutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
            write(outputStream);
        } catch (Exception e) {
            log.error("", e);
        } finally {
            if (isEnd && null != outputStream) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.error("", e);
                }
            }
        }
        return this;
    }

    /**
     * 写入数据
     *
     * @param outputStream 流
     */
    private void write(ServletOutputStream outputStream) throws Exception {
        if (body instanceof byte[]) {
            outputStream.write((byte[]) body);
            return;
        }

        if (isXml) {
            MediaTypeHandler mediaTypeHandler = ServiceProvider.of(MediaTypeHandler.class).getExtension("xml");
            outputStream.write(mediaTypeHandler.asByteArray(body));
            return;
        }
        MediaTypeHandler mediaTypeHandler = ServiceProvider.of(MediaTypeHandler.class).getExtension("json");
        outputStream.write(mediaTypeHandler.asByteArray(body));

    }

    /**
     * 添加消息头
     *
     * @param cookieName  消息头
     * @param cookieValue 消息头
     * @return this
     */
    public ResponseHandler<T> cookie(String cookieName, String cookieValue) {
        httpCookie.add(new Cookie(cookieName, cookieValue));
        return this;
    }

    /**
     * 添加cookie
     *
     * @param cookieName  消息头
     * @param cookieValue 消息头
     * @return this
     */
    public ResponseHandler<T> cookie(String domain, String cookieName, String cookieValue) {
        Cookie cookie = new Cookie(cookieName, cookieValue);
        cookie.setDomain(domain);
        httpCookie.add(cookie);
        return this;
    }

    /**
     * 添加cookie
     *
     * @param cookieName  消息头
     * @param cookieValue 消息头
     * @param expiry      过期时间
     * @return this
     */
    public ResponseHandler<T> cookie(String cookieName, String cookieValue, int expiry) {
        Cookie cookie = new Cookie(cookieName, cookieValue);
        cookie.setMaxAge(expiry);
        httpCookie.add(cookie);
        return this;
    }

    /**
     * 添加cookie
     *
     * @param cookie cookie
     * @return this
     */
    public ResponseHandler<T> cookie(Cookie cookie) {
        httpCookie.add(cookie);
        return this;
    }

    /**
     * 添加消息头
     *
     * @param headerName  消息头
     * @param headerValue 消息头
     * @return this
     */
    public ResponseHandler<T> header(String headerName, String headerValue) {
        httpHeaders.add(headerName, headerValue);
        if (CONTENT_TYPE.equalsIgnoreCase(headerName)) {
            this.isJson = APPLICATION_JSON.equalsIgnoreCase(headerValue);
            this.isXml = TEXT_XML.equalsIgnoreCase(headerValue);
        }
        return this;
    }

    /**
     * 添加消息头 accessControlAllowOrigin
     *
     * @param allowedOrigin allowedOrigin
     * @return this
     */
    public ResponseHandler<T> accessControlAllowOrigin(String allowedOrigin) {
        httpHeaders.setAccessControlAllowOrigin(allowedOrigin);
        return this;
    }

    /**
     * 添加消息头 contentLength
     *
     * @param contentLength contentLength
     * @return this
     */
    public ResponseHandler<T> contentLength(long contentLength) {
        httpHeaders.setContentLength(contentLength);
        return this;
    }

    /**
     * 添加消息头 contentType
     *
     * @param parseMediaType parseMediaType
     * @return this
     */
    public ResponseHandler<T> contentType(MediaType parseMediaType) {
        httpHeaders.setContentType(parseMediaType);
        return this;
    }

    /**
     * 是否结束
     *
     * @param isEnd 是否结束
     * @return this
     */
    public ResponseHandler<T> body(boolean isEnd) {
        this.isEnd = isEnd;
        return this;
    }

    /**
     * 消息体
     *
     * @param body 消息体
     * @return this
     */
    public ResponseHandler<T> body(T body) {
        this.body = body;
        return this;
    }
}
