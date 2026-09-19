package com.chua.starter.common.support.constant;

/**
 * MDC 链路追踪常量
 * <p>
 * 定义 MDC（Mapped Diagnostic 上下文）相关的常量，用于统一管理链路追踪的 Key 和 头部 名称。
 * </p>
 *
 * @版本 1.0.0
 * @since 2025-12-08
 * @author CH
 */
public class MdcConstant {

    /**
     * x-追踪-标识
     */
    public static final String X_TRACE_ID = "x-trace-id";

    /**
     * 追踪-标识
     */
    public static final String TRACE_ID = "traceId";

    /**
     * 用户标识
     */
    public static final String USER_ID = "userId";

    /**
     * 请求IP
     */
    public static final String REQUEST_IP = "requestIp";
}
