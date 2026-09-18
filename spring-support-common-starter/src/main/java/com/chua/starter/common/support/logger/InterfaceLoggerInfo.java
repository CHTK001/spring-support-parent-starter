package com.chua.starter.common.support.logger;

import com.chua.starter.common.support.filter.CustomHttpServletRequestWrapper;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
* 接口日志信息
*
* @author CH
* @since 4.0.0
 */
@EqualsAndHashCode(callSuper = true)
public class InterfaceLoggerInfo extends ApplicationEvent {


    final transient HttpServletRequest request; // 请求
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

    /**
    * 创建 接口日志记录器信息 实例
    * @param request 请求
    */
    public InterfaceLoggerInfo(HttpServletRequest request) {
        /**
        * 父。
        * @param request 请求
        */
        super(request);
        this.request = request;
        this.method = request.getMethod();
        this.url = URLDecoder.decode(request.getRequestURI(), UTF_8);
        this.ip = RequestUtils.getIpAddress(request);
        this.queryParams = request.getQueryString();
        if (request instanceof CustomHttpServletRequestWrapper wrapper) {
            String header = wrapper.getHeader("Content-Type");
            try {
                if (header != null && (header.contains("application/json") || header.contains("text/"))) {
                } else {
                    this.body = wrapper.getInputStream().readAllBytes();
                }
            } catch (IOException ignored) {
            }
        } else if (request instanceof ContentCachingRequestWrapper wrapper) {
            String header = wrapper.getHeader("Content-Type");
            if (header != null && (header.contains("application/json") || header.contains("text/"))) {
            } else {
                this.body = wrapper.getContentAsByteArray();
            }
        }
    }

    /**
    * 获取 请求
    *
    * @return request
    */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
    * 获取 方法
    *
    * @return method
    */
    public String getMethod() {
        return method;
    }

    /**
    * 设置 方法
    *
    * @param method 方法
    */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
    * 获取 url
    *
    * @return url
    */
    public String getUrl() {
        return url;
    }

    /**
    * 设置 url
    *
    * @param url url
    */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
    * 获取 ip
    *
    * @return ip
    */
    public String getIp() {
        return ip;
    }

    /**
    * 设置 ip
    *
    * @param ip ip
    */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
    * 获取 查询参数
    *
    * @return queryParams
    */
    public String getQueryParams() {
        return queryParams;
    }

    /**
    * 设置 查询参数
    *
    * @param queryParams 查询参数
    */
    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }

    /**
    * 获取 主体
    *
    * @return body
    */
    public byte[] getBody() {
        return body;
    }

    /**
    * 设置 主体
    *
    * @param body 主体
    */
    public void setBody(byte[] body) {
        this.body = body;
    }

}
