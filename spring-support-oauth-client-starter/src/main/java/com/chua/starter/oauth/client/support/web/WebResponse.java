package com.chua.starter.oauth.client.support.web;

import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.advice.Advice;
import com.chua.starter.oauth.client.support.advice.AdviceResolver;
import com.chua.starter.oauth.client.support.advice.HtmlAdviceResolver;
import com.chua.starter.oauth.client.support.advice.JsonAdviceResolver;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 响应
 *
 * @author CH
 */
@Slf4j
public class WebResponse {
    private final AuthClientProperties authProperties;
    private final FilterChain chain;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public WebResponse(AuthClientProperties authProperties, FilterChain chain, HttpServletRequest request, HttpServletResponse response) {
        this.authProperties = authProperties;
        this.chain = chain;
        this.request = request;
        this.response = response;
    }

    public void doFailureChain(Information information) {
        if (null != information) {
            response.setStatus(information.getCode());
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

    private boolean isLocalhost() {
        String ipAddress = RequestUtils.getIpAddress(request);
        return null == ipAddress || "localhost".equalsIgnoreCase(ipAddress) || "127.0.0.1".equalsIgnoreCase(ipAddress);
    }
}
