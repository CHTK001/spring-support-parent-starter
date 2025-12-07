package com.chua.starter.common.support.logger;

import com.chua.common.support.utils.IoUtils;
import com.chua.starter.common.support.filter.CustomHttpServletRequestWrapper;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 接口日志信息
 *
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class InterfaceLoggerInfo extends ApplicationEvent {


    final transient HttpServletRequest request;
    /**
     * 请求方法
     */
    private String method;
    /**
     * 请求地址
     */
    private String url;
    /**
     * 请求ip
     */
    private String ip;
    /**
     * 请求参数
     */
    private String queryParams;
    /**
     * 请求�?
     */
    private byte[] body = new byte[0];

    public InterfaceLoggerInfo(HttpServletRequest request) {
        super(request);
        this.method = request.getMethod();
        this.url = URLDecoder.decode(request.getRequestURI(), UTF_8);
        this.ip = RequestUtils.getIpAddress(request);
        this.queryParams = request.getQueryString();
        if (request instanceof CustomHttpServletRequestWrapper wrapper) {
            String header = wrapper.getHeader("Content-Type");
            try {
                if (header != null && (header.contains("application/json") || header.contains("text/"))) {
                    this.body = IoUtils.toString(wrapper.getInputStream(), UTF_8).getBytes(UTF_8);
                } else {
                    this.body = IoUtils.toByteArray(wrapper.getInputStream());
                }
            } catch (IOException ignored) {
            }
        }
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            String header = wrapper.getHeader("Content-Type");
            if (header != null && (header.contains("application/json") || header.contains("text/"))) {
                this.body = IoUtils.toString(wrapper.getContentAsByteArray(), UTF_8).getBytes(UTF_8);
            } else {
                this.body = wrapper.getContentAsByteArray();
            }
        }
        this.request = request;
    }
}

