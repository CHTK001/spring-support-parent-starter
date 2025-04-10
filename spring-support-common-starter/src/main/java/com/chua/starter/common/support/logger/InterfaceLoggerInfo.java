package com.chua.starter.common.support.logger;

import com.chua.common.support.utils.IoUtils;
import com.chua.starter.common.support.filter.CustomHttpServletRequestWrapper;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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
     * 请求体
     */
    private byte[] body = new byte[0];

    public InterfaceLoggerInfo(HttpServletRequest request) {
        super(request);
        this.method = request.getMethod();
        this.url = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        this.ip = RequestUtils.getIpAddress(request);
        this.queryParams = request.getQueryString();
        if (request instanceof CustomHttpServletRequestWrapper wrapper) {
            try {
                this.body = IoUtils.toByteArray(wrapper.getInputStream());
            } catch (IOException ignored) {
            }
        }
        this.request = request;
    }
}
