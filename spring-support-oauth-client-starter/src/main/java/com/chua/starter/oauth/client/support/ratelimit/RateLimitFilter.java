package com.chua.starter.oauth.client.support.ratelimit;

import com.chua.common.support.lang.json.Json;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
* o认证 客户端限流过滤器
* <p>
* 作为 Servlet 过滤器并置于最高优先级（在认证过滤器之前），对包括未认证流量在内的
* 所有请求先执行限流检查：全局接口 QPS 限流（防刷/防 执行s）与登录接口 IP 限流
* （防暴力破解）。配置前缀 <code>plugin.OAuth.rate-限制</code>。
* </p>
*
* @since 4.0.0.42
* @author CH
 */
public class RateLimitFilter implements Filter {

    /**
    * 限流拒绝状态码
    */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /**
    * 登录路径匹配正则：URI 路径段以 login 结尾（排除 login编码 等含 login 的非登录接口）
    */
    private static final Pattern LOGIN_PATH_PATTERN = Pattern.compile("(^|/)login$");

    /**
    * 客户端配置
    */
    private final AuthClientProperties authClientProperties;

    /**
    * 构造函数
    *
    * @param authClientProperties 客户端配置
    */
    public RateLimitFilter(AuthClientProperties authClientProperties) {
        this.authClientProperties = authClientProperties;
    }

    @Override
    /** 执行过滤 */
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest) || !(servletResponse instanceof HttpServletResponse)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        AuthClientProperties.RateLimit rateLimit = authClientProperties.getRateLimit();
        if (rateLimit == null || !rateLimit.isEnable()) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        String ip = RequestUtils.getIpAddress(request);
        RateLimitService rateLimitService = RateLimitService.getInstance();

        // 全局接口 QPS 限流（未认证流量同样受限）
        if (rateLimit.getApi().isEnable() && !rateLimitService.checkApiLimit(ip)) {
            /**
    * 写入rejected。
    * @param response 响应
    * @param "请求过于频繁，请稍后再试" "请求过于频繁，请稍后再试"
    */
            writeRejected(response, "请求过于频繁，请稍后再试");
            return;
        }

        // 登录接口 IP 限流
        if (rateLimit.getLogin().isEnable() && isLoginRequest(request) && !rateLimitService.checkLoginIpLimit(ip)) {
            /**
            * 写入rejected。
            * @param response 响应
            * @param "登录尝试过于频繁，请稍后再试" "登录尝试过于频繁，请稍后再试"
            */
            writeRejected(response, "登录尝试过于频繁，请稍后再试");
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    /**
    * 判断是否为登录请求
    *
    * @param request 请求
    * @return true-是登录请求
    */
    private boolean isLoginRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && LOGIN_PATH_PATTERN.matcher(uri).find();
    }

    /**
    * 写入限流拒绝响应
    *
    * @param response 响应
    * @param message  提示信息
    */
    private void writeRejected(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HTTP_TOO_MANY_REQUESTS);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>(4);
        body.put("code", "42900");
        body.put("msg", message);
        body.put("success", false);
        response.getWriter().write(Json.toJson(body));
    }
}
