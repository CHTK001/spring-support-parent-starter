package com.chua.starter.apm.support.web;

import java.util.Map;

/**
 * APM 统一接口的请求抽象。
 * <p>
 * 嵌入模式由 {@code ApmServlet} 从 {@code HttpServletRequest} 转换而来，
 * 独立模式由 {@code ServerHandler} 从 {@code ServerRequest} 转换而来，
 * 两者之后走同一套 {@code ApmDispatcher}，保证两种启动方式行为完全一致。
 * </p>
 *
 * @param method  HTTP 方法
 * @param path    请求路径（可带统一前缀）
 * @param params  查询参数
 * @author CH
 * @since 4.0.0.51
 */
public record ApmRequest(String method, String path, Map<String, String> params) {

    /**
     * 构造 GET 请求
     *
     * @param path   路径
     * @param params 参数
     * @return 请求
     */
    public static ApmRequest get(String path, Map<String, String> params) {
        return new ApmRequest("GET", path, params == null ? Map.of() : params);
    }

    /**
     * 取参数
     *
     * @param name 参数名
     * @return 值，不存在返回 null
     */
    public String param(String name) {
        return params == null ? null : params.get(name);
    }
}
