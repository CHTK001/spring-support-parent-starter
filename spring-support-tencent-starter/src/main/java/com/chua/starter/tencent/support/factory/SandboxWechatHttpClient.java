package com.chua.starter.tencent.support.factory;

import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.http.HttpHeaders;
import com.wechat.pay.java.core.http.HttpRequest;
import com.wechat.pay.java.core.http.HttpResponse;
import com.wechat.pay.java.core.http.RequestBody;

import java.io.InputStream;

/**
 * 微信支付沙箱环境 http客户端 包装器
 * <p>将生产环境 URL {@code api.mch.weixin.qq.com/...} 重写为沙箱环境 URL
 * {@code api.mch.weixin.qq.com/sandboxnew/...}，使 SDK 服务类可在沙箱环境下工作。</p>
 *
 * @since 4.0.0.41
 * @author CH
 */
public class SandboxWechatHttpClient implements HttpClient {

    /** Production_主机 */
    private static final String PRODUCTION_HOST = "api.mch.weixin.qq.com";
    /** Sandbox_路径 */
    private static final String SANDBOX_PATH = "/sandboxnew";

    /** Delegate */
    private final HttpClient delegate;

    /**
    * 创建 sandboxwechathttp客户端 实例
    * @param delegate delegate
    */
    public SandboxWechatHttpClient(HttpClient delegate) {
        this.delegate = delegate;
    }

    @Override
    /** 执行 */
    public <T> HttpResponse<T> execute(HttpRequest request, Class<T> responseClass) {
        return delegate.execute(rewriteRequest(request), responseClass);
    }

    @Override
    /** 获取 */
    public <T> HttpResponse<T> get(HttpHeaders headers, String url, Class<T> responseClass) {
        return delegate.get(headers, rewriteUrl(url), responseClass);
    }

    @Override
    /** Post */
    public <T> HttpResponse<T> post(HttpHeaders headers, String url, RequestBody body, Class<T> responseClass) {
        return delegate.post(headers, rewriteUrl(url), body, responseClass);
    }

    @Override
    /** Patch */
    public <T> HttpResponse<T> patch(HttpHeaders headers, String url, RequestBody body, Class<T> responseClass) {
        return delegate.patch(headers, rewriteUrl(url), body, responseClass);
    }

    @Override
    /** 放入 */
    public <T> HttpResponse<T> put(HttpHeaders headers, String url, RequestBody body, Class<T> responseClass) {
        return delegate.put(headers, rewriteUrl(url), body, responseClass);
    }

    @Override
    /** 删除 */
    public <T> HttpResponse<T> delete(HttpHeaders headers, String url, Class<T> responseClass) {
        return delegate.delete(headers, rewriteUrl(url), responseClass);
    }

    @Override
    /** Download */
    public InputStream download(String url) {
        return delegate.download(rewriteUrl(url));
    }

    /**
    * rewrite请求
    *
    * @param request 请求
    * @return rewrite请求的结果
    */
    private HttpRequest rewriteRequest(HttpRequest request) {
        String originalUrl = request.getUrl().toString();
        String newUrl = rewriteUrl(originalUrl);
        if (newUrl.equals(originalUrl)) {
            return request;
        }
        return new HttpRequest.Builder()
                .httpMethod(request.getHttpMethod())
                .url(newUrl)
                .headers(request.getHeaders())
                .body(request.getBody())
                .build();
    }

    /**
     * rewriteurl
     *
     * @param url url
     * @return rewriteUrl的结果
     */
    private String rewriteUrl(String url) {
        if (url == null || url.contains(SANDBOX_PATH)) {
            return url;
        }
        String prefix = "https://" + PRODUCTION_HOST + "/";
        if (url.startsWith(prefix)) {
            return url.replace(prefix, "https://" + PRODUCTION_HOST + SANDBOX_PATH + "/");
        }
        return url;
    }
}
