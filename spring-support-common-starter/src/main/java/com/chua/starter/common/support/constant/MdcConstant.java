package com.chua.starter.common.support.constant;

import com.chua.common.support.http.HttpConstant;

/**
 * MDC 链路追踪常量
 * <p>
 * 定义 MDC（Mapped Diagnostic Context）相关的常量，用于统一管理链路追踪的 Key 和 Header 名称。
 * </p>
 * <p>
 * 常量统一定义在 {@link HttpConstant} 中，此接口继承以方便使用。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-08
 * @see HttpConstant
 */
public interface MdcConstant extends HttpConstant {
    /**
     * x-trace-id
     */
    String X_TRACE_ID = "x-trace-id";
    /**
     * trace-id
     */
    String TRACE_ID = "traceId";
}
