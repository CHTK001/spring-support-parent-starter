package com.chua.starter.logger.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 日志事件模型：客户端采集 → 传输 → 服务端存储 → 查询/推送 的统一载体。
 *
 * <p>忽略未知字段：新版本客户端向旧版本服务端推送新增字段时，旧服务端不因
 * 未知属性反序列化失败而丢弃整批事件。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoggerEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件时间戳（毫秒）
     */
    private long ts;

    /**
     * 应用名
     */
    private String app;

    /**
     * 实例标识（pid@host）
     */
    private String instance;

    /**
     * 级别：TRACE/DEBUG/INFO/WARN/ERROR
     */
    private String level;

    /**
     * 线程名
     */
    private String thread;

    /**
     * logger 名
     */
    private String logger;

    /**
     * 链路追踪标识（可空）：服务端采集取自 MDC 的 traceId，
     * HTTP 推送缺省时回落请求头 x-trace-id；用于链路追踪聚合
     */
    private String traceId;

    /**
     * 格式化后的消息
     */
    private String message;

    /**
     * 异常堆栈（可空）
     */
    private String stacktrace;

    /**
     * MDC 上下文（可空）
     */
    private Map<String, String> mdc;
}
