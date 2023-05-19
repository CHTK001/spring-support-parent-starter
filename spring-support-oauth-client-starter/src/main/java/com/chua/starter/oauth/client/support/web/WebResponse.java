package com.chua.starter.oauth.client.support.web;

import com.chua.common.support.json.Json;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.advice.Advice;
import com.chua.starter.oauth.client.support.advice.AdviceResolver;
import com.chua.starter.oauth.client.support.advice.HtmlAdviceResolver;
import com.chua.starter.oauth.client.support.advice.JsonAdviceResolver;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 响应
 *
 * @author CH
 */
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
            response.getOutputStream().write(
                    Json.toJson(
                                    ReturnResult.newBuilder()
                                            .setCode(null == information ? 403 : information.getCode())
                                            .setMsg(null == information ? "鉴权失败" : information.getMessage()).build())
                            .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private boolean isLocalhost() {
        String ipAddress = RequestUtils.getIpAddress(request);
        return null == ipAddress || "localhost".equalsIgnoreCase(ipAddress) || "127.0.0.1".equalsIgnoreCase(ipAddress);
    }
}
