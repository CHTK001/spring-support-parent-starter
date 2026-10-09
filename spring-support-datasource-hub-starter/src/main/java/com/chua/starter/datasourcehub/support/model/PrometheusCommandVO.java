package com.chua.starter.datasourcehub.support.model;

/**
 * Prometheus 命令执行结果（重载等）。
 *
 * @param success 是否成功
 * @param message 面向用户的结果摘要
 * @param output  命令输出原文
 * @author CH
 * @since 4.0.0.42
 */
public record PrometheusCommandVO(
        boolean success,
        String message,
        String output
) {
}
