package com.chua.starter.monitor.support.model;

/**
 * 抓包会话异常诊断行
 *
 * @param key      稳定标识，如 reset / unreachable / retransmission …
 * @param label    人可读名称
 * @param severity 严重级别：danger / warning / info
 * @param count    命中报文数
 * @param filter   可回填显示过滤条的等价表达式（可空）
 * @author CH
 * @since 4.0.0.42
 */
public record TsharkAnomalyVO(String key, String label, String severity, long count, String filter) {
}