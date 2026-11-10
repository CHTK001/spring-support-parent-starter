package com.chua.starter.httpman.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 接口调试控制台 - 代理执行结果
 * <p>由 {@code HttpmanProxyExecutor} 返回{@code Mono<HttpmanResponse>}，
 * 承载一次真实发出的 HTTP 请求的完整结果。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class HttpmanResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应状态码（失败的连接异常时为 -1）
     */
    private int status;

    /**
     * 响应头（扁平化：Header-Name -> value）
     */
    private Map<String, String> headers;

    /**
     * 响应正文（字符串）
     */
    private String body;

    /**
     * 响应正文长度（字节）
     */
    private long bodyLength;

    /**
     * 耗时（毫秒）
     */
    private long costMs;

    /**
     * 错误信息（连接失败/超时时写入）
     */
    private String error;

    /**
     * 是否执行成功
     */
    public boolean isOk() {
        // [P3C A20 豁免]
    return status >= 200 && status < 300 && error == null;
    }

    /**
     * 失败结果（从异常中构造）
     *
     * @param method 方法
     * @param url    地址
     * @param start  发起时间戳（毫秒）
     * @param e      异常
     * @return 执行结果
     */
    public static HttpmanResponse error(String method, String url, long start, Throwable e) {
        HttpmanResponse response = new HttpmanResponse();
        response.setStatus(-1);
        response.setCostMs(System.currentTimeMillis() - start);
        response.setError(String.valueOf(e == null ? "unknown" : e.getMessage()));
        return response;
    }
}
