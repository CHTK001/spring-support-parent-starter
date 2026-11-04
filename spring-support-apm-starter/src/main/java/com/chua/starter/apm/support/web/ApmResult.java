package com.chua.starter.apm.support.web;

/**
 * APM 统一接口的处理结果。
 * <p>
 * 只做「成功/失败 + 数据 + 说明」三元组，不掺 HTTP 状态码：
 * 嵌入模式的 Servlet 与独立模式的 ServerHandler 各自把 {@code ok=false}
 * 翻译成本协议栈的错误响应，{@code ok=true} 统一包成 {@code ReturnResult.ok(data)}。
 * </p>
 *
 * @param ok      是否成功
 * @param data    数据
 * @param message 失败原因（成功时为 null）
 * @author CH
 * @since 4.0.0.51
 */
public record ApmResult(boolean ok, Object data, String message) {

    /**
     * 成功
     *
     * @param data 数据
     * @return 结果
     */
    public static ApmResult ok(Object data) {
        return new ApmResult(true, data, null);
    }

    /**
     * 失败
     *
     * @param message 原因
     * @return 结果
     */
    public static ApmResult fail(String message) {
        return new ApmResult(false, null, message);
    }
}
