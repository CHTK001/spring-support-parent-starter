// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.advice.Advice;
import com.chua.starter.oauth.client.support.advice.AdviceResolver;
import com.chua.starter.oauth.client.support.advice.HtmlAdviceResolver;
import com.chua.starter.oauth.client.support.advice.JsonAdviceResolver;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 响应
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class WebResponse {

    /**
     * 认证属性
    */
    private final AuthClientProperties authProperties;
    /**
     * Chain
    */
    private final FilterChain chain;
    /**
     * 请求
    */
    private final HttpServletRequest request;
    /**
     * 响应
    */
    private final HttpServletResponse response;

    /**
     * 创建 web响应 实例
     * @param authProperties 认证属性
     * @param chain 过滤器chain
     * @param request httpservlet请求
     * @param response httpservlet响应
     * @param chain chain
     * @param request 请求
     * @param response 响应
     */
    public WebResponse(AuthClientProperties authProperties, FilterChain chain, HttpServletRequest request, HttpServletResponse response) {
        this.authProperties = authProperties;
        this.chain = chain;
        this.request = request;
        this.response = response;
    }

    /**
     * 执行失败chain
     *
     * @param information 信息
     */
    public void doFailureChain(Information information) {
        if (null != information) {
            response.setStatus(information.getCode());
            log.error("鉴权服务器异常{} {}", information.getCode(), information.getMessage());
        }
        String accept = request.getHeader("accept");
        String contentType = response.getContentType();
        Advice advice = Advice.create();
        AdviceResolver resolve = advice.resolve(accept, contentType);
        if (null == resolve) {
            resolve = new JsonAdviceResolver();
        }
        if (resolve.isHtml()) {
            ((HtmlAdviceResolver) resolve).resolve(request, response, 400, authProperties);
            return;
        }
        response.setHeader("Content-Type", resolve.type());
        try {
            resolve.resolve(response, information.getCode(), information.getMessage());
        } catch (IOException e) {
            log.error("", e);
        }

    }

    /**
     * 是否Localhost
     *
     * @return 是否localhost的结果
     */
    private boolean isLocalhost() {
        String ipAddress = RequestUtils.getIpAddress(request);
        return null == ipAddress || "localhost".equalsIgnoreCase(ipAddress) || "127.0.0.1".equalsIgnoreCase(ipAddress);
    }
}