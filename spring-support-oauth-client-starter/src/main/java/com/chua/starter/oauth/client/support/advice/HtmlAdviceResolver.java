// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.advice;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.lang.balance.LoadBalance;
import com.chua.common.support.lang.balance.Node;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.xml.Xml;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.google.common.base.Strings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

/**
 * HTML
 *
 * @since 2022/7/29 10:24
 * @author CH
 */
@Slf4j
public class HtmlAdviceResolver implements AdviceResolver {

    @Override
    /**
     * 类型
     * @return 返回值
    */
    public String type() {
        return MediaType.TEXT_HTML_VALUE;
    }

    @Override
    /**
     * 解析
     * @param message 字符串
     * @param response 参数
     * @param status 整数
     * @return 返回值
     * @throws IOException 异常
    */
    public Object resolve(HttpServletResponse response, Integer status, String message) throws IOException {
        return null;
    }

    @Override
    /**
     * 是否HTML
     * @return 返回值
    */
    public boolean isHtml() {
        return true;
    }

    /**
     * HTML
     *
     * @param request        请求
     * @param response       响应
     * @param code           错误码
     * @param authProperties 认证属性
     */
    public void resolve(HttpServletRequest request, HttpServletResponse response, int code, AuthClientProperties authProperties) {
        String url1 = authProperties.getLoginPage();
        if (code == 403) {
            url1 = authProperties.getNoPermissionPage();
        }
        if (isLocalhost(request)) {
            try {
                response.sendRedirect(url1);
            } catch (IOException e) {
                log.error("", e);
            }
            return;
        }

        LoadBalance balance1 = ServiceProvider.of(LoadBalance.class).getExtension(authProperties.getBalance());
        LoadBalance robin1 = balance1.create();
        String[] split = OauthClientRuntimeContext.resolvePlaceholders(authProperties.getLoginAddress()).split(",");
        robin1.addNode((Object[]) split);

        Node robin = robin1.selectNode();
        String address = robin.getContent().toString();

        String queryString = request.getQueryString();
        String toString = request.getRequestURL().toString();
        if (!Strings.isNullOrEmpty(queryString)) {
            toString += "?" + queryString;
        }

        try {
            String encode = URLEncoder.encode(toString, StandardCharsets.UTF_8);
            String url = address;
            if (Strings.isNullOrEmpty(url)) {
                /**
                 * 尝试。
                 * @param response.getOutputStream( 响应.获取输出流(
                 */
                try (OutputStream writer = response.getOutputStream()) {
                    writer.write(Xml.toXmlByte(Json.getJsonObject(Json.toJson(ReturnResult.error("未授权")))));
                } catch (Exception e) {
                    throw new RuntimeException("未授权响应写出失败: " + e.getMessage(), e);
                }
                return;
            }
            if (!url.startsWith("http")) {
                url = "http://" + url;
            }
            response.sendRedirect(url + url1 + "?redirect_url=" + encode);
        } catch (IOException e) {
            log.error("", e);
        }
    }

    /**
     * 是否本地
     *
     * @param request 请求
     * @return 是否本地
     */
    private boolean isLocalhost(HttpServletRequest request) {
        String ipAddress = RequestUtils.getIpAddress(request);
        return null == ipAddress || "localhost".equalsIgnoreCase(ipAddress) || "127.0.0.1".equalsIgnoreCase(ipAddress);
    }
}